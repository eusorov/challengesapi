# Challenges API

Backend API for the **challenges app**: users create challenges, share them with other users, and record **check-ins** according to a **schedule** (daily or a few specific days per week).

This file is project memory for humans and AI agents working in this repository. Extend it when domain rules or stack details become concrete.

---

## Core concepts

| Term | Meaning |
|------|--------|
| **Challenge** | A goal or habit defined by a user, with a required **start date**, an **optional end date** (inclusive window when set; omit end for open-ended), metadata, and rules. Has an **owner** and participants. |
| **SubTask** | Optional steps under a challenge. A challenge can have zero or more subtasks; check-ins can target the **challenge** or a specific **subtask** (see **Check-in**). |
| **Participant** | A **user** in a **challenge**: either for the **whole challenge** (`subTask` unset) or **scoped to a subtask** (`subTask` set). Same user can have both kinds of rows. Distinct from **`Challenge` owner**. **Invite** models invitations; roles beyond membership can be modeled later. |
| **Invite** | One user (**inviter**) invites another (**invitee**) to a **challenge**, optionally scoped to a **subtask** (`subTask` unset = whole challenge). Status (pending / accepted / …) is stored on the invite; accepting may create a **Participant** in a later API/service layer. |
| **Check-in** | A calendar-dated record that a user completed progress for a **challenge** on a given day, or optionally for a specific **subtask** of that challenge on that day. |
| **Comment** | A **user**-authored note on a **challenge** (challenge-wide) or on a specific **subtask** (`subTask` set). Exposed as **`GET`/`POST /api/challenges/{challengeId}/comments`** (optional **`?subTaskId=`** filter) and **`GET`/`PUT`/`DELETE /api/comments/{id}`** with **`API-Version: 1`**. Editing and deleting by id are not restricted by auth in this phase. |
| **Cadence / schedule** | How often check-ins are expected: **every day**, **specific weekdays** (e.g. Mon / Wed / Fri), or **one specific calendar date**. A **challenge** can have a schedule, and **each subtask** can have its **own** schedule. Do **not** assume “only daily” unless the model encodes that. |

---

## Product rules for agents
- When adding APIs, persistence, or models, **reuse this vocabulary** in naming (`Challenge`, `CheckIn`, `Participant`, `Schedule`, `Invite`, `Comment`, or project-standard equivalents). Avoid overloading terms (e.g. do not use “check-in” for unrelated events).
- Prefer small, focused changes that preserve existing contracts and semantics.

---

## Repository and stack

This repository is intended to host the **challenges API** — a Java Spring Boot service that exposes a **JSON REST API** using **Spring Web MVC** (`spring-boot-starter-webmvc` in Spring Boot 4).

- **Not in scope here:** The **React** web UI (and any other separate SPA)—those are separate repos that call this API. Server-rendered pages (Thymeleaf, JSP) are also not the goal; use **`@RestController`** and JSON.

### Stack

| Item | Value |
|------|--------|
| Language | Java **25** |
| Framework | Spring Boot **4.0.5** |
| Build | **Gradle** 9.4.1 (wrapper — see `gradle/wrapper/gradle-wrapper.properties`) |
| HTTP | **Spring Web MVC** — JSON REST (`spring-boot-starter-webmvc`). **React** SPA is **out of scope** in this repo (separate client). |
| Persistence | **PostgreSQL**, **Spring Data JPA**, **Flyway** migrations (`src/main/resources/db/migration`) |
| Security | **Spring Security** (stateless), **JWT** (JJWT), auth behavior in **`com.authspring.api`** (merged with **`com.challenges.api`** in one app) |
| API docs | **springdoc-openapi** — OpenAPI 3 + Swagger UI (`/v3/api-docs`, `/swagger-ui.html`) |
| Email / resilience | **Spring Mail**; **Resilience4j** (Spring Boot 4 starter) |
| Local DB | **`docker-compose.yml`** — Postgres (optional); integration tests use DB **`challengestest`** (see `application-test.yml`); **`prepareTestDatabase`** runs **Flyway clean** before `./gradlew test` |
| Test | JUnit 5 (`spring-boot-starter-webmvc-test`, `spring-security-test`, JPA/JDBC test starters) |
| Run | `./gradlew bootRun` |
| Test command | `./gradlew test` — PostgreSQL **`challengestest`**; **`prepareTestDatabase`** runs Flyway clean first. |

### REST API (current phase)

- **Base path:** JSON resources live under **`/api/...`** (see controller `@RequestMapping` paths).
- **Versioning:** send header **`API-Version: 1`** on HTTP requests; controllers are mapped at **`version = "1"`**.
- **Layering:** **`@RestController`** classes in **`com.challenges.api.web`** delegate to **`@Service`** types in **`com.challenges.api.service`**; **repositories** are only used from services. Auth endpoints and JWT filters live under **`com.authspring.api`**.
- **Invites → participants:** When an invite is **`ACCEPTED`** (**`InviteStatus.ACCEPTED`**), the API creates a **`Participant`** row for the invitee (challenge-wide or subtask-scoped to match the invite).
- **`POST /api/challenges/{id}/join`:** Authenticated user self-join per [`docs/superpowers/specs/2026-04-21-challenge-join-design.md`](docs/superpowers/specs/2026-04-21-challenge-join-design.md) — public challenges allow open join; private challenges require a usable **`PENDING`** invite. Returns **`ParticipantResponse`**; **201** on first challenge-wide insert, **200** when already a challenge-wide participant.
- **`POST /api/challenges` (create):** The **owner** automatically gets a **challenge-wide** **`Participant`** row (same as join semantics for membership).
- **`GET /api/challenges/mine`:** **Bearer JWT** required (**401** if missing). Paged owned challenges (public and private) for the current user; same pagination defaults as **`GET /api/challenges`**.
- **`GET /api/invites`:** **Bearer JWT** required (**401** if missing). Query **`role=RECEIVED`** (default: invites where you are **invitee**) or **`role=SENT`** (invites you **sent**); optional **`challengeId`** narrows the list. Not a global invite dump.
- **`GET /api/challenges/{id}/participants`:** Visibility matches **`GET /api/challenges/{id}`** (public vs private, **`UserPrincipal`** when present). Unauthorized private access → **404**.
- **`GET /api/challenges/{id}/check-ins`**, **`GET /api/challenges/{id}/check-in-summaries`**, **`GET /api/check-ins/{id}`:** **Bearer JWT** required (**404** if missing). Viewer must be **owner** or any **participant** of the challenge, and the challenge must pass **`findByIdForViewer`** (invite-only visibility for **`GET /api/challenges/{id}`** does **not** allow reading check-ins). Otherwise **404**.
- **`PUT /api/challenges/{id}`**, **`DELETE /api/challenges/{id}`:** **Bearer JWT** required (**401** without). **`ownerUserId`** in the **`PUT`** body must match the JWT subject (**403** if not). Only the **challenge owner** may replace or delete (**403** for others). **`PUT`** does **not** transfer ownership; **`owner`** stays the current owner. Missing challenge on **`DELETE`** → **404**.
- **`POST /api/subtasks`**, **`PUT /api/subtasks/{id}`**, **`DELETE /api/subtasks/{id}`:** **Bearer JWT** required (**401** without). Only the **challenge owner** may create, replace, or delete subtasks (**403** for others). **`GET`** subtask routes stay unauthenticated as today.
- **`POST /api/check-ins`:** **Bearer JWT** required (**401** without). **`userId`** in the body must match the JWT subject (**403** if not). **Owner** or **participant** only: challenge-wide check-in requires challenge-wide **`Participant`** (or owner); with **`subTaskId`**, actor must be challenge-wide participant **or** subtask-scoped for that subtask. Otherwise **403**.
- **Authentication:** **JWT** — most **`/api/**`** routes require a **Bearer** token after login/register; **public** paths include **`POST /api/login`**, **`POST /api/register`**, **`POST /api/users`**, password reset / forgot-password, and **`GET /api/email/verify/**`**. See **`SecurityConfig`** for the exact allowlist.

**Scope:** Only the REST API in this repository—**React** UI and server-rendered templates are not built here.
