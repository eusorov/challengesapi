# Comment entity and REST API — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Introduce a **`Comment`** JPA entity so **users** can attach threaded discussion to a **challenge** (challenge-wide) or to a **subtask** of that challenge, exposed through **`CommentService`** + **`CommentController`** with integration tests and **`API-Version: 1`**.

**Architecture:** One **`Comment`** table: every row references **`Challenge`** (for listing and FK consistency) and optionally **`SubTask`**—when **`subTask` is null** the comment is challenge-level; when **non-null** it is scoped to that subtask (**`@PrePersist` / `@PreUpdate`** enforce **`subTask.challenge`** equals **`Comment.challenge`**). No separate `ChallengeComment` / `SubTaskComment` tables (**DRY**). **`CommentService`** owns validation and **`CommentRepository`** queries; **`CommentController`** maps DTOs only (**same layering as** [`InviteService`](../../src/main/java/com/challenges/api/service/InviteService.java)). **404** via **`Optional`** / empty result (**no** new `NotFoundException` types).

**Tech Stack:** Java **25**, Spring Boot **4.x**, Spring Data JPA, **`spring-boot-starter-validation`**, existing **`GlobalExceptionHandler`**, MockMvc integration tests (`@SpringBootTest`, **`org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`**, Jackson **`tools.jackson.databind`** in tests).

---

## File map (after completion)

| Path | Role |
|------|------|
| [`src/main/java/com/challenges/api/model/Comment.java`](../../src/main/java/com/challenges/api/model/Comment.java) | JPA entity: author **`User`**, **`Challenge`**, optional **`SubTask`**, **`body`**, **`createdAt`**. |
| [`src/main/java/com/challenges/api/repo/CommentRepository.java`](../../src/main/java/com/challenges/api/repo/CommentRepository.java) | `JpaRepository<Comment, Long>` + list queries. |
| [`src/main/java/com/challenges/api/service/CommentService.java`](../../src/main/java/com/challenges/api/service/CommentService.java) | Create/list/get/update/delete; **`@Transactional`** + **`Assert.notNull`** on ids where applicable. |
| [`src/main/java/com/challenges/api/web/dto/CommentRequest.java`](../../src/main/java/com/challenges/api/web/dto/CommentRequest.java) | Create: **`userId`**, **`body`**, optional **`subTaskId`**. |
| [`src/main/java/com/challenges/api/web/dto/CommentUpdateRequest.java`](../../src/main/java/com/challenges/api/web/dto/CommentUpdateRequest.java) | Update: **`body`** only (**`@NotBlank`**). |
| [`src/main/java/com/challenges/api/web/dto/CommentResponse.java`](../../src/main/java/com/challenges/api/web/dto/CommentResponse.java) | **`from(Comment)`** — **`id`**, **`userId`**, **`challengeId`**, **`subTaskId`**, **`body`**, **`createdAt`**. |
| [`src/main/java/com/challenges/api/web/CommentController.java`](../../src/main/java/com/challenges/api/web/CommentController.java) | **`version = "1"`**, nested + by-id routes (see **API summary**). |
| [`src/test/java/com/challenges/api/web/CommentControllerIT.java`](../../src/test/java/com/challenges/api/web/CommentControllerIT.java) | MockMvc tests. |
| [`AGENTS.md`](../../AGENTS.md) | Mention **comments** resource (optional bullet). |

---

## API summary (normative)

All requests carry header **`API-Version: 1`**.

| Action | Method & path | Notes |
|--------|----------------|--------|
| List comments for a challenge | **`GET /api/challenges/{challengeId}/comments`** | Optional query **`?subTaskId=`** — if set, only comments for that subtask (must belong to challenge); if omitted, return **all** comments for the challenge (including subtask-scoped rows). |
| Create comment | **`POST /api/challenges/{challengeId}/comments`** | JSON **`{ "userId": long, "body": "…", "subTaskId": null \| long }`**. **`subTaskId` null** → challenge-wide comment. |
| Get one | **`GET /api/comments/{id}`** | |
| Update body | **`PUT /api/comments/{id}`** | JSON **`{ "body": "…" }`** — **YAGNI**: no ownership check (no auth); any caller can edit (**document** in AGENTS). |
| Delete | **`DELETE /api/comments/{id}`** | |

**Ordering:** List endpoints return **newest first** (**`createdAt DESC`**) — align repository method names with Spring Data sort.

**JSON:** **`createdAt`** as ISO-8601 instant string (default Jackson for **`Instant`**).

---

### Task 1: `Comment` entity

**Files:**
- Create: [`src/main/java/com/challenges/api/model/Comment.java`](../../src/main/java/com/challenges/api/model/Comment.java)

- [ ] **Step 1:** Add entity class with:

```java
@Entity
@Table(name = "comments")
public class Comment {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "author_user_id", nullable = false)
	private User author;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "challenge_id", nullable = false)
	private Challenge challenge;

	/** {@code null} = comment on the whole challenge; non-null = comment on this subtask (must belong to {@link #challenge}). */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "subtask_id")
	private SubTask subTask;

	@Column(nullable = false, length = 8000)
	private String body;

	@Column(nullable = false, updatable = false)
	private Instant createdAt = Instant.now();

	// protected ctor, public ctor(author, challenge, subTask, body), getters, setBody for PUT
	@PrePersist
	@PreUpdate
	private void validateSubTaskBelongsToChallenge() { /* same idea as Participant */ }
}
```

- [ ] **Step 2:** `./gradlew compileJava --no-daemon` → **SUCCESS**

- [ ] **Step 3:** Commit

```bash
git add src/main/java/com/challenges/api/model/Comment.java
git commit -m "feat: add Comment entity"
```

---

### Task 2: `CommentRepository`

**Files:**
- Create: [`src/main/java/com/challenges/api/repo/CommentRepository.java`](../../src/main/java/com/challenges/api/repo/CommentRepository.java)

- [ ] **Step 1:**

```java
public interface CommentRepository extends JpaRepository<Comment, Long> {

	List<Comment> findByChallenge_IdOrderByCreatedAtDesc(Long challengeId);

	List<Comment> findByChallenge_IdAndSubTask_IdOrderByCreatedAtDesc(Long challengeId, Long subTaskId);
}
```

- [ ] **Step 2:** `./gradlew compileJava --no-daemon` → **SUCCESS**

- [ ] **Step 3:** Commit **`feat: add CommentRepository`**

---

### Task 3: DTO records

**Files:**
- Create: [`src/main/java/com/challenges/api/web/dto/CommentRequest.java`](../../src/main/java/com/challenges/api/web/dto/CommentRequest.java)
- Create: [`src/main/java/com/challenges/api/web/dto/CommentUpdateRequest.java`](../../src/main/java/com/challenges/api/web/dto/CommentUpdateRequest.java)
- Create: [`src/main/java/com/challenges/api/web/dto/CommentResponse.java`](../../src/main/java/com/challenges/api/web/dto/CommentResponse.java)

- [ ] **Step 1:** Implement:

```java
// CommentRequest — @NotNull Long userId, @NotBlank String body, Long subTaskId (optional)
// CommentUpdateRequest — @NotBlank String body
// CommentResponse — record + public static CommentResponse from(Comment c) { ... }
```

Use **`Instant`** in **`CommentResponse`** for **`createdAt`**; expose as field for JSON serialization.

- [ ] **Step 2:** `./gradlew compileJava --no-daemon` → **SUCCESS**

- [ ] **Step 3:** Commit **`feat: add Comment DTOs`**

---

### Task 4: `CommentService`

**Files:**
- Create: [`src/main/java/com/challenges/api/service/CommentService.java`](../../src/main/java/com/challenges/api/service/CommentService.java)

- [ ] **Step 1:** Inject **`CommentRepository`**, **`UserRepository`**, **`ChallengeRepository`**, **`SubTaskRepository`**.

- [ ] **Step 2:** Methods (signatures illustrative):

```java
List<Comment> listForChallenge(Long challengeId, Long subTaskIdFilter);

Optional<Comment> create(Long challengeId, CommentRequest req);

Optional<Comment> findById(Long id);

Optional<Comment> update(Long id, CommentUpdateRequest req);

boolean delete(Long id);
```

**`listForChallenge`:** **`Assert.notNull(challengeId)`**; if **`subTaskIdFilter != null`**, return **`findByChallenge_IdAndSubTask_IdOrderByCreatedAtDesc(challengeId, subTaskIdFilter)`** after verifying subtask belongs to challenge (load subtask, compare **`getChallenge().getId()`**); if **`subTaskIdFilter == null`**, **`findByChallenge_IdOrderByCreatedAtDesc(challengeId)`**.

**`create`:** Load **user**, **challenge**; if **`req.subTaskId()`** present load **subtask** and validate same challenge; else **`subTask = null`**; **`new Comment(author, challenge, subTask, req.body())`**; **`save`**. Return **empty** if user or challenge missing, or subtask missing / wrong challenge (**404** path in controller).

**`update`:** **`findById`**, **`setBody`**, **`save`**.

**`delete`:** **`existsById`** + **`deleteById`** returning **boolean**.

Use **`@Transactional`** / **`readOnly = true`** mirroring [`UserService`](../../src/main/java/com/challenges/api/service/UserService.java).

- [ ] **Step 3:** `./gradlew compileJava --no-daemon` → **SUCCESS**

- [ ] **Step 4:** Commit **`feat: add CommentService`**

---

### Task 5: `CommentController`

**Files:**
- Create: [`src/main/java/com/challenges/api/web/CommentController.java`](../../src/main/java/com/challenges/api/web/CommentController.java)

- [ ] **Step 1:**

```java
@RestController
@RequestMapping(path = "/api", version = "1")
public class CommentController {

	private final CommentService commentService;

	public CommentController(CommentService commentService) {
		this.commentService = commentService;
	}

	@GetMapping("/challenges/{challengeId}/comments")
	public List<CommentResponse> list(
			@PathVariable Long challengeId, @RequestParam(required = false) Long subTaskId) {
		return commentService.listForChallenge(challengeId, subTaskId).stream()
				.map(CommentResponse::from)
				.toList();
	}

	@PostMapping("/challenges/{challengeId}/comments")
	public ResponseEntity<CommentResponse> create(
			@PathVariable Long challengeId, @Valid @RequestBody CommentRequest req) {
		return commentService.create(challengeId, req)
				.map(c -> ResponseEntity.status(HttpStatus.CREATED).body(CommentResponse.from(c)))
				.orElse(ResponseEntity.notFound().build());
	}

	@GetMapping("/comments/{id}")
	public ResponseEntity<CommentResponse> get(@PathVariable Long id) { ... }

	@PutMapping("/comments/{id}")
	public ResponseEntity<CommentResponse> replace(
			@PathVariable Long id, @Valid @RequestBody CommentUpdateRequest req) { ... }

	@DeleteMapping("/comments/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) { ... }
}
```

Use **`ResponseEntity`** **404** when **`Optional`** empty. Do **not** add Lombok — this repo does not use it (**explicit constructor**).

- [ ] **Step 2:** `./gradlew compileJava --no-daemon` → **SUCCESS**

- [ ] **Step 3:** Commit **`feat: add Comment REST controller`**

---

### Task 6: Integration tests `CommentControllerIT`

**Files:**
- Create: [`src/test/java/com/challenges/api/web/CommentControllerIT.java`](../../src/test/java/com/challenges/api/web/CommentControllerIT.java)

- [ ] **Step 1:** Follow [`UserControllerIT`](../../src/test/java/com/challenges/api/web/UserControllerIT.java): **`@SpringBootTest`**, **`@AutoConfigureMockMvc`**, **`@Transactional`**, constants **`HV` / `V1`**, **`tools.jackson.databind.ObjectMapper`**.

- [ ] **Step 2:** **`@BeforeEach`**: persist **owner** + **commenter** **`User`**, **`Challenge`** owned by owner, optionally one **`SubTask`** (via **`SubTaskRepository`** or **`Challenge` + new SubTask(...)** + **`save`**) — mirror [`InviteControllerIT`](../../src/test/java/com/challenges/api/web/InviteControllerIT.java) setup style.

- [ ] **Step 3:** **Test A —** **`POST /api/challenges/{id}/comments`** with **`userId`** = commenter, **`body`**, **`subTaskId: null`** → **201**; **`GET /api/challenges/{id}/comments`** → one item, **`subTaskId` empty** in JSON.

- [ ] **Step 4:** **Test B —** **`POST`** with **`subTaskId`** set → **201**; **`GET …/comments?subTaskId=`** returns only that row; **`GET`** without query returns **≥ 2** comments (or **2** if only these two created).

- [ ] **Step 5:** **Test C —** **`PUT /api/comments/{id}`** changes body; **`GET`** reflects change; **`DELETE`** → **204**; **`GET /api/comments/{id}`** → **404**.

- [ ] **Step 6:** Run:

```bash
./gradlew test --tests com.challenges.api.web.CommentControllerIT --no-daemon
```

Expected: **BUILD SUCCESSFUL**, all tests **PASSED**

- [ ] **Step 7:** Commit **`test: add CommentControllerIT`**

---

### Task 7: Documentation

**Files:**
- Modify: [`AGENTS.md`](../../AGENTS.md)

- [ ] **Step 1:** Under domain / REST bullets, add that **comments** live under **`/api/challenges/.../comments`** and **`/api/comments/{id}`**, with **`API-Version: 1`**, and that **editing/deleting** is not restricted by auth in this phase.

- [ ] **Step 2:** Commit **`docs: document Comment API`**

---

### Task 8: Full test suite

- [ ] **Step 1:**

```bash
./gradlew test --no-daemon
```

Expected: **BUILD SUCCESSFUL**

- [ ] **Step 2:** Commit only if formatting changed tracked files.

---

## Self-review

| Requirement | Task |
|-------------|------|
| New **Comment** entity, challenge + optional subtask | **1** |
| **User** as author | **1** |
| **Repository** list + create path | **2** |
| **Service** + validation | **4** |
| **REST** nested + by id | **5** |
| **Tests** | **6** |
| Docs | **7** |

**Placeholder scan:** All tasks include concrete interfaces or code; list filtering semantics spelled out (**optional `subTaskId`**).

**Type consistency:** **`CommentRequest.userId`** matches **`User.id`**; **`challengeId`** path aligns with **`Challenge.id`**; **`subTaskId`** optional aligns with entity.

---

## Execution handoff

**Plan path:** [`docs/superpowers/plans/2026-04-18-comment-entity-api.md`](2026-04-18-comment-entity-api.md)

**Plan complete and saved to `docs/superpowers/plans/2026-04-18-comment-entity-api.md`. Two execution options:**

**1. Subagent-Driven (recommended)** — Dispatch a fresh subagent per task; review between tasks; use **superpowers:subagent-driven-development**.

**2. Inline Execution** — Execute tasks in this session using **superpowers:executing-plans**.

**Which approach?**
