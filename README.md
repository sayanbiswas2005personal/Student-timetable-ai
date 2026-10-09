<div align="center">
  <h1>🏫 College Timetable Lookup System</h1>
  <p><strong>A modern, verified timetable lookup tool for college staff and administration.</strong></p>
</div>

<hr/>

A staff member meets a student on campus and needs to know which class they are supposed to be in right now. They type a registration number, and the system says: *Cloud Computing, 09:30 to 10:25, room AU6-4304* — or *no class is scheduled at this time*.

> [!WARNING]
> **This is a timetable lookup tool, not an attendance system.** A timetable says where a student is *expected*. It cannot show where they *are*, whether they have permission, or whether a class was cancelled. Nothing in this product claims otherwise, and a frontend test fails the build if any status message uses the words "absent", "bunking", "missing" or "violation".

## 📑 Contents
- [✨ What it does](#-what-it-does)
- [🚀 Quick start](#-quick-start)
- [📂 Project layout](#-project-layout)
- [🧪 Running the tests](#-running-the-tests)
- [⚙️ Configuration](#-configuration)
- [📚 Documentation](#-documentation)
- [🔧 Troubleshooting](#-troubleshooting)

## ✨ What it does

### 🧑‍🏫 For staff
- **Look up a student** by registration number.
- **Look up a section** by course, semester and section, including free text such as *"B.Tech CSE AI-ML, semester 5, section D"*.
- **See the expected class right now:** subject, faculty, room, start and end time.
- **See the next scheduled class,** including when it falls on a later day.
- **See a whole weekly section timetable** with a current time indicator.

### 🛡️ For administrators
- **Manage students:** Enrol, edit, search for and deactivate students.
- **Manage college data:** Departments, programmes, academic terms, sections, subjects, faculty and rooms.
- **Upload timetables:** Upload timetable PDFs, review what was extracted, correct it, and turn it into a draft timetable.
- **Publish & Rollback:** Publish a timetable, and roll back to an earlier published version.
- **Audit trail:** Read an audit trail of every administrative change and sign in attempt.

### 🚫 Deliberately not
- No attendance, no "is this student skipping class", no enforcement.
- No invented data. Nothing enters a published timetable without a human approving it.
- No language model in the lookup path. Every answer comes from a verified database record.

## 🚀 Quick start

Full detail, including the MySQL setup, is in **[docs/SETUP.md](docs/SETUP.md)**.

### 1. Prerequisites
JDK 21+, Maven 3.9+, Node.js 20+, MySQL 8. ([SETUP.md](docs/SETUP.md) has the exact commands for macOS.)

### 2. Database
With Docker:
```bash
docker compose up -d
```
Without Docker, using the bundled tarball install (data lives under `~/.local`, nothing in this directory):
```bash
./scripts/mysql-start.sh
```

### 3. Backend
```bash
cd backend
cp .env.example .env
# Set DB_URL, DB_USERNAME, DB_PASSWORD, and for the first run only:
#   BOOTSTRAP_ADMIN_ENABLED=true
#   BOOTSTRAP_ADMIN_USERNAME=admin
#   BOOTSTRAP_ADMIN_PASSWORD=a-long-password-of-12-or-more-characters

set -a; source .env; set +a
mvn spring-boot:run
```
Flyway creates the schema on first start. Check it is up:
```bash
curl http://localhost:8080/actuator/health
```
Then remove `BOOTSTRAP_ADMIN_PASSWORD` from the environment: it is only needed for the very first start.

### 4. Frontend
```bash
cd frontend
cp .env.example .env.local
npm install
npm run dev
```
Open <http://localhost:5173>.

### 5. See it work
Add `SEED_DEMO_DATA=true` to `backend/.env` and restart. That inserts a small dataset where every name is labelled `(TEST DATA)`, so you can try the workflow before any real college data exists.
Sign in and look up `TEST/UG/01/BTCSEAIML/2023/001`.

> [!CAUTION]
> The seeded timetable deliberately leaves one slot free on Wednesday and Thursday, so you can see a free period as well as a class. **Never enable this against real data.**

## 📂 Project layout

```
college-timetable-system/
├── backend/                 Spring Boot 3.5, Java 21
│   ├── src/main/java/com/college/timetable/
│   │   ├── config/          timezone, security, CORS, OpenAPI, bootstrap, demo data
│   │   ├── security/        principal, user details, rate limiting
│   │   ├── controller/      REST endpoints, one per area
│   │   ├── dto/             request and response shapes
│   │   ├── entity/          JPA entities
│   │   ├── repository/      Spring Data repositories
│   │   ├── service/
│   │   │   ├── lookup/      ** the timetable lookup engine **
│   │   │   ├── timetable/   versions, validation, publishing, rollback
│   │   │   ├── pdf/         PDFBox extraction, grid parsing, OCR client
│   │   │   ├── importreview/ the review and approval workflow
│   │   │   ├── student/     student records
│   │   │   ├── admin/       reference data
│   │   │   └── audit/       audit trail
│   │   ├── exception/       one error shape for every failure
│   │   └── util/            normalisation, time parsing, safe uploads
│   └── src/main/resources/db/migration/   Flyway
├── frontend/                React 19, Vite, TypeScript, Tailwind CSS v4
│   └── src/
│       ├── api/             typed API client, auth provider
│       ├── components/      common, layout, search, timetable, admin
│       ├── hooks/           async state, college clock, recent lookups
│       ├── pages/           one file per screen
│       ├── types/           the API contract, typed
│       └── utils/           status presentation
├── ocr-service/             optional PaddleOCR service for scanned PDFs
├── docs/                    architecture, database, API, PDF import, setup
├── scripts/                 local MySQL management
└── docker-compose.yml       MySQL for development
```

## 🧪 Running the tests

```bash
# Backend: 184 tests, no external service required
cd backend && mvn test

# Frontend: 27 tests
cd frontend && npm run test

# Frontend quality gates
cd frontend && npm run typecheck && npm run lint && npm run build
```

The backend suite uses an in-memory H2 database in MySQL compatibility mode, running the **same Flyway migrations** as production. That is why the migrations avoid engine and charset clauses, and it means the schema is verified on every test run rather than only on first deployment.

Every time assertion is pinned to a fixed instant, so the suite gives the same answer today as it will in three years. No test depends on the wall clock.

## ⚙️ Configuration

Backend configuration comes from environment variables; `backend/.env.example` documents all of them. The ones that matter most:

| Variable | Default | Purpose |
| --- | --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | local MySQL | Database connection |
| `COLLEGE_TIMEZONE` | `Asia/Kolkata` | **Every** date and time calculation |
| `SEED_DEMO_DATA` | `false` | Loads clearly labelled synthetic data |
| `BOOTSTRAP_ADMIN_*` | disabled | Creates the first administrator, once |
| `RATE_LIMIT_ENABLED` | `true` | Throttling on login and lookup |
| `IMPORT_MAX_FILE_SIZE`, `IMPORT_MAX_PAGES` | 25 MB, 100 | Upload limits |
| `OCR_ENABLED`, `OCR_BASE_URL` | disabled | OCR fallback for scanned PDFs |

Frontend configuration is `VITE_API_BASE_URL`, which defaults to `/api` so the dev server proxies to the backend. Nothing is hard coded to a production host.

**No secret is committed.** `.env` is gitignored; only `.env.example`, which contains placeholders, is tracked.

## 📚 Documentation

| Document | Contents |
| --- | --- |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Design decisions and why they were made |
| [docs/DATABASE.md](docs/DATABASE.md) | Schema, constraints, indexes, useful queries |
| [docs/API.md](docs/API.md) | Every endpoint, with examples |
| [docs/PDF_IMPORT.md](docs/PDF_IMPORT.md) | The import workflow and what it refuses to guess |
| [docs/SETUP.md](docs/SETUP.md) | Step by step setup for macOS, plus troubleshooting |
| [ocr-service/README.md](ocr-service/README.md) | The optional OCR service |

## 🔧 Troubleshooting

### Java version problems
```bash
mvn -version   # shows which JDK Maven actually uses
```
`Unsupported class file major version` means Maven is on an old JDK:
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

### Maven errors
- `mvn: command not found` → `brew install maven`.
- Download failures → check `~/.m2/settings.xml` for a proxy.

### Node and npm problems
- `npm: command not found` → `brew install node`, then open a new terminal.
- Corrupted install → `rm -rf node_modules && npm install`.

### MySQL connection errors
- `Communications link failure` → the server is not running: `docker compose ps`, or `./scripts/mysql-start.sh`.
- `Access denied for user` → check `DB_USERNAME` and `DB_PASSWORD`.
- `Unknown database` → create the schema; see [SETUP.md](docs/SETUP.md).
- `Public Key Retrieval is not allowed` → add `allowPublicKeyRetrieval=true` to `DB_URL`.

### Port conflicts
```bash
lsof -ti:8080 | xargs kill    # backend
lsof -ti:5173 | xargs kill    # frontend
./scripts/mysql-stop.sh       # MySQL, script install
```

### CORS errors
In development you should not get one: `VITE_API_BASE_URL` stays as `/api` and the dev server proxies it. For a split deployment, set `CORS_ALLOWED_ORIGINS` on the backend.

### PDF extraction failures
- `NOT_A_PDF` on a real PDF → it is an image, or the file was altered in transit.
- No rows found → the layout is not the template the parser understands. See [PDF_IMPORT.md](docs/PDF_IMPORT.md).
- Every page needs OCR → the PDF is a scan. See [ocr-service/README.md](ocr-service/README.md).

## 📊 Current state

| Component | Status |
| --- | --- |
| Backend, 184 tests | Passing ✅ |
| Frontend, 27 tests, typecheck, lint, build | Passing ✅ |
| Verified against MySQL 8.4.6 end to end | Yes ✅ |
| Timetable lookup, versioning, publishing, rollback | Implemented and tested ✅ |
| PDF import with PDFBox, review and approval | Implemented and tested ✅ |
| Parser verified against a real 52-page college timetable | Yes, see [docs/PDF_IMPORT.md](docs/PDF_IMPORT.md) ✅ |
| OCR service | Implemented, **not** enabled by default (needs PaddleOCR installed) ⚠️ |
| Real college data | **Not loaded.** Every dataset in this repository is synthetic and labelled 🛡️ |