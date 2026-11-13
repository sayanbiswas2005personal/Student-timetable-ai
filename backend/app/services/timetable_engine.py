from datetime import datetime, date, time
from typing import Optional, List, Dict, Any, Tuple
import pytz
from sqlalchemy.orm import Session
from sqlalchemy import and_, or_

from backend.app.core.config import settings
from backend.app.models.timetable import Timetable, ProcessingStatus
from backend.app.models.timetable_entry import TimetableEntry
from backend.app.models.schedule_override import ScheduleOverride, OverrideType
from backend.app.schemas.timetable import TimetableEntrySchema
from backend.app.schemas.search import FacultySearchResponse
from backend.app.services.json_validator import time_to_minutes

DAYS_MAP = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"]

class TimetableEngine:
    """
    Timetable matching service.
    Resolves active timetable entries against campus local time, overrides, and schedule states.
    """

    def __init__(self, db: Session, timezone_str: Optional[str] = None):
        self.db = db
        self.tz_name = timezone_str or settings.TIMEZONE
        try:
            self.tz = pytz.timezone(self.tz_name)
        except Exception:
            self.tz = pytz.timezone("Asia/Kolkata")

    def get_current_campus_time(
        self,
        simulated_date_str: Optional[str] = None,
        simulated_time_str: Optional[str] = None
    ) -> Tuple[date, time, str, str, bool]:
        """
        Returns (current_date, current_time, day_name, time_str, is_simulated).
        Converts server UTC to institutional campus timezone, or uses simulation values.
        """
        is_simulated = False
        now_campus = datetime.now(self.tz)

        target_date = now_campus.date()
        target_time = now_campus.time()

        if simulated_date_str:
            try:
                target_date = datetime.strptime(simulated_date_str.strip(), "%Y-%m-%d").date()
                is_simulated = True
            except ValueError:
                pass

        if simulated_time_str:
            try:
                # Accept HH:MM or HH:MM:SS
                parts = simulated_time_str.strip().split(":")
                target_time = time(int(parts[0]), int(parts[1]))
                is_simulated = True
            except (ValueError, IndexError):
                pass

        day_index = target_date.weekday() # 0 = Monday, 6 = Sunday
        day_name = DAYS_MAP[day_index]
        time_str = f"{target_time.hour:02d}:{target_time.minute:02d}"

        return target_date, target_time, day_name, time_str, is_simulated

    def find_active_timetable(self, department: str) -> Optional[Timetable]:
        """
        Finds the published active timetable for the department (or institutional global timetable).
        """
        # First try exact department published timetable
        timetable = self.db.query(Timetable).filter(
            Timetable.is_published == True,
            or_(
                Timetable.department == department,
                Timetable.department == "ALL",
                Timetable.department == None
            )
        ).order_by(Timetable.version.desc(), Timetable.id.desc()).first()

        return timetable

    def check_schedule(
        self,
        department: str,
        year: int,
        semester: int,
        section: str,
        simulated_date_str: Optional[str] = None,
        simulated_time_str: Optional[str] = None
    ) -> FacultySearchResponse:
        """
        Core search engine method implementing Section 6 requirements.
        """
        query_date, query_time_obj, day_name, current_time_str, is_simulated = self.get_current_campus_time(
            simulated_date_str, simulated_time_str
        )
        current_minutes = time_to_minutes(current_time_str)

        dept_clean = department.strip().upper()
        sec_clean = section.strip().upper()

        response = FacultySearchResponse(
            current_status="UNKNOWN",
            status_label="Checking Schedule",
            status_badge_color="gray",
            message="",
            department=dept_clean,
            year=year,
            semester=semester,
            section=sec_clean,
            query_date=query_date.isoformat(),
            query_day=day_name,
            query_time=current_time_str,
            is_simulated=is_simulated,
            warnings=[]
        )

        # 1. Check for Active Timetable
        active_timetable = self.find_active_timetable(dept_clean)
        if not active_timetable:
            response.current_status = "TIMETABLE_UNAVAILABLE"
            response.status_label = "Timetable Unavailable"
            response.status_badge_color = "yellow"
            response.message = (
                f"No published active timetable was found for {dept_clean} (Year {year}, Sem {semester}, Sec {sec_clean}). "
                "Please notify the academic administrator."
            )
            response.warnings.append("No active published timetable in the database.")
            return response

        # 2. Check if Timetable is Expired
        if active_timetable.valid_until and active_timetable.valid_until < query_date:
            response.current_status = "TIMETABLE_EXPIRED"
            response.status_label = "Timetable Expired"
            response.status_badge_color = "red"
            response.message = (
                f"The active timetable for {dept_clean} expired on {active_timetable.valid_until.isoformat()}. "
                "Timetable needs updating by an administrator."
            )
            response.warnings.append(f"Timetable expired on {active_timetable.valid_until}.")
            return response

        # 3. Check for Date-Specific Overrides (Holidays, Cancellations, Substitutions)
        overrides = self.db.query(ScheduleOverride).filter(
            ScheduleOverride.date == query_date,
            or_(
                ScheduleOverride.department == dept_clean,
                ScheduleOverride.department == "ALL"
            ),
            or_(
                ScheduleOverride.section == sec_clean,
                ScheduleOverride.section == "ALL"
            ),
            or_(
                ScheduleOverride.semester == semester,
                ScheduleOverride.semester == 0 # 0 denotes all semesters
            )
        ).all()

        # Check full-day Holiday override
        holiday = next((o for o in overrides if o.override_type == OverrideType.HOLIDAY), None)
        if holiday:
            response.current_status = "HOLIDAY"
            response.status_label = "Institutional Holiday"
            response.status_badge_color = "blue"
            response.message = f"Today is an institutional holiday: {holiday.reason}."
            response.override_applied = {
                "type": holiday.override_type.value,
                "reason": holiday.reason
            }
            return response

        # 4. Fetch All Scheduled Entries for Today
        today_db_entries = self.db.query(TimetableEntry).filter(
            TimetableEntry.timetable_id == active_timetable.id,
            TimetableEntry.day == day_name,
            TimetableEntry.year == year,
            TimetableEntry.semester == semester,
            TimetableEntry.department == dept_clean,
            or_(
                TimetableEntry.section == sec_clean,
                TimetableEntry.section.ilike(f"{sec_clean}%")
            )
        ).order_by(TimetableEntry.start_time.asc()).all()

        # Convert to Pydantic schemas
        today_entries: List[TimetableEntrySchema] = []
        for ent in today_db_entries:
            try:
                today_entries.append(TimetableEntrySchema(
                    day=ent.day,
                    start_time=ent.start_time,
                    end_time=ent.end_time,
                    department=ent.department,
                    year=ent.year,
                    semester=ent.semester,
                    section=ent.section,
                    subject_code=ent.subject_code,
                    subject=ent.subject,
                    faculty=ent.faculty,
                    room=ent.room,
                    notes=ent.notes
                ))
            except Exception:
                pass

        response.today_schedule = today_entries

        # 5. Check if Any Classes Scheduled Today
        if not today_entries:
            response.current_status = "NO_CLASSES_TODAY"
            response.status_label = "No Classes Today"
            response.status_badge_color = "gray"
            response.message = f"No scheduled classes found for {dept_clean} Sec {sec_clean} on {day_name}."
            return response

        # Determine daily college hours bounds
        earliest_start = min(time_to_minutes(e.start_time) for e in today_entries)
        latest_end = max(time_to_minutes(e.end_time) for e in today_entries)

        # 6. Check Before College Hours
        if current_minutes < earliest_start:
            response.current_status = "BEFORE_COLLEGE_HOURS"
            response.status_label = "Before College Hours"
            response.status_badge_color = "gray"
            response.next_class = today_entries[0]
            response.message = (
                f"College has not started yet. First lecture begins at {today_entries[0].start_time} "
                f"({today_entries[0].subject})."
            )
            return response

        # 7. Check After College Hours
        if current_minutes >= latest_end:
            response.current_status = "AFTER_COLLEGE_HOURS"
            response.status_label = "Classes Ended"
            response.status_badge_color = "gray"
            response.message = f"Scheduled classes for {dept_clean} Sec {sec_clean} have ended for today."
            return response

        # 8. Check for Current Active Class: start_time <= current_time < end_time
        matching_entries: List[TimetableEntrySchema] = []
        for entry in today_entries:
            e_start = time_to_minutes(entry.start_time)
            e_end = time_to_minutes(entry.end_time)
            if e_start <= current_minutes < e_end:
                matching_entries.append(entry)

        # 9. Find Next Scheduled Class: start_time > current_time
        next_candidates = [e for e in today_entries if time_to_minutes(e.start_time) > current_minutes]
        if next_candidates:
            # Sort by start_time
            next_candidates.sort(key=lambda x: time_to_minutes(x.start_time))
            response.next_class = next_candidates[0]

        # 10. Handle Matching Entries
        if len(matching_entries) > 1:
            # Multiple matching entries = Conflicting timetable entries
            response.current_status = "TIMETABLE_CONFLICT"
            response.status_label = "Timetable Conflict"
            response.status_badge_color = "red"
            response.has_conflict = True
            response.conflicting_entries = matching_entries
            response.current_class = matching_entries[0]
            subjects_str = ", ".join(e.subject for e in matching_entries)
            response.message = (
                f"Warning: Multiple conflicting classes scheduled at this time: {subjects_str}. "
                "Please alert the department coordinator to resolve this overlap."
            )
            response.warnings.append("Multiple overlapping entries found in active timetable.")
            return response

        if len(matching_entries) == 1:
            current_entry = matching_entries[0]
            response.current_class = current_entry

            # Check if this specific slot has an override
            slot_override = next(
                (o for o in overrides if (
                    o.start_time and o.end_time and
                    time_to_minutes(o.start_time) <= current_minutes < time_to_minutes(o.end_time)
                )),
                None
            )

            if slot_override:
                response.override_applied = {
                    "type": slot_override.override_type.value,
                    "reason": slot_override.reason,
                    "subject": slot_override.subject,
                    "faculty": slot_override.faculty,
                    "room": slot_override.room
                }

                if slot_override.override_type == OverrideType.CANCELLED:
                    response.current_status = "CLASS_CANCELLED"
                    response.status_label = "Class Cancelled"
                    response.status_badge_color = "yellow"
                    response.message = (
                        f"The scheduled class '{current_entry.subject}' has been cancelled for today. "
                        f"Reason: {slot_override.reason}"
                    )
                    return response

                elif slot_override.override_type == OverrideType.SUBSTITUTION:
                    response.current_status = "FACULTY_SUBSTITUTION"
                    response.status_label = "Faculty Substitution"
                    response.status_badge_color = "blue"
                    if slot_override.faculty:
                        current_entry.faculty = slot_override.faculty
                    if slot_override.subject:
                        current_entry.subject = slot_override.subject
                    if slot_override.room:
                        current_entry.room = slot_override.room
                    response.current_class = current_entry
                    response.message = (
                        f"Faculty substitution in effect: {slot_override.faculty or 'Substitute Faculty'} "
                        f"is conducting '{current_entry.subject}'. Reason: {slot_override.reason}."
                    )
                    return response

                elif slot_override.override_type == OverrideType.SPECIAL_CLASS:
                    response.current_status = "SPECIAL_CLASS"
                    response.status_label = "Special Class"
                    response.status_badge_color = "blue"
                    response.message = f"Special class in progress: {slot_override.reason}."
                    return response

            # Check if it's lunch break
            if "LUNCH" in current_entry.subject.upper() or "BREAK" in current_entry.subject.upper():
                response.current_status = "LUNCH_BREAK"
                response.status_label = "Lunch Break"
                response.status_badge_color = "yellow"
                response.message = "Scheduled lunch break is currently in progress."
                return response

            # Regular Scheduled Class in Progress
            response.current_status = "SCHEDULED_CLASS_IN_PROGRESS"
            response.status_label = "Scheduled Class in Progress"
            response.status_badge_color = "green"
            response.message = (
                "The selected section has a scheduled class at this time. "
                "Please verify the student's attendance with the concerned faculty."
            )
            return response

        # 11. No Class Matching Current Time -> Free Period or Lunch Break
        # Check standard lunch hour e.g. 13:25 to 14:30 or gap between classes
        if 13 <= query_time_obj.hour <= 14 and current_minutes < 14 * 60 + 30:
            # Common lunch window
            response.current_status = "LUNCH_BREAK"
            response.status_label = "Lunch / Recess"
            response.status_badge_color = "yellow"
            response.message = "No lecture scheduled during this period (Lunch / Campus Recess)."
            return response

        response.current_status = "FREE_PERIOD"
        response.status_label = "Free Period"
        response.status_badge_color = "blue"
        if response.next_class:
            response.message = (
                f"No scheduled class at this time (Free Period). "
                f"Next class is '{response.next_class.subject}' at {response.next_class.start_time}."
            )
        else:
            response.message = "No scheduled class at this time (Free Period)."

        return response

