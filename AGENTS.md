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
| **Cadence / schedule** | How often check-ins are expected: **every day**, **specific weekdays** (e.g. Mon / Wed / Fri), or **one specific calendar date**. A **challenge** can have a schedule, and **each subtask** can have its **own** schedule. Do **not** assume “only daily” unless the model encodes that. |

---

## Product rules for agents
- When adding APIs, persistence, or models, **reuse this vocabulary** in naming (`Challenge`, `CheckIn`, `Participant`, `Schedule`, `Invite`, or project-standard equivalents). Avoid overloading terms (e.g. do not use “check-in” for unrelated events).
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
| Test | JUnit 5 (`spring-boot-starter-webmvc-test`) |
| Run | `./gradlew bootRun` |
| Test command | `./gradlew test` |

### REST API (current phase)

- **Base path:** JSON resources live under **`/api/...`** (see controller `@RequestMapping` paths).
- **Versioning:** send header **`API-Version: 1`** on HTTP requests; controllers are mapped at **`version = "1"`**.
- **Layering:** **`@RestController`** classes in **`com.challenges.api.web`** delegate to **`@Service`** types in **`com.challenges.api.service`**; **repositories** are only used from services.
- **Invites → participants:** When an invite is **`ACCEPTED`** (**`InviteStatus.ACCEPTED`**), the API creates a **`Participant`** row for the invitee (challenge-wide or subtask-scoped to match the invite).
- **Authentication:** **none** in this phase—callers pass **user ids** and related ids in request bodies as each endpoint documents.

**Scope:** Only the REST API in this repository—**React** UI and server-rendered templates are not built here.
