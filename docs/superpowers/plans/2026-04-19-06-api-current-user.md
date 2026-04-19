# GET /api/user (current authenticated user) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add **only** `GET /api/user` (header `API-Version: 1`) returning the **currently logged-in** user as JSON, matching the behavior of `/Users/evgenyusorov/workspace/java/authspring/app/src/main/java/com/authspring/api/web/UserController.java` (`currentUser`). **Do not** add other auth or user endpoints.

**Architecture:** Reuse existing stack: **`JwtAuthenticationFilter`** sets **`UserPrincipal`** from `Authorization: Bearer <jwt>`; **`SecurityConfig`** already requires authentication for `/api/**` except whitelisted routes, so `GET /api/user` is protected. Add a dedicated **`CurrentUserController`** in **`com.authspring.api.web`** (name avoids clashing with **`com.challenges.api.web.UserController`** at `/api/users`). Use **`@RequiresAuth`** + **`@AuthenticationPrincipal UserPrincipal`**, reload **`com.challenges.api.model.User`** via **`UserRepository.findById(principal.getId())`**, map with **`AuthUserResponse.fromEntity`** (same shape as login/register `user`). Return **404** if the user row disappeared after token issue.

**Tech stack:** Spring Boot 4, Spring Security 7, MockMvc ITs against PostgreSQL `challengestest` (existing test profile), JJWT.

**Reference (sibling repo, read-only):** `authspring/.../web/UserController.java` — copy the **single** `GET /user` method pattern; swap `UserResponse` → **`AuthUserResponse`**, `UserRepository` package → **`com.challenges.api.repo.UserRepository`**.

---

## File map

| Path | Responsibility |
|------|----------------|
| `src/main/java/com/authspring/api/web/CurrentUserController.java` | **`GET /api/user`**, `@RequiresAuth`, `UserRepository`, `AuthUserResponse`. |
| `src/test/java/com/authspring/api/CurrentUserIT.java` | Login → Bearer → `GET /api/user` **200**; no Bearer **401** ProblemDetail (matches **`ProblemJsonAuthenticationEntryPoint`**). |
| `src/main/java/com/challenges/api/config/SecurityConfig.java` | **No change** if `/api/**` remains `authenticated()` and `GET /api/user` is not `permitAll`. |
| `src/main/java/com/authspring/api/security/RequiresAuth.java` | **Already present** — reuse. |
| `src/main/java/com/authspring/api/security/UserPrincipal.java` | **Already present** — reuse. |

**Out of scope:** Logout, register, password reset, email verify routes, `UserResponse` DTO from authspring (full Laravel fields), changing **`AuthUserResponse`** unless you explicitly want parity with authspring’s extra JSON fields later.

---

### Task 1: `CurrentUserController`

**Files:**
- Create: `src/main/java/com/authspring/api/web/CurrentUserController.java`

- [x] **Step 1: Add controller**

```java
package com.authspring.api.web;

import com.authspring.api.security.RequiresAuth;
import com.authspring.api.security.UserPrincipal;
import com.authspring.api.web.dto.AuthUserResponse;
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
	public ResponseEntity<AuthUserResponse> currentUser(@AuthenticationPrincipal UserPrincipal principal) {
		return userRepository
				.findById(principal.getId())
				.map(AuthUserResponse::fromEntity)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}
}
```

- [x] **Step 2: Compile**

Run: `./gradlew compileJava --no-daemon`  
Expected: BUILD SUCCESSFUL

- [x] **Step 3: Commit**

```bash
git add src/main/java/com/authspring/api/web/CurrentUserController.java
git commit -m "feat(auth): GET /api/user for current user"
```

---

### Task 2: Integration tests `CurrentUserIT`

**Files:**
- Create: `src/test/java/com/authspring/api/CurrentUserIT.java`

- [x] **Step 1: Write the failing test first (optional TDD)**

Create the file below, run tests **before** Task 1 if you want a red build; otherwise implement Task 1 first.

- [x] **Step 2: Add `CurrentUserIT`**

```java
package com.authspring.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.challenges.api.ChallengesApiApplication;
import com.challenges.api.model.User;
import com.challenges.api.repo.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = ChallengesApiApplication.class)
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class CurrentUserIT {

	private static final String API_VERSION = "API-Version";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void getUser_withBearer_returnsCurrentUserJson() throws Exception {
		userRepository.deleteAll();
		User user = new User("Ada", "ada@example.com", passwordEncoder.encode("secret"), "user");
		user.setEmailVerifiedAt(Instant.parse("2020-01-01T00:00:00Z"));
		userRepository.save(user);

		MvcResult login =
				mockMvc.perform(post("/api/login")
								.header(API_VERSION, "1")
								.contentType(MediaType.APPLICATION_JSON)
								.content("{\"email\":\"ada@example.com\",\"password\":\"secret\"}"))
						.andExpect(status().isOk())
						.andReturn();
		String token = JsonPath.read(login.getResponse().getContentAsString(), "$.token");

		mockMvc.perform(get("/api/user").header(API_VERSION, "1").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.email").value("ada@example.com"))
				.andExpect(jsonPath("$.name").value("Ada"))
				.andExpect(jsonPath("$.role").value("user"));
	}

	@Test
	void getUser_withoutBearer_returns401() throws Exception {
		mockMvc.perform(get("/api/user").header(API_VERSION, "1"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.title").value("Unauthorized"))
				.andExpect(jsonPath("$.detail").value("Authentication is required."));
	}
}
```

- [x] **Step 3: Run tests**

Run: `./gradlew test --no-daemon`  
Expected: BUILD SUCCESSFUL (requires Postgres `challengestest` and Flyway applied; if Flyway checksum errors after editing migrations, `DROP SCHEMA public CASCADE` on `challengestest` or `flyway repair`).

- [x] **Step 4: Commit**

```bash
git add src/test/java/com/authspring/api/CurrentUserIT.java
git commit -m "test(auth): GET /api/user with and without Bearer"
```

---

## Self-review

1. **Spec coverage:** Single endpoint `GET /api/user`, protected, JSON body aligned with **`AuthUserResponse`**, 401 without auth — Tasks 1–2 cover all of this. **404** when user deleted after token issue is covered by controller logic (no extra IT required for YAGNI).
2. **Placeholder scan:** No TBD/TODO; full Java for controller and IT included.
3. **Type consistency:** `UserPrincipal.getId()` → `UserRepository.findById(Long)` → **`AuthUserResponse.fromEntity(User)`** matches existing login/register types.

---

**Plan complete and saved to `docs/superpowers/plans/2026-04-19-06-api-current-user.md`. Two execution options:**

**1. Subagent-Driven (recommended)** — dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — execute tasks in this session using executing-plans, batch execution with checkpoints.

**Which approach?**
