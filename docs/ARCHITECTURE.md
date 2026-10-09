# Architecture

## What this system is

A lookup tool for one question: **which class is this student scheduled to attend right now?**

A staff member meets a student on campus, types a registration number, and sees the expected
class: subject, faculty, room and time, or a clear statement that nothing is scheduled.

## What this system is not

It is not an attendance system. This distinction runs through the whole design:

- A timetable says where a student is *expected*. It cannot show where they *are*.
- Nothing in the product uses the words "absent", "bunking", "missing" or "violation" to describe
  a student. A test in the frontend (`src/utils/presentation.test.ts`) fails the build if any of
  those words appear in a status message.
- A free period is reported as "No class is scheduled". It is never rendered as a problem.
- No lookup endpoint can cancel a class, grant permission, or record that a class happened. Those
  are facts the system does not have and cannot infer.

## Shape of the system

A modular monolith: one Spring Boot application, one MySQL database, one React front end.

```
Browser
  |
  |  same origin: the SPA is served by the same host as /api,
  |  so the session cookie and CSRF header work without CORS
  v
Vite / static files  ──►  React 19 + TypeScript + Tailwind
                                 |
                                 |  REST over HTTP/JSON, session cookie + CSRF
                                 v
                        Spring Boot 3.5 (Java 21)
                                 |
        +------------------------+------------------------+
        |                        |                        |
   lookup engine          PDF import               administration
   (deterministic)        (PDFBox 3 -> review)      (catalog, publishing)
        |                        |                        |
        +------------------------+------------------------+
                                 |
                          MySQL 8 + Flyway
                                 ^
                                 |
                    optional OCR service (separate process)
```

Splitting this into microservices would add network failure modes to a tool whose entire value is
answering a question in under a second. The one genuinely hard boundary, OCR, is separated because
it needs heavy native libraries, not because it changes often.

## Backend modules

| Package | Responsibility |
| --- | --- |
| `config` | College timezone, security chain, CORS, OpenAPI, bootstrap admin, demo seeder |
| `security` | Principal, user details, rate limiting filter, JSON entry points |
| `service.lookup` | **The core.** Version selection, the decision engine, section search |
| `service.student` | Student records, with authoritative section mapping |
| `service.timetable` | Versioned timetables: drafts, validation, publishing, rollback |
| `service.pdf` | PDFBox extraction, grid parsing, cell parsing, OCR client |
| `service.importreview` | The import workflow: upload, review, approve into a draft |
| `service.admin` | Reference data: departments, programmes, terms, sections, subjects, rooms |
| `service.audit` | Append-only trail of administrative and authentication activity |

Controllers hold no business logic. Entities are never returned from the API: every response is a
DTO in `com.college.timetable.dto`.

## The lookup engine

This is the part worth reading the code for.

### Only approved data is read

`TimetableSelectionService` returns a timetable only when it is `PUBLISHED` **and** in force on the
requested date (`effective_from <= date <= effective_to`, both inclusive). A newer draft can never
shadow an older published timetable, because drafts are filtered out before selection happens.

When nothing can be selected, the engine distinguishes two situations a staff member needs to hear
differently:

| Situation | Status returned |
| --- | --- |
| No timetable row exists for this section at all | `NO_TIMETABLE` |
| A draft exists and is in force today, but nobody approved it | `TIMETABLE_NOT_PUBLISHED` |
| A published version exists, but it does not cover this date | `NO_TIMETABLE` |

### Intervals are half open

`start_time <= instant < end_time`. A class from 10:00 to 11:00 is running at 10:30 and finished at
11:00. This is implemented in `TimetableEntry.contains` and tested at both boundaries.

### Overlaps are a data fault, not a choice

If two entries claim the same instant, the engine returns `TIMETABLE_CONFLICT` and lists both. It
never picks one, because picking one would look authoritative while being a guess.

### Time comes from the clock, never from `LocalDate.now()`

Every service injects `java.time.Clock`, bound to the configured college timezone. Tests pin it to a
fixed instant, which is why the suite gives the same answer on a Friday at 5pm as it does at 3am on
a Sunday. There is no `Instant.now()` in the lookup path.

## Authentication

A server side session, not a token in JavaScript storage:

- the session id lives in an **HttpOnly** cookie, so a cross site scripting bug cannot exfiltrate it;
- because the credential is a cookie, **CSRF protection stays on**. Spring Security writes a
  readable `XSRF-TOKEN` cookie and the SPA echoes it in `X-XSRF-TOKEN`;
- sessions can be invalidated server side;
- public registration does not exist. The first administrator is created by an environment
  variable at startup, once.

Two roles: `STAFF` and `ADMIN`. Enforced by URL rules in `SecurityConfig` and independently by
`@PreAuthorize` on the write endpoints. The frontend's route guard is a usability measure, not the
security boundary.

## Rate limiting

`InMemoryRateLimiter` is a small sliding window counter applied to `/api/auth/login` and
`/api/lookup`. It is per instance, which is the right trade for a single application server; a
multi-instance deployment should put a real limiter in front instead. Its limits are configurable
and can be disabled with `RATE_LIMIT_ENABLED=false`.

## Search

Deterministic, in three stages:

1. `SearchQueryParser` reads what was literally typed: `semester 5`, `5th semester`, `fifth
   semester`, `section D`, `2025-26`. A bare letter is deliberately **not** read as a section.
2. `SectionSearchService` resolves the course text against the verified programme list, comparing
   with punctuation and spacing removed, plus administrator declared aliases.
3. If more than one verified section still matches, the response is `AMBIGUOUS_SEARCH` with the
   candidates listed, and the user chooses. The system never picks for them, because the wrong
   section means the wrong students' timetable.

No language model is involved, and none is required. Any future language model assistance would be
validated against database records before it could affect a lookup.

## PDF import

The full pipeline is described in [PDF_IMPORT.md](PDF_IMPORT.md). The load-bearing ideas:

- PDFBox text extraction is **checked**, not assumed. A page with too little text is flagged for OCR
  rather than being treated as an empty timetable.
- Geometry, not text order, drives parsing. Timetable grids lose their structure in plain text
  extraction, so words are grouped into rows and columns from their positions.
- Parser output is a proposal. Every row keeps the raw source text, a confidence and a list of what
  could not be recognised.
- Approval creates a **draft**; publishing is a separate, explicit step. OCR output is never
  published automatically.

## Frontend

React 19, Vite, TypeScript in strict mode, Tailwind CSS v4, React Router, React Hook Form, Zod,
Axios, Lucide.

- `AuthProvider` holds only *who* is signed in. The credential is in a cookie the browser cannot
  read, so a reload is the source of truth.
- `useAsync` gives every screen the same loading, error and cancellation behaviour.
- Recent lookups live in `sessionStorage` only. A list of who staff have been asking about is
  student data, and no screen needs it on a server.
- Tailwind's utility classes are used directly. There is no component library, so the UI stays
  small and every screen reads the same way.

## Accessibility

Semantic HTML throughout, a skip link, visible focus rings that are never removed, labelled inputs
with `aria-describedby` wiring, `role="alert"` for errors and `role="status"` for progress, and
keyboard operable tables and dialogs. Status is never conveyed by colour alone: every badge has
text.

## Testing strategy

| Layer | What it proves |
| --- | --- |
| Unit tests (JUnit, Mockito) | Boundary conditions, normalisation, parsing, PDF extraction |
| Integration tests (Spring Boot + H2 in MySQL mode) | The complete workflow against the real schema |
| Security tests (MockMvc) | Role separation, CSRF, and that errors leak nothing |
| Frontend tests (Vitest, Testing Library) | Form validation, every result state, API failure, role gating |

The test database is H2 in MySQL compatibility mode, and the **same Flyway migrations** run there
as in production. That is why the migrations avoid engine and charset clauses: it is what makes the
suite possible without a running MySQL, while still exercising the real DDL.

## Deliberate limitations

- The grid parser understands layouts where time slots sit in a header row and weekdays in the left
  column. That covers the common case; other templates need a new `ColumnBoundaryDetector`.
- Faculty matching is by name or staff code. Where neither is present, the field stays null rather
  than being guessed.
- Rate limiting is per instance.
- There is no offline mode. A lookup needs the server, because the server holds the timetable.