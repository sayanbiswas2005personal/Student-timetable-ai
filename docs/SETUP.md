# Setup on macOS

Exact commands for a clean Mac. Read [ARCHITECTURE.md](ARCHITECTURE.md) first if you want to know
what you are installing.

## 1. Prerequisites

You need four things: **JDK 21 or newer**, **Maven 3.9+**, **Node.js 20 or newer**, and **MySQL 8**.

Check what you already have:

```bash
java -version
mvn -version
node -v
mysql --version
```

### Java

```bash
brew install openjdk@21
```

Then, for the current shell only:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

To make it permanent, add to `~/.zshrc`:

```bash
echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 21)' >> ~/.zshrc
```

Spring Boot 3.5 compiles to Java 21 bytecode and also runs on newer JDKs.

### Maven

```bash
brew install maven
```

### Node.js

```bash
brew install node
```

### MySQL — pick one

**Option A, Docker** (simplest):

```bash
brew install --cask docker
docker compose up -d
```

**Option B, a MySQL server installed directly.** Useful when Docker will not run on the machine.
The repository ships scripts that manage a tarball install under `~/.local`, with no changes to the
project directory:

```bash
./scripts/mysql-start.sh     # first run downloads and initialises, then starts on port 3307
./scripts/mysql-stop.sh
./scripts/mysql-client.sh college_timetable
```

## 2. Create the database

With Docker, skip this: `docker-compose.yml` creates the schema and the user for you.

Without Docker:

```sql
CREATE DATABASE college_timetable CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE college_timetable_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'timetable_app'@'localhost' IDENTIFIED BY 'choose-a-real-password';
CREATE USER 'timetable_app'@'127.0.0.1' IDENTIFIED BY 'choose-a-real-password';
GRANT ALL PRIVILEGES ON college_timetable.*      TO 'timetable_app'@'localhost';
GRANT ALL PRIVILEGES ON college_timetable.*      TO 'timetable_app'@'127.0.0.1';
GRANT ALL PRIVILEGES ON college_timetable_test.* TO 'timetable_app'@'localhost';
GRANT ALL PRIVILEGES ON college_timetable_test.* TO 'timetable_app'@'127.0.0.1';
FLUSH PRIVILEGES;
```

`college_timetable_test` is only used if you run the integration tests against real MySQL; the
default test profile uses an in-memory database.

## 3. Configure the backend

```bash
cd backend
cp .env.example .env
```

Edit `.env`. The four values you must set:

```
DB_URL=jdbc:mysql://localhost:3306/college_timetable?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Kolkata&characterEncoding=utf8
DB_USERNAME=timetable_app
DB_PASSWORD=the-password-you-chose
COLLEGE_TIMEZONE=Asia/Kolkata
```

Add these once, to create the first administrator:

```
BOOTSTRAP_ADMIN_ENABLED=true
BOOTSTRAP_ADMIN_USERNAME=admin
BOOTSTRAP_ADMIN_PASSWORD=a-long-password-of-at-least-12-characters
```

Flyway creates every table on first start. There is no separate schema script to run.

### Loading configuration

`.env` is a convenience, not something Spring Boot reads by itself. Either export the values:

```bash
set -a; source .env; set +a
mvn spring-boot:run
```

or copy the values into your shell profile. The important rule is: **`.env` must never be
committed**. It is already in `.gitignore`, and only `.env.example` is tracked.

## 4. Run the backend

```bash
cd backend
set -a; source .env; set +a
mvn spring-boot:run
```

Watch for these lines:

```
Successfully applied 1 migration to schema `college_timetable`
Started CollegeTimetableApplication
Created the initial administrator account 'admin'
```

Then, in another terminal:

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP",...}
```

API documentation is at <http://localhost:8080/swagger-ui.html> once signed in.

**Delete `BOOTSTRAP_ADMIN_PASSWORD` from your environment now.** The account exists; the variable
is only for the very first start.

## 5. Configure and run the frontend

```bash
cd frontend
cp .env.example .env.local
npm install
npm run dev
```

Open <http://localhost:5173>.

`VITE_API_BASE_URL=/api` and the Vite dev server proxies `/api` to the backend, so the browser sees
a single origin. That is deliberate: the session cookie and CSRF header then behave exactly as they
do in production, and no CORS configuration is needed.

## 6. Try it with synthetic data

To explore the workflow before any real college data exists:

```
SEED_DEMO_DATA=true
```

Restart the backend. It inserts a small, clearly labelled dataset where every name carries
`(TEST DATA)`, for example `B.Tech CSE AI-ML (TEST DATA)` and registration numbers beginning
`TEST/`.

The seeded timetable leaves one slot free on Wednesday and Thursday on purpose, so the "no class
scheduled" result can be seen as well as the class result.

**Never enable this against real data.** It writes synthetic students into the college database.

Then sign in and try `TEST/UG/01/BTCSEAIML/2023/001`.

## 7. Run the tests

Backend, no external service needed:

```bash
cd backend
mvn test
```

Frontend:

```bash
cd frontend
npm run test
```

Quality checks:

```bash
cd frontend
npm run typecheck
npm run lint
npm run build
```

## 8. Optional: the OCR service

Only needed for scanned PDFs. See `ocr-service/README.md`.

```bash
cd ocr-service
python3 -m venv .venv && . .venv/bin/activate
pip install -r requirements.txt
brew install poppler
pip install "paddleocr>=2.9,<3" "paddlepaddle>=2.6"
uvicorn app.main:app --port 8081
```

Then set `OCR_ENABLED=true` and `OCR_BASE_URL=http://localhost:8081` in `backend/.env`.

Without it, a scanned PDF is reported as needing OCR. Nothing is guessed.

## Everyday commands

| Task | Command |
| --- | --- |
| Start the database (no Docker) | `./scripts/mysql-start.sh` |
| Stop it | `./scripts/mysql-stop.sh` |
| Open a database prompt | `./scripts/mysql-client.sh college_timetable` |
| Start the backend | `cd backend && mvn spring-boot:run` |
| Start the frontend | `cd frontend && npm run dev` |
| Backend tests | `cd backend && mvn test` |
| Frontend tests | `cd frontend && npm run test` |
| Production frontend build | `cd frontend && npm run build` |

## Troubleshooting

### Java

| Symptom | Cause and fix |
| --- | --- |
| `Unsupported class file major version` | Maven is on an old JDK. `mvn -version` shows which one it uses. `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`. |
| `release version 21 not supported` | JDK is older than 21. Install `openjdk@21`. |
| Builds fine, app fails to start | A different JDK is being picked up at runtime. Check `java -version` in the same shell. |

### Maven

| Symptom | Cause and fix |
| --- | --- |
| `mvn: command not found` | Not installed, or not on `PATH`. `brew install maven`. |
| `Could not transfer artifact ... Connection refused` | Network or proxy. Check `~/.m2/settings.xml` for a `<proxy>` block. |
| Build is very slow the first time | Maven is downloading the dependency tree. Later builds are fast. |
| `Port 8080 is already in use` | See ports below. |

### Node

| Symptom | Cause and fix |
| --- | --- |
| `npm: command not found` | `brew install node`, then open a new terminal. |
| `npm install` fails on Apple Silicon | Update Node to 20 or newer. |
| Vite fails to start | Delete `node_modules` and run `npm install` again. |

### MySQL

| Symptom | Cause and fix |
| --- | --- |
| `Communications link failure` | MySQL is not running. `docker compose ps` or `./scripts/mysql-start.sh`. |
| `Access denied for user` | Username or password mismatch. Check `DB_USERNAME` and `DB_PASSWORD`. |
| `Unknown database 'college_timetable'` | The schema was not created. See step 2. |
| `Public Key Retrieval is not allowed` | Add `allowPublicKeyRetrieval=true` to `DB_URL`. |
| Backend starts but finds no tables | Flyway did not run. Check the startup log for `Successfully applied ...`. |
| Server start-up is slow, then succeeds | Normal: the first run initialises the data directory. |

### Ports

| Port | Used by | To free it |
| --- | --- | --- |
| 3306 | MySQL (Docker) | `docker compose stop mysql` |
| 3307 | MySQL (script install) | `./scripts/mysql-stop.sh` |
| 8080 | Backend | `lsof -ti:8080 \| xargs kill` or set `SERVER_PORT` |
| 8081 | OCR service | `lsof -ti:8081 \| xargs kill` |
| 5173 | Frontend dev server | Set `VITE_PORT` in `frontend/.env.local` |

### CORS

A CORS error means the browser and the API are on different origins.

**In development you should not get one.** If you do, `VITE_API_BASE_URL` was probably changed to a
full URL. Leave it as `/api` and let the dev server proxy.

For a genuinely split deployment, add the frontend origin to the backend:

```
CORS_ALLOWED_ORIGINS=https://timetable.college.example
```

Note that `allowCredentials` is on, because authentication uses a cookie, so wildcard origins are
deliberately not allowed.

### PDF extraction

| Symptom | Cause and fix |
| --- | --- |
| `NOT_A_PDF` on a real PDF | The file is an image, or the magic bytes were altered in transit. |
| No rows, warning about the time-slot header | The template is not the one the parser understands. See [PDF_IMPORT.md](PDF_IMPORT.md). |
| `pagesNeedingOcr` on every page | The PDF is a scan. Enable the OCR service or run OCR externally. |
| Upload fails immediately | Larger than 25 MB or 100 pages. Both limits are configurable. |

### OCR

| Symptom | Cause and fix |
| --- | --- |
| `OCR_NOT_CONFIGURED` | `OCR_ENABLED` is not `true`, or the backend was not restarted. |
| `503 PaddleOCR is not installed` | `pip install "paddleocr>=2.9,<3" "paddlepaddle>=2.6"`. |
| Poppler errors | `brew install poppler`. |
| OCR is very slow | Lower `OCR_DPI`, or split the PDF. |

## Ports summary

| Service | Default port | Configurable with |
| --- | --- | --- |
| MySQL | 3306 / 3307 | `DB_URL` |
| Backend | 8080 | `SERVER_PORT` |
| Frontend dev server | 5173 | `VITE_PORT` |
| OCR service | 8081 | `uvicorn --port` |