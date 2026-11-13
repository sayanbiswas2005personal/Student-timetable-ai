from collections import Counter
import logging
from pathlib import Path
from sqlalchemy.orm import Session
from backend.app.database.session import engine, Base, SessionLocal
from backend.app.models.user import User, UserRole
from backend.app.models.department import Department
from backend.app.models.academic_structure import AcademicStructure
from backend.app.models.timetable import Timetable, ProcessingStatus
from backend.app.models.timetable_entry import TimetableEntry
from backend.app.core.security import hash_password
from backend.app.services.pdf_extractor import extract_timetable_data
import json

logger = logging.getLogger("studentai.init_db")

def init_db(db: Session = None):
    """
    Initializes database tables and creates default admin, faculty, and departments.
    """
    Base.metadata.create_all(bind=engine)
    
    close_session = False
    if db is None:
        db = SessionLocal()
        close_session = True

    try:
        # 1. Seed Default Admin
        admin = db.query(User).filter(User.email == "admin@studentai.com").first()
        if not admin:
            admin = User(
                name="System Administrator",
                email="admin@studentai.com",
                password_hash=hash_password("admin123"),
                role=UserRole.ADMIN,
                department="Administration",
                is_active=True
            )
            db.add(admin)
            db.commit()
            db.refresh(admin)
            logger.info("Created default admin: admin@studentai.com (password: admin123)")

        # 2. Seed Default Faculty
        faculty = db.query(User).filter(User.email == "faculty@studentai.com").first()
        if not faculty:
            faculty = User(
                name="Prof. Bodhi Chakraborty",
                email="faculty@studentai.com",
                password_hash=hash_password("faculty123"),
                role=UserRole.FACULTY,
                department="CSE",
                is_active=True
            )
            db.add(faculty)
            db.commit()
            logger.info("Created default faculty: faculty@studentai.com (password: faculty123)")

        # 3. Seed Departments
        departments_data = [
            {"code": "CSE", "name": "Computer Science & Engineering"},
            {"code": "BCA", "name": "Bachelor of Computer Applications"},
            {"code": "MCA", "name": "Master of Computer Applications"},
            {"code": "ECE", "name": "Electronics & Communication Engineering"},
        ]
        for dept_dict in departments_data:
            existing = db.query(Department).filter(Department.code == dept_dict["code"]).first()
            if not existing:
                dept = Department(code=dept_dict["code"], name=dept_dict["name"], is_active=True)
                db.add(dept)
        db.commit()

        # 4. Seed Academic Structures
        structures = [
            # CSE
            ("CSE", "2026-2027", 1, 1, "A"), ("CSE", "2026-2027", 1, 1, "B"),
            ("CSE", "2026-2027", 2, 3, "A"), ("CSE", "2026-2027", 2, 3, "B"),
            ("CSE", "2026-2027", 2, 3, "C"), ("CSE", "2026-2027", 2, 3, "D"),
            ("CSE", "2026-2027", 2, 3, "E"),
            ("CSE", "2026-2027", 3, 5, "A"), ("CSE", "2026-2027", 3, 5, "B"),
            ("CSE", "2026-2027", 3, 5, "C"), ("CSE", "2026-2027", 3, 5, "D"),
            ("CSE", "2026-2027", 4, 7, "A"), ("CSE", "2026-2027", 4, 7, "B"),
            ("CSE", "2026-2027", 4, 7, "C"), ("CSE", "2026-2027", 4, 7, "D"),
            # BCA
            ("BCA", "2026-2027", 1, 1, "A"), ("BCA", "2026-2027", 1, 1, "B"),
            ("BCA", "2026-2027", 2, 3, "A"), ("BCA", "2026-2027", 2, 3, "B"),
            ("BCA", "2026-2027", 3, 5, "A"),
            # MCA
            ("MCA", "2026-2027", 1, 1, "A"), ("MCA", "2026-2027", 2, 3, "A")
        ]
        for dept_code, ac_yr, yr, sem, sec in structures:
            existing = db.query(AcademicStructure).filter(
                AcademicStructure.department_code == dept_code,
                AcademicStructure.academic_year == ac_yr,
                AcademicStructure.semester == sem,
                AcademicStructure.section == sec
            ).first()
            if not existing:
                struct = AcademicStructure(
                    department_code=dept_code,
                    academic_year=ac_yr,
                    year=yr,
                    semester=sem,
                    section=sec
                )
                db.add(struct)
        db.commit()

        # 5. Populate the sample timetable with all PDF pages
        published_tt = db.query(Timetable).filter(Timetable.is_published == True).first()
        sample_pdf_path = Path("Section wise Timetable V15.pdf")
        demo_tt = db.query(Timetable).filter(
            Timetable.original_filename == sample_pdf_path.name,
            Timetable.extraction_provider == "LAYOUT_PARSER_DEMO"
        ).first()
        if sample_pdf_path.exists() and (not published_tt or demo_tt):
            should_publish_demo = not published_tt or (demo_tt is not None and demo_tt.is_published)
            logger.info("Initializing sample timetable from Section wise Timetable V15.pdf...")
            res = extract_timetable_data(str(sample_pdf_path), provider="layout_parser")
            if res.success and res.data and res.data.get("entries"):
                data = res.data
                if demo_tt:
                    tt = demo_tt
                else:
                    tt = Timetable(
                        original_filename=sample_pdf_path.name,
                        file_path=str(sample_pdf_path.resolve()),
                        file_size_bytes=sample_pdf_path.stat().st_size,
                        academic_year=data.get("academic_year", "2026-2027"),
                        institution=data.get("institution", "Adamas University"),
                        department=data.get("department", "ALL"),
                        processing_status=ProcessingStatus.PUBLISHED,
                        extraction_provider="LAYOUT_PARSER_DEMO",
                        extracted_json=json.dumps(data),
                        is_published=True,
                        version=1,
                        approved_by_user_id=admin.id
                    )
                    db.add(tt)
                    db.flush()

                tt.file_path = str(sample_pdf_path.resolve())
                tt.file_size_bytes = sample_pdf_path.stat().st_size
                tt.academic_year = data.get("academic_year", "2026-2027")
                tt.institution = data.get("institution", "Adamas University")
                tt.department = data.get("department", "ALL")
                tt.extracted_json = json.dumps(data)
                if should_publish_demo:
                    tt.processing_status = ProcessingStatus.PUBLISHED
                    tt.is_published = True

                existing_entries = db.query(TimetableEntry).filter(
                    TimetableEntry.timetable_id == tt.id
                ).all()
                entry_fields = (
                    "day", "start_time", "end_time", "department", "year",
                    "semester", "section", "subject_code", "subject", "faculty",
                    "room", "notes"
                )
                entry_key = lambda values: tuple(values.get(field) for field in entry_fields)
                existing_counts = Counter(
                    entry_key({field: getattr(entry, field) for field in entry_fields})
                    for entry in existing_entries
                )
                target_counts = Counter(entry_key(entry) for entry in data["entries"])
                inserted_counts = Counter()
                for entry_dict in data["entries"]:
                    key = entry_key(entry_dict)
                    if existing_counts[key] + inserted_counts[key] >= target_counts[key]:
                        continue
                    db.add(TimetableEntry(
                        timetable_id=tt.id,
                        **entry_dict
                    ))
                    inserted_counts[key] += 1

                for department_code in {entry["department"] for entry in data["entries"]}:
                    if not db.query(Department).filter(Department.code == department_code).first():
                        db.add(Department(
                            code=department_code,
                            name=department_code,
                            is_active=True
                        ))

                for dept_code, year, semester, section in {
                    (entry["department"], entry["year"], entry["semester"], entry["section"])
                    for entry in data["entries"]
                }:
                    if not db.query(AcademicStructure).filter(
                        AcademicStructure.department_code == dept_code,
                        AcademicStructure.academic_year == tt.academic_year,
                        AcademicStructure.semester == semester,
                        AcademicStructure.section == section
                    ).first():
                        db.add(AcademicStructure(
                            department_code=dept_code,
                            academic_year=tt.academic_year,
                            year=year,
                            semester=semester,
                            section=section
                        ))

                db.commit()
                logger.info(
                    f"Sample timetable seeded successfully with {len(data['entries'])} entries "
                    f"across {res.page_count} pages."
                )

    finally:
        if close_session:
            db.close()

if __name__ == "__main__":
    init_db()
