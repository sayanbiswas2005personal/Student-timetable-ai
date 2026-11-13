import pytest
from datetime import datetime, date, timedelta, timezone
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool
from fastapi.testclient import TestClient

from backend.app.database.session import Base, get_db
from backend.app.models.user import User, UserRole
from backend.app.models.department import Department
from backend.app.models.academic_structure import AcademicStructure
from backend.app.models.timetable import Timetable, ProcessingStatus
from backend.app.models.timetable_entry import TimetableEntry
from backend.app.models.schedule_override import ScheduleOverride, OverrideType
from backend.app.core.security import hash_password, create_access_token
from backend.app.main import app

@pytest.fixture
def db_session():
    # Fresh in-memory database with StaticPool for each test
    engine = create_engine(
        "sqlite:///:memory:",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool
    )
    Base.metadata.create_all(bind=engine)
    TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
    session = TestingSessionLocal()

    try:
        # Seed test admin & faculty
        admin = User(
            name="Test Admin",
            email="testadmin@studentai.com",
            password_hash=hash_password("adminpass"),
            role=UserRole.ADMIN,
            is_active=True
        )
        faculty = User(
            name="Test Faculty",
            email="testfaculty@studentai.com",
            password_hash=hash_password("facultypass"),
            role=UserRole.FACULTY,
            department="CSE",
            is_active=True
        )
        session.add_all([admin, faculty])
        session.commit()

        # Seed CSE Department
        dept = Department(name="Computer Science", code="CSE", is_active=True)
        session.add(dept)

        # Seed Active Timetable
        tt = Timetable(
            original_filename="test_timetable.pdf",
            file_path="/tmp/test_timetable.pdf",
            academic_year="2026-2027",
            institution="Adamas University",
            department="CSE",
            valid_from=date(2026, 7, 1),
            valid_until=date(2027, 6, 30),
            processing_status=ProcessingStatus.PUBLISHED,
            is_published=True,
            version=1
        )
        session.add(tt)
        session.commit()
        session.refresh(tt)

        # Seed Timetable entries for Monday
        # Entry 1: 09:30 - 10:25 (Class 1)
        # Entry 2: 10:30 - 11:25 (Class 2)
        # Gap: 11:25 - 13:30 (Free period)
        # Entry 3: 13:30 - 14:25 (Class 3)
        # Entry 4: 14:30 - 15:25 (Class 4)
        entries = [
            TimetableEntry(
                timetable_id=tt.id,
                day="MONDAY",
                start_time="09:30",
                end_time="10:25",
                department="CSE",
                year=3,
                semester=7,
                section="A",
                subject_code="CS701",
                subject="Database Management Systems",
                faculty="Professor X",
                room="204"
            ),
            TimetableEntry(
                timetable_id=tt.id,
                day="MONDAY",
                start_time="10:30",
                end_time="11:25",
                department="CSE",
                year=3,
                semester=7,
                section="A",
                subject_code="CS702",
                subject="Cloud Computing",
                faculty="Dr. Y",
                room="205"
            ),
            TimetableEntry(
                timetable_id=tt.id,
                day="MONDAY",
                start_time="13:30",
                end_time="14:25",
                department="CSE",
                year=3,
                semester=7,
                section="A",
                subject_code="CS703",
                subject="Cyber Security",
                faculty="Dr. Z",
                room="206"
            ),
            TimetableEntry(
                timetable_id=tt.id,
                day="MONDAY",
                start_time="14:30",
                end_time="15:25",
                department="CSE",
                year=3,
                semester=7,
                section="A",
                subject_code="CS704",
                subject="Machine Learning",
                faculty="Dr. W",
                room="207"
            ),
        ]
        session.add_all(entries)
        session.commit()

        yield session
    finally:
        session.close()
        Base.metadata.drop_all(bind=engine)

@pytest.fixture
def client(db_session):
    def override_get_db():
        try:
            yield db_session
        finally:
            pass

    app.dependency_overrides[get_db] = override_get_db
    test_client = TestClient(app)
    yield test_client
    app.dependency_overrides.clear()

@pytest.fixture
def admin_token(db_session):
    return create_access_token(data={"sub": "testadmin@studentai.com", "role": "ADMIN", "uid": 1})

@pytest.fixture
def faculty_token(db_session):
    return create_access_token(data={"sub": "testfaculty@studentai.com", "role": "FACULTY", "uid": 2})
