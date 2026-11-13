from typing import List, Dict, Any, Tuple
from datetime import datetime
from backend.app.schemas.timetable import NormalizedTimetableJSON, TimetableEntrySchema, DAYS_OF_WEEK

class TimetableValidationResult:
    def __init__(self, is_valid: bool, errors: List[str], warnings: List[str], normalized_data: Dict[str, Any]):
        self.is_valid = is_valid
        self.errors = errors
        self.warnings = warnings
        self.normalized_data = normalized_data

def time_to_minutes(t_str: str) -> int:
    h, m = map(int, t_str.split(":"))
    return h * 60 + m

def validate_and_normalize_timetable_json(raw_json: Any) -> TimetableValidationResult:
    """
    Validates and normalizes raw timetable JSON data.
    Checks:
    - Schema compliance
    - Mandatory fields
    - Valid times and ordering (start_time < end_time)
    - Valid days
    - Overlapping/conflicting periods for the same section
    - Unresolved values
    """
    errors: List[str] = []
    warnings: List[str] = []
    
    if not isinstance(raw_json, dict):
        return TimetableValidationResult(
            is_valid=False,
            errors=["Root timetable JSON must be a dictionary."],
            warnings=[],
            normalized_data={}
        )
    
    # Check top-level fields
    academic_year = raw_json.get("academic_year")
    if not academic_year:
        warnings.append("Academic year is missing or empty. Defaulted to '2026-2027'.")
        raw_json["academic_year"] = "2026-2027"

    institution = raw_json.get("institution")
    if not institution:
        raw_json["institution"] = "Adamas University"

    timezone_str = raw_json.get("timezone")
    if not timezone_str:
        raw_json["timezone"] = "Asia/Kolkata"

    entries_raw = raw_json.get("entries")
    if entries_raw is None or not isinstance(entries_raw, list):
        errors.append("Timetable must contain a list of 'entries'.")
        return TimetableValidationResult(is_valid=False, errors=errors, warnings=warnings, normalized_data=raw_json)

    if len(entries_raw) == 0:
        warnings.append("Timetable contains zero entries.")

    validated_entries: List[Dict[str, Any]] = []
    # Track section schedule to detect overlaps: key = (day, department, year, semester, section)
    section_schedules: Dict[Tuple[str, str, int, int, str], List[Tuple[int, int, str, int]]] = {}

    for idx, item in enumerate(entries_raw):
        row_id = idx + 1
        if not isinstance(item, dict):
            errors.append(f"Entry #{row_id} is not an object.")
            continue

        # Check for unresolved markers
        for k, v in item.items():
            if isinstance(v, str) and any(marker in v.upper() for marker in ["UNRESOLVED", "???", "UNKNOWN", "TODO"]):
                warnings.append(f"Entry #{row_id} field '{k}' has unresolved value: '{v}'.")

        try:
            entry_schema = TimetableEntrySchema.model_validate(item)
            entry_dict = entry_schema.model_dump()
            validated_entries.append(entry_dict)

            start_min = time_to_minutes(entry_dict["start_time"])
            end_min = time_to_minutes(entry_dict["end_time"])

            if start_min >= end_min:
                errors.append(
                    f"Entry #{row_id} ({entry_dict['subject']}): start_time ({entry_dict['start_time']}) "
                    f"must be before end_time ({entry_dict['end_time']})."
                )

            # Check overlap within the same section and day
            sec_key = (
                entry_dict["day"],
                entry_dict["department"].upper(),
                entry_dict["year"],
                entry_dict["semester"],
                entry_dict["section"].upper()
            )

            if sec_key not in section_schedules:
                section_schedules[sec_key] = []

            for existing_start, existing_end, existing_subject, existing_idx in section_schedules[sec_key]:
                # Overlap check: start_min < existing_end and end_min > existing_start
                if start_min < existing_end and end_min > existing_start:
                    warnings.append(
                        f"Potential conflict: Entry #{row_id} ('{entry_dict['subject']}' "
                        f"{entry_dict['start_time']}-{entry_dict['end_time']}) overlaps with Entry #{existing_idx} "
                        f"('{existing_subject}' for {entry_dict['day']} Section {entry_dict['section']})."
                    )

            section_schedules[sec_key].append((start_min, end_min, entry_dict["subject"], row_id))

        except Exception as e:
            errors.append(f"Entry #{row_id} validation failed: {str(e)}")

    raw_json["entries"] = validated_entries
    is_valid = len(errors) == 0

    return TimetableValidationResult(
        is_valid=is_valid,
        errors=errors,
        warnings=warnings,
        normalized_data=raw_json
    )

