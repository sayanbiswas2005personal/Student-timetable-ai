# AU Student Timetable Intelligence System

A full-stack web application for student timetable intelligence, utilizing modern architecture and OCR capabilities.

## Architecture
The system consists of:
- **Backend**: FastAPI, SQLAlchemy, Alembic
- **Database**: PostgreSQL (currently configured to SQLite for rapid local testing without Docker)
- **Frontend**: React, Vite, TypeScript, Tailwind CSS
- **OCR Engine**: OpenCV + Tesseract + Google Gemini (for processing images into structured data)

```mermaid
graph TD
    A[Frontend React App] -->|REST API| B(FastAPI Backend)
    B --> C[(PostgreSQL)]
    B --> D[OpenCV OCR Pipeline]
    B --> E[Google Gemini AI]
    D -->|Extracted Tables| C
    E -->|Natural Language Explanation| A
```

## Features
- Search for classes by Registration and Roll Number
- Normalizes search input gracefully
- Shows "Current Class" and "Next Class" by referencing the exact server time
- Generates AI explanation of schedules
- Support for complex/merged table cells via computer vision bounding boxes

## Prerequisites
- **Python 3.9+** (Tested with 3.9)
- **Node.js** (Required for Vite React frontend)
- **PostgreSQL** (Or Docker to run the database)

## Setup Backend
```bash
cd student-timetable-ai
python3 -m venv venv
source venv/bin/activate
pip install -r backend/requirements.txt
cd backend
alembic upgrade head
cd ..
PYTHONPATH=backend python scripts/seed_database.py
cd backend
uvicorn app.main:app --reload
```

## Setup Frontend
```bash
cd student-timetable-ai/frontend
npm install
npm run dev
```

## Environment Variables
Create `.env` inside the `backend` folder:
```
DATABASE_URL=sqlite:///./timetable.db
GEMINI_API_KEY=your-api-key
PORT=8000
```
> [!NOTE]
> For PostgreSQL, change the `DATABASE_URL` to `postgresql://postgres:postgres@localhost:5432/timetable_db` and start the Docker container with `docker-compose up -d`.

## OCR Processing Pipeline
The timetable extraction (in `backend/app/services/ocr_service.py`) works by:
1. Converting the document to grayscale and applying adaptive thresholding.
2. Using morphological transformations in OpenCV to detect horizontal and vertical grid lines.
3. Calculating cell intersections to detect standard and merged cells.
4. Using Tesseract (or Gemini Vision) to extract content per-cell.

## Known Limitations
- The OCR pipeline requires `tesseract` to be installed on the host machine.
- Node.js must be installed to run the frontend bundle process.
- The `gemini-2.5-flash` model is configured; please provide a valid API key.
