import pytest
from pathlib import Path
from backend.app.services.pdf_extractor import extract_timetable_data, GeminiCloudExtractor, LayoutParserExtractor

SAMPLE_PDF = "Section wise Timetable V15.pdf"

def test_layout_parser_sample_pdf_extraction():
    """Req 2: Test local layout parser extraction against the real institutional PDF."""
    if not Path(SAMPLE_PDF).exists():
        pytest.skip("Sample PDF not found in workspace.")

    res = extract_timetable_data(SAMPLE_PDF, provider="layout_parser", page_limit=2)
    assert res.success is True
    assert res.provider == "LAYOUT_PARSER_DEMO"
    assert res.page_count == 52
    assert res.data is not None
    assert "entries" in res.data
    assert len(res.data["entries"]) > 0

    first_entry = res.data["entries"][0]
    assert first_entry["day"] in ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"]
    assert ":" in first_entry["start_time"]
    assert ":" in first_entry["end_time"]
    assert first_entry["department"] is not None
    assert first_entry["section"] is not None

def test_layout_parser_extracts_all_sample_pdf_pages():
    if not Path(SAMPLE_PDF).exists():
        pytest.skip("Sample PDF not found in workspace.")

    res = extract_timetable_data(SAMPLE_PDF, provider="layout_parser")

    assert res.success is True
    assert res.page_count == 52
    assert len(res.data["entries"]) > 1000
    assert res.data["department"] == "ALL"
    assert {"CSE", "BCA", "MCA"}.issubset(
        {entry["department"] for entry in res.data["entries"]}
    )

def test_gemini_extractor_missing_key_graceful_handling():
    """Req 2: Verify Gemini extractor returns clean error without crashing when API key is missing."""
    extractor = GeminiCloudExtractor()
    res = extractor.extract(SAMPLE_PDF, api_key="")
    assert res.success is False
    assert res.provider == "GEMINI_API"
    assert "Gemini API Key is not configured" in res.error

def test_nonexistent_pdf_handling():
    """Req 2: Non-existent PDF returns graceful error."""
    res = extract_timetable_data("non_existent_file.pdf", provider="layout_parser")
    assert res.success is False
    assert res.error is not None
