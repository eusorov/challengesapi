# Challenges API

Backend service for a **challenges** product: **users create challenges, invite others to participate, and track progress** (schedules, check-ins, comments, and optional challenge images). This repository is **API-only**—there is no web UI here; clients consume **JSON over HTTP**.

## Goal

- **Challenges:** Owners define goals with dates, optional subtasks, and schedules (daily, specific weekdays, or a single fixed date).
- **Invitations:** Users invite others to a challenge (whole challenge or a specific subtask). When an invite is **accepted**, the API records **participants** accordingly.
- **Engagement:** Participants record **check-ins**, leave **comments**, and owners can attach an **optional cover image** per challenge (stored in **Amazon S3** when configured).

## Stack

| Layer | Technology |
|--------|------------|
| Language | Java **25** |
| Framework | **Spring Boot 4.0.5** |
| HTTP | **Spring Web MVC** — JSON REST (`spring-boot-starter-webmvc`) |
| Build | **Gradle** **9.4.1** (wrapper) |
| Persistence | **PostgreSQL**, **Spring Data JPA**, **Flyway** (`src/main/resources/db/migration`) |
| Security | **Spring Security** (stateless), **JWT** (**JJWT**); auth packages live under **`com.authspring.api`** alongside **`com.challenges.api`** in one deployable app |
| Object storage | **AWS SDK for Java 2.x** (**S3**) for challenge image uploads (bucket/region via config; see `application.yml`) |
| API docs | **springdoc-openapi** — `/v3/api-docs`, **Swagger UI** at `/swagger-ui.html` |
| Other | **Spring Mail**, **Resilience4j**, **Spring Actuator** (health) |

## API conventions

- Base path: **`/api/...`**
- Version header: **`API-Version: 1`** on requests (controllers use `version = "1"`).
- Most routes require a **Bearer** JWT; public exceptions include login, register, some user creation, email verification links, and password reset flows (see `SecurityConfig`).

## Local development

1. Start **PostgreSQL** (for example `docker compose up` from the repo root; default user/database match `application.yml`).
2. Set **JWT** and database env vars as needed (defaults exist for local use; see `src/main/resources/application.yml`).
3. Run the app:

```bash
./gradlew bootRun
```

4. Run tests — requires PostgreSQL with database **`challengestest`** (see `docker-compose.yml`). **`./gradlew test`** runs Flyway clean on that database first, then the suite.

```bash
./gradlew test
```
More detail for contributors and agents lives in **`AGENTS.md`** and **`CLAUDE.md`**.

## Demo data (optional)

Never enable this in **production**.

With Postgres running, start the app with profile **`demo-seed`**:

```bash
./gradlew bootRun --args='--spring.profiles.active=demo-seed'
```

This inserts **1000 users** (`bulk-demo-0000@demo.local` through `bulk-demo-0999@demo.local`) with fake names from **Datafaker**, password **`password`** (BCrypt **`User.TEST_PASSWORD_HASH`**), **1000 challenges** (same index: user 0 owns challenge 0, and so on), a **schedule** per challenge (either **daily** or **specific weekdays**), **1–5 subtasks** per challenge each with the same kind of schedule, the **owner** as a **challenge-wide participant** (like **`POST /api/challenges`**), plus **10 other members** per challenge (each with **4 check-ins** — half challenge-wide and half on a subtask, except every **100th** challenge where the last member is **subtask-scoped only** and check-ins match that). Every **100th** challenge also gets sample **invites** (pending, accepted, declined, cancelled, and a subtask-scoped pending) and **comments** (challenge-wide and on a subtask). A second start **does not duplicate** rows (idempotent guard on `bulk-demo-0000@demo.local`). The **`demo-seed`** **`ApplicationRunner`** is off under the **`test`** profile (`challenges.demo.seed.on-startup: false` in `application-test.yml`) so `./gradlew test` does not commit seed data into **`challengestest`**; **`bootRun`** with **`demo-seed`** still runs the loader by default.
