# Participant on invite accept — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When an invitee **accepts** a challenge invite (`InviteStatus.ACCEPTED`), persist a **`Participant`** row so they appear on **`GET /api/challenges/{challengeId}/participants`** (matching invite scope: challenge-wide vs subtask-scoped).

**Architecture:** Keep **`Participant`** creation inside **`InviteService`** (same transaction as invite update/create). Use **`ParticipantRepository`** **`existsBy…`** checks so accepting twice or repeating **`PUT`** with **`ACCEPTED`** does not insert duplicate rows. Map invite scope to **`Participant`** constructors: **`subTask == null`** → **`new Participant(invitee, challenge)`**; **non-null subtask** → **`new Participant(invitee, challenge, subTask)`**. Do **not** remove **`Participant`** rows when invite moves to **DECLINED** / **CANCELLED** in this phase (**YAGNI**).

**Tech Stack:** Spring Boot **4.0.x**, Spring Data JPA, existing models **`Invite`**, **`Participant`**, **`InviteStatus`**, **`GlobalExceptionHandler`** (no new exception types for control flow).

---

## File map

| Path | Role |
|------|------|
| [`src/main/java/com/challenges/api/repo/ParticipantRepository.java`](../../src/main/java/com/challenges/api/repo/ParticipantRepository.java) | Add **`existsBy…`** query methods for idempotent membership checks. |
| [`src/main/java/com/challenges/api/service/InviteService.java`](../../src/main/java/com/challenges/api/service/InviteService.java) | Inject **`ParticipantRepository`**; after successful **`create`** / **`update`**, sync **`Participant`** when status is **`ACCEPTED`**. |
| [`src/test/java/com/challenges/api/web/InviteControllerIT.java`](../../src/test/java/com/challenges/api/web/InviteControllerIT.java) | Assert **`GET …/participants`** includes invitee after **`PUT`** accept. |
| [`src/test/java/com/challenges/api/integration/ChallengeDomainWorkflowIT.java`](../../src/test/java/com/challenges/api/integration/ChallengeDomainWorkflowIT.java) | Remove manual **`ParticipantRepository.save`**; assert participants via API only. |
| [`AGENTS.md`](../../AGENTS.md) | Short note: accepting an invite creates **`Participant`** membership (optional doc task). |

---

## Domain rules (normative)

| Invite scope | `Invite.subTask` | `Participant` row |
|----------------|-------------------|-------------------|
| Whole challenge | `null` | **`Participant(invitee, challenge)`** — `subTask` **null** on participant |
| Subtask-only | non-null | **`Participant(invitee, challenge, subTask)`** |

- **Idempotency:** Before **`save(new Participant(…))`**, use **`existsBy…`** for the same **(userId, challengeId, subTask scope)** so re **`PUT`** **`ACCEPTED`** or duplicate logic does not violate DB constraints or create duplicates.
- **Triggers:** **`InviteService.create`** when saved invite ends with status **`ACCEPTED`** (e.g. optional status on **`InviteRequest`**). **`InviteService.update`** when the saved invite has status **`ACCEPTED`** (including transition **PENDING → ACCEPTED**).
- **Out of scope:** Revoking **`Participant`** on decline/cancel; notifying users; authorization (**caller identity** remains out of scope as today).

---

### Task 1: `ParticipantRepository` existence queries

**Files:**
- Modify: [`src/main/java/com/challenges/api/repo/ParticipantRepository.java`](../../src/main/java/com/challenges/api/repo/ParticipantRepository.java)

- [ ] **Step 1:** Add Spring Data JPA methods (names must match property paths on **`Participant`**):

```java
boolean existsByUser_IdAndChallenge_IdAndSubTaskIsNull(Long userId, Long challengeId);

boolean existsByUser_IdAndChallenge_IdAndSubTask_Id(Long userId, Long challengeId, Long subTaskId);
```

- [ ] **Step 2:** Run compilation:

```bash
./gradlew compileJava --no-daemon
```

Expected: **BUILD SUCCESSFUL**

- [ ] **Step 3:** Commit

```bash
git add src/main/java/com/challenges/api/repo/ParticipantRepository.java
git commit -m "feat: add ParticipantRepository exists queries for invite accept"
```

---

### Task 2: `InviteService` — sync `Participant` on `ACCEPTED`

**Files:**
- Modify: [`src/main/java/com/challenges/api/service/InviteService.java`](../../src/main/java/com/challenges/api/service/InviteService.java)

- [ ] **Step 1:** Add **`ParticipantRepository`** constructor dependency and field **`private final ParticipantRepository participants`**.

- [ ] **Step 2:** Add **`private void ensureParticipantForAcceptedInvite(Invite inv)`** implementing:

```java
private void ensureParticipantForAcceptedInvite(Invite inv) {
	if (inv.getStatus() != InviteStatus.ACCEPTED) {
		return;
	}
	User invitee = inv.getInvitee();
	Challenge challenge = inv.getChallenge();
	SubTask st = inv.getSubTask();
	if (st == null) {
		if (!participants.existsByUser_IdAndChallenge_IdAndSubTaskIsNull(invitee.getId(), challenge.getId())) {
			participants.save(new Participant(invitee, challenge));
		}
	} else {
		if (!participants.existsByUser_IdAndChallenge_IdAndSubTask_Id(invitee.getId(), challenge.getId(), st.getId())) {
			participants.save(new Participant(invitee, challenge, st));
		}
	}
}
```

Add imports: **`com.challenges.api.model.Challenge`**, **`InviteStatus`**, **`Participant`**, **`SubTask`**, **`User`** (only those not already present).

- [ ] **Step 3:** At the end of **`create`**, after **`invites.save(inv)`**, if the method returns the saved invite (variable **`inv`** or returned entity), call **`ensureParticipantForAcceptedInvite(saved)`** on the **persisted** instance (same as returned **`Optional`** content).

- [ ] **Step 4:** In **`update`**, inside the **`map` lambda**, after **`inv.setStatus` / `setExpiresAt`**, assign **`Invite saved = invites.save(inv)`**, call **`ensureParticipantForAcceptedInvite(saved)`**, then **`return saved`** (replace the prior single **`return invites.save(inv)`**).

- [ ] **Step 5:** Run:

```bash
./gradlew compileJava --no-daemon
```

Expected: **BUILD SUCCESSFUL**

- [ ] **Step 6:** Commit

```bash
git add src/main/java/com/challenges/api/service/InviteService.java
git commit -m "feat: create Participant when invite is accepted"
```

---

### Task 3: Integration test — invite accept lists participant

**Files:**
- Modify: [`src/test/java/com/challenges/api/web/InviteControllerIT.java`](../../src/test/java/com/challenges/api/web/InviteControllerIT.java)

- [ ] **Step 1:** After **`PUT`** accept (existing test **`createThenUpdateStatus`**), add **`mockMvc.perform(get("/api/challenges/" + challenge.getId() + "/participants").header("API-Version", "1"))`** with expectations:

```java
mockMvc.perform(get("/api/challenges/" + challenge.getId() + "/participants").header(HV, V1))
		.andExpect(status().isOk())
		.andExpect(jsonPath("$[0].userId").value(invitee.getId().intValue()))
		.andExpect(jsonPath("$[0].challengeId").value(challenge.getId().intValue()));
```

(Adjust index **`$[0]`** if multiple participants; for a fresh DB with only this invite, one row is enough.)

- [ ] **Step 2:** Run:

```bash
./gradlew test --tests com.challenges.api.web.InviteControllerIT --no-daemon
```

Expected: **BUILD SUCCESSFUL**, test **PASSED**

- [ ] **Step 3:** Commit

```bash
git add src/test/java/com/challenges/api/web/InviteControllerIT.java
git commit -m "test: participant appears after invite accept"
```

---

### Task 4: Workflow IT — drop manual `Participant` seed

**Files:**
- Modify: [`src/test/java/com/challenges/api/integration/ChallengeDomainWorkflowIT.java`](../../src/test/java/com/challenges/api/integration/ChallengeDomainWorkflowIT.java)

- [ ] **Step 1:** Remove **`ParticipantRepository`** field and **`participants.save(...)`** block after invite **`PUT`**.

- [ ] **Step 2:** Remove unused imports **`Challenge`**, **`Participant`**, **`User`**, **`ParticipantRepository`**, **`UserRepository`**, **`ChallengeRepository`** if no longer needed (keep only what the test still uses for assertions — if **none**, remove **`@Autowired`** repository fields entirely).

- [ ] **Step 3:** Update class Javadoc: state that **`Participant`** is created **by the API** when the invite is accepted.

- [ ] **Step 4:** Run:

```bash
./gradlew test --tests com.challenges.api.integration.ChallengeDomainWorkflowIT --no-daemon
```

Expected: **PASSED**

- [ ] **Step 5:** Commit

```bash
git add src/test/java/com/challenges/api/integration/ChallengeDomainWorkflowIT.java
git commit -m "test: workflow relies on invite accept for Participant"
```

---

### Task 5: Optional — subtask-scoped invite

**Files:**
- Create or extend: a test class under [`src/test/java/com/challenges/api/`](../../src/test/java/com/challenges/api/)

- [ ] **Step 1:** Add **`@SpringBootTest` + `MockMvc`** test (or extend **`InviteControllerIT`** with a second **`@Test`**): create challenge + subtask via repos or API, **`POST /api/invites`** with **`subTaskId`** set, **`PUT` `ACCEPTED`**, then **`GET /api/challenges/{id}/participants`** and assert **`$[0].subTaskId`** matches subtask (or use **`GET`** with known IDs). Verifies **`Participant(invitee, challenge, subTask)`** path.

- [ ] **Step 2:** `./gradlew test --no-daemon` → **SUCCESS**

- [ ] **Step 3:** Commit **`test: accept subtask-scoped invite creates scoped Participant`**

---

### Task 6: Documentation

**Files:**
- Modify: [`AGENTS.md`](../../AGENTS.md)

- [ ] **Step 1:** Under **REST API** / domain bullets, add one line: **Accepting an invite** (**`InviteStatus.ACCEPTED`**) **creates** a **`Participant`** row (challenge-wide or subtask-scoped per invite).

- [ ] **Step 2:** Commit **`docs: note Participant on invite accept`**

---

### Task 7: Full suite

- [ ] **Step 1:** Run:

```bash
./gradlew test --no-daemon
```

Expected: **BUILD SUCCESSFUL**, all tests **GREEN**

- [ ] **Step 2:** Commit only if any file left unstaged from formatting — otherwise no empty commit.

---

## Self-review

| Requirement | Task |
|-------------|------|
| Participant created on **ACCEPTED** | **2** |
| Challenge-wide vs subtask scope | **2** (`ensureParticipantForAcceptedInvite`) |
| Idempotent accepts | **1** + **2** (`existsBy…`) |
| Integration coverage | **3**, **4** |
| Optional subtask E2E | **5** |
| Docs | **6** |

**Placeholder scan:** No TBD steps; declining/removing membership explicitly out of scope in **Architecture**.

**Type consistency:** **`user.id`**, **`challenge.id`**, **`subTask.id`** line up with **`ParticipantRepository`** method parameter names and **`Participant`** entity FKs.

---

## Execution handoff

**Plan path:** [`docs/superpowers/plans/2026-04-18-participant-on-invite-accept.md`](2026-04-18-participant-on-invite-accept.md)

**Plan complete and saved to `docs/superpowers/plans/2026-04-18-participant-on-invite-accept.md`. Two execution options:**

**1. Subagent-Driven (recommended)** — Dispatch a fresh subagent per task; review between tasks; use **superpowers:subagent-driven-development**.

**2. Inline Execution** — Execute tasks in this session using **superpowers:executing-plans**.

**Which approach?**
