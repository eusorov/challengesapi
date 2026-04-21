# Challenge self-join (`POST /api/challenges/{id}/join`) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add authenticated self-join so users become `Participant` rows per [`docs/superpowers/specs/2026-04-21-challenge-join-design.md`](../specs/2026-04-21-challenge-join-design.md): owner always challenge-wide; public challenges open join; private challenges require a usable `PENDING` invite (accept + sync participant like today).

**Architecture:** `ChallengeController` exposes `POST …/join` with `UserPrincipal`. `ParticipantService.joinChallenge(challengeId, userId)` loads the challenge, applies rules, uses `ParticipantRepository` for idempotent challenge-wide membership, and delegates usable-pending resolution + accept to `InviteService`. `ChallengeService.create` inserts owner `Participant` after save. Shared invite→participant sync stays in `InviteService` (extract helper if needed to avoid duplication).

**Tech Stack:** Spring Boot 4, Spring Web MVC, Spring Data JPA, Spring Security JWT (`UserPrincipal`), JUnit 5 + MockMvc ITs, existing `JwtLoginSupport`.

---

## File map

| Path | Role |
|------|------|
| [`src/main/java/com/challenges/api/web/ChallengeController.java`](../../src/main/java/com/challenges/api/web/ChallengeController.java) | Add `POST /{id}/join` with `@AuthenticationPrincipal`. |
| [`src/main/java/com/challenges/api/service/ParticipantService.java`](../../src/main/java/com/challenges/api/service/ParticipantService.java) | Implement `joinChallenge`; inject `ChallengeRepository`, `UserRepository`, `InviteService`, `ParticipantRepository`. |
| [`src/main/java/com/challenges/api/service/InviteService.java`](../../src/main/java/com/challenges/api/service/InviteService.java) | Add `acceptOldestUsablePendingInviteForJoin`; extract `syncParticipantForAcceptedInvite(Invite)` used by `ensureParticipantForAcceptedInvite` and join. |
| [`src/main/java/com/challenges/api/service/InviteJoinAcceptResult.java`](../../src/main/java/com/challenges/api/service/InviteJoinAcceptResult.java) | `record` holding accepted invite + `participantInserted` (or nest in `InviteService` if preferred). |
| [`src/main/java/com/challenges/api/service/ChallengeService.java`](../../src/main/java/com/challenges/api/service/ChallengeService.java) | After `challenges.save(ch)` in `create`, ensure owner challenge-wide `Participant`. |
| [`src/main/java/com/challenges/api/repo/InviteRepository.java`](../../src/main/java/com/challenges/api/repo/InviteRepository.java) | Derived query: pending invites for `(invitee, challenge)` ordered by `id` asc. |
| [`src/main/java/com/challenges/api/repo/ParticipantRepository.java`](../../src/main/java/com/challenges/api/repo/ParticipantRepository.java) | Queries to load `Participant` with associations for `ParticipantResponse` after join (challenge-wide and subtask-scoped lookups). |
| [`src/main/java/com/challenges/api/service/JoinChallengeOutcome.java`](../../src/main/java/com/challenges/api/service/JoinChallengeOutcome.java) | New package-private or public `record JoinChallengeOutcome(Participant participant, boolean created)` in `service` package (or nested in `ParticipantService` if you prefer a single file). |
| [`src/test/java/com/challenges/api/web/ChallengeJoinControllerIT.java`](../../src/test/java/com/challenges/api/web/ChallengeJoinControllerIT.java) | New IT covering join matrix from spec. |
| [`src/test/java/com/challenges/api/web/ChallengeControllerIT.java`](../../src/test/java/com/challenges/api/web/ChallengeControllerIT.java) | Assert owner `Participant` exists after `POST /api/challenges` (via `GET …/participants` or repository — prefer API). |
| [`AGENTS.md`](../../AGENTS.md) | Document join endpoint and owner participant-on-create. |

---

### Task 1: Repository support

**Files:**
- Modify: `src/main/java/com/challenges/api/repo/InviteRepository.java`
- Modify: `src/main/java/com/challenges/api/repo/ParticipantRepository.java`

- [ ] **Step 1:** Add to `InviteRepository`:

```java
List<Invite> findByInvitee_IdAndChallenge_IdAndStatusOrderByIdAsc(
    Long inviteeId, Long challengeId, InviteStatus status);
```

- [ ] **Step 2:** Add to `ParticipantRepository` two fetch-join queries (mirror style of existing `findByIdInWithAssociations`):

```java
@Query(
    """
    select distinct p from Participant p
    join fetch p.user
    join fetch p.challenge
    left join fetch p.subTask
    where p.user.id = :userId and p.challenge.id = :challengeId and p.subTask is null
    """)
Optional<Participant> findChallengeWideWithAssociations(
    @Param("userId") Long userId, @Param("challengeId") Long challengeId);

@Query(
    """
    select distinct p from Participant p
    join fetch p.user
    join fetch p.challenge
    join fetch p.subTask
    where p.user.id = :userId and p.challenge.id = :challengeId and p.subTask.id = :subTaskId
    """)
Optional<Participant> findSubTaskScopedWithAssociations(
    @Param("userId") Long userId,
    @Param("challengeId") Long challengeId,
    @Param("subTaskId") Long subTaskId);
```

- [ ] **Step 3:** Run `./gradlew test` — should still pass (no behavior change yet).

- [ ] **Step 4:** Commit: `git add ... && git commit -m "chore: add repositories for challenge join lookups"`

---

### Task 2: InviteService — accept oldest usable pending invite for join

**Files:**
- Modify: `src/main/java/com/challenges/api/service/InviteService.java`

- [ ] **Step 1:** Create `src/main/java/com/challenges/api/service/InviteJoinAcceptResult.java` with `public record InviteJoinAcceptResult(Invite invite, boolean participantInserted)` (imports: `Invite` model).

- [ ] **Step 2:** Replace `ensureParticipantForAcceptedInvite` body with a call to a new private method `syncParticipantForAcceptedInvite(Invite inv)` returning `boolean` — `true` iff a new `Participant` was **inserted**, `false` if status was not `ACCEPTED` or the row already existed:

```java
private void ensureParticipantForAcceptedInvite(Invite inv) {
  syncParticipantForAcceptedInvite(inv);
}

/** @return {@code true} if a new {@link Participant} row was saved */
private boolean syncParticipantForAcceptedInvite(Invite inv) {
  if (inv.getStatus() != InviteStatus.ACCEPTED) {
    return false;
  }
  User invitee = inv.getInvitee();
  Challenge challenge = inv.getChallenge();
  SubTask st = inv.getSubTask();
  if (st == null) {
    if (!participants.existsByUser_IdAndChallenge_IdAndSubTaskIsNull(invitee.getId(), challenge.getId())) {
      return false;
    }
    participants.save(new Participant(invitee, challenge));
    return true;
  }
  if (!participants.existsByUser_IdAndChallenge_IdAndSubTask_Id(invitee.getId(), challenge.getId(), st.getId())) {
    participants.save(new Participant(invitee, challenge, st));
    return true;
  }
  return false;
}
```

- [ ] **Step 3:** Add:

```java
/**
 * Accepts the oldest usable {@link InviteStatus#PENDING} invite for (invitee, challenge),
 * syncs participant, returns the loaded invite with associations and whether a new participant row was inserted.
 */
@Transactional
public Optional<InviteJoinAcceptResult> acceptOldestUsablePendingInviteForJoin(
    @NonNull Long inviteeUserId, @NonNull Long challengeId) {
  Assert.notNull(inviteeUserId, "inviteeUserId must not be null");
  Assert.notNull(challengeId, "challengeId must not be null");
  Instant now = Instant.now();
  List<Invite> pending =
      invites.findByInvitee_IdAndChallenge_IdAndStatusOrderByIdAsc(
          inviteeUserId, challengeId, InviteStatus.PENDING);
  for (Invite inv : pending) {
    if (inv.getExpiresAt() != null && !inv.getExpiresAt().isAfter(now)) {
      continue;
    }
    inv.setStatus(InviteStatus.ACCEPTED);
    Invite saved = invites.save(inv);
    boolean inserted = syncParticipantForAcceptedInvite(saved);
    Invite withAssoc =
        invites.findByIdWithAssociations(saved.getId()).orElseThrow();
    return Optional.of(new InviteJoinAcceptResult(withAssoc, inserted));
  }
  return Optional.empty();
}
```

Use the project’s `import` style for `Instant`.

- [ ] **Step 4:** Run `./gradlew test`.

- [ ] **Step 5:** Commit: `feat(invites): accept oldest usable pending invite for join`

---

### Task 3: ParticipantService — join orchestration

**Files:**
- Create: `src/main/java/com/challenges/api/service/JoinChallengeOutcome.java`
- Modify: `src/main/java/com/challenges/api/service/ParticipantService.java`

- [ ] **Step 1:** Add record:

```java
package com.challenges.api.service;

import com.challenges.api.model.Participant;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record JoinChallengeOutcome(Participant participant, boolean created) {}
```

- [ ] **Step 2:** Extend `ParticipantService` constructor with `ChallengeRepository`, `UserRepository`, `InviteService`. Implement `joinChallenge` using `InviteJoinAcceptResult` from Task 2:

1. Load `Challenge` via `challenges.findByIdWithSubtasksAndOwner(challengeId)`; missing → `ResponseStatusException(NOT_FOUND)`.
2. Load `User` via `users.findById(userId)`; missing → `NOT_FOUND`.
3. If `challenge.getOwner().getId().equals(userId)` **or** `!challenge.isPrivate()` → `return ensureChallengeWideParticipant(user, challenge)`.
4. Else `inviteService.acceptOldestUsablePendingInviteForJoin(userId, challengeId)`:
   - If empty → `ResponseStatusException(FORBIDDEN)`.
   - Else `Invite invite = result.invite()`, `boolean created = result.participantInserted()`; load `Participant p` via `findChallengeWideWithAssociations` when `invite.getSubTask() == null`, else `findSubTaskScopedWithAssociations(userId, challengeId, invite.getSubTask().getId())`; `orElseThrow` if missing; `return new JoinChallengeOutcome(p, created)`.

- [ ] **Step 3:** Implement `ensureChallengeWideParticipant(User user, Challenge challenge)`:

```java
private JoinChallengeOutcome ensureChallengeWideParticipant(User user, Challenge challenge) {
  Long uid = user.getId();
  Long cid = challenge.getId();
  if (participants.existsByUser_IdAndChallenge_IdAndSubTaskIsNull(uid, cid)) {
    Participant p =
        participants
            .findChallengeWideWithAssociations(uid, cid)
            .orElseThrow();
    return new JoinChallengeOutcome(p, false);
  }
  Participant p = participants.save(new Participant(user, challenge));
  p =
      participants
          .findChallengeWideWithAssociations(uid, cid)
          .orElseThrow();
  return new JoinChallengeOutcome(p, true);
}
```

- [ ] **Step 4:** Add imports (`Challenge`, `User`, `Invite`, `Optional`, `InviteService`, `ChallengeRepository`, `UserRepository`, `HttpStatus` / `ResponseStatusException` as used).

- [ ] **Step 5:** Run `./gradlew test`.

- [ ] **Step 6:** Commit: `feat(participants): add joinChallenge orchestration`

---

### Task 4: ChallengeController endpoint

**Files:**
- Modify: `src/main/java/com/challenges/api/web/ChallengeController.java`

- [ ] **Step 1:** Inject `ParticipantService` (constructor injection alongside `ChallengeService`).

- [ ] **Step 2:** Add imports: `com.authspring.api.security.UserPrincipal`, `AuthenticationPrincipal`, `ParticipantResponse`, `ParticipantService`, `JoinChallengeOutcome`.

- [ ] **Step 3:** Add handler:

```java
@PostMapping({ "/{id:\\d+}/join", "/{id:\\d+}/join/" })
public ResponseEntity<ParticipantResponse> join(
    @PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
  JoinChallengeOutcome outcome =
      participantService.joinChallenge(id, principal.getId());
  ParticipantResponse body = ParticipantResponse.from(outcome.participant());
  if (outcome.created()) {
    return ResponseEntity.status(HttpStatus.CREATED).body(body);
  }
  return ResponseEntity.ok(body);
}
```

- [ ] **Step 4:** Run `./gradlew test`.

- [ ] **Step 5:** Commit: `feat(challenges): POST /api/challenges/{id}/join`

---

### Task 5: Owner participant on challenge create

**Files:**
- Modify: `src/main/java/com/challenges/api/service/ChallengeService.java`

- [ ] **Step 1:** Add `ParticipantRepository` to constructor and field.

- [ ] **Step 2:** In `create`, after `Challenge ch = ...` and `return challenges.save(ch)` — change to save then ensure participant:

```java
Challenge saved = challenges.save(ch);
Long oid = owner.getId();
Long cid = saved.getId();
if (!participants.existsByUser_IdAndChallenge_IdAndSubTaskIsNull(oid, cid)) {
  participants.save(new com.challenges.api.model.Participant(owner, saved));
}
return saved;
```

Use normal imports instead of FQCN if consistent with file style.

- [ ] **Step 3:** Run `./gradlew test`.

- [ ] **Step 4:** Commit: `feat(challenges): add owner as participant on create`

---

### Task 6: Integration tests — `ChallengeJoinControllerIT`

**Files:**
- Create: `src/test/java/com/challenges/api/web/ChallengeJoinControllerIT.java`

- [ ] **Step 1:** New class mirroring [`InviteControllerIT`](../../src/test/java/com/challenges/api/web/InviteControllerIT.java): `@SpringBootTest`, `@AutoConfigureMockMvc`, `@Transactional`, `HV` / `V1`, `JwtLoginSupport` for two users + tokens.

- [ ] **Step 2:** Test cases (each `throws Exception`):
  1. **Public challenge:** User B `POST /api/challenges/{id}/join` → **201**, body `userId` = B, `subTaskId` null. Second `POST` → **200**, same membership.
  2. **Owner idempotent:** After creating challenge as A, A joins → **200** (participant already from Task 5).
  3. **Private, no invite:** B `POST join` → **403**.
  4. **Private + pending invite:** A creates private challenge (`"private":true` in JSON like `ChallengeControllerIT`), A posts invite for B (reuse pattern from `InviteControllerIT`), B’s bearer `POST join` → **201**, `GET /api/invites/{id}` or list shows **ACCEPTED**; `GET /api/challenges/{id}/participants` includes B.
  5. **Expired invite:** Create invite with `expiresAt` in the past (ISO-8601 string in JSON); B `POST join` → **403**.
  6. **Two pending invites:** Create two `POST /api/invites` for same B and challenge (if API allows); smallest invite id should be accepted — assert one **ACCEPTED** and join succeeds; optionally assert other still **PENDING**.

- [ ] **Step 3:** Run `./gradlew test --tests 'com.challenges.api.web.ChallengeJoinControllerIT'`

- [ ] **Step 4:** Run full `./gradlew test`

- [ ] **Step 5:** Commit: `test: add ChallengeJoinControllerIT`

---

### Task 7: Extend `ChallengeControllerIT` for owner participant

**Files:**
- Modify: `src/test/java/com/challenges/api/web/ChallengeControllerIT.java`

- [ ] **Step 1:** In `createChallengeThenGetById`, after creating challenge, call:

```java
mockMvc.perform(get("/api/challenges/" + challengeId + "/participants")
        .header(HV, V1)
        .header(HttpHeaders.AUTHORIZATION, bearerAuth))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.content[0].userId").value(owner1.getId().intValue()))
    .andExpect(jsonPath("$.content[0].subTaskId").doesNotExist());
```

Adjust `subTaskId` expectation to match how `ParticipantResponse` serializes null (project may use `null` in JSON — use `jsonPath("$.content[0].subTaskId").value((Object) null)` or omit if field absent).

- [ ] **Step 2:** Run `./gradlew test --tests 'com.challenges.api.web.ChallengeControllerIT'`

- [ ] **Step 3:** Commit: `test: assert owner participant after challenge create`

---

### Task 8: AGENTS.md

**Files:**
- Modify: `AGENTS.md`

- [ ] **Step 1:** Under REST API / product rules, add bullets:
  - `POST /api/challenges/{id}/join` — authenticated user joins per [`docs/superpowers/specs/2026-04-21-challenge-join-design.md`](../../docs/superpowers/specs/2026-04-21-challenge-join-design.md) (public vs private + pending invite); returns `ParticipantResponse`; **201** first time challenge-wide insert, **200** if already challenge-wide.
  - On **`POST /api/challenges`** (create), owner gets a **challenge-wide** `Participant` row automatically.

- [ ] **Step 2:** Commit: `docs: document challenge join and owner participant`

---

## Plan self-review

**Spec coverage:**

| Spec section | Task |
|--------------|------|
| POST join + JWT + ParticipantResponse | Task 4 |
| Owner / public / private + pending + expires | Tasks 2–3, 6 |
| 404 missing challenge | Task 3 (`findByIdWithSubtasksAndOwner`) |
| 403 private non-owner no invite | Task 3 |
| 201 vs 200 idempotent challenge-wide | Tasks 3–4, 6 |
| Owner participant on create | Task 5 |
| Private join accepts invite + subtask scope | Tasks 2–3, 6 |
| AGENTS.md | Task 8 |

**Known gap (spec strictness):** After a private join succeeds, a **second** `POST join` without a new `PENDING` invite yields **403** per spec. The plan does not add “already participant” bypass for private challenges; if product wants idempotent **200**, add a Task 9: before throwing **403**, if user has any `Participant` for that challenge, return **200** with the preferred row (challenge-wide over subtask). Not required for MVP merge if spec is interpreted literally.

**Placeholder scan:** No TBD/TODO left in executable steps; `participantInserted` comes from `InviteJoinAcceptResult` (Task 2).

**Type consistency:** `JoinChallengeOutcome`, `Participant`, `Invite`, `UserPrincipal.getId()` align with existing code.

---

**Plan complete and saved to `docs/superpowers/plans/2026-04-21-challenge-join.md`. Two execution options:**

1. **Subagent-driven (recommended)** — dispatch a fresh subagent per task, review between tasks.  
2. **Inline execution** — run tasks in this session with checkpoints.

Which approach do you want?
