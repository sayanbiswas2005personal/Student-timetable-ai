from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from typing import List

from backend.app.database.session import get_db
from backend.app.core.dependencies import require_admin
from backend.app.models.user import User
from backend.app.models.department import Department
from backend.app.models.academic_structure import AcademicStructure
from backend.app.schemas.department import DepartmentCreate, DepartmentUpdate, DepartmentResponse
from backend.app.schemas.academic_structure import AcademicStructureCreate, AcademicStructureResponse

router = APIRouter(prefix="/admin", tags=["Academic Management"])

# Departments
@router.get("/departments", response_model=List[DepartmentResponse])
def get_all_departments(db: Session = Depends(get_db), current_user: User = Depends(require_admin)):
    return db.query(Department).order_by(Department.code).all()

@router.post("/departments", response_model=DepartmentResponse)
def create_department(dept_in: DepartmentCreate, db: Session = Depends(get_db), current_user: User = Depends(require_admin)):
    code_clean = dept_in.code.strip().upper()
    existing = db.query(Department).filter(Department.code == code_clean).first()
    if existing:
        raise HTTPException(status_code=400, detail="Department with this code already exists.")
    dept = Department(name=dept_in.name.strip(), code=code_clean, is_active=True)
    db.add(dept)
    db.commit()
    db.refresh(dept)
    return dept

@router.delete("/departments/{id}")
def delete_department(id: int, db: Session = Depends(get_db), current_user: User = Depends(require_admin)):
    dept = db.query(Department).filter(Department.id == id).first()
    if not dept:
        raise HTTPException(status_code=404, detail="Department not found.")
    db.delete(dept)
    db.commit()
    return {"message": "Department deleted."}

# Academic Structures
@router.get("/academic-structures", response_model=List[AcademicStructureResponse])
def get_all_structures(db: Session = Depends(get_db), current_user: User = Depends(require_admin)):
    return db.query(AcademicStructure).order_by(
        AcademicStructure.department_code,
        AcademicStructure.year,
        AcademicStructure.semester,
        AcademicStructure.section
    ).all()

@router.post("/academic-structures", response_model=AcademicStructureResponse)
def create_structure(struct_in: AcademicStructureCreate, db: Session = Depends(get_db), current_user: User = Depends(require_admin)):
    existing = db.query(AcademicStructure).filter(
        AcademicStructure.department_code == struct_in.department_code.strip().upper(),
        AcademicStructure.academic_year == struct_in.academic_year.strip(),
        AcademicStructure.semester == struct_in.semester,
        AcademicStructure.section == struct_in.section.strip().upper()
    ).first()
    if existing:
        raise HTTPException(status_code=400, detail="Academic structure section already exists.")
    struct = AcademicStructure(
        department_code=struct_in.department_code.strip().upper(),
        academic_year=struct_in.academic_year.strip(),
        year=struct_in.year,
        semester=struct_in.semester,
        section=struct_in.section.strip().upper()
    )
    db.add(struct)
    db.commit()
    db.refresh(struct)
    return struct

@router.delete("/academic-structures/{id}")
def delete_structure(id: int, db: Session = Depends(get_db), current_user: User = Depends(require_admin)):
    struct = db.query(AcademicStructure).filter(AcademicStructure.id == id).first()
    if not struct:
        raise HTTPException(status_code=404, detail="Structure not found.")
    db.delete(struct)
    db.commit()
    return {"message": "Structure deleted."}

