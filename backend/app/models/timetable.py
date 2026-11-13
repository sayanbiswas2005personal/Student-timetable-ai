import enum
from datetime import datetime, date, timezone
from sqlalchemy import Column, Integer, String, Boolean, DateTime, Date, Text, Enum, ForeignKey
from sqlalchemy.orm import relationship
from backend.app.database.session import Base

class ProcessingStatus(str, enum.Enum):
    PENDING = "PENDING"
    PROCESSING = "PROCESSING"
    EXTRACTED = "EXTRACTED"
    ERROR = "ERROR"
    PUBLISHED = "PUBLISHED"

class Timetable(Base):
    __tablename__ = "timetables"

    id = Column(Integer, primary_key=True, index=True)
    original_filename = Column(String(255), nullable=False)
    file_path = Column(String(500), nullable=False)
    file_size_bytes = Column(Integer, nullable=True)
    uploaded_at = Column(DateTime, default=lambda: datetime.now(timezone.utc), nullable=False)
    
    academic_year = Column(String(50), nullable=True) # e.g. "2026-2027"
    institution = Column(String(150), default="Adamas University")
    department = Column(String(50), nullable=True) # Optional specific department or ALL
    valid_from = Column(Date, nullable=True)
    valid_until = Column(Date, nullable=True)
    
    processing_status = Column(Enum(ProcessingStatus), default=ProcessingStatus.PENDING, nullable=False)
    processing_error = Column(Text, nullable=True)
    extraction_provider = Column(String(50), nullable=True) # "GEMINI_API" or "LAYOUT_PARSER_DEMO"
    
    extracted_json = Column(Text, nullable=True) # Normalized JSON timetable string
    is_published = Column(Boolean, default=False, nullable=False)
    version = Column(Integer, default=1, nullable=False)
    
    approved_by_user_id = Column(Integer, ForeignKey("users.id"), nullable=True)
    approved_at = Column(DateTime, nullable=True)

    # Relationships
    entries = relationship("TimetableEntry", back_populates="timetable", cascade="all, delete-orphan")
    approved_by = relationship("User", foreign_keys=[approved_by_user_id])

    def __repr__(self):
        return f"<Timetable {self.id} v{self.version} ({self.processing_status})>"

