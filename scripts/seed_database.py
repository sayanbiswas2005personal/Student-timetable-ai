import sys
import os

# Add the backend directory to the Python path
sys.path.append(os.path.join(os.path.dirname(__file__), '../backend'))

from app.core.database import SessionLocal, engine
from app.models.university import Base, Department, Program, Batch, Semester, Section, Student
from app.models.timetable import TimetableEntry, Faculty, Room, Subject
import datetime

def seed_db():
    print("Dropping and recreating all tables...")
    Base.metadata.drop_all(bind=engine)
    Base.metadata.create_all(bind=engine)
    
    db = SessionLocal()
    try:
        # Hierarchy
        dept = Department(name="Computer Science", code="CS")
        db.add(dept)
        db.flush()

        prog_aiml = Program(department_id=dept.id, name="B.Tech Computer Science AI-ML", code="BTCSC AI-ML", degree="B.Tech")
        prog_pharma = Program(department_id=dept.id, name="B.Pharma", code="B PHARMA", degree="B.Pharma")
        db.add_all([prog_aiml, prog_pharma])
        db.flush()

        batch_2023 = Batch(year=2023)
        db.add(batch_2023)
        db.flush()

        sem_6 = Semester(number=6)
        db.add(sem_6)
        db.flush()

        sec_a = Section(name="A", program_id=prog_aiml.id, batch_id=batch_2023.id, semester_id=sem_6.id)
        sec_b = Section(name="B", program_id=prog_aiml.id, batch_id=batch_2023.id, semester_id=sem_6.id)
        sec_c = Section(name="C", program_id=prog_aiml.id, batch_id=batch_2023.id, semester_id=sem_6.id)
        sec_d = Section(name="D", program_id=prog_aiml.id, batch_id=batch_2023.id, semester_id=sem_6.id)
        db.add_all([sec_a, sec_b, sec_c, sec_d])
        db.flush()

        student_1 = Student(
            section_id=sec_d.id,
            registration_number="AU 2023 000916",
            normalized_registration_number="AU2023000916",
            roll_number="UG 02 BTCSC AI-ML 2023/024",
            normalized_roll_number="UG02BTCSCAIML2023024",
            first_name="Test",
            last_name="Student"
        )
        db.add(student_1)
        db.flush()

        # Faculties
        fac_payel = Faculty(name="Dr. Payel Chaudhury")
        fac_debasree = Faculty(name="Ms. DEBASREE MITRA")
        fac_hirak = Faculty(name="Dr. HIRAK MAZUMDAR")
        fac_amitava = Faculty(name="Prof.(Dr.) Amitava Sen")
        fac_tathagata = Faculty(name="Dr. Tathagata Dasgupta")
        fac_jeet = Faculty(name="Jeet Banerjee")
        db.add_all([fac_payel, fac_debasree, fac_hirak, fac_amitava, fac_tathagata, fac_jeet])
        db.flush()
        
        # Rooms
        room_a = Room(name="Room A")
        room_b = Room(name="AU6-4304")
        db.add_all([room_a, room_b])
        db.flush()

        # Subjects
        sub_oe3 = Subject(name="OPEN ELECTIVE III", code="OE3")
        sub_minor = Subject(name="MINOR_CSE14050", code="CSE14050")
        sub_oe2 = Subject(name="Open Elective II", code="OE2")
        sub_crypto = Subject(name="Professional Elective - IV Cryptography & Cyber Security", code="CSE11040")
        sub_nlp = Subject(name="Specialization Course-IV Natural Language Processing", code="CSE11209")
        sub_cloud = Subject(name="Professional Elective - III Cloud Computing", code="CSE11036")
        sub_cll1 = Subject(name="CLL1 G3_7 (Soft Skill-Non-NEP)", code="CLL1")
        sub_cll2 = Subject(name="CLL2 G3_7 (Aptitude-Non-NEP)", code="CLL2")
        sub_ind = Subject(name="Industrial Management", code="MGT11402")
        sub_lib = Subject(name="Library", code="LIB")
        sub_nlp_lab = Subject(name="Specialization Course-IV Lab NLP", code="CSE12210")
        sub_cloud_lab = Subject(name="Cloud Computing Lab", code="CSE12045")
        db.add_all([sub_oe3, sub_minor, sub_oe2, sub_crypto, sub_nlp, sub_cloud, sub_cll1, sub_cll2, sub_ind, sub_lib, sub_nlp_lab, sub_cloud_lab])
        db.flush()

        # Helper
        def t(h, m):
            return datetime.time(hour=h, minute=m)
            
        entries = []
        
        # MONDAY
        entries.append(TimetableEntry(section_id=sec_d.id, day_of_week=0, start_time=t(9,30), end_time=t(10,25), subject_id=sub_oe3.id, faculty_id=fac_payel.id, room_id=room_a.id))
        entries.append(TimetableEntry(section_id=sec_d.id, day_of_week=0, start_time=t(10,30), end_time=t(11,25), subject_id=sub_minor.id, room_id=room_a.id))
        entries.append(TimetableEntry(section_id=sec_d.id, day_of_week=0, start_time=t(11,30), end_time=t(12,25), subject_id=sub_oe2.id, faculty_id=fac_jeet.id, room_id=room_a.id))
        entries.append(TimetableEntry(section_id=sec_d.id, day_of_week=0, start_time=t(12,30), end_time=t(13,25), subject_id=sub_crypto.id, faculty_id=fac_debasree.id, room_id=room_a.id))
        entries.append(TimetableEntry(section_id=sec_d.id, day_of_week=0, start_time=t(14,30), end_time=t(15,25), subject_id=sub_nlp.id, faculty_id=fac_hirak.id, room_id=room_a.id))

        # TUESDAY
        entries.append(TimetableEntry(section_id=sec_d.id, day_of_week=1, start_time=t(9,30), end_time=t(10,25), subject_id=sub_cloud.id, faculty_id=fac_amitava.id, room_id=room_a.id))
        entries.append(TimetableEntry(section_id=sec_d.id, day_of_week=1, start_time=t(10,30), end_time=t(11,25), subject_id=sub_minor.id, room_id=room_a.id))
        entries.append(TimetableEntry(section_id=sec_d.id, day_of_week=1, start_time=t(11,30), end_time=t(12,25), subject_id=sub_cll1.id, room_id=room_a.id))
        entries.append(TimetableEntry(section_id=sec_d.id, day_of_week=1, start_time=t(12,30), end_time=t(13,25), subject_id=sub_cll2.id, room_id=room_a.id))

        # WEDNESDAY
        entries.append(TimetableEntry(section_id=sec_d.id, day_of_week=2, start_time=t(9,30), end_time=t(10,25), subject_id=sub_oe3.id, faculty_id=fac_payel.id, room_id=room_a.id))
        entries.append(TimetableEntry(section_id=sec_d.id, day_of_week=2, start_time=t(10,30), end_time=t(11,25), subject_id=sub_minor.id, room_id=room_a.id))
        entries.append(TimetableEntry(section_id=sec_d.id, day_of_week=2, start_time=t(11,30), end_time=t(12,25), subject_id=sub_oe2.id, faculty_id=fac_jeet.id, room_id=room_a.id))
        entries.append(TimetableEntry(section_id=sec_d.id, day_of_week=2, start_time=t(12,30), end_time=t(13,25), subject_id=sub_crypto.id, faculty_id=fac_debasree.id, room_id=room_a.id))
        entries.append(TimetableEntry(section_id=sec_d.id, day_of_week=2, start_time=t(14,30), end_time=t(15,25), subject_id=sub_cloud.id, faculty_id=fac_amitava.id, room_id=room_a.id))
        entries.append(TimetableEntry(section_id=sec_d.id, day_of_week=2, start_time=t(15,30), end_time=t(16,25), subject_id=sub_nlp.id, faculty_id=fac_hirak.id, room_id=room_a.id))

        # THURSDAY
        entries.append(TimetableEntry(section_id=sec_d.id, day_of_week=3, start_time=t(9,30), end_time=t(10,25), subject_id=sub_oe3.id, faculty_id=fac_payel.id, room_id=room_a.id))
        entries.append(TimetableEntry(section_id=sec_d.id, day_of_week=3, start_time=t(10,30), end_time=t(11,25), subject_id=sub_ind.id, faculty_id=fac_tathagata.id, room_id=room_a.id))
        entries.append(TimetableEntry(section_id=sec_d.id, day_of_week=3, start_time=t(11,30), end_time=t(12,25), subject_id=sub_oe2.id, faculty_id=fac_jeet.id, room_id=room_a.id))
        entries.append(TimetableEntry(section_id=sec_d.id, day_of_week=3, start_time=t(12,30), end_time=t(13,25), subject_id=sub_lib.id, room_id=room_a.id))
        entries.append(TimetableEntry(section_id=sec_d.id, day_of_week=3, start_time=t(15,30), end_time=t(17,25), subject_id=sub_nlp_lab.id, faculty_id=fac_hirak.id, room_id=room_b.id))

        # FRIDAY
        entries.append(TimetableEntry(section_id=sec_d.id, day_of_week=4, start_time=t(9,30), end_time=t(10,25), subject_id=sub_cloud.id, faculty_id=fac_amitava.id, room_id=room_a.id))
        entries.append(TimetableEntry(section_id=sec_d.id, day_of_week=4, start_time=t(10,30), end_time=t(11,25), subject_id=sub_nlp.id, faculty_id=fac_hirak.id, room_id=room_a.id))
        entries.append(TimetableEntry(section_id=sec_d.id, day_of_week=4, start_time=t(11,30), end_time=t(12,25), subject_id=sub_minor.id, room_id=room_a.id))
        entries.append(TimetableEntry(section_id=sec_d.id, day_of_week=4, start_time=t(12,30), end_time=t(13,25), subject_id=sub_crypto.id, faculty_id=fac_debasree.id, room_id=room_a.id))
        entries.append(TimetableEntry(section_id=sec_d.id, day_of_week=4, start_time=t(14,30), end_time=t(17,25), subject_id=sub_cloud_lab.id, faculty_id=fac_amitava.id, room_id=room_b.id))

        db.add_all(entries)
        db.commit()
        print("Database seeded successfully.")
    except Exception as e:
        print(f"Error seeding database: {e}")
        db.rollback()
    finally:
        db.close()

if __name__ == "__main__":
    seed_db()
