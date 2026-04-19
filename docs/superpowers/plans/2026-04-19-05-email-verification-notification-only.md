# Email verification notification API only Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port **only** `POST /api/email/verification-notification` from `/Users/evgenyusorov/workspace/java/authspring/app/src/main/java/com/authspring/api/web/EmailVerificationNotificationController.java` into **challengesapi**: same Laravel-style responses (**200** `{ "status": "verification-link-sent" }`, **409** `{ "status", "message" }` when already verified), **multipart/form** consumption, **`@RequiresAuth`** (Bearer JWT). **Do not** add `GET /api/email/verify/...`, password reset routes, or any other authspring controllers.

**Architecture:** Add **`com.authspring.api`** classes mirroring authspring: **`EmailVerificationNotificationController`**, **`EmailVerificationNotificationService`**, **`EmailVerificationNotificationOutcome`**, **`EmailVerificationMailSender`**, **`VerificationProperties`**, **`VerificationMailProperties`**, **`LaravelSignedUrlSigner`**, **`EmailVerificationHashes`**. Use **`com.challenges.api.model.User`**, **`com.challenges.api.repo.UserRepository`**, **`com.authspring.api.security.UserPrincipal`** (already wraps challenges `User`). **Signed URL** for the email body is built with **`LaravelSignedUrlSigner`** (HMAC over `publicBaseUrl + /api/email/verify/{id}/{hash}?expires=...` + `&signature=...`) so the link is valid when a future verify endpoint exists; this plan does **not** implement that endpoint. **`ChallengesApiApplication`** must register **`@EnableConfigurationProperties`** for **`VerificationProperties`** and **`VerificationMailProperties`** (in addition to existing **`JwtProperties`**). Root **`application.yml`** already defines **`app.verification`** and **`app.mail.verification`**; **`application-test.yml`** defines **`app.verification`** — if binding tests fail for mail templates, add the same **`app.mail.verification`** block under the test profile (copy from main YAML).

**Tech stack:** Spring Boot 4, Spring Security (JWT filter already on **`/api/**`**), `JavaMailSender`, JUnit 5, MockMvc, Mockito.

**Source files (sibling repo, read-only):**
- `authspring/.../web/EmailVerificationNotificationController.java`
- `authspring/.../service/EmailVerificationNotificationService.java`
- `authspring/.../service/EmailVerificationNotificationOutcome.java`
- `authspring/.../service/EmailVerificationMailSender.java`
- `authspring/.../config/VerificationProperties.java`
- `authspring/.../config/VerificationMailProperties.java`
- `authspring/.../security/LaravelSignedUrlSigner.java`
- `authspring/.../security/EmailVerificationHashes.java`
- `authspring/.../AuthEmailVerificationNotificationIT.java` (adapt to challengesapi test DB + `ChallengesApiApplication`)

---

## File map (create / modify)

| Path | Responsibility |
|------|----------------|
| `src/main/java/com/authspring/api/security/EmailVerificationHashes.java` | SHA-256 hex of email for verify URL path segment. |
| `src/main/java/com/authspring/api/config/VerificationProperties.java` | `app.verification` binding: `signingKey`, `publicBaseUrl`, `expireMinutes`. |
| `src/main/java/com/authspring/api/config/VerificationMailProperties.java` | `app.mail.verification` binding (subject, templates, lines, from, `expiryMinutes`). |
| `src/main/java/com/authspring/api/security/LaravelSignedUrlSigner.java` | Builds absolute signed verify URL (same algorithm as authspring). |
| `src/main/java/com/authspring/api/service/EmailVerificationMailSender.java` | Sends plain-text MIME using `JavaMailSender` + `VerificationMailProperties`; **`User`** = **`com.challenges.api.model.User`**. |
| `src/main/java/com/authspring/api/service/EmailVerificationNotificationOutcome.java` | Sealed: `Sent`, `AlreadyVerified`. |
| `src/main/java/com/authspring/api/service/EmailVerificationNotificationService.java` | Load user by principal id; if `emailVerifiedAt != null` → conflict outcome; else sign URL, send mail, `Sent`. |
| `src/main/java/com/authspring/api/web/EmailVerificationNotificationController.java` | `POST /api/email/verification-notification`, `@RequiresAuth`, switch on outcome → 200 / 409 JSON. |
| `src/main/java/com/challenges/api/ChallengesApiApplication.java` | Add **`VerificationProperties`** and **`VerificationMailProperties`** to **`@EnableConfigurationProperties`**. |
| `src/test/java/com/authspring/api/security/EmailVerificationHashesTest.java` | Unit tests for `sha256Hex` (known vector + length + null). |
| `src/test/java/com/authspring/api/service/EmailVerificationNotificationServiceTest.java` | Mockito: already verified vs send mail vs user missing. |
| `src/test/java/com/authspring/api/AuthEmailVerificationNotificationIT.java` | Mock **`JavaMailSender`**, login → multipart POST → assert 200 + `verify(send)`; set `emailVerifiedAt` → 409; no Bearer → 401. |

**Out of scope:** `VerifyEmailController`, `LaravelSignedUrlValidator`, `EmailVerificationService`, forgot/reset password mail, CORS-only changes unless tests fail for unrelated reasons.

---

### Task 1: `EmailVerificationHashes` + test

**Files:**
- Create: `src/main/java/com/authspring/api/security/EmailVerificationHashes.java`
- Create: `src/test/java/com/authspring/api/security/EmailVerificationHashesTest.java`

- [x] **Step 1: Add utility class**

```java
package com.authspring.api.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public final class EmailVerificationHashes {

	private static final HexFormat HEX = HexFormat.of();

	private EmailVerificationHashes() {}

	public static String sha256Hex(String email) {
		Objects.requireNonNull(email, "email");
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] digest = md.digest(email.getBytes(StandardCharsets.UTF_8));
			return HEX.formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 MessageDigest not available", e);
		}
	}
}
```

- [x] **Step 2: Add tests**

```java
package com.authspring.api.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EmailVerificationHashesTest {

	private static final String ADA_EMAIL_SHA256_HEX =
			"b5fc85e55755f9e0d030a10ab4429b6b2944855f9a0d60077fe832becbc41d72";

	@Test
	void sha256Hex_matchesGoldenVector() {
		assertEquals(ADA_EMAIL_SHA256_HEX, EmailVerificationHashes.sha256Hex("ada@example.com"));
	}

	@Test
	void sha256Hex_returns64LowercaseHexChars() {
		String hex = EmailVerificationHashes.sha256Hex("any@example.com");
		assertEquals(64, hex.length());
		assertTrue(hex.chars().allMatch(c -> (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')));
	}

	@Test
	void sha256Hex_null_throws() {
		assertThrows(NullPointerException.class, () -> EmailVerificationHashes.sha256Hex(null));
	}
}
```

- [x] **Step 3: Run tests**

Run: `./gradlew test --tests 'com.authspring.api.security.EmailVerificationHashesTest' --no-daemon`  
Expected: BUILD SUCCESSFUL

- [x] **Step 4: Commit**

```bash
git add src/main/java/com/authspring/api/security/EmailVerificationHashes.java \
  src/test/java/com/authspring/api/security/EmailVerificationHashesTest.java
git commit -m "feat(auth): EmailVerificationHashes for verify URL path"
```

---

### Task 2: Configuration records + `ChallengesApiApplication`

**Files:**
- Create: `src/main/java/com/authspring/api/config/VerificationProperties.java`
- Create: `src/main/java/com/authspring/api/config/VerificationMailProperties.java`
- Modify: `src/main/java/com/challenges/api/ChallengesApiApplication.java`

- [x] **Step 1: `VerificationProperties`**

```java
package com.authspring.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.verification")
public record VerificationProperties(String signingKey, String publicBaseUrl, int expireMinutes) {}
```

- [x] **Step 2: `VerificationMailProperties`**

```java
package com.authspring.api.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mail.verification")
public record VerificationMailProperties(
		String subject,
		String greetingTemplate,
		List<String> lines,
		String actionLabel,
		List<String> footerLines,
		String salutation,
		int expiryMinutes,
		String fromAddress,
		String fromName) {

	public VerificationMailProperties {
		lines = lines == null ? List.of() : List.copyOf(lines);
		footerLines = footerLines == null ? List.of() : List.copyOf(footerLines);
	}
}
```

- [x] **Step 3: Enable properties on the application class**

```java
@SpringBootApplication(scanBasePackages = {"com.challenges.api", "com.authspring.api"})
@EnableConfigurationProperties({
	JwtProperties.class,
	VerificationProperties.class,
	VerificationMailProperties.class
})
public class ChallengesApiApplication {
```

- [x] **Step 4: Run context test**

Run: `./gradlew test --tests 'com.challenges.api.ChallengesApiApplicationTests' --no-daemon`  
Expected: BUILD SUCCESSFUL (Postgres + Flyway available)

- [x] **Step 5: Commit**

```bash
git add src/main/java/com/authspring/api/config/VerificationProperties.java \
  src/main/java/com/authspring/api/config/VerificationMailProperties.java \
  src/main/java/com/challenges/api/ChallengesApiApplication.java
git commit -m "feat(auth): bind verification and verification mail properties"
```

---

### Task 3: `LaravelSignedUrlSigner`

**Files:**
- Create: `src/main/java/com/authspring/api/security/LaravelSignedUrlSigner.java`

- [x] **Step 1: Copy implementation (package `com.authspring.api`)**

```java
package com.authspring.api.security;

import com.authspring.api.config.VerificationProperties;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class LaravelSignedUrlSigner {

	private final VerificationProperties properties;

	public LaravelSignedUrlSigner(VerificationProperties properties) {
		this.properties = properties;
	}

	public String buildVerifyEmailUrl(long userId, String email) {
		String key = properties.signingKey();
		if (key == null || key.isEmpty()) {
			throw new IllegalStateException("app.verification.signing-key is not set");
		}
		String base = properties.publicBaseUrl().replaceAll("/$", "");
		String hash = EmailVerificationHashes.sha256Hex(email);
		long expires = Instant.now().getEpochSecond() + properties.expireMinutes() * 60L;
		String originalWithoutSig =
				base + "/api/email/verify/" + userId + "/" + hash + "?expires=" + expires;
		String sig = hmacSha256Hex(originalWithoutSig, key);
		return originalWithoutSig + "&signature=" + sig;
	}

	private static String hmacSha256Hex(String data, String key) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			byte[] out = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(out);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
}
```

- [x] **Step 2: Compile**

Run: `./gradlew compileJava --no-daemon`  
Expected: BUILD SUCCESSFUL

- [x] **Step 3: Commit**

```bash
git add src/main/java/com/authspring/api/security/LaravelSignedUrlSigner.java
git commit -m "feat(auth): LaravelSignedUrlSigner for verification email links"
```

---

### Task 4: `EmailVerificationMailSender`

**Files:**
- Create: `src/main/java/com/authspring/api/service/EmailVerificationMailSender.java`

- [x] **Step 1: Add sender (import `com.challenges.api.model.User`)**

```java
package com.authspring.api.service;

import com.authspring.api.config.VerificationMailProperties;
import com.challenges.api.model.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailVerificationMailSender {

	private static final Charset MAIL_CHARSET = StandardCharsets.UTF_8;

	private final JavaMailSender mailSender;
	private final VerificationMailProperties mail;

	public EmailVerificationMailSender(JavaMailSender mailSender, VerificationMailProperties mail) {
		this.mailSender = mailSender;
		this.mail = mail;
	}

	public void send(User user, String verificationUrl) throws MessagingException, UnsupportedEncodingException {
		String body = buildBody(user, verificationUrl);

		MimeMessage mime = mailSender.createMimeMessage();
		MimeMessageHelper helper = new MimeMessageHelper(mime, false, MAIL_CHARSET.name());
		helper.setFrom(mail.fromAddress(), mail.fromName());
		helper.setTo(user.getEmail());
		helper.setSubject(mail.subject());
		helper.setText(body, false);
		mailSender.send(mime);
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
git add src/main/java/com/authspring/api/service/EmailVerificationMailSender.java
git commit -m "feat(auth): send verification email with templated body"
```

---

### Task 5: Outcome + notification service

**Files:**
- Create: `src/main/java/com/authspring/api/service/EmailVerificationNotificationOutcome.java`
- Create: `src/main/java/com/authspring/api/service/EmailVerificationNotificationService.java`

- [x] **Step 1: Outcome sealed interface**

```java
package com.authspring.api.service;

public sealed interface EmailVerificationNotificationOutcome {
	record AlreadyVerified() implements EmailVerificationNotificationOutcome {}

	record Sent() implements EmailVerificationNotificationOutcome {}
}
```

- [x] **Step 2: Service**

```java
package com.authspring.api.service;

import com.authspring.api.security.LaravelSignedUrlSigner;
import com.authspring.api.security.UserPrincipal;
import com.challenges.api.model.User;
import com.challenges.api.repo.UserRepository;
import jakarta.mail.MessagingException;
import java.io.UnsupportedEncodingException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailVerificationNotificationService {

	private final UserRepository userRepository;
	private final LaravelSignedUrlSigner signedUrlSigner;
	private final EmailVerificationMailSender mailSender;

	public EmailVerificationNotificationService(
			UserRepository userRepository,
			LaravelSignedUrlSigner signedUrlSigner,
			EmailVerificationMailSender mailSender) {
		this.userRepository = userRepository;
		this.signedUrlSigner = signedUrlSigner;
		this.mailSender = mailSender;
	}

	@Transactional(readOnly = true)
	public EmailVerificationNotificationOutcome send(UserPrincipal principal) {
		User user =
				userRepository
						.findById(principal.getId())
						.orElseThrow(() -> new IllegalStateException("User not found: " + principal.getId()));
		if (user.getEmailVerifiedAt() != null) {
			return new EmailVerificationNotificationOutcome.AlreadyVerified();
		}
		String url = signedUrlSigner.buildVerifyEmailUrl(user.getId(), user.getEmail());
		try {
			mailSender.send(user, url);
		} catch (MessagingException | UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
		return new EmailVerificationNotificationOutcome.Sent();
	}
}
```

- [x] **Step 3: Unit test (mocked mail + signer + repo)**

Create `src/test/java/com/authspring/api/service/EmailVerificationNotificationServiceTest.java`:

```java
package com.authspring.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.authspring.api.security.LaravelSignedUrlSigner;
import com.authspring.api.security.UserPrincipal;
import com.challenges.api.model.User;
import com.challenges.api.repo.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailVerificationNotificationServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private LaravelSignedUrlSigner signedUrlSigner;

	@Mock
	private EmailVerificationMailSender mailSender;

	@InjectMocks
	private EmailVerificationNotificationService service;

	@Test
	void send_returnsAlreadyVerified_whenEmailVerifiedAtSet() {
		User u = mock(User.class);
		when(u.getId()).thenReturn(1L);
		when(u.getEmail()).thenReturn("ada@example.com");
		when(u.getRole()).thenReturn("user");
		when(u.getEmailVerifiedAt()).thenReturn(Instant.parse("2020-01-01T00:00:00Z"));
		when(userRepository.findById(1L)).thenReturn(Optional.of(u));

		assertThat(service.send(new UserPrincipal(u)))
				.isInstanceOf(EmailVerificationNotificationOutcome.AlreadyVerified.class);
		verify(mailSender, never()).send(any(), any());
		verify(signedUrlSigner, never()).buildVerifyEmailUrl(anyLong(), any());
	}

	@Test
	void send_sendsMailAndReturnsSent_whenUnverified() throws Exception {
		User u = mock(User.class);
		when(u.getId()).thenReturn(2L);
		when(u.getEmail()).thenReturn("b@example.com");
		when(u.getName()).thenReturn("Bob");
		when(u.getRole()).thenReturn("user");
		when(u.getEmailVerifiedAt()).thenReturn(null);
		when(userRepository.findById(2L)).thenReturn(Optional.of(u));
		when(signedUrlSigner.buildVerifyEmailUrl(2L, "b@example.com")).thenReturn("https://app/verify");

		assertThat(service.send(new UserPrincipal(u)))
				.isInstanceOf(EmailVerificationNotificationOutcome.Sent.class);
		verify(mailSender).send(u, "https://app/verify");
	}

	@Test
	void send_throws_whenUserMissing() {
		when(userRepository.findById(99L)).thenReturn(Optional.empty());
		User u = mock(User.class);
		when(u.getId()).thenReturn(99L);
		when(u.getEmail()).thenReturn("x@y.z");
		when(u.getRole()).thenReturn("user");
		when(u.getEmailVerifiedAt()).thenReturn(null);

		assertThatThrownBy(() -> service.send(new UserPrincipal(u)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("99");
	}
}
```

- [x] **Step 4: Run tests**

Run: `./gradlew test --tests 'com.authspring.api.service.EmailVerificationNotificationServiceTest' --no-daemon`  
Expected: BUILD SUCCESSFUL

- [x] **Step 5: Commit**

```bash
git add src/main/java/com/authspring/api/service/EmailVerificationNotificationOutcome.java \
  src/main/java/com/authspring/api/service/EmailVerificationNotificationService.java \
  src/test/java/com/authspring/api/service/EmailVerificationNotificationServiceTest.java
git commit -m "feat(auth): EmailVerificationNotificationService"
```

---

### Task 6: Controller

**Files:**
- Create: `src/main/java/com/authspring/api/web/EmailVerificationNotificationController.java`

- [x] **Step 1: Add controller**

```java
package com.authspring.api.web;

import com.authspring.api.security.RequiresAuth;
import com.authspring.api.security.UserPrincipal;
import com.authspring.api.service.EmailVerificationNotificationOutcome;
import com.authspring.api.service.EmailVerificationNotificationService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiresAuth
@RestController
@RequestMapping(path = "/api", version = "1")
public class EmailVerificationNotificationController {

	private final EmailVerificationNotificationService notificationService;

	public EmailVerificationNotificationController(EmailVerificationNotificationService notificationService) {
		this.notificationService = notificationService;
	}

	@PostMapping(
			value = "/email/verification-notification",
			consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
	public ResponseEntity<Object> store(@AuthenticationPrincipal UserPrincipal principal) {
		return switch (notificationService.send(principal)) {
			case EmailVerificationNotificationOutcome.AlreadyVerified() ->
					ResponseEntity.status(HttpStatus.CONFLICT)
							.body(
									Map.of(
											"status",
											"email-already-verified",
											"message",
											"Email address is already verified"));
			case EmailVerificationNotificationOutcome.Sent() ->
					ResponseEntity.ok(Map.of("status", "verification-link-sent"));
		};
	}
}
```

- [x] **Step 2: Security check**

Confirm **`SecurityConfig`** does **not** permit anonymous **`POST /api/email/verification-notification`** (default: **`/api/**` authenticated** is correct). No change needed if current rules match.

- [x] **Step 3: Compile**

Run: `./gradlew compileJava --no-daemon`  
Expected: BUILD SUCCESSFUL

- [x] **Step 4: Commit**

```bash
git add src/main/java/com/authspring/api/web/EmailVerificationNotificationController.java
git commit -m "feat(auth): POST /api/email/verification-notification"
```

---

### Task 7: Integration test (challengesapi style)

**Files:**
- Create: `src/test/java/com/authspring/api/AuthEmailVerificationNotificationIT.java`

- [x] **Step 1: Add IT**

```java
package com.authspring.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.challenges.api.ChallengesApiApplication;
import com.challenges.api.model.User;
import com.challenges.api.repo.UserRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = ChallengesApiApplication.class)
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AuthEmailVerificationNotificationIT.MockMailSenderConfig.class)
@Transactional
class AuthEmailVerificationNotificationIT {

	private static final String API_VERSION = "API-Version";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JavaMailSender javaMailSender;

	@TestConfiguration
	static class MockMailSenderConfig {

		@Bean
		@Primary
		JavaMailSender javaMailSender() {
			JavaMailSender mail = mock(JavaMailSender.class);
			Session session = Session.getInstance(new Properties());
			MimeMessage mimeMessage = new MimeMessage(session);
			when(mail.createMimeMessage()).thenReturn(mimeMessage);
			return mail;
		}
	}

	@BeforeEach
	void setUp() {
		clearInvocations(javaMailSender);
		userRepository.deleteAll();
		userRepository.save(new User("Ada", "ada@example.com", passwordEncoder.encode("secret"), "user"));
	}

	@Test
	void sendsVerificationWhenUnverified() throws Exception {
		String token = loginToken();

		mockMvc.perform(
						multipart("/api/email/verification-notification")
								.header(API_VERSION, "1")
								.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("verification-link-sent"));

		verify(javaMailSender).send(any(MimeMessage.class));
	}

	@Test
	void alreadyVerifiedReturns409() throws Exception {
		User u = userRepository.findByEmail("ada@example.com").orElseThrow();
		u.setEmailVerifiedAt(Instant.parse("2020-01-01T00:00:00Z"));
		userRepository.save(u);

		String token = loginToken();

		mockMvc.perform(
						multipart("/api/email/verification-notification")
								.header(API_VERSION, "1")
								.header("Authorization", "Bearer " + token))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value("email-already-verified"));

		verify(javaMailSender, never()).send(any(MimeMessage.class));
	}

	@Test
	void withoutTokenReturns401() throws Exception {
		mockMvc.perform(multipart("/api/email/verification-notification").header(API_VERSION, "1"))
				.andExpect(status().isUnauthorized());
	}

	private String loginToken() throws Exception {
		MvcResult login =
				mockMvc.perform(
								post("/api/login")
										.header(API_VERSION, "1")
										.contentType(MediaType.APPLICATION_JSON)
										.content("{\"email\":\"ada@example.com\",\"password\":\"secret\"}"))
						.andExpect(status().isOk())
						.andReturn();
		return JsonPath.read(login.getResponse().getContentAsString(), "$.token");
	}
}
```

- [x] **Step 2: Optional — `application-test.yml`**

If **`VerificationMailProperties`** fails to bind (missing **`app.mail.verification`** in merged config), append under **`app:`** in **`src/test/resources/application-test.yml`**:

```yaml
  mail:
    verification:
      subject: Verify Your Email Address
      greeting-template: "Hello Dear {name}!"
      lines:
        - Welcome! Thank you for registering with us.
        - Please click the link below to verify your email address and activate your account.
      action-label: Verify Email Address
      footer-lines:
        - This verification link will expire in {minutes} minutes.
        - If you did not create an account, no further action is required.
      salutation: Best regards, Team
      expiry-minutes: 60
      from-address: noreply@example.com
      from-name: Team
```

- [x] **Step 3: Run full suite**

Run: `./gradlew test --no-daemon`  
Expected: BUILD SUCCESSFUL

- [x] **Step 4: Commit**

```bash
git add src/test/java/com/authspring/api/AuthEmailVerificationNotificationIT.java
git commit -m "test(auth): verification-notification endpoint IT"
```

---

## Self-review

1. **Spec coverage:** Only **`POST /api/email/verification-notification`** + supporting types; verify GET route explicitly excluded.
2. **Placeholder scan:** Email hash test uses a **concrete** expected value after local computation (Step 1 Task 1).
3. **Type consistency:** **`User`** is **`com.challenges.api.model.User`** everywhere in new services; **`UserPrincipal`** already matches **`challengesapi`**.

---

**Plan complete and saved to `docs/superpowers/plans/2026-04-19-05-email-verification-notification-only.md`. Two execution options:**

**1. Subagent-Driven (recommended)** — dispatch a fresh subagent per task, review between tasks.

**2. Inline Execution** — execute tasks in this session using executing-plans with checkpoints.

**Which approach?**
