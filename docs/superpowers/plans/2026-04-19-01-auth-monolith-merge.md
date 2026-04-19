# Auth monolith merge (authspring → challengesapi) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Merge JWT authentication from the sibling project `/Users/evgenyusorov/workspace/java/authspring` (GitHub `eusorov/authspring`) into **challengesapi** as a **single Spring Boot 4.0.5 monolith**, one deployable JAR. **Auth behavior** lives in **`com.authspring.api.*`** (same as the sibling repo); **challenges** stay in **`com.challenges.api.*`**. **There must be exactly one persisted user type:** a **merged** `com.challenges.api.model.User`, not two entities or two tables.

**Architecture:** **User merge first (see next section).** Port authspring’s **application-layer auth** (config, security, services, REST controllers, DTOs) into **`com.authspring.api.*`** inside this repo—same package names as the sibling project so diffs and muscle memory carry over. **`@SpringBootApplication` must scan both `com.challenges.api` and `com.authspring.api`** (see **Task 2b**). Persistence stays unified: **one `users` table** per Flyway **`V1__create_users.sql`**; **`com.challenges.api.model.User`** is the only `User` entity and the type for `UserPrincipal`, JWT subject, and all challenge FKs—**delete** `com.authspring.api.domain.User` after copy. Non-user auth entities (`PersonalAccessToken`, password-reset tables) remain ported into **`com.challenges.api.model`** + **`com.authspring.api` services** import them from there (avoids `@EntityScan` on two roots). **PostgreSQL + Flyway**; copy **V1–V4**; add **V5**. **Rename** authspring’s `UserController` → **`CurrentUserController`** to avoid clashing with challenges **`/api/users`**.

**Tech stack:** Java 25, Spring Boot 4.0.5, Gradle, Spring Security, JJWT 0.12.6, Spring Mail, Flyway, PostgreSQL, Resilience4j Spring Boot 4, Testcontainers PostgreSQL, JUnit 5.

---

## Package layout (monolith)

| Package root | Owns |
|--------------|------|
| **`com.challenges.api`** | Spring Boot **entrypoint** (`ChallengesApiApplication` with **broad component scan**), **domain** (`model`, `repo`, `service`, `web` for challenges CRUD). **Merged `User`** and auth-related **JPA entities** that are not “logic” (`PersonalAccessToken`, password reset entities) live under **`com.challenges.api.model`**; **`UserRepository`** and other `JpaRepository` interfaces live under **`com.challenges.api.repo`**. |
| **`com.authspring.api`** | **Auth behavior only:** `config`, `security`, `service`, `web` (+ `web/dto`). Package declarations stay **`package com.authspring.api...`**. Imports reference **`com.challenges.api.model.User`**, **`com.challenges.api.repo.UserRepository`**, **`com.challenges.api.repo.PersonalAccessTokenRepository`**, etc. **No** second `User` entity under `com.authspring.api.domain`. |

---

## Merged `User` entity (single source of truth)

Authspring and challengesapi each define a JPA `@Entity` named `User` mapped to table **`users`**. In the monolith they **must become one class** so:

- `Challenge`, `Participant`, `CheckIn`, `Comment`, `Invite`, and any other `@ManyToOne` / `@JoinColumn` to users reference **the same** `User` type auth uses.
- `JwtService`, `SessionService`, `RegisterService`, `UserPrincipal`, and `PersonalAccessTokenService` (which sets `tokenable_type` to `User.class.getName()`) all resolve to **`com.challenges.api.model.User`**—one fully qualified name in the database.

### Field reconciliation

| Concern | challengesapi (before) | authspring (before) | Merged decision |
|--------|-------------------------|---------------------|-----------------|
| Table name | `users` | `users` | **One table `users`.** |
| Primary key | `Long id` identity | `Long id` identity | **Keep.** |
| Email | `unique`, length **320** | `unique`, length **255** | **Use Flyway V1: `VARCHAR(255)`.** Update any challenge DTO validation that assumed 320. |
| Display / profile | (none) | `name` required | **Add `name`**; required for register and for sensible challenge UX. |
| Credentials | (none) | `password` BCrypt | **Add `password`**; never expose in JSON DTOs. |
| Authorization | (none) | `role` | **Add `role`** (string, matches authspring length 8). |
| Email verification | (none) | `email_verified_at`, Laravel extras | **Take authspring columns** as in V1. |
| Timestamps | `createdAt` with field default `Instant.now()` | `created_at` / `updated_at` nullable in DB | **Use `@PrePersist` / `@PreUpdate`** to set both (matches Task 3 sample); nullable columns OK. |
| Other | — | `date_closed`, `remember_token` | **Keep** from authspring for parity with ported services. |

### Rules for implementers

1. **One Java class:** `src/main/java/com/challenges/api/model/User.java` is the only `User` entity. Delete or never copy `authspring/.../domain/User.java` into the monolith except as a reference while typing fields.
2. **One repository:** `com.challenges.api.repo.UserRepository` is the only `JpaRepository<User, Long>`; it must include `findByEmail` for auth.
3. **After porting `com.authspring.api`:** run `rg "com\.authspring\.api\.domain\.User" src/main/java` and expect **zero** matches (file `domain/User.java` must not exist); confirm **`com.authspring.api`** classes that need a user import **`com.challenges.api.model.User`**.
4. **Existing challenge-only data:** If you had a dev database with old `users` rows (email-only), you **cannot** keep that schema: either **wipe and migrate** with Flyway from empty Postgres, or supply a **one-off data migration** (not in V1) that adds NOT NULL columns with defaults (e.g. placeholder `name`, random `password`, `role = 'user'`) before setting `NOT NULL`. Greenfield: start from Flyway V1 + V5.

### Constructor and API migration

- Remove **`User(String email)`** as the only constructor; replace with **`User(String name, String email, String password, String role)`** (password already encoded when passed from service, or raw only inside a factory—match the Task 3 sample).
- Every callsite that did `new User("x@test")` must use **`TestUserFactory`** or a service that supplies **name + encoded password + role**.

---

## File map (create / modify / delete)

| Path | Responsibility |
|------|----------------|
| `build.gradle` | Add security, mail, flyway, postgres, jjwt, resilience4j, security-test, testcontainers. |
| `src/main/resources/application.yml` | Replace `application.properties`; datasource, flyway, jwt, app.* mail/frontend/verification, resilience4j, logging. |
| `src/test/resources/application-test.yml` | Testcontainers PostgreSQL, flyway, JWT secret override. |
| `src/main/resources/db/migration/V1__create_users.sql` … `V4__…` | Copied from authspring `app/src/main/resources/db/migration/`. |
| `src/main/resources/db/migration/V5__baseline_challenges_schema.sql` | **New:** DDL for tables JPA currently creates (`challenges`, `schedules`, `sub_tasks`, `participants`, `invites`, `check_ins`, `comments`, …) — generate once from Hibernate `ddl-auto` + schema export **or** hand-write to match existing entity mappings (required when `ddl-auto=validate`). |
| `src/main/java/com/challenges/api/model/User.java` | **Merge** challenges + authspring into **one** entity (see **Merged `User` entity**); sole FK target for domain + auth. **Do not** keep `com.authspring.api.domain.User`. |
| `src/main/java/com/challenges/api/model/PersonalAccessToken.java` | Port from authspring `domain/PersonalAccessToken.java` (package `com.challenges.api.model`). |
| `src/main/java/com/challenges/api/model/PasswordReset.java`, `PasswordResetToken.java`, `PasswordResetId.java` | Port from authspring `domain` into **`com.challenges.api.model`**. |
| `src/main/java/com/challenges/api/repo/UserRepository.java` | Add `Optional<User> findByEmail(String email)`; sole `JpaRepository` for `User`. |
| `src/main/java/com/challenges/api/repo/PersonalAccessTokenRepository.java` | Port from authspring; **delete** any duplicate under `com.authspring.api.repo`. |
| `src/main/java/com/challenges/api/repo/*` | Repos for password-reset entities if missing. |
| `src/main/java/com/authspring/api/config/*.java` | Port from sibling `api/config`; keep **`package com.authspring.api.config`**. |
| `src/main/java/com/authspring/api/security/*.java` | Port from sibling `api/security`; fix imports to **`com.challenges.api.model.User`**; DTO renames as below. |
| `src/main/java/com/authspring/api/service/*.java` | Port from sibling `api/service`; **`PersonalAccessTokenService`** uses **`com.challenges.api.model.User.class.getName()`** for `tokenable_type`. |
| `src/main/java/com/authspring/api/web/*.java` | Port controllers; replace `UserController` with **`CurrentUserController`**. |
| `src/main/java/com/authspring/api/web/dto/*.java` | Port DTOs; rename authspring **`UserResponse`** → **`UserProfileResponse`** (avoid clash with `com.challenges.api.web.dto.UserResponse`). |
| `src/main/java/com/challenges/api/ChallengesApiApplication.java` | Add **`scanBasePackages = {"com.challenges.api", "com.authspring.api"}`** (Task 2b). |
| `src/main/java/com/challenges/api/web/GlobalExceptionHandler.java` | Merge authspring handler behaviors; reference **`com.authspring.api.security.UserPrincipal`**. |
| `src/main/java/com/challenges/api/web/UserController.java` | Keep mapping `/api/users`; update `UserService` / DTO assumptions for richer `User`. |
| `src/main/java/com/challenges/api/service/UserService.java` | Adjust `create`/`replace` for non-null password & name & role **or** narrow CRUD to admin-only and document deprecation. |
| `src/main/java/com/challenges/api/web/dto/UserResponse.java` | Keep record for `/api/users` list; map from expanded `User` (subset fields). |
| `docker-compose.yml` (optional, repo root) | PostgreSQL + Mailhog mirror authspring `docker-compose.yml` for local dev. |
| Delete | `src/main/resources/application.properties` (after YAML migration). |

---

### Task 1: Failing integration test — protected challenge route

**Files:**
- Create: `src/test/java/com/authspring/api/ProtectedChallengeIT.java`
- Modify: (none until Task 2)

- [ ] **Step 1: Write the failing test**

```java
package com.authspring.api;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ProtectedChallengeIT {

    private static final String HV = "API-Version";
    private static final String V1 = "1";

    @Autowired
    MockMvc mockMvc;

    @Test
    void listChallenges_withoutBearer_returns401() throws Exception {
        mockMvc.perform(get("/api/challenges").header(HV, V1).accept(APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.authspring.api.ProtectedChallengeIT`

Expected: **FAIL** (e.g. `status expected:<401> but was:<200>`) because security is not yet applied.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/authspring/api/ProtectedChallengeIT.java
git commit -m "test: expect 401 for unauthenticated challenge list"
```

---

### Task 2: Gradle dependencies

**Files:**
- Modify: `build.gradle`

- [ ] **Step 1: Add dependencies**

Replace the entire `dependencies { ... }` block with:

```gradle
dependencies {
	implementation 'org.springframework.boot:spring-boot-starter-webmvc'
	implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
	implementation 'org.springframework.boot:spring-boot-starter-validation'
	implementation 'org.springframework.boot:spring-boot-starter-security'
	implementation 'org.springframework.boot:spring-boot-starter-mail'
	implementation 'org.springframework.boot:spring-boot-starter-flyway'
	implementation 'org.flywaydb:flyway-database-postgresql'
	runtimeOnly 'org.postgresql:postgresql'

	implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
	runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
	runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'

	implementation platform('io.github.resilience4j:resilience4j-bom:2.4.0')
	implementation 'io.github.resilience4j:resilience4j-spring-boot4'

	testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'
	testImplementation 'org.springframework.boot:spring-boot-starter-data-jpa-test'
	testImplementation 'org.springframework.boot:spring-boot-starter-jdbc-test'
	testImplementation 'org.springframework.security:spring-security-test'
	testImplementation 'org.springframework.boot:spring-boot-testcontainers'
	testImplementation 'org.testcontainers:testcontainers-junit-jupiter:2.0.4'
	testImplementation 'org.testcontainers:testcontainers-postgresql:2.0.4'
	testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
```

- [ ] **Step 2: Sync and compile**

Run: `./gradlew compileJava compileTestJava`

Expected: PASS (tests may still fail).

- [ ] **Step 3: Commit**

```bash
git add build.gradle
git commit -m "build: add security, jwt, flyway, postgres, resilience4j, testcontainers"
```

---

### Task 2b: Component scan for `com.authspring.api`

**Files:**
- Modify: `src/main/java/com/challenges/api/ChallengesApiApplication.java`

Without this, beans under **`com.authspring.api`** (security, services, auth controllers) are **not** registered because `@SpringBootApplication` defaults to scanning only the application class package (`com.challenges.api`).

- [ ] **Step 1: Add `scanBasePackages`**

```java
package com.challenges.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.challenges.api", "com.authspring.api"})
public class ChallengesApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(ChallengesApiApplication.class, args);
	}
}
```

- [ ] **Step 2: Run** `./gradlew compileJava` — expect PASS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/challenges/api/ChallengesApiApplication.java
git commit -m "feat: scan com.authspring.api for auth beans"
```

**Note:** All **`JpaRepository`** interfaces stay under **`com.challenges.api.repo`** so Spring Data needs **no** extra `@EnableJpaRepositories` for `com.authspring.api`. If you later move a repository interface under `com.authspring.api.repo`, add **`@EnableJpaRepositories(basePackages = {"com.challenges.api.repo", "com.authspring.api.repo"})`** on `ChallengesApiApplication` (or a `@Configuration`).

---

### Task 3: Merge `User` entity and `UserRepository` (challengesapi + authspring)

**Files:**
- Modify: `src/main/java/com/challenges/api/model/User.java`
- Modify: `src/main/java/com/challenges/api/repo/UserRepository.java`

**Prerequisite:** Read **Merged `User` entity** above. The outcome is **one** `User` class satisfying both challenge relations and auth persistence.

- [ ] **Step 1: Replace `User.java` with the merged entity** (union of authspring columns + continued use as `Challenge.owner` / all user FKs). Side-by-side check against authspring `app/.../domain/User.java`: every `@Column` in authspring must appear on the merged class with the same **`name =`** where applicable so V1 DDL matches Hibernate mapping.

```java
package com.challenges.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 255)
	private String name;

	@Column(nullable = false, unique = true, length = 255)
	private String email;

	@Column(name = "email_verified_at")
	private Instant emailVerifiedAt;

	@Column(name = "date_closed")
	private LocalDate dateClosed;

	@Column(nullable = false, length = 255)
	private String password;

	@Column(nullable = false, length = 8)
	private String role;

	@Column(name = "remember_token", length = 100)
	private String rememberToken;

	@Column(name = "created_at")
	private Instant createdAt;

	@Column(name = "updated_at")
	private Instant updatedAt;

	protected User() {
	}

	public User(String name, String email, String password, String role) {
		this.name = name;
		this.email = email;
		this.password = password;
		this.role = role;
	}

	@PrePersist
	@PreUpdate
	void touchTimestamps() {
		Instant now = Instant.now();
		if (createdAt == null) {
			createdAt = now;
		}
		updatedAt = now;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = java.util.Objects.requireNonNull(email);
	}

	public Instant getEmailVerifiedAt() {
		return emailVerifiedAt;
	}

	public void setEmailVerifiedAt(Instant emailVerifiedAt) {
		this.emailVerifiedAt = emailVerifiedAt;
	}

	public LocalDate getDateClosed() {
		return dateClosed;
	}

	public void setDateClosed(LocalDate dateClosed) {
		this.dateClosed = dateClosed;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getRole() {
		return role;
	}

	public void setRole(String role) {
		this.role = role;
	}

	public String getRememberToken() {
		return rememberToken;
	}

	public void setRememberToken(String rememberToken) {
		this.rememberToken = rememberToken;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
```

- [ ] **Step 2: Extend `UserRepository.java`**

```java
package com.challenges.api.repo;

import com.challenges.api.model.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmail(String email);
}
```

- [ ] **Step 3: Reconcile email length with Flyway V1** The old challengesapi `User` used `@Column(length = 320)`; authspring **`V1__create_users.sql`** uses **`VARCHAR(255)`**. The merged entity **must** use **`length = 255`** (as in the Step 1 sample). After Task 4, add **`@Size(max = 255)`** on email in **`UserRequest`**, **`RegisterRequest`**, and any other auth DTOs that validated a longer email.

- [ ] **Step 4: Run compile**

Run: `./gradlew compileJava`

Expected: PASS (callers of `new User(email)` still break — fixed in Tasks 4–5).

- [ ] **Step 5: Verify a single `users` table mapping**

Run:

```bash
rg '@Table\(name = "users"\)' src/main/java
```

Expected: **Exactly one** Java file (the merged `com.challenges.api.model.User`).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/challenges/api/model/User.java src/main/java/com/challenges/api/repo/UserRepository.java
git commit -m "feat: merge challenges and authspring into single User entity"
```

---

### Task 4: `UserService`, `UserRequest`, `UserResponse` for CRUD `/api/users`

**Files:**
- Modify: `src/main/java/com/challenges/api/service/UserService.java`
- Modify: `src/main/java/com/challenges/api/web/dto/UserRequest.java` (add `name`, `password`, `role` fields with validation)
- Modify: `src/main/java/com/challenges/api/web/dto/UserResponse.java`
- Modify: `src/main/java/com/challenges/api/web/UserController.java`

- [ ] **Step 1: Expand `UserRequest` to a record** (example — align field names with your validation style)

```java
package com.challenges.api.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserRequest(
		@NotBlank @Size(max = 255) String name,
		@NotBlank @Email @Size(max = 255) String email,
		@NotBlank @Size(min = 8, max = 255) String password,
		@NotBlank @Size(max = 8) String role) {}
```

- [ ] **Step 2: Replace `UserResponse` with subset for admin list**

```java
package com.challenges.api.web.dto;

import com.challenges.api.model.User;
import java.time.Instant;

public record UserResponse(Long id, String name, String email, String role, Instant createdAt) {

	public static UserResponse from(User u) {
		return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getRole(), u.getCreatedAt());
	}
}
```

- [ ] **Step 3: Update `UserService`** — inject `PasswordEncoder`, encode password on create/replace, use full constructor:

```java
package com.challenges.api.service;

import com.challenges.api.model.User;
import com.challenges.api.repo.UserRepository;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NonNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Service
public class UserService {

	private final UserRepository users;
	private final PasswordEncoder passwordEncoder;

	public UserService(UserRepository users, PasswordEncoder passwordEncoder) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional(readOnly = true)
	public @NonNull List<User> listUsers() {
		return users.findAll();
	}

	@Transactional(readOnly = true)
	public Optional<User> findById(@NonNull Long id) {
		Assert.notNull(id, "id must not be null");
		return users.findById(id);
	}

	@Transactional
	public @NonNull User create(@NonNull String name, @NonNull String email, @NonNull String rawPassword, @NonNull String role) {
		User u = new User(name, email, passwordEncoder.encode(rawPassword), role);
		return users.save(u);
	}

	@Transactional
	public Optional<User> replace(@NonNull Long id, @NonNull String name, @NonNull String email, @NonNull String rawPassword, @NonNull String role) {
		return users.findById(id).map(u -> {
			u.setName(name);
			u.setEmail(email);
			u.setPassword(passwordEncoder.encode(rawPassword));
			u.setRole(role);
			return users.save(u);
		});
	}

	@Transactional
	public boolean delete(@NonNull Long id) {
		if (!users.existsById(id)) {
			return false;
		}
		users.deleteById(id);
		return true;
	}
}
```

- [ ] **Step 4: Update `UserController` method bodies** to pass `req.name()`, `req.email()`, `req.password()`, `req.role()` into `UserService`.

- [ ] **Step 5: Run tests** — expect failures in ITs still using `new User("email")`.

Run: `./gradlew test`

Expected: **FAIL** in multiple tests (constructor).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/challenges/api/service/UserService.java src/main/java/com/challenges/api/web/dto/UserRequest.java src/main/java/com/challenges/api/web/dto/UserResponse.java src/main/java/com/challenges/api/web/UserController.java
git commit -m "feat: user CRUD aligned with auth user shape"
```

---

### Task 5: Test support — create users with encoded password

**Files:**
- Create: `src/test/java/com/challenges/api/support/TestUserFactory.java`

- [ ] **Step 1: Add factory**

```java
package com.challenges.api.support;

import com.challenges.api.model.User;
import com.challenges.api.repo.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class TestUserFactory {

	private final UserRepository users;
	private final PasswordEncoder passwordEncoder;

	@Autowired
	public TestUserFactory(UserRepository users, PasswordEncoder passwordEncoder) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
	}

	public User createUser(String email) {
		return users.save(new User("Test User", email, passwordEncoder.encode("password123"), "user"));
	}
}
```

- [ ] **Step 2: Update each `@BeforeEach` that used `new User("x@test")`** to inject `TestUserFactory` and call `createUser("x@test")` (files include `ChallengeControllerIT`, `UserControllerIT`, `ParticipantControllerIT`, domain workflow ITs — grep for `new User(`).

Run: `rg "new User\\(" src/test/java`

- [ ] **Step 3: Update JSON fixtures** that POST `/api/users` to include `name`, `password`, `role` per new `UserRequest`.

- [ ] **Step 4: Run tests**

Run: `./gradlew test`

Expected: may still FAIL (no DB/flyway/security yet).

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/challenges/api/support/TestUserFactory.java src/test/java
git commit -m "test: create users with bcrypt password via TestUserFactory"
```

---

### Task 6: Flyway migrations and PostgreSQL test configuration

**Files:**
- Create: `src/main/resources/db/migration/V1__create_users.sql` (copy from authspring)
- Create: `V2__create_password_resets.sql`, `V3__create_password_reset_tokens.sql`, `V4__create_personal_access_tokens.sql` (copy)
- Create: `V5__baseline_challenges_schema.sql` (**author must** dump current H2/JPA schema or export from entities — include all tables referenced under `com.challenges.api.model` except duplicates)
- Create: `src/main/resources/application.yml` (see Task 8 for full content — can split: Task 6 adds flyway + datasource only)
- Create: `src/test/resources/application-test.yml`
- Delete: `src/main/resources/application.properties`

- [ ] **Step 1: Copy `V1`–`V4`** from `/Users/evgenyusorov/workspace/java/authspring/app/src/main/resources/db/migration/` into `challengesapi/src/main/resources/db/migration/` unchanged.

- [ ] **Step 2: Add test profile** `src/test/resources/application-test.yml` (match authspring shape — **no** hard-coded datasource URL; Testcontainers supplies JDBC via `@ServiceConnection`):

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  flyway:
    enabled: true
    locations: classpath:db/migration

jwt:
  secret: test-jwt-secret-must-be-at-least-32-characters
  expiration-ms: 3600000
  password-reset-expiration-ms: 3600000

resilience4j:
  ratelimiter:
    instances:
      apiGlobal:
        limitForPeriod: 1000000
        limitRefreshPeriod: 1m
        timeoutDuration: 5s

app:
  frontend:
    base-url: http://localhost:3000
  verification:
    signing-key: test-verification-signing-key-32chars!!
    public-base-url: https://example.com
    expire-minutes: 60
```

- [ ] **Step 3: On every `@SpringBootTest` that needs a database**, add the same Testcontainers wiring authspring uses (`AuthLoginIT` pattern):

```java
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SomeIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    // ...
}
```

Optional: extract an abstract `AbstractPostgresIT` in `src/test/java/com/challenges/api/support/` holding the `@Container` field so ITs extend one class instead of repeating.

- [ ] **Step 4: Author `V5__baseline_challenges_schema.sql`** so `ddl-auto=validate` passes. Practical approach: temporarily run with `ddl-auto=create-only` + `schema-export` **or** start app against empty Postgres once and `\d` each table. The plan requires **no placeholder**: the engineer must list every column/FK exactly as JPA mappings dictate.

- [ ] **Step 5: Run Flyway + tests**

Run: `./gradlew test`

Expected: **FAIL** until `V5` is correct and Task 7 security is wired; once both are correct, green.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/db/migration src/test/resources/application-test.yml
git commit -m "db: add flyway migrations including challenges baseline"
```

---

### Task 7: Port authspring code into `com.authspring.api`

**Files:** (mechanical port + fixes)

- [ ] **Step 1: Copy tree** into this repo under **`src/main/java/com/authspring/api`** (excludes **`domain/User.java`** — merged `User` is only `com.challenges.api.model.User`).

From a shell:

```bash
SRC="/Users/evgenyusorov/workspace/java/authspring/app/src/main/java/com/authspring/api"
DST="/Users/evgenyusorov/workspace/java/challengesapi/src/main/java/com/authspring/api"
mkdir -p "$DST"
rsync -a --exclude='domain/User.java' "$SRC"/ "$DST"/
```

Keep existing **`package com.authspring.api...`** lines; **do not** rewrite packages to `com.challenges.api`.

- [ ] **Step 2: Move non-`User` JPA types** from **`src/main/java/com/authspring/api/domain/`** into **`com.challenges.api.model`** (`PersonalAccessToken`, `PasswordReset`, `PasswordResetToken`, `PasswordResetId`): change their **`package`** and **`import`** statements, then **delete** the old files under `com.authspring.api.domain` and remove the folder if empty.

- [ ] **Step 3: Fix imports inside `com.authspring.api`**

- **`import com.authspring.api.domain.User`** → **`import com.challenges.api.model.User`** everywhere (JwtService, SessionService, RegisterService, UserPrincipal, tests you port later, etc.).
- **`com.authspring.api.domain.PersonalAccessToken`** (and other moved entities) → **`com.challenges.api.model.***`.
- **`com.authspring.api.repo.UserRepository`** → **`com.challenges.api.repo.UserRepository`**; **delete** `src/main/java/com/authspring/api/repo/UserRepository.java` if present.
- **`PersonalAccessTokenRepository`** (and other auth-table repos): move interfaces to **`com.challenges.api.repo`** and delete duplicates under **`com.authspring.api.repo`**, or keep under `com.authspring.api.repo` **only if** you add `@EnableJpaRepositories(basePackages = {...})` per Task 2b note—**prefer** all repos under **`com.challenges.api.repo`**.

- [ ] **Step 3b: Confirm `UserPrincipal` wraps merged `User`**

Open `src/main/java/com/authspring/api/security/UserPrincipal.java` and ensure the stored user type is **`com.challenges.api.model.User`**.

- [ ] **Step 4: Rename DTO** — `com/authspring/api/web/dto/UserResponse.java` → **`UserProfileResponse.java`**; update `LoginResponse`, `RegisterResponse`, controllers.

- [ ] **Step 5: Replace `UserController.java`** with **`CurrentUserController.java`** in **`com.authspring.api.web`**:

```java
package com.authspring.api.web;

import com.authspring.api.security.RequiresAuth;
import com.authspring.api.security.UserPrincipal;
import com.authspring.api.web.dto.UserProfileResponse;
import com.challenges.api.repo.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api", version = "1")
public class CurrentUserController {

	private final UserRepository userRepository;

	public CurrentUserController(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@RequiresAuth
	@GetMapping("/user")
	public ResponseEntity<UserProfileResponse> currentUser(@AuthenticationPrincipal UserPrincipal principal) {
		return userRepository
				.findById(principal.getId())
				.map(UserProfileResponse::fromEntity)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}
}
```

Delete the old **`UserController.java`** in the same package.

- [ ] **Step 6: `compileJava`**

Run: `./gradlew compileJava`

Fix stragglers until clean.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/authspring/api src/main/java/com/challenges/api/model src/main/java/com/challenges/api/repo
git commit -m "feat: port authspring sources into com.authspring.api"
```

---

### Task 8: Security filter chain (monolith rules)

**Files:**
- Modify: `src/main/java/com/authspring/api/security/SecurityConfig.java` (ported)

- [ ] **Step 1: Use ordered rules** — public auth entrypoints; authenticated business API.

Example `authorizeHttpRequests` block (align with ported authspring routes: `VerifyEmailController` is **GET** `/api/email/verify/{id}/{hash}`; `EmailVerificationNotificationController` is **authenticated** **POST** `/api/email/verification-notification`):

```java
import org.springframework.http.HttpMethod;

http.authorizeHttpRequests(auth -> auth
	.requestMatchers("/actuator/**").permitAll()
	.requestMatchers("/api/login", "/api/register").permitAll()
	.requestMatchers("/api/forgot-password", "/api/reset-password").permitAll()
	.requestMatchers(HttpMethod.GET, "/api/email/verify/**").permitAll()
	.requestMatchers("/api/secured/**").authenticated()
	.requestMatchers("/api/user", "/api/logout").authenticated()
	.requestMatchers("/api/email/verification-notification").authenticated()
	.requestMatchers("/api/needsverified/**").authenticated()
	.requestMatchers("/api/users/**").authenticated()
	.requestMatchers("/api/challenges/**").authenticated()
	.requestMatchers("/api/schedules/**").authenticated()
	.requestMatchers("/api/invites/**").authenticated()
	.requestMatchers("/api/comments/**").authenticated()
	.requestMatchers("/api/**").permitAll()
	.anyRequest().denyAll());
```

Adjust paths to match **exact** `@RequestMapping` prefixes in your controllers (grep `src/main/java/com/challenges/api/web` and `src/main/java/com/authspring/api/web` for `path =`).

- [ ] **Step 2: Ensure `JwtAuthenticationFilter` servlet path** skips **versioned** login if needed — Spring Boot versioned mapping may keep servlet path `/api/login`; if not, add the actual path Spring reports in a failing IT.

- [ ] **Step 3: Run** `./gradlew test --tests com.authspring.api.ProtectedChallengeIT`

Expected: **PASS** (401 without token).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/authspring/api/security/SecurityConfig.java
git commit -m "feat: secure API routes in monolith security chain"
```

---

### Task 9: Merge `GlobalExceptionHandler`

**Files:**
- Modify: `src/main/java/com/challenges/api/web/GlobalExceptionHandler.java`

- [ ] **Step 1: Combine** authspring’s `GlobalExceptionHandler` behaviors (field error map, `AccessDeniedException` with `UserPrincipal` check, `NoResourceFoundException`, `DataAccessException`) **with** challengesapi’s simple `fallback(Exception)` if you still want a last-resort 500 handler — **do not** duplicate `@ExceptionHandler` signatures. Use **`com.authspring.api.security.UserPrincipal`** in the `AccessDeniedException` branch (same FQCN as in the ported authspring handler).

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/challenges/api/web/GlobalExceptionHandler.java
git commit -m "feat: unify RFC 9457 exception handling with security"
```

---

### Task 10: Main `application.yml` (dev/prod shape)

**Files:**
- Create: `src/main/resources/application.yml`

- [ ] **Step 1: Port** from `/Users/evgenyusorov/workspace/java/authspring/app/src/main/resources/application.yml` with changes:

- `spring.application.name: challenges-api`
- `spring.datasource` → your PostgreSQL URL (or keep docker-compose defaults)
- Keep `jwt`, `app.frontend`, `app.verification`, `app.mail.*`, `resilience4j`, `management` blocks identical in spirit to authspring.

- [ ] **Step 2: Remove** `src/main/resources/application.properties`.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/application.yml src/main/resources/application.properties
git commit -m "config: application.yml for monolith with jwt and mail"
```

---

### Task 11: Authenticated challenge IT (JWT + token row)

**Files:**
- Modify: `src/test/java/com/challenges/api/web/ChallengeControllerIT.java` (or new test)

- [ ] **Step 1: Add helper** in test to login and return Bearer token

Use `mockMvc.perform(post("/api/login").header("API-Version", "1").contentType(APPLICATION_JSON).content("{\"email\":\"...\",\"password\":\"password123\"}"))` and parse `token` from JSON (use same `ObjectMapper` as existing tests).

Because `JwtAuthenticationFilter` requires a **row in `personal_access_tokens`**, login via `SessionService` already records it — use the **same** token string returned in `LoginResponse`.

- [ ] **Step 2: Call** `get("/api/challenges").header("Authorization", "Bearer " + token).header("API-Version","1")` and expect `200`.

- [ ] **Step 3: Run** `./gradlew test --tests com.challenges.api.web.ChallengeControllerIT`

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/challenges/api/web/ChallengeControllerIT.java
git commit -m "test: exercise challenges API with JWT"
```

---

### Task 12: Port authspring tests (subset)

**Files:**
- Copy selected ITs from `authspring/app/src/test/java/com/authspring/api/` → `challengesapi/src/test/java/com/authspring/api/` keeping **`package com.authspring.api`** (and subpackages) where applicable; add `@ActiveProfiles("test")` and Testcontainers wiring as in Task 6.

- [ ] **Step 1: Port** `AuthLoginIT`, `AuthRegisterIT`, `SecureRouteIT`, `UserRouteIT` **first**; fix imports so test code uses **`com.challenges.api.model.User`**, not `com.authspring.api.domain.User`.

- [ ] **Step 2: Defer** mail-heavy ITs until `spring.mail` host in test YAML points to Testcontainers or greenmail — or use `@MockBean` on mail senders if tests become flaky.

- [ ] **Step 3: Run** `./gradlew test`

Expected: **PASS**

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/authspring/api
git commit -m "test: port core auth integration tests"
```

---

### Task 13: Documentation and cleanup

**Files:**
- Modify: `AGENTS.md` (authentication section)
- Delete: authspring-only controllers not needed (`SecuredTestController` if you don’t want Laravel parity endpoints in prod)

- [ ] **Step 1: Update `AGENTS.md`** — replace “Authentication: none” with: JWT Bearer, login `/api/login`, logout `/api/logout`, header `API-Version: 1`, PostgreSQL + Flyway.

- [ ] **Step 2: Commit**

```bash
git add AGENTS.md
git commit -m "docs: describe monolith JWT auth"
```

---

## Self-review

**1. Spec coverage**

| Requirement | Task(s) |
|-------------|---------|
| **Single merged `User` entity** (no duplicate auth vs domain user) | **Merged `User` entity** section + Task 3 + Task 7 Step 3/3b + verification `rg` |
| **`com.authspring.api` beans load in one app** | **Task 2b** `scanBasePackages` on `ChallengesApiApplication` |
| Monolith, different packages | **`com.authspring.api`** (auth) vs **`com.challenges.api`** (challenges + merged persistence) |
| JWT login/logout/register etc. | Task 7 port + Task 8 security |
| challenges domain still works | Task 3 User FK + Task 5 fixtures + Task 11 IT |
| DB unified | Task 3 + Task 6 V1–V5 |
| SPA/mobile clients | CORS + Bearer unchanged from authspring (Task 10) |

**Gap:** `V5__baseline_challenges_schema.sql` must be authored manually from entities — no shortcut in this plan avoids that engineering step.

**2. Placeholder scan**

No `TBD` / `TODO` / vague “add validation” steps; the only open-ended work is explicitly named: **author `V5` from real DDL**.

**3. Type consistency**

- Exactly **one** JPA type maps to `users`: **`com.challenges.api.model.User`** (merged fields); `tokenable_type` / `UserPrincipal` / auth services and challenge FKs all use this type.
- `User` constructor is `(name, email, password, role)` everywhere after Task 3–4 (password stored encoded).
- Auth profile DTO is **`UserProfileResponse`**; admin list DTO remains **`UserResponse`** in `com.challenges.api.web.dto`.

---

**Plan complete and saved to `docs/superpowers/plans/2026-04-19-01-auth-monolith-merge.md`. Two execution options:**

**1. Subagent-Driven (recommended)** — Dispatch a fresh subagent per task, review between tasks, fast iteration. **REQUIRED SUB-SKILL:** superpowers:subagent-driven-development.

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints. **REQUIRED SUB-SKILL:** superpowers:executing-plans.

**Which approach?**
