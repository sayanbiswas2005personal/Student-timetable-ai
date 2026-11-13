from pydantic import BaseModel
from typing import Optional, List
from backend.app.schemas.timetable import TimetableEntrySchema

class FacultySearchQuery(BaseModel):
    department: str
    year: int
    semester: int
    section: str
    simulated_time: Optional[str] = None # Optional "HH:MM" e.g. "14:15"
    simulated_date: Optional[str] = None # Optional "YYYY-MM-DD" e.g. "2026-11-02"

class FacultySearchResponse(BaseModel):
    current_status: str
    status_label: str
    status_badge_color: str # "green", "blue", "yellow", "red", "gray"
    message: str
    disclaimer: str = (
        "The timetable only establishes expected class schedules. It does not establish "
        "an individual student's actual attendance. Never automatically label a student as "
        "bunking or recommend punishment based solely on a timetable."
    )
    
    # Query Context
    department: str
    year: int
    semester: int
    section: str
    query_date: str
    query_day: str
    query_time: str
    is_simulated: bool = False
    
    # Match Details
    current_class: Optional[TimetableEntrySchema] = None
    next_class: Optional[TimetableEntrySchema] = None
    override_applied: Optional[dict] = None
    
    # Today's complete schedule
    today_schedule: List[TimetableEntrySchema] = []
    
    # Warnings or conflicts
    warnings: List[str] = []
    has_conflict: bool = False
    conflicting_entries: List[TimetableEntrySchema] = []

