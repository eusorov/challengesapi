# Challenges API

Backend API for the **challenges app**: users create challenges, share them with other users, and record **check-ins** according to a **schedule** (daily or a few specific days per week).

This file is project memory for humans and AI agents working in this repository. Extend it when domain rules or stack details become concrete.

---

## Core concepts

| Term | Meaning |
|------|--------|
| **Challenge** | User-defined goal/habit with required **start date**, optional **end date** (open-ended if unset), **owner**, participants. |
| **SubTask** | Optional steps under a challenge; check-ins can target the challenge or a subtask. |
| **Participant** | **User** in a **challenge**, optionally tied to a **subtask** (whole-challenge vs subtask-scoped membership). |
| **Check-in** | Progress for a **challenge** on a given day, or for a **subtask** of that challenge on that day. |
| **Cadence / schedule** | Daily, weekly weekdays, or **a single fixed calendar date**. Per **challenge** and/or per **subtask** (separate schedules). |

---

## Product rules for agents
- When adding APIs, persistence, or models, **reuse this vocabulary** in naming (`Challenge`, `CheckIn`, `Participant`, `Schedule`, or project-standard equivalents). Avoid overloading terms (e.g. do not use “check-in” for unrelated events).
- Prefer small, focused changes that preserve existing contracts and semantics.

---

## Repository and stack

This repository hosts the **challenges API**: **JSON REST** via **Spring Web MVC** (`@RestController`). A **React** UI is **out of scope** here (separate client later).

### Stack

| Item | Value |
|------|--------|
| Language | Java **25** |
| Framework | Spring Boot **4.0.5** |
| Build | **Gradle** 9.4.1 (wrapper) |
| HTTP | **Spring Web MVC** (`spring-boot-starter-webmvc`), JSON REST. **React** client out of scope here. |
| Run / test | `./gradlew bootRun` / `./gradlew test` |
