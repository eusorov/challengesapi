# Null-safety, Optional, constructor injection & input validation — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Standardize **absent values** as **`Optional`** (not `null`) for single-result service APIs, **`List`** that is **never `null`** (empty list ok), add **`org.springframework.lang`** **`@NonNullApi` / `@NonNull` / `@Nullable`**, ensure **constructor injection everywhere** (including tests), and **fail fast** with **`org.springframework.util.Assert.notNull`** on entry (note: Spring’s API is **`Assert.notNull`**, not `Assert.NonNull`).

**Architecture:** Apply **`@NonNullApi`** at **package** level (`package-info.java`) for **`com.challenges.api.service`**, **`com.challenges.api.web`**, and **`com.challenges.api.web.dto`**, and **`com.challenges.api.repo`**. Override with **`@Nullable`** on parameters that intentionally allow absent filters (e.g. **`challengeId`/`subTaskId`** query filters). Annotate **DTO record** components that are optional in JSON with **`@Nullable`**. Keep **entity** classes largely unchanged in the first pass (optional follow-up: **`model` package** annotations). **Repositories** remain Spring Data interfaces; default **`@NonNullApi`** applies to declared custom methods and return types.

**Tech Stack:** Java **25**, Spring Boot **4.0.5**, `spring-core` **`org.springframework.lang`**, **`Optional`**, JUnit **5**, Gradle **`./gradlew test`**.

---

## Conventions (normative)

| Situation | Pattern |
|-----------|---------|
| Load by id might miss | **`Optional<T>`** return |
| Create/replace might miss FK | **`Optional<T>`** return |
| List / stream of rows | **`List<T>`** — **never `null`**, may be **empty** |
| Boolean success | **`boolean`** (e.g. delete) |
| Optional filter argument | **`@Nullable Long`** (or `Optional` wrapper — **prefer `@Nullable`** for existing APIs) |
| Validation | **`Assert.notNull(name, "name must not be null");`** at the **start** of public service methods |

**Do not** change REST JSON contracts (same status codes and bodies).

---

## File map

| Path | Role |
|------|------|
| [`src/main/java/com/challenges/api/service/package-info.java`](../../src/main/java/com/challenges/api/service/package-info.java) | **`@NonNullApi`** for services |
| [`src/main/java/com/challenges/api/web/package-info.java`](../../src/main/java/com/challenges/api/web/package-info.java) | **`@NonNullApi`** for controllers + advice |
| [`src/main/java/com/challenges/api/web/dto/package-info.java`](../../src/main/java/com/challenges/api/web/dto/package-info.java) | **`@NonNullApi`**; optional fields **`@Nullable`** on records |
| [`src/main/java/com/challenges/api/repo/package-info.java`](../../src/main/java/com/challenges/api/repo/package-info.java) | **`@NonNullApi`** for repositories |
| All `*Service.java` | **`@Nullable` where needed**, **`Assert.notNull`** gaps, explicit **`@NonNull`** on returns where useful |
| All `*Controller.java` + [`GlobalExceptionHandler.java`](../../src/main/java/com/challenges/api/web/GlobalExceptionHandler.java) | Same defaults under **`web`** package |
| DTO records with optional IDs | **`@Nullable Long`** on `subTaskId`, `expiresAt`, etc. |
| All `*IT.java` / `*Test.java` under `src/test/java` | **Constructor injection** instead of field **`@Autowired`** |

---

## API audit (current state — no behavioral change expected)

- **`UserService.create`** returns **`User`** (always persisted) — **keep** non-null **`User`**; annotate **`@NonNull`** return.
- **`InviteService.list(Long challengeIdFilter)`** — filter **`@Nullable`**; return **`@NonNull List<Invite>`**.
- **`CommentService.listForChallenge(Long challengeId, Long subTaskIdFilter)`** — second arg **`@Nullable`**.
- **`ScheduleService.parseWeekDays(List<String> raw)`** — parameter **`@Nullable`**; return **`@NonNull List<DayOfWeek>`** (already **`List.of()`** when null/empty).
- **`ScheduleService.update(..., List<DayOfWeek> weekDays)`** — today calls **`s.replaceWeekDays(weekDays)`**; add **`Assert.notNull(weekDays, "weekDays must not be null");`** (controller always sends a list; defense in depth).
- **`ScheduleService.createForChallenge` / `createForSubTask`** — add **`Assert.notNull(weekDays, "weekDays must not be null");`** OR document that **`null`** means empty — **YAGNI: use `Assert.notNull`** for API clarity.

**Production controllers** already use **constructors** only — verify with:

```bash
grep -R "@Autowired" src/main/java/com/challenges/api/web --include="*.java"
```

Expected: **no matches** on fields (only if any constructor uses `@Autowired` — optional to remove redundant **`@Autowired`** on single constructor).

---

### Task 1: Package defaults (`package-info.java`)

**Files:**
- Create: [`src/main/java/com/challenges/api/service/package-info.java`](../../src/main/java/com/challenges/api/service/package-info.java)
- Create: [`src/main/java/com/challenges/api/web/package-info.java`](../../src/main/java/com/challenges/api/web/package-info.java)
- Create: [`src/main/java/com/challenges/api/web/dto/package-info.java`](../../src/main/java/com/challenges/api/web/dto/package-info.java)
- Create: [`src/main/java/com/challenges/api/repo/package-info.java`](../../src/main/java/com/challenges/api/repo/package-info.java)

- [ ] **Step 1:** Add four **`package-info.java`** files with identical structure; only **`package`** line differs.

[`src/main/java/com/challenges/api/service/package-info.java`](../../src/main/java/com/challenges/api/service/package-info.java):

```java
@NonNullApi
package com.challenges.api.service;

import org.springframework.lang.NonNullApi;
```

Repeat for:

- `package com.challenges.api.web;`
- `package com.challenges.api.web.dto;`
- `package com.challenges.api.repo;`

- [ ] **Step 2:** `./gradlew compileJava --no-daemon` → **SUCCESS**

- [ ] **Step 3:** Commit **`chore: add NonNullApi package defaults`**

---

### Task 2: Service layer — `@Nullable` filters + `Assert.notNull` gaps

**Files:** (modify each)

- [`src/main/java/com/challenges/api/service/InviteService.java`](../../src/main/java/com/challenges/api/service/InviteService.java)
- [`src/main/java/com/challenges/api/service/CommentService.java`](../../src/main/java/com/challenges/api/service/CommentService.java)
- [`src/main/java/com/challenges/api/service/ScheduleService.java`](../../src/main/java/com/challenges/api/service/ScheduleService.java)
- [`src/main/java/com/challenges/api/service/ChallengeService.java`](../../src/main/java/com/challenges/api/service/ChallengeService.java) — ensure **`create`/`replace`** assert **`req`** not null if not already
- [`src/main/java/com/challenges/api/service/SubTaskService.java`](../../src/main/java/com/challenges/api/service/SubTaskService.java)
- [`src/main/java/com/challenges/api/service/UserService.java`](../../src/main/java/com/challenges/api/service/UserService.java)

- [ ] **Step 1:** Add import:

```java
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
```

(Use **`NonNull`** only where it clarifies return types, e.g. **`@NonNull List<Invite>`**.)

- [ ] **Step 2:** **`InviteService`**: change signature to:

```java
@Transactional(readOnly = true)
public @NonNull List<Invite> list(@Nullable Long challengeIdFilter) {
```

- [ ] **Step 3:** **`CommentService`**: change **`listForChallenge`** to:

```java
@Transactional(readOnly = true)
public @NonNull List<Comment> listForChallenge(Long challengeId, @Nullable Long subTaskIdFilter) {
```

(body unchanged; **`Assert.notNull(challengeId, ...)`** already present)

- [ ] **Step 4:** **`ScheduleService`**: annotate **`parseWeekDays`**:

```java
public static @NonNull List<DayOfWeek> parseWeekDays(@Nullable List<String> raw) {
```

Add at start of **`createForChallenge`**, **`createForSubTask`**, and **`update`**:

```java
Assert.notNull(weekDays, "weekDays must not be null");
```

(Place **`Assert.notNull(weekDays, ...)`** immediately after **`kind`** / **`id`** asserts in each method.)

- [ ] **Step 5:** **`ChallengeService.replace`** — if **`req`** is not asserted, add **`Assert.notNull(req, "request must not be null");`** at method entry. **`SubTaskService.create`/`replace`** — same for **`req`** where missing.

- [ ] **Step 6:** **`./gradlew compileJava --no-daemon`** → **SUCCESS**

- [ ] **Step 7:** Commit **`refactor(services): Nullable filters and assert weekDays`**

---

### Task 3: DTO records — `@Nullable` optional fields

**Files:** (edit each record; add import `org.springframework.lang.Nullable`)

- [`InviteRequest.java`](../../src/main/java/com/challenges/api/web/dto/InviteRequest.java) — **`subTaskId`**, **`status`**, **`expiresAt`**
- [`CommentRequest.java`](../../src/main/java/com/challenges/api/web/dto/CommentRequest.java) — **`subTaskId`**
- [`ScheduleCreateRequest.java`](../../src/main/java/com/challenges/api/web/dto/ScheduleCreateRequest.java) — **`challengeId`** vs **`subTaskId`** (one of; both may appear in validation — mark nullable components that are optional in JSON)
- Any other DTO where **`Long`** field is optional in API

Example for **`InviteRequest`**:

```java
import org.springframework.lang.Nullable;

public record InviteRequest(
		@NotNull Long inviterUserId,
		@NotNull Long inviteeUserId,
		@NotNull Long challengeId,
		@Nullable Long subTaskId,
		InviteStatus status,
		@Nullable Instant expiresAt) {}
```

- [ ] **Step 1:** Apply **`@Nullable`** to optional components (do **not** mark **`@NotNull`** on primitives/strings already covered by **`@NotBlank`/`@NotNull`** from Jakarta unless you add **both** — prefer minimal diff: only **`@Nullable`** on optional reference types).

- [ ] **Step 2:** `./gradlew compileJava --no-daemon` → **SUCCESS**

- [ ] **Step 3:** Commit **`refactor(dto): mark optional fields Nullable`**

---

### Task 4: Controllers & `GlobalExceptionHandler` — explicit nullness

**Files:**

- [`src/main/java/com/challenges/api/web/CommentController.java`](../../src/main/java/com/challenges/api/web/CommentController.java)
- [`CheckInController.java`](../../src/main/java/com/challenges/api/web/CheckInController.java)
- Remaining controllers as needed

- [ ] **Step 1:** On **`@RequestParam(required = false) Long subTaskId`** in **`CommentController.list`**, add **`@Nullable Long subTaskId`**.

- [ ] **Step 2:** Add **`@NonNull`** on **`List<...>`** return types in controllers where you want IDE clarity (optional; package default already implies non-null references).

- [ ] **Step 3:** `./gradlew compileJava` → **SUCCESS**

- [ ] **Step 4:** Commit **`refactor(web): Nullable request params where optional`**

---

### Task 5: Tests — constructor injection only

**Scope:** Every class under **`src/test/java`** that uses **field** **`@Autowired`**.

Known files (from grep):  
`CommentControllerIT`, `ChallengeDomainWorkflowIT`, `InviteControllerIT`, `ParticipantControllerIT`, `CheckInControllerIT`, `ScheduleControllerIT`, `SubTaskControllerIT`, `ChallengeControllerIT`, `UserControllerIT`, `InviteRepositoryTest`, `CheckInRepositoryTest`, `ScheduleRepositoryTest`, `ParticipantRepositoryTest`, `SubTaskRepositoryTest`, `ChallengeRepositoryTest`, `UserRepositoryTest`, `DomainBulkFixtureIT`.

- [ ] **Step 1:** For each test class, replace:

```java
@Autowired
private MockMvc mockMvc;
```

with **single constructor** + **`final`** fields (**JUnit 5** + Spring resolve constructor parameters automatically on **`@SpringBootTest`** classes):

```java
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserControllerIT {

	private static final String HV = "API-Version";
	private static final String V1 = "1";

	private final MockMvc mockMvc;

	UserControllerIT(MockMvc mockMvc) {
		this.mockMvc = mockMvc;
	}
	// ...
}
```

- [ ] **Step 2:** Remove unused **`import org.springframework.beans.factory.annotation.Autowired;`** from each updated file.

- [ ] **Step 3:** For tests with **multiple** dependencies, use one constructor listing all (e.g. **`MockMvc`**, **`ObjectMapper`**, **`UserRepository`**, …).

- [ ] **Step 4:** Run:

```bash
./gradlew test --no-daemon
```

Expected: **BUILD SUCCESSFUL**

- [ ] **Step 5:** Commit **`refactor(test): constructor injection in IT and repository tests`**

---

### Task 6: Repositories (optional explicit `@NonNull` on custom methods)

**Files:** [`CommentRepository.java`](../../src/main/java/com/challenges/api/repo/CommentRepository.java), other repos with **`@Query`**

- [ ] **Step 1:** No need to redeclare inherited **`findById`** from **`JpaRepository`**. Optionally add **`@NonNull`** on **`List<...>`** return types of custom query methods — only if the compiler/IDE stays clean.

- [ ] **Step 2:** `./gradlew compileJava` → **SUCCESS**

- [ ] **Step 3:** Commit only if changes exist: **`chore(repo): clarify NonNull list returns`**

---

### Task 7: Full verification

- [ ] **Step 1:**

```bash
./gradlew test --no-daemon
```

Expected: **BUILD SUCCESSFUL**

- [ ] **Step 2:** Commit only if formatting or remaining fixes.

---

## Self-review

| Requirement | Task |
|-------------|------|
| **`Optional`** for missing single results | Already standard in services; plan adds **annotations** + **`Schedule`** **`weekDays`** validation — **no** new `null` returns |
| **`@NonNullApi` / `@Nullable` / `@NonNull`** | **1–4** |
| **Constructor injection** | **Production OK**; **5** for **tests** |
| **Early validation (`Assert.notNull`)** | **2** (**weekDays**, **req** gaps) |

**Placeholder scan:** None.

**Type consistency:** **`list(@Nullable Long challengeIdFilter)`** callers pass **`Long`** or **`null`** from controller **`@RequestParam(required = false)`** — unchanged behavior.

---

## Execution handoff

**Plan path:** [`docs/superpowers/plans/2026-04-18-nullability-optional-constructors.md`](2026-04-18-nullability-optional-constructors.md)

**Plan complete and saved to `docs/superpowers/plans/2026-04-18-nullability-optional-constructors.md`. Two execution options:**

**1. Subagent-Driven (recommended)** — Fresh subagent per task; use **superpowers:subagent-driven-development**.

**2. Inline Execution** — Batched in this session with **superpowers:executing-plans**.

**Which approach?**
