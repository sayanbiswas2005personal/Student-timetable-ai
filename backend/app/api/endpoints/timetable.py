from fastapi import APIRouter, UploadFile, File, Depends, HTTPException
from sqlalchemy.orm import Session
from app.core.database import get_db
import os
import shutil
import uuid
from app.services.ocr_service import process_timetable_image

router = APIRouter()

# Ensure upload directory exists
UPLOAD_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(__file__))), "timetable_data")
os.makedirs(UPLOAD_DIR, exist_ok=True)

@router.post("/upload")
async def upload_timetable(
    file: UploadFile = File(...),
    db: Session = Depends(get_db)
):
    if not file.filename.lower().endswith(('.png', '.jpg', '.jpeg')):
        raise HTTPException(status_code=400, detail="Only image files are allowed.")
        
    try:
        # Save file temporarily
        file_extension = os.path.splitext(file.filename)[1]
        unique_filename = f"{uuid.uuid4()}{file_extension}"
        file_path = os.path.join(UPLOAD_DIR, unique_filename)
        
        with open(file_path, "wb") as buffer:
            shutil.copyfileobj(file.file, buffer)
            
        # Process image using our OCR pipeline
        # Note: Since the exact DB linking requires manual review of the OCR output in a production app,
        # we will run the OCR process and return the extracted JSON structure back to the frontend.
        # This proves the pipeline works.
        try:
            extracted_data = process_timetable_image(file_path)
        except Exception as e:
            # If tesseract isn't installed on the host machine, catch it gracefully
            return {
                "success": False, 
                "error": "OCR Processing failed. Please ensure Tesseract is installed.",
                "details": str(e)
            }
            
        return {
            "success": True,
            "message": "Timetable processed successfully",
            "file_path": file_path,
            "extracted_data": extracted_data
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
