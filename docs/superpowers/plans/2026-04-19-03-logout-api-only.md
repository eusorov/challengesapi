# Logout API only (authspring → challengesapi) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add **only** `POST /api/logout` (API-Version `1`) from `/Users/evgenyusorov/workspace/java/authspring/app/src/main/java/com/authspring/api/web/LoginController.java`: **Bearer JWT required**, deletes the matching `personal_access_tokens` row (SHA-256 of the compact JWT), returns `{"message":"Logged out"}`. **401** when not authenticated (no valid Bearer session). **Do not** port register, forgot password, current-user, email verification, or other authspring controllers.

**Architecture:** Logout is annotated with **`@RequiresAuth`** (`@PreAuthorize("isAuthenticated()")`). That requires: (1) **`@EnableMethodSecurity`** on the existing security configuration, (2) a **`JwtAuthenticationFilter`** that validates the Bearer JWT with **`JwtService.parseAndValidate`**, checks the token exists in **`personal_access_tokens`** via **`PersonalAccessTokenService.existsForJwtCompact`**, loads **`com.challenges.api.model.User`** by id, and sets **`SecurityContextHolder`** with **`UserPrincipal`**. (3) **`ProblemJsonAuthenticationEntryPoint`** for filter-level 401 JSON (optional if only method security triggers; still useful). (4) **`GlobalExceptionHandler`** must handle **`AccessDeniedException`** like authspring so anonymous calls to `@PreAuthorize` return **401 ProblemDetail**, not a generic 500 from the catch-all handler. **Single `SecurityFilterChain`** stays in **`com.challenges.api.config.SecurityConfig`** (do **not** add a second `@EnableWebSecurity` class). New types live under **`com.authspring.api.security`** except the updated **`SecurityConfig`**.

**Tech stack:** Spring Boot 4.0.x, Spring Security 7, JJWT 0.12.6, JUnit 5, MockMvc, Mockito (existing).

**Source (read-only):** `authspring/app/src/main/java/com/authspring/api/web/LoginController.java` (lines 70–79), `PersonalAccessTokenService.java` (revoke + exists), `JwtAuthenticationFilter.java`, `UserPrincipal.java`, `RequiresAuth.java`, `ProblemJsonAuthenticationEntryPoint.java`, `SecurityConfig.java` (filter + method security pattern), `GlobalExceptionHandler.java` (`AccessDeniedException` block).

---

## File map (create / modify)

| Path | Responsibility |
|------|----------------|
| `src/main/java/com/authspring/api/security/JwtService.java` | Add **`parseAndValidate(String)`** → **`Claims`**. |
| `src/main/java/com/authspring/api/security/UserPrincipal.java` | **Create:** `UserDetails` wrapper for **`com.challenges.api.model.User`**. |
| `src/main/java/com/authspring/api/security/RequiresAuth.java` | **Create:** meta-annotation → **`@PreAuthorize("isAuthenticated()")`**. |
| `src/main/java/com/authspring/api/security/JwtAuthenticationFilter.java` | **Create:** Bearer parsing, skip **`POST /api/login`**, set authentication when JWT + PAT row exist. |
| `src/main/java/com/authspring/api/security/ProblemJsonAuthenticationEntryPoint.java` | **Create:** RFC 9457 JSON **401** for **`AuthenticationEntryPoint`**. |
| `src/main/java/com/challenges/api/config/SecurityConfig.java` | **`@EnableMethodSecurity`**, **STATELESS** session, **`authenticationEntryPoint`**, **`addFilterBefore(JwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)`**; keep **`anyRequest().permitAll()`** so URL matchers do not block anonymous access to `/api/**` (method security enforces logout). |
| `src/main/java/com/challenges/api/repo/PersonalAccessTokenRepository.java` | **`Optional<PersonalAccessToken> findByToken(String token)`**, **`void deleteByToken(String token)`**. |
| `src/main/java/com/authspring/api/service/PersonalAccessTokenService.java` | **`existsForJwtCompact`**, **`revokeByJwtFromRequest(HttpServletRequest)`** (parse `Authorization`, **`deleteByToken(sha256Hex(jwt))`** — single **`@Transactional`** method is enough; no **`@Lazy` self** required). |
| `src/main/java/com/authspring/api/web/LoginController.java` | Inject **`PersonalAccessTokenService`**; add **`destroy`** (`POST /logout`, **`@RequiresAuth`**). |
| `src/main/java/com/challenges/api/web/GlobalExceptionHandler.java` | **`@ExceptionHandler(AccessDeniedException.class)`** → **401** when principal is not **`UserPrincipal`**, else **403** (copy authspring logic). |
| `src/test/java/com/authspring/api/security/JwtServiceTest.java` | Test **`parseAndValidate`** round-trip on token from **`createToken`**. |
| `src/test/java/com/authspring/api/service/PersonalAccessTokenServiceTest.java` | Test **`revokeByJwtFromRequest`** deletes row when **`Authorization: Bearer …`** present. |
| `src/test/java/com/authspring/api/AuthLoginIT.java` | Add **`logoutWithBearerReturnsMessage`**, **`logoutWithoutTokenReturns401`** (mirror **`AuthLoginIT`** in authspring). |

**Out of scope:** CORS / `FrontendProperties`, `/api/secured/**` URL rules, `JwtService.createPasswordResetFlowToken`, register/forgot/reset/verify routes, `GlobalExceptionHandler` parity beyond **`AccessDeniedException`**.

---

### Task 1: `JwtService.parseAndValidate`

**Files:**
- Modify: `src/main/java/com/authspring/api/security/JwtService.java`
- Modify: `src/test/java/com/authspring/api/security/JwtServiceTest.java`

- [ ] **Step 1: Add parser method**

```java
import io.jsonwebtoken.Claims;

public Claims parseAndValidate(String token) {
	return Jwts.parser()
			.verifyWith(signingKey)
			.build()
			.parseSignedClaims(token)
			.getPayload();
}
```

- [ ] **Step 2: Add test**

```java
@Test
void parseAndValidate_roundTripsCreateToken() {
	JwtService svc = new JwtService(new JwtProperties(SECRET_32, 60_000L, 3600_000L));
	User user = mock(User.class);
	when(user.getId()).thenReturn(42L);
	when(user.getEmail()).thenReturn("x@y.z");
	String jwt = svc.createToken(user);
	Claims claims = svc.parseAndValidate(jwt);
	assertThat(claims.getSubject()).isEqualTo("42");
	assertThat(claims.get("email", String.class)).isEqualTo("x@y.z");
}
```

- [ ] **Step 3: Run tests**

Run: `./gradlew test --tests 'com.authspring.api.security.JwtServiceTest' --no-daemon`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/authspring/api/security/JwtService.java \
  src/test/java/com/authspring/api/security/JwtServiceTest.java
git commit -m "feat(auth): JwtService.parseAndValidate for Bearer validation"
```

---

### Task 2: `PersonalAccessTokenRepository` query methods

**Files:**
- Modify: `src/main/java/com/challenges/api/repo/PersonalAccessTokenRepository.java`

- [ ] **Step 1: Extend interface**

```java
package com.challenges.api.repo;

import com.challenges.api.model.PersonalAccessToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonalAccessTokenRepository extends JpaRepository<PersonalAccessToken, Long> {

	Optional<PersonalAccessToken> findByToken(String token);

	void deleteByToken(String token);
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileJava --no-daemon`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/challenges/api/repo/PersonalAccessTokenRepository.java
git commit -m "feat(auth): PAT repository find/delete by token hash"
```

---

### Task 3: `PersonalAccessTokenService` — exists + revoke

**Files:**
- Modify: `src/main/java/com/authspring/api/service/PersonalAccessTokenService.java`
- Modify: `src/test/java/com/authspring/api/service/PersonalAccessTokenServiceTest.java`

- [ ] **Step 1: Add methods** (keep existing **`recordLoginToken`**; add imports **`jakarta.servlet.http.HttpServletRequest`**, **`org.springframework.http.HttpHeaders`**)

```java
@Transactional(readOnly = true)
public boolean existsForJwtCompact(String jwtCompact) {
	return repository.findByToken(sha256Hex(jwtCompact)).isPresent();
}

@Transactional
public void revokeByJwtFromRequest(HttpServletRequest request) {
	String header = request.getHeader(HttpHeaders.AUTHORIZATION);
	if (header == null || !header.startsWith("Bearer ")) {
		return;
	}
	String raw = header.substring("Bearer ".length()).trim();
	if (raw.isEmpty()) {
		return;
	}
	repository.deleteByToken(sha256Hex(raw));
}
```

- [ ] **Step 2: Add unit test**

```java
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

@Test
void revokeByJwtFromRequest_deletesWhenBearerPresent() {
	HttpServletRequest req = mock(HttpServletRequest.class);
	when(req.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer abc.def.sig");
	service.revokeByJwtFromRequest(req);
	verify(repository).deleteByToken(anyString());
}

@Test
void revokeByJwtFromRequest_noOpWhenHeaderMissing() {
	HttpServletRequest req = mock(HttpServletRequest.class);
	when(req.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(null);
	service.revokeByJwtFromRequest(req);
	verifyNoInteractions(repository);
}
```

(Use **`anyString()`** from **`org.mockito.ArgumentMatchers`**; for **`deleteByToken`** verify the argument is 64 hex chars if you prefer **`argThat(s -> s != null && s.length() == 64)`**.)

- [ ] **Step 3: Run tests**

Run: `./gradlew test --tests 'com.authspring.api.service.PersonalAccessTokenServiceTest' --no-daemon`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/authspring/api/service/PersonalAccessTokenService.java \
  src/test/java/com/authspring/api/service/PersonalAccessTokenServiceTest.java
git commit -m "feat(auth): PAT exists + revoke by Bearer JWT"
```

---

### Task 4: `UserPrincipal`

**Files:**
- Create: `src/main/java/com/authspring/api/security/UserPrincipal.java`

- [ ] **Step 1: Add class** ( **`com.challenges.api.model.User`** )

```java
package com.authspring.api.security;

import com.challenges.api.model.User;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public final class UserPrincipal implements UserDetails {

	private final Long id;
	private final String email;
	private final String role;
	private final Instant emailVerifiedAt;

	public UserPrincipal(User user) {
		this.id = user.getId();
		this.email = user.getEmail();
		this.role = user.getRole();
		this.emailVerifiedAt = user.getEmailVerifiedAt();
	}

	public Long getId() {
		return id;
	}

	public Instant getEmailVerifiedAt() {
		return emailVerifiedAt;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
	}

	@Override
	public String getPassword() {
		return "";
	}

	@Override
	public String getUsername() {
		return email;
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return true;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	@Override
	public boolean isEnabled() {
		return true;
	}
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileJava --no-daemon`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/authspring/api/security/UserPrincipal.java
git commit -m "feat(auth): UserPrincipal for JWT security context"
```

---

### Task 5: `RequiresAuth`

**Files:**
- Create: `src/main/java/com/authspring/api/security/RequiresAuth.java`

- [ ] **Step 1: Add annotation**

```java
package com.authspring.api.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("isAuthenticated()")
public @interface RequiresAuth {}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/authspring/api/security/RequiresAuth.java
git commit -m "feat(auth): RequiresAuth meta-annotation"
```

---

### Task 6: `JwtAuthenticationFilter`

**Files:**
- Create: `src/main/java/com/authspring/api/security/JwtAuthenticationFilter.java`

- [ ] **Step 1: Add filter** (imports: **`com.challenges.api.repo.UserRepository`**, **`com.challenges.api.model.User`**)

```java
package com.authspring.api.security;

import com.authspring.api.service.PersonalAccessTokenService;
import com.challenges.api.repo.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtService jwtService;
	private final UserRepository userRepository;
	private final PersonalAccessTokenService personalAccessTokenService;

	public JwtAuthenticationFilter(
			JwtService jwtService,
			UserRepository userRepository,
			PersonalAccessTokenService personalAccessTokenService) {
		this.jwtService = jwtService;
		this.userRepository = userRepository;
		this.personalAccessTokenService = personalAccessTokenService;
	}

	@Override
	protected void doFilterInternal(
			@NonNull HttpServletRequest request,
			@NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain) throws ServletException, IOException {
		if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
			filterChain.doFilter(request, response);
			return;
		}
		String path = request.getServletPath();
		if (path.isEmpty()) {
			path = request.getRequestURI();
		}
		if ("/api/login".equals(path) && "POST".equalsIgnoreCase(request.getMethod())) {
			filterChain.doFilter(request, response);
			return;
		}

		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header == null || !header.startsWith(BEARER_PREFIX)) {
			filterChain.doFilter(request, response);
			return;
		}

		String rawToken = header.substring(BEARER_PREFIX.length()).trim();
		if (rawToken.isEmpty()) {
			filterChain.doFilter(request, response);
			return;
		}

		try {
			Claims claims = jwtService.parseAndValidate(rawToken);
			if (!personalAccessTokenService.existsForJwtCompact(rawToken)) {
				filterChain.doFilter(request, response);
				return;
			}
			long userId = Long.parseLong(claims.getSubject());
			userRepository
					.findById(userId)
					.ifPresent(user -> {
						UserPrincipal principal = new UserPrincipal(user);
						UsernamePasswordAuthenticationToken authentication =
								new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
						authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
						SecurityContextHolder.getContext().setAuthentication(authentication);
					});
		} catch (Exception ex) {
			log.debug("JWT authentication skipped: {}", ex.toString());
		}

		filterChain.doFilter(request, response);
	}
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileJava --no-daemon`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/authspring/api/security/JwtAuthenticationFilter.java
git commit -m "feat(auth): JWT filter for Bearer + PAT existence"
```

---

### Task 7: `ProblemJsonAuthenticationEntryPoint`

**Files:**
- Create: `src/main/java/com/authspring/api/security/ProblemJsonAuthenticationEntryPoint.java`

- [ ] **Step 1: Add component** (inject **`ObjectMapper`** from Spring Boot)

```java
package com.authspring.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class ProblemJsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final ObjectMapper objectMapper;

	public ProblemJsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public void commence(
			HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
			throws IOException {
		ProblemDetail pd =
				ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Authentication is required.");
		pd.setTitle("Unauthorized");
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		objectMapper.writeValue(response.getOutputStream(), pd);
	}
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/authspring/api/security/ProblemJsonAuthenticationEntryPoint.java
git commit -m "feat(auth): JSON 401 entry point"
```

---

### Task 8: Merge security configuration

**Files:**
- Modify: `src/main/java/com/challenges/api/config/SecurityConfig.java`

- [ ] **Step 1: Replace bean** (keep **`PasswordEncoder`**; add imports)

```java
package com.challenges.api.config;

import com.authspring.api.security.JwtAuthenticationFilter;
import com.authspring.api.security.ProblemJsonAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			JwtAuthenticationFilter jwtAuthenticationFilter,
			ProblemJsonAuthenticationEntryPoint authenticationEntryPoint)
			throws Exception {
		http.csrf(csrf -> csrf.disable());
		http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
		http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
		http.exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint));
		http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}
}
```

- [ ] **Step 2: Run full test suite** (reset **`challengestest`** if Flyway checksum drift)

Run: `./gradlew test --no-daemon`  
Expected: BUILD SUCCESSFUL (integration tests still permit anonymous for non-`@RequiresAuth` routes)

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/challenges/api/config/SecurityConfig.java
git commit -m "feat(auth): stateless JWT filter and method security"
```

---

### Task 9: `GlobalExceptionHandler` — `AccessDeniedException`

**Files:**
- Modify: `src/main/java/com/challenges/api/web/GlobalExceptionHandler.java`

- [ ] **Step 1: Add handler** (before or after other handlers; **not** inside **`fallback`**)

```java
import com.authspring.api.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@ExceptionHandler(AccessDeniedException.class)
public ResponseEntity<ProblemDetail> accessDenied(AccessDeniedException ex, HttpServletRequest request) {
	Authentication auth = SecurityContextHolder.getContext().getAuthentication();
	if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal)) {
		ProblemDetail pd =
				ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Authentication is required.");
		pd.setTitle("Unauthorized");
		pd.setInstance(URI.create(request.getRequestURI()));
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(pd);
	}
	String detail = ex.getMessage();
	if (detail == null || detail.isBlank()) {
		detail = "Access is denied.";
	}
	ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, detail);
	pd.setTitle("Forbidden");
	pd.setInstance(URI.create(request.getRequestURI()));
	return ResponseEntity.status(HttpStatus.FORBIDDEN).body(pd);
}
```

- [ ] **Step 2: Run tests**

Run: `./gradlew test --no-daemon`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/challenges/api/web/GlobalExceptionHandler.java
git commit -m "fix(api): map AccessDenied to 401/403 ProblemDetail"
```

---

### Task 10: `LoginController` logout

**Files:**
- Modify: `src/main/java/com/authspring/api/web/LoginController.java`

- [ ] **Step 1: Add endpoint**

Add constructor dependency **`PersonalAccessTokenService`** and method:

```java
import com.authspring.api.security.RequiresAuth;
import com.authspring.api.service.PersonalAccessTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;

private final PersonalAccessTokenService personalAccessTokenService;

public LoginController(SessionService sessionService, PersonalAccessTokenService personalAccessTokenService) {
	this.sessionService = sessionService;
	this.personalAccessTokenService = personalAccessTokenService;
}

@RequiresAuth
@PostMapping("/logout")
public Map<String, String> destroy(HttpServletRequest request) {
	personalAccessTokenService.revokeByJwtFromRequest(request);
	return Map.of("message", "Logged out");
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileJava --no-daemon`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/authspring/api/web/LoginController.java
git commit -m "feat(auth): POST /api/logout"
```

---

### Task 11: Integration tests for logout

**Files:**
- Modify: `src/test/java/com/authspring/api/AuthLoginIT.java`

- [ ] **Step 1: Add tests** (reuse **`API_VERSION`** header; extract token via **`com.jayway.jsonpath.JsonPath`** if on classpath, or **`JsonNode`** from **`ObjectMapper`**)

```java
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

@Autowired
private ObjectMapper objectMapper;

@Test
void logoutWithBearerReturnsMessage() throws Exception {
	MvcResult login = mockMvc.perform(post("/api/login")
					.header(API_VERSION, "1")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"email\":\"ada@example.com\",\"password\":\"secret\"}"))
			.andExpect(status().isOk())
			.andReturn();
	JsonNode root = objectMapper.readTree(login.getResponse().getContentAsString());
	String token = root.get("token").asText();

	mockMvc.perform(post("/api/logout")
					.header(API_VERSION, "1")
					.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("Logged out"));
}

@Test
void logoutWithoutTokenReturns401() throws Exception {
	mockMvc.perform(post("/api/logout").header(API_VERSION, "1")).andExpect(status().isUnauthorized());
}
```

- [ ] **Step 2: Run tests**

Run: `./gradlew test --tests 'com.authspring.api.AuthLoginIT' --no-daemon`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run full suite**

Run: `./gradlew test --no-daemon`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/authspring/api/AuthLoginIT.java
git commit -m "test(auth): logout with Bearer and 401 without auth"
```

---

## Self-review

1. **Spec coverage:** Logout route, PAT revocation, Bearer requirement, 401 behavior, filter alignment with login-only PAT rows — covered. No extra authspring endpoints.
2. **Placeholder scan:** No TBD/TODO; code blocks are complete.
3. **Type consistency:** `UserPrincipal` wraps **`com.challenges.api.model.User`**; filter uses **`UserRepository`**; PAT **`token`** column remains 64-char hash; **`deleteByToken`** / **`findByToken`** match repository field **`token`**.

---

**Plan complete and saved to `docs/superpowers/plans/2026-04-19-03-logout-api-only.md`. Two execution options:**

**1. Subagent-Driven (recommended)** — dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — execute tasks in this session using executing-plans, batch execution with checkpoints.

**Which approach?**
