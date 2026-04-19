# Login API only (authspring → challengesapi) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port **only** the **`POST /api/login`** contract from `/Users/evgenyusorov/workspace/java/authspring` into **challengesapi**: JSON and form-urlencoded bodies, **200** with `{ "token", "user" }`, **422** `ProblemDetail` with Laravel-style `errors.email` on bad credentials. **Do not** add register, forgot/reset password, email verification, `/api/logout`, `/api/users` (authspring), secured test routes, or `JwtAuthenticationFilter`-based protection of other endpoints.

**Architecture:** Add **`com.authspring.api`** as a **component-scan sibling** of **`com.challenges.api`**. Reuse **`com.challenges.api.model.User`** and **`PasswordEncoder`** already defined in **`com.challenges.api.config.SecurityConfig`** (single bean; do **not** add a second `SecurityConfig`). Persist issued JWT fingerprints in **`personal_access_tokens`** using the **existing Flyway** table from **`V11__personal_access_tokens.sql`** (`user_id`, `token_hash`, …) — **not** authspring’s polymorphic `tokenable_type` / `tokenable_id` / `token` column names; implement a **challenges-shaped JPA entity** and a **small** `PersonalAccessTokenService` that only **`recordLoginToken`**. Login response **`user`** object uses a **new DTO** (e.g. `AuthUserResponse`) so it does not clash with **`com.challenges.api.web.dto.UserResponse`** (`/api/users`).

**Tech stack:** Java 25, Spring Boot 4.0.x, Spring Security, JJWT 0.12.6, PostgreSQL, Flyway, JUnit 5, MockMvc (same as existing ITs).

**Source references (sibling repo, read-only):**
- `authspring/app/src/main/java/com/authspring/api/web/LoginController.java` — copy **only** `loginJson`, `loginForm`, `loginResult`, `invalidLoginProblemDetail` (omit `destroy` / `/logout`).
- `authspring/app/src/main/java/com/authspring/api/service/SessionService.java`
- `authspring/app/src/main/java/com/authspring/api/web/dto/LoginRequest.java`, `LoginResponse.java`
- `authspring/app/src/main/java/com/authspring/api/security/JwtService.java` — **subset:** `createToken` only (omit `createPasswordResetFlowToken` until a password-reset plan exists).
- `authspring/app/src/test/java/com/authspring/api/AuthLoginIT.java` — port **first three** tests only (omit logout tests).

---

## File map (create / modify)

| Path | Responsibility |
|------|----------------|
| `src/main/java/com/challenges/api/ChallengesApiApplication.java` | Add `scanBasePackages = {"com.challenges.api", "com.authspring.api"}`. |
| `src/main/java/com/authspring/api/config/JwtProperties.java` | Copy from authspring; `package com.authspring.api.config`; `@ConfigurationProperties(prefix = "jwt")`. Bound via `@EnableConfigurationProperties` on **`ChallengesApiApplication`** (Task 1) or a single `@Configuration` class under `com.authspring.api.config` — not both. |
| `src/main/java/com/authspring/api/security/JwtService.java` | `createToken(com.challenges.api.model.User)` + constructor validation (secret ≥ 32 bytes). |
| `src/main/java/com/challenges/api/model/PersonalAccessToken.java` | JPA entity mapped to **`V11`** columns (`user_id`, `token_hash`, …). |
| `src/main/java/com/challenges/api/repo/PersonalAccessTokenRepository.java` | `findByTokenHash`, `deleteByTokenHash` (only if you add revoke later; **YAGNI** for login-only — `JpaRepository` save is enough if service only inserts). |
| `src/main/java/com/authspring/api/service/PersonalAccessTokenService.java` | **Only** `recordLoginToken(User, String jwtCompact)`; SHA-256 hex → `token_hash`; set `user_id`, `name`, `abilities`, `expires_at`, `created_at` from `JwtProperties.expirationMs`. |
| `src/main/java/com/authspring/api/service/SessionService.java` | Same logic as authspring: `findByEmail`, `passwordEncoder.matches`, `jwtService.createToken`, `recordLoginToken`, `AuthUserResponse.from`. |
| `src/main/java/com/authspring/api/web/dto/LoginRequest.java` | Same as authspring; optional `@Size(max = 255)` on email. |
| `src/main/java/com/authspring/api/web/dto/LoginResponse.java` | `record LoginResponse(String token, AuthUserResponse user)`. |
| `src/main/java/com/authspring/api/web/dto/AuthUserResponse.java` | Fields that exist on **`com.challenges.api.model.User`**: `id`, `name`, `email`, `role`, `created_at`, `updated_at` (use `@JsonProperty` for snake_case). **Do not** require `email_verified_at` / `date_closed` / `remember_token` until those columns exist on `User`. |
| `src/main/java/com/authspring/api/web/LoginController.java` | **`@RequestMapping(path = "/api", version = "1")`**; two `POST /login` handlers + private `loginResult` / `invalidLoginProblemDetail`; **no** `/logout`. |
| `src/main/java/com/challenges/api/repo/UserRepository.java` | `Optional<User> findByEmail(String email);` |
| `src/test/java/com/authspring/api/AuthLoginIT.java` | Three tests: JSON login OK, form login OK, wrong password → 422 + `errors.email[0]`. Use **`com.challenges.api.model.User`** in `@BeforeEach`, **`UserRepository`**, same header `API-Version: 1` as other ITs. Match existing test DB setup (**`@AutoConfigureTestDatabase(Replace.NONE)`** + `application-test.yml` Postgres, **not** Testcontainers, unless you later unify). |

**Explicitly out of scope (do not add in this plan):**
- `JwtAuthenticationFilter`, `UserPrincipal`, `@RequiresAuth`, `/api/logout`, `ProblemJsonAuthenticationEntryPoint` (unless a follow-up plan secures routes).
- `RegisterController`, `ForgotPasswordController`, `ResetPasswordController`, `VerifyEmailController`, `UserController` (authspring), `EmailVerificationNotificationController`.
- Full `PersonalAccessTokenService` (`revokeByJwtFromRequest`, `existsForJwtCompact`) until logout/JWT gate exists.
- `JwtService.createPasswordResetFlowToken` / password-reset flows.

---

### Task 1: Component scan + JWT properties binding

**Files:**
- Modify: `src/main/java/com/challenges/api/ChallengesApiApplication.java`
- Create: `src/main/java/com/authspring/api/config/JwtProperties.java`

- [ ] **Step 1: Update the Spring Boot application**

```java
package com.challenges.api;

import com.authspring.api.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = {"com.challenges.api", "com.authspring.api"})
@EnableConfigurationProperties(JwtProperties.class)
public class ChallengesApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(ChallengesApiApplication.class, args);
	}
}
```

(If you prefer not to annotate the main class, use a dedicated `@Configuration` class under `com.authspring.api.config` with `@EnableConfigurationProperties(JwtProperties.class)` instead — **one** approach only.)

- [ ] **Step 2: Add `JwtProperties`**

Create `src/main/java/com/authspring/api/config/JwtProperties.java`:

```java
package com.authspring.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, long expirationMs, long passwordResetExpirationMs) {}
```

- [ ] **Step 3: Confirm YAML**

`src/main/resources/application.yml` and `src/test/resources/application-test.yml` already define `jwt.secret`, `jwt.expiration-ms`, `jwt.password-reset-expiration-ms`. No change required unless binding fails in a test.

- [ ] **Step 4: Run compile**

Run: `./gradlew compileJava --no-daemon`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/challenges/api/ChallengesApiApplication.java \
  src/main/java/com/authspring/api/config/JwtProperties.java
git commit -m "feat(auth): scan authspring package and bind JwtProperties"
```

---

### Task 2: `UserRepository.findByEmail`

**Files:**
- Modify: `src/main/java/com/challenges/api/repo/UserRepository.java`

- [ ] **Step 1: Add derived query**

```java
package com.challenges.api.repo;

import com.challenges.api.model.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmail(String email);
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileJava --no-daemon`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/challenges/api/repo/UserRepository.java
git commit -m "feat(user): add findByEmail for login"
```

---

### Task 3: `JwtService` (login token only)

**Files:**
- Create: `src/main/java/com/authspring/api/security/JwtService.java`

- [ ] **Step 1: Add service**

```java
package com.authspring.api.security;

import com.authspring.api.config.JwtProperties;
import com.challenges.api.model.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public final class JwtService {

	private final JwtProperties properties;
	private final SecretKey signingKey;

	public JwtService(JwtProperties properties) {
		this.properties = properties;
		byte[] keyBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
		if (keyBytes.length < 32) {
			throw new IllegalStateException("jwt.secret must be at least 32 bytes (256 bits) for HS256");
		}
		this.signingKey = Keys.hmacShaKeyFor(keyBytes);
	}

	public String createToken(User user) {
		Instant now = Instant.now();
		Instant exp = now.plusMillis(properties.expirationMs());
		return Jwts.builder()
				.id(UUID.randomUUID().toString())
				.subject(user.getId().toString())
				.claim("email", user.getEmail())
				.issuedAt(Date.from(now))
				.expiration(Date.from(exp))
				.signWith(signingKey)
				.compact();
	}
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileJava --no-daemon`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/authspring/api/security/JwtService.java
git commit -m "feat(auth): add JwtService.createToken for login"
```

---

### Task 4: `PersonalAccessToken` entity (matches `V11`)

**Files:**
- Create: `src/main/java/com/challenges/api/model/PersonalAccessToken.java`

- [ ] **Step 1: Add entity** (align names with `src/main/resources/db/migration/V11__personal_access_tokens.sql`)

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
@Table(name = "personal_access_tokens")
public class PersonalAccessToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(nullable = false, length = 255)
	private String name;

	@Column(name = "token_hash", nullable = false, unique = true, length = 255)
	private String tokenHash;

	@Column(columnDefinition = "TEXT")
	private String abilities;

	@Column(name = "last_used_at")
	private Instant lastUsedAt;

	@Column(name = "expires_at")
	private Instant expiresAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected PersonalAccessToken() {
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public void setUserId(Long userId) {
		this.userId = userId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getTokenHash() {
		return tokenHash;
	}

	public void setTokenHash(String tokenHash) {
		this.tokenHash = tokenHash;
	}

	public String getAbilities() {
		return abilities;
	}

	public void setAbilities(String abilities) {
		this.abilities = abilities;
	}

	public Instant getLastUsedAt() {
		return lastUsedAt;
	}

	public void setLastUsedAt(Instant lastUsedAt) {
		this.lastUsedAt = lastUsedAt;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public void setExpiresAt(Instant expiresAt) {
		this.expiresAt = expiresAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}
}
```

- [ ] **Step 2: Add repository**

Create `src/main/java/com/challenges/api/repo/PersonalAccessTokenRepository.java`:

```java
package com.challenges.api.repo;

import com.challenges.api.model.PersonalAccessToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonalAccessTokenRepository extends JpaRepository<PersonalAccessToken, Long> {}
```

- [ ] **Step 3: Compile**

Run: `./gradlew compileJava --no-daemon`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/challenges/api/model/PersonalAccessToken.java \
  src/main/java/com/challenges/api/repo/PersonalAccessTokenRepository.java
git commit -m "feat(auth): JPA entity for personal_access_tokens (V11)"
```

---

### Task 5: `PersonalAccessTokenService` (insert-only for login)

**Files:**
- Create: `src/main/java/com/authspring/api/service/PersonalAccessTokenService.java`

- [ ] **Step 1: Add service**

```java
package com.authspring.api.service;

import com.authspring.api.config.JwtProperties;
import com.challenges.api.model.PersonalAccessToken;
import com.challenges.api.model.User;
import com.challenges.api.repo.PersonalAccessTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonalAccessTokenService {

	private final PersonalAccessTokenRepository repository;
	private final JwtProperties jwtProperties;

	public PersonalAccessTokenService(
			PersonalAccessTokenRepository repository, JwtProperties jwtProperties) {
		this.repository = repository;
		this.jwtProperties = jwtProperties;
	}

	@Transactional
	public void recordLoginToken(User user, String jwtCompact) {
		Instant now = Instant.now();
		Instant expiresAt = now.plusMillis(jwtProperties.expirationMs());
		PersonalAccessToken row = new PersonalAccessToken();
		row.setUserId(user.getId());
		row.setName("api");
		row.setTokenHash(sha256Hex(jwtCompact));
		row.setAbilities("[\"*\"]");
		row.setExpiresAt(expiresAt);
		row.setCreatedAt(now);
		repository.save(row);
	}

	private static String sha256Hex(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileJava --no-daemon`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/authspring/api/service/PersonalAccessTokenService.java
git commit -m "feat(auth): persist login JWT hash in personal_access_tokens"
```

---

### Task 6: DTOs + `SessionService`

**Files:**
- Create: `src/main/java/com/authspring/api/web/dto/LoginRequest.java`
- Create: `src/main/java/com/authspring/api/web/dto/AuthUserResponse.java`
- Create: `src/main/java/com/authspring/api/web/dto/LoginResponse.java`
- Create: `src/main/java/com/authspring/api/service/SessionService.java`

- [ ] **Step 1: `LoginRequest`**

```java
package com.authspring.api.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
		@NotBlank @Email @Size(max = 255) String email,
		@NotBlank String password) {}
```

- [ ] **Step 2: `AuthUserResponse`**

```java
package com.authspring.api.web.dto;

import com.challenges.api.model.User;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthUserResponse(
		Long id,
		String name,
		String email,
		String role,
		@JsonProperty("created_at") Instant createdAt,
		@JsonProperty("updated_at") Instant updatedAt) {

	public static AuthUserResponse fromEntity(User user) {
		return new AuthUserResponse(
				user.getId(),
				user.getName(),
				user.getEmail(),
				user.getRole(),
				user.getCreatedAt(),
				user.getUpdatedAt());
	}
}
```

- [ ] **Step 3: `LoginResponse`**

```java
package com.authspring.api.web.dto;

public record LoginResponse(String token, AuthUserResponse user) {}
```

- [ ] **Step 4: `SessionService`**

```java
package com.authspring.api.service;

import com.authspring.api.security.JwtService;
import com.authspring.api.web.dto.AuthUserResponse;
import com.authspring.api.web.dto.LoginRequest;
import com.authspring.api.web.dto.LoginResponse;
import com.challenges.api.model.User;
import com.challenges.api.repo.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final PersonalAccessTokenService personalAccessTokenService;

	public SessionService(
			UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			JwtService jwtService,
			PersonalAccessTokenService personalAccessTokenService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.personalAccessTokenService = personalAccessTokenService;
	}

	@Transactional
	public LoginResponse login(LoginRequest request) {
		User user = userRepository.findByEmail(request.email()).orElse(null);
		if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
			return null;
		}
		String token = jwtService.createToken(user);
		personalAccessTokenService.recordLoginToken(user, token);
		return new LoginResponse(token, AuthUserResponse.fromEntity(user));
	}
}
```

- [ ] **Step 5: Compile**

Run: `./gradlew compileJava --no-daemon`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/authspring/api/web/dto/LoginRequest.java \
  src/main/java/com/authspring/api/web/dto/AuthUserResponse.java \
  src/main/java/com/authspring/api/web/dto/LoginResponse.java \
  src/main/java/com/authspring/api/service/SessionService.java
git commit -m "feat(auth): SessionService and login DTOs"
```

---

### Task 7: `LoginController` (login endpoints only)

**Files:**
- Create: `src/main/java/com/authspring/api/web/LoginController.java`

- [ ] **Step 1: Add controller** (no `/logout`, no `RequiresAuth`)

```java
package com.authspring.api.web;

import com.authspring.api.service.SessionService;
import com.authspring.api.web.dto.LoginRequest;
import com.authspring.api.web.dto.LoginResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api", version = "1")
public class LoginController {

	private final SessionService sessionService;

	public LoginController(SessionService sessionService) {
		this.sessionService = sessionService;
	}

	@PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> loginJson(@Valid @RequestBody LoginRequest request) {
		return loginResult(request);
	}

	@PostMapping(
			value = "/login",
			consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
	public ResponseEntity<Object> loginForm(@Valid @ModelAttribute LoginRequest request) {
		return loginResult(request);
	}

	private ResponseEntity<Object> loginResult(LoginRequest request) {
		LoginResponse response = sessionService.login(request);
		if (response == null) {
			return ResponseEntity.status(HttpStatusCode.valueOf(422)).body(invalidLoginProblemDetail());
		}
		return ResponseEntity.ok(response);
	}

	private static ProblemDetail invalidLoginProblemDetail() {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(
				HttpStatusCode.valueOf(422), "The provided credentials are incorrect.");
		pd.setTitle("Invalid credentials");
		pd.setProperty("message", "The given data was invalid.");
		pd.setProperty("errors", Map.of("email", List.of("The provided credentials are incorrect.")));
		return pd;
	}
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileJava --no-daemon`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/authspring/api/web/LoginController.java
git commit -m "feat(auth): POST /api/login (JSON and form)"
```

---

### Task 8: Integration tests (login only)

**Files:**
- Create: `src/test/java/com/authspring/api/AuthLoginIT.java`

- [ ] **Step 1: Add IT** (match **challengesapi** test style: `SpringBootTest`, `AutoConfigureMockMvc`, `Replace.NONE`, **no** Testcontainers unless the rest of the suite migrated)

```java
package com.authspring.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.challenges.api.model.User;
import com.challenges.api.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class AuthLoginIT {

	private static final String API_VERSION = "API-Version";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@BeforeEach
	void setUp() {
		userRepository.deleteAll();
		User user = new User("Ada", "ada@example.com", passwordEncoder.encode("secret"), "user");
		userRepository.save(user);
	}

	@Test
	void loginReturnsTokenAndUser() throws Exception {
		mockMvc.perform(post("/api/login")
						.header(API_VERSION, "1")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"ada@example.com\",\"password\":\"secret\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isString())
				.andExpect(jsonPath("$.user.email").value("ada@example.com"))
				.andExpect(jsonPath("$.user.name").value("Ada"));
	}

	@Test
	void loginWithFormUrlEncodedReturnsTokenAndUser() throws Exception {
		mockMvc.perform(post("/api/login")
						.header(API_VERSION, "1")
						.contentType(MediaType.APPLICATION_FORM_URLENCODED)
						.param("email", "ada@example.com")
						.param("password", "secret"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isString())
				.andExpect(jsonPath("$.user.email").value("ada@example.com"))
				.andExpect(jsonPath("$.user.name").value("Ada"));
	}

	@Test
	void loginWrongPasswordReturns422() throws Exception {
		mockMvc.perform(post("/api/login")
						.header(API_VERSION, "1")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"ada@example.com\",\"password\":\"wrong\"}"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.errors.email[0]").value("The provided credentials are incorrect."));
	}
}
```

- [ ] **Step 2: Run tests**

Run: `./gradlew test --no-daemon`  
Expected: BUILD SUCCESSFUL (all tests, including `AuthLoginIT`)

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/authspring/api/AuthLoginIT.java
git commit -m "test(auth): login JSON, form, and invalid credentials"
```

---

## Self-review

1. **Spec coverage:** Single route **`POST /api/login`** (JSON + form), success payload, 422 error shape, persistence of token hash — each maps to Tasks 3–8. **Out-of-scope** items listed explicitly.
2. **Placeholder scan:** No TBD/TODO steps; code blocks are complete.
3. **Type consistency:** `LoginResponse` uses `AuthUserResponse`; `JwtService.createToken` takes `com.challenges.api.model.User`; `PersonalAccessToken` columns match **`V11`**.

**Flyway / DB note:** Changing **`V11`** after it has run in an environment will cause checksum errors — this plan **does not** change **`V11`**. Fresh DBs run migrations 1–11 as today.

---

**Plan complete and saved to `docs/superpowers/plans/2026-04-19-02-login-api-only.md`. Two execution options:**

**1. Subagent-Driven (recommended)** — dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — execute tasks in this session using executing-plans, batch execution with checkpoints.

**Which approach?**
