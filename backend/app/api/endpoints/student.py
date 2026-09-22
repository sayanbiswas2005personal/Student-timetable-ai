from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from app.core.database import get_db
from app.schemas.student import StudentLookupRequest, StudentLookupResponse, StudentInfo, ClassInfo, TodayScheduleItem
from app.services.student_service import get_student_by_reg_and_roll
from app.services.schedule_service import get_today_schedule_for_section
from app.services.gemini_service import generate_schedule_explanation

router = APIRouter()

@router.post("/lookup", response_model=StudentLookupResponse)
def lookup_student(request: StudentLookupRequest, db: Session = Depends(get_db)):
    try:
        section, student = get_student_by_reg_and_roll(db, request.registration_number, request.roll_number)
    except ValueError as e:
        return StudentLookupResponse(
            success=False,
            error=str(e)
        )
    
    if not section:
        return StudentLookupResponse(
            success=False,
            error="Could not find student or determine program/batch from roll number."
        )
        
    student_info = StudentInfo(
        registration_number=request.registration_number if not student else student.registration_number,
        roll_number=request.roll_number if not student else student.roll_number,
        first_name=student.first_name if student else None,
        last_name=student.last_name if student else None,
        program=section.program.name,
        batch=section.batch.year,
        semester=section.semester.number,
        section=section.name
    )
    
    schedule, current_class, next_class = get_today_schedule_for_section(db, section.id)
    
    gemini_explanation = None
    if current_class or next_class:
        gemini_explanation = generate_schedule_explanation(
            student_data={"program": student_info.program, "section": student_info.section},
            current_class=current_class,
            next_class=next_class
        )
    
    return StudentLookupResponse(
        success=True,
        student=student_info,
        current_class=ClassInfo(**current_class) if current_class else None,
        next_class=ClassInfo(**next_class) if next_class else None,
        today_schedule=[TodayScheduleItem(**item) for item in schedule],
        gemini_explanation=gemini_explanation
    )
