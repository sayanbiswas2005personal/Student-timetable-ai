from sqlalchemy import Column, Integer, String, ForeignKey, DateTime, Time, Boolean
from sqlalchemy.orm import relationship
from sqlalchemy.sql import func
from app.models.base import Base

class Subject(Base):
    __tablename__ = "subjects"
    id = Column(Integer, primary_key=True, index=True)
    code = Column(String, unique=True, index=True)
    name = Column(String)
    is_lab = Column(Boolean, default=False)

class Faculty(Base):
    __tablename__ = "faculty"
    id = Column(Integer, primary_key=True, index=True)
    name = Column(String, unique=True, index=True)

class Room(Base):
    __tablename__ = "rooms"
    id = Column(Integer, primary_key=True, index=True)
    name = Column(String, unique=True, index=True)

class TimetableSource(Base):
    __tablename__ = "timetable_sources"
    id = Column(Integer, primary_key=True, index=True)
    filename = Column(String)
    uploaded_at = Column(DateTime(timezone=True), server_default=func.now())
    status = Column(String, default="PENDING")

class TimetableEntry(Base):
    __tablename__ = "timetable_entries"
    id = Column(Integer, primary_key=True, index=True)
    section_id = Column(Integer, ForeignKey("sections.id"), index=True)
    subject_id = Column(Integer, ForeignKey("subjects.id"), nullable=True)
    faculty_id = Column(Integer, ForeignKey("faculty.id"), nullable=True)
    room_id = Column(Integer, ForeignKey("rooms.id"), nullable=True)
    day_of_week = Column(Integer, index=True) # 0 = Monday, 6 = Sunday
    start_time = Column(Time, index=True)
    end_time = Column(Time, index=True)
    period_start = Column(Integer)
    period_end = Column(Integer)
    source_id = Column(Integer, ForeignKey("timetable_sources.id"), nullable=True)
    raw_text = Column(String, nullable=True)
    group_name = Column(String, nullable=True) # For electives/groups

    section = relationship("Section")
    subject = relationship("Subject")
    faculty = relationship("Faculty")
    room = relationship("Room")
    source = relationship("TimetableSource")
