from backend.app.schemas.user import UserLogin, TokenResponse, UserCreate, UserUpdate, UserResponse
from backend.app.schemas.timetable import (
    TimetableEntrySchema,
    NormalizedTimetableJSON,
    TimetableCreateResponse,
    TimetablePreviewResponse,
    TimetableListItem,
    TimetableUpdateRequest
)
from backend.app.schemas.search import FacultySearchQuery, FacultySearchResponse
from backend.app.schemas.department import DepartmentCreate, DepartmentUpdate, DepartmentResponse
from backend.app.schemas.academic_structure import AcademicStructureCreate, AcademicStructureResponse
from backend.app.schemas.schedule_override import ScheduleOverrideCreate, ScheduleOverrideResponse

__all__ = [
    "UserLogin",
    "TokenResponse",
    "UserCreate",
    "UserUpdate",
    "UserResponse",
    "TimetableEntrySchema",
    "NormalizedTimetableJSON",
    "TimetableCreateResponse",
    "TimetablePreviewResponse",
    "TimetableListItem",
    "TimetableUpdateRequest",
    "FacultySearchQuery",
    "FacultySearchResponse",
    "DepartmentCreate",
    "DepartmentUpdate",
    "DepartmentResponse",
    "AcademicStructureCreate",
    "AcademicStructureResponse",
    "ScheduleOverrideCreate",
    "ScheduleOverrideResponse"
]

