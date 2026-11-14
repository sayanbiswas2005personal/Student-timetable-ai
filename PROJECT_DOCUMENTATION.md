# StudentAI.com — Full Project Documentation

## 1) Project overview

StudentAI.com is a full-stack campus timetable and verification system designed for academic institutions, faculty members, and administrators. The platform combines:

- a faculty-facing timetable lookup interface,
- an admin console for managing academic data and timetable versions,
- a PDF-based timetable ingestion pipeline,
- a validation and normalization system for raw timetable JSON,
- a schedule engine that maps the current time to the correct timetable state,
- a role-based authentication and authorization system,
- an audit trail for administrative actions.

The core use case is simple but important: faculty can look up a section's timetable and know whether students are in a class, in a free period, in a holiday, or in a conflict state. Administrators can upload a PDF timetable, extract structured schedule data, verify it, publish it, and manage overrides.

The repository is intentionally organized into two major parts:

- backend/: Python + FastAPI service and database layer
- frontend/: React + TypeScript + Vite UI

There is also a root sample PDF file and a local SQLite database to support offline/local development.

---

## 2) What the project does in plain terms

### Faculty-facing side
Faculty users log in and can query a department/year/semester/section combination to get the current status of the schedule. The application calculates:

- whether a class is currently in progress,
- whether there is a free period,
- whether the section is on holiday,
- whether the timetable is expired,
- whether there are schedule conflicts,
- what the next class will be,
- whether a cancellation or substitution override applies.

It is built to support campus operations and decision support, not attendance automation.

The README explicitly states the policy:

- timetables represent expected class schedules,
- they are not a replacement for attendance register systems,
- faculty must still use legitimate attendance/roll records when required.

### Admin-facing side
Administrators manage the data pipeline:

1. Upload a timetable PDF
2. Extract entries using either:
   - Google Gemini cloud extraction, or
   - local layout-parsing fallback
3. Validate timetable JSON and detect errors or warnings
4. Review/modify extracted data
5. Publish the timetable to a live version
6. Manage department and academic structures
7. Apply holiday/cancellation/substitution overrides
8. Review audit logs and runtime settings

---

## 3) High-level architecture

The system is a classic layered application:

- Presentation Layer: React frontend
- API Layer: FastAPI routers
- Service Layer: timetable extraction, validation, schedule engine
- Data Layer: SQLAlchemy models + SQLite database
- Security Layer: JWT + bcrypt
- File Storage Layer: uploaded PDF files saved under storage/timetables

### Main runtime flow

1. User logs in from the frontend.
2. Frontend sends requests to FastAPI endpoints under /api.
3. FastAPI authenticates via JWT and resolves the current user.
4. The backend maps the request to a router module.
5. SQLAlchemy queries or inserts data into the SQLite database.
6. Service modules validate data or compute timetable logic.
7. Frontend renders the answer in a user-friendly status card or admin dashboard.

---

## 4) Technology stack

### Backend
- Python 3.11+/3.12
- FastAPI
- SQLAlchemy
- Pydantic V2
- SQLite (local database)
- JWT (PyJWT)
- bcrypt
- pypdf
- google-genai (optional for Gemini-based extraction)
- pytz

### Frontend
- React 18
- TypeScript
- Vite
- Tailwind CSS
- React Router DOM
- Lucide icons

### Runtime behavior
- The backend serves both API and static frontend when built assets exist.
- In development, the frontend can run separately under Vite and proxy API requests.
- In production-style use, the backend may serve static assets built by the frontend.

---

## 5) Repository structure and responsibilities

```text
Student-timetable-ai/
├── README.md
├── PROJECT_DOCUMENTATION.md
├── Section wise Timetable V15.pdf
├── studentai.db
├── .env.example
├── backend/
│   ├── requirements.txt
│   ├── app/
│   │   ├── main.py
│   │   ├── api/
│   │   │   ├── auth.py
│   │   │   ├── search.py
│   │   │   ├── admin_academic.py
│   │   │   ├── admin_overrides.py
│   │   │   ├── admin_settings.py
│   │   │   ├── admin_timetables.py
│   │   │   ├── admin_users.py
│   │   │   └── audit.py
│   │   ├── core/
│   │   │   ├── config.py
│   │   │   ├── dependencies.py
│   │   │   └── security.py
│   │   ├── database/
│   │   │   ├── init_db.py
│   │   │   └── session.py
│   │   ├── models/
│   │   │   ├── academic_structure.py
│   │   │   ├── audit_log.py
│   │   │   ├── department.py
│   │   │   ├── schedule_override.py
│   │   │   ├── timetable.py
│   │   │   ├── timetable_entry.py
│   │   │   └── user.py
│   │   ├── schemas/
│   │   │   ├── academic_structure.py
│   │   │   ├── department.py
│   │   │   ├── search.py
│   │   │   ├── timetable.py
│   │   │   └── user.py
│   │   ├── services/
│   │   │   ├── json_validator.py
│   │   │   ├── pdf_extractor.py
│   │   │   └── timetable_engine.py
│   │   └── tests/
│   │       ├── conftest.py
│   │       ├── test_admin_settings.py
│   │       ├── test_auth_and_roles.py
│   │       ├── test_json_validator.py
│   │       ├── test_pdf_extractor.py
│   │       ├── test_search_metadata.py
│   │       └── test_timetable_engine.py
│   └── requirements.txt
├── frontend/
│   ├── index.html
│   ├── package.json
│   ├── postcss.config.js
│   ├── tailwind.config.js
│   ├── tsconfig.json
│   ├── tsconfig.node.json
│   ├── vite.config.ts
│   ├── vercel.json
│   └── src/
│       ├── App.tsx
│       ├── index.css
│       ├── main.tsx
│       ├── components/
│       │   └── Navbar.tsx
│       ├── context/
│       │   └── AuthContext.tsx
│       ├── layouts/
│       │   └── AdminLayout.tsx
│       ├── pages/
│       │   ├── FacultySearchPage.tsx
│       │   ├── LoginPage.tsx
│       │   └── admin/
│       │       ├── AdminAcademicPage.tsx
│       │       ├── AdminAuditPage.tsx
│       │       ├── AdminOverridesPage.tsx
│       │       ├── AdminOverviewPage.tsx
│       │       ├── AdminSettingsPage.tsx
│       │       ├── AdminTimetablesPage.tsx
│       │       ├── AdminUploadPage.tsx
│       │       └── AdminUsersPage.tsx
│       └── services/
│           └── api.ts
├── storage/
│   └── timetables/
│       └── 1793617788_Section wise Timetable V15.pdf
└── README.md
```

---

## 6) Core backend files explained in detail

### 6.1 backend/app/main.py
This is the application entrypoint for FastAPI.

It does the following:

- configures logging,
- initializes the DB during startup,
- creates the FastAPI app,
- adds CORS middleware,
- registers all routers,
- exposes a /api/health endpoint,
- serves static frontend assets if the built frontend exists,
- otherwise returns a simple JSON app status.

Important details:

- It includes routers for:
  - auth
  - search
  - admin_timetables
  - admin_academic
  - admin_users
  - admin_overrides
  - admin_settings
  - audit
- It mounts the frontend dist assets under /assets if available.
- It prevents API/docs routes from being incorrectly served as frontend pages.

### 6.2 backend/app/core/config.py
This file loads environment variables from a root .env file or default values.

Main configuration values:

- APP_NAME: StudentAI.com
- APP_VERSION: 1.0.0
- GEMINI_API_KEY
- GEMINI_MODEL
- DATABASE_URL
- SECRET_KEY
- ALGORITHM: HS256
- ACCESS_TOKEN_EXPIRE_MINUTES
- TIMEZONE: default Asia/Kolkata
- STORAGE_DIR
- MAX_UPLOAD_SIZE_MB

This is central configuration and is imported by multiple modules.

### 6.3 backend/app/core/security.py
Responsible for:

- bcrypt password hashing,
- password verification,
- JWT creation with expiry,
- JWT decoding.

This is the security layer used by both login and route access control.

### 6.4 backend/app/core/dependencies.py
Defines FastAPI dependency functions:

- get_current_user: validates JWT token and loads active user
- require_faculty_or_admin: allows faculty and admin users
- require_admin: restricts routes to admin only

Most protected routes depend on these checks.

### 6.5 backend/app/database/session.py
This is the SQLAlchemy engine setup.

It:

- creates the SQLite engine,
- enables foreign keys for SQLite,
- creates a SessionLocal factory,
- sets up Base = declarative_base(),
- provides get_db() for dependency injection.

### 6.6 backend/app/database/init_db.py
This is the seed/bootstrap script for the app.

Responsibilities:

- create all database tables,
- create default admin user: admin@studentai.com / admin123,
- create default faculty user: faculty@studentai.com / faculty123,
- seed departments,
- seed academic structures,
- import a sample PDF timetable from the root Section wise Timetable V15.pdf,
- parse it using the layout parser,
- insert timetable entries into the database,
- publish a demo timetable when appropriate.

This file is extremely important because it turns an empty database into a working sample system instantly.

---

## 7) Backend model files by table

### 7.1 models/user.py
Table: users

Fields:

- id
- name
- email
- password_hash
- role: ADMIN or FACULTY
- department
- is_active
- created_at

Purpose:

- identifies users,
- allows authentication and route access checks,
- supports admin/faculty separation.

### 7.2 models/department.py
Table: departments

Fields:

- id
- name
- code
- is_active

Purpose:

- stores academic departments such as CSE, BCA, MCA, ECE.

### 7.3 models/academic_structure.py
Table: academic_structures

Fields:

- id
- department_code
- academic_year
- year
- semester
- section

Purpose:

- stores combinations that exist in the academic system,
- supports dropdowns and faculty queries,
- tags timetable entries into valid academic sections.

### 7.4 models/timetable.py
Table: timetables

Fields:

- id
- original_filename
- file_path
- file_size_bytes
- uploaded_at
- academic_year
- institution
- department
- valid_from
- valid_until
- processing_status
- processing_error
- extraction_provider
- extracted_json
- is_published
- version
- approved_by_user_id
- approved_at

Purpose:

- represents a timetable document version,
- stores metadata and extracted timetable JSON,
- tracks publish state and versioning,
- supports admin review and rollback concepts.

Processing statuses:

- PENDING
- PROCESSING
- EXTRACTED
- ERROR
- PUBLISHED

### 7.5 models/timetable_entry.py
Table: timetable_entries

Fields:

- id
- timetable_id
- day
- start_time
- end_time
- department
- year
- semester
- section
- subject_code
- subject
- faculty
- room
- notes

Purpose:

- stores one class slot in the timetable database,
- used to compute current status, next class, free periods, conflicts, and overrides.

It also creates an index on department, year, semester, section, and day for fast retrieval.

### 7.6 models/schedule_override.py
Table: schedule_overrides

Fields:

- id
- date
- department
- year
- semester
- section
- override_type
- start_time
- end_time
- subject
- faculty
- room
- reason
- created_by_user_id
- created_at

Override types:

- CANCELLED
- SUBSTITUTION
- RESCHEDULED
- SPECIAL_CLASS
- HOLIDAY

Purpose:

- allows date-specific modifications to the real schedule,
- used for emergency closures, special classes, substitution faculty, and full-day holidays.

### 7.7 models/audit_log.py
Table: audit_logs

Fields:

- id
- user_id
- user_email
- action
- details
- timestamp

Purpose:

- tracks admin actions like upload, extraction, publication, user changes, settings updates, and more.

---

## 8) Backend schemas

The schemas directory contains Pydantic models that describe both request bodies and response payloads.

### schemas/user.py
Defines user/session response models and token payloads.

### schemas/search.py
Defines the response schema for current-class queries, including:

- current_status
- status_label
- status_badge_color
- current_class
- next_class
- today_schedule
- warnings
- override_applied
- has_conflict
- conflicting_entries

This is the API contract drives the faculty dashboard.

### schemas/timetable.py
This is the most important schema layer. It includes:

- TimetableEntrySchema
- NormalizedTimetableJSON
- Timetable validation models
- admin preview and update request models

This structure is used throughout extraction, validation, and publishing.

### schemas/department.py
Department response representation.

### schemas/academic_structure.py
Academic structure response representation.

---

## 9) Backend service layer

### 9.1 services/json_validator.py
This module is the gatekeeper for timetable correctness.

It validates:

- required root fields,
- time ordering: start_time must be before end_time,
- day names must be valid,
- missing values are normalized,
- unresolved values are flagged,
- schedule overlaps are warned about,
- timetable entries are structured into valid Pydantic models.

Important functions:

- time_to_minutes(string_time)
- validate_and_normalize_timetable_json(raw_json)

This is crucial because malformed extracted timetable data must never be published without review.

### 9.2 services/pdf_extractor.py
This file is responsible for converting an uploaded timetable PDF into structured JSON.

It has two extraction strategies:

1. GeminiCloudExtractor
   - Uses the official Google Gemini SDK
   - Requires GEMINI_API_KEY if this provider is used
   - Can extract and parse a PDF via LLM-based document understanding

2. LayoutParserExtractor
   - Uses PDF text extraction and geometric/coordinate logic
   - Works offline without OCR dependencies
   - Designed for demo or local academic PDFs

The main dispatcher function is:

- extract_timetable_data(pdf_path, provider=None, api_key=None, page_limit=None)

Behavior:

- if provider == gemini, use Gemini
- if provider == layout_parser, use layout parser
- otherwise try Gemini if configured; else fall back to local parser

This gives a robust “cloud-or-local” extraction pipeline.

### 9.3 services/timetable_engine.py
This is the core business logic of schedule evaluation.

It calculates the institution’s current timetable state. It is the engine behind the faculty search result.

Key behaviors:

- converts server time to campus timezone (default Asia/Kolkata),
- optionally accepts simulated_date and simulated_time for testing,
- finds the published active timetable,
- checks timetable validity/expiry,
- checks day-specific or section-specific overrides,
- looks for timetable conflicts,
- checks whether there is a scheduled class in progress,
- detects free periods,
- calculates next class,
- handles lunch break logic,
- handles holidays, cancellations, substitutions, and special classes.

The core logic is:

- current class: start_time <= current_time < end_time
- next class: the next entry with start_time greater than now

This is the heart of the app.

---

## 10) API routes and responsibilities

The backend exposes all endpoints under /api.

### 10.1 Authentication routes (/api/auth)

- POST /api/auth/login
  - accepts email and password
  - validates using bcrypt + JWT
  - returns token and user profile

- POST /api/auth/logout
  - client-side token invalidation signal

- GET /api/auth/me
  - returns authenticated user profile

### 10.2 Search routes (/api/search)

- GET /api/search/current-class
  - main faculty lookup endpoint
  - accepts department, year, semester, section
  - optionally accepts simulated_date and simulated_time
  - requires faculty/admin access

- GET /api/search/next-class
  - returns next class only

- GET /api/search/server-time
  - returns current campus time and timezone info

- GET /api/search/departments
  - returns active departments

- GET /api/search/academic-structures
  - returns valid year/semester/section combinations for a department

### 10.3 Admin timetable routes (/api/admin/timetables)

- GET /api/admin/timetables
  - list timetable records

- POST /api/admin/timetables/upload
  - upload PDF file
  - stores file under storage/timetables
  - creates database row

- POST /api/admin/timetables/{id}/extract
  - run extraction against a specific timetable PDF

- GET /api/admin/timetables/{id}/preview
  - preview extracted JSON + validation errors/warnings

- PUT /api/admin/timetables/{id}
  - edit timetable metadata/entries

- POST /api/admin/timetables/{id}/publish
  - validate and publish a timetable

### 10.4 Admin academic routes
Likely manage departments, academic structures, and academic records.

### 10.5 Admin users routes
Manage user accounts and roles.

### 10.6 Admin overrides routes
Manage schedule overrides such as holidays, cancellations, and substitutions.

### 10.7 Admin settings routes
Show or edit environment/system settings.

### 10.8 Audit routes
Show action logs and system history.

---

## 11) Frontend structure and responsibilities

### 11.1 frontend/src/App.tsx
This is the root router.

It defines routes:

- /login
- /
- /admin
- /admin/upload
- /admin/timetables
- /admin/departments or /admin/academic
- /admin/overrides
- /admin/users
- /admin/settings
- /admin/audit

It also protects routes with:

- ProtectedRoute: only authenticated users can access faculty features
- AdminProtectedRoute: only admin users can access admin pages.

### 11.2 frontend/src/context/AuthContext.tsx
This manages:

- login state,
- token persistence in localStorage,
- user object persistence,
- auth verification on app load,
- logout handling.

This is the app’s authentication state manager.

### 11.3 frontend/src/services/api.ts
This file is the TypeScript API client.

It defines:

- request helper for fetch calls,
- typed interfaces for users, timetable data, overrides, audit logs,
- endpoint wrappers for auth, search, admin, settings, etc.

It handles:

- Authorization: Bearer token from localStorage,
- auto-redirect to /login on 401,
- typed API communication between frontend and backend.

This is a major bridge between the UI and backend.

### 11.4 frontend/src/pages/LoginPage.tsx
This page is the default login screen.

It includes:

- email/password form,
- error handling,
- demo credentials buttons,
- role-aware navigation after login.

### 11.5 frontend/src/pages/FacultySearchPage.tsx
This is the main faculty portal page.

It likely provides:

- department dropdown,
- year and semester selection,
- section selection,
- simulated date/time controls,
- current class summary,
- next class summary,
- status badge display,
- warnings and conflict display,
- live clock / campus time information.

In other words, it is the frontend representation of the timetable engine results.

### 11.6 frontend/src/layouts/AdminLayout.tsx
The admin shell layout, likely including:

- side navigation,
- admin menu items,
- page content area,
- layout structure for all admin console screens.

### 11.7 frontend/src/pages/admin/*
These pages each correspond to an admin capability:

- AdminOverviewPage.tsx: overall dashboard summary
- AdminUploadPage.tsx: upload new PDF file
- AdminTimetablesPage.tsx: list and manage timetable versions
- AdminAcademicPage.tsx: departments and academic structure management
- AdminOverridesPage.tsx: schedule overrides management
- AdminUsersPage.tsx: user roles and accounts
- AdminAuditPage.tsx: audit logs and admin activity
- AdminSettingsPage.tsx: runtime configuration view/edit

### 11.8 frontend/src/components/Navbar.tsx
Likely provides the navigation top bar for the app.

---

## 12) How the timetable engine decides status

The timetable logic in timetable_engine.py is the core functionality of the project. It works like a finite-state classifier for a section’s schedule.

### Step-by-step decision sequence

1. Resolve the campus timezone and current date/time.
2. Find a published timetable for the relevant department.
3. Check for expired timetable versions.
4. Check date-specific overrides.
5. Pull the schedule for the current day and section.
6. Determine whether there is any class at this moment.
7. Decide between:
   - scheduled class in progress
   - free period
   - lunch break
   - no classes today
   - before college hours
   - after college hours
   - institutional holiday
   - timetable conflict
   - faculty substitution
   - class cancellation
   - special class

### Current class rule
The app uses this condition:

- start_time <= current_time < end_time

This is explicitly described in the project README and implemented in the engine.

### Conflict detection
The timetable engine warns if multiple entries overlap within the same day, department, semester, and section.

### Override precedence
An override can override the default schedule state based on the date and slot.

Examples:

- full-day holiday blocks everything,
- a cancelled class clears a scheduled period,
- a substitution changes the faculty/subject/room
- a special class modifies the schedule state with its own message.

---

## 13) PDF extraction and normalization pipeline

This is one of the strongest features of the project.

### Input
- A timetable PDF file in the form of a document.
- Example root file: Section wise Timetable V15.pdf

### Extraction providers

#### Gemini cloud path
- If GEMINI_API_KEY exists, the system attempts cloud extraction using the Google GenAI SDK.
- A document is uploaded to Gemini.
- A prompt asks the model to extract timetable entries into a JSON schema.
- Output is parsed as JSON.
- Success depends on the model and PDF quality.

#### Local fallback path
- When no API key is present or the cloud path fails, the system runs the LayoutParserExtractor.
- The parser extracts text and geometry from each PDF page.
- It identifies day slots, time slots, subjects, codes, faculty, room numbers, etc.
- It reconstructs a timetable-like data structure.

### Validation pass
After extraction, the system runs validate_and_normalize_timetable_json() to ensure:

- entries are correct objects,
- required fields exist,
- times are valid,
- start_time < end_time,
- unresolved placeholders are warnings,
- overlaps are flagged.

### Publishing
Validated timetable data can then be published to live use.

---

## 14) Database and local runtime details

### Database type
The project is built around SQLite for local development and functionality.

The app uses SQLAlchemy session management and the default database file:

- studentai.db

### Why SQLite works here
- zero-config local environment,
- easy prototype deployment,
- sufficient for academic scheduling demo use,
- easy to initialize with demo seed data.

### Production note
The README notes that the app is compatible with PostgreSQL, but local default is SQLite. For a persistent production deployment, an external database is recommended.

---

## 15) Storage and upload behavior

The app stores uploaded PDFs in:

- storage/timetables/

The workflow:

1. Admin uploads PDF using Multipart form upload.
2. File is saved to a sanitized path with a timestamp prefix.
3. File metadata is stored in the timetables table.
4. PDF is processed by the extraction service.
5. The file remains on disk and the DB stores its path.

This is important because the app can reprocess or preview earlier uploads without re-uploading the original file.

---

## 16) Security model

### Authentication
The app uses JWT tokens.

- On login, the backend verifies the password.
- If valid, it creates a signed JWT.
- The token carries user identity and role data.

### Authorization
Role rules:

- FACULTY: can search timetable information
- ADMIN: can upload, validate, publish, manage users, settings, overrides, and audit data

The dependency layer enforces access control with FastAPI Depends.

### Password hashing
Passwords are hashed using bcrypt before being stored.

### CORS
The FastAPI app allows all origins in development mode, which is common for local/demo usage but should be tightened for production.

---

## 17) Default demo accounts

The app seeds two users automatically in init_db.py.

| Role | Email | Password | Access |
|---|---|---|---|
| Admin | admin@studentai.com | admin123 | Full admin dashboard |
| Faculty | faculty@studentai.com | faculty123 | Timetable search portal |

This makes it easy to test the app without external configuration.

---

## 18) Workflow examples

### Example A: Faculty checks current class

1. Faculty logs in.
2. They select department, year, semester, and section.
3. Frontend calls GET /api/search/current-class.
4. Backend loads the published timetable and computes the schedule.
5. The engine checks:
   - current time in campus timezone,
   - existing schedule for the day,
   - overrides,
   - lunch windows,
   - next class,
   - warnings/conflicts.
6. Frontend renders a status card such as:
   - Scheduled class in progress
   - Free period
   - Institutional holiday
   - Class cancelled

### Example B: Admin uploads a timetable PDF

1. Admin logs in.
2. Admin selects the PDF and optional metadata.
3. Backend stores the file and creates a timetable DB record.
4. Admin triggers extraction.
5. The extraction service parses the PDF.
6. Validation identifies issues or warnings.
7. Admin reviews preview and updates entries if needed.
8. Admin publishes the timetable.
9. The timetable becomes available to faculty queries.

### Example C: A cancellation override

1. Admin creates a ScheduleOverride record with:
   - date
   - department
   - semester
   - section
   - override type CANCELLED
   - optional time range
   - reason
2. Faculty queries the schedule for that day.
3. The timetable engine sees the override and returns a cancelled class status instead of the normal class state.

---

## 19) Validation and publishing rules

The app has a strong editorial review pipeline before a timetable is considered live.

### Before publish
- timetable must exist,
- timetable must have extracted JSON,
- extracted data must pass validation,
- there must be at least one entry,
- no validation errors may remain,
- authorizations must be satisfied.

### Publishing behavior
The publish endpoint:

- loads the extracted JSON,
- validates it,
- creates or updates timetable data in normalized form,
- marks the timetable as published,
- deactivates older active timetables logically by versioning,
- stores entries in a clean database form.

This ensures the app does not publish broken or empty timetables.

---

## 20) Testing strategy and test files

The repository includes a backend test suite under backend/tests.

### Files
- conftest.py
- test_admin_settings.py
- test_auth_and_roles.py
- test_json_validator.py
- test_pdf_extractor.py
- test_search_metadata.py
- test_timetable_engine.py

### Main test areas
- login and role enforcement
- JSON validation
- PDF extraction logic
- timetable engine behavior
- metadata search logic
- admin settings and access rules

### Typical validation commands

```bash
PYTHONPATH=. pytest -v backend/tests
```

The README says the system is designed around 11 core required scenarios with broader automated coverage.

---

## 21) The sample timetable and demo data

The project includes a sample PDF:

- Section wise Timetable V15.pdf

It is also copied into storage/timetables for initial demo use.

The initializer script loads this file and seeds timetable entries automatically. This reduces setup friction and gives a working institutional dataset immediately.

The included dataset appears to contain many sections and multiple class periods across days.

---

## 22) Important implementation notes and design choices

### 22.1 Local-first demo design
The project was designed to work without heavy infrastructure. It intentionally uses:

- SQLite for local database,
- local parser fallback,
- default seeded users,
- sample PDF data,
- simplified but robust route protection.

### 22.2 Role-oriented product design
The system splits responsibilities across admin vs faculty, which mirrors real campus administration roles.

### 22.3 Time-zone awareness
The app is explicitly configured for Asia/Kolkata by default, and the timetable engine refers to campus time instead of raw server time.

### 22.4 Operational safety
The app separates data processing from data publishing. It validates before publish and tracks admin activity in audit logs.

### 22.5 Flexibility of extraction providers
The app supports both LLM extraction and local parser strategies, which reduces dependence on any single provider.

---

## 23) Deployment notes

### Local development
The project runs with:

- backend: uvicorn backend.app.main:app --reload
- frontend: npm run dev in frontend/

### Static build deployment
A built frontend can be served by the backend if the dist folder exists.

### Vercel deployment hints
The frontend is configured for static deployment via Vercel. The recommended pattern is:

- root directory: frontend/
- build command: npm run build
- output directory: dist
- VITE_API_BASE_URL points to the deployed backend URL

The backend is separate from the frontend deployment and requires a persistent database and file storage.

---

## 24) Common extension points

If this project is extended in the future, the most natural improvement areas are:

- add stronger timetable editing UI for bulk edits,
- add export to CSV/XLSX,
- add real academic-year management,
- add mail notifications for schedule changes,
- add student-facing portal access,
- add persistent PostgreSQL hosting,
- add multi-tenant department support,
- add richer analytics dashboards.

---

## 25) Summary

StudentAI.com is a practical timetable intelligence platform that blends:

- data collection from PDFs,
- timetable validation and normalization,
- schedule logic under campus time,
- role-based access,
- admin workflow processing,
- faculty-facing status checks,
- audit and version-tracking tools.

Its goal is to answer one very important daily academic question: “What is the current state of this section’s timetable right now?”

This is not just a CRUD application. It is a domain-specific operational system designed around institutional scheduling logic, validity rules, and faculty decision support.

---

## 26) Quick reference: most important files

- backend/app/main.py — app entrypoint, route registration, static serving
- backend/app/core/config.py — environment config
- backend/app/core/security.py — JWT and encryption
- backend/app/core/dependencies.py — auth/role checks
- backend/app/database/init_db.py — database bootstrap and sample data
- backend/app/services/pdf_extractor.py — PDF extraction logic
- backend/app/services/json_validator.py — timetable validation
- backend/app/services/timetable_engine.py — runtime class status logic
- backend/app/models/timetable.py — timetable metadata model
- backend/app/models/timetable_entry.py — class slot model
- backend/app/models/schedule_override.py — holiday and substitute model
- backend/app/api/search.py — faculty search logic
- backend/app/api/admin_timetables.py — admin timetable workflow
- frontend/src/App.tsx — app routing
- frontend/src/services/api.ts — frontend API layer
- frontend/src/context/AuthContext.tsx — auth state management
- frontend/src/pages/FacultySearchPage.tsx — main faculty UI
- frontend/src/pages/LoginPage.tsx — sign in screen

---

## 27) Final interpretation

This project behaves like an academic system turned into a lightweight internal campus platform. It is highly useful in a real institution because it centralizes timetable data, supports validation, and makes schedule status readily understandable for faculty. The combination of PDF parsing, time-based schedule evaluation, and role-based admin workflows makes it more than a UI wrapper—it is a real operational scheduling engine.

If you want, I can also produce a second, even more technical version of this document specifically focused on:

1. all API endpoints with request/response examples,
2. all database tables and field-level details,
3. a code-by-code architecture walkthrough,
4. a developer onboarding guide for new contributors.
