# Backend

Spring Boot 3.5, Java 21, MySQL 8, Flyway.

```bash
cp .env.example .env
# Set DB_URL, DB_USERNAME and DB_PASSWORD, plus for the first run only:
#   BOOTSTRAP_ADMIN_ENABLED=true
#   BOOTSTRAP_ADMIN_USERNAME=admin
#   BOOTSTRAP_ADMIN_PASSWORD=a-long-password-of-12-or-more-characters

set -a; source .env; set +a
mvn spring-boot:run
```

Flyway creates the schema on first start.

| Command | Purpose |
| --- | --- |
| `mvn spring-boot:run` | Run the application |
| `mvn test` | 156 tests; no external service needed |
| `mvn package` | Build the executable jar |
| `mvn flyway:migrate` | Apply migrations without starting the app |

API documentation is at `/swagger-ui.html` once signed in.

Full setup and troubleshooting are in [`../docs/SETUP.md`](../docs/SETUP.md).

## Where things are

| Package | Responsibility |
| --- | --- |
| `config` | College timezone, security chain, CORS, OpenAPI, bootstrap admin, demo seeder |
| `security` | Principal, user details, rate limiting filter, JSON error entry points |
| `service.lookup` | The timetable lookup engine and version selection |
| `service.timetable` | Versioned timetables, validation, publishing, rollback |
| `service.pdf` | PDFBox text extraction, grid parsing, cell parsing, OCR client |
| `service.importreview` | Upload, review and approval of imported rows |
| `service.student` | Student records with an authoritative section mapping |
| `service.admin` | Reference data: departments, programmes, terms, sections, subjects, rooms |
| `service.audit` | Append-only trail of administrative and authentication activity |

## Conventions

- **No JPA entity is ever returned from the API.** Every response is a DTO.
- **No business logic in controllers.** Controllers validate, delegate and translate.
- **`hibernate.ddl-auto` is `none`.** Flyway owns the schema.
- **`java.time.Clock` is injected, never `Instant.now()`** in the lookup path, so tests can pin time.
- **Every service method that writes anything also writes an audit record.**
- **Only `PUBLISHED` timetable rows are ever read by the lookup engine.**