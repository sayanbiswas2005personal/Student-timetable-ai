import enum
from datetime import datetime, date, timezone
from sqlalchemy import Column, Integer, String, Date, DateTime, Enum, ForeignKey
from sqlalchemy.orm import relationship
from backend.app.database.session import Base

class OverrideType(str, enum.Enum):
    CANCELLED = "CANCELLED"
    SUBSTITUTION = "SUBSTITUTION"
    RESCHEDULED = "RESCHEDULED"
    SPECIAL_CLASS = "SPECIAL_CLASS"
    HOLIDAY = "HOLIDAY"

class ScheduleOverride(Base):
    __tablename__ = "schedule_overrides"

    id = Column(Integer, primary_key=True, index=True)
    date = Column(Date, nullable=False, index=True)
    department = Column(String(50), nullable=False, index=True)
    year = Column(Integer, nullable=False)
    semester = Column(Integer, nullable=False)
    section = Column(String(20), nullable=False)
    
    override_type = Column(Enum(OverrideType), default=OverrideType.CANCELLED, nullable=False)
    start_time = Column(String(10), nullable=True) # HH:MM (applies to a slot or all day if null)
    end_time = Column(String(10), nullable=True) # HH:MM
    
    subject = Column(String(200), nullable=True)
    faculty = Column(String(200), nullable=True)
    room = Column(String(100), nullable=True)
    reason = Column(String(300), nullable=False)
    
    created_by_user_id = Column(Integer, ForeignKey("users.id"), nullable=True)
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc), nullable=False)

    created_by = relationship("User", foreign_keys=[created_by_user_id])

    def __repr__(self):
        return f"<ScheduleOverride {self.date} {self.override_type} {self.department}-{self.section}: {self.reason}>"

