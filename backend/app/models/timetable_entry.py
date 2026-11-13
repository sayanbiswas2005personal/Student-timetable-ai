from sqlalchemy import Column, Integer, String, ForeignKey, Index
from sqlalchemy.orm import relationship
from backend.app.database.session import Base

class TimetableEntry(Base):
    __tablename__ = "timetable_entries"

    id = Column(Integer, primary_key=True, index=True)
    timetable_id = Column(Integer, ForeignKey("timetables.id", ondelete="CASCADE"), nullable=False, index=True)
    
    day = Column(String(20), nullable=False, index=True) # "MONDAY", "TUESDAY", etc.
    start_time = Column(String(10), nullable=False, index=True) # "09:30" (24-hour HH:MM)
    end_time = Column(String(10), nullable=False, index=True) # "10:25" (24-hour HH:MM)
    
    department = Column(String(50), nullable=False, index=True) # e.g. "CSE", "BCA", "MCA"
    year = Column(Integer, nullable=False, index=True) # 1, 2, 3, 4
    semester = Column(Integer, nullable=False, index=True) # 1 to 8
    section = Column(String(20), nullable=False, index=True) # "A", "B", etc.
    
    subject_code = Column(String(50), nullable=True) # e.g. "CSE11104"
    subject = Column(String(200), nullable=False) # e.g. "Data Structures and Algorithms"
    faculty = Column(String(200), nullable=True) # e.g. "Dr. BODHI CHAKRABORTY"
    room = Column(String(100), nullable=True) # e.g. "AU6-LAB-4001A" or "Room 204"
    notes = Column(String(255), nullable=True)

    timetable = relationship("Timetable", back_populates="entries")

    __table_args__ = (
        Index("ix_entries_search", "department", "year", "semester", "section", "day"),
    )

    def __repr__(self):
        return f"<TimetableEntry {self.department} Sem{self.semester}-{self.section} {self.day} {self.start_time}-{self.end_time}: {self.subject}>"

