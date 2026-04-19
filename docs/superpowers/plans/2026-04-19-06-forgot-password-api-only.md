# Forgot password API only Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port **only** `POST /api/forgot-password` from `/Users/evgenyusorov/workspace/java/authspring/app/src/main/java/com/authspring/api/web/ForgotPasswordController.java` into **challengesapi**: form-urlencoded / multipart body with **`email`**, **200** `{"status":"We have e-mailed your password reset link!"}`, **422** `ProblemDetail` with `errors.email` when the user does not exist. **Do not** add or change reset-password, login, register, verify-email, or any other HTTP routes beyond this one.

**Architecture:** Mirror authspring’s flow: **`ForgotPasswordController`** → **`PasswordResetLinkService`** (normalize email, load **`com.challenges.api.model.User`**, random 64-char plain token, BCrypt-hash into **`password_reset_tokens`**, call **`PasswordResetEmailSender`**) → **`JwtService.createPasswordResetFlowToken`** for the `api_token` query param in the frontend URL. Reuse existing **`PasswordResetToken`** / **`PasswordResetTokenRepository`** (**`V10__password_reset_tokens.sql`**), **`UserRepository.findByEmail`**, **`JavaMailSender`**, **`app.mail.password-reset`** in **`application.yml`**, and **`FrontendProperties`**. Register **`PasswordResetMailProperties`** on the main application.

**Tech stack:** Java 25, Spring Boot 4.0.x, Spring Security, Spring Mail, JJWT, JUnit 5, Mockito, MockMvc.

**Source (read-only):** `authspring/.../ForgotPasswordController.java`, `PasswordResetLinkService.java`, `PasswordResetEmailSender.java`, `SendPasswordResetLinkOutcome.java`, `ForgotPasswordRequest.java`, `PasswordResetMailProperties.java`; JWT helper in `authspring/.../JwtService.java#createPasswordResetFlowToken`.

---

## File map (create / modify)

| Path | Responsibility |
|------|----------------|
| `src/main/java/com/authspring/api/security/JwtService.java` | Add **`createPasswordResetFlowToken(User)`** using **`jwt.passwordResetExpirationMs`** and claim **`purpose` = `"password_reset"`** (same as authspring). |
| `src/main/java/com/authspring/api/config/PasswordResetMailProperties.java` | **Create:** `@ConfigurationProperties(prefix = "app.mail.password-reset")` record (copy from authspring). |
| `src/main/java/com/challenges/api/ChallengesApiApplication.java` | Add **`PasswordResetMailProperties.class`** to **`@EnableConfigurationProperties`**. |
| `src/main/java/com/authspring/api/service/SendPasswordResetLinkOutcome.java` | **Create:** sealed interface `Sent` / `UserNotFound`. |
| `src/main/java/com/authspring/api/web/dto/ForgotPasswordRequest.java` | **Create:** `record` with `@NotBlank @Email @Size(max = 255) String email`. |
| `src/main/java/com/authspring/api/service/PasswordResetEmailSender.java` | **Create:** build Laravel-style reset URL + plain text body; imports **`com.challenges.api.model.User`**. |
| `src/main/java/com/authspring/api/service/PasswordResetLinkService.java` | **Create:** transactional send flow; imports **`com.challenges.api`**. |
| `src/main/java/com/authspring/api/web/ForgotPasswordController.java` | **Create:** `POST /forgot-password` **only** `FORM_URLENCODED` + `MULTIPART_FORM_DATA` (no JSON mapping in authspring). |
| `src/main/java/com/challenges/api/config/SecurityConfig.java` | Permit **`POST /api/forgot-password`** (same matcher list as login/register/reset-password). |
| `src/test/java/com/authspring/api/security/JwtServiceTest.java` | Add test: password-reset JWT has **`purpose`** claim and validates. |
| `src/test/java/com/authspring/api/service/PasswordResetLinkServiceTest.java` | **Create:** Mockito unit tests (unknown email; known user saves token + sends mail). |
| `src/test/java/com/authspring/api/AuthForgotPasswordIT.java` | **Create:** MockMvc IT with **`@SpringBootTest(classes = ChallengesApiApplication.class)`**, **`@Import(MockMailSenderConfig)`**, Postgres **`Replace.NONE`**, **`multipart("/api/forgot-password").param("email", ...)`**. |

**Explicitly out of scope:** `ResetPasswordController`, `PasswordResetService` behavior changes, new Flyway migrations, CORS changes, rate limiting.

---

### Task 1: `JwtService.createPasswordResetFlowToken`

**Files:**
- Modify: `src/main/java/com/authspring/api/security/JwtService.java`

- [x] **Step 1: Add method after `createToken`**

```java
public String createPasswordResetFlowToken(User user) {
	Instant now = Instant.now();
	Instant exp = now.plusMillis(properties.passwordResetExpirationMs());
	return Jwts.builder()
			.id(UUID.randomUUID().toString())
			.subject(user.getId().toString())
			.claim("email", user.getEmail())
			.claim("purpose", "password_reset")
			.issuedAt(Date.from(now))
			.expiration(Date.from(exp))
			.signWith(signingKey)
			.compact();
}
```

- [x] **Step 2: Compile**

Run: `./gradlew compileJava --no-daemon`  
Expected: BUILD SUCCESSFUL

- [x] **Step 3: Commit**

```bash
git add src/main/java/com/authspring/api/security/JwtService.java
git commit -m "feat(auth): JWT for password reset flow"
```

---

### Task 2: `PasswordResetMailProperties` + application binding

**Files:**
- Create: `src/main/java/com/authspring/api/config/PasswordResetMailProperties.java`
- Modify: `src/main/java/com/challenges/api/ChallengesApiApplication.java`

- [x] **Step 1: Add properties record**

Create `src/main/java/com/authspring/api/config/PasswordResetMailProperties.java`:

```java
package com.authspring.api.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mail.password-reset")
public record PasswordResetMailProperties(
		String subject,
		String greetingTemplate,
		List<String> lines,
		String actionLabel,
		List<String> footerLines,
		String salutation,
		int expiryMinutes,
		String fromAddress,
		String fromName) {

	public PasswordResetMailProperties {
		lines = lines == null ? List.of() : List.copyOf(lines);
		footerLines = footerLines == null ? List.of() : List.copyOf(footerLines);
	}
}
```

- [x] **Step 2: Register on main class**

Add `PasswordResetMailProperties.class` to the `@EnableConfigurationProperties({ ... })` array in `ChallengesApiApplication.java`.

- [x] **Step 3: Compile**

Run: `./gradlew compileJava --no-daemon`  
Expected: BUILD SUCCESSFUL

- [x] **Step 4: Commit**

```bash
git add src/main/java/com/authspring/api/config/PasswordResetMailProperties.java \
  src/main/java/com/challenges/api/ChallengesApiApplication.java
git commit -m "feat(auth): bind password reset mail properties"
```

---

### Task 3: `SendPasswordResetLinkOutcome` + `ForgotPasswordRequest`

**Files:**
- Create: `src/main/java/com/authspring/api/service/SendPasswordResetLinkOutcome.java`
- Create: `src/main/java/com/authspring/api/web/dto/ForgotPasswordRequest.java`

- [x] **Step 1: Outcome type**

```java
package com.authspring.api.service;

public sealed interface SendPasswordResetLinkOutcome
		permits SendPasswordResetLinkOutcome.Sent, SendPasswordResetLinkOutcome.UserNotFound {

	record Sent() implements SendPasswordResetLinkOutcome {}

	record UserNotFound() implements SendPasswordResetLinkOutcome {}
}
```

- [x] **Step 2: Request DTO**

```java
package com.authspring.api.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 255) String email) {}
```

- [x] **Step 3: Compile**

Run: `./gradlew compileJava --no-daemon`  
Expected: BUILD SUCCESSFUL

- [x] **Step 4: Commit**

```bash
git add src/main/java/com/authspring/api/service/SendPasswordResetLinkOutcome.java \
  src/main/java/com/authspring/api/web/dto/ForgotPasswordRequest.java
git commit -m "feat(auth): forgot-password request and outcome types"
```

---

### Task 4: `PasswordResetEmailSender`

**Files:**
- Create: `src/main/java/com/authspring/api/service/PasswordResetEmailSender.java`

- [x] **Step 1: Implement sender** (imports **`com.challenges.api.model.User`**)

```java
package com.authspring.api.service;

import com.authspring.api.config.FrontendProperties;
import com.authspring.api.config.PasswordResetMailProperties;
import com.authspring.api.security.JwtService;
import com.challenges.api.model.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetEmailSender {

	private static final Charset MAIL_CHARSET = StandardCharsets.UTF_8;

	private final JavaMailSender mailSender;
	private final JwtService jwtService;
	private final FrontendProperties frontend;
	private final PasswordResetMailProperties mail;

	public PasswordResetEmailSender(
			JavaMailSender mailSender,
			JwtService jwtService,
			FrontendProperties frontend,
			PasswordResetMailProperties mail) {
		this.mailSender = mailSender;
		this.jwtService = jwtService;
		this.frontend = frontend;
		this.mail = mail;
	}

	public void send(User user, String plainToken) throws MessagingException, UnsupportedEncodingException {
		String apiToken = jwtService.createPasswordResetFlowToken(user);
		String url = buildResetUrl(user, plainToken, apiToken);
		String body = buildBody(user, url);

		MimeMessage mime = mailSender.createMimeMessage();
		MimeMessageHelper helper = new MimeMessageHelper(mime, false, MAIL_CHARSET.name());
		helper.setFrom(mail.fromAddress(), mail.fromName());
		helper.setTo(user.getEmail());
		helper.setSubject(mail.subject());
		helper.setText(body, false);
		mailSender.send(mime);
	}

	private String buildResetUrl(User user, String plainToken, String apiToken) {
		String base = frontend.baseUrl().replaceAll("/$", "");
		return base
				+ "/?new_password=1&password_reset_token="
				+ urlEncode(plainToken)
				+ "&email="
				+ urlEncode(user.getEmail())
				+ "&api_token="
				+ urlEncode(apiToken)
				+ "&user_id="
				+ user.getId()
				+ "&user_name="
				+ urlEncode(user.getName());
	}

	private static String urlEncode(String s) {
		return URLEncoder.encode(s, MAIL_CHARSET);
	}

	private String buildBody(User user, String url) {
		String greeting = mail.greetingTemplate().replace("{name}", user.getName());
		StringBuilder sb = new StringBuilder();
		sb.append(greeting).append("\n\n");
		for (String line : mail.lines()) {
			sb.append(line).append("\n\n");
		}
		sb.append(mail.actionLabel()).append(":\n").append(url).append("\n\n");
		for (String footer : mail.footerLines()) {
			String line = footer.replace("{minutes}", String.valueOf(mail.expiryMinutes()));
			sb.append(line).append("\n\n");
		}
		sb.append(mail.salutation());
		return sb.toString();
	}
}
```

- [x] **Step 2: Compile**

Run: `./gradlew compileJava --no-daemon`  
Expected: BUILD SUCCESSFUL

- [x] **Step 3: Commit**

```bash
git add src/main/java/com/authspring/api/service/PasswordResetEmailSender.java
git commit -m "feat(auth): send password reset email with signed URL"
```

---

### Task 5: `PasswordResetLinkService`

**Files:**
- Create: `src/main/java/com/authspring/api/service/PasswordResetLinkService.java`

- [x] **Step 1: Implement service**

```java
package com.authspring.api.service;

import com.authspring.api.web.dto.ForgotPasswordRequest;
import com.challenges.api.model.PasswordResetToken;
import com.challenges.api.model.User;
import com.challenges.api.repo.PasswordResetTokenRepository;
import com.challenges.api.repo.UserRepository;
import jakarta.mail.MessagingException;
import java.io.UnsupportedEncodingException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetLinkService {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final String ALPHANUM =
			"0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

	private final UserRepository userRepository;
	private final PasswordResetTokenRepository passwordResetTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final PasswordResetEmailSender passwordResetEmailSender;

	public PasswordResetLinkService(
			UserRepository userRepository,
			PasswordResetTokenRepository passwordResetTokenRepository,
			PasswordEncoder passwordEncoder,
			PasswordResetEmailSender passwordResetEmailSender) {
		this.userRepository = userRepository;
		this.passwordResetTokenRepository = passwordResetTokenRepository;
		this.passwordEncoder = passwordEncoder;
		this.passwordResetEmailSender = passwordResetEmailSender;
	}

	@Transactional
	public SendPasswordResetLinkOutcome send(ForgotPasswordRequest request) {
		String email = request.email().trim().toLowerCase(Locale.ROOT);
		User user = userRepository.findByEmail(email).orElse(null);
		if (user == null) {
			return new SendPasswordResetLinkOutcome.UserNotFound();
		}
		String plain = randomToken(64);
		String hash = passwordEncoder.encode(plain);
		passwordResetTokenRepository.save(new PasswordResetToken(email, hash, Instant.now()));
		try {
			passwordResetEmailSender.send(user, plain);
		} catch (MessagingException | UnsupportedEncodingException e) {
			throw new IllegalStateException("Failed to send password reset email", e);
		}
		return new SendPasswordResetLinkOutcome.Sent();
	}

	private static String randomToken(int length) {
		StringBuilder sb = new StringBuilder(length);
		for (int i = 0; i < length; i++) {
			sb.append(ALPHANUM.charAt(RANDOM.nextInt(ALPHANUM.length())));
		}
		return sb.toString();
	}
}
```

- [x] **Step 2: Compile**

Run: `./gradlew compileJava --no-daemon`  
Expected: BUILD SUCCESSFUL

- [x] **Step 3: Commit**

```bash
git add src/main/java/com/authspring/api/service/PasswordResetLinkService.java
git commit -m "feat(auth): forgot-password link generation and persistence"
```

---

### Task 6: `ForgotPasswordController`

**Files:**
- Create: `src/main/java/com/authspring/api/web/ForgotPasswordController.java`

- [x] **Step 1: Add controller**

```java
package com.authspring.api.web;

import com.authspring.api.service.PasswordResetLinkService;
import com.authspring.api.service.SendPasswordResetLinkOutcome;
import com.authspring.api.web.dto.ForgotPasswordRequest;
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
public class ForgotPasswordController {

	private final PasswordResetLinkService passwordResetLinkService;

	public ForgotPasswordController(PasswordResetLinkService passwordResetLinkService) {
		this.passwordResetLinkService = passwordResetLinkService;
	}

	@PostMapping(
			value = "/forgot-password",
			consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
	public ResponseEntity<Object> store(@Valid @ModelAttribute ForgotPasswordRequest request) {
		return switch (passwordResetLinkService.send(request)) {
			case SendPasswordResetLinkOutcome.Sent() -> ResponseEntity.ok(
					Map.of("status", "We have e-mailed your password reset link!"));
			case SendPasswordResetLinkOutcome.UserNotFound() -> ResponseEntity.status(HttpStatusCode.valueOf(422))
					.body(userNotFoundProblem());
		};
	}

	private static ProblemDetail userNotFoundProblem() {
		String msg = "We can't find a user with that e-mail address.";
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(422), msg);
		pd.setTitle("Password reset failed");
		pd.setProperty("message", "The given data was invalid.");
		pd.setProperty("errors", Map.of("email", List.of(msg)));
		return pd;
	}
}
```

- [x] **Step 2: Compile**

Run: `./gradlew compileJava --no-daemon`  
Expected: BUILD SUCCESSFUL

- [x] **Step 3: Commit**

```bash
git add src/main/java/com/authspring/api/web/ForgotPasswordController.java
git commit -m "feat(auth): POST /api/forgot-password"
```

---

### Task 7: Security — permit unauthenticated `POST /api/forgot-password`

**Files:**
- Modify: `src/main/java/com/challenges/api/config/SecurityConfig.java`

- [x] **Step 1: Extend permitAll matcher**

Change the line that permits login/register/reset-password to include forgot-password, for example:

```java
.requestMatchers(HttpMethod.POST, "/api/login", "/api/register", "/api/reset-password", "/api/forgot-password")
		.permitAll()
```

- [x] **Step 2: Run tests**

Run: `./gradlew test --no-daemon`  
Expected: BUILD SUCCESSFUL (after integration test from Task 9 exists; until then at least compile)

- [x] **Step 3: Commit**

```bash
git add src/main/java/com/challenges/api/config/SecurityConfig.java
git commit -m "fix(security): allow unauthenticated forgot-password"
```

---

### Task 8: Unit test — `PasswordResetLinkService`

**Files:**
- Create: `src/test/java/com/authspring/api/service/PasswordResetLinkServiceTest.java`

- [x] **Step 1: Add test class** (`com.challenges.api` types)

```java
package com.authspring.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.authspring.api.web.dto.ForgotPasswordRequest;
import com.challenges.api.model.PasswordResetToken;
import com.challenges.api.model.User;
import com.challenges.api.repo.PasswordResetTokenRepository;
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
class PasswordResetLinkServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordResetTokenRepository passwordResetTokenRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private PasswordResetEmailSender passwordResetEmailSender;

	@InjectMocks
	private PasswordResetLinkService passwordResetLinkService;

	@Test
	void unknownEmail_returnsUserNotFound() throws Exception {
		when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.empty());

		var outcome = passwordResetLinkService.send(new ForgotPasswordRequest("A@B.COM"));

		assertInstanceOf(SendPasswordResetLinkOutcome.UserNotFound.class, outcome);
		verify(passwordResetTokenRepository, never()).save(any());
		verify(passwordResetEmailSender, never()).send(any(), any());
	}

	@Test
	void knownUser_savesHashedTokenAndSendsEmail() throws Exception {
		User user = new User("Ada", "ada@example.com", "hash", "user");
		when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(user));
		when(passwordEncoder.encode(any())).thenReturn("hashed-token");

		var outcome = passwordResetLinkService.send(new ForgotPasswordRequest("ada@example.com"));

		assertInstanceOf(SendPasswordResetLinkOutcome.Sent.class, outcome);
		ArgumentCaptor<String> plainCaptor = ArgumentCaptor.forClass(String.class);
		verify(passwordResetEmailSender).send(eq(user), plainCaptor.capture());
		assertEquals(64, plainCaptor.getValue().length());

		ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
		verify(passwordResetTokenRepository).save(tokenCaptor.capture());
		assertEquals("ada@example.com", tokenCaptor.getValue().getEmail());
		assertEquals("hashed-token", tokenCaptor.getValue().getToken());
	}
}
```

- [x] **Step 2: Run test**

Run: `./gradlew test --no-daemon --tests 'com.authspring.api.service.PasswordResetLinkServiceTest'`  
Expected: BUILD SUCCESSFUL

- [x] **Step 3: Commit**

```bash
git add src/test/java/com/authspring/api/service/PasswordResetLinkServiceTest.java
git commit -m "test(auth): PasswordResetLinkService"
```

---

### Task 9: Unit test — `JwtService` password-reset token

**Files:**
- Modify: `src/test/java/com/authspring/api/security/JwtServiceTest.java`

- [x] **Step 1: Add test method**

```java
@Test
void createPasswordResetFlowToken_includesPurposeClaim() {
	long resetMs = 120_000L;
	JwtService svc = new JwtService(new JwtProperties(SECRET_32, 86_400_000L, resetMs));
	User user = mock(User.class);
	when(user.getId()).thenReturn(3L);
	when(user.getEmail()).thenReturn("r@example.com");

	String jwt = svc.createPasswordResetFlowToken(user);

	SecretKey key = Keys.hmacShaKeyFor(SECRET_32.getBytes(StandardCharsets.UTF_8));
	Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(jwt).getPayload();

	assertThat(claims.getSubject()).isEqualTo("3");
	assertThat(claims.get("email", String.class)).isEqualTo("r@example.com");
	assertThat(claims.get("purpose", String.class)).isEqualTo("password_reset");
}
```

Add imports: `io.jsonwebtoken.Claims` (if not present).

- [x] **Step 2: Run test**

Run: `./gradlew test --no-daemon --tests 'com.authspring.api.security.JwtServiceTest'`  
Expected: BUILD SUCCESSFUL

- [x] **Step 3: Commit**

```bash
git add src/test/java/com/authspring/api/security/JwtServiceTest.java
git commit -m "test(auth): password reset JWT claims"
```

---

### Task 10: Integration test — `AuthForgotPasswordIT`

**Files:**
- Create: `src/test/java/com/authspring/api/AuthForgotPasswordIT.java`

- [x] **Step 1: Add IT** (matches authspring behavior; **challengesapi** packages)

```java
package com.authspring.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.challenges.api.ChallengesApiApplication;
import com.challenges.api.model.User;
import com.challenges.api.repo.PasswordResetTokenRepository;
import com.challenges.api.repo.UserRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = ChallengesApiApplication.class)
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AuthForgotPasswordIT.MockMailSenderConfig.class)
@Transactional
class AuthForgotPasswordIT {

	private static final String API_VERSION = "API-Version";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordResetTokenRepository passwordResetTokenRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JavaMailSender javaMailSender;

	@TestConfiguration
	static class MockMailSenderConfig {

		@Bean
		@Primary
		JavaMailSender javaMailSender() {
			JavaMailSender mock = Mockito.mock(JavaMailSender.class);
			Session session = Session.getInstance(new Properties());
			MimeMessage mimeMessage = new MimeMessage(session);
			Mockito.when(mock.createMimeMessage()).thenReturn(mimeMessage);
			return mock;
		}
	}

	@BeforeEach
	void setUp() {
		passwordResetTokenRepository.deleteAll();
		userRepository.deleteAll();
		userRepository.save(new User("Ada", "ada@example.com", passwordEncoder.encode("secret"), "user"));
	}

	@Test
	void forgotPasswordSendsMailAndPersistsToken() throws Exception {
		mockMvc.perform(multipart("/api/forgot-password")
						.param("email", "ada@example.com")
						.header(API_VERSION, "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("We have e-mailed your password reset link!"));

		verify(javaMailSender).send(any(MimeMessage.class));
		Assertions.assertTrue(passwordResetTokenRepository.findById("ada@example.com").isPresent());
	}

	@Test
	void forgotPasswordUnknownUserReturns422() throws Exception {
		mockMvc.perform(multipart("/api/forgot-password")
						.param("email", "nobody@example.com")
						.header(API_VERSION, "1"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.errors.email[0]")
						.value("We can't find a user with that e-mail address."));
	}
}
```

- [x] **Step 2: Full suite**

Run: `./gradlew test --no-daemon`  
Expected: BUILD SUCCESSFUL (Postgres **`challengestest`** reachable; reset schema if Flyway checksum drift)

- [x] **Step 3: Commit**

```bash
git add src/test/java/com/authspring/api/AuthForgotPasswordIT.java
git commit -m "test(auth): forgot-password IT"
```

---

## Self-review

1. **Spec coverage:** Single endpoint, outcomes, email + DB side effects, security permit, JWT for `api_token`, tests — all mapped.
2. **Placeholder scan:** No TBD/TODO; code blocks are complete.
3. **Type consistency:** `User` / `PasswordResetToken` / repositories use **`com.challenges.api`**; authspring keeps **`package com.authspring.api`** for web/services/config.

---

**Plan complete and saved to `docs/superpowers/plans/2026-04-19-06-forgot-password-api-only.md`. Two execution options:**

**1. Subagent-Driven (recommended)** — fresh subagent per task, review between tasks.

**2. Inline Execution** — run tasks in this session with executing-plans and checkpoints.

**Which approach?**
