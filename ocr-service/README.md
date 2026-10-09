# OCR service (optional)

A small FastAPI service that recognises text in **scanned** timetable PDFs.

## Why it exists

Apache PDFBox reads the text a PDF actually contains. A timetable that was scanned, or exported as
page images, contains none. PDFBox will happily return an empty string for such a page, and an
empty timetable is worse than no timetable, because it looks like real data.

The backend handles this honestly: it detects pages with too little text, marks them as needing
OCR, and stops. It never invents rows for a page it could not read. This service is what an
administrator can optionally point it at to read those pages.

## Why it is a separate service

PaddleOCR needs large native libraries. Keeping them out of the JVM means:

- the backend builds and runs on a machine with no OCR support at all,
- a broken or slow OCR engine cannot take the lookup endpoint down with it,
- upgrading the engine never touches the application.

## Running it

```bash
cd ocr-service
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt

# macOS needs Poppler, which renders PDF pages into images:
brew install poppler

# Install the OCR engine itself (large download, first run only):
pip install "paddleocr>=2.9,<3" "paddlepaddle>=2.6"

uvicorn app.main:app --port 8081
```

Check it is up:

```bash
curl http://localhost:8081/health
# {"status":"ok"}
```

## Connecting it to the backend

In `backend/.env`:

```
OCR_ENABLED=true
OCR_BASE_URL=http://localhost:8081
OCR_TIMEOUT_SECONDS=120
```

Restart the backend. Upload the scanned PDF again and the pages that need OCR will be processed
instead of being reported as a failure.

## The contract

`POST /ocr` with the raw PDF as the body. The optional `X-Pages` header lists the 1-based page
numbers the backend found lacking embedded text, for example `1,3`.

```json
{
  "pages": [
    { "page_number": 1, "text": "Mo 1. 9:30 - 10:25 ...", "confidence": 0.9137 }
  ],
  "engine": "paddleocr"
}
```

`confidence` is the engine's own mean score for the page, never adjusted upward. The backend stores
it next to every extracted row and shows it in the review screen.

## What this service will never do

- It never decides what a row means. Parsing and validation stay in the backend.
- It never publishes anything. A reviewed and approved row is still required.
- It never rounds a low confidence up to make a page look better than it is.

## Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| `503 PaddleOCR is not installed` | `pip install "paddleocr>=2.9,<3" "paddlepaddle>=2.6"` then restart. |
| `PDF to image conversion failed` | Poppler is missing. `brew install poppler` (macOS) or `apt install poppler-utils`. |
| Very slow, or the process is killed | OCR is memory hungry. Lower `OCR_DPI` to 200, and raise `OCR_MAX_PAGES` carefully. |
| Garbled text | The scan is poor quality. Raise `OCR_DPI` to 400 for that document; a human may still need to correct the rows. |
| Backend reports `OCR_NOT_CONFIGURED` | `OCR_ENABLED` is not `true`, or the backend was not restarted after changing it. |
