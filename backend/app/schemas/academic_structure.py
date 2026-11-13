from pydantic import BaseModel, ConfigDict
from typing import Optional

class AcademicStructureCreate(BaseModel):
    department_code: str
    academic_year: str
    year: int
    semester: int
    section: str

class AcademicStructureResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    department_code: str
    academic_year: str
    year: int
    semester: int
    section: str
