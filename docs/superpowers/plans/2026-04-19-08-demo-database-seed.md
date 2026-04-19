# Demo database seed (users, challenges, schedules, participants, check-ins, invites) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Load a **repeatable demo dataset** into the database: **10 users**, **10 challenges** (each with a **description**), **ownership spread** so some users own **one** challenge and some **two**, **1–5 participants** per challenge, **distinct challenge-level schedules** (`DAILY` vs `WEEKLY_ON_SELECTED_DAYS` with varied weekdays), **0–3 subtasks** per challenge with **their own** schedules where subtasks exist, **check-ins** both **challenge-wide** (`subtask_id` null) and **subtask-scoped**, and **invites** in **PENDING**, **ACCEPTED**, and **DECLINED** (inviter ≠ invitee, participants may appear as invitees on other challenges).

**Architecture:** Use a Spring **`ApplicationRunner`** in **`com.challenges.api.dev`** gated by **`@Profile("demo-seed")`** so **production and normal `bootRun` never load demo data**. Idempotency: if **`UserRepository.findByEmail("seed01@demo.local")`** is present, **exit without inserting** (safe to restart). Build graphs with **existing JPA entities** and repositories (same patterns as [`DomainBulkFixtureIT`](../../src/test/java/com/challenges/api/integration/DomainBulkFixtureIT.java): save **Challenge**, **`Schedule.forChallenge` + `bindSchedule` + `scheduleRepository.save`**, **SubTask** + **`Schedule.forSubTask`**, **Participant**, **CheckIn**, **Invite** with **`setStatus`** where needed). Password for all seed users: **BCrypt hash of `"password"`** — reuse **`User.TEST_PASSWORD_HASH`** constant so local login matches integration tests.

**Tech stack:** Java 25, Spring Boot 4.0.5, Spring Data JPA, PostgreSQL, JUnit 5, profiles.

---

## File map

| Path | Responsibility |
|------|----------------|
| [`src/main/java/com/challenges/api/repo/UserRepository.java`](../../src/main/java/com/challenges/api/repo/UserRepository.java) | Add **`boolean existsByEmail(String email)`** for idempotency guard. |
| `src/main/java/com/challenges/api/dev/DemoDataLoader.java` | **`@Component` `@Profile("demo-seed")` `ApplicationRunner`**: creates full dataset (see Task 2). |
| [`README.md`](../../README.md) | Document **`demo-seed`** profile and **never** enable in production. |
| `src/test/java/com/challenges/api/dev/DemoDataLoaderIT.java` | **`@SpringBootTest` `@ActiveProfiles({"test", "demo-seed"})`**: asserts seed users and challenge count (only this class activates the profile; other tests stay `test` only). |

**Domain rules (from codebase):** `ScheduleKind` is only **`DAILY`** and **`WEEKLY_ON_SELECTED_DAYS`** ([`ScheduleKind.java`](../../src/main/java/com/challenges/api/model/ScheduleKind.java)). `DayOfWeek` persists as **`MONDAY`**, **`TUESDAY`**, … ([`Schedule.java`](../../src/main/java/com/challenges/api/model/Schedule.java)). **`InviteStatus`**: **`PENDING`**, **`ACCEPTED`**, **`DECLINED`**, **`CANCELLED`**.

---

### Task 1: `UserRepository.existsByEmail`

**Files:**
- Modify: [`src/main/java/com/challenges/api/repo/UserRepository.java`](../../src/main/java/com/challenges/api/repo/UserRepository.java)

- [ ] **Step 1: Add derived query**

```java
boolean existsByEmail(String email);
```

(Place after `findByEmail`; Spring Data JPA implements it.)

- [ ] **Step 2: `./gradlew compileJava --no-daemon`**

Expected: **BUILD SUCCESSFUL**

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/challenges/api/repo/UserRepository.java
git commit -m "chore(repo): existsByEmail for demo seed idempotency"
```

---

### Task 2: `DemoDataLoader` (full dataset)

**Files:**
- Create: `src/main/java/com/challenges/api/dev/DemoDataLoader.java`

- [ ] **Step 1: Create class** — copy the following **entire file** (adjust imports if your IDE orders them differently; keep logic identical).

```java
package com.challenges.api.dev;

import com.challenges.api.model.Challenge;
import com.challenges.api.model.CheckIn;
import com.challenges.api.model.Invite;
import com.challenges.api.model.InviteStatus;
import com.challenges.api.model.Participant;
import com.challenges.api.model.Schedule;
import com.challenges.api.model.ScheduleKind;
import com.challenges.api.model.SubTask;
import com.challenges.api.model.User;
import com.challenges.api.repo.ChallengeRepository;
import com.challenges.api.repo.CheckInRepository;
import com.challenges.api.repo.InviteRepository;
import com.challenges.api.repo.ParticipantRepository;
import com.challenges.api.repo.ScheduleRepository;
import com.challenges.api.repo.SubTaskRepository;
import com.challenges.api.repo.UserRepository;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("demo-seed")
@Order
public class DemoDataLoader implements ApplicationRunner {

	private static final String SEED_EMAIL_1 = "seed01@demo.local";
	private static final List<DayOfWeek> MON_WED_FRI =
			List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
	private static final List<DayOfWeek> TUE_THU = List.of(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY);
	private static final List<DayOfWeek> WEEKDAYS = List.of(
			DayOfWeek.MONDAY,
			DayOfWeek.TUESDAY,
			DayOfWeek.WEDNESDAY,
			DayOfWeek.THURSDAY,
			DayOfWeek.FRIDAY);
	private static final List<DayOfWeek> SAT_SUN = List.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

	private final UserRepository users;
	private final ChallengeRepository challenges;
	private final SubTaskRepository subTasks;
	private final ScheduleRepository schedules;
	private final ParticipantRepository participants;
	private final CheckInRepository checkIns;
	private final InviteRepository invites;

	public DemoDataLoader(
			UserRepository users,
			ChallengeRepository challenges,
			SubTaskRepository subTasks,
			ScheduleRepository schedules,
			ParticipantRepository participants,
			CheckInRepository checkIns,
			InviteRepository invites) {
		this.users = users;
		this.challenges = challenges;
		this.subTasks = subTasks;
		this.schedules = schedules;
		this.participants = participants;
		this.checkIns = checkIns;
		this.invites = invites;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (users.existsByEmail(SEED_EMAIL_1)) {
			return;
		}

		List<User> seedUsers = new ArrayList<>();
		for (int i = 1; i <= 10; i++) {
			String n = i < 10 ? "0" + i : "10";
			seedUsers.add(users.save(new User(
					"Seed User " + n,
					"seed" + n + "@demo.local",
					User.TEST_PASSWORD_HASH,
					User.DEFAULT_ROLE)));
		}

		String[] descriptions = {
			"Morning runs before work — build the habit slowly.",
			"Read 20 pages of non-fiction every day.",
			"No sugar: whole-team wellness challenge for April.",
			"Meditation streak: 10 minutes minimum.",
			"Hydration: 8 glasses — track with the group.",
			"Strength training 3× per week at the gym.",
			"Learn Spanish vocabulary — 15 new words daily.",
			"Sleep before 11pm — recovery focus.",
			"Walk 8k steps — office group challenge.",
			"Journal one page each evening for mindfulness."
		};

		LocalDate base = LocalDate.of(2026, 4, 1);
		int[] ownerIndex = {0, 0, 1, 2, 2, 3, 4, 5, 6, 7};
		List<Challenge> chList = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			User owner = seedUsers.get(ownerIndex[i]);
			LocalDate start = base.plusDays(i * 2L);
			LocalDate end = i % 3 == 0 ? start.plusMonths(2) : null;
			chList.add(challenges.save(new Challenge(
					owner, "Demo Challenge " + (i + 1), descriptions[i], start, end)));
		}

		List<List<DayOfWeek>> challengeWeekPatterns = List.of(
				List.of(),
				MON_WED_FRI,
				List.of(),
				TUE_THU,
				WEEKDAYS,
				List.of(),
				SAT_SUN,
				List.of(),
				MON_WED_FRI,
				List.of());
		ScheduleKind[] challengeKinds = {
			ScheduleKind.DAILY,
			ScheduleKind.WEEKLY_ON_SELECTED_DAYS,
			ScheduleKind.DAILY,
			ScheduleKind.WEEKLY_ON_SELECTED_DAYS,
			ScheduleKind.WEEKLY_ON_SELECTED_DAYS,
			ScheduleKind.DAILY,
			ScheduleKind.WEEKLY_ON_SELECTED_DAYS,
			ScheduleKind.DAILY,
			ScheduleKind.WEEKLY_ON_SELECTED_DAYS,
			ScheduleKind.DAILY
		};

		for (int i = 0; i < 10; i++) {
			Challenge ch = chList.get(i);
			Schedule sch =
					Schedule.forChallenge(ch, challengeKinds[i], challengeWeekPatterns.get(i));
			ch.bindSchedule(sch);
			schedules.save(sch);
		}

		int[] participantCount = {5, 2, 4, 1, 3, 5, 2, 4, 1, 3};
		for (int c = 0; c < 10; c++) {
			Challenge ch = chList.get(c);
			int ownerIdx = ownerIndex[c];
			int n = participantCount[c];
			for (int k = 0; k < n; k++) {
				int uIdx = (ownerIdx + 1 + k) % 10;
				if (uIdx == ownerIdx) {
					uIdx = (uIdx + 1) % 10;
				}
				participants.save(new Participant(seedUsers.get(uIdx), ch));
			}
		}

		int[] subtaskCounts = {2, 0, 1, 3, 0, 1, 2, 0, 1, 0};
		List<List<SubTask>> subtasksByChallenge = new ArrayList<>();
		for (int c = 0; c < 10; c++) {
			subtasksByChallenge.add(new ArrayList<>());
			Challenge ch = chList.get(c);
			for (int s = 0; s < subtaskCounts[c]; s++) {
				SubTask st = subTasks.save(new SubTask(ch, "Subtask " + (s + 1) + " of Ch " + (c + 1), s));
				subtasksByChallenge.get(c).add(st);
				boolean stDaily = (c + s) % 2 == 0;
				Schedule stSch =
						stDaily
								? Schedule.forSubTask(st, ScheduleKind.DAILY, List.of())
								: Schedule.forSubTask(
										st, ScheduleKind.WEEKLY_ON_SELECTED_DAYS, MON_WED_FRI);
				st.bindSchedule(stSch);
				schedules.save(stSch);
			}
		}

		Challenge ch0 = chList.get(0);
		User p0a = seedUsers.get(1);
		User p0b = seedUsers.get(2);
		checkIns.save(new CheckIn(p0a, ch0, ch0.getStartDate(), null));
		checkIns.save(new CheckIn(p0b, ch0, ch0.getStartDate().plusDays(1), null));

		List<SubTask> ch0subs = subtasksByChallenge.get(0);
		if (!ch0subs.isEmpty()) {
			SubTask st0 = ch0subs.getFirst();
			checkIns.save(new CheckIn(seedUsers.get(3), ch0, ch0.getStartDate().plusDays(2), st0));
		}

		Challenge ch3 = chList.get(3);
		List<SubTask> ch3subs = subtasksByChallenge.get(3);
		if (ch3subs.size() >= 2) {
			checkIns.save(
					new CheckIn(seedUsers.get(4), ch3, ch3.getStartDate().plusDays(1), ch3subs.get(1)));
		}

		Challenge ch6 = chList.get(6);
		checkIns.save(new CheckIn(seedUsers.get(8), ch6, ch6.getStartDate(), null));
		List<SubTask> ch6subs = subtasksByChallenge.get(6);
		if (!ch6subs.isEmpty()) {
			checkIns.save(new CheckIn(seedUsers.get(9), ch6, ch6.getStartDate().plusDays(3), ch6subs.getFirst()));
		}

		User u8 = seedUsers.get(8);
		User u9 = seedUsers.get(9);
		User u0 = seedUsers.getFirst();
		User u1 = seedUsers.get(1);
		User u2 = seedUsers.get(2);
		User u3 = seedUsers.get(3);
		User u4 = seedUsers.get(4);

		Invite inv1 = invites.save(new Invite(u8, u9, ch0));
		inv1.setStatus(InviteStatus.PENDING);
		invites.save(inv1);

		Invite inv2 = invites.save(new Invite(u1, u8, chList.get(5)));
		inv2.setStatus(InviteStatus.PENDING);
		invites.save(inv2);

		if (!ch3subs.isEmpty()) {
			Invite inv3 = invites.save(new Invite(u2, u9, ch3, ch3subs.getFirst()));
			inv3.setStatus(InviteStatus.PENDING);
			invites.save(inv3);
		}

		Challenge ch9 = chList.get(9);
		Invite inv4 = invites.save(new Invite(u0, u1, ch9));
		inv4.setStatus(InviteStatus.ACCEPTED);
		invites.save(inv4);
		participants.save(new Participant(u1, ch9));

		Invite inv5 = invites.save(new Invite(u3, u4, chList.get(2)));
		inv5.setStatus(InviteStatus.DECLINED);
		inv5.setExpiresAt(Instant.parse("2026-05-01T00:00:00Z"));
		invites.save(inv5);
	}
}
```

- [ ] **Step 2: `./gradlew compileJava --no-daemon`**

Expected: **BUILD SUCCESSFUL**

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/challenges/api/dev/DemoDataLoader.java
git commit -m "feat(dev): demo-seed profile loads rich challenge dataset"
```

---

### Task 3: Integration test (profile isolation)

**Files:**
- Create: `src/test/java/com/challenges/api/dev/DemoDataLoaderIT.java`

- [ ] **Step 1: Add test class**

```java
package com.challenges.api.dev;

import static org.assertj.core.api.Assertions.assertThat;

import com.challenges.api.repo.ChallengeRepository;
import com.challenges.api.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"test", "demo-seed"})
class DemoDataLoaderIT {

	private final UserRepository users;
	private final ChallengeRepository challenges;

	@Autowired
	DemoDataLoaderIT(UserRepository users, ChallengeRepository challenges) {
		this.users = users;
		this.challenges = challenges;
	}

	@Test
	void demoSeedCreatesUsersAndChallenges() {
		assertThat(users.existsByEmail("seed01@demo.local")).isTrue();
		assertThat(users.existsByEmail("seed10@demo.local")).isTrue();
		assertThat(challenges.findAll()).hasSizeGreaterThanOrEqual(10);
	}
}
```

- [ ] **Step 2: Run**

```bash
./gradlew test --tests 'com.challenges.api.dev.DemoDataLoaderIT' --no-daemon
```

Expected: **BUILD SUCCESSFUL**; test **PASS** (Postgres **`challengestest`** running; **`prepareTestDatabase`** runs before tests).

- [ ] **Step 3: Run full suite**

```bash
./gradlew test --no-daemon
```

Expected: **BUILD SUCCESSFUL** (other tests do **not** use **`demo-seed`**).

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/challenges/api/dev/DemoDataLoaderIT.java
git commit -m "test(dev): cover demo-seed loader with isolated profile"
```

---

### Task 4: README + manual smoke

**Files:**
- Modify: [`README.md`](../../README.md)

- [ ] **Step 1: After “Local development”**, append subsection **Demo data (optional)** to [`README.md`](../../README.md) with:
  - Heading: `## Demo data (optional)`
  - Line: Never enable this in **production**.
  - Line: With Postgres running, start the app with profile **`demo-seed`**.
  - Fenced shell block:

```bash
./gradlew bootRun --args='--spring.profiles.active=demo-seed'
```

  - Closing paragraph: This inserts **10 users** (`seed01@demo.local` through `seed10@demo.local`) with password **`password`** (BCrypt **`User.TEST_PASSWORD_HASH`**), **10 challenges**, schedules, subtasks, participants, check-ins, and invites. A second start **does not duplicate** rows (idempotent guard on `seed01@demo.local`).

- [ ] **Step 2: Manual smoke (optional)**

```bash
docker compose up -d
./gradlew bootRun --args='--spring.profiles.active=demo-seed'
```

In another terminal, verify e.g. `psql` or API: expect **≥ 10** users with email like **`seed%@demo.local`**.

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "docs: document demo-seed profile"
```

---

## Self-review

1. **Spec coverage:** 10 users ✓; 10 challenges with descriptions ✓; ownership: users **0–7** own challenges, **0 and 2** own **two** each, **8–9** own **none** (participants + invitees) ✓; 1–5 participants per challenge via **`participantCount`** ✓; varied challenge schedules ✓; 0–3 subtasks with own schedules ✓; challenge-level and subtask-level check-ins ✓; invites PENDING / ACCEPTED / DECLINED ✓; participants also receive invites ✓.
2. **Placeholder scan:** No TBD; full Java for loader and IT.
3. **Type consistency:** `ScheduleKind`, `InviteStatus`, `DayOfWeek` names match JPA entities; `User.DEFAULT_ROLE` is **`"USER"`** (matches column length).

---

## Execution handoff

**Plan complete and saved to** `docs/superpowers/plans/2026-04-19-08-demo-database-seed.md`. **Two execution options:**

**1. Subagent-Driven (recommended)** — Fresh subagent per task; use **superpowers:subagent-driven-development**.

**2. Inline Execution** — Run tasks in this session using **superpowers:executing-plans**.

**Which approach?**
