from pydantic import BaseModel, ConfigDict, Field, field_validator
from typing import List, Optional, Any
from datetime import date, datetime
import re

DAYS_OF_WEEK = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"]

class TimetableEntrySchema(BaseModel):
    day: str = Field(..., description="Day of the week (e.g. MONDAY, TUESDAY, etc.)")
    start_time: str = Field(..., description="Start time in 24-hr format (HH:MM, e.g. '09:30')")
    end_time: str = Field(..., description="End time in 24-hr format (HH:MM, e.g. '10:25')")
    department: str = Field(..., description="Department or program code, e.g. 'CSE', 'BCA'")
    year: int = Field(..., ge=1, le=6, description="Academic year (1, 2, 3, 4)")
    semester: int = Field(..., ge=1, le=12, description="Academic semester (1 to 8)")
    section: str = Field(..., description="Section identifier, e.g. 'A', 'B', 'V A'")
    subject_code: Optional[str] = Field(None, description="Course or subject code, e.g. 'CSE11104'")
    subject: str = Field(..., description="Course name or subject description")
    faculty: Optional[str] = Field(None, description="Assigned faculty name / instructor")
    room: Optional[str] = Field(None, description="Assigned room / lab, e.g. 'AU6-LAB-4001A'")
    notes: Optional[str] = Field(None, description="Any special notes or lab grouping")

    @field_validator("day")
    @classmethod
    def normalize_day(cls, v: str) -> str:
        clean = v.strip().upper()
        mapping = {
            "MO": "MONDAY", "MON": "MONDAY", "MONDAY": "MONDAY",
            "TU": "TUESDAY", "TUE": "TUESDAY", "TUESDAY": "TUESDAY",
            "WE": "WEDNESDAY", "WED": "WEDNESDAY", "WEDNESDAY": "WEDNESDAY",
            "TH": "THURSDAY", "THU": "THURSDAY", "THURSDAY": "THURSDAY",
            "FR": "FRIDAY", "FRI": "FRIDAY", "FRIDAY": "FRIDAY",
            "SA": "SATURDAY", "SAT": "SATURDAY", "SATURDAY": "SATURDAY",
            "SU": "SUNDAY", "SUN": "SUNDAY", "SUNDAY": "SUNDAY",
        }
        if clean in mapping:
            return mapping[clean]
        raise ValueError(f"Invalid day: '{v}'. Must be one of Monday through Sunday.")

    @field_validator("start_time", "end_time")
    @classmethod
    def validate_time(cls, v: str) -> str:
        v = v.strip()
        # accept H:MM or HH:MM
        match = re.match(r"^(\d{1,2}):(\d{2})$", v)
        if not match:
            raise ValueError(f"Invalid time format: '{v}'. Expected HH:MM in 24-hour time.")
        h, m = int(match.group(1)), int(match.group(2))
        if not (0 <= h <= 23 and 0 <= m <= 59):
            raise ValueError(f"Time out of bounds: '{v}'.")
        return f"{h:02d}:{m:02d}"

class NormalizedTimetableJSON(BaseModel):
    schema_version: str = Field("1.0", description="Schema version")
    institution: str = Field("Adamas University", description="Institution name")
    timezone: str = Field("Asia/Kolkata", description="IANA timezone name")
    academic_year: str = Field("2026-2027", description="Academic year")
    department: Optional[str] = Field(None, description="Primary department code or ALL")
    valid_from: Optional[str] = Field("2026-07-01", description="Effective start date YYYY-MM-DD")
    valid_until: Optional[str] = Field("2027-06-30", description="Effective end date YYYY-MM-DD")
    entries: List[TimetableEntrySchema] = Field(default_factory=list, description="Extracted timetable entries")

class TimetableCreateResponse(BaseModel):
    id: int
    original_filename: str
    processing_status: str
    message: str

class TimetablePreviewResponse(BaseModel):
    id: int
    original_filename: str
    academic_year: Optional[str]
    institution: Optional[str]
    processing_status: str
    processing_error: Optional[str]
    extraction_provider: Optional[str]
    is_published: bool
    version: int
    total_entries: int
    data: Optional[NormalizedTimetableJSON]
    validation_errors: List[str] = []
    validation_warnings: List[str] = []

class TimetableListItem(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    original_filename: str
    academic_year: Optional[str]
    institution: Optional[str]
    uploaded_at: datetime
    processing_status: str
    is_published: bool
    version: int
    total_entries: int
    extraction_provider: Optional[str]

class TimetableUpdateRequest(BaseModel):
    academic_year: Optional[str] = None
    valid_from: Optional[str] = None
    valid_until: Optional[str] = None
    entries: List[TimetableEntrySchema]
