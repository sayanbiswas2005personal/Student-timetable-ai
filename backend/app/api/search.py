from fastapi import APIRouter, Depends, Query, HTTPException
from sqlalchemy.orm import Session
from sqlalchemy import func
from typing import Optional, List, Dict, Any
from datetime import datetime
import pytz

from backend.app.database.session import get_db
from backend.app.models.department import Department
from backend.app.schemas.search import FacultySearchResponse
from backend.app.schemas.department import DepartmentResponse
from backend.app.schemas.academic_structure import AcademicStructureResponse
from backend.app.services.timetable_engine import TimetableEngine
from backend.app.core.config import settings
from backend.app.core.dependencies import require_faculty_or_admin
from backend.app.models.user import User
from backend.app.models.timetable import Timetable
from backend.app.models.timetable_entry import TimetableEntry

router = APIRouter(prefix="/search", tags=["Timetable Search"])

@router.get("/current-class", response_model=FacultySearchResponse)
def get_current_class(
    department: str = Query(..., description="Department or program code, e.g. CSE"),
    year: int = Query(..., ge=1, le=5, description="Academic year (1 to 4)"),
    semester: int = Query(..., ge=1, le=10, description="Academic semester (1 to 8)"),
    section: str = Query(..., description="Section letter, e.g. A, B, C"),
    simulated_date: Optional[str] = Query(None, description="Optional YYYY-MM-DD to simulate a specific date"),
    simulated_time: Optional[str] = Query(None, description="Optional HH:MM to simulate a specific time"),
    db: Session = Depends(get_db),
    current_user: User = Depends(require_faculty_or_admin)
):
    """
    Core search endpoint for faculty.
    Evaluates currently scheduled class for the section against current or simulated time.
    """
    engine = TimetableEngine(db)
    result = engine.check_schedule(
        department=department,
        year=year,
        semester=semester,
        section=section,
        simulated_date_str=simulated_date,
        simulated_time_str=simulated_time
    )
    return result

@router.get("/next-class")
def get_next_class(
    department: str = Query(...),
    year: int = Query(...),
    semester: int = Query(...),
    section: str = Query(...),
    simulated_date: Optional[str] = Query(None),
    simulated_time: Optional[str] = Query(None),
    db: Session = Depends(get_db),
    current_user: User = Depends(require_faculty_or_admin)
):
    """
    Returns only next scheduled class details.
    """
    engine = TimetableEngine(db)
    result = engine.check_schedule(
        department=department,
        year=year,
        semester=semester,
        section=section,
        simulated_date_str=simulated_date,
        simulated_time_str=simulated_time
    )
    return {
        "current_status": result.current_status,
        "next_class": result.next_class,
        "query_time": result.query_time,
        "query_day": result.query_day
    }

@router.get("/server-time")
def get_server_time():
    """
    Returns current campus time and configured timezone for synchronization.
    """
    try:
        tz = pytz.timezone(settings.TIMEZONE)
    except Exception:
        tz = pytz.timezone("Asia/Kolkata")
    
    now_campus = datetime.now(tz)
    return {
        "timezone": settings.TIMEZONE,
        "iso_timestamp": now_campus.isoformat(),
        "date": now_campus.strftime("%Y-%m-%d"),
        "time": now_campus.strftime("%H:%M:%S"),
        "day": now_campus.strftime("%A").upper(),
        "formatted": now_campus.strftime("%A, %d %B %Y - %I:%M:%S %p")
    }

# Public helper endpoints for dropdowns
@router.get("/departments", response_model=List[DepartmentResponse])
def get_departments(db: Session = Depends(get_db)):
    """
    List active departments that have entries in a published timetable.
    """
    published_departments = db.query(TimetableEntry.department).join(
        Timetable, Timetable.id == TimetableEntry.timetable_id
    ).filter(Timetable.is_published == True).distinct()
    depts = db.query(Department).filter(
        Department.is_active == True,
        Department.code.in_(published_departments)
    ).order_by(Department.code).all()
    return depts

@router.get("/academic-structures", response_model=List[AcademicStructureResponse])
def get_academic_structures(
    department: Optional[str] = None,
    db: Session = Depends(get_db)
):
    """List only combinations with entries in a published timetable."""
    query = db.query(
        func.min(TimetableEntry.id).label("id"),
        TimetableEntry.department.label("department_code"),
        func.min(Timetable.academic_year).label("academic_year"),
        TimetableEntry.year,
        TimetableEntry.semester,
        TimetableEntry.section
    ).join(Timetable, Timetable.id == TimetableEntry.timetable_id).filter(
        Timetable.is_published == True
    )
    if department:
        query = query.filter(TimetableEntry.department == department.strip().upper())
    structures = query.group_by(
        TimetableEntry.department,
        TimetableEntry.year,
        TimetableEntry.semester,
        TimetableEntry.section
    ).order_by(
        TimetableEntry.year,
        TimetableEntry.semester,
        TimetableEntry.section
    ).all()
    return [dict(structure._mapping) for structure in structures]
