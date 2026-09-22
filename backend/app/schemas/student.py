from pydantic import BaseModel
from typing import Optional, List

class StudentLookupRequest(BaseModel):
    registration_number: str
    roll_number: str

class StudentInfo(BaseModel):
    registration_number: str
    roll_number: str
    first_name: Optional[str] = None
    last_name: Optional[str] = None
    program: str
    batch: int
    semester: int
    section: str

class ClassInfo(BaseModel):
    subject: str
    start_time: str
    end_time: str
    faculty: Optional[str] = None
    room: Optional[str] = None
    group_name: Optional[str] = None

class TodayScheduleItem(ClassInfo):
    is_current: bool = False
    is_completed: bool = False

class StudentLookupResponse(BaseModel):
    success: bool
    student: Optional[StudentInfo] = None
    current_class: Optional[ClassInfo] = None
    next_class: Optional[ClassInfo] = None
    today_schedule: List[TodayScheduleItem] = []
    gemini_explanation: Optional[str] = None
    error: Optional[str] = None
