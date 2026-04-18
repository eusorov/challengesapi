# Domain Entities (User, Challenge, SubTask, Participant, Schedule, CheckIn) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add **JPA entities** and **Spring Data repositories** for `User`, `Challenge`, `SubTask`, **`Participant`**, `Schedule`, and `CheckIn`, persisting to **H2** in dev/tests, with `@DataJpaTest` coverage—**no REST controllers** in this plan (persistence slice only).

**Architecture:** Entities under `com.challenges.api.model` using **Jakarta Persistence**.  
**`Challenge`** is owned by a **`User`** (creator / **`owner`**) and has a required **start date** and an **optional end date** (`LocalDate`; **`null` endDate** means no fixed end / open-ended). When **`endDate`** is set, it must be **≥ `startDate`** (`@PrePersist` / `@PreUpdate`). **`SubTask`** belongs to a **`Challenge`** (implement **before** `Participant` so the FK exists). A **`Participant`** row links a **`User`** to a **`Challenge`** **and optionally to a `SubTask`**: if **`subTask` is `null`**, the user participates in the **whole challenge**; if **`subTask` is set**, participation is **scoped to that subtask** (must belong to the same `challenge`; validated in **`@PrePersist`**). The same user may have **both** a challenge-wide row and one or more subtask-scoped rows. **DB-level uniqueness** for “one row per scope” is **not** fully expressed with a single portable `@UniqueConstraint` when `subtask_id` is nullable—**enforce duplicates in a service layer or add partial unique indexes in a later migration**; tests in the plan check the main happy paths. Whether the **`owner`** also has `Participant` rows is a **product rule** for later.  
**`Schedule`** (cadence: daily vs selected weekdays) can be attached in a **one-to-one** way to **either** a **`Challenge`** **or** a **`SubTask`**, not both in the same row: each `Schedule` row has **at most one** of `challenge_id` / `subtask_id` set (`@PrePersist` enforces exactly one).  
**`CheckIn`** records who checked in on which **`LocalDate`**: **`User`** + **`Challenge`**, optionally **`SubTask`**. Mixed uniqueness rules stay a follow-up. **`Challenge` and `SubTask` do not reference `Schedule` until Task 6** so tasks compile in order.

**Tech Stack:** Spring Boot **4.0.5** + **Java 25**, `spring-boot-starter-data-jpa`, `com.h2database:h2` (runtime), `@DataJpaTest` for repositories.

---

## File map (after completion)

| Path | Role |
|------|------|
| [`build.gradle`](../../build.gradle) | `data-jpa`, `h2` |
| [`src/main/resources/application.properties`](../../src/main/resources/application.properties) | H2 + JPA |
| `com.challenges.api.model.*` | Entities + `ScheduleKind` (includes **`Participant`**) |
| `com.challenges.api.repo.*` | `JpaRepository` interfaces |
| `src/test/java/com/challenges/api/repo/*Test.java` | `@DataJpaTest` |

---

### Task 1: Add Spring Data JPA + H2

**Files:** Modify `build.gradle`, `src/main/resources/application.properties`

- [ ] **Step 1:** In `dependencies { }` ensure:

```groovy
	implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
	runtimeOnly 'com.h2database:h2'
```

(Keep existing `webmvc` / test entries.)

- [ ] **Step 2:** Append:

```properties
spring.datasource.url=jdbc:h2:mem:challenges;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
```

- [ ] **Step 3:** `./gradlew test --no-daemon` → **BUILD SUCCESSFUL**

- [ ] **Step 4:** `git add build.gradle src/main/resources/application.properties && git commit -m "build: add Spring Data JPA and H2"`

---

### Task 2: `User` + `UserRepository` + test

**Files:** Create `model/User.java`, `repo/UserRepository.java`, `test/.../UserRepositoryTest.java`

- [ ] **Step 1:** `User.java`

```java
package com.challenges.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 320)
	private String email;

	@Column(nullable = false, updatable = false)
	private Instant createdAt = Instant.now();

	protected User() {
	}

	public User(String email) {
		this.email = email;
	}

	public Long getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
```

- [ ] **Step 2:** `UserRepository.java`

```java
package com.challenges.api.repo;

import com.challenges.api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
```

- [ ] **Step 3:** `UserRepositoryTest.java`

```java
package com.challenges.api.repo;

import static org.assertj.core.api.Assertions.assertThat;

import com.challenges.api.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class UserRepositoryTest {

	@Autowired
	private UserRepository userRepository;

	@Test
	void savesAndFindsUserById() {
		User saved = userRepository.save(new User("pat@example.com"));
		assertThat(saved.getId()).isNotNull();
		assertThat(userRepository.findById(saved.getId())).get().extracting(User::getEmail).isEqualTo("pat@example.com");
	}
}
```

- [ ] **Step 4:** `./gradlew test --no-daemon` → success

- [ ] **Step 5:** `git add ... && git commit -m "feat: add User entity and repository"`

---

### Task 3: `Challenge` (owner `User`) — **no `Schedule` field yet**

**Files:** `Challenge.java`, `ChallengeRepository.java`, `ChallengeRepositoryTest.java`

- [ ] **Step 1:** `Challenge.java` (**omit** any `Schedule` reference)

```java
package com.challenges.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.time.LocalDate;

@Entity
@Table(name = "challenges")
public class Challenge {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_user_id", nullable = false)
	private User owner;

	@Column(nullable = false, length = 500)
	private String title;

	@Column(length = 8000)
	private String description;

	/** Inclusive start of the challenge window. */
	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	/** Inclusive end of the challenge window, or {@code null} for an open-ended challenge. When non-null, must not be before {@link #startDate}. */
	@Column(name = "end_date")
	private LocalDate endDate;

	@Column(nullable = false, updatable = false)
	private Instant createdAt = Instant.now();

	protected Challenge() {
	}

	public Challenge(User owner, String title, String description, LocalDate startDate, LocalDate endDate) {
		this.owner = owner;
		this.title = title;
		this.description = description;
		this.startDate = startDate;
		this.endDate = endDate;
	}

	@PrePersist
	@PreUpdate
	private void validateDateRange() {
		if (endDate != null && endDate.isBefore(startDate)) {
			throw new IllegalStateException("endDate must not be before startDate");
		}
	}

	public Long getId() {
		return id;
	}

	public User getOwner() {
		return owner;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public LocalDate getStartDate() {
		return startDate;
	}

	public LocalDate getEndDate() {
		return endDate;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
```

- [ ] **Step 2:** `ChallengeRepository.java` — `JpaRepository<Challenge, Long>`

- [ ] **Step 3:** `ChallengeRepositoryTest.java` — (a) **`Challenge` with `startDate` + `endDate`** (e.g. Jan 1–31, 2026); (b) **`Challenge` with `startDate` only** (`endDate` **`null`**). Flush/clear, assert persisted **`startDate` / `endDate`** as expected.

- [ ] **Step 4:** `./gradlew test` → success

- [ ] **Step 5:** commit `feat: add Challenge entity and repository`

---

### Task 4: `SubTask` + repository + test

**Files:** `SubTask.java`, `SubTaskRepository.java`, `SubTaskRepositoryTest.java`

- [ ] **Step 1:** `SubTask.java`

```java
package com.challenges.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "subtasks")
public class SubTask {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "challenge_id", nullable = false)
	private Challenge challenge;

	@Column(nullable = false, length = 500)
	private String title;

	@Column(name = "sort_index", nullable = false)
	private int sortIndex;

	protected SubTask() {
	}

	public SubTask(Challenge challenge, String title, int sortIndex) {
		this.challenge = challenge;
		this.title = title;
		this.sortIndex = sortIndex;
	}

	public Long getId() {
		return id;
	}

	public Challenge getChallenge() {
		return challenge;
	}

	public String getTitle() {
		return title;
	}

	public int getSortIndex() {
		return sortIndex;
	}
}
```

- [ ] **Step 2:** `SubTaskRepository` extends `JpaRepository<SubTask, Long>`

- [ ] **Step 3:** Test: persist `User`, `Challenge`, `SubTask`, reload `SubTask` and assert `challenge.getId()` reachable.

- [ ] **Step 4:** `./gradlew test` → commit `feat: add SubTask entity and repository`

---

### Task 5: `Participant` (challenge-wide and/or subtask-scoped) + repository + test

**Files:** `Participant.java`, `ParticipantRepository.java`, `ParticipantRepositoryTest.java`

- [ ] **Step 1:** `Participant.java` — **`User`** + **`Challenge`** always; optional **`SubTask`**. **`subTask == null`** → participant in the **whole challenge**; **`subTask` set** → participant **only for that subtask** (must be under the same challenge). **`@PrePersist` / `@PreUpdate`** validate `subTask == null || subTask.getChallenge().getId().equals(challenge.getId())`.

```java
package com.challenges.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "participants")
public class Participant {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "challenge_id", nullable = false)
	private Challenge challenge;

	/** {@code null} = membership for the entire challenge; non-null = membership scoped to this subtask only. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "subtask_id")
	private SubTask subTask;

	@Column(nullable = false, updatable = false)
	private Instant joinedAt = Instant.now();

	protected Participant() {
	}

	/** Challenge-wide participation (not tied to a single subtask). */
	public Participant(User user, Challenge challenge) {
		this.user = Objects.requireNonNull(user);
		this.challenge = Objects.requireNonNull(challenge);
		this.subTask = null;
	}

	/** Participation scoped to one subtask (must belong to {@code challenge}). */
	public Participant(User user, Challenge challenge, SubTask subTask) {
		this.user = Objects.requireNonNull(user);
		this.challenge = Objects.requireNonNull(challenge);
		this.subTask = Objects.requireNonNull(subTask);
	}

	@PrePersist
	@PreUpdate
	private void validateSubTaskBelongsToChallenge() {
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

	public User getUser() {
		return user;
	}

	public Challenge getChallenge() {
		return challenge;
	}

	public SubTask getSubTask() {
		return subTask;
	}

	public Instant getJoinedAt() {
		return joinedAt;
	}
}
```

- [ ] **Step 2:** `ParticipantRepository.java`

```java
package com.challenges.api.repo;

import com.challenges.api.model.Participant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

	List<Participant> findByChallenge_Id(Long challengeId);

	List<Participant> findByChallenge_IdAndSubTaskIsNull(Long challengeId);

	List<Participant> findBySubTask_Id(Long subTaskId);
}
```

- [ ] **Step 3:** `ParticipantRepositoryTest.java` — (a) save challenge-wide `Participant(u, c)`; (b) save subtask-scoped `Participant(u, c, st)`; (c) assert `findByChallenge_Id` returns both rows, `findByChallenge_IdAndSubTaskIsNull` returns only (a), `findBySubTask_Id` returns only (b). Uniqueness duplicates: **defer to service/migration** per Architecture.

- [ ] **Step 4:** `./gradlew test` → commit `feat: add Participant for challenge and subtask scope`

---

### Task 6: `ScheduleKind`, `Schedule`, link `Challenge` ↔ `Schedule` and `SubTask` ↔ `Schedule`

**Files:** `ScheduleKind.java`, `Schedule.java`, `ScheduleRepository.java`, modify `Challenge.java`, modify `SubTask.java`, `ScheduleRepositoryTest.java`

- [ ] **Step 1:** `ScheduleKind.java`

```java
package com.challenges.api.model;

public enum ScheduleKind {
	DAILY,
	WEEKLY_ON_SELECTED_DAYS
}
```

- [ ] **Step 2:** `Schedule.java` — **owning** side: optional FK to **`Challenge`** **or** to **`SubTask`** (exactly one required; enforced in **`@PrePersist`** / **`@PreUpdate`**).

```java
package com.challenges.api.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "schedules")
public class Schedule {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** When set, this schedule belongs to the challenge (subtask must be null). */
	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "challenge_id", unique = true)
	private Challenge challenge;

	/** When set, this schedule belongs to the subtask (challenge must be null). */
	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "subtask_id", unique = true)
	private SubTask subTask;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	private ScheduleKind kind = ScheduleKind.DAILY;

	@ElementCollection
	@Enumerated(EnumType.STRING)
	@CollectionTable(name = "schedule_weekdays", joinColumns = @JoinColumn(name = "schedule_id"))
	@Column(name = "day_of_week", nullable = false, length = 16)
	private List<DayOfWeek> weekDays = new ArrayList<>();

	protected Schedule() {
	}

	/** Schedule for a challenge (not for a subtask). */
	public static Schedule forChallenge(Challenge challenge, ScheduleKind kind, List<DayOfWeek> weekDays) {
		Schedule s = new Schedule();
		s.challenge = challenge;
		s.subTask = null;
		s.kind = kind;
		if (weekDays != null) {
			s.weekDays = new ArrayList<>(weekDays);
		}
		return s;
	}

	/** Schedule for a subtask (not stored on the challenge row). */
	public static Schedule forSubTask(SubTask subTask, ScheduleKind kind, List<DayOfWeek> weekDays) {
		Schedule s = new Schedule();
		s.subTask = subTask;
		s.challenge = null;
		s.kind = kind;
		if (weekDays != null) {
			s.weekDays = new ArrayList<>(weekDays);
		}
		return s;
	}

	@PrePersist
	@PreUpdate
	private void validateExactlyOneOwner() {
		boolean hasC = challenge != null;
		boolean hasS = subTask != null;
		if (hasC == hasS) {
			throw new IllegalStateException("Schedule must have exactly one of challenge or subTask");
		}
	}

	public Long getId() {
		return id;
	}

	public Challenge getChallenge() {
		return challenge;
	}

	public SubTask getSubTask() {
		return subTask;
	}

	public ScheduleKind getKind() {
		return kind;
	}

	public List<DayOfWeek> getWeekDays() {
		return weekDays;
	}
}
```

- [ ] **Step 3:** Add to **`Challenge.java`** (inverse side for challenge-owned schedules):

```java
import jakarta.persistence.CascadeType;
import jakarta.persistence.OneToOne;

	@OneToOne(mappedBy = "challenge", cascade = CascadeType.ALL, optional = true)
	private Schedule schedule;

	public Schedule getSchedule() {
		return schedule;
	}

	public void bindSchedule(Schedule s) {
		this.schedule = s;
	}
```

- [ ] **Step 4:** Add to **`SubTask.java`** (inverse side for subtask-owned schedules):

```java
import jakarta.persistence.CascadeType;
import jakarta.persistence.OneToOne;

	@OneToOne(mappedBy = "subTask", cascade = CascadeType.ALL, optional = true)
	private Schedule schedule;

	public Schedule getSchedule() {
		return schedule;
	}

	public void bindSchedule(Schedule s) {
		this.schedule = s;
	}
```

Persistence examples: `Schedule sch = Schedule.forChallenge(ch, ScheduleKind.DAILY, List.of()); ch.bindSchedule(sch); scheduleRepository.save(sch);` — and for a subtask: `Schedule stSch = Schedule.forSubTask(st, ScheduleKind.WEEKLY_ON_SELECTED_DAYS, List.of(DayOfWeek.MONDAY)); st.bindSchedule(stSch); scheduleRepository.save(stSch);`

- [ ] **Step 5:** `ScheduleRepository` + `@DataJpaTest`: (a) `User` → `Challenge` → `Schedule.forChallenge` + `DAILY`; (b) same setup plus `SubTask` → `Schedule.forSubTask` with `WEEKLY_ON_SELECTED_DAYS` and two weekdays. Assert both schedules persist and `getChallenge()` / `getSubTask()` on each `Schedule` match.

- [ ] **Step 6:** `./gradlew test` → commit `feat: add Schedule entity linked to Challenge or SubTask`

---

### Task 7: `CheckIn` + repository + test

**Files:** `CheckIn.java`, `CheckInRepository.java`, `CheckInRepositoryTest.java`

- [ ] **Step 1:** `CheckIn.java`

```java
package com.challenges.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "check_ins")
public class CheckIn {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "challenge_id", nullable = false)
	private Challenge challenge;

	@Column(name = "check_date", nullable = false)
	private LocalDate checkDate;

	/** When {@code null}, check-in is for the challenge as a whole; when set, check-in is for this subtask (must belong to {@link #challenge}). */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "subtask_id")
	private SubTask subTask;

	protected CheckIn() {
	}

	public CheckIn(User user, Challenge challenge, LocalDate checkDate, SubTask subTask) {
		this.user = user;
		this.challenge = challenge;
		this.checkDate = checkDate;
		this.subTask = subTask;
	}

	public Long getId() {
		return id;
	}

	public User getUser() {
		return user;
	}

	public Challenge getChallenge() {
		return challenge;
	}

	public LocalDate getCheckDate() {
		return checkDate;
	}

	public SubTask getSubTask() {
		return subTask;
	}
}
```

- [ ] **Step 2:** `CheckInRepository` + tests: (a) challenge-level check-in (`subTask == null`); (b) subtask-level check-in (`subTask` set, same `challenge`); (c) same user/day can persist **both** a challenge-level row and a subtask row if you want that product rule—otherwise add validation later (not in this entity-only plan).

- [ ] **Step 3:** `./gradlew test` → commit `feat: add CheckIn entity and repository`

---

### Task 8: Application context smoke test

- [ ] Ensure **`ChallengesApiApplication`** still starts with JPA: run `./gradlew test` (includes `ChallengesApiApplicationTests` `@SpringBootTest`). If lazy loading causes issues, leave defaults; fix only if red.

- [ ] Optional: `@SpringBootTest` + `@AutoConfigureTestDatabase` already implied for integration—no change if green.

---

### Task 9: Large domain fixture — integration test (counts + schedules)

**Goal:** One **`@SpringBootTest`** integration test that builds a **deterministic** dataset and asserts **repository `count()`**s and a sample of **schedule kinds**. Resolves the target scale as: **10 Users**, **10 Challenges** (5 creating users × 2 challenges each; if you also want **20** challenges in a variant, duplicate the loop—this task follows the **5×2 = 10** reading of “each \[creating\] user has 2 challenges”).

**Files:**
- Create: `src/test/java/com/challenges/api/integration/DomainBulkFixtureIT.java` (name is a suggestion)

**Dataset rules (exact targets):**

| Entity | Count | Rule |
|--------|------:|------|
| **`User`** | **10** | Persist `u0` … `u9` (e.g. emails `u0@fixture.test` … `u9@fixture.test`). |
| **Creators** | **5** | **`User0`–`User4`** each **own exactly 2** **`Challenge`**s → **10 challenges** total (`User5`–`User9` never own a challenge in this fixture). |
| **`Challenge`** | **10** | Owner mapping: **`User⌊i/2⌋`** owns **`Challenge` i** for **i = 0‥9** (i.e. ch0–ch1 → User0, ch2–ch3 → User1, … ch8–ch9 → User4). Each challenge has **required `startDate`**, optional **`endDate`** as you prefer (e.g. open-ended). |
| **`Participant`** | **50** (= 5 × 10) | **Challenge-wide only**: each **`Participant`** has **`subTask == null`**. For **`Challenge` index `c`**, create **5** participants for **`User` ids `(c + d) mod 10` for `d ∈ {0,1,2,3,4}`** so every challenge gets **5** distinct users (overlap across challenges is OK). |
| **`SubTask`** | **90** | **`Challenge` index 0**: **0** subtasks. **`Challenge` indices 1–9**: **10** subtasks each (`st-0` … `st-9` titles / sortIndex 0‥9). |
| **`Schedule`** | **100** | **One schedule per challenge** (10) **+ one per subtask** (90). Kind alternates for variety: **`Challenge` / `SubTask` index `k` even** → **`ScheduleKind.DAILY`** with **empty** `weekDays`; **`k` odd** → **`WEEKLY_ON_SELECTED_DAYS`** with **`weekDays = [MONDAY, TUESDAY, FRIDAY]`** (“3 times per week” = those three weekdays). Use **`k` = challenge index for challenge-level**; **`k` = challengeIndex * 100 + subIndex` for subtasks** or simply **alternate by `(challengeIndex + subIndex) % 2`** so both kinds appear. Persist with **`bindSchedule` + `scheduleRepository.save`** as in Task 6. |

**Reference types** (match Task 6 `forChallenge` / `forSubTask`):

```java
import java.time.DayOfWeek;
import java.util.List;

List<DayOfWeek> monTueFri = List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.FRIDAY);

// DAILY:
Schedule.forChallenge(ch, ScheduleKind.DAILY, List.of());
// 3×/week:
Schedule.forChallenge(ch, ScheduleKind.WEEKLY_ON_SELECTED_DAYS, monTueFri);
```

**Do not** create `CheckIn` rows in this task unless you extend the spec—the counts above are sufficient for Task 9.

- [ ] **Step 1:** Create the test class with `@SpringBootTest`, **`@Transactional`**, **`@Autowired`** **`UserRepository`**, **`ChallengeRepository`**, **`SubTaskRepository`**, **`ParticipantRepository`**, **`ScheduleRepository`**.

- [ ] **Step 2:** Implement **`@Test void bulkFixture_countsAndSchedules()`** (or similar) that:
  1. Saves **10 users**.
  2. Saves **10 challenges** with the owner mapping and dates.
  3. For each challenge **`c`**, saves **5** **`Participant`** rows `(user[(c+d)%10], challenge[c])` with **`subTask` null.
  4. For **`c == 0`**: skip subtasks; for **`c == 1‥9`**: create **10** **`SubTask`**s each, save.
  5. For **each** challenge: create **`Schedule`** (daily vs Mon/Tue/Fri per rule), **`challenge.bindSchedule(...)`**, **`scheduleRepository.save`**.
  6. For **each** subtask: create **`Schedule`** (same alternating rule), **`subTask.bindSchedule(...)`**, **`save`**.

- [ ] **Step 3:** Assert:

```java
	assertThat(userRepository.count()).isEqualTo(10);
	assertThat(challengeRepository.count()).isEqualTo(10);
	assertThat(participantRepository.count()).isEqualTo(50);
	assertThat(subTaskRepository.count()).isEqualTo(90);
	assertThat(scheduleRepository.count()).isEqualTo(100);
```

 Optionally spot-check: **`scheduleRepository.findAll()`** stream count **`filter(DAILY)`** vs **`WEEKLY`** matches your alternation (not exact ratio if you use a mixed rule—document expected split in a comment).

- [ ] **Step 4:** `./gradlew test --no-daemon` → **BUILD SUCCESSFUL**.

- [ ] **Step 5:** Commit: `test: add bulk domain fixture integration test`

---

## Self-review

| Requirement | Task |
|-------------|------|
| User | Task 2 |
| Challenge ( **startDate** required; **endDate** optional) | Task 3 |
| SubTask | Task 4 |
| Participant (challenge-wide and subtask-scoped) | Task 5 |
| Schedule | Task 6 |
| CheckIn | Task 7 |
| Challenge → many SubTasks; SubTask-level check-ins | Architecture + Task 7 `subTask` field |
| Schedule one-to-one with Challenge **or** SubTask (XOR per row) | Task 6 `Schedule` + `forChallenge` / `forSubTask` |
| Bulk fixture: 10 users, 10 challenges, 50 participants, 90 subtasks, 100 schedules | Task 9 |
| Invites / roles beyond membership | Out of scope (only `Participant` entity here) |
| No placeholders | Concrete class bodies above |

**Type consistency:** `Challenge.bindSchedule` / `Schedule` ctor must agree on `Challenge` reference; adjust names in one place if you rename.

---

## Execution handoff

**Plan path:** [`docs/superpowers/plans/2026-04-18-domain-entities-jpa.md`](2026-04-18-domain-entities-jpa.md)

1. **Subagent-driven** — `superpowers:subagent-driven-development`  
2. **Inline** — `superpowers:executing-plans`

**Which approach?**
