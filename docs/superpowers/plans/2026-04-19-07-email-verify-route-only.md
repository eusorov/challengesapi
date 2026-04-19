# Email verify GET route only Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port **only** `GET /api/email/verify/{id}/{hash}` from `/Users/evgenyusorov/workspace/java/authspring` (same behavior as `VerifyEmailController` + `EmailVerificationService`): validate Laravel-style `expires` + `signature` query params, constant-time compare path `hash` to `SHA-256(email)` (hex), set `email_verified_at` when first verified, issue JWT + `PersonalAccessTokenService.recordLoginToken`, respond **302** with `Location` pointing at `app.frontend.base-url` with `email_verified`, `api_token`, `auto_login`, `user_id`, `user_name` query fragments; invalid/expired → **403** empty body. **Do not** add other authspring routes (resend notification, register, etc.).

**Architecture:** New **`com.authspring.api`** types: **`FrontendProperties`** (binds existing `app.frontend.base-url`), **`LaravelSignedUrlValidator`** (HMAC over full URL without `signature` param — copy from authspring), sealed **`EmailVerificationOutcome`**, **`EmailVerificationService`** (imports **`com.challenges.api.model.User`** / **`UserRepository`**), **`VerifyEmailController`**. Reuse existing **`EmailVerificationHashes`**, **`JwtService`**, **`PersonalAccessTokenService`**, **`VerificationProperties`**. **`SecurityConfig`** must **`permitAll`** for this GET path **before** `/api/**` authenticated. Optionally add **`setUpdatedAt`** on **`User`** so the service can mirror authspring’s explicit timestamp update (or omit `setUpdatedAt` calls and rely on `@PreUpdate` only — Task 1 chooses one approach).

**Tech stack:** Spring Boot 4, Spring Security, JJWT, JUnit 5, MockMvc, PostgreSQL + Flyway (existing IT style).

**Source (read-only):** `authspring/app/src/main/java/com/authspring/api/web/VerifyEmailController.java`, `.../service/EmailVerificationService.java`, `.../service/EmailVerificationOutcome.java`, `.../security/LaravelSignedUrlValidator.java`, `authspring/app/src/test/java/com/authspring/api/AuthVerifyEmailIT.java`, `.../security/LaravelSignedUrlValidatorTest.java`.

---

## File map (create / modify)

| Path | Responsibility |
|------|----------------|
| `src/main/java/com/authspring/api/config/FrontendProperties.java` | `record FrontendProperties(String baseUrl)` + `@ConfigurationProperties(prefix = "app.frontend")` — YAML key `base-url` → `baseUrl`. |
| `src/main/java/com/challenges/api/ChallengesApiApplication.java` | Add `FrontendProperties.class` to `@EnableConfigurationProperties`. |
| `src/main/java/com/authspring/api/security/LaravelSignedUrlValidator.java` | **Create** — same logic as authspring (depends on `VerificationProperties`). |
| `src/main/java/com/authspring/api/service/EmailVerificationOutcome.java` | Sealed interface + `RedirectToFrontend`, `InvalidOrExpiredLink`. |
| `src/main/java/com/authspring/api/service/EmailVerificationService.java` | Verify flow; `User` / `UserRepository` from **`com.challenges.api`**. |
| `src/main/java/com/authspring/api/web/VerifyEmailController.java` | `GET /api/email/verify/{id}/{hash}` + switch on outcome. |
| `src/main/java/com/challenges/api/config/SecurityConfig.java` | `.requestMatchers(HttpMethod.GET, "/api/email/verify/**").permitAll()` **before** `/api/**` authenticated. |
| `src/main/java/com/challenges/api/model/User.java` | Add **`setUpdatedAt(Instant)`** if you keep authspring’s `user.setUpdatedAt(now)` line (recommended). |
| `src/test/java/com/authspring/api/security/LaravelSignedUrlValidatorTest.java` | Unit tests (MockHttpServletRequest). |
| `src/test/java/com/authspring/api/AuthVerifyEmailIT.java` | MockMvc IT: happy path 302 + DB `emailVerifiedAt`; bad signature 403. |

**Out of scope:** `EmailVerificationNotificationController`, mail send, `POST` routes, `LaravelSignedUrlSigner` changes (already used by notification flow).

---

### Task 1: `User.setUpdatedAt` (recommended)

**Files:**
- Modify: `src/main/java/com/challenges/api/model/User.java`

- [ ] **Step 1: Add setter**

After `getUpdatedAt()` / before `setEmail`, add:

```java
	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = java.util.Objects.requireNonNull(updatedAt);
	}
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileJava --no-daemon`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/challenges/api/model/User.java
git commit -m "feat(user): add setUpdatedAt for verification flow"
```

---

### Task 2: `FrontendProperties` + bootstrap registration

**Files:**
- Create: `src/main/java/com/authspring/api/config/FrontendProperties.java`
- Modify: `src/main/java/com/challenges/api/ChallengesApiApplication.java`

- [ ] **Step 1: Create properties record**

```java
package com.authspring.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.frontend")
public record FrontendProperties(String baseUrl) {}
```

- [ ] **Step 2: Register on main application**

Add `FrontendProperties.class` to the `@EnableConfigurationProperties` list in `ChallengesApiApplication` (alongside `JwtProperties`, `VerificationProperties`, …).

- [ ] **Step 3: Compile**

Run: `./gradlew compileJava --no-daemon`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/authspring/api/config/FrontendProperties.java \
  src/main/java/com/challenges/api/ChallengesApiApplication.java
git commit -m "feat(config): bind app.frontend for verify redirect"
```

---

### Task 3: `LaravelSignedUrlValidator`

**Files:**
- Create: `src/main/java/com/authspring/api/security/LaravelSignedUrlValidator.java`

- [ ] **Step 1: Add class** (copy from authspring; package `com.authspring.api.security`; import `com.authspring.api.config.VerificationProperties`)

```java
package com.authspring.api.security;

import com.authspring.api.config.VerificationProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class LaravelSignedUrlValidator {

	private final VerificationProperties properties;

	public LaravelSignedUrlValidator(VerificationProperties properties) {
		this.properties = properties;
	}

	public boolean hasValidSignature(HttpServletRequest request) {
		if (!hasCorrectSignature(request)) {
			return false;
		}
		return signatureHasNotExpired(request);
	}

	public boolean hasCorrectSignature(HttpServletRequest request) {
		String key = properties.signingKey();
		if (key == null || key.isEmpty()) {
			return false;
		}
		String fullUrl = request.getRequestURL().toString();
		String qs = request.getQueryString();
		String withoutSig = stripSignatureParameter(qs);
		String original =
				withoutSig == null || withoutSig.isEmpty() ? fullUrl : fullUrl + "?" + withoutSig;
		String expected = hmacSha256Hex(original, key);
		String provided = request.getParameter("signature");
		return provided != null && constantTimeEquals(expected, provided);
	}

	static String stripSignatureParameter(String queryString) {
		if (queryString == null || queryString.isEmpty()) {
			return "";
		}
		List<String> parts = new ArrayList<>();
		for (String part : queryString.split("&")) {
			int eq = part.indexOf('=');
			String name = eq >= 0 ? part.substring(0, eq) : part;
			if ("signature".equals(name)) {
				continue;
			}
			parts.add(part);
		}
		return String.join("&", parts);
	}

	private static boolean signatureHasNotExpired(HttpServletRequest request) {
		String exp = request.getParameter("expires");
		if (exp == null || exp.isEmpty()) {
			return true;
		}
		try {
			long ts = Long.parseLong(exp);
			return Instant.now().getEpochSecond() <= ts;
		} catch (NumberFormatException _) {
			return false;
		}
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

	private static boolean constantTimeEquals(String a, String b) {
		if (a.length() != b.length()) {
			return false;
		}
		int r = 0;
		for (int i = 0; i < a.length(); i++) {
			r |= a.charAt(i) ^ b.charAt(i);
		}
		return r == 0;
	}
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileJava --no-daemon`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/authspring/api/security/LaravelSignedUrlValidator.java
git commit -m "feat(auth): Laravel signed URL validation for email verify"
```

---

### Task 4: `EmailVerificationOutcome`

**Files:**
- Create: `src/main/java/com/authspring/api/service/EmailVerificationOutcome.java`

- [ ] **Step 1: Add sealed interface**

```java
package com.authspring.api.service;

public sealed interface EmailVerificationOutcome
		permits EmailVerificationOutcome.RedirectToFrontend, EmailVerificationOutcome.InvalidOrExpiredLink {

	record RedirectToFrontend(String redirectUrl) implements EmailVerificationOutcome {}

	record InvalidOrExpiredLink() implements EmailVerificationOutcome {}
}
```

- [ ] **Step 2: Compile / commit**

Run: `./gradlew compileJava --no-daemon` && `git add ... && git commit -m "feat(auth): email verification outcome types"`

---

### Task 5: `EmailVerificationService`

**Files:**
- Create: `src/main/java/com/authspring/api/service/EmailVerificationService.java`

- [ ] **Step 1: Implement service**

```java
package com.authspring.api.service;

import com.authspring.api.config.FrontendProperties;
import com.authspring.api.security.EmailVerificationHashes;
import com.authspring.api.security.JwtService;
import com.authspring.api.security.LaravelSignedUrlValidator;
import com.challenges.api.model.User;
import com.challenges.api.repo.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailVerificationService {

	private final UserRepository userRepository;
	private final LaravelSignedUrlValidator laravelSignedUrlValidator;
	private final JwtService jwtService;
	private final FrontendProperties frontendProperties;
	private final PersonalAccessTokenService personalAccessTokenService;

	public EmailVerificationService(
			UserRepository userRepository,
			LaravelSignedUrlValidator laravelSignedUrlValidator,
			JwtService jwtService,
			FrontendProperties frontendProperties,
			PersonalAccessTokenService personalAccessTokenService) {
		this.userRepository = userRepository;
		this.laravelSignedUrlValidator = laravelSignedUrlValidator;
		this.jwtService = jwtService;
		this.frontendProperties = frontendProperties;
		this.personalAccessTokenService = personalAccessTokenService;
	}

	@Transactional
	public EmailVerificationOutcome verify(HttpServletRequest request, Long id, String hash) {
		if (!laravelSignedUrlValidator.hasValidSignature(request)) {
			return new EmailVerificationOutcome.InvalidOrExpiredLink();
		}
		User user = userRepository.findById(id).orElse(null);
		if (user == null) {
			return new EmailVerificationOutcome.InvalidOrExpiredLink();
		}
		String expectedHash = EmailVerificationHashes.sha256Hex(user.getEmail());
		if (!constantTimeEquals(expectedHash, hash.toLowerCase(Locale.ROOT))) {
			return new EmailVerificationOutcome.InvalidOrExpiredLink();
		}
		if (user.getEmailVerifiedAt() == null) {
			Instant now = Instant.now();
			user.setEmailVerifiedAt(now);
			user.setUpdatedAt(now);
			userRepository.save(user);
		}
		String token = jwtService.createToken(user);
		personalAccessTokenService.recordLoginToken(user, token);
		String url = buildRedirectUrl(user, token);
		return new EmailVerificationOutcome.RedirectToFrontend(url);
	}

	private String buildRedirectUrl(User user, String token) {
		String base = frontendProperties.baseUrl().replaceAll("/$", "");
		return base
				+ "/?email_verified=1&api_token="
				+ urlEncode(token)
				+ "&auto_login=1&user_id="
				+ user.getId()
				+ "&user_name="
				+ urlEncode(user.getName());
	}

	private static String urlEncode(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	private static boolean constantTimeEquals(String a, String b) {
		if (a.length() != b.length()) {
			return false;
		}
		int r = 0;
		for (int i = 0; i < a.length(); i++) {
			r |= a.charAt(i) ^ b.charAt(i);
		}
		return r == 0;
	}
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileJava --no-daemon`  
Expected: BUILD SUCCESSFUL (requires Task 1 `setUpdatedAt`)

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/authspring/api/service/EmailVerificationService.java
git commit -m "feat(auth): email verification service"
```

---

### Task 6: `VerifyEmailController`

**Files:**
- Create: `src/main/java/com/authspring/api/web/VerifyEmailController.java`

- [ ] **Step 1: Add controller**

```java
package com.authspring.api.web;

import com.authspring.api.service.EmailVerificationOutcome;
import com.authspring.api.service.EmailVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api", version = "1")
public class VerifyEmailController {

	private final EmailVerificationService emailVerificationService;

	public VerifyEmailController(EmailVerificationService emailVerificationService) {
		this.emailVerificationService = emailVerificationService;
	}

	@GetMapping("/email/verify/{id}/{hash}")
	public ResponseEntity<Object> verify(
			HttpServletRequest request, @PathVariable Long id, @PathVariable String hash) {
		return switch (emailVerificationService.verify(request, id, hash)) {
			case EmailVerificationOutcome.RedirectToFrontend(var url) ->
					ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, url).build();
			case EmailVerificationOutcome.InvalidOrExpiredLink() ->
					ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		};
	}
}
```

- [ ] **Step 2: Compile / commit**

Run: `./gradlew compileJava --no-daemon`  
```bash
git add src/main/java/com/authspring/api/web/VerifyEmailController.java
git commit -m "feat(auth): GET /api/email/verify/{id}/{hash}"
```

---

### Task 7: Security — permit unauthenticated GET

**Files:**
- Modify: `src/main/java/com/challenges/api/config/SecurityConfig.java`

- [ ] **Step 1: Insert matcher** (immediately after `OPTIONS` / `actuator`, or with other `permitAll` API routes — **must precede** `.requestMatchers("/api/**").authenticated()`)

```java
.requestMatchers(HttpMethod.GET, "/api/email/verify/**").permitAll()
```

- [ ] **Step 2: Run tests**

Run: `./gradlew test --no-daemon`  
Expected: BUILD SUCCESSFUL (fix ordering if verify still returns 401)

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/challenges/api/config/SecurityConfig.java
git commit -m "fix(security): permit GET email verify without JWT"
```

---

### Task 8: Unit test — `LaravelSignedUrlValidator`

**Files:**
- Create: `src/test/java/com/authspring/api/security/LaravelSignedUrlValidatorTest.java`

- [ ] **Step 1: Add test class** (same as authspring `LaravelSignedUrlValidatorTest` — imports `com.authspring.api.config.VerificationProperties`, `MockHttpServletRequest`)

Use the full class from authspring `LaravelSignedUrlValidatorTest.java` (valid signature, wrong signature, `stripSignatureParameter`).

- [ ] **Step 2: Run**

Run: `./gradlew test --no-daemon --tests 'com.authspring.api.security.LaravelSignedUrlValidatorTest'`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/authspring/api/security/LaravelSignedUrlValidatorTest.java
git commit -m "test(auth): LaravelSignedUrlValidator"
```

---

### Task 9: Integration test — verify redirect + 403

**Files:**
- Create: `src/test/java/com/authspring/api/AuthVerifyEmailIT.java`

- [ ] **Step 1: Add IT** (adapt authspring `AuthVerifyEmailIT` to this repo)

Key adaptations:
- `import com.challenges.api.ChallengesApiApplication;`
- `import com.challenges.api.model.User;`
- `import com.challenges.api.repo.UserRepository;`
- `@SpringBootTest(classes = ChallengesApiApplication.class)` at class level
- **No** Testcontainers — use `@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)` + Postgres `challengestest` like `AuthLoginIT`
- `SIGNING_KEY` must equal `src/test/resources/application-test.yml` → `app.verification.signing-key` (`test-verification-signing-key-32chars!!`)
- Build signed URL exactly like authspring IT: `original = "http://localhost/api/email/verify/" + id + "/" + hash + "?expires=" + expires` then `signature = hmacSha256Hex(original, SIGNING_KEY)`
- `mockMvc.perform(get("/api/email/verify/{id}/{hash}", id, hash).header("API-Version", "1").queryParam(...))`
- Assert `302`, `Location` contains `email_verified=1` and `api_token=`, reload user and `assertNotNull(reloaded.getEmailVerifiedAt())`
- Second test: `signature=invalid` → `403`

Full reference implementation:

```java
package com.authspring.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.challenges.api.ChallengesApiApplication;
import com.challenges.api.model.User;
import com.challenges.api.repo.UserRepository;
import com.authspring.api.security.EmailVerificationHashes;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = ChallengesApiApplication.class)
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class AuthVerifyEmailIT {

	private static final String API_VERSION = "API-Version";
	private static final String SIGNING_KEY = "test-verification-signing-key-32chars!!";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@BeforeEach
	void setUp() {
		userRepository.deleteAll();
		userRepository.save(new User("Ada", "ada@example.com", passwordEncoder.encode("secret"), "USER"));
	}

	@Test
	void verifyEmailRedirectsWithJwtInLocation() throws Exception {
		User user = userRepository.findByEmail("ada@example.com").orElseThrow();
		Long id = user.getId();
		String email = user.getEmail();
		String hash = EmailVerificationHashes.sha256Hex(email);
		long expires = Instant.now().getEpochSecond() + 3600;

		String fullUrl = "http://localhost/api/email/verify/" + id + "/" + hash;
		String original = fullUrl + "?expires=" + expires;
		String signature = hmacSha256Hex(original, SIGNING_KEY);

		mockMvc.perform(get("/api/email/verify/{id}/{hash}", id, hash)
						.header(API_VERSION, "1")
						.queryParam("expires", Long.toString(expires))
						.queryParam("signature", signature))
				.andExpect(status().isFound())
				.andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.containsString("email_verified=1")))
				.andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.containsString("api_token=")));

		User reloaded = userRepository.findById(id).orElseThrow();
		Assertions.assertNotNull(reloaded.getEmailVerifiedAt());
	}

	@Test
	void verifyEmailInvalidSignatureReturns403() throws Exception {
		User user = userRepository.findByEmail("ada@example.com").orElseThrow();
		Long id = user.getId();
		String hash = EmailVerificationHashes.sha256Hex(user.getEmail());

		mockMvc.perform(get("/api/email/verify/{id}/{hash}", id, hash)
						.header(API_VERSION, "1")
						.queryParam("expires", Long.toString(Instant.now().getEpochSecond() + 3600))
						.queryParam("signature", "invalid"))
				.andExpect(status().isForbidden());
	}

	private static String hmacSha256Hex(String data, String key) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		byte[] out = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
		return HexFormat.of().formatHex(out);
	}
}
```

- [ ] **Step 2: Full suite**

Run: `./gradlew test --no-daemon`  
Expected: BUILD SUCCESSFUL (requires Docker Postgres + `challengestest` migrated, or reset schema if Flyway checksum drift)

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/authspring/api/AuthVerifyEmailIT.java
git commit -m "test(auth): email verify redirect and forbidden"
```

---

## Self-review

1. **Spec coverage:** Single GET route, signed query string, hash check, persist `email_verified_at`, JWT + PAT, 302 vs 403 — Tasks 3–9. Supporting config/security/User setter — Tasks 1–2, 7.
2. **Placeholder scan:** No TBD/TODO.
3. **Consistency:** `User` uses `USER` role in IT to match `User.DEFAULT_ROLE`; `SIGNING_KEY` matches `application-test.yml`; HMAC string uses `http://localhost` + path (MockMvc default request URL) per authspring IT.

---

**Plan complete and saved to `docs/superpowers/plans/2026-04-19-07-email-verify-route-only.md`. Two execution options:**

**1. Subagent-Driven (recommended)** — dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — execute tasks in this session using executing-plans, batch execution with checkpoints.

**Which approach?**
