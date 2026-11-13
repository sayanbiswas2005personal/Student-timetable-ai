import pytest
from datetime import date
from backend.app.services.timetable_engine import TimetableEngine
from backend.app.models.timetable import Timetable
from backend.app.models.timetable_entry import TimetableEntry
from backend.app.models.schedule_override import ScheduleOverride, OverrideType

def test_department_and_section_matching(db_session):
    """Req 3: Verify searching by Department, Year, Semester, and Section."""
    engine = TimetableEngine(db_session)
    
    # Existing section CSE Yr 3 Sem 7 Sec A on Monday 10:00 (during Class 1)
    res = engine.check_schedule("CSE", 3, 7, "A", simulated_date_str="2026-11-02", simulated_time_str="10:00")
    assert res.current_status == "SCHEDULED_CLASS_IN_PROGRESS"
    assert res.current_class is not None
    assert res.current_class.subject == "Database Management Systems"
    assert res.department == "CSE"
    assert res.section == "A"

    # Non-existing section Sec Z
    res_z = engine.check_schedule("CSE", 3, 7, "Z", simulated_date_str="2026-11-02", simulated_time_str="10:00")
    assert res_z.current_status == "NO_CLASSES_TODAY"

def test_current_time_class_matching(db_session):
    """Req 4: start_time <= current_time < end_time."""
    engine = TimetableEngine(db_session)

    # Class 1 is 09:30 to 10:25
    # Exact start boundary: 09:30 should match
    res_start = engine.check_schedule("CSE", 3, 7, "A", simulated_date_str="2026-11-02", simulated_time_str="09:30")
    assert res_start.current_status == "SCHEDULED_CLASS_IN_PROGRESS"
    assert res_start.current_class.subject == "Database Management Systems"

    # Middle of class: 10:00 should match
    res_mid = engine.check_schedule("CSE", 3, 7, "A", simulated_date_str="2026-11-02", simulated_time_str="10:00")
    assert res_mid.current_status == "SCHEDULED_CLASS_IN_PROGRESS"
    assert res_mid.current_class.subject == "Database Management Systems"

    # End boundary: 10:25 (start of gap/next class) - 10:25 should NOT match Class 1
    res_end = engine.check_schedule("CSE", 3, 7, "A", simulated_date_str="2026-11-02", simulated_time_str="10:25")
    # At 10:25, Class 1 has ended, Class 2 starts at 10:30, so 10:25 is a 5-minute break/free period
    assert res_end.current_status == "FREE_PERIOD"

def test_free_period_detection(db_session):
    """Req 5: Detect free periods between scheduled classes."""
    engine = TimetableEngine(db_session)

    # Monday has Class 2 ending at 11:25 and Class 3 starting at 13:30.
    # At 12:00, it is a free period!
    res = engine.check_schedule("CSE", 3, 7, "A", simulated_date_str="2026-11-02", simulated_time_str="12:00")
    assert res.current_status == "FREE_PERIOD"
    assert res.current_class is None
    assert "Free Period" in res.status_label

def test_next_class_calculation(db_session):
    """Req 6: Next-class calculation during free periods or between classes."""
    engine = TimetableEngine(db_session)

    # At 08:30 (before college hours), next class should be the first class (09:30 DBMS)
    res_before = engine.check_schedule("CSE", 3, 7, "A", simulated_date_str="2026-11-02", simulated_time_str="08:30")
    assert res_before.current_status == "BEFORE_COLLEGE_HOURS"
    assert res_before.next_class is not None
    assert res_before.next_class.start_time == "09:30"
    assert res_before.next_class.subject == "Database Management Systems"

    # At 10:00 (during Class 1), next class should be Class 2 (10:30 Cloud Computing)
    res_during = engine.check_schedule("CSE", 3, 7, "A", simulated_date_str="2026-11-02", simulated_time_str="10:00")
    assert res_during.next_class is not None
    assert res_during.next_class.start_time == "10:30"
    assert res_during.next_class.subject == "Cloud Computing"

    # At 12:00 (free period), next class should be Class 3 (13:30 Cyber Security)
    res_free = engine.check_schedule("CSE", 3, 7, "A", simulated_date_str="2026-11-02", simulated_time_str="12:00")
    assert res_free.next_class is not None
    assert res_free.next_class.start_time == "13:30"
    assert res_free.next_class.subject == "Cyber Security"

def test_timezone_handling(db_session):
    """Req 7: Verify institutional campus timezone conversion."""
    engine = TimetableEngine(db_session, timezone_str="Asia/Kolkata")
    # Campus local time fetching
    date_val, time_val, day_name, time_str, is_sim = engine.get_current_campus_time()
    assert day_name in ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"]
    assert len(time_str) == 5 # HH:MM

def test_holiday_and_schedule_overrides(db_session):
    """Req 8: Holiday, class cancellation, and faculty substitution overrides."""
    engine = TimetableEngine(db_session)
    target_date = date(2026, 11, 2)

    # 1. Full-Day Holiday Override
    holiday = ScheduleOverride(
        date=target_date,
        department="CSE",
        year=3,
        semester=7,
        section="A",
        override_type=OverrideType.HOLIDAY,
        reason="Diwali Campus Holiday"
    )
    db_session.add(holiday)
    db_session.commit()

    res_hol = engine.check_schedule("CSE", 3, 7, "A", simulated_date_str="2026-11-02", simulated_time_str="10:00")
    assert res_hol.current_status == "HOLIDAY"
    assert "Diwali" in res_hol.message

    # Clean up holiday
    db_session.delete(holiday)
    db_session.commit()

    # 2. Class Cancellation Override for 09:30 to 10:25
    cancellation = ScheduleOverride(
        date=target_date,
        department="CSE",
        year=3,
        semester=7,
        section="A",
        override_type=OverrideType.CANCELLED,
        start_time="09:30",
        end_time="10:25",
        reason="Faculty attending academic conference"
    )
    db_session.add(cancellation)
    db_session.commit()

    res_cancel = engine.check_schedule("CSE", 3, 7, "A", simulated_date_str="2026-11-02", simulated_time_str="10:00")
    assert res_cancel.current_status == "CLASS_CANCELLED"
    assert "conference" in res_cancel.message

    # Clean up cancellation
    db_session.delete(cancellation)
    db_session.commit()

    # 3. Faculty Substitution Override for 09:30 to 10:25
    sub = ScheduleOverride(
        date=target_date,
        department="CSE",
        year=3,
        semester=7,
        section="A",
        override_type=OverrideType.SUBSTITUTION,
        start_time="09:30",
        end_time="10:25",
        faculty="Dr. Substitute Sharma",
        reason="Routine faculty replacement"
    )
    db_session.add(sub)
    db_session.commit()

    res_sub = engine.check_schedule("CSE", 3, 7, "A", simulated_date_str="2026-11-02", simulated_time_str="10:00")
    assert res_sub.current_status == "FACULTY_SUBSTITUTION"
    assert "Dr. Substitute Sharma" in res_sub.current_class.faculty
    assert "replacement" in res_sub.message

    db_session.delete(sub)
    db_session.commit()

def test_expired_timetable_detection(db_session):
    """Req 9: Expired timetable warning."""
    engine = TimetableEngine(db_session)
    
    # Query for date in 2029 (timetable expired June 2027)
    res = engine.check_schedule("CSE", 3, 7, "A", simulated_date_str="2029-01-15", simulated_time_str="10:00")
    assert res.current_status == "TIMETABLE_EXPIRED"
    assert "needs updating" in res.message

def test_conflicting_timetable_entries(db_session):
    """Req 11: Multiple conflicting timetable entries at the same time slot."""
    # Find active timetable
    tt = db_session.query(Timetable).filter(Timetable.is_published == True).first()
    
    # Add a conflicting overlapping entry on Monday 09:30 to 10:25 for the same section
    conflict_entry = TimetableEntry(
        timetable_id=tt.id,
        day="MONDAY",
        start_time="09:30",
        end_time="10:25",
        department="CSE",
        year=3,
        semester=7,
        section="A",
        subject_code="CS799",
        subject="Conflicting Advanced AI Lab",
        faculty="Prof. Conflict",
        room="301"
    )
    db_session.add(conflict_entry)
    db_session.commit()

    engine = TimetableEngine(db_session)
    res = engine.check_schedule("CSE", 3, 7, "A", simulated_date_str="2026-11-02", simulated_time_str="10:00")
    
    assert res.current_status == "TIMETABLE_CONFLICT"
    assert res.has_conflict is True
    assert len(res.conflicting_entries) == 2
    assert "Warning: Multiple conflicting classes" in res.message

    db_session.delete(conflict_entry)
    db_session.commit()

