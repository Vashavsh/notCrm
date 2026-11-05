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

A session is checked against the database on every request: when the CRM closes the account or resets the password (bumps `session_version`) the next request answers `403` with `code: "session_revoked"`. Login failures are rate limited per address+login, per address and per login (`LoginRateLimiter`).

## Run locally

The CRM's local Postgres (`../backend/compose.yaml`, port 5434) can host the `learn` schema too. The app creates the schema itself; for local runs give it a user that may create schemas (the CRM user works):

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21 PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"
export DB_URL=jdbc:postgresql://localhost:5434/pipeline_crm DB_USERNAME=pipeline_user DB_PASSWORD=<local password>
./mvnw spring-boot:run
curl http://localhost:8081/api/health   # {"status":"ok"}
```

## Tests

`./mvnw test` starts PostgreSQL 18 with Testcontainers (Docker must be running). On a Mac where Docker Desktop's default socket is off, Testcontainers may not find Docker; export `DOCKER_HOST=unix://$HOME/.docker/run/docker.sock` and `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`, and with Docker Engine 29 also `JAVA_TOOL_OPTIONS=-Dapi.version=1.44`.
