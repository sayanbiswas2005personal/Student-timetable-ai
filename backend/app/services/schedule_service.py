from datetime import datetime
import pytz
from sqlalchemy.orm import Session
from app.models.timetable import TimetableEntry

def get_kolkata_time():
    tz = pytz.timezone('Asia/Kolkata')
    return datetime.now(tz)

def get_today_schedule_for_section(db: Session, section_id: int):
    now = get_kolkata_time()
    current_day = now.weekday() # Monday = 0
    current_time = now.time()

    entries = db.query(TimetableEntry).filter(
        TimetableEntry.section_id == section_id,
        TimetableEntry.day_of_week == current_day
    ).order_by(TimetableEntry.start_time).all()

    schedule = []
    current_class = None
    next_class = None
    
    for entry in entries:
        is_current = entry.start_time <= current_time < entry.end_time
        is_completed = entry.end_time <= current_time
        
        info = {
            "subject": entry.subject.name if entry.subject else entry.raw_text,
            "start_time": entry.start_time.strftime("%H:%M"),
            "end_time": entry.end_time.strftime("%H:%M"),
            "faculty": entry.faculty.name if entry.faculty else None,
            "room": entry.room.name if entry.room else None,
            "group_name": entry.group_name,
            "is_current": is_current,
            "is_completed": is_completed
        }
        
        schedule.append(info)
        
        if is_current:
            current_class = info
            
        if not is_completed and not is_current and next_class is None:
            # First class that is neither completed nor current
            next_class = info
            
    return schedule, current_class, next_class
