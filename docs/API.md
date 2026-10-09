# REST API

Base URL in development: `/api`, proxied by the Vite dev server to
`http://localhost:8080`. Live documentation, once signed in, is at
`http://localhost:8080/swagger-ui.html`; the machine readable description is at `/v3/api-docs`.

## Authentication and CSRF

1. `GET /api/auth/csrf` returns a token. Spring Security also writes it to the readable
   `XSRF-TOKEN` cookie.
2. `POST /api/auth/login` with the token in the `X-XSRF-TOKEN` header. On success the browser
   receives an **HttpOnly** `JSESSIONID` cookie.
3. Every later `POST`, `PUT`, `PATCH` or `DELETE` sends `X-XSRF-TOKEN` with the cookie value.
   `GET` requests need no token.

There is no registration endpoint. The first administrator is created from environment variables at
startup; later accounts are created by an administrator.

## Errors

Every failure has the same shape. No stack trace, SQL or configuration value ever appears in a
response.

```json
{
  "code": "TIMETABLE_OVERLAP",
  "message": "This period overlaps an existing period on FRIDAY.",
  "details": ["Overlapping entry types: CLASS"],
  "path": "/api/timetables/12/entries",
  "timestamp": "2026-10-09T04:15:00Z"
}
```

| Status | Meaning |
| --- | --- |
| 400 | Malformed or invalid input |
| 401 | Not signed in, or invalid credentials |
| 403 | Signed in, but the role does not permit it |
| 404 | No such record |
| 409 | Conflict: duplicate, or a read only record |
| 413 | Upload larger than the configured limit |
| 422 | Well formed but cannot be processed: invalid PDF, nothing to approve, not publishable |
| 429 | Rate limited; the `Retry-After` header says for how long |

Common codes: `INVALID_CREDENTIALS`, `STUDENT_NOT_FOUND`, `REGISTRATION_REQUIRED`, `NOT_A_PDF`,
`OCR_NOT_CONFIGURED`, `TIMETABLE_OVERLAP`, `TIMETABLE_NOT_PUBLISHABLE`, `NOTHING_TO_APPROVE`,
`RATE_LIMITED`.

---

## Authentication

### `POST /api/auth/login`

```json
{ "username": "admin", "password": "..." }
```

```json
{
  "user": { "id": 1, "username": "admin", "displayName": "...", "role": "ADMIN" },
  "message": "Signed in."
}
```

`400` missing fields, `401` wrong credentials (the message never says which part was wrong),
`403` disabled account, `429` too many attempts.

### `POST /api/auth/logout`

Invalidates the session. `204`.

### `GET /api/auth/me`

The signed in account, or `401`.

### `GET /api/auth/csrf`

```json
{ "token": "...", "headerName": "X-XSRF-TOKEN", "cookieName": "XSRF-TOKEN", "parameterName": "_csrf" }
```

---

## Timetable lookup

These endpoints answer the question the system exists for. All of them default to *now* in the
configured college timezone. `at` accepts an ISO local date time such as `2026-10-09T10:32` and
exists for historical lookup and for testing; using it is recorded in the audit trail.

### `GET /api/lookup/clock`

```json
{ "date": "2026-10-09", "time": "15:42", "dayName": "FRIDAY",
  "timezone": "Asia/Kolkata", "instant": "2026-10-09T10:12:00Z" }
```

### `GET /api/lookup/student?reg=UG/02/BTCSEAIML/2023/024`

Registration numbers contain slashes, so they travel as a query parameter. The path form
`/api/lookup/student/UG/02/BTCSEAIML/2023/024` is also accepted.

```json
{
  "status": "CLASS_IN_PROGRESS",
  "message": "Scheduled: Cloud Computing from 09:30 to 10:25.",
  "evaluatedAt": "2026-10-09T04:45:00Z",
  "collegeTimezone": "Asia/Kolkata",
  "collegeDate": "2026-10-09",
  "collegeTime": "10:45",
  "dayName": "FRIDAY",
  "student": {
    "registrationNumber": "UG/02/BTCSEAIML/2023/024",
    "sectionId": 4,
    "fullName": null,
    "departmentName": "Computer Science and Engineering",
    "programName": "B.Tech CSE AI-ML",
    "programCode": "BTCSEAIML",
    "semesterNumber": 5,
    "academicYear": "2025-26",
    "sectionName": "D",
    "active": true
  },
  "currentClass": {
    "subjectCode": "CSE11036",
    "subjectName": "Cloud Computing",
    "facultyName": "Prof. A Sen",
    "roomCode": "AU6-4304",
    "roomBuilding": "Academic Block 6",
    "dayOfWeek": 5,
    "dayName": "FRIDAY",
    "startTime": "09:30",
    "endTime": "10:25",
    "entryType": "CLASS",
    "timetableId": 3,
    "timetableVersion": 2
  },
  "nextClass": { "...": "same shape" },
  "nextClassOnLaterDay": false,
  "timetable": {
    "id": 3, "version": 2, "status": "PUBLISHED",
    "effectiveFrom": "2026-08-01", "effectiveTo": null, "sourceFilename": "sem5.pdf"
  },
  "notices": [],
  "options": [],
  "conflictingClasses": []
}
```

### `GET /api/lookup/section`

Accepts identifiers, free text, or both.

| Parameter | Meaning |
| --- | --- |
| `sectionId` | Exact section |
| `programId` | Programme, from `GET /api/programs` |
| `semester` | 1 to 12 |
| `section` | Section label |
| `academicYear` | For example `2025-26` |
| `q` | Free text: `B.Tech CSE AI-ML, semester 5, section D` |
| `reg` | A registration number, handled as a student lookup |
| `at` | Instant to evaluate |

Resolves exactly the same way as the student endpoint, with `student` null. When several verified
sections match, the response is `AMBIGUOUS_SEARCH` and `options` lists the choices; the API never
picks one.

### `GET /api/lookup/section/{sectionId}/week`

The whole published week, plus `status` and `statusMessage` explaining whether a published
timetable was in force for the requested date.

### `GET /api/lookup/next-class?reg=...` or `?sectionId=...`

```json
{ "status": "NO_CLASS_NOW", "nextClass": { "...": "..." }, "onLaterDay": true, "student": { "...": "..." } }
```

### Lookup statuses

| Status | Meaning |
| --- | --- |
| `CLASS_IN_PROGRESS` | A published timetable exists and a `CLASS` entry contains the instant |
| `NO_CLASS_NOW` | A published timetable exists, nothing is scheduled at this instant |
| `BREAK` | An explicitly declared break contains the instant |
| `OTHER_IN_PROGRESS` | An `OTHER` block (assembly, library) contains the instant |
| `NO_TIMETABLE` | No timetable covers this section on this date |
| `TIMETABLE_NOT_PUBLISHED` | A draft is in force today and nobody has approved it |
| `STUDENT_NOT_FOUND` | No student matches that registration number |
| `STUDENT_INACTIVE` | The record exists but is deactivated |
| `AMBIGUOUS_SEARCH` | The search matched more than one verified section |
| `TIMETABLE_CONFLICT` | Two or more entries claim the same instant: a data fault |

These describe the **schedule only**. None of them asserts anything about a student's presence.

---

## Students

| Method | Path | Role | Notes |
| --- | --- | --- | --- |
| `GET` | `/api/students/by-registration?reg=...` | any | Single student |
| `GET` | `/api/students/search?q=...&page=0&size=25` | any | Partial registration number, paginated |
| `GET` | `/api/students/section/{sectionId}` | any | Enrolled students |
| `POST` | `/api/students` | ADMIN | `{ registrationNumber, fullName?, sectionId, active? }` |
| `PUT` | `/api/students/{id}` | ADMIN | Full update |
| `PATCH` | `/api/students/{id}/status` | ADMIN | `{ "active": false }` |

`409` when the registration number already exists, in any spacing or case.

---

## Catalog

Read access for every signed in user; writes are `ADMIN` only.

```
GET  /api/departments          POST /api/departments        PUT /api/departments/{id}
GET  /api/programs             POST /api/programs           PUT /api/programs/{id}
GET  /api/academic-terms       POST /api/academic-terms     PUT /api/academic-terms/{id}
GET  /api/sections             POST /api/sections           PUT /api/sections/{id}
GET  /api/subjects             POST /api/subjects           PUT /api/subjects/{id}
GET  /api/faculties            POST /api/faculties          PUT /api/faculties/{id}
GET  /api/rooms                POST /api/rooms              PUT /api/rooms/{id}
```

---

## Timetable management

Read access for every signed in user; all writes are `ADMIN` only.

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/timetables?sectionId=` | All versions |
| `GET` | `/api/timetables/{id}` | One version with its periods |
| `GET` | `/api/timetables/{id}/validate` | Whether it can be published |
| `POST` | `/api/timetables` | Create a draft: `{ sectionId, effectiveFrom, effectiveTo?, sourceFilename? }` |
| `PUT` | `/api/timetables/{id}` | Change effective dates |
| `POST` | `/api/timetables/{id}/entries` | Add a period |
| `PUT` | `/api/timetables/{id}/entries/{entryId}` | Replace a period |
| `DELETE` | `/api/timetables/{id}/entries/{entryId}` | Remove a period |
| `POST` | `/api/timetables/{id}/publish` | Approve for use |
| `POST` | `/api/timetables/{id}/rollback` | Restore an earlier published version |

A period:

```json
{
  "dayOfWeek": 5,
  "startTime": "09:30",
  "endTime": "10:25",
  "subjectId": 12,
  "facultyId": 4,
  "roomId": 3,
  "entryType": "CLASS",
  "rawSourceText": "Cloud Computing (CSE11036)"
}
```

`409 TIMETABLE_OVERLAP` when the period intersects an existing one. `409` when the version is
already published, because published versions are read only.

`POST /api/timetables/{id}/publish` runs in one transaction: the previous published version is
closed the day before the new one starts and marked `SUPERSEDED`, so history stays queryable. If
validation fails it returns `422 TIMETABLE_NOT_PUBLISHABLE` with every blocking problem listed.

---

## PDF import (ADMIN only)

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/imports` | `multipart/form-data`, field `file`. Extracts and returns the job |
| `GET` | `/api/imports` | Import history |
| `GET` | `/api/imports/{id}` | One job |
| `GET` | `/api/imports/{id}/candidates` | Extracted rows for review |
| `PUT` | `/api/imports/{id}/candidates/{candidateId}` | Correct or accept a row |
| `DELETE` | `/api/imports/{id}/candidates/{candidateId}` | Discard a row |
| `POST` | `/api/imports/{id}/approve` | Create a **draft** from the accepted rows |
| `POST` | `/api/imports/{id}/publish?timetableId=` | Publish that draft |

`422 NOT_A_PDF` when the upload is not a PDF (checked by extension, content type **and** magic
bytes). `422 OCR_NOT_CONFIGURED` when a page has no embedded text and OCR is off. `422
NOTHING_TO_APPROVE` when no row has been reviewed.

See [PDF_IMPORT.md](PDF_IMPORT.md) for the workflow.

---

## Administration (ADMIN only)

```
GET   /api/admin/users
POST  /api/admin/users              { username, password, displayName?, role }
PATCH /api/admin/users/{id}/status  { active }
POST  /api/admin/users/{id}/reset-password  { newPassword }
GET   /api/admin/audit-logs?limit=100
GET   /api/admin/audit-logs/{entityType}/{entityId}
```

Passwords must be at least 12 characters with some variety.

---

## Health

`GET /actuator/health` is public and needs no authentication, for uptime checks. Everything else
under `/actuator` is not exposed.