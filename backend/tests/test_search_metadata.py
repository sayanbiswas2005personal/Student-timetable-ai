from backend.app.models.department import Department
from backend.app.models.timetable import ProcessingStatus, Timetable
from backend.app.models.timetable_entry import TimetableEntry


def test_search_metadata_only_lists_published_timetable_sections(client, db_session, faculty_token):
    db_session.add_all([
        Department(name="Bachelor of Computer Applications", code="BCA", is_active=True),
        Department(name="Electronics", code="ECE", is_active=True),
    ])
    published = Timetable(
        original_filename="published.pdf",
        file_path="/tmp/published.pdf",
        academic_year="2026-2027",
        department="ALL",
        processing_status=ProcessingStatus.PUBLISHED,
        is_published=True,
    )
    unpublished = Timetable(
        original_filename="unpublished.pdf",
        file_path="/tmp/unpublished.pdf",
        academic_year="2026-2027",
        department="CSE",
        processing_status=ProcessingStatus.PENDING,
        is_published=False,
    )
    db_session.add_all([published, unpublished])
    db_session.flush()
    db_session.add_all([
        TimetableEntry(
            timetable_id=published.id,
            day="MONDAY",
            start_time="09:30",
            end_time="10:25",
            department="BCA",
            year=1,
            semester=1,
            section="A",
            subject="Published BCA class",
        ),
        TimetableEntry(
            timetable_id=unpublished.id,
            day="MONDAY",
            start_time="09:30",
            end_time="10:25",
            department="CSE",
            year=2,
            semester=3,
            section="Z",
            subject="Unpublished CSE class",
        ),
    ])
    db_session.commit()

    departments = client.get("/api/search/departments")
    structures = client.get("/api/search/academic-structures")
    bca_structures = client.get("/api/search/academic-structures?department=BCA")
    schedule = client.get(
        "/api/search/current-class",
        params={
            "department": "BCA",
            "year": 1,
            "semester": 1,
            "section": "A",
            "simulated_date": "2026-11-02",
            "simulated_time": "10:00",
        },
        headers={"Authorization": f"Bearer {faculty_token}"},
    )

    assert departments.status_code == 200
    assert {department["code"] for department in departments.json()} == {"CSE", "BCA"}
    assert structures.status_code == 200
    assert any(
        structure["department_code"] == "BCA"
        and structure["year"] == 1
        and structure["semester"] == 1
        and structure["section"] == "A"
        for structure in structures.json()
    )
    assert not any(structure["section"] == "Z" for structure in structures.json())
    assert [structure["section"] for structure in bca_structures.json()] == ["A"]
    assert schedule.status_code == 200
    assert schedule.json()["current_class"]["subject"] == "Published BCA class"
