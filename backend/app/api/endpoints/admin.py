from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from app.core.database import get_db
from app.models.university import Student, Section
from app.models.timetable import TimetableEntry
from typing import List
from pydantic import BaseModel

router = APIRouter()

# Return models
class AdminStudentResponse(BaseModel):
    id: int
    registration_number: str
    roll_number: str
    section_name: str
    program_name: str
    batch_year: int
    first_name: str = None
    last_name: str = None

class AdminCreateStudentRequest(BaseModel):
    registration_number: str
    roll_number: str
    first_name: str
    last_name: str
    section_id: int

class AdminSectionResponse(BaseModel):
    id: int
    name: str
    program_name: str
    batch_year: int

class AdminTimetableEntryResponse(BaseModel):
    day_of_week: int
    start_time: str
    end_time: str
    subject: str
    faculty: str
    room: str

@router.post("/students", response_model=AdminStudentResponse)
def create_student(request: AdminCreateStudentRequest, db: Session = Depends(get_db)):
    # Check if section exists
    section = db.query(Section).filter(Section.id == request.section_id).first()
    if not section:
        raise HTTPException(status_code=404, detail="Section not found")
        
    # Check if student exists
    normalized_reg = request.registration_number.replace(" ", "").upper()
    normalized_roll = request.roll_number.replace(" ", "").upper()
    
    existing = db.query(Student).filter(
        (Student.normalized_registration_number == normalized_reg) | 
        (Student.normalized_roll_number == normalized_roll)
    ).first()
    
    if existing:
        raise HTTPException(status_code=400, detail="Student with this Registration Number or Roll Number already exists.")
        
    student = Student(
        registration_number=request.registration_number,
        normalized_registration_number=normalized_reg,
        roll_number=request.roll_number,
        normalized_roll_number=normalized_roll,
        first_name=request.first_name,
        last_name=request.last_name,
        section_id=request.section_id
    )
    
    db.add(student)
    db.commit()
    db.refresh(student)
    
    return AdminStudentResponse(
        id=student.id,
        registration_number=student.registration_number,
        roll_number=student.roll_number,
        first_name=student.first_name,
        last_name=student.last_name,
        section_name=student.section.name,
        program_name=student.section.program.name,
        batch_year=student.section.batch.year
    )

@router.put("/students/{student_id}", response_model=AdminStudentResponse)
def update_student(student_id: int, request: AdminCreateStudentRequest, db: Session = Depends(get_db)):
    student = db.query(Student).filter(Student.id == student_id).first()
    if not student:
        raise HTTPException(status_code=404, detail="Student not found")

    section = db.query(Section).filter(Section.id == request.section_id).first()
    if not section:
        raise HTTPException(status_code=404, detail="Section not found")

    # Check if updated registration/roll number conflicts with another student
    normalized_reg = request.registration_number.replace(" ", "").upper()
    normalized_roll = request.roll_number.replace(" ", "").upper()

    existing = db.query(Student).filter(
        (Student.id != student_id) &
        ((Student.normalized_registration_number == normalized_reg) | 
         (Student.normalized_roll_number == normalized_roll))
    ).first()

    if existing:
        raise HTTPException(status_code=400, detail="Student with this Registration Number or Roll Number already exists.")

    student.registration_number = request.registration_number
    student.normalized_registration_number = normalized_reg
    student.roll_number = request.roll_number
    student.normalized_roll_number = normalized_roll
    student.first_name = request.first_name
    student.last_name = request.last_name
    student.section_id = request.section_id

    db.commit()
    db.refresh(student)

    return AdminStudentResponse(
        id=student.id,
        registration_number=student.registration_number,
        roll_number=student.roll_number,
        first_name=student.first_name,
        last_name=student.last_name,
        section_name=student.section.name,
        program_name=student.section.program.name,
        batch_year=student.section.batch.year
    )

@router.get("/students", response_model=List[AdminStudentResponse])
def get_all_students(db: Session = Depends(get_db)):
    students = db.query(Student).all()
    result = []
    for s in students:
        result.append(AdminStudentResponse(
            id=s.id,
            registration_number=s.registration_number,
            roll_number=s.roll_number,
            first_name=s.first_name,
            last_name=s.last_name,
            section_name=s.section.name,
            program_name=s.section.program.name,
            batch_year=s.section.batch.year
        ))
    return result

@router.get("/sections", response_model=List[AdminSectionResponse])
def get_all_sections(db: Session = Depends(get_db)):
    sections = db.query(Section).all()
    result = []
    for s in sections:
        result.append(AdminSectionResponse(
            id=s.id,
            name=s.name,
            program_name=s.program.name,
            batch_year=s.batch.year
        ))
    return result

@router.get("/timetable/{section_id}", response_model=List[AdminTimetableEntryResponse])
def get_timetable_for_section(section_id: int, db: Session = Depends(get_db)):
    entries = db.query(TimetableEntry).filter(TimetableEntry.section_id == section_id).order_by(TimetableEntry.day_of_week, TimetableEntry.start_time).all()
    result = []
    for e in entries:
        result.append(AdminTimetableEntryResponse(
            day_of_week=e.day_of_week,
            start_time=e.start_time.strftime("%H:%M"),
            end_time=e.end_time.strftime("%H:%M"),
            subject=e.subject.name,
            faculty=e.faculty.name if e.faculty else "Unknown Faculty",
            room=e.room.name if e.room else "TBD"
        ))
    return result
