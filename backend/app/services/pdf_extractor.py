import os
import re
import json
import logging
from typing import Dict, Any, List, Optional, Tuple
from pathlib import Path
import pypdf

from backend.app.core.config import settings
from backend.app.schemas.timetable import NormalizedTimetableJSON, TimetableEntrySchema

logger = logging.getLogger("studentai.extractor")

ROMAN_TO_INT = {
    "I": 1, "II": 2, "III": 3, "IV": 4,
    "V": 5, "VI": 6, "VII": 7, "VIII": 8
}

SLOTS_DEF = [
    ("09:30", "10:25", 491.125),
    ("10:30", "11:25", 811.75),
    ("11:30", "12:25", 1132.375),
    ("12:30", "13:25", 1453.0),
    ("13:30", "14:25", 1773.625),
    ("14:30", "15:25", 2094.25),
    ("15:30", "16:25", 2414.875),
    ("16:30", "17:25", 2735.5)
]

DAYS_DEF = [
    ("MONDAY", 589.0),
    ("TUESDAY", 895.0),
    ("WEDNESDAY", 1201.0),
    ("THURSDAY", 1507.0),
    ("FRIDAY", 1813.0)
]

class ExtractionResult:
    def __init__(
        self,
        success: bool,
        provider: str,
        data: Optional[Dict[str, Any]] = None,
        error: Optional[str] = None,
        warnings: Optional[List[str]] = None,
        page_count: int = 0
    ):
        self.success = success
        self.provider = provider
        self.data = data
        self.error = error
        self.warnings = warnings or []
        self.page_count = page_count

class BaseTimetableExtractor:
    def extract(self, pdf_path: str, **kwargs) -> ExtractionResult:
        raise NotImplementedError

class GeminiCloudExtractor(BaseTimetableExtractor):
    """
    Cloud extraction provider using Google Gemini API via official google-genai SDK.
    """
    def extract(self, pdf_path: str, **kwargs) -> ExtractionResult:
        api_key = kwargs["api_key"] if "api_key" in kwargs else settings.GEMINI_API_KEY
        if not api_key:
            return ExtractionResult(
                success=False,
                provider="GEMINI_API",
                error="Gemini API Key is not configured. Please set GEMINI_API_KEY in your .env or choose the Demo / Layout Parser mode."
            )

        model_name = kwargs.get("model") or settings.GEMINI_MODEL
        try:
            from google import genai
            client = genai.Client(api_key=api_key)

            # Upload the PDF file
            logger.info(f"Uploading {pdf_path} to Gemini API...")
            uploaded_file = client.files.upload(file=pdf_path)

            prompt = (
                "You are an expert university timetable data analyst. Analyze this multi-page institutional timetable PDF. "
                "Each page represents a class section timetable with periods, days of the week, subjects, subject codes, faculty, and room numbers. "
                "Extract all timetable entries into a single normalized JSON object following the required schema. "
                "Important guidelines: "
                "1. Department: Extract department code (e.g. 'CSE', 'BCA', 'MCA'). "
                "2. Academic Year & Semester: Convert Roman numerals (e.g. 'III' -> Semester 3, Year 2; 'VII' -> Semester 7, Year 4; 'I' -> Semester 1, Year 1). "
                "3. Section: Extract the exact section letter or name (e.g. 'A', 'B', 'V A', 'BFSI', etc.). "
                "4. Period times: Represent times in 24-hour HH:MM format (e.g. '09:30' to '10:25'). "
                "5. Days: Use full uppercase English day name: 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'. "
                "6. Do not fabricate missing information. If a room or faculty is not indicated, use null. "
                "7. Ensure the output strictly conforms to the requested JSON schema."
            )

            schema = NormalizedTimetableJSON.model_json_schema()

            logger.info(f"Calling Gemini model {model_name}...")
            interaction = client.interactions.create(
                model=model_name,
                input=[
                    {"type": "text", "text": prompt},
                    {"type": "document", "uri": uploaded_file.uri, "mime_type": uploaded_file.mime_type}
                ],
                response_format={
                    "type": "text",
                    "mime_type": "application/json",
                    "schema": schema
                }
            )

            output_text = interaction.output_text
            if not output_text:
                return ExtractionResult(
                    success=False,
                    provider="GEMINI_API",
                    error="Gemini returned an empty response."
                )

            parsed_data = json.loads(output_text)
            
            # Count pages
            reader = pypdf.PdfReader(pdf_path)
            page_count = len(reader.pages)

            return ExtractionResult(
                success=True,
                provider="GEMINI_API",
                data=parsed_data,
                page_count=page_count
            )

        except Exception as e:
            logger.error(f"Gemini API extraction failed: {e}", exc_info=True)
            return ExtractionResult(
                success=False,
                provider="GEMINI_API",
                error=f"Gemini API extraction error: {str(e)}"
            )

class LayoutParserExtractor(BaseTimetableExtractor):
    """
    High-precision local tabular layout parser for institutional timetable PDFs.
    Uses pypdf geometry and layout coordinate matching.
    Operates without local OCR or cloud network dependencies.
    """
    def extract(self, pdf_path: str, **kwargs) -> ExtractionResult:
        try:
            reader = pypdf.PdfReader(pdf_path)
            total_pages = len(reader.pages)
            page_limit = kwargs.get("page_limit")
            pages_to_process = range(min(page_limit or total_pages, total_pages))

            institution = "Adamas University"
            timezone_val = "Asia/Kolkata"
            academic_year = "2026-2027"
            valid_from = "2026-07-01"
            valid_until = "2027-06-30"

            all_entries: List[Dict[str, Any]] = []
            warnings: List[str] = []

            for page_idx in pages_to_process:
                page = reader.pages[page_idx]
                raw_text = page.extract_text() or ""
                
                # Check institution or footer
                if "Adamas University" in raw_text:
                    institution = "Adamas University"
                
                # Check w.e.f. date
                wef_match = re.search(r'w\.e\.f\.\s*(\d{1,2}/\d{1,2}/\d{4})', raw_text)
                if wef_match:
                    parts = wef_match.group(1).split('/')
                    if len(parts) == 3:
                        valid_from = f"{parts[2]}-{int(parts[1]):02d}-{int(parts[0]):02d}"

                # Parse Title (e.g. "B.TECH CSE III A", "BCA I A", "MCA III A", "B.TECH CSE VII B")
                header_match = re.search(
                    r'(B\.TECH|M\.TECH|BCA|MCA)(?:\s+([A-Z0-9_\-]+))?\s+([IVXLCDM]+)\s+([A-Za-z0-9\s\(\)\-_/]+)',
                    raw_text
                )
                
                dept = "CSE"
                sem_num = 3
                year_num = 2
                section = "A"

                if header_match:
                    prog = header_match.group(1)
                    sub_dept = header_match.group(2)
                    rom_sem = header_match.group(3)
                    sec_str = header_match.group(4).split('\n')[0].strip()

                    dept = f"{prog} {sub_dept}".strip() if sub_dept else prog
                    if "CSE" in dept:
                        dept = "CSE"
                    elif "BCA" in dept:
                        dept = "BCA"
                    elif "MCA" in dept:
                        dept = "MCA"

                    sem_num = ROMAN_TO_INT.get(rom_sem, 1)
                    year_num = max(1, (sem_num + 1) // 2)
                    section = sec_str if sec_str else f"Sec-{page_idx+1}"
                else:
                    first_lines = [l.strip() for l in raw_text.split('\n') if l.strip()]
                    section = f"Page-{page_idx+1}"
                    if len(first_lines) > 1:
                        section = first_lines[1][:15]

                # Extract text chunks with coordinates
                cell_chunks: Dict[Tuple[int, int], List[str]] = {}

                def visitor_body(text, cm, tm, fontDict, fontSize):
                    t = text.strip()
                    if not t:
                        return
                    x, y = tm[4], tm[5]
                    # Filter out margins and headers
                    if y < 450 or y > 2100 or x < 300 or x > 3000:
                        return
                    
                    # Find closest day row
                    day_idx = min(range(len(DAYS_DEF)), key=lambda i: abs(y - DAYS_DEF[i][1]))
                    # Find closest period column
                    slot_idx = min(range(len(SLOTS_DEF)), key=lambda i: abs(x - SLOTS_DEF[i][2]))
                    
                    key = (day_idx, slot_idx)
                    if key not in cell_chunks:
                        cell_chunks[key] = []
                    cell_chunks[key].append(t)

                page.extract_text(visitor_text=visitor_body)

                # Process grouped cells
                for (d_i, s_i), chunks in sorted(cell_chunks.items()):
                    content = " ".join(chunks).strip()
                    if not content or content in ["Mo", "Tu", "We", "Th", "Fr"]:
                        continue

                    day_name = DAYS_DEF[d_i][0]
                    start_time, end_time, _ = SLOTS_DEF[s_i]

                    # Parse cell content: Subject, Subject Code, Faculty, Room
                    subject = content
                    subject_code = None
                    faculty = None
                    room = None

                    # Extract Subject Code like (CSE11104) or (SDS11510)
                    code_match = re.search(r'\(([A-Z]{2,4}\d{4,5}[A-Z]?)\)', content)
                    if code_match:
                        subject_code = code_match.group(1)
                    
                    # Extract Room like AU6-LAB-4001A, AU6-2003, AU6-4305, Room 204
                    room_match = re.search(r'(AU\d-[A-Z0-9\-]+|AU\d-\d+|Room\s*\d+|LAB-[A-Z0-9]+)', content, re.IGNORECASE)
                    if room_match:
                        room = room_match.group(1).upper()

                    # Extract Faculty (often starts with Dr., Mr., Ms., Prof., etc.)
                    faculty_match = re.search(
                        r'((?:Dr\.|Mr\.|Ms\.|Prof\.|Mrs\.)\s+[A-Za-z\.\s]+(?:\s*\(\d+\))?(?:\s*/\s*[A-Za-z\.\s]+)?)',
                        content
                    )
                    if faculty_match:
                        faculty = faculty_match.group(1).strip()
                        # Clean up faculty if room caught in it
                        if room and room in faculty:
                            faculty = faculty.replace(room, "").strip()

                    # Clean up subject title
                    clean_subject = content
                    if subject_code:
                        clean_subject = clean_subject.replace(f"({subject_code})", "")
                    if faculty:
                        clean_subject = clean_subject.replace(faculty, "")
                    if room:
                        clean_subject = clean_subject.replace(room, "")
                    
                    # Clean repeated whitespace and numbers
                    clean_subject = re.sub(r'\(\d+\)', '', clean_subject)
                    clean_subject = re.sub(r'\s+', ' ', clean_subject).strip()

                    if not clean_subject:
                        clean_subject = content

                    # Special handling for lab / double slot or library
                    if "Library" in clean_subject:
                        clean_subject = "Library"
                        faculty = "Librarian / Self-Study"

                    entry = {
                        "day": day_name,
                        "start_time": start_time,
                        "end_time": end_time,
                        "department": dept,
                        "year": year_num,
                        "semester": sem_num,
                        "section": section,
                        "subject_code": subject_code,
                        "subject": clean_subject[:120],
                        "faculty": faculty,
                        "room": room,
                        "notes": None
                    }
                    all_entries.append(entry)

            normalized_result = {
                "schema_version": "1.0",
                "institution": institution,
                "timezone": timezone_val,
                "academic_year": academic_year,
                "department": next(iter({entry["department"] for entry in all_entries}))
                if len({entry["department"] for entry in all_entries}) == 1 else "ALL",
                "valid_from": valid_from,
                "valid_until": valid_until,
                "entries": all_entries
            }

            return ExtractionResult(
                success=True,
                provider="LAYOUT_PARSER_DEMO",
                data=normalized_result,
                warnings=warnings,
                page_count=total_pages
            )

        except Exception as e:
            logger.error(f"Layout parser extraction failed: {e}", exc_info=True)
            return ExtractionResult(
                success=False,
                provider="LAYOUT_PARSER_DEMO",
                error=f"Layout parser error: {str(e)}"
            )

def extract_timetable_data(
    pdf_path: str,
    provider: Optional[str] = None,
    api_key: Optional[str] = None,
    page_limit: Optional[int] = None
) -> ExtractionResult:
    """
    Modular dispatcher for timetable extraction.
    Supports:
    - 'gemini': Cloud extraction using Gemini 3.8 Flash
    - 'layout_parser': Local coordinate layout extraction
    - auto (None): Uses Gemini if API key is present; otherwise falls back to LayoutParser
    """
    key_to_use = api_key or settings.GEMINI_API_KEY

    if provider == "gemini":
        extractor = GeminiCloudExtractor()
        return extractor.extract(pdf_path, api_key=key_to_use)
    
    if provider == "layout_parser":
        extractor = LayoutParserExtractor()
        return extractor.extract(pdf_path, page_limit=page_limit)

    # Automatic mode
    if key_to_use:
        logger.info("GEMINI_API_KEY found, attempting Gemini Cloud extraction...")
        gemini_result = GeminiCloudExtractor().extract(pdf_path, api_key=key_to_use)
        if gemini_result.success:
            return gemini_result
        logger.warning(f"Gemini Cloud extraction returned error ({gemini_result.error}), falling back to Layout Parser...")
    
    logger.info("Using LayoutParserExtractor (local / demo mode)...")
    return LayoutParserExtractor().extract(pdf_path, page_limit=page_limit)
