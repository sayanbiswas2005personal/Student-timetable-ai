import re
from sqlalchemy.orm import Session
from app.models.university import Student, Program, Batch, Section

def normalize_string(s: str) -> str:
    """Removes spaces, slashes, and special characters, converts to uppercase."""
    if not s:
        return ""
    return re.sub(r'[\s/\\\-_]', '', s).upper()

def parse_roll_number(db: Session, roll_number: str):
    """
    Attempts to extract Program and Batch from a string like: UG 02 BTCSC AI-ML 2023/015
    """
    roll_upper = roll_number.upper()
    
    # Try to find batch year (4 digits)
    batch_match = re.search(r'(20\d{2})', roll_upper)
    batch_year = int(batch_match.group(1)) if batch_match else None
    
    # Try to find program string
    # Assuming standard structure contains program code like BTCSC AI-ML
    programs = db.query(Program).all()
    matched_program = None
    for p in programs:
        # Simple substring match for the program code in the raw roll number
        # Need to strip spaces to be resilient against typing differences
        if normalize_string(p.code) in normalize_string(roll_upper):
            matched_program = p
            break
            
    if matched_program and batch_year:
        # Find the batch
        batch = db.query(Batch).filter(Batch.year == batch_year).first()
        if not batch:
            return None
            
        # Try to extract the specific student number at the end (e.g. 015 from 2023/015, 2023015, or 2023 015)
        student_num_match = re.search(r'[/\s\-]?0*(\d+)$', roll_upper)
        student_num = int(student_num_match.group(1)) if student_num_match else None
        
        # User specified rule: Section D has rolls 1 to 73
        target_section_name = None
        if student_num is not None:
            if 1 <= student_num <= 73:
                target_section_name = "D"
            # We can add more rules here later
            
        if target_section_name:
            section = db.query(Section).filter(
                Section.program_id == matched_program.id,
                Section.batch_id == batch.id,
                Section.name == target_section_name
            ).first()
        else:
            # Fallback to just returning a section if we don't know the exact rule
            section = db.query(Section).filter(
                Section.program_id == matched_program.id,
                Section.batch_id == batch.id
            ).first()
            
        return section
        
    return None

def get_student_by_reg_and_roll(db: Session, reg_number: str, roll_number: str):
    norm_reg = normalize_string(reg_number)
    norm_roll = normalize_string(roll_number)
    
    student = db.query(Student).filter(
        Student.normalized_registration_number == norm_reg,
        Student.normalized_roll_number == norm_roll
    ).first()
    
    if student:
        return student.section, student
        
    # Check for conflict
    s_reg = db.query(Student).filter(Student.normalized_registration_number == norm_reg).first()
    s_roll = db.query(Student).filter(Student.normalized_roll_number == norm_roll).first()
    
    if s_reg or s_roll:
        raise ValueError("The provided Registration Number and Roll Number conflict or belong to different records. Please verify your inputs.")
        
    # Fallback to parsing roll number dynamically
    section = parse_roll_number(db, roll_number)
    if section:
        return section, None
        
    return None, None
