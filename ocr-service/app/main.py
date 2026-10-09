"""
Optional OCR service for scanned timetable PDFs.

Why this exists
---------------
Apache PDFBox can only read text that a PDF actually contains. A timetable that was scanned or
exported as images has none, so the backend cannot extract a single cell from it. Something has to
read the pixels, and PaddleOCR is the most accurate open engine that runs on a laptop without a
GPU.

Why it is a separate process
----------------------------
PaddleOCR pulls in heavy native dependencies. Keeping them out of the JVM means the backend stays
easy to build and easy to run, and a machine with no OCR support at all can still run the whole
rest of the system.

Honesty about the output
-----------------------
OCR is a guess. The service returns the engine's own confidence for each page and never rounds it
up. The backend stores that number next to every extracted row, and an administrator must review
and correct the rows before anything can be published. This service never decides anything.
"""

import io
import logging
import os
from typing import Any, Dict, List, Optional

from fastapi import FastAPI, Header, HTTPException, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

logger = logging.getLogger("college-timetable-ocr")

MAX_UPLOAD_BYTES = int(os.environ.get("OCR_MAX_UPLOAD_BYTES", str(40 * 1024 * 1024)))
MAX_PAGES = int(os.environ.get("OCR_MAX_PAGES", "100"))
DPI = int(os.environ.get("OCR_DPI", "300"))
LANGUAGES = os.environ.get("OCR_LANGUAGES", "en")

app = FastAPI(
    title="College Timetable OCR",
    version="1.0.0",
    description="Optional OCR fallback for scanned timetable PDFs.",
)

# The engine is loaded once, lazily. Loading takes seconds, so it must not happen at import time
# in a way that would break a health check.
_engine = None  # type: Optional[Any]


class OcrPage(BaseModel):
    """One page of recognised text plus the engine's own confidence."""

    page_number: int = Field(ge=1)
    text: str
    confidence: float = Field(ge=0.0, le=1.0)


class OcrResponse(BaseModel):
    pages: List[OcrPage]
    engine: str


def get_engine():  # type: -> Any
    """Loads PaddleOCR on first use and reuses it afterwards."""
    global _engine
    if _engine is None:
        try:
            from paddleocr import PaddleOCR  # type: ignore
        except ImportError as exc:  # pragma: no cover - depends on the environment
            raise HTTPException(
                status_code=503,
                detail=(
                    "PaddleOCR is not installed. Install it with "
                    "'pip install -r requirements.txt' and restart this service."
                ),
            ) from exc

        logger.info("Loading PaddleOCR, languages=%s", LANGUAGES)
        _engine = PaddleOCR(lang=LANGUAGES, use_angle_cls=True, show_log=False)
    return _engine


def pdf_page_count(pdf_bytes):  # type: (bytes) -> int
    """Counts pages with a minimal parse, so a huge file is rejected before it is rendered."""
    try:
        import pypdf

        reader = pypdf.PdfReader(io.BytesIO(pdf_bytes))
        return len(reader.pages)
    except HTTPException:
        raise
    except Exception as exc:  # pragma: no cover - malformed input
        raise HTTPException(
            status_code=422, detail=f"The PDF could not be read: {exc}"
        ) from exc


@app.get("/health")
def health():  # type: () -> Dict[str, str]
    """Liveness probe. Deliberately does not load the OCR engine."""
    return {"status": "ok"}


@app.post("/ocr", response_model=OcrResponse)
async def ocr(
    request: Request,
    x_pages: Optional[str] = Header(default=None, alias="X-Pages"),
) -> OcrResponse:
    """
    Recognises text in a PDF.

    The body is the raw PDF. The optional ``X-Pages`` header lists the 1-based page numbers the
    backend found lacking embedded text, for example ``1,3``. When it is absent every page is
    processed.

    Returns the recognised text per page together with the engine's own confidence. A low
    confidence is reported as-is: the point of this endpoint is to tell the truth about how much a
    human still has to check.
    """
    pdf_bytes = await request.body()
    if not pdf_bytes:
        raise HTTPException(status_code=400, detail="The request body was empty.")
    if len(pdf_bytes) > MAX_UPLOAD_BYTES:
        raise HTTPException(
            status_code=413,
            detail=f"The file is larger than the {MAX_UPLOAD_BYTES} byte limit.",
        )

    page_count = pdf_page_count(pdf_bytes)
    if page_count > MAX_PAGES:
        raise HTTPException(
            status_code=422,
            detail=f"This PDF has {page_count} pages; the limit is {MAX_PAGES}.",
        )

    wanted = _requested_pages(x_pages, page_count)
    engine = get_engine()

    try:
        import numpy as np
        import pdf2image
    except ImportError as exc:  # pragma: no cover - depends on the environment
        raise HTTPException(
            status_code=503,
            detail="pdf2image and numpy are required. Install requirements.txt and restart.",
        ) from exc

    images = pdf2image.convert_from_bytes(pdf_bytes, dpi=DPI)
    pages: List[OcrPage] = []
    for page_number in wanted:
        result = engine.ocr(np.array(images[page_number - 1]), cls=True)
        text, confidence = _flatten(result)
        pages.append(OcrPage(page_number=page_number, text=text, confidence=confidence))
        logger.info("page %d: %d characters, confidence %.2f", page_number, len(text), confidence)

    if not pages:
        raise HTTPException(status_code=422, detail="No pages were processed.")

    return OcrResponse(pages=pages, engine="paddleocr")


def _requested_pages(x_pages, page_count):  # type: (Optional[str], int) -> List[int]
    if not x_pages:
        return list(range(1, page_count + 1))
    try:
        wanted = sorted({int(part) for part in x_pages.split(",") if part.strip()})
    except ValueError as exc:
        raise HTTPException(
            status_code=400, detail="X-Pages must be a comma separated list of page numbers."
        ) from exc
    if not wanted:
        return list(range(1, page_count + 1))
    out_of_range = [page for page in wanted if page < 1 or page > page_count]
    if out_of_range:
        raise HTTPException(
            status_code=422,
            detail=f"Requested pages {out_of_range} do not exist; the PDF has {page_count}.",
        )
    return wanted


def _flatten(result):  # type: (Any) -> tuple
    """
    Reduces PaddleOCR's nested result to plain text and one honest confidence value.

    PaddleOCR returns, per image, a list of lines; each line is ``[box, (text, score)]``. The
    confidence reported here is the mean across every recognised line. If part of the page was
    illegible, the average says so rather than hiding it behind the best reading on the page.
    """
    lines: list[str] = []
    scores: list[float] = []

    try:
        for page in result or []:
            for line in page or []:
                if not line or len(line) < 2 or not line[1]:
                    continue
                text, score = line[1][0], float(line[1][1])
                if text:
                    lines.append(text)
                    scores.append(score)
    except (TypeError, ValueError):
        logger.warning("Unexpected OCR result shape; returning empty text", exc_info=True)

    if not scores:
        return "", 0.0
    return "\n".join(lines), round(sum(scores) / len(scores), 4)


@app.exception_handler(HTTPException)
async def http_exception_handler(_: Request, exc: HTTPException) -> JSONResponse:
    return JSONResponse(status_code=exc.status_code, content={"detail": exc.detail})