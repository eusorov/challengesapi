# Null-safety, Optional, constructor injection & input validation — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Standardize **absent values** as **`Optional`** (not `null`) for single-result service APIs, **`List`** that is **never `null`** (empty list ok), add **package-level** **`@NullMarked`** (JSpecify) plus **member-level** **`org.jspecify.annotations.NonNull`** / **`@Nullable`** on **returns, parameters, and record components** (not `org.springframework.lang.NonNull` / `@Nullable`). Use **`@NonNull`** explicitly on **function parameters** wherever the contract requires a value (ids, request DTOs, non-optional inputs), **except** on **`@PathVariable`** — those are **required by default**; do **not** stack JSpecify **`@NonNull`** or Jakarta **`@NotNull`** on them. Use **`@Nullable`** only for optional filters or absent JSON fields. Ensure **constructor injection everywhere** (including tests), and **fail fast** with **`org.springframework.util.Assert.notNull`** on entry (note: Spring’s API is **`Assert.notNull`**, not `Assert.NonNull`). Where JDK or Spring Data types are not annotated for nullness, **bridge** to the declared contract with **`java.util.Objects.requireNonNull(...)`** (see conventions).

**Architecture:** Apply **`@NullMarked`** at **package** level (`package-info.java`) for **`com.challenges.api.service`**, **`com.challenges.api.web`**, and **`com.challenges.api.web.dto`**, and **`com.challenges.api.repo`** — import **`org.jspecify.annotations.NullMarked`** only in those files. (Spring **`@NonNullApi`** is **deprecated** since Framework **7.0**; **`@NullMarked`** is the JSpecify replacement.) Use **`org.jspecify.annotations.NonNull`** and **`org.jspecify.annotations.Nullable`** on **methods, parameters, record components, and fields** where explicit nullness helps the IDE. Under **`@NullMarked`**, unannotated parameters are already treated as non-null by analysis; **explicit JSpecify `@NonNull` on parameters** helps for **service** methods and **controller** parameters that are not covered by another “required” signal. **Do not** add redundant **`@NonNull`** or Jakarta **`@NotNull`** on parameters that already have **`@PathVariable`**: **`@PathVariable`** defaults to **`required = true`**, so the binding is required and **`@NullMarked`** already treats the parameter as non-null — duplicating only adds noise. Override with **`@Nullable`** on parameters that intentionally allow absent filters (e.g. **`challengeId`/`subTaskId`** query filters). Annotate **DTO record** components that are optional in JSON with **`@Nullable`**. Keep **entity** classes largely unchanged in the first pass (optional follow-up: **`model` package** annotations). **Repositories** remain Spring Data interfaces; default **`@NullMarked`** applies to declared custom methods and return types.

**Tech Stack:** Java **25**, Spring Boot **4.0.5**, **`org.jspecify.annotations.NullMarked`** (package defaults), **`org.jspecify`** **`NonNull` / `Nullable`**, **`java.util.Objects.requireNonNull`**, **`Optional`**, JUnit **5**, Gradle **`./gradlew test`**.

---

## Conventions (normative)

| Situation | Pattern |
|-----------|---------|
| Load by id might miss | **`Optional<T>`** return |
| Create/replace might miss FK | **`Optional<T>`** return |
| List / stream of rows | **`List<T>`** — **never `null`**, may be **empty** |
| Boolean success | **`boolean`** (e.g. delete) |
| Optional filter argument | **`@Nullable Long`** (or `Optional` wrapper — **prefer `@Nullable`** for existing APIs) |
| Required id, request DTO, or other non-optional input | **`@NonNull`** on the **parameter** (e.g. **`@NonNull Long challengeId`**, **`@NonNull CommentRequest req`**) — pair with **`Assert.notNull`** in services for runtime checks |
| Required **`@PathVariable`** (fixed URI segment) | **`@PathVariable Long challengeId`** only — **no** extra JSpecify **`@NonNull`** or Jakarta **`@NotNull`** (path variables are **required by default**; **`@NullMarked`** is enough) |
| Validation | **`Assert.notNull(name, "name must not be null");`** at the **start** of public service methods |
| JDK / Spring Data vs JSpecify contract | **`Objects.requireNonNull(expr)`** on **`return`** when **`expr`** is a **`List`**, **`Optional`**, or entity from **`save` / `findById` / `map`** and the analyzer reports *unchecked conversion to `@NonNull …`* — runtime behavior unchanged if values are never null |

**Do not** change REST JSON contracts (same status codes and bodies).

---

## File map

| Path | Role |
|------|------|
| [`src/main/java/com/challenges/api/service/package-info.java`](../../src/main/java/com/challenges/api/service/package-info.java) | **`@NullMarked`** for services |
| [`src/main/java/com/challenges/api/web/package-info.java`](../../src/main/java/com/challenges/api/web/package-info.java) | **`@NullMarked`** for controllers + advice |
| [`src/main/java/com/challenges/api/web/dto/package-info.java`](../../src/main/java/com/challenges/api/web/dto/package-info.java) | **`@NullMarked`**; optional fields **`@Nullable`** on records |
| [`src/main/java/com/challenges/api/repo/package-info.java`](../../src/main/java/com/challenges/api/repo/package-info.java) | **`@NullMarked`** for repositories |
| All `*Service.java` | **`@Nullable` where needed** (JSpecify), **`@NonNull`** on **required parameters** (ids, DTOs), **`Assert.notNull`** gaps, explicit **`@NonNull`** on returns where useful, **`Objects.requireNonNull`** on returns from JDK/repositories where the IDE warns |
| All `*Controller.java` + [`GlobalExceptionHandler.java`](../../src/main/java/com/challenges/api/web/GlobalExceptionHandler.java) | Same defaults under **`web`** package; **`@Nullable`** on optional **`@RequestParam(required = false)`**; **`@PathVariable`** without duplicate **`@NonNull`** |
| DTO records with optional IDs | **`@Nullable Long`** on `subTaskId`, `expiresAt`, etc. |
| All `*IT.java` / `*Test.java` under `src/test/java` | **Constructor injection** instead of field **`@Autowired`** |

---

## API audit (current state — no behavioral change expected)

- **`UserService.create`** returns **`User`** (always persisted) — **keep** non-null **`User`**; annotate **`@NonNull`** return.
- **`InviteService.list(Long challengeIdFilter)`** — filter **`@Nullable`**; return **`@NonNull List<Invite>`**.
- **`CommentService.listForChallenge(Long challengeId, Long subTaskIdFilter)`** — **`challengeId`** **`@NonNull`**; second arg **`@Nullable`**.
- **`ScheduleService.parseWeekDays(List<String> raw)`** — parameter **`@Nullable`** (JSpecify); return **`@NonNull List<DayOfWeek>`**; use **`Objects.requireNonNull(Collections.emptyList())`** / **`Objects.requireNonNull(stream…toList())`** so Eclipse null analysis accepts **`@NonNull List<DayOfWeek>`** (JDK collectors are not annotated).
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

- [x] **Step 1:** Add four **`package-info.java`** files with identical structure; only **`package`** line differs.

[`src/main/java/com/challenges/api/service/package-info.java`](../../src/main/java/com/challenges/api/service/package-info.java):

```java
@NullMarked
package com.challenges.api.service;

import org.jspecify.annotations.NullMarked;
```

Repeat for:

- `package com.challenges.api.web;`
- `package com.challenges.api.web.dto;`
- `package com.challenges.api.repo;`

- [x] **Step 2:** `./gradlew compileJava --no-daemon` → **SUCCESS**

- [x] **Step 3:** Commit **`chore: add NullMarked package defaults`**

---

### Task 2: Service layer — `@Nullable` filters + `Assert.notNull` gaps

**Files:** (modify each)

- [`src/main/java/com/challenges/api/service/InviteService.java`](../../src/main/java/com/challenges/api/service/InviteService.java)
- [`src/main/java/com/challenges/api/service/CommentService.java`](../../src/main/java/com/challenges/api/service/CommentService.java)
- [`src/main/java/com/challenges/api/service/ScheduleService.java`](../../src/main/java/com/challenges/api/service/ScheduleService.java)
- [`src/main/java/com/challenges/api/service/ChallengeService.java`](../../src/main/java/com/challenges/api/service/ChallengeService.java) — ensure **`create`/`replace`** assert **`req`** not null if not already
- [`src/main/java/com/challenges/api/service/SubTaskService.java`](../../src/main/java/com/challenges/api/service/SubTaskService.java)
- [`src/main/java/com/challenges/api/service/UserService.java`](../../src/main/java/com/challenges/api/service/UserService.java)

- [x] **Step 1:** Add imports for member-level nullness (JSpecify, not Spring):

```java
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
```

Use **`@NonNull`** on:
- **Return types** where useful (e.g. **`@NonNull List<Invite>`**, **`@NonNull User`**).
- **Parameters** that are always required: resource ids (**`Long id`**, **`challengeId`**), request records (**`CommentRequest req`**), and any argument that is not an optional filter. Example:

```java
public Optional<Comment> create(@NonNull Long challengeId, @NonNull CommentRequest req)
public boolean delete(@NonNull Long id)
```

**Optional — `ScheduleService` bridging:** After **`@NullMarked`**, repository and stream results may still trigger *unchecked conversion* warnings. Where appropriate:

- **`parseWeekDays`:** `return Objects.requireNonNull(Collections.emptyList());` and `return Objects.requireNonNull(raw.stream()…toList());` (add **`import java.util.Collections`** if needed).
- **`createForChallenge` / `createForSubTask`:** e.g. `return Objects.requireNonNull(repo.findById(id).map(e -> replace…(Objects.requireNonNull(e), …)));`
- **`findById` / `update`:** wrap **`findByIdWithAssociations`** / **`map`** / **`schedules.save`** results with **`Objects.requireNonNull(...)`** as needed for a clean Problems view.

Keep **`Assert.notNull`** for **API** preconditions; use **`Objects.requireNonNull`** for **annotation / IDE** bridging of trusted framework return values.

- [x] **Step 2:** **`InviteService`**: change signature to:

```java
@Transactional(readOnly = true)
public @NonNull List<Invite> list(@Nullable Long challengeIdFilter) {
```

- [x] **Step 3:** **`CommentService`**: change **`listForChallenge`** to:

```java
@Transactional(readOnly = true)
public @NonNull List<Comment> listForChallenge(@NonNull Long challengeId, @Nullable Long subTaskIdFilter) {
```

(body unchanged; **`Assert.notNull(challengeId, ...)`** already present). Apply the same **parameter `@NonNull`** pattern to other service methods with required ids or DTOs (**`create`**, **`update`**, **`findById`**, **`delete`**, etc.).

- [x] **Step 4:** **`ScheduleService`**: annotate **`parseWeekDays`**:

```java
public static @NonNull List<DayOfWeek> parseWeekDays(@Nullable List<String> raw) {
```

Add at start of **`createForChallenge`**, **`createForSubTask`**, and **`update`**:

```java
Assert.notNull(weekDays, "weekDays must not be null");
```

(Place **`Assert.notNull(weekDays, ...)`** immediately after **`kind`** / **`id`** asserts in each method.)

- [x] **Step 5:** **`ChallengeService.replace`** — if **`req`** is not asserted, add **`Assert.notNull(req, "request must not be null");`** at method entry. **`SubTaskService.create`/`replace`** — same for **`req`** where missing.

- [x] **Step 6:** **`./gradlew compileJava --no-daemon`** → **SUCCESS**

- [x] **Step 7:** Commit **`refactor(services): Nullable filters and assert weekDays`**

---

### Task 3: DTO records — `@Nullable` optional fields

**Files:** (edit each record; add import `org.jspecify.annotations.Nullable`)

- [`InviteRequest.java`](../../src/main/java/com/challenges/api/web/dto/InviteRequest.java) — **`subTaskId`**, **`status`**, **`expiresAt`**
- [`CommentRequest.java`](../../src/main/java/com/challenges/api/web/dto/CommentRequest.java) — **`subTaskId`**
- [`ScheduleCreateRequest.java`](../../src/main/java/com/challenges/api/web/dto/ScheduleCreateRequest.java) — **`challengeId`** vs **`subTaskId`** (one of; both may appear in validation — mark nullable components that are optional in JSON)
- Any other DTO where **`Long`** field is optional in API

Example for **`InviteRequest`**:

```java
import org.jspecify.annotations.Nullable;

public record InviteRequest(
		@NotNull Long inviterUserId,
		@NotNull Long inviteeUserId,
		@NotNull Long challengeId,
		@Nullable Long subTaskId,
		@Nullable InviteStatus status,
		@Nullable Instant expiresAt) {}
```

- [x] **Step 1:** Apply **`@Nullable`** to optional components (do **not** mark **`@NotNull`** on primitives/strings already covered by **`@NotBlank`/`@NotNull`** from Jakarta unless you add **both** — prefer minimal diff: only **`@Nullable`** on optional reference types).

- [x] **Step 2:** `./gradlew compileJava --no-daemon` → **SUCCESS**

- [x] **Step 3:** Commit **`refactor(dto): mark optional fields Nullable`**

---

### Task 4: Controllers & `GlobalExceptionHandler` — explicit nullness

**Files:**

- [`src/main/java/com/challenges/api/web/CommentController.java`](../../src/main/java/com/challenges/api/web/CommentController.java)
- [`CheckInController.java`](../../src/main/java/com/challenges/api/web/CheckInController.java)
- Remaining controllers as needed

- [x] **Step 1:** On **`@RequestParam(required = false) Long subTaskId`** in **`CommentController.list`**, add **`@Nullable Long subTaskId`** (import **`org.jspecify.annotations.Nullable`**).

- [x] **Step 2:** Add **`@NonNull`** (**`org.jspecify.annotations.NonNull`**) on **`List<...>`** return types in controllers where you want IDE clarity (optional; package default already implies non-null references). If the IDE warns on **`stream().…toList()`**, wrap with **`Objects.requireNonNull(...)`** on the return expression.

- [x] **Step 2b:** **Do not** add JSpecify **`@NonNull`** or Jakarta **`@NotNull`** on **`@PathVariable`** parameters — **`@PathVariable`** is **required by default** (`required = true`), so a separate nullness annotation is redundant; package **`@NullMarked`** already treats the parameter as non-null. Use **`@PathVariable Long challengeId`** (or **`@PathVariable("id") Long id`**), not **`@PathVariable @NonNull Long …`**. Do **not** add **`@NonNull`** on **`@RequestParam(required = false)`** — use **`@Nullable`** there.

- [x] **Step 3:** `./gradlew compileJava` → **SUCCESS**

- [x] **Step 4:** Commit **`refactor(web): Nullable request params where optional`**

---

### Task 5: Tests — constructor injection only

**Scope:** Every class under **`src/test/java`** that uses **field** **`@Autowired`**.

Known files (from grep):  
`CommentControllerIT`, `ChallengeDomainWorkflowIT`, `InviteControllerIT`, `ParticipantControllerIT`, `CheckInControllerIT`, `ScheduleControllerIT`, `SubTaskControllerIT`, `ChallengeControllerIT`, `UserControllerIT`, `InviteRepositoryTest`, `CheckInRepositoryTest`, `ScheduleRepositoryTest`, `ParticipantRepositoryTest`, `SubTaskRepositoryTest`, `ChallengeRepositoryTest`, `UserRepositoryTest`, `DomainBulkFixtureIT`.

- [x] **Step 1:** For each test class, replace:

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

- [x] **Step 2:** Remove unused **`import org.springframework.beans.factory.annotation.Autowired;`** from each updated file.

- [x] **Step 3:** For tests with **multiple** dependencies, use one constructor listing all (e.g. **`MockMvc`**, **`ObjectMapper`**, **`UserRepository`**, …).

- [x] **Step 4:** Run:

```bash
./gradlew test --no-daemon
```

Expected: **BUILD SUCCESSFUL**

- [x] **Step 5:** Commit **`refactor(test): constructor injection in IT and repository tests`**

---

### Task 6: Repositories (optional explicit `@NonNull` on custom methods)

**Files:** [`CommentRepository.java`](../../src/main/java/com/challenges/api/repo/CommentRepository.java), other repos with **`@Query`**

- [x] **Step 1:** No need to redeclare inherited **`findById`** from **`JpaRepository`**. Optionally add **`org.jspecify.annotations.NonNull`** on **`List<...>`** return types of custom query methods — only if the compiler/IDE stays clean. Use **`Objects.requireNonNull`** on returns only if the analyzer still complains about Spring Data signatures.

- [x] **Step 2:** `./gradlew compileJava` → **SUCCESS**

- [x] **Step 3:** Commit only if changes exist: **`chore(repo): clarify NonNull list returns`**

---

### Task 7: Full verification

- [x] **Step 1:**

```bash
./gradlew test --no-daemon
```

Expected: **BUILD SUCCESSFUL**

- [x] **Step 2:** Commit only if formatting or remaining fixes.

---

## Self-review

| Requirement | Task |
|-------------|------|
| **`Optional`** for missing single results | Already standard in services; plan adds **annotations** + **`Schedule`** **`weekDays`** validation — **no** new `null` returns |
| **`@NullMarked` (package) + JSpecify `@Nullable` / `@NonNull`** | **1–4** |
| **`@NonNull` on required parameters** | **Task 2** (services); **Task 4** for **`@RequestBody`** / non-path params — **not** on **`@PathVariable`** (redundant with Spring default + **`@NullMarked`**) |
| **`Objects.requireNonNull` bridging** | Documented in **Task 2** / **Task 4**; **`ScheduleService`** is the main example |
| **Constructor injection** | **Production OK**; **5** for **tests** |
| **Early validation (`Assert.notNull`)** | **2** (**weekDays**, **req** gaps) |

**Placeholder scan:** None.

**Type consistency:** **`list(@Nullable Long challengeIdFilter)`** callers pass **`Long`** or **`null`** from controller **`@RequestParam(required = false)`** — unchanged behavior.

---

## Execution handoff

**Plan path:** [`docs/superpowers/plans/2026-04-18-07-nullability-optional-constructors.md`](2026-04-18-07-nullability-optional-constructors.md)

**Plan complete and saved to `docs/superpowers/plans/2026-04-18-07-nullability-optional-constructors.md`. Two execution options:**

**1. Subagent-Driven (recommended)** — Fresh subagent per task; use **superpowers:subagent-driven-development**.

**2. Inline Execution** — Batched in this session with **superpowers:executing-plans**.

**Which approach?**
