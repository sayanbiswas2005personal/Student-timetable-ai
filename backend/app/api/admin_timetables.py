import os
import json
import shutil
from pathlib import Path
from datetime import datetime, timezone
from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, UploadFile, File, Form, Query, status
from sqlalchemy.orm import Session
from sqlalchemy import or_

from backend.app.database.session import get_db
from backend.app.core.config import settings
from backend.app.core.dependencies import require_admin
from backend.app.models.user import User
from backend.app.models.department import Department
from backend.app.models.timetable import Timetable, ProcessingStatus
from backend.app.models.timetable_entry import TimetableEntry
from backend.app.models.audit_log import AuditLog
from backend.app.schemas.timetable import (
    TimetableCreateResponse,
    TimetablePreviewResponse,
    TimetableListItem,
    TimetableUpdateRequest,
    NormalizedTimetableJSON
)
from backend.app.services.pdf_extractor import extract_timetable_data
from backend.app.services.json_validator import validate_and_normalize_timetable_json

router = APIRouter(prefix="/admin/timetables", tags=["Admin Timetable Management"])

@router.get("", response_model=List[TimetableListItem])
def list_timetables(
    db: Session = Depends(get_db),
    current_user: User = Depends(require_admin)
):
    """
    List all uploaded timetables and their statuses.
    """
    timetables = db.query(Timetable).order_by(Timetable.uploaded_at.desc()).all()
    results = []
    for tt in timetables:
        entry_count = len(tt.entries)
        if entry_count == 0 and tt.extracted_json:
            try:
                parsed = json.loads(tt.extracted_json)
                entry_count = len(parsed.get("entries", []))
            except Exception:
                entry_count = 0

        results.append(TimetableListItem(
            id=tt.id,
            original_filename=tt.original_filename,
            academic_year=tt.academic_year,
            institution=tt.institution,
            uploaded_at=tt.uploaded_at,
            processing_status=tt.processing_status.value,
            is_published=tt.is_published,
            version=tt.version,
            total_entries=entry_count,
            extraction_provider=tt.extraction_provider
        ))
    return results

@router.post("/upload", response_model=TimetableCreateResponse)
async def upload_timetable_pdf(
    file: UploadFile = File(...),
    academic_year: Optional[str] = Form("2026-2027"),
    department: Optional[str] = Form("CSE"),
    db: Session = Depends(get_db),
    current_user: User = Depends(require_admin)
):
    """
    Upload a new timetable PDF file.
    Validates file type and size.
    """
    if not file.filename.lower().endswith(".pdf"):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Invalid file format. Only PDF files (.pdf) are allowed."
        )

    # Sanitize filename and create storage path
    safe_filename = f"{int(datetime.now().timestamp())}_{file.filename}"
    file_path = settings.STORAGE_DIR / safe_filename

    # Read and check size
    file_bytes = await file.read()
    file_size_mb = len(file_bytes) / (1024 * 1024)
    if file_size_mb > settings.MAX_UPLOAD_SIZE_MB:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"File exceeds maximum allowed size of {settings.MAX_UPLOAD_SIZE_MB}MB."
        )

    # Save to disk
    with open(file_path, "wb") as f:
        f.write(file_bytes)

    # Create DB entry
    timetable = Timetable(
        original_filename=file.filename,
        file_path=str(file_path.resolve()),
        file_size_bytes=len(file_bytes),
        academic_year=academic_year,
        department=department,
        processing_status=ProcessingStatus.PENDING,
        is_published=False,
        version=1
    )
    db.add(timetable)
    db.commit()
    db.refresh(timetable)

    # Audit log
    audit = AuditLog(
        user_id=current_user.id,
        user_email=current_user.email,
        action="UPLOAD_TIMETABLE",
        details=f"Uploaded {file.filename} ({file_size_mb:.2f} MB), Timetable ID: {timetable.id}"
    )
    db.add(audit)
    db.commit()

    return TimetableCreateResponse(
        id=timetable.id,
        original_filename=file.filename,
        processing_status=timetable.processing_status.value,
        message="Timetable PDF uploaded successfully. Ready for extraction."
    )

@router.post("/{id}/extract")
def extract_timetable(
    id: int,
    provider: Optional[str] = Query(None, description="'gemini', 'layout_parser', or auto"),
    page_limit: Optional[int] = Query(None, description="Optional limit of pages to extract"),
    db: Session = Depends(get_db),
    current_user: User = Depends(require_admin)
):
    """
    Extract structured timetable JSON from the uploaded PDF.
    Uses Google Gemini 3.8 Flash Cloud API or layout parser fallback.
    """
    timetable = db.query(Timetable).filter(Timetable.id == id).first()
    if not timetable:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Timetable not found.")

    try:
        timetable.processing_status = ProcessingStatus.PROCESSING
        timetable.processing_error = None
        db.commit()

        # Call extractor
        extraction_res = extract_timetable_data(
            pdf_path=timetable.file_path,
            provider=provider,
            page_limit=page_limit
        )

        if not extraction_res.success or not extraction_res.data:
            timetable.processing_status = ProcessingStatus.ERROR
            timetable.processing_error = extraction_res.error or "Extraction failed."
            db.commit()
            raise HTTPException(
                status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
                detail=f"Extraction failed: {timetable.processing_error}"
            )

        # Validate extracted data
        val_res = validate_and_normalize_timetable_json(extraction_res.data)

        timetable.extracted_json = json.dumps(val_res.normalized_data)
        timetable.extraction_provider = extraction_res.provider
        timetable.processing_status = ProcessingStatus.EXTRACTED
        timetable.processing_error = None
        if val_res.normalized_data.get("academic_year"):
            timetable.academic_year = val_res.normalized_data["academic_year"]
        if val_res.normalized_data.get("institution"):
            timetable.institution = val_res.normalized_data["institution"]

        db.commit()
        db.refresh(timetable)

        # Audit log
        audit = AuditLog(
            user_id=current_user.id,
            user_email=current_user.email,
            action="EXTRACT_TIMETABLE",
            details=f"Extracted {len(val_res.normalized_data.get('entries', []))} entries using {extraction_res.provider}."
        )
        db.add(audit)
        db.commit()

        return {
            "id": timetable.id,
            "processing_status": timetable.processing_status.value,
            "extraction_provider": timetable.extraction_provider,
            "total_entries": len(val_res.normalized_data.get("entries", [])),
            "validation_errors": val_res.errors,
            "validation_warnings": val_res.warnings,
            "message": f"Successfully extracted {len(val_res.normalized_data.get('entries', []))} entries."
        }
    except HTTPException:
        db.rollback()
        timetable.processing_status = ProcessingStatus.ERROR
        timetable.processing_error = timetable.processing_error or "Extraction failed."
        db.commit()
        raise
    except Exception as exc:
        db.rollback()
        timetable.processing_status = ProcessingStatus.ERROR
        timetable.processing_error = str(exc) or "Unexpected extraction failure."
        db.commit()
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Extraction failed: {timetable.processing_error}"
        ) from exc

@router.get("/{id}/preview", response_model=TimetablePreviewResponse)
def get_timetable_preview(
    id: int,
    db: Session = Depends(get_db),
    current_user: User = Depends(require_admin)
):
    """
    Returns preview of extracted JSON data along with any validation issues.
    """
    timetable = db.query(Timetable).filter(Timetable.id == id).first()
    if not timetable:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Timetable not found.")

    parsed_json = None
    val_errors = []
    val_warnings = []
    total_entries = 0

    if timetable.extracted_json:
        try:
            raw_data = json.loads(timetable.extracted_json)
            val_res = validate_and_normalize_timetable_json(raw_data)
            parsed_json = NormalizedTimetableJSON.model_validate(val_res.normalized_data)
            val_errors = val_res.errors
            val_warnings = val_res.warnings
            total_entries = len(parsed_json.entries)
        except Exception as e:
            val_errors.append(f"Failed to parse extracted JSON: {str(e)}")

    return TimetablePreviewResponse(
        id=timetable.id,
        original_filename=timetable.original_filename,
        academic_year=timetable.academic_year,
        institution=timetable.institution,
        processing_status=timetable.processing_status.value,
        processing_error=timetable.processing_error,
        extraction_provider=timetable.extraction_provider,
        is_published=timetable.is_published,
        version=timetable.version,
        total_entries=total_entries,
        data=parsed_json,
        validation_errors=val_errors,
        validation_warnings=val_warnings
    )

@router.put("/{id}")
def update_timetable_json(
    id: int,
    update_req: TimetableUpdateRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(require_admin)
):
    """
    Allows admin to edit extracted timetable entries or metadata before approving.
    """
    timetable = db.query(Timetable).filter(Timetable.id == id).first()
    if not timetable:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Timetable not found.")

    current_data = {}
    if timetable.extracted_json:
        try:
            current_data = json.loads(timetable.extracted_json)
        except Exception:
            pass

    if update_req.academic_year:
        current_data["academic_year"] = update_req.academic_year
        timetable.academic_year = update_req.academic_year

    if update_req.valid_from:
        current_data["valid_from"] = update_req.valid_from

    if update_req.valid_until:
        current_data["valid_until"] = update_req.valid_until

    current_data["entries"] = [e.model_dump() for e in update_req.entries]

    # Validate
    val_res = validate_and_normalize_timetable_json(current_data)
    if not val_res.is_valid:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={"errors": val_res.errors, "warnings": val_res.warnings}
        )

    timetable.extracted_json = json.dumps(val_res.normalized_data)
    db.commit()

    return {
        "message": "Timetable updated successfully.",
        "total_entries": len(val_res.normalized_data["entries"]),
        "validation_warnings": val_res.warnings
    }

@router.post("/{id}/publish")
def publish_timetable(
    id: int,
    db: Session = Depends(get_db),
    current_user: User = Depends(require_admin)
):
    """
    Transactional publishing of an approved timetable.
    Validates, deactivates older active timetables, and writes to TimetableEntry.
    """
    timetable = db.query(Timetable).filter(Timetable.id == id).first()
    if not timetable:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Timetable not found.")

    if not timetable.extracted_json:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Cannot publish timetable without extracted data. Run extraction first."
        )

    try:
        raw_data = json.loads(timetable.extracted_json)
    except Exception as e:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Malformed extracted JSON.")

    val_res = validate_and_normalize_timetable_json(raw_data)
    if not val_res.is_valid:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={"message": "Cannot publish timetable with validation errors.", "errors": val_res.errors}
        )

    norm_data = val_res.normalized_data
    entries_list = norm_data.get("entries", [])
    if not entries_list:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Cannot publish an empty timetable."
        )

    try:
        departments = {entry["department"] for entry in entries_list}
        timetable.department = next(iter(departments)) if len(departments) == 1 else "ALL"
        for department_code in departments:
            if not db.query(Department).filter(Department.code == department_code).first():
                db.add(Department(
                    code=department_code,
                    name=department_code,
                    is_active=True
                ))

        # Atomic Transaction
        # 1. Unpublish other timetables
        db.query(Timetable).filter(
            Timetable.id != timetable.id,
            Timetable.is_published == True
        ).update({"is_published": False})

        # 2. Delete any existing entries for this timetable
        db.query(TimetableEntry).filter(TimetableEntry.timetable_id == timetable.id).delete()

        # 3. Create timetable entries
        for entry_dict in entries_list:
            ent = TimetableEntry(
                timetable_id=timetable.id,
                day=entry_dict["day"],
                start_time=entry_dict["start_time"],
                end_time=entry_dict["end_time"],
                department=entry_dict["department"],
                year=entry_dict["year"],
                semester=entry_dict["semester"],
                section=entry_dict["section"],
                subject_code=entry_dict.get("subject_code"),
                subject=entry_dict["subject"],
                faculty=entry_dict.get("faculty"),
                room=entry_dict.get("room"),
                notes=entry_dict.get("notes")
            )
            db.add(ent)

        # 4. Mark published
        timetable.is_published = True
        timetable.processing_status = ProcessingStatus.PUBLISHED
        timetable.approved_by_user_id = current_user.id
        timetable.approved_at = datetime.now(timezone.utc)
        timetable.version += 1

        # 5. Audit Log
        audit = AuditLog(
            user_id=current_user.id,
            user_email=current_user.email,
            action="PUBLISH_TIMETABLE",
            details=f"Published Timetable ID: {timetable.id} (v{timetable.version}) with {len(entries_list)} entries."
        )
        db.add(audit)

        db.commit()
        db.refresh(timetable)

        return {
            "message": f"Timetable published successfully! {len(entries_list)} entries are now live.",
            "timetable_id": timetable.id,
            "version": timetable.version,
            "is_published": True,
            "total_entries": len(entries_list)
        }

    except Exception as e:
        db.rollback()
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to publish timetable: {str(e)}"
        )

@router.post("/{id}/deactivate")
def deactivate_timetable(
    id: int,
    db: Session = Depends(get_db),
    current_user: User = Depends(require_admin)
):
    """
    Deactivates an active timetable.
    """
    timetable = db.query(Timetable).filter(Timetable.id == id).first()
    if not timetable:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Timetable not found.")

    timetable.is_published = False
    audit = AuditLog(
        user_id=current_user.id,
        user_email=current_user.email,
        action="DEACTIVATE_TIMETABLE",
        details=f"Deactivated Timetable ID: {timetable.id}"
    )
    db.add(audit)
    db.commit()

    return {"message": f"Timetable ID {timetable.id} deactivated.", "is_published": False}

@router.delete("/{id}")
def delete_timetable(
    id: int,
    db: Session = Depends(get_db),
    current_user: User = Depends(require_admin)
):
    """
    Delete a timetable record and its file.
    """
    timetable = db.query(Timetable).filter(Timetable.id == id).first()
    if not timetable:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Timetable not found.")

    if timetable.is_published:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Cannot delete an active published timetable. Deactivate it first."
        )

    # Delete file if exists
    if os.path.exists(timetable.file_path):
        try:
            os.remove(timetable.file_path)
        except Exception:
            pass

    db.delete(timetable)
    audit = AuditLog(
        user_id=current_user.id,
        user_email=current_user.email,
        action="DELETE_TIMETABLE",
        details=f"Deleted Timetable ID: {timetable.id} ({timetable.original_filename})"
    )
    db.add(audit)
    db.commit()

    return {"message": f"Timetable {id} deleted successfully."}
