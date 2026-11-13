from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from typing import List

from backend.app.database.session import get_db
from backend.app.core.dependencies import require_admin
from backend.app.models.user import User
from backend.app.models.schedule_override import ScheduleOverride, OverrideType
from backend.app.schemas.schedule_override import ScheduleOverrideCreate, ScheduleOverrideResponse

router = APIRouter(prefix="/admin/overrides", tags=["Schedule Overrides"])

@router.get("", response_model=List[ScheduleOverrideResponse])
def get_overrides(db: Session = Depends(get_db), current_user: User = Depends(require_admin)):
    return db.query(ScheduleOverride).order_by(ScheduleOverride.date.desc()).all()

@router.post("", response_model=ScheduleOverrideResponse)
def create_override(
    override_in: ScheduleOverrideCreate,
    db: Session = Depends(get_db),
    current_user: User = Depends(require_admin)
):
    override = ScheduleOverride(
        date=override_in.date,
        department=override_in.department.strip().upper(),
        year=override_in.year,
        semester=override_in.semester,
        section=override_in.section.strip().upper(),
        override_type=override_in.override_type,
        start_time=override_in.start_time,
        end_time=override_in.end_time,
        subject=override_in.subject,
        faculty=override_in.faculty,
        room=override_in.room,
        reason=override_in.reason,
        created_by_user_id=current_user.id
    )
    db.add(override)
    db.commit()
    db.refresh(override)
    return override

@router.delete("/{id}")
def delete_override(id: int, db: Session = Depends(get_db), current_user: User = Depends(require_admin)):
    override = db.query(ScheduleOverride).filter(ScheduleOverride.id == id).first()
    if not override:
        raise HTTPException(status_code=404, detail="Override not found.")
    db.delete(override)
    db.commit()
    return {"message": "Override deleted."}

