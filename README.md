# StudentAI.com — Smart Campus Timetable & Student Verification System

StudentAI.com is a production-grade campus timetable and student schedule verification platform built for teachers, faculty members, and academic administrators.

It enables faculty to instantly check expected class schedules, current lecture progress, room assignments, and upcoming classes for any student section, while empowering administrators to upload institutional timetable PDFs, extract structured JSON with Google Gemini 3.8 Flash (or the high-precision offline Layout Parser), inspect/edit the timetable grid, and publish approved schedules.

---

## Key Features

### 1. Faculty Verification Portal
- **Intuitive Dropdown Search**: Instant lookup by Department, Academic Year, Semester, and Section (no typing errors).
- **Time Engine with Institutional Timezone**: Matches server time converted to `Asia/Kolkata` (default campus timezone) using the exact matching condition `start_time <= current_time < end_time`.
- **Live IST Clock & Simulation Sandbox**: Live campus clock display with a built-in interactive **Time Simulator** allowing faculty to test any date and time (e.g., Monday 10:00 AM, Tuesday 2:30 PM, holidays, recess).
- **Status Classification**:
  - `Scheduled class in progress` (Subject, Subject Code, Faculty, Room, Timing)
  - `Free period` (Shows next upcoming lecture and countdown)
  - `Lunch break / Campus Recess`
  - `Before / After college hours`
  - `Institutional Holiday`
  - `Class Cancelled / Faculty Substitution / Special Class`
  - `Timetable Conflict` (flags overlapping classes)
- **Mandatory Policy Notice**: Explicitly emphasizes that timetables establish expected class schedules and do not replace individual attendance rolls.

### 2. Admin Dashboard & Timetable Lifecycle
- **PDF-to-JSON Timetable Processing**:
  - **Google Gemini 3.8 Flash Cloud API**: Direct PDF understanding through the official Google Gemini SDK (`google-genai`).
  - **Deterministic Layout Parser**: Zero-dependency local coordinate table parser for offline/demo operation without OCR.
- **Interactive Timetable Editor**: Inspect, edit individual cells, add/remove rows, and review validation errors/warnings before publishing.
- **Two-Step Approval & Publishing**: Timetables are never published automatically; admin approval is mandatory and executed via an atomic database transaction.
- **Timetable Archives & Rollbacks**: View all timetable versions, deactivate active releases, or roll back.
- **Schedule Overrides Engine**: Manage full-day holidays, emergency cancellations, and faculty substitutions.
- **User & Department Management**: Manage faculty/admin accounts and academic structures.
- **Audit Logs**: Tracks all upload, extraction, update, and publishing actions.

---

## Technology Stack

- **Frontend**: React 18, TypeScript, Vite, Tailwind CSS, Lucide React icons, React Router DOM.
- **Backend**: Python 3.11+ / 3.12, FastAPI, Pydantic v2, SQLAlchemy, Uvicorn.
- **Database**: SQLite for local development (`studentai.db`), fully compatible with PostgreSQL.
- **PDF Processing**: Google Gemini API (`google-genai` SDK with `gemini-3.8-flash`) + Layout Parser fallback. No local OCR (Tesseract/EasyOCR) dependencies.
- **Security**: JWT authentication, bcrypt password hashing, role-based authorization (`FACULTY`, `ADMIN`).

---

## Project Structure

```text
Student-timetable-ai/
│
├── backend/
│   ├── app/
│   │   ├── api/                   # REST API routes (auth, search, admin_timetables, etc.)
│   │   ├── core/                  # Security (JWT, bcrypt), config, dependencies
│   │   ├── database/              # SQLAlchemy session & init_db seeding
│   │   ├── models/                # Database models (User, Timetable, Entries, Overrides, etc.)
│   │   ├── schemas/               # Pydantic validation schemas
│   │   ├── services/              # timetable_engine, pdf_extractor, json_validator
│   │   └── main.py                # FastAPI app entry point & SPA static file serving
│   ├── tests/                     # 17 automated tests covering all 11 required test cases
│   └── requirements.txt
│
├── frontend/
│   ├── src/
│   │   ├── components/            # Navbar, Live Clock
│   │   ├── context/               # AuthContext (JWT management)
│   │   ├── layouts/               # AdminLayout with responsive sidebar
│   │   ├── pages/                 # FacultySearchPage, LoginPage, Admin pages
│   │   ├── services/              # Typed API service client
│   │   ├── App.tsx
│   │   └── main.tsx
│   ├── index.html
│   ├── package.json
│   ├── tailwind.config.js
│   └── vite.config.ts
│
├── storage/
│   └── timetables/                # Safe PDF storage directory
│
├── Section wise Timetable V15.pdf # Institutional timetable sample (52 sections)
├── .env.example
├── .gitignore
└── README.md
```

---

## Quick Start (Installation & Setup)

### Prerequisites
- Python 3.11 or Python 3.12
- Node.js 18+ and npm

### 1. Environment Configuration

Copy `.env.example` to `.env`:

```bash
cp .env.example .env
```

Default configuration in `.env`:
```env
# Optional: Google Gemini API Key for Cloud PDF extraction
GEMINI_API_KEY=
GEMINI_MODEL=gemini-3.8-flash

# Database (SQLite default for zero-config local dev)
DATABASE_URL=sqlite:///./studentai.db

# Security
SECRET_KEY=studentai-secret-development-key-2026-secure-jwt
ACCESS_TOKEN_EXPIRE_MINUTES=1440

# Campus Timezone
TIMEZONE=Asia/Kolkata
```

> **Note**: When `GEMINI_API_KEY` is omitted, the application seamlessly runs in demo/local mode using the built-in Layout Parser, fully parsing the included sample `Section wise Timetable V15.pdf`.

---

### 2. Backend Setup

#### On macOS / Linux:
```bash
# Create virtual environment
python3 -m venv .venv
source .venv/bin/activate

# Install dependencies
pip install -r backend/requirements.txt

# Initialize database and default seed data
PYTHONPATH=. python -m backend.app.database.init_db
```

#### On Windows (Command Prompt / PowerShell):
```powershell
# Create virtual environment
python -m venv .venv
.venv\Scripts\activate

# Install dependencies
pip install -r backend\requirements.txt

# Initialize database and default seed data
set PYTHONPATH=.
python -m backend.app.database.init_db
```

---

### 3. Frontend Setup

```bash
cd frontend
npm install
npm run build
cd ..
```

---

## Running the Application

### Option A: Unified Full-Stack Server (Recommended)
FastAPI serves both the REST API and the compiled React frontend on a single port:

```bash
# Activate virtual environment
source .venv/bin/activate   # On Windows: .venv\Scripts\activate

# Start server
PYTHONPATH=. uvicorn backend.app.main:app --host 0.0.0.0 --port 8000 --reload
```

Open your browser at **[http://localhost:8000](http://localhost:8000)**.

### Option B: Vite Development Mode
Run the backend and Vite dev server concurrently with hot module replacement:

1. Terminal 1 (Backend):
   ```bash
   PYTHONPATH=. uvicorn backend.app.main:app --port 8000 --reload
   ```

2. Terminal 2 (Frontend):
   ```bash
   cd frontend
   npm run dev
   ```
   Open **[http://localhost:5173](http://localhost:5173)** (Vite proxies `/api` calls to port 8000).

---

## Default Demo Accounts

Quick-login credentials available with 1-click on the Sign-In page:

| Role | Email | Password | Access Level |
|---|---|---|---|
| **Faculty** | `faculty@studentai.com` | `faculty123` | Student Timetable Search & Verification |
| **Admin** | `admin@studentai.com` | `admin123` | Full Dashboard, PDF Upload, Extraction, Publishing |

---

## Running the Automated Test Suite

Run the full pytest suite covering all 11 required scenarios:

```bash
PYTHONPATH=. pytest -v backend/tests
```

### Test Coverage Checklist:
1. `test_valid_timetable_json` — JSON schema compliance & normalization.
2. `test_invalid_time_ordering` — Validation fails when `start_time >= end_time`.
3. `test_missing_mandatory_fields` — Validation fails on missing structure or entries.
4. `test_unresolved_markers_warning` — Unresolved markers produce warnings.
5. `test_layout_parser_sample_pdf_extraction` — Tests coordinate table extraction on `Section wise Timetable V15.pdf`.
6. `test_gemini_extractor_missing_key_graceful_handling` — Graceful fallback when API key is missing.
7. `test_nonexistent_pdf_handling` — Graceful handling for missing files.
8. `test_department_and_section_matching` — Department, Year, Semester, Section lookup.
9. `test_current_time_class_matching` — Verifies `start_time <= current_time < end_time` boundary conditions.
10. `test_free_period_detection` — Detects free periods between scheduled classes.
11. `test_next_class_calculation` — Calculates next scheduled lecture and start time.
12. `test_timezone_handling` — Verifies `Asia/Kolkata` time conversion.
13. `test_holiday_and_schedule_overrides` — Full-day holidays, cancelled lectures, and substitutions.
14. `test_expired_timetable_detection` — Flags timetables that have passed their expiration date.
15. `test_conflicting_timetable_entries` — Flags overlapping entries for the same section.
16. `test_login_flow` — JWT token generation and authentication.
17. `test_role_authorization_enforcement` — Protects admin routes with 403 Forbidden for faculty.

---

## API Documentation

When the backend is running, interactive Swagger API documentation is available at:
- **Swagger UI**: [http://localhost:8000/docs](http://localhost:8000/docs)
- **ReDoc**: [http://localhost:8000/redoc](http://localhost:8000/redoc)

