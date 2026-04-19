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

4. Run tests (expects DB **`challengestest`**; the **`prepareTestDatabase`** Gradle task runs **Flyway clean** on it before tests):

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

This inserts **10 users** (`seed01@demo.local` through `seed10@demo.local`) with password **`password`** (BCrypt hash **`User.TEST_PASSWORD_HASH`** in code), **10 challenges**, schedules, subtasks, participants, check-ins, and invites. A second start **does not duplicate** rows (idempotent guard on `seed01@demo.local`). The **`demo-seed`** **`ApplicationRunner`** is off under the **`test`** profile (`challenges.demo.seed.on-startup: false` in `application-test.yml`) so `./gradlew test` does not commit seed data into **`challengestest`**; **`bootRun`** with **`demo-seed`** still runs the loader by default.
