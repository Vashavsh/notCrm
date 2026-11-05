# learn-backend

Backend of the student course site (learn.shabash-hub.ru). A separate Spring Boot app from the CRM in `../backend`: own jar, own port (8081), own deploy. See the "Student course site" section of the root `CLAUDE.md` for the architecture and the rules that follow from it.

- The app only uses the Postgres schema `learn`. Flyway keeps its own history there, and the connection's `search_path` is `learn`, so it can never read the CRM's `public` tables.
- `learn.account` is the bridge: the CRM writes it (issue access, reset password, close access), this app reads it.
- Course content (lessons, images, Amplitude credentials) is not in git. It will be read from a directory configured by `LEARN_CONTENT_DIR`.

## API (so far)

Same shape as the old course site, so its front end can be reused. Errors are `{"error": text, "code"?: string}`.

| Call | What it does |
|---|---|
| `GET /api/session` | `{user \| null, csrfToken}`; the front end sends the token back in the `X-CSRFToken` header on every POST |
| `POST /api/login` `{login, password}` | starts a session (cookie `LEARN_SESSION`, HttpOnly, 90 days, renewed on every request) |
| `POST /api/logout` | ends it |
| `POST /api/change-password` `{currentPassword, newPassword}` | the student's own change; their other sessions end |
| `GET /api/course` | modules, lessons, the student's status in each, resume point |
| `GET /api/lessons/{id}` | opens a lesson (no answers in it, the placeholder name replaced); `POST .../answers`, `/drafts`, `/complete`, `/restart` need the current `attemptId` or answer `409 attempt_conflict` |
| `POST /api/lessons/{id}/attachment` (multipart `file`, `questionId`, `attemptId`) | a picture for an open answer, re-encoded to PNG (PNG/JPEG/WebP, 6 MB, 20 megapixels) ; shown at `GET /api/attachments/{id}` to its owner only |
| `GET /api/resources`, `GET /api/resources/{id}` | the dataset catalog; the shared Amplitude login of one dataset |
| `GET /assets/course/**` | the pictures lessons use, for logged-in students, only files a lesson refers to |

A session is checked against the database on every request: when the CRM closes the account or resets the password (bumps `session_version`) the next request answers `403` with `code: "session_revoked"`. Login failures are rate limited per address+login, per address and per login (`LoginRateLimiter`).

## Course content

Not in git. `LEARN_CONTENT_DIR` points at a directory with `lessons/lesson-NN.json`, `stages.json`, `resources.json` and `images/`; `LEARN_SECRETS_DIR` at a directory with one `<resource id>.json` per external resource (the shared Amplitude logins). The app checks the content at startup (unique lesson and question ids, correct answers inside the options, known resources, every block listed in `stages.json`) and **refuses to start** if anything is wrong, so a bad content deploy fails the health check and is rolled back. Question and lesson ids must never change: progress is stored against them.

The shared Amplitude logins are written by `python3 -I scripts/import-amplitude.py <"Доступ к Amplitude.docx"> <content dir> <secrets dir>` (it matches each row of the document to a resource in `resources.json` by its data period, writes `<resource id>.json` with mode 600 and never prints a login or password). Run it again to replace the logins once the datasets move to the school's own Amplitude account.

On the developer's machine the real content lives next to the repo in `../notCrm-local-data/learn-content`. Tests use a small fictional course in `src/test/resources/content-fixture`; `RealCourseContentTest` checks the real one when `LEARN_REAL_CONTENT_DIR` is set.

## Run locally

The CRM's local Postgres (`../backend/compose.yaml`, port 5434) can host the `learn` schema too. The app creates the schema itself; for local runs give it a user that may create schemas (the CRM user works):

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21 PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"
export DB_URL=jdbc:postgresql://localhost:5434/pipeline_crm DB_USERNAME=pipeline_user DB_PASSWORD=<local password>
export LEARN_CONTENT_DIR="$PWD/../../notCrm-local-data/learn-content"   # adjust for your checkout/worktree
./mvnw spring-boot:run
curl http://localhost:8081/api/health   # {"status":"ok"}
```

## Tests

`./mvnw test` starts PostgreSQL 18 with Testcontainers (Docker must be running). On a Mac where Docker Desktop's default socket is off, Testcontainers may not find Docker; export `DOCKER_HOST=unix://$HOME/.docker/run/docker.sock` and `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`, and with Docker Engine 29 also `JAVA_TOOL_OPTIONS=-Dapi.version=1.44`.
