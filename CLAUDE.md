# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository layout

- `backend/` — Spring Boot REST API (Java 21). The real, production-bound implementation.
- `frontend/` — React 19 + TypeScript + Vite SPA. The real, production-bound implementation.
- `pipeline-crm-prototype.html` — a standalone, no-backend HTML/CSS/vanilla-JS mockup (all data in memory, resets on reload). It is the original UX/business-logic spec used to design `backend/` and `frontend/`, not code that is deployed or extended. Treat it as read-only design reference.
- `DEVELOPMENT_BACKLOG.md` — authoritative backend spec: roles, per-endpoint rules, sprint breakdown. What `backend/` was built against.
- `FRONTEND_PLAN.md` — frontend technical plan: stack constraints, folder layout, DTO/page contracts. What `frontend/` was built against.
- `DEEPSEEK_BACKEND_IMPLEMENTATION_PROMPT.md` — the original backend implementation prompt.

## Commands

### Backend (run from `backend/`)

```bash
# 1. Postgres (only needed once per machine reboot / volume reset)
docker compose --env-file .env.example up -d
docker compose --env-file .env.example ps    # wait for "healthy"

# 2. Run the app (env vars come straight from .env.example, no .env file needed)
export JAVA_HOME=/opt/homebrew/opt/openjdk@21   # only if `java -version` doesn't already resolve to 21
export PATH="$JAVA_HOME/bin:$PATH"
set -a; source .env.example; set +a
./mvnw spring-boot:run

# Health check
curl http://localhost:8080/api/v1/health   # {"status":"ok"}

# Tests (needs Docker running — Testcontainers spins up Postgres 16)
./mvnw test
./mvnw test -Dtest=BusinessRulesIntegrationTest                 # single class
./mvnw test -Dtest=BusinessRulesIntegrationTest#someMethodName  # single test
```

### Frontend (run from `frontend/`)

```bash
npm run dev      # Vite dev server, http://localhost:5173
npm run build    # tsc -b && vite build
npm run lint      # oxlint
```
No test suite is configured on the frontend yet.

### Local dev gotchas

- This machine has no system `java`; JDK 21 lives at `/opt/homebrew/opt/openjdk@21` (Homebrew). Export `JAVA_HOME`/`PATH` as above before any `./mvnw` command.
- Ports: backend `8080`, frontend `5173` (falls back to `5174` if busy — if so, restart backend with `FRONTEND_ORIGIN=http://localhost:5174 ./mvnw spring-boot:run` so CORS allows it), Postgres `5434` (not the Postgres default 5432, to avoid clashing with other local projects).
- Never run `docker compose down -v` — it deletes the Postgres volume (local data). Changing `POSTGRES_PASSWORD` in the env file does not change the already-provisioned role password inside an existing volume.
- `./mvnw test` against Docker Desktop 29+: Testcontainers' bundled docker-java speaks an API version the daemon rejects (HTTP 400, "Could not find a valid Docker environment"). Run with `DOCKER_HOST=unix://$HOME/.docker/run/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock ./mvnw test -Dapi.version=1.44`.
- Local student photos: real Telegram avatars live in `../notCrm-local-data/student-avatars/` next to the repo (never inside it — the repo is public). Under the `dev` profile `DevAvatarSeeder` hands them to demo students once (`DEV_AVATARS_DIR` overrides the path; from a worktree pass an absolute path). Set `AVATARS_ENABLED=false` locally if you don't want the app to fetch real photos from Telegram.
- Seed logins: `admin`/`admin123` (ADMIN), `anya.t`/`curator1` and `igor.l`/`curator2` (CURATOR, active), `sveta.r`/`curator3` (CURATOR, blocked).

### Git workflow — read this if you're about to commit

The user routinely runs **multiple Claude Code sessions in parallel on this exact same checkout** (`/Users/vasa/Documents/Codex Folder/notCrm`) — they all share one physical `.git`, one working tree, one current branch. Another session's checkout, commit, or uncommitted changes can appear underneath you mid-task. This has already caused a real collision once (two sessions both landed commits on a branch literally named `feature/kanban-drag-reorder`).

- **Never push or commit directly to `main`.** It's blocked by Claude Code's own auto-mode classifier (a safety guardrail, not a bug to work around) and it's the wrong flow regardless — always go through a branch + PR.
- **Pick a branch name specific to your actual change**, not a generic one another session could plausibly also pick (avoid bare `feature/...`, `fix/...`; prefix with what you're actually doing, e.g. `feature/curator-permissions`).
- **Before touching git, check `git status`.** If there are uncommitted changes that aren't obviously yours, or you're not sure what branch you're on / whether it's up to date — don't `git checkout` in the shared working tree (you can carry someone else's in-progress edits across, or strand them). Instead create an **isolated worktree** straight from `origin/main`:
  ```bash
  git fetch origin
  git worktree add /tmp/<something-unique> -b <your-branch-name> origin/main
  cd /tmp/<something-unique>
  # make your changes here, commit, push — this never touches the shared checkout
  ```
- Stage specific files (`git add <paths>`), never `git add -A`/`-A .` — the shared working tree will usually have other sessions' unrelated changes sitting in it.
- Flow once committed: `git push -u origin <branch>` → `gh pr create --base main --head <branch> ...` → `gh pr merge <number> --merge` (only merge with the user's explicit go-ahead — merging triggers a real production deploy, see below).
- No PR-time CI exists on this repo (`.github/workflows/deploy.yml` only triggers on `push: branches: [main]`) — `./mvnw test` only ever runs for real as the first step of that same deploy job. This is fail-safe by step order: if tests fail, every later step (build, SSH, activate-on-server) is skipped, so a red run never reaches the production server, it just doesn't deploy. Use `gh run list --branch main` / `gh run view <id>` to watch it after merging.
- **To discard a file's local changes so it matches `origin/main`, don't use bare `git restore <path>`** — in the shared working tree the checked-out branch is often a stale, already-merged feature branch, and `git restore` resets to *that* branch's HEAD, not to `origin/main` (this silently reverted a just-merged `CLAUDE.md` section once). Use `git show origin/main:<path> > <path>` or `git checkout origin/main -- <path>` instead.
- **Before cleaning up a stray uncommitted file you don't recognize in the shared tree**, confirm it's actually a safe-to-discard duplicate rather than someone else's in-progress work: byte-compare it against the branch of whatever PR it looks like it belongs to — `diff <(git show <branch>:<path>) <path>`. (Don't use `git diff <branch> -- <path>` for this on an *untracked* file — it misleadingly reports the file as "deleted" instead of comparing content.) Identical → safe to discard. Different, or you can't find a matching branch → leave it alone.
- **After your branch is merged, if the shared working tree has nothing uncommitted you don't recognize, return it to a synced `main`** (`git checkout main && git merge --ff-only origin/main`) rather than leaving it parked on your now-merged feature branch — that's exactly what caused two different sessions to land commits on a branch both happened to call `feature/kanban-drag-reorder`. Git won't let a checkout destroy someone else's uncommitted tracked changes (it refuses instead) and never touches untracked files, so this is safe as long as you checked `git status` first.
- **If `gh pr merge` gets blocked by Claude Code's auto-mode classifier**, that's a deliberate per-session safety judgment, not a bug — don't retry the identical command hoping it slips through (it may get *more* resistant on repeat attempts, not less). Say so plainly to the user and either let them merge it themselves (terminal or the PR's GitHub page), or — only on their fresh, explicit, specific go-ahead — ask a different session to do that exact merge. The latter is not a workaround as long as the authorization is real and comes from the user in the moment, not inferred from an earlier broad instruction or relayed secondhand from the blocked session.

## Architecture

### Backend — modular monolith, packages under `com.pipeline.crm.*`

`auth`, `security`, `user`, `lead`, `student`, `avatar` (students' Telegram photos), `pipeline` (stages), `cohort`, `statistics`, `audit`, `common`, `config`.

- **Auth**: `POST /api/v1/auth/login` returns a JWT access token (15 min) and sets an HttpOnly refresh cookie (7 days, `POST /auth/refresh` to rotate). `CurrentUser` (id, username, role) is the request-scoped identity used everywhere.
- **RBAC**: ADMIN has full access; CURATOR is scoped to their own leads/students. Fetching another curator's record by ID returns `404`, not `403` (don't leak existence). Server-side checks are the only source of truth — the frontend hiding a button is not enforcement.
- **Schema/migrations**: Flyway-owned; Hibernate runs with `ddl-auto: validate`, so it never creates/alters tables itself. Every schema or data change is a new `V{n}__description.sql` under `backend/src/main/resources/db/migration/` — never edit an already-applied migration. `V1` is the schema, `V2` seeds users/pipeline-stages/cohorts, `V4` adds `students.stage_position` (manual Kanban card order); all three apply unconditionally everywhere (local, tests, prod). `V3` (realistic 6-months-of-cohorts lead/student demo data) lives in a separate `backend/src/main/resources/db/dev-seed/` location that's only added to `spring.flyway.locations` under the `dev` Spring profile (`application-dev.yml`), activated locally via `SPRING_PROFILES_ACTIVE=dev` in `.env.example` — **never** activate `dev` in production, `V3` deletes all existing leads/students before inserting its demo set. (Its header also documents the id-prefix convention needed to keep hand-authored UUIDs valid: only hex digits are legal in a UUID literal — letters like `g`/`h` are not.)
- **Kanban card order**: `Student.stagePosition` is a plain integer, scoped per `currentStageId`, used only as a tie-breaker *within* a zone — it does not encode the red/normal/paused grouping itself (that's always computed live, see `cardZone.ts` / `HealthCalculator`). `POST /students/{id}/reorder` (`{stageId, beforeStudentId}`) drives the drag gesture: on every call it re-fetches all siblings in the target stage ordered by `stagePosition`, splices the moved student in at the `beforeStudentId` anchor (or appends if `null`), and renumbers that stage's siblings 0..n-1 in one transaction — simple and correct at this app's scale (≤ a few dozen students per stage), not something to over-engineer with fractional positions. Positions are intentionally allowed to go non-dense across stages (e.g. a stage a student just left keeps its old gaps) — nothing relies on density, only relative order, and `StudentRepository.nextStagePosition` always uses `MAX+1` so it's gap-safe.
- **Cohorts follow the start date (any year)**: a student's cohort is always the cohort of the month of `startedAt` ("Декабрь 2025", "Январь 2027", …). `CohortService.resolveForMonth` finds the non-archived cohort whose `startDate` falls in that month (UTC) or creates a "Месяц Год" one starting on the 1st (restoring an archived cohort with that date/name instead of colliding with the unique `start_date`/`name`). It runs on student create, lead conversion **and whenever `startedAt` is edited** (`StudentService.followStartDateCohort`, audited as `change-cohort`). There is deliberately no manual cohort change anymore — `UpdateStudentRequest` has no `cohortId` and the UI shows the cohort read-only. The frontend only *previews* the cohort on create (`utils/cohorts.ts`).
- **Stage changes / "days on stage"**: all stage transitions (`moveStage`, `reorder`) go through `StudentService.changeStage`. Moving a student back to the stage they were on immediately before collapses the visit — the intermediate history row is deleted and the earlier one reopened, so `stageEnteredAt` (and thus "дней на этапе") is restored rather than reset. The audit log still records both moves.
- **Audit log**: `AuditService.log(...)` is the write side; `GET /api/v1/audit-log` (ADMIN only, filters `actorId`/`entityType`, paged) is the read side (`AuditQueryService` resolves names and builds the Russian description at read time; keep new `action` values handled there). Shown in Админка → Логи. Older rows stored bare-string payloads (e.g. just a stage id) — keep those readable.
- **Error handling / validation**: `SecurityConfig` permits the servlet ERROR dispatch (otherwise any unhandled 4xx/5xx is re-authenticated and surfaces as a misleading 401, which makes the frontend try a token refresh and can log the user out). `GlobalExceptionHandler` maps unreadable JSON / bad path ids to 400 and FK/constraint violations to 409 — don't add a catch-all `Exception` handler, it would swallow `AccessDeniedException` (403). `postpayPercent` is `@Min(0) @Max(100)` everywhere; `StudentService.requireNotInFuture` guards "on stage since". Cohort ids are checked via `CohortService.require`, curator ids via `user.CuratorGuard.requireAssignable` (must exist, be a non-deleted, non-blocked CURATOR — an admin id or a typo is a 400).
- **Staff directory**: `GET /api/v1/directory/users` is names-only and open to every signed-in user (`useCurators` uses it for everyone). Curators can't call `/users` (admin-only), so names of other people (comment authors, previous curators) and the "reassign" picker must come from the directory.
- **Deleting students**: `DELETE /api/v1/students/{id}` is a soft delete (`deletedAt`, audited as "delete"). Allowed for ADMIN and for curators with the per-user flag `canDeleteStudents` (V7, default false; toggle in Админка → Пользователи). Like `seeLeads`/`seeStats`/`canReassign` it rides in the JWT (refreshed on `/auth/refresh`), so a toggle takes effect at most one access-token lifetime later. A curator still only reaches their own students (404 otherwise).
- **Student photos** (`avatar` package, `V8`): the backend scrapes the public `t.me/<handle>` page (og:image, only from `app.avatars.allowed-image-hosts`), stores a 128px JPEG in `student_avatars`, and exposes it as `StudentDto.avatarVersion` + `GET /students/{id}/avatar?v=` (same RBAC as the student; the frontend loads it via axios as a blob because the token is header-only). Fetched after commit on create / lead conversion / handle change (`StudentTelegramChanged`), nightly at 01:00 UTC + 1 min after startup for rows past `next_check_at` (14d + up to 3d jitter, 1d after a failure), and by the refresh button (1/min per student). All Telegram requests go one at a time, 5 s apart. t.me intermittently serves the page without the photo (worse under bursts), so "no photo" only counts after 3 such answers in a row (`noPhotoAttempts`); never trust a single empty answer. Tests never hit Telegram: `application-test.yml` disables it and `AvatarIntegrationTest` uses a local stub. Unofficial source by design — failures degrade to initials, nothing else depends on it.
- **Core domain flow**: `Lead` → (`POST /leads/{id}/convert`) → `Student`. A `Student.cohortId` is an explicit FK chosen at conversion/creation time — cohort membership is **not** derived from `startedAt`, so a cohort's reporting group is independent of when a student actually started training.
- **PipelineStage**: fixed `position` (ordering) and `normDays` (expected duration on that stage; `null` = no norm, e.g. the final stage). `Student.currentStageId` + `stageEnteredAt` drive health/overdue calculations.
- **Health/overdue** (`student.HealthCalculator`): `days = now - stageEnteredAt`; green if `days < 0.7 * normDays`, yellow `0.7–1.0x`, red `> 1.0x`; paused students are always `"paused"`; a stage with `normDays == null` is always green/never overdue.
- **Cohort funnel stats** (`statistics.StatisticsService.cohorts()`, backs the "Воронка доходимости по когортам" chart): for each non-archived cohort with ≥1 visible student, computes cumulative reached-count per pipeline stage (`currentStage.position >= stage.position`) plus a "planned stage position now" derived by walking `cohort.startDate` through cumulative `normDays`. The per-student on-track/behind split is computed from each student's own `startedAt`, not the cohort's `startDate` — students who joined late in the month aren't unfairly counted as behind.
- **Tests**: `AbstractIntegrationTest` boots a real Postgres 16 Testcontainer and runs actual Flyway migrations against it — integration tests exercise real schema + business rules, not mocks. Production runs PostgreSQL 18, so the Testcontainer image and `backend/compose.yaml` should move to 18 (not done yet).

### Student course site ("learn") — decided 2026-11-05, not built yet

The student-facing course (Timur's GoPractice MVP, 38 lessons / 456 tasks) is deliberately **not** a module of this backend. It is a second, independently deployed Spring Boot app so that a course fix never restarts the CRM and vice versa, and so a compromise of the public-facing course cannot reach leads or payments. This is an explicit exception to the "modular monolith" principle; the bot and any future sales CRM still go into `backend/`. Do not merge `learn` into `backend/` without asking the owner.

- **Layout**: `backend/` (this CRM, unchanged), `learn-backend/` (new, own `pom.xml`, own jar, own systemd unit on its own port), `learn-frontend/` (Timur's React app adapted to the new API, served on learn.shabash-hub.ru, nginx proxies `/api` to the learn backend). Do not turn `backend/` into a multi-module build — the existing paths are referenced by CI and by other sessions' branches.
- **One Postgres, two schemas**: `public` = CRM (Flyway history as today), `learn` = course (own Flyway history). The learn DB role has **no grants on `public`**; the CRM role may write `learn.account` and read the progress views, nothing else in `learn`. Roles are cluster-level and are not part of `pg_dump -Fc`, so they are created by a documented setup script, not by Flyway.
- **Bridge (the only coupling)**: the CRM writes `learn.account` (login, BCrypt hash, status, `session_version`, optional stage) and reads `learn.v_student_progress`. The learn app never reads CRM tables. Change bridge objects additively only — either side can be rolled back independently.
- **No STUDENT role in `users`**: students never authenticate against the CRM, so `JwtService`/`SecurityConfig` stay as they are. The course has its own secrets and server-side sessions stored in Postgres (~90 days, sliding); resetting a password or closing access bumps `session_version`/status and takes effect on the next request.
- **Course content stays out of this repo** while it is public: lesson JSON, images and Amplitude credentials live in a directory configured by `LEARN_CONTENT_DIR` (a gitignored folder locally, a path outside the release dirs on the server). Tests in CI use a small fictional fixture set. If the repo is made private, content may move in. Never commit credentials or real student data.
- **CI/CD**: path-filtered workflows. `deploy.yml` gets `paths-ignore` for `learn-*`; the learn workflow runs only for `learn-*`. Merging to `main` still deploys to production; until the server is back, work accumulates on `feature/learn-platform` and each step lands there through its own PR.
- **Temporary decision**: the CRM keeps the student's initial password readable so a curator can show/copy it (card tab "Учебный сайт"). Remove once the bot delivers credentials, and never write a password into the bot's outbox.
- **Deferred on purpose**: "Отчислен" status (a separate "Закрыть доступ" PR comes first), stage-based material gating (the whole course is stage "GoPractice"), a lesson editor (lessons are edited as files), the "не заходил N дней" board badge.

### Frontend

- React 19 + TypeScript + Vite, Tailwind v4 (via the PostCSS plugin). No state-management library — local hooks per page (`useState`/`useEffect`, small custom hooks).
- `src/api/client.ts`: single axios instance. Access token is kept **in memory only** (never localStorage). On a 401 (excluding the login/refresh routes themselves), it triggers a queued refresh: concurrent in-flight requests wait on the single refresh call instead of each firing their own; `registerAuthHooks` (wired by `AuthProvider`) supplies the actual refresh + auth-lost callbacks.
- Structure mirrors the backend domains: `src/pages/{leads,students,stats,admin}`, one `src/api/*.ts` per resource, `src/types` mirrors backend DTOs 1:1.
