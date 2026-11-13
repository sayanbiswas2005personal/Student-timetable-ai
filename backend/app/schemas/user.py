from pydantic import BaseModel, ConfigDict, field_validator
from typing import Optional
from datetime import datetime
import re
from backend.app.models.user import UserRole

class UserLogin(BaseModel):
    email: str
    password: str

class TokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user: "UserResponse"

class UserCreate(BaseModel):
    name: str
    email: str
    password: str
    role: UserRole = UserRole.FACULTY
    department: Optional[str] = None

    @field_validator("email")
    @classmethod
    def validate_email(cls, v: str) -> str:
        v = v.strip().lower()
        if not re.match(r"^[^@]+@[^@]+\.[^@]+$", v):
            raise ValueError(f"Invalid email address: {v}")
        return v

class UserUpdate(BaseModel):
    name: Optional[str] = None
    role: Optional[UserRole] = None
    department: Optional[str] = None
    is_active: Optional[bool] = None
    password: Optional[str] = None

class UserResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    name: str
    email: str
    role: UserRole
    department: Optional[str]
    is_active: bool
    created_at: datetime

TokenResponse.model_rebuild()
