# Reset password API only (authspring → challengesapi) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port **only** `POST /api/reset-password` from `/Users/evgenyusorov/workspace/java/authspring/app/src/main/java/com/authspring/` into **challengesapi**: multipart or form body with `token`, `email`, `password`, `confirmed`; **200** `{"status":"Your password has been reset."}`; **422** `ProblemDetail` with `errors.email[]` for unknown user or invalid token. **Do not** add `/api/forgot-password`, mail senders, or any other authspring routes.

**Architecture:** Add **`PasswordResetService`**, **`PasswordResetOutcome`** (sealed interface), **`ResetPasswordRequest`**, and **`ResetPasswordController`** under **`com.authspring.api`**, wired to **`com.challenges.api.model.User`**, **`com.challenges.api.repo.UserRepository`**, and existing **`com.challenges.api.repo.PasswordResetTokenRepository`** / **`PasswordResetToken`**. Token verification matches authspring: DB row stores **BCrypt hash** of the **plain** reset token; request sends the **plain** token; **`PasswordEncoder.matches(plain, hash)`**. **`SecurityConfig`** must **`permitAll`** for **`POST /api/reset-password`** (today `/api/**` requires authentication). Optionally add the same path to **`JwtAuthenticationFilter`**’s early bypass list next to `/api/login` and `/api/register` for consistency (not strictly required if clients send no `Authorization` header).

**Tech stack:** Java 25, Spring Boot 4.x, Spring Security, Hibernate, JUnit 5, MockMvc, Mockito, AssertJ.

**Source files (sibling repo, read-only):**
- `authspring/app/src/main/java/com/authspring/api/web/ResetPasswordController.java`
- `authspring/app/src/main/java/com/authspring/api/service/PasswordResetService.java`
- `authspring/app/src/main/java/com/authspring/api/service/PasswordResetOutcome.java`
- `authspring/app/src/main/java/com/authspring/api/web/dto/ResetPasswordRequest.java`
- `authspring/app/src/test/java/com/authspring/api/AuthResetPasswordIT.java`

---

## Preconditions (already in challengesapi)

| Item | Location |
|------|----------|
| Flyway `password_reset_tokens` | `src/main/resources/db/migration/V10__password_reset_tokens.sql` (`email` PK, `token`, `created_at` timestamptz NOT NULL) |
| JPA `PasswordResetToken` | `src/main/java/com/challenges/api/model/PasswordResetToken.java` |
| `PasswordResetTokenRepository` | `src/main/java/com/challenges/api/repo/PasswordResetTokenRepository.java` |
| `User` has `setPassword`, `setRememberToken`, `setUpdatedAt` | `src/main/java/com/challenges/api/model/User.java` |
| `UserRepository.findByEmail` | `src/main/java/com/challenges/api/repo/UserRepository.java` |

**Out of scope:** `ForgotPasswordController`, `PasswordResetLinkService`, `PasswordResetEmailSender`, `PasswordResetMailProperties`, JWT “password reset flow” tokens (`JwtService.createPasswordResetFlowToken` does not exist in challengesapi and is **not** required for this endpoint).

---

## File map

| Path | Responsibility |
|------|----------------|
| `src/main/java/com/authspring/api/service/PasswordResetOutcome.java` | Sealed outcome: `Success`, `UserNotFound`, `InvalidToken`. |
| `src/main/java/com/authspring/api/web/dto/ResetPasswordRequest.java` | Validation: token, email, password min 8, `confirmed` must match `password`. |
| `src/main/java/com/authspring/api/service/PasswordResetService.java` | Normalize email; load user; load token row by email; BCrypt match; update user password + remember token + `updatedAt`; delete token row. |
| `src/main/java/com/authspring/api/web/ResetPasswordController.java` | `POST /api/reset-password` consumes `APPLICATION_FORM_URLENCODED` + `MULTIPART_FORM_DATA` only (same as authspring — **no JSON**). |
| `src/main/java/com/challenges/api/config/SecurityConfig.java` | Permit `POST /api/reset-password`. |
| `src/main/java/com/authspring/api/security/JwtAuthenticationFilter.java` | (Optional) Bypass `POST /api/reset-password` like login/register. |
| `src/test/java/com/authspring/api/service/PasswordResetServiceTest.java` | Unit tests with mocks. |
| `src/test/java/com/authspring/api/AuthResetPasswordIT.java` | Integration tests: success + login, unknown user 422, wrong token 422. |

---

### Task 1: `PasswordResetOutcome`

**Files:**
- Create: `src/main/java/com/authspring/api/service/PasswordResetOutcome.java`

- [ ] **Step 1: Add sealed interface**

```java
package com.authspring.api.service;

public sealed interface PasswordResetOutcome permits PasswordResetOutcome.Success, PasswordResetOutcome.UserNotFound,
		PasswordResetOutcome.InvalidToken {

	record Success() implements PasswordResetOutcome {}

	record UserNotFound() implements PasswordResetOutcome {}

	record InvalidToken() implements PasswordResetOutcome {}
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileJava --no-daemon`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/authspring/api/service/PasswordResetOutcome.java
git commit -m "feat(auth): PasswordResetOutcome for reset-password flow"
```

---

### Task 2: `ResetPasswordRequest`

**Files:**
- Create: `src/main/java/com/authspring/api/web/dto/ResetPasswordRequest.java`

- [ ] **Step 1: Add record**

```java
package com.authspring.api.web.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Reset-password body: {@code token}, {@code email}, {@code password}, {@code confirmed} (password confirmation).
 */
public record ResetPasswordRequest(
		@NotBlank String token,
		@NotBlank @Email @Size(max = 255) String email,
		@NotBlank @Size(min = 8, message = "The password must be at least 8 characters.") String password,
		@NotBlank String confirmed) {

	@AssertTrue(message = "The password field confirmation does not match.")
	public boolean isPasswordMatching() {
		return password != null && password.equals(confirmed);
	}
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileJava --no-daemon`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/authspring/api/web/dto/ResetPasswordRequest.java
git commit -m "feat(auth): ResetPasswordRequest DTO"
```

---

### Task 3: `PasswordResetService`

**Files:**
- Create: `src/main/java/com/authspring/api/service/PasswordResetService.java`

- [ ] **Step 1: Add service** (imports use **`com.challenges.api`** persistence types)

```java
package com.authspring.api.service;

import com.authspring.api.web.dto.ResetPasswordRequest;
import com.challenges.api.model.User;
import com.challenges.api.repo.PasswordResetTokenRepository;
import com.challenges.api.repo.UserRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {

	private static final SecureRandom SECURE_RANDOM = new SecureRandom();
	private static final String REMEMBER_ALPHANUM =
			"0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

	private final UserRepository userRepository;
	private final PasswordResetTokenRepository passwordResetTokenRepository;
	private final PasswordEncoder passwordEncoder;

	public PasswordResetService(
			UserRepository userRepository,
			PasswordResetTokenRepository passwordResetTokenRepository,
			PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordResetTokenRepository = passwordResetTokenRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public PasswordResetOutcome reset(ResetPasswordRequest request) {
		String email = request.email().trim().toLowerCase(Locale.ROOT);
		User user = userRepository.findByEmail(email).orElse(null);
		if (user == null) {
			return new PasswordResetOutcome.UserNotFound();
		}
		var tokenRow = passwordResetTokenRepository.findById(email);
		if (tokenRow.isEmpty() || !passwordEncoder.matches(request.token(), tokenRow.get().getToken())) {
			return new PasswordResetOutcome.InvalidToken();
		}
		user.setPassword(passwordEncoder.encode(request.password()));
		user.setRememberToken(randomRememberToken());
		user.setUpdatedAt(Instant.now());
		userRepository.save(user);
		passwordResetTokenRepository.deleteById(email);
		return new PasswordResetOutcome.Success();
	}

	private static String randomRememberToken() {
		StringBuilder sb = new StringBuilder(60);
		for (int i = 0; i < 60; i++) {
			sb.append(REMEMBER_ALPHANUM.charAt(SECURE_RANDOM.nextInt(REMEMBER_ALPHANUM.length())));
		}
		return sb.toString();
	}
}
```

**Note:** `PasswordResetToken` entity must expose **`getToken()`** returning the stored hash (already does). No `PasswordResetLinkService` in this plan — integration tests **insert** a row with `passwordEncoder.encode(plainToken)` manually.

- [ ] **Step 2: Compile**

Run: `./gradlew compileJava --no-daemon`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/authspring/api/service/PasswordResetService.java
git commit -m "feat(auth): PasswordResetService"
```

---

### Task 4: `ResetPasswordController`

**Files:**
- Create: `src/main/java/com/authspring/api/web/ResetPasswordController.java`

- [ ] **Step 1: Add controller**

```java
package com.authspring.api.web;

import com.authspring.api.service.PasswordResetOutcome;
import com.authspring.api.service.PasswordResetService;
import com.authspring.api.web.dto.ResetPasswordRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api", version = "1")
public class ResetPasswordController {

	private final PasswordResetService passwordResetService;

	public ResetPasswordController(PasswordResetService passwordResetService) {
		this.passwordResetService = passwordResetService;
	}

	@PostMapping(
			value = "/reset-password",
			consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
	public ResponseEntity<Object> store(@Valid @ModelAttribute ResetPasswordRequest request) {
		return switch (passwordResetService.reset(request)) {
			case PasswordResetOutcome.Success() -> ResponseEntity.ok(Map.of("status", "Your password has been reset."));
			case PasswordResetOutcome.UserNotFound() -> ResponseEntity.status(HttpStatusCode.valueOf(422))
					.body(resetFailedProblem("We can't find a user with that email address."));
			case PasswordResetOutcome.InvalidToken() -> ResponseEntity.status(HttpStatusCode.valueOf(422))
					.body(resetFailedProblem("This password reset token is invalid."));
		};
	}

	private static ProblemDetail resetFailedProblem(String emailMessage) {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(422), emailMessage);
		pd.setTitle("Password reset failed");
		pd.setProperty("message", "The given data was invalid.");
		pd.setProperty("errors", Map.of("email", List.of(emailMessage)));
		return pd;
	}
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileJava --no-daemon`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/authspring/api/web/ResetPasswordController.java
git commit -m "feat(auth): POST /api/reset-password"
```

---

### Task 5: Security — permit unauthenticated reset

**Files:**
- Modify: `src/main/java/com/challenges/api/config/SecurityConfig.java`

- [ ] **Step 1: Allow POST `/api/reset-password`**

In `requestMatchers` for `HttpMethod.POST`, extend the line that permits login/register so it includes reset-password, for example:

```java
.requestMatchers(HttpMethod.POST, "/api/login", "/api/register", "/api/reset-password").permitAll()
```

(Exact formatting may match your file; keep one line or chain consistently.)

- [ ] **Step 2 (optional): `JwtAuthenticationFilter`**

In `src/main/java/com/authspring/api/security/JwtAuthenticationFilter.java`, extend the bypass condition:

```java
if (("POST".equalsIgnoreCase(request.getMethod()))
		&& ("/api/login".equals(path) || "/api/register".equals(path) || "/api/reset-password".equals(path))) {
```

- [ ] **Step 3: Compile**

Run: `./gradlew compileJava --no-daemon`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/challenges/api/config/SecurityConfig.java \
  src/main/java/com/authspring/api/security/JwtAuthenticationFilter.java
git commit -m "fix(auth): permit POST /api/reset-password without JWT"
```

---

### Task 6: Unit test — `PasswordResetServiceTest`

**Files:**
- Create: `src/test/java/com/authspring/api/service/PasswordResetServiceTest.java`

- [ ] **Step 1: Add tests**

```java
package com.authspring.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.authspring.api.web.dto.ResetPasswordRequest;
import com.challenges.api.model.PasswordResetToken;
import com.challenges.api.model.User;
import com.challenges.api.repo.PasswordResetTokenRepository;
import com.challenges.api.repo.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordResetTokenRepository passwordResetTokenRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@InjectMocks
	private PasswordResetService passwordResetService;

	@Test
	void reset_returnsUserNotFound_whenEmailUnknown() {
		when(userRepository.findByEmail("a@b.c")).thenReturn(Optional.empty());

		PasswordResetOutcome outcome = passwordResetService.reset(
				new ResetPasswordRequest("tok", "a@b.c", "newpass12", "newpass12"));

		assertThat(outcome).isInstanceOf(PasswordResetOutcome.UserNotFound.class);
		verify(passwordResetTokenRepository, never()).findById(any());
	}

	@Test
	void reset_returnsInvalidToken_whenRowMissingOrMismatch() {
		User user = new User("N", "a@b.c", "oldhash", "USER");
		when(userRepository.findByEmail("a@b.c")).thenReturn(Optional.of(user));
		when(passwordResetTokenRepository.findById("a@b.c")).thenReturn(Optional.empty());

		assertThat(passwordResetService.reset(new ResetPasswordRequest("t", "a@b.c", "newpass12", "newpass12")))
				.isInstanceOf(PasswordResetOutcome.InvalidToken.class);

		when(passwordResetTokenRepository.findById("a@b.c"))
				.thenReturn(Optional.of(new PasswordResetToken("a@b.c", "bcrypt-hash", Instant.now())));
		when(passwordEncoder.matches("wrong", "bcrypt-hash")).thenReturn(false);

		assertThat(passwordResetService.reset(new ResetPasswordRequest("wrong", "a@b.c", "newpass12", "newpass12")))
				.isInstanceOf(PasswordResetOutcome.InvalidToken.class);
	}

	@Test
	void reset_success_updatesPasswordDeletesToken() {
		User user = new User("N", "a@b.c", "oldhash", "USER");
		when(userRepository.findByEmail("a@b.c")).thenReturn(Optional.of(user));
		when(passwordResetTokenRepository.findById("a@b.c"))
				.thenReturn(Optional.of(new PasswordResetToken("a@b.c", "bcrypt-hash", Instant.now())));
		when(passwordEncoder.matches("plain-tok", "bcrypt-hash")).thenReturn(true);
		when(passwordEncoder.encode("newpass12")).thenReturn("new-hash");

		PasswordResetOutcome outcome = passwordResetService.reset(
				new ResetPasswordRequest("plain-tok", "a@b.c", "newpass12", "newpass12"));

		assertThat(outcome).isInstanceOf(PasswordResetOutcome.Success.class);
		ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(userCaptor.capture());
		assertThat(userCaptor.getValue().getPassword()).isEqualTo("new-hash");
		assertThat(userCaptor.getValue().getRememberToken()).isNotNull().hasSize(60);
		verify(passwordResetTokenRepository).deleteById("a@b.c");
	}
}
```

- [ ] **Step 2: Run unit test only**

Run: `./gradlew test --no-daemon --tests 'com.authspring.api.service.PasswordResetServiceTest'`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/authspring/api/service/PasswordResetServiceTest.java
git commit -m "test(auth): PasswordResetService unit tests"
```

---

### Task 7: Integration test — `AuthResetPasswordIT`

**Files:**
- Create: `src/test/java/com/authspring/api/AuthResetPasswordIT.java`

- [ ] **Step 1: Add IT** (match **challengesapi** style: `SpringBootTest(classes = ChallengesApiApplication.class)`, `Replace.NONE`, **no** Testcontainers)

```java
package com.authspring.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.challenges.api.ChallengesApiApplication;
import com.challenges.api.model.PasswordResetToken;
import com.challenges.api.model.User;
import com.challenges.api.repo.PasswordResetTokenRepository;
import com.challenges.api.repo.UserRepository;
import java.time.Instant;
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

@SpringBootTest(classes = ChallengesApiApplication.class)
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class AuthResetPasswordIT {

	private static final String API_VERSION = "API-Version";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordResetTokenRepository passwordResetTokenRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@BeforeEach
	void setUp() {
		passwordResetTokenRepository.deleteAll();
		userRepository.deleteAll();
		User user = new User("Ada", "ada@example.com", passwordEncoder.encode("secret"), "USER");
		userRepository.save(user);
	}

	@Test
	void resetPasswordSuccessAndLoginWithNewPassword() throws Exception {
		String plainToken = "plain-reset-token";
		passwordResetTokenRepository.save(new PasswordResetToken(
				"ada@example.com", passwordEncoder.encode(plainToken), Instant.now()));

		mockMvc.perform(multipart("/api/reset-password")
						.param("token", plainToken)
						.param("email", "ada@example.com")
						.param("password", "newsecret12")
						.param("confirmed", "newsecret12")
						.header(API_VERSION, "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("Your password has been reset."));

		mockMvc.perform(post("/api/login")
						.header(API_VERSION, "1")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"ada@example.com\",\"password\":\"newsecret12\"}"))
				.andExpect(status().isOk());
	}

	@Test
	void resetPasswordUnknownUserReturns422() throws Exception {
		mockMvc.perform(multipart("/api/reset-password")
						.param("token", "t")
						.param("email", "nobody@example.com")
						.param("password", "newsecret12")
						.param("confirmed", "newsecret12")
						.header(API_VERSION, "1"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.errors.email[0]")
						.value("We can't find a user with that email address."));
	}

	@Test
	void resetPasswordInvalidTokenReturns422() throws Exception {
		passwordResetTokenRepository.save(new PasswordResetToken(
				"ada@example.com", passwordEncoder.encode("expected-token"), Instant.now()));

		mockMvc.perform(multipart("/api/reset-password")
						.param("token", "wrong-token")
						.param("email", "ada@example.com")
						.param("password", "newsecret12")
						.param("confirmed", "newsecret12")
						.header(API_VERSION, "1"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.errors.email[0]").value("This password reset token is invalid."));
	}
}
```

**Note:** Align `User` constructor **role** string with your app (`"USER"` vs `"user"`) — must match what `RegisterService` / Flyway expect.

- [ ] **Step 2: Run full test suite**

Requires Postgres **`challengestest`** and Flyway clean if migrations changed:

```bash
docker exec challenges-postgres psql -U challenges -d challengestest -c \
  "DROP SCHEMA public CASCADE; CREATE SCHEMA public; GRANT ALL ON SCHEMA public TO challenges; GRANT ALL ON SCHEMA public TO public;"
./gradlew test --no-daemon
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/authspring/api/AuthResetPasswordIT.java
git commit -m "test(auth): AuthResetPasswordIT"
```

---

## Self-review

1. **Spec coverage:** `POST /api/reset-password` only; success body; 422 shapes; BCrypt token check; user update + token row delete — Tasks 3–7. Forgot-password / email — explicitly out of scope.
2. **Placeholder scan:** No TBD/TODO; code blocks complete.
3. **Type consistency:** `ResetPasswordRequest` field names match `@ModelAttribute` params (`confirmed`); `User` / repos use `com.challenges.api` package; role string in IT must match project convention.

---

**Plan complete and saved to `docs/superpowers/plans/2026-04-19-06-reset-password-api-only.md`. Two execution options:**

**1. Subagent-Driven (recommended)** — dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — execute tasks in this session using executing-plans, batch execution with checkpoints.

**Which approach?**
