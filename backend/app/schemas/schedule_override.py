from pydantic import BaseModel, ConfigDict
from typing import Optional
from datetime import date, datetime
from backend.app.models.schedule_override import OverrideType

class ScheduleOverrideCreate(BaseModel):
    date: date
    department: str
    year: int
    semester: int
    section: str
    override_type: OverrideType
    start_time: Optional[str] = None
    end_time: Optional[str] = None
    subject: Optional[str] = None
    faculty: Optional[str] = None
    room: Optional[str] = None
    reason: str

class ScheduleOverrideResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    date: date
    department: str
    year: int
    semester: int
    section: str
    override_type: OverrideType
    start_time: Optional[str]
    end_time: Optional[str]
    subject: Optional[str]
    faculty: Optional[str]
    room: Optional[str]
    reason: str
    created_at: datetime
    created_by_user_id: Optional[int]
