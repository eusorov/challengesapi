# REST API Controllers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose **JSON REST** endpoints under **`/api/...`** for **CRUD** on **`User`**, **`Challenge`**, **`SubTask`**, **`Schedule`**, **`Invite`**, and **`CheckIn`**, plus **read-only listing of `Participant`** rows for a challenge. **API versions are negotiated with a request header**, not a **`/v1`** path segment (see **API versioning** below). Callers pass **`userId` / `ownerUserId` / foreign keys in request bodies or path variables** as needed. **Authentication and authorization are out of scope** (no JWT, no session enforcement—treat identity as caller-supplied data only).

**Architecture:** Use **Spring Framework 7 first-class API versioning**: a **`WebMvcConfigurer`** bean implements **`configureApiVersioning(ApiVersionConfigurer)`** and **`configurer.useRequestHeader("API-Version")`** so the version is read from the **`API-Version`** HTTP header (see [Spring MVC – API Versioning](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/api-version.html)). Each **`@RestController`** uses **`@RequestMapping(path = "/api/…", version = "1")`**. **Error handling:** exactly **one** class, **`GlobalExceptionHandler`**, annotated with **`@RestControllerAdvice`**, is the **only** place that converts **framework and persistence failures** to **`ProblemDetail`** responses (**400 / 409 / 500** as appropriate). **Do not** introduce custom exception types for “not found” or business validation—**controllers and services** return **`Optional`** / **`ResponseEntity.notFound()`** (and similar) for **404** instead of throwing. **`IllegalStateException` / `IllegalArgumentException`** from **JPA entity callbacks** (`@PrePersist`, etc.) may still reach **`GlobalExceptionHandler`** as **400**—that is expected. **Do not** add a second **`@ControllerAdvice`**.

**Layering:** **`@RestController`** classes live in **`com.challenges.api.web`** and **only** delegate HTTP ↔ JSON to **application services** in **`com.challenges.api.service`** (`UserService`, `ChallengeService`, `SubTaskService`, `ScheduleService`, `InviteService`, `CheckInService`, `ParticipantService`). **Repositories** (**`com.challenges.api.repo`**, **`JpaRepository`**) are **only** injected into **`@Service`** types, not into controllers. Controllers map **`…Response.from(entity)`** / **`…Request`** bodies; **transaction boundaries** (`@Transactional`) belong on services (read-only vs write methods as appropriate). **DTOs** remain in **`com.challenges.api.web.dto`** (services may accept these request records to avoid duplication). **`ScheduleService`** centralizes schedule **`bindSchedule`** + replace/delete semantics. **Participants** are **read-only** over HTTP (**`GET` only**).

**Tech Stack:** Spring Boot **4.0.5** (includes **Spring Framework 7** with **`ApiVersionConfigurer`**), Java **25**, Spring Web MVC (`spring-boot-starter-webmvc`), Spring Data JPA (existing), **`spring-boot-starter-validation`**. Tests: **`@SpringBootTest` + `@AutoConfigureMockMvc`** with **MockMvc**—every request must include **`API-Version: 1`** (or use a **`@BeforeEach`** that wraps **`mockMvc.perform`** with a **`RequestPostProcessor`** / custom helper).

---

## File map (after completion)

| Path | Role |
|------|------|
| [`build.gradle`](../../build.gradle) | Add **`spring-boot-starter-validation`**. |
| [`src/main/java/com/challenges/api/web/GlobalExceptionHandler.java`](../../src/main/java/com/challenges/api/web/GlobalExceptionHandler.java) | **Single** `@RestControllerAdvice` → **`ProblemDetail`** (only central handler). |
| [`src/main/java/com/challenges/api/web/dto/*.java`](../../src/main/java/com/challenges/api/web/dto) | Request/response **records** per resource. |
| [`src/main/java/com/challenges/api/web/ApiVersionWebConfiguration.java`](../../src/main/java/com/challenges/api/web/ApiVersionWebConfiguration.java) | **`configureApiVersioning`** → **`useRequestHeader("API-Version")`**. |
| [`src/main/java/com/challenges/api/service/*.java`](../../src/main/java/com/challenges/api/service) | **`@Service`** — **use cases**, **`JpaRepository`** access, **`@Transactional`**. |
| [`src/main/java/com/challenges/api/web/UserController.java`](../../src/main/java/com/challenges/api/web/UserController.java) | **`/api/users`** → **`UserService`** only |
| [`src/main/java/com/challenges/api/web/ChallengeController.java`](../../src/main/java/com/challenges/api/web/ChallengeController.java) | **`/api/challenges`** → **`ChallengeService`** |
| [`src/main/java/com/challenges/api/web/SubTaskController.java`](../../src/main/java/com/challenges/api/web/SubTaskController.java) | **`/api/subtasks`** + nested list → **`SubTaskService`** |
| [`src/main/java/com/challenges/api/service/ScheduleService.java`](../../src/main/java/com/challenges/api/service/ScheduleService.java) | Schedule XOR + **`bindSchedule`** + replace/delete |
| [`src/main/java/com/challenges/api/web/ScheduleController.java`](../../src/main/java/com/challenges/api/web/ScheduleController.java) | **`/api/schedules`** → **`ScheduleService`** |
| [`src/main/java/com/challenges/api/web/InviteController.java`](../../src/main/java/com/challenges/api/web/InviteController.java) | **`/api/invites`** → **`InviteService`** |
| [`src/main/java/com/challenges/api/web/CheckInController.java`](../../src/main/java/com/challenges/api/web/CheckInController.java) | **`/api/check-ins`** → **`CheckInService`** |
| [`src/main/java/com/challenges/api/web/ParticipantController.java`](../../src/main/java/com/challenges/api/web/ParticipantController.java) | **`GET`** **`/api/challenges/{challengeId}/participants`** → **`ParticipantService`** |
| [`src/main/java/com/challenges/api/model/*.java`](../../src/main/java/com/challenges/api/model) | **Mutators** on entities for **PUT** (see **Task 3**). |
| [`src/main/java/com/challenges/api/repo/*.java`](../../src/main/java/com/challenges/api/repo) | Extra **`find…`** methods (**Task 4**). |
| [`src/test/java/com/challenges/api/web/`](../../src/test/java/com/challenges/api/web) | MockMvc integration tests. |
| [`AGENTS.md`](../../AGENTS.md) | Mention **REST base path** **`/api`**, **header `API-Version: 1`**, and **no auth** in this phase. |

---

## API versioning (normative)

All JSON endpoints below are served under **`/api/**`** and require a **supported API version** carried in a **request header**, not in the URL path.

| Item | Value |
|------|--------|
| **Header name** | **`API-Version`** (Spring’s default when using **`useRequestHeader("API-Version")`**; confirm in [Spring MVC API versioning](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/api-version.html)) |
| **Supported version** (initial) | **`1`** — send **`API-Version: 1`** on **every** request to versioned controllers |
| **Framework mapping** | **`@RequestMapping(..., version = "1")`** on the controller class (or per handler); unsupported / missing version behavior is defined by Spring (typically **400** for invalid version) |

**Example (curl):**

```http
GET /api/users HTTP/1.1
Host: localhost:8080
Accept: application/json
API-Version: 1
```

**Integration tests:** add **`.header("API-Version", "1")`** to every **`MockMvc.perform(...)`** call, or a shared **`RequestPostProcessor`** (e.g. `apiV1()`) to avoid repetition.

---

## API summary (normative for the plan)

All paths assume **`API-Version: 1`** as above.

| Resource | Create (POST) | Read (GET) | Update (PUT) | Delete (DELETE) |
|----------|----------------|------------|----------------|-----------------|
| **Users** | `/api/users` body `{ "email": "…" }` | `/api/users`, `/api/users/{id}` | `/api/users/{id}` body `{ "email": "…" }` | `/api/users/{id}` |
| **Challenges** | `/api/challenges` body includes **`ownerUserId`**, `title`, `description?`, `startDate`, `endDate?` | list + by id | same shape as create (include **ownerUserId** if owner may change) | by id |
| **SubTasks** | `/api/subtasks` body `{ "challengeId", "title", "sortIndex" }` | `/api/challenges/{challengeId}/subtasks`, `/api/subtasks/{id}` | `{ "title", "sortIndex" }` (**challenge** not moved) | `/api/subtasks/{id}` |
| **Schedules** | `/api/schedules` body: **exactly one** of `challengeId` **or** `subTaskId`, plus `kind`, `weekDays` (strings: `MONDAY` … `SUNDAY`) | `/api/schedules/{id}` | body: `kind`, `weekDays` (owner not switched) | `/api/schedules/{id}` (clear inverse refs carefully—**service** handles) |
| **Invites** | `/api/invites` with **`inviterUserId`**, **`inviteeUserId`**, **`challengeId`**, **`subTaskId?`**, optional status/expiry | list optional + by id | status / expiry / ids as needed | by id |
| **Check-ins** | `/api/check-ins` `{ "userId", "challengeId", "checkDate", "subTaskId?" }` | by id; list **`GET /api/challenges/{challengeId}/check-ins`** | `{ "checkDate", "subTaskId?" }` or full replace | by id |
| **Participants** | — | **`GET /api/challenges/{challengeId}/participants`** | — | — |

**JSON dates:** ISO-8601 **`LocalDate`** as `"2026-04-18"`. **Instants** as ISO-8601 strings.

---

### Task 1: Validation + single global exception handler

**Files:**
- Modify: `build.gradle`
- Create: `src/main/java/com/challenges/api/web/GlobalExceptionHandler.java`

**Rules:** **One** `@RestControllerAdvice` type only—**`GlobalExceptionHandler`**. No **`EntityNotFoundException`** (or similar) for control flow; controllers return **`ResponseEntity.notFound()`** where appropriate. This class handles **validation**, **binding**, **JSON parse errors**, **data integrity**, **IllegalArgument/IllegalState** (e.g. from JPA lifecycle callbacks), and a **last-resort** **`Exception`** → **500**.

- [ ] **Step 1:** In **`dependencies { }`** add:

```groovy
implementation 'org.springframework.boot:spring-boot-starter-validation'
```

- [ ] **Step 2:** Create **`GlobalExceptionHandler`**:

```java
package com.challenges.api.web;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException ex) {
		String msg = ex.getBindingResult().getFieldErrors().stream()
				.map(f -> f.getField() + ": " + f.getDefaultMessage())
				.findFirst()
				.orElse("Validation error");
		return problem(HttpStatus.BAD_REQUEST, msg);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ProblemDetail> notReadable(HttpMessageNotReadableException ex) {
		return problem(HttpStatus.BAD_REQUEST, "Malformed JSON or incompatible body: " + ex.getMessage());
	}

	@ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
	public ResponseEntity<ProblemDetail> badRequest(RuntimeException ex) {
		return problem(HttpStatus.BAD_REQUEST, ex.getMessage());
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ProblemDetail> conflict(DataIntegrityViolationException ex) {
		return problem(
				HttpStatus.CONFLICT, "Data integrity violation: " + ex.getMostSpecificCause().getMessage());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ProblemDetail> fallback(Exception ex) {
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error");
	}

	private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
		return ResponseEntity.status(status).body(pd);
	}
}
```

Tune the **`Exception`** handler in production (log full stack, generic message only to client).

- [ ] **Step 3:** `./gradlew compileJava --no-daemon` → **BUILD SUCCESSFUL**

- [ ] **Step 4:** Commit

```bash
git add build.gradle src/main/java/com/challenges/api/web/GlobalExceptionHandler.java
git commit -m "feat: add validation and global exception handler"
```

---

### Task 2: Enable header-based API versioning (Spring MVC)

**Files:**
- Create: `src/main/java/com/challenges/api/web/ApiVersionWebConfiguration.java`

**Reference:** [Spring MVC — API Versioning](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/api-version.html) ( **`WebMvcConfigurer#configureApiVersioning`**, **`ApiVersionConfigurer.useRequestHeader`** ).

- [ ] **Step 1:** Add configuration **without** `@EnableWebMvc` (Spring Boot auto-config must remain in charge):

```java
package com.challenges.api.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ApiVersionWebConfiguration implements WebMvcConfigurer {

	@Override
	public void configureApiVersioning(ApiVersionConfigurer configurer) {
		configurer.useRequestHeader("API-Version");
	}
}
```

**Import:** **`org.springframework.web.servlet.config.annotation.ApiVersionConfigurer`** (Servlet / Spring MVC). WebFlux uses the **`org.springframework.web.reactive.config`** variant—do **not** mix them.

- [ ] **Step 2:** `./gradlew compileJava --no-daemon` → **BUILD SUCCESSFUL**

- [ ] **Step 3:** Commit

```bash
git add src/main/java/com/challenges/api/web/ApiVersionWebConfiguration.java
git commit -m "feat: enable API versioning via API-Version header"
```

---

### Task 3: Entity mutators for updates (PUT)

**Files:** Modify each listed entity.

Controllers must load entities by id and apply changes, then **`save`**. Without setters, **PUT** cannot be implemented cleanly.

- [ ] **Step 1:** **`User.java`** — add:

```java
public void setEmail(String email) {
	this.email = java.util.Objects.requireNonNull(email);
}
```

- [ ] **Step 2:** **`Challenge.java`** — add:

```java
public void setOwner(User owner) {
	this.owner = java.util.Objects.requireNonNull(owner);
}

public void setTitle(String title) {
	this.title = java.util.Objects.requireNonNull(title);
}

public void setDescription(String description) {
	this.description = description;
}

public void setStartDate(java.time.LocalDate startDate) {
	this.startDate = java.util.Objects.requireNonNull(startDate);
}

public void setEndDate(java.time.LocalDate endDate) {
	this.endDate = endDate;
}
```

- [ ] **Step 3:** **`SubTask.java`** — add:

```java
public void setTitle(String title) {
	this.title = java.util.Objects.requireNonNull(title);
}

public void setSortIndex(int sortIndex) {
	this.sortIndex = sortIndex;
}
```

- [ ] **Step 4:** **`CheckIn.java`** — add setters so PUT can change date / subtask reference:

```java
public void setUser(User user) {
	this.user = user;
}

public void setChallenge(Challenge challenge) {
	this.challenge = challenge;
}

public void setCheckDate(java.time.LocalDate checkDate) {
	this.checkDate = java.util.Objects.requireNonNull(checkDate);
}

public void setSubTask(SubTask subTask) {
	this.subTask = subTask;
}
```

- [ ] **Step 5:** **`Schedule.java`** — add methods to replace cadence (used by PUT; clear and repopulate **`weekDays`** list in-place so **`@ElementCollection`** updates correctly):

```java
public void setKind(ScheduleKind kind) {
	this.kind = java.util.Objects.requireNonNull(kind);
}

public void replaceWeekDays(java.util.List<java.time.DayOfWeek> days) {
	this.weekDays.clear();
	if (days != null) {
		this.weekDays.addAll(days);
	}
}
```

- [ ] **Step 6:** `./gradlew test --no-daemon` → existing tests still **BUILD SUCCESSFUL**

- [ ] **Step 7:** Commit

```bash
git add src/main/java/com/challenges/api/model/User.java src/main/java/com/challenges/api/model/Challenge.java src/main/java/com/challenges/api/model/SubTask.java src/main/java/com/challenges/api/model/CheckIn.java src/main/java/com/challenges/api/model/Schedule.java
git commit -m "feat: add entity mutators for REST updates"
```

---

### Task 4: Repository query methods for nested lists

**Files:**
- Modify: `src/main/java/com/challenges/api/repo/SubTaskRepository.java`
- Modify: `src/main/java/com/challenges/api/repo/CheckInRepository.java`
- Modify: `src/main/java/com/challenges/api/repo/UserRepository.java` (optional **email** lookup—skip if unused)

- [ ] **Step 1:** **`SubTaskRepository`**

```java
java.util.List<com.challenges.api.model.SubTask> findByChallenge_IdOrderBySortIndexAsc(Long challengeId);
```

- [ ] **Step 2:** **`CheckInRepository`**

```java
java.util.List<com.challenges.api.model.CheckIn> findByChallenge_IdOrderByCheckDateDesc(Long challengeId);
```

- [ ] **Step 3:** `./gradlew test --no-daemon` → **BUILD SUCCESSFUL**

- [ ] **Step 4:** Commit

```bash
git add src/main/java/com/challenges/api/repo/SubTaskRepository.java src/main/java/com/challenges/api/repo/CheckInRepository.java
git commit -m "feat: add repository queries for nested REST lists"
```

---

### Task 5: DTO records (single package)

**Files:**
- Create under `src/main/java/com/challenges/api/web/dto/` — one file per record group or combined **`WebDtos.java`** if you prefer fewer files; the plan shows **separate files** for clarity.

Minimum set (abbreviated—implement with **exact JSON property names** used in controllers):

- `UserRequest` / `UserResponse`
- `ChallengeRequest` (`ownerUserId`, `title`, `description`, `startDate`, `endDate`) / `ChallengeResponse` (`id`, `ownerUserId`, …)
- `SubTaskRequest` (create: `challengeId`, `title`, `sortIndex`) / `SubTaskUpdateRequest` (`title`, `sortIndex`) / `SubTaskResponse`
- `ScheduleCreateRequest` (`challengeId`, `subTaskId`, `kind`, `weekDays` list of **String**) with **`@AssertTrue`** method `isExactlyOneOwner()` returning whether **xor** holds
- `ScheduleUpdateRequest` (`kind`, `weekDays`)
- `ScheduleResponse` (`id`, `challengeId`, `subTaskId`, `kind`, `weekDays`)
- `InviteRequest` / `InviteResponse` (map **`InviteStatus`** as string)
- `CheckInRequest` (create) / `CheckInUpdateRequest` / `CheckInResponse`
- `ParticipantResponse` (`id`, `userId`, `challengeId`, `subTaskId`, `joinedAt`)

**Jackson `DayOfWeek`:** Use **`String`** in DTOs and parse with **`DayOfWeek.valueOf(s)`** in controllers/services—invalid enum → **`IllegalArgumentException`** → handled by **`GlobalExceptionHandler`** (**400**).

- [ ] **Step 1:** Add all **`record`** types with **`jakarta.validation.constraints`** as appropriate (`@NotBlank`, `@NotNull`, `@Email` on user email).

- [ ] **Step 2:** `./gradlew compileJava --no-daemon` → **SUCCESS**

- [ ] **Step 3:** Commit

```bash
git add src/main/java/com/challenges/api/web/dto
git commit -m "feat: add REST DTO records"
```

---

### Task 6: `UserController`

**Files:**
- Create: `src/main/java/com/challenges/api/web/UserController.java`
- Create: `src/test/java/com/challenges/api/web/UserControllerIT.java`

- [ ] **Step 1:** Implement **`UserService`** in **`com.challenges.api.service`** (inject **`UserRepository`**; list/find/create/replace/delete with **`Optional`** / **`boolean`** for existence—match existing app behavior). **`UserController`** injects **`UserService` only**; map **`UserResponse.from`** in the controller. Example controller shape:

```java
@RestController
@RequestMapping(path = "/api/users", version = "1")
public class UserController {
	private final UserService userService;
	public UserController(UserService userService) { this.userService = userService; }

	@GetMapping
	public List<UserResponse> list() {
		return userService.listUsers().stream().map(UserResponse::from).toList();
	}
	// get / create / put / delete — delegate to userService, map DTOs, return ResponseEntity.notFound() when Optional empty
}
```

Implement **`UserRequest`** / **`UserResponse`** in **Task 5** with **`from`** factory: `UserResponse.from(User u)` returns **`id`, `email`, `createdAt`**.

- [ ] **Step 2:** **`UserControllerIT`** — `@SpringBootTest` + `@AutoConfigureMockMvc`; import **`org.springframework.http.MediaType`**. **`mockMvc.perform(post("/api/users").header("API-Version", "1").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"x@y.z\"}"))`** → **201**; then **GET** `/api/users` with the same **`API-Version`** header.

- [ ] **Step 3:** `./gradlew test --no-daemon`

- [ ] **Step 4:** Commit

```bash
git add src/main/java/com/challenges/api/web/UserController.java src/test/java/com/challenges/api/web/UserControllerIT.java src/main/java/com/challenges/api/web/dto/UserRequest.java src/main/java/com/challenges/api/web/dto/UserResponse.java
git commit -m "feat: add User REST API"
```

(Adjust paths if you merged DTO files.)

---

### Task 7: `ChallengeController`

**Files:**
- Create: `src/main/java/com/challenges/api/web/ChallengeController.java`
- Create: `src/test/java/com/challenges/api/web/ChallengeControllerIT.java`

- [ ] **Step 1:** Implement **`ChallengeService`** (**`UserRepository`** + **`ChallengeRepository`**). **`ChallengeController`** injects **`ChallengeService` only**; **`create`/`replace`** return **`Optional.empty()`** when owner or challenge row is missing → controller **`404`**.

- [ ] **Step 2:** Integration test: create two users, POST challenge with **`ownerUserId`**, GET by id assert **owner** id in JSON.

- [ ] **Step 3:** `./gradlew test --no-daemon` → commit **`feat: add Challenge REST API`**

---

### Task 8: `SubTaskController`

**Files:**
- Create: `src/main/java/com/challenges/api/web/SubTaskController.java`
- Create: `src/test/java/com/challenges/api/web/SubTaskControllerIT.java`

- [ ] **Step 1:** **`SubTaskService`** encapsulates **`ChallengeRepository`** + **`SubTaskRepository`**. **`SubTaskController`** delegates to **`SubTaskService` only**. Endpoints:
	- **`GET /api/challenges/{challengeId}/subtasks`** → **`findByChallenge_IdOrderBySortIndexAsc`**
	- **`GET/PUT/DELETE /api/subtasks/{id}`**
	- **`POST /api/subtasks`** with **`challengeId`** in body

- [ ] **Step 2:** Test nested list + CRUD.

- [ ] **Step 3:** Commit **`feat: add SubTask REST API`**

---

### Task 9: `ScheduleService` + `ScheduleController`

**Files:**
- Create: `src/main/java/com/challenges/api/service/ScheduleService.java`
- Create: `src/main/java/com/challenges/api/web/ScheduleController.java`
- Create: `src/test/java/com/challenges/api/web/ScheduleControllerIT.java`

- [ ] **Step 1:** **`ScheduleService`** in **`com.challenges.api.service`** (illustrative—must match repositories and any replace-existing-schedule behavior you add):

```java
package com.challenges.api.service;

import com.challenges.api.model.Challenge;
import com.challenges.api.model.Schedule;
import com.challenges.api.model.ScheduleKind;
import com.challenges.api.model.SubTask;
import com.challenges.api.repo.ChallengeRepository;
import com.challenges.api.repo.ScheduleRepository;
import com.challenges.api.repo.SubTaskRepository;
import java.time.DayOfWeek;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduleService {

	private final ScheduleRepository schedules;
	private final ChallengeRepository challenges;
	private final SubTaskRepository subTasks;

	public ScheduleService(ScheduleRepository schedules, ChallengeRepository challenges, SubTaskRepository subTasks) {
		this.schedules = schedules;
		this.challenges = challenges;
		this.subTasks = subTasks;
	}

	@Transactional
	public java.util.Optional<Schedule> createForChallenge(Long challengeId, ScheduleKind kind, List<DayOfWeek> weekDays) {
		return challenges.findById(challengeId).map(ch -> {
			Schedule s = Schedule.forChallenge(ch, kind, weekDays);
			ch.bindSchedule(s);
			return schedules.save(s);
		});
	}

	@Transactional
	public java.util.Optional<Schedule> createForSubTask(Long subTaskId, ScheduleKind kind, List<DayOfWeek> weekDays) {
		return subTasks.findById(subTaskId).map(st -> {
			Schedule s = Schedule.forSubTask(st, kind, weekDays);
			st.bindSchedule(s);
			return schedules.save(s);
		});
	}

	@Transactional
	public java.util.Optional<Schedule> update(Long id, ScheduleKind kind, List<DayOfWeek> weekDays) {
		return schedules.findById(id).map(s -> {
			s.setKind(kind);
			s.replaceWeekDays(weekDays);
			return schedules.save(s);
		});
	}

	@Transactional
	public boolean delete(Long id) {
		return schedules.findById(id).map(s -> {
			schedules.delete(s);
			return true;
		}).orElse(false);
	}
}
```

**Note:** **Deleting** a schedule that is the inverse of **`Challenge.schedule`** / **`SubTask.schedule`** may require **clearing inverse refs** before delete—if **orphanRemoval** is not configured and **`DELETE`** fails with FK violations, extend **delete** to: **load `Schedule`**, if **`challenge != null`**, **`challenge.bindSchedule(null)`**; if **`subTask != null`**, **`subTask.bindSchedule(null)`**; **`scheduleRepository.delete`**. Implement based on Hibernate error messages from an integration test.

- [ ] **Step 2:** **`ScheduleController`**: **POST** parses **`ScheduleCreateRequest`**, branches on **xor** owner, calls **`createForChallenge`** / **`createForSubTask`**; map **`Optional.empty()`** → **`ResponseEntity.notFound()`** or **400** if request references missing parent ids (choose one policy and document it).

- [ ] **Step 3:** Integration test: create challenge-only schedule **DAILY** with empty weekdays.

- [ ] **Step 4:** Commit **`feat: add Schedule REST API`**

---

### Task 10: `InviteController`

**Files:**
- Create: `src/main/java/com/challenges/api/web/InviteController.java`
- Create: `src/test/java/com/challenges/api/web/InviteControllerIT.java`

- [ ] **Step 1:** **`InviteService`** injects **`InviteRepository`**, **`UserRepository`**, **`ChallengeRepository`**, **`SubTaskRepository`**. **POST**: load entities, validate subtask/challenge, **`new Invite(...)`**, save. **PUT**: **`status`** / **`expiresAt`**. **GET** list optional **`?challengeId=`**. **`InviteController`** → **`InviteService` only**.

- [ ] **Step 2:** Test create + update status.

- [ ] **Step 3:** Commit **`feat: add Invite REST API`**

---

### Task 11: `CheckInController`

**Files:**
- Create: `src/main/java/com/challenges/api/web/CheckInController.java`
- Create: `src/test/java/com/challenges/api/web/CheckInControllerIT.java`

- [ ] **Step 1:** **`CheckInService`** loads **user, challenge, optional subtask**; **`new CheckIn(...)`**; listings via **`findByChallenge_IdOrderByCheckDateDesc`**. **PUT** validates **`subTask.challenge.id`** vs **`checkIn.challenge.id`** inside the service (**`IllegalStateException`**/**`IllegalArgumentException`** → **400**). **`CheckInController`** → **`CheckInService` only**.

- [ ] **Step 2:** Test create + list by challenge.

- [ ] **Step 3:** Commit **`feat: add CheckIn REST API`**

---

### Task 12: `ParticipantController` (read-only)

**Files:**
- Create: `src/main/java/com/challenges/api/web/ParticipantController.java`

- [ ] **Step 1:**

Implement **`ParticipantService`** (**`ParticipantRepository.findByChallenge_Id`**). **`ParticipantController`** injects **`ParticipantService` only** and maps **`ParticipantResponse.from`**.

```java
@RestController
@RequestMapping(path = "/api/challenges", version = "1")
public class ParticipantController {
	private final ParticipantService participantService;
	@GetMapping("/{challengeId}/participants")
	public List<ParticipantResponse> listForChallenge(@PathVariable Long challengeId) {
		return participantService.listForChallenge(challengeId).stream().map(ParticipantResponse::from).toList();
	}
}
```

- [ ] **Step 2:** Integration test that creates **`Participant`** via repository in **`@BeforeEach`** or inline, then **GET** returns rows.

- [ ] **Step 3:** Commit **`feat: add read-only Participant listing`**

---

### Task 13: Documentation + full suite

**Files:**
- Modify: `AGENTS.md`

- [ ] **Step 1:** Add bullets: **Resource paths** under **`/api/…`**, version via header **`API-Version: 1`**, **no authentication** in current phase, clients pass **user ids** in payloads as documented.

- [ ] **Step 2:** `./gradlew test --no-daemon` → **BUILD SUCCESSFUL**

- [ ] **Step 3:** Commit **`docs: document REST API phase in AGENTS.md`**

---

## Self-review

| Requirement | Task |
|-------------|------|
| **Single `GlobalExceptionHandler`** (no custom not-found exceptions) | **Task 1** |
| **Header API versioning** (`API-Version`, `configureApiVersioning`) | **Task 2** |
| CRUD Users | Task 6 |
| CRUD Challenges (**`ownerUserId`** in payload) | Task 7 |
| CRUD SubTasks | Task 8 |
| CRUD Schedules | Task 9 |
| CRUD Invites | Task 10 |
| CRUD Check-ins | Task 11 |
| Show participants (list) | Task 12 |
| Auth out of scope | Stated in header + **Task 13** (AGENTS) |
| Entity mutators + repository queries + **application services** (`com.challenges.api.service`, including **`ScheduleService`**) | Tasks **3–4**, **6–12** |

**Placeholder scan:** No TBD steps; **Schedule delete** notes a concrete follow-up if FK errors occur.

**Type consistency:** DTO field names **`ownerUserId`**, **`inviterUserId`**, **`challengeId`**, **`subTaskId`** align across tasks.

---

## Execution handoff

**Plan path:** [`docs/superpowers/plans/2026-04-18-04-rest-api-controllers.md`](2026-04-18-04-rest-api-controllers.md)

**Plan complete and saved to `docs/superpowers/plans/2026-04-18-04-rest-api-controllers.md`. Two execution options:**

**1. Subagent-Driven (recommended)** — Dispatch a fresh subagent per task; review between tasks; use **superpowers:subagent-driven-development**.

**2. Inline execution** — Execute tasks in this session using **superpowers:executing-plans**.

**Which approach?**
