from sqlalchemy import Column, Integer, String, UniqueConstraint
from backend.app.database.session import Base

class AcademicStructure(Base):
    __tablename__ = "academic_structures"

    id = Column(Integer, primary_key=True, index=True)
    department_code = Column(String(20), index=True, nullable=False)
    academic_year = Column(String(20), index=True, nullable=False) # e.g. "2026-2027"
    year = Column(Integer, nullable=False) # 1, 2, 3, 4
    semester = Column(Integer, nullable=False) # 1 to 8
    section = Column(String(20), nullable=False) # "A", "B", "C", etc.

    __table_args__ = (
        UniqueConstraint('department_code', 'academic_year', 'semester', 'section', name='uq_academic_structure'),
    )

    def __repr__(self):
        return f"<AcademicStructure {self.department_code} Yr{self.year} Sem{self.semester} Sec{self.section}>"

