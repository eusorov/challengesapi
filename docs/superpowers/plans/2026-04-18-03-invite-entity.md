# Invite Entity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a JPA **`Invite`** entity and **`InviteRepository`** so one **`User`** can invite another **`User`** to a **`Challenge`**, with optional **`SubTask`** scope—mirroring **`Participant`** semantics—plus **`@DataJpaTest`** coverage. **No REST controllers, no email/tokens, and no automatic creation of `Participant` on accept** (those belong to follow-up work).

**Architecture:** An **`Invite`** row represents a directed invitation: **`inviter`** sent an invite to **`invitee`** for a **`challenge`**. Optional **`subTask`** means “invite scoped to this subtask”; **`null`** means challenge-wide, consistent with **`Participant`**. **`InviteStatus`** tracks lifecycle (**`PENDING`**, **`ACCEPTED`**, **`DECLINED`**, **`CANCELLED`**). Validators ensure **`inviter` ≠ `invitee`**, and if **`subTask`** is set it belongs to **`challenge`**. Optional **`expiresAt`** may be set; callers enforce expiry in a later service layer unless you add **`@PrePersist`** / **`@PreUpdate`** checks in a dedicated step below.

**Tech Stack:** Spring Boot **4.0.5**, Java **25**, Jakarta Persistence, Spring Data JPA, H2 (existing), tests use **`spring-boot-starter-data-jpa-test`** with imports **`org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`** and **`org.springframework.boot.jpa.test.autoconfigure.TestEntityManager`** (Spring Boot **4** packages—not the old `org.springframework.boot.test.autoconfigure.orm.jpa` paths).

---

## File map (after completion)

| Path | Role |
|------|------|
| [`build.gradle`](../../build.gradle) | No change unless a new test dep is missing (should already include `spring-boot-starter-data-jpa-test`). |
| [`src/main/java/com/challenges/api/model/InviteStatus.java`](../../src/main/java/com/challenges/api/model/InviteStatus.java) | Enum for invite lifecycle. |
| [`src/main/java/com/challenges/api/model/Invite.java`](../../src/main/java/com/challenges/api/model/Invite.java) | Entity. |
| [`src/main/java/com/challenges/api/repo/InviteRepository.java`](../../src/main/java/com/challenges/api/repo/InviteRepository.java) | `JpaRepository`. |
| [`src/test/java/com/challenges/api/repo/InviteRepositoryTest.java`](../../src/test/java/com/challenges/api/repo/InviteRepositoryTest.java) | `@DataJpaTest`. |
| [`AGENTS.md`](../../AGENTS.md) | Add **`Invite`** row to Core concepts table. |

---

### Task 1: `InviteStatus` enum

**Files:**
- Create: `src/main/java/com/challenges/api/model/InviteStatus.java`

- [ ] **Step 1:** Create enum with four values.

```java
package com.challenges.api.model;

public enum InviteStatus {
	PENDING,
	ACCEPTED,
	DECLINED,
	CANCELLED
}
```

- [ ] **Step 2:** Commit

```bash
git add src/main/java/com/challenges/api/model/InviteStatus.java
git commit -m "feat: add InviteStatus enum"
```

---

### Task 2: `Invite` entity

**Files:**
- Create: `src/main/java/com/challenges/api/model/Invite.java`

- [ ] **Step 1:** Add entity with **`inviter`**, **`invitee`** (`User`, both required), **`challenge`** (`Challenge`, required), optional **`subTask`** (`SubTask`), **`status`** (default **`PENDING`**), **`createdAt`** (immutable), optional **`expiresAt`**. Private no-arg constructor for JPA. `@PrePersist` + `@PreUpdate`: (1) if **`subTask != null`**, require **`subTask.getChallenge().getId()`** and **`challenge.getId()`** both non-null and equal; if either id is null (unsaved graph), skip the id check so first persist in one transaction still works—mirror [`Participant`](../../src/main/java/com/challenges/api/model/Participant.java) `validateSubTaskBelongsToChallenge` pattern. (2) If **`inviter.getId()`** and **`invitee.getId()`** are both non-null and equal, throw **`IllegalStateException("inviter and invitee must differ")`**.

```java
package com.challenges.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "invites")
public class Invite {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "inviter_id", nullable = false)
	private User inviter;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "invitee_id", nullable = false)
	private User invitee;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "challenge_id", nullable = false)
	private Challenge challenge;

	/** {@code null} = invite to whole challenge; non-null = invite scoped to this subtask. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "subtask_id")
	private SubTask subTask;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 24)
	private InviteStatus status = InviteStatus.PENDING;

	@Column(nullable = false, updatable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "expires_at")
	private Instant expiresAt;

	protected Invite() {
	}

	public Invite(User inviter, User invitee, Challenge challenge, SubTask subTask) {
		this.inviter = Objects.requireNonNull(inviter);
		this.invitee = Objects.requireNonNull(invitee);
		this.challenge = Objects.requireNonNull(challenge);
		this.subTask = subTask;
	}

	/** Challenge-wide invite (no subtask scope). */
	public Invite(User inviter, User invitee, Challenge challenge) {
		this(inviter, invitee, challenge, null);
	}

	@PrePersist
	@PreUpdate
	private void validate() {
		if (inviter.getId() != null && invitee.getId() != null && inviter.getId().equals(invitee.getId())) {
			throw new IllegalStateException("inviter and invitee must differ");
		}
		if (subTask == null) {
			return;
		}
		if (subTask.getChallenge().getId() == null || challenge.getId() == null) {
			return;
		}
		if (!subTask.getChallenge().getId().equals(challenge.getId())) {
			throw new IllegalStateException("subTask must belong to the same challenge");
		}
	}

	public Long getId() {
		return id;
	}

	public User getInviter() {
		return inviter;
	}

	public User getInvitee() {
		return invitee;
	}

	public Challenge getChallenge() {
		return challenge;
	}

	public SubTask getSubTask() {
		return subTask;
	}

	public InviteStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public void setStatus(InviteStatus status) {
		this.status = Objects.requireNonNull(status);
	}

	public void setExpiresAt(Instant expiresAt) {
		this.expiresAt = expiresAt;
	}
}
```

- [ ] **Step 2:** `./gradlew test --no-daemon` → **BUILD SUCCESSFUL** (compiles with existing entities).

- [ ] **Step 3:** Commit

```bash
git add src/main/java/com/challenges/api/model/Invite.java
git commit -m "feat: add Invite entity"
```

---

### Task 3: `InviteRepository` + `@DataJpaTest`

**Files:**
- Create: `src/main/java/com/challenges/api/repo/InviteRepository.java`
- Create: `src/test/java/com/challenges/api/repo/InviteRepositoryTest.java`

- [ ] **Step 1:** Repository

```java
package com.challenges.api.repo;

import com.challenges.api.model.Invite;
import com.challenges.api.model.InviteStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InviteRepository extends JpaRepository<Invite, Long> {

	List<Invite> findByInvitee_IdAndStatus(Long inviteeUserId, InviteStatus status);

	List<Invite> findByChallenge_Id(Long challengeId);
}
```

- [ ] **Step 2:** Test class — persist **`User`** (inviter + invitee), **`Challenge`**, flush; build **`Invite`** challenge-wide; save and reload; assert **`inviter` / `invitee` / `challenge`** ids and **`PENDING`**. Second test: add **`SubTask`**, **`Invite(inviter, invitee, challenge, subTask)`**, save, **`findByInvitee_IdAndStatus(invitee.getId(), PENDING)`** returns one row.

```java
package com.challenges.api.repo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.challenges.api.model.Challenge;
import com.challenges.api.model.Invite;
import com.challenges.api.model.InviteStatus;
import com.challenges.api.model.SubTask;
import com.challenges.api.model.User;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class InviteRepositoryTest {

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private InviteRepository inviteRepository;

	@Test
	void savesChallengeWideInvite() {
		User inviter = entityManager.persistAndFlush(new User("inviter@example.com"));
		User invitee = entityManager.persistAndFlush(new User("invitee@example.com"));
		Challenge ch = entityManager.persistAndFlush(new Challenge(inviter, "Group run", null, LocalDate.of(2026, 7, 1), null));

		Invite inv = inviteRepository.save(new Invite(inviter, invitee, ch));
		entityManager.flush();
		entityManager.clear();

		Invite loaded = inviteRepository.findById(inv.getId()).orElseThrow();
		assertThat(loaded.getInviter().getId()).isEqualTo(inviter.getId());
		assertThat(loaded.getInvitee().getId()).isEqualTo(invitee.getId());
		assertThat(loaded.getChallenge().getId()).isEqualTo(ch.getId());
		assertThat(loaded.getSubTask()).isNull();
		assertThat(loaded.getStatus()).isEqualTo(InviteStatus.PENDING);
	}

	@Test
	void findsPendingByInvitee() {
		User inviter = entityManager.persistAndFlush(new User("a@example.com"));
		User invitee = entityManager.persistAndFlush(new User("b@example.com"));
		Challenge ch = entityManager.persistAndFlush(new Challenge(inviter, "C", null, LocalDate.of(2026, 7, 2), null));
		SubTask st = entityManager.persistAndFlush(new SubTask(ch, "mile", 0));
		inviteRepository.save(new Invite(inviter, invitee, ch, st));
		entityManager.flush();
		entityManager.clear();

		assertThat(inviteRepository.findByInvitee_IdAndStatus(invitee.getId(), InviteStatus.PENDING)).hasSize(1);
	}

	@Test
	void rejectsSelfInviteWhenIdsPresent() {
		User u = entityManager.persistAndFlush(new User("solo@example.com"));
		Challenge ch = entityManager.persistAndFlush(new Challenge(u, "Solo", null, LocalDate.of(2026, 7, 3), null));

		assertThatThrownBy(() -> entityManager.persistAndFlush(new Invite(u, u, ch)))
				.hasRootCauseInstanceOf(IllegalStateException.class);
	}
}
```

- [ ] **Step 3:** `./gradlew test --no-daemon` → **BUILD SUCCESSFUL**

- [ ] **Step 4:** Commit

```bash
git add src/main/java/com/challenges/api/repo/InviteRepository.java src/test/java/com/challenges/api/repo/InviteRepositoryTest.java
git commit -m "feat: add Invite repository and tests"
```

---

### Task 4: Document **`Invite`** in `AGENTS.md`

**Files:**
- Modify: `AGENTS.md`

- [ ] **Step 1:** In **Core concepts**, add a row after **Participant** (or in table order that reads well):

| **Invite** | One user (**inviter**) invites another (**invitee**) to a **challenge**, optionally scoped to a **subtask** (`subTask` unset = whole challenge). Status (**pending** / **accepted** / …) is stored on the invite; accepting may create a **Participant** in a later API/service layer. |

- [ ] **Step 2:** Adjust the **Participant** row if it still says “Invites … modeled later”—change to note that **`Invite`** exists for invitations; roles beyond membership remain future work if still true.

- [ ] **Step 2b:** In **Product rules for agents**, extend the vocabulary parenthetical to include **`Invite`** alongside **`Challenge`**, **`CheckIn`**, **`Participant`**, **`Schedule`**.

- [ ] **Step 3:** Commit

```bash
git add AGENTS.md
git commit -m "docs: document Invite in AGENTS.md"
```

---

## Self-review

| Requirement | Task |
|-------------|------|
| Users send invites to other users (`inviter` → `invitee`) | Task 2 |
| Tied to **Challenge** (and optional **SubTask** scope) | Task 2 |
| Status lifecycle field | Task 1 + Task 2 |
| Persist + query tests | Task 3 |
| Project vocabulary in docs | Task 4 |
| REST / email tokens / `Participant` creation on accept | Out of scope (explicit) |

**Type consistency:** **`Invite`** constructors and **`Participant`** subtask/challenge rules use the same null-id skip pattern for unsaved graphs.

---

## Execution handoff

**Plan path:** [`docs/superpowers/plans/2026-04-18-invite-entity.md`](2026-04-18-invite-entity.md)

**Plan complete and saved to `docs/superpowers/plans/2026-04-18-invite-entity.md`. Two execution options:**

1. **Subagent-Driven (recommended)** — Dispatch a fresh subagent per task; review between tasks; use **superpowers:subagent-driven-development**.
2. **Inline execution** — Run tasks in this session with **superpowers:executing-plans**.

**Which approach?**

---

## Note on deprecated Cursor command

The Cursor **`/write-plan`** command is **deprecated** and may be removed in a future release. Prefer invoking the **superpowers:writing-plans** skill (this document) for new plans.
