# Challenges API

Backend API for the **challenges app**: users create challenges, share them with other users, and record **check-ins** according to a **schedule** (daily or a few specific days per week).

This file is project memory for humans and AI agents working in this repository. Extend it when domain rules or stack details become concrete.

---

## Core concepts

| Term | Meaning |
|------|--------|
| **Challenge** | A goal or habit defined by a user, with metadata and rules as implemented in code. Typically has a creator and one or more participants. |
| **SubTask** | A challenge can have a subtask but not mandatory defined by a user, with metadata and rules as implemented in code.|
| **Participant** | Other users who can access the challenge (visibility and permissions depend on implementation: invites, links, memberships, etc.). Do not assume a single sharing mechanism unless the codebase fixes one. |
| **Check-in** | A timestamped or calendar-dated record that the user completed (or reported) progress for a challenge on a given day or instant. |
| **Cadence / schedule** | How often check-ins are expected: **every day**, or **specific days per week** (e.g. Mon / Wed / Fri). Do **not** assume “only daily” or “only weekdays” unless the model or API explicitly encodes that. |

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
