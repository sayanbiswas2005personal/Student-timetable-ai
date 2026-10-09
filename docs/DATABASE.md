# Database

MySQL 8.x. The schema is owned by **Flyway**; Hibernate is configured with `ddl-auto: none` and
never creates or alters a table.

- Migrations: `backend/src/main/resources/db/migration`
- Single baseline: `V1__baseline_schema.sql`

## Applying migrations

Flyway runs automatically when the backend starts. To apply them without starting the application:

```bash
cd backend
mvn flyway:migrate -Dflyway.url="$DB_URL" -Dflyway.user="$DB_USERNAME" -Dflyway.password="$DB_PASSWORD"
```

Rollback is deliberately not automated: dropping a timetable table would destroy published data.
To undo a change, write a new `V2__...sql` that reverses it.

## Portability rule

The migrations contain **no engine or charset clauses**. MySQL 8 defaults to InnoDB and utf8mb4, so
omitting them lets the identical file run against the H2 MySQL-compatibility database the test suite
uses. Adding `ENGINE=InnoDB` or `DEFAULT CHARSET=utf8mb4` would still work on MySQL but would break
`mvn test` on a machine without a database.

## Tables

### Reference data

| Table | Purpose | Notable constraints |
| --- | --- | --- |
| `departments` | Academic departments | `code` unique |
| `programs` | Degree programmes | `code` unique; FK to department |
| `academic_terms` | One semester of one academic year | unique `(academic_year, semester_number)`; check semester 1-12 |
| `sections` | A student group | unique `(program_id, academic_term_id, section_name)` |
| `subjects` | Teachable subjects | `subject_code` unique; `program_id` nullable (shared subjects) |
| `faculties` | Teaching staff, optional | `faculty_code` unique |
| `rooms` | Classrooms and laboratories | `room_code` unique |

### Students

`students` holds `registration_number` (exactly as printed) and
`registration_number_normalized` (the search key). The normalized column is unique, which is what
stops `"ug/02/a/2023/1"` and `"UG/02/A/2023/1"` both being created as different people.

Normalization removes only cosmetic differences: surrounding whitespace, non-breaking and zero-width
spaces, repeated whitespace, and case. **Punctuation is never altered**, so
`UG/02/A/2023/1` cannot match `UG/02/A/2023/10`.

`section_id` is mandatory. The lookup engine reads the student's section from this column and
never derives it from the registration number, because no verified rule exists that would allow it.

### Timetables

| Table | Purpose |
| --- | --- |
| `timetables` | A versioned weekly timetable for one section |
| `timetable_entries` | The periods inside one version |

`timetables` has unique `(section_id, version)` and status in
`DRAFT | UNDER_REVIEW | PUBLISHED | SUPERSEDED`. Only `PUBLISHED` rows are read by the lookup
engine.

`effective_from` and `effective_to` are inclusive. `effective_to IS NULL` means open ended.

Publishing sets the previous published version's `effective_to` to the day before the new one
starts, so a historical lookup still returns the timetable that was in force on that date.

`timetable_entries` uses `day_of_week` 1 (Monday) to 7 (Sunday), matching
`java.time.DayOfWeek#getValue()`. A check constraint enforces `end_time > start_time`. Overlap is
**not** a constraint: overlapping entries are real data faults that the system reports
(`TIMETABLE_CONFLICT`) so an administrator can fix them, rather than silently rejecting writes.

`subject_id`, `faculty_id` and `room_id` are nullable. Many published timetables do not name the
faculty, and the system must work without it. `raw_source_text` keeps the cell text the row came
from, so a reviewer can compare the stored value against the source document.

### Import workflow

`import_jobs` tracks one uploaded PDF: internal filename, status, page counts, how many pages need
OCR, and the timetable it eventually produced. `stored_filename` is always a generated name;
`filename` is the display name only and is never used to build a path.

`import_candidates` holds one extracted row per cell, with the raw text, the parser's full field
extraction as JSON, a confidence value, the reviewer's decision and the resolved foreign keys.

### Security and audit

`app_users` stores a BCrypt hash (cost 12). Plaintext passwords are never stored or logged.

`audit_logs` is append-only and records administrative changes plus authentication attempts,
including failed sign ins. It deliberately holds no credentials and no bulk student data.

## Relationships

```
departments 1─* programs 1─* sections *─1 academic_terms
                       │        │
                       │        ├──* students
                       │        ├──* timetables 1─* timetable_entries
                       │        └──* import_candidates
                       └──* subjects

timetable_entries *─1 subjects / faculties / rooms   (all nullable)
import_candidates *─1 import_jobs
```

## Indexes

Every foreign key is indexed, plus:

- `timetables (section_id, status, effective_from, effective_to)` for the lookup hot path;
- `timetable_entries (timetable_id, day_of_week, start_time)` for day queries;
- `timetable_entries (section_id, day_of_week)` for section views;
- `students (section_id)` for enrolment lists;
- `import_candidates (import_job_id, page_number)` for review ordering;
- `audit_logs` on user, entity and timestamp.

## Inspecting the data

```bash
./scripts/mysql-client.sh college_timetable
```

```sql
-- Which timetable is in force for a section today?
SELECT t.id, t.version, t.status, t.effective_from, t.effective_to
FROM timetables t
JOIN sections s ON s.id = t.section_id
WHERE s.section_name = 'D' AND t.status = 'PUBLISHED'
ORDER BY t.version DESC;

-- Find overlapping entries, which the lookup engine would report as a conflict.
SELECT e1.day_of_week, e1.start_time, e1.end_time, e2.id
FROM timetable_entries e1
JOIN timetable_entries e2
  ON e1.timetable_id = e2.timetable_id
 AND e1.id < e2.id
 AND e1.day_of_week = e2.day_of_week
 AND e1.start_time < e2.end_time
 AND e2.start_time < e1.end_time;

-- Recent administrative activity.
SELECT occurred_at, username, action, entity_type, entity_id, details
FROM audit_logs
ORDER BY occurred_at DESC
LIMIT 50;
```

## Backup

```bash
mysqldump --socket="$HOME/.local/var/mysql-timetable/mysql.sock" \
  -u root college_timetable > backup-$(date +%F).sql
```

The database contains student registration numbers, so backups are as sensitive as the student
directory itself and belong under the college's normal data protection rules.
## Times are wall clock, not instants

`hibernate.jdbc.time_zone` is deliberately **not** set. Setting it makes Hibernate bind `LocalTime`
as a UTC instant, so a 09:30 class is written to the `TIME` column as 04:00 and the same shift is
undone when it is read back. Nothing in Java can see the difference, but the stored bytes are wrong:
a report, a spreadsheet export or any other tool that reads the column directly gets the wrong time.

`TimetableTimeStorageIntegrationTest` asserts against the column through plain JDBC rather than
through the entity, because an entity-level assertion passes whether or not the value was shifted.
Audit timestamps are `Instant` and are unaffected; they are stored in UTC and returned with a `Z`.
