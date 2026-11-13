import pytest
from backend.app.services.json_validator import validate_and_normalize_timetable_json

def test_valid_timetable_json():
    """Req 1: Valid timetable JSON data passes validation."""
    valid_data = {
        "schema_version": "1.0",
        "institution": "Adamas University",
        "timezone": "Asia/Kolkata",
        "academic_year": "2026-2027",
        "department": "CSE",
        "valid_from": "2026-07-01",
        "valid_until": "2027-06-30",
        "entries": [
            {
                "day": "MONDAY",
                "start_time": "14:00",
                "end_time": "15:00",
                "department": "CSE",
                "year": 3,
                "semester": 7,
                "section": "A",
                "subject_code": "CS701",
                "subject": "Database Management Systems",
                "faculty": "Professor X",
                "room": "204"
            }
        ]
    }
    result = validate_and_normalize_timetable_json(valid_data)
    assert result.is_valid is True
    assert len(result.errors) == 0
    assert len(result.normalized_data["entries"]) == 1

def test_invalid_time_ordering():
    """Req 1: start_time >= end_time should fail validation."""
    invalid_data = {
        "academic_year": "2026-2027",
        "entries": [
            {
                "day": "MONDAY",
                "start_time": "15:00",
                "end_time": "14:00", # Error: end before start
                "department": "CSE",
                "year": 3,
                "semester": 7,
                "section": "A",
                "subject": "Algorithms"
            }
        ]
    }
    result = validate_and_normalize_timetable_json(invalid_data)
    assert result.is_valid is False
    assert any("must be before end_time" in err for err in result.errors)

def test_missing_mandatory_fields():
    """Req 1: Missing entries or invalid root fails validation."""
    result = validate_and_normalize_timetable_json("Not a dictionary")
    assert result.is_valid is False
    assert any("must be a dictionary" in err for err in result.errors)

    result_no_entries = validate_and_normalize_timetable_json({"academic_year": "2026-2027"})
    assert result_no_entries.is_valid is False
    assert any("must contain a list of 'entries'" in err for err in result_no_entries.errors)

def test_unresolved_markers_warning():
    """Req 1: Unresolved markers produce warnings."""
    data_with_unresolved = {
        "academic_year": "2026-2027",
        "entries": [
            {
                "day": "MONDAY",
                "start_time": "09:30",
                "end_time": "10:25",
                "department": "CSE",
                "year": 3,
                "semester": 7,
                "section": "A",
                "subject": "Mathematics",
                "faculty": "UNRESOLVED_FACULTY",
                "room": "???"
            }
        ]
    }
    result = validate_and_normalize_timetable_json(data_with_unresolved)
    assert len(result.warnings) >= 2
    assert any("unresolved value" in w for w in result.warnings)

