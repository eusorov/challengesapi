# Register API only (authspring → challengesapi) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port **only** **`POST /api/register`** from `/Users/evgenyusorov/workspace/java/authspring/app/src/main/java/com/authspring/api/` into **challengesapi**: form / multipart fields `name`, `email`, `password`, `password_confirmation`; **200** with `{ "message", "token", "user" }`; duplicate email **422** `ProblemDetail` with `errors.email`. **Do not** add verify-email, forgot/reset password, or any other authspring routes.

**Architecture:** Add **`RegisterController`**, **`RegisterService`**, **`RegistrationOutcome`**, **`RegisterRequest`**, **`RegisterResponse`** under **`com.authspring.api`**, mirroring the sibling project. Use **`com.challenges.api.model.User`**, **`com.challenges.api.repo.UserRepository`**, existing **`JwtService`**, **`PersonalAccessTokenService`**, and **`PasswordEncoder`**. Success payload **`user`** must use **`AuthUserResponse`** (same shape as login), not authspring’s `UserResponse`. New users get **`User.DEFAULT_ROLE`** (`ROLE_USER`) — **do not** hardcode `"user"` from authspring’s `RegisterService`. Rely on **`@PrePersist`** on `User` for `created_at` / `updated_at` (no `setCreatedAt` on `User` in challengesapi). **Email** lookup and storage: **trim + `toLowerCase(Locale.ROOT)`** before `findByEmail` and before `save`, matching authspring.

**Tech stack:** Java 25, Spring Boot 4.x, Spring Security (`PasswordEncoder`), JJWT, JPA, MockMvc, JUnit 5, Mockito.

**Source files (read-only reference):**
- `authspring/.../web/RegisterController.java`
- `authspring/.../service/RegisterService.java`
- `authspring/.../service/RegistrationOutcome.java`
- `authspring/.../web/dto/RegisterRequest.java`
- `authspring/.../web/dto/RegisterResponse.java` (adapt `user` type → **`AuthUserResponse`**)
- `authspring/.../AuthRegisterIT.java` (adapt packages, DB setup, `User.DEFAULT_ROLE` seed)

---

## File map (create / modify)

| Path | Responsibility |
|------|----------------|
| `src/main/java/com/authspring/api/web/dto/RegisterRequest.java` | Same validation as authspring (`@AssertTrue` password match). |
| `src/main/java/com/authspring/api/web/dto/RegisterResponse.java` | `record RegisterResponse(String message, String token, AuthUserResponse user)`. |
| `src/main/java/com/authspring/api/service/RegistrationOutcome.java` | Sealed interface + `Registered` / `EmailAlreadyTaken`. |
| `src/main/java/com/authspring/api/service/RegisterService.java` | Register + JWT + `recordLoginToken`; imports **`com.challenges.api.*`**. |
| `src/main/java/com/authspring/api/web/RegisterController.java` | `POST /api/register`, form + multipart only (no JSON body). |
| `src/test/java/com/authspring/api/AuthRegisterIT.java` | Integration tests: success + duplicate email + optional login smoke; **`@SpringBootTest(classes = ChallengesApiApplication.class)`**. |
| `src/test/java/com/authspring/api/service/RegisterServiceTest.java` | Unit tests: duplicate email, success path (mocked repos/services). |

**Out of scope:** Email sending, verification links, JSON `POST /register`, `UserController` (authspring), any new Flyway migrations (users table already supports registration).

---

### Task 1: `RegisterRequest`

**Files:**
- Create: `src/main/java/com/authspring/api/web/dto/RegisterRequest.java`

- [ ] **Step 1: Add DTO**

```java
package com.authspring.api.web.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registration body: {@code name}, {@code email}, {@code password}, {@code password_confirmation}.
 */
public record RegisterRequest(
		@NotBlank @Size(max = 255) String name,
		@NotBlank @Email @Size(max = 255) String email,
		@NotBlank @Size(min = 8, message = "The password must be at least 8 characters.") String password,
		@NotBlank String password_confirmation) {

	@AssertTrue(message = "The password field confirmation does not match.")
	public boolean isPasswordMatching() {
		return password != null && password.equals(password_confirmation);
	}
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileJava --no-daemon`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/authspring/api/web/dto/RegisterRequest.java
git commit -m "feat(auth): add RegisterRequest DTO"
```

---

### Task 2: `RegisterResponse` + `RegistrationOutcome`

**Files:**
- Create: `src/main/java/com/authspring/api/web/dto/RegisterResponse.java`
- Create: `src/main/java/com/authspring/api/service/RegistrationOutcome.java`

- [ ] **Step 1: `RegisterResponse`**

```java
package com.authspring.api.web.dto;

public record RegisterResponse(String message, String token, AuthUserResponse user) {}
```

- [ ] **Step 2: `RegistrationOutcome`**

```java
package com.authspring.api.service;

import com.authspring.api.web.dto.RegisterResponse;

public sealed interface RegistrationOutcome permits RegistrationOutcome.Registered, RegistrationOutcome.EmailAlreadyTaken {

	record Registered(RegisterResponse response) implements RegistrationOutcome {}

	record EmailAlreadyTaken() implements RegistrationOutcome {}
}
```

- [ ] **Step 3: Compile**

Run: `./gradlew compileJava --no-daemon`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/authspring/api/web/dto/RegisterResponse.java \
  src/main/java/com/authspring/api/service/RegistrationOutcome.java
git commit -m "feat(auth): RegisterResponse and RegistrationOutcome"
```

---

### Task 3: `RegisterService`

**Files:**
- Create: `src/main/java/com/authspring/api/service/RegisterService.java`

- [ ] **Step 1: Implement service**

```java
package com.authspring.api.service;

import static com.challenges.api.model.User.DEFAULT_ROLE;

import com.authspring.api.security.JwtService;
import com.authspring.api.web.dto.AuthUserResponse;
import com.authspring.api.web.dto.RegisterRequest;
import com.authspring.api.web.dto.RegisterResponse;
import com.challenges.api.model.User;
import com.challenges.api.repo.UserRepository;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisterService {

	public static final String SUCCESS_MESSAGE =
			"User registered successfully. Please check your email to verify your account.";

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final PersonalAccessTokenService personalAccessTokenService;

	public RegisterService(
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
	public RegistrationOutcome register(RegisterRequest request) {
		String email = request.email().trim().toLowerCase(Locale.ROOT);
		if (userRepository.findByEmail(email).isPresent()) {
			return new RegistrationOutcome.EmailAlreadyTaken();
		}
		User user = new User(
				request.name().trim(),
				email,
				passwordEncoder.encode(request.password()),
				DEFAULT_ROLE);
		userRepository.save(user);
		String token = jwtService.createToken(user);
		personalAccessTokenService.recordLoginToken(user, token);
		RegisterResponse response =
				new RegisterResponse(SUCCESS_MESSAGE, token, AuthUserResponse.fromEntity(user));
		return new RegistrationOutcome.Registered(response);
	}
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileJava --no-daemon`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/authspring/api/service/RegisterService.java
git commit -m "feat(auth): RegisterService"
```

---

### Task 4: `RegisterController`

**Files:**
- Create: `src/main/java/com/authspring/api/web/RegisterController.java`

- [ ] **Step 1: Add controller**

```java
package com.authspring.api.web;

import com.authspring.api.service.RegisterService;
import com.authspring.api.service.RegistrationOutcome;
import com.authspring.api.web.dto.RegisterRequest;
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
public class RegisterController {

	private final RegisterService registerService;

	public RegisterController(RegisterService registerService) {
		this.registerService = registerService;
	}

	@PostMapping(
			value = "/register",
			consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
	public ResponseEntity<Object> store(@Valid @ModelAttribute RegisterRequest request) {
		return switch (registerService.register(request)) {
			case RegistrationOutcome.Registered(var response) -> ResponseEntity.ok(response);
			case RegistrationOutcome.EmailAlreadyTaken() -> ResponseEntity.status(HttpStatusCode.valueOf(422))
					.body(duplicateEmailProblem());
		};
	}

	private static ProblemDetail duplicateEmailProblem() {
		String msg = "The email has already been taken.";
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(422), msg);
		pd.setTitle("Registration failed");
		pd.setProperty("message", "The given data was invalid.");
		pd.setProperty("errors", Map.of("email", List.of(msg)));
		return pd;
	}
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileJava --no-daemon`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/authspring/api/web/RegisterController.java
git commit -m "feat(auth): POST /api/register"
```

---

### Task 5: `RegisterServiceTest` (unit)

**Files:**
- Create: `src/test/java/com/authspring/api/service/RegisterServiceTest.java`

- [ ] **Step 1: Add tests**

```java
package com.authspring.api.service;

import static com.authspring.api.service.RegisterService.SUCCESS_MESSAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.authspring.api.security.JwtService;
import com.authspring.api.web.dto.RegisterRequest;
import com.authspring.api.web.dto.RegisterResponse;
import com.challenges.api.model.User;
import com.challenges.api.repo.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class RegisterServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private JwtService jwtService;

	@Mock
	private PersonalAccessTokenService personalAccessTokenService;

	@InjectMocks
	private RegisterService registerService;

	@Test
	void register_returnsEmailAlreadyTaken_whenEmailExists() {
		when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(new User("X", "ada@example.com", "h", User.DEFAULT_ROLE)));

		RegistrationOutcome outcome =
				registerService.register(new RegisterRequest("Ada", "ada@example.com", "password12", "password12"));

		assertThat(outcome).isInstanceOf(RegistrationOutcome.EmailAlreadyTaken.class);
		verify(userRepository, never()).save(any());
		verify(jwtService, never()).createToken(any());
	}

	@Test
	void register_savesUserIssuesJwtAndRecordsPat_whenEmailFree() {
		when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.empty());
		when(passwordEncoder.encode("password12")).thenReturn("ENC");
		when(jwtService.createToken(any(User.class))).thenReturn("jwt");

		RegistrationOutcome outcome =
				registerService.register(new RegisterRequest("Ada", "Ada@Example.com", "password12", "password12"));

		assertThat(outcome).isInstanceOf(RegistrationOutcome.Registered.class);
		RegisterResponse res = ((RegistrationOutcome.Registered) outcome).response();
		assertThat(res.message()).isEqualTo(SUCCESS_MESSAGE);
		assertThat(res.token()).isEqualTo("jwt");
		assertThat(res.user().email()).isEqualTo("ada@example.com");
		assertThat(res.user().name()).isEqualTo("Ada");

		ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(userCaptor.capture());
		User saved = userCaptor.getValue();
		assertThat(saved.getEmail()).isEqualTo("ada@example.com");
		assertThat(saved.getPassword()).isEqualTo("ENC");

		verify(personalAccessTokenService).recordLoginToken(any(User.class), org.mockito.Mockito.eq("jwt"));
	}
}
```

- [ ] **Step 2: Run unit tests**

Run: `./gradlew test --no-daemon --tests 'com.authspring.api.service.RegisterServiceTest'`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/authspring/api/service/RegisterServiceTest.java
git commit -m "test(auth): RegisterService unit tests"
```

---

### Task 6: `AuthRegisterIT` (integration)

**Files:**
- Create: `src/test/java/com/authspring/api/AuthRegisterIT.java`

- [ ] **Step 1: Add IT**

```java
package com.authspring.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.authspring.api.service.RegisterService;
import com.challenges.api.ChallengesApiApplication;
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

@SpringBootTest(classes = ChallengesApiApplication.class)
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class AuthRegisterIT {

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
	}

	@Test
	void registerReturnsMessageAndCanLogin() throws Exception {
		mockMvc.perform(multipart("/api/register")
						.param("name", "Ada")
						.param("email", "Ada@Example.com")
						.param("password", "newsecret12")
						.param("password_confirmation", "newsecret12")
						.header(API_VERSION, "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message")
						.value(RegisterService.SUCCESS_MESSAGE))
				.andExpect(jsonPath("$.token").isString())
				.andExpect(jsonPath("$.user.email").value("ada@example.com"))
				.andExpect(jsonPath("$.user.name").value("Ada"));

		mockMvc.perform(post("/api/login")
						.header(API_VERSION, "1")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"ada@example.com\",\"password\":\"newsecret12\"}"))
				.andExpect(status().isOk());
	}

	@Test
	void registerDuplicateEmailReturns422() throws Exception {
		userRepository.save(
				new User("Ada", "ada@example.com", passwordEncoder.encode("secret"), User.DEFAULT_ROLE));

		mockMvc.perform(multipart("/api/register")
						.param("name", "Bob")
						.param("email", "ada@example.com")
						.param("password", "newsecret12")
						.param("password_confirmation", "newsecret12")
						.header(API_VERSION, "1"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.errors.email[0]").value("The email has already been taken."));
	}
}
```

- [ ] **Step 2: Run full test suite**

Requires Postgres **`challengestest`** (or your test datasource). If Flyway checksums drift after editing migrations, reset the test schema (see prior project notes), then:

Run: `./gradlew test --no-daemon`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/authspring/api/AuthRegisterIT.java
git commit -m "test(auth): AuthRegisterIT"
```

---

## Self-review

1. **Spec coverage:** Form/multipart register, success JSON, duplicate 422, email normalization, JWT + PAT — covered. Explicitly excludes other authspring endpoints and email delivery.
2. **Placeholder scan:** No TBD/TODO; code blocks complete.
3. **Type consistency:** `RegisterResponse.user` is **`AuthUserResponse`** everywhere; `User.DEFAULT_ROLE` matches **`UserService`**; `RegistrationOutcome` matches controller `switch`.

---

**Plan complete and saved to `docs/superpowers/plans/2026-04-19-04-register-api-only.md`. Two execution options:**

**1. Subagent-Driven (recommended)** — dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — execute tasks in this session using executing-plans, batch execution with checkpoints.

**Which approach?**
