# Security: require authentication for mutating and sensitive API routes

**Status:** Done (2026-04-21)  
**Source:** [`docs/superpowers/specs/2026-04-21-main-workflows-api-design.md`](../superpowers/specs/2026-04-21-main-workflows-api-design.md) — see **§ Security: HTTP authentication**.

## Problem (resolved)

`SecurityConfig` used **`requestMatchers("/api/**").permitAll()`**, so JWT was optional at the HTTP authorization layer. Controllers that assume **`UserPrincipal`** could break or behave inconsistently; **user id spoofing** in JSON was easier.

## Implementation

- **`src/main/java/com/challenges/api/config/SecurityConfig.java`** — explicit **`permitAll`** / **`authenticated()`** rules; **`/api/**`** defaults to **authenticated** after allowlisted routes (order matters: **`GET /api/challenges/mine`** before generic challenge **`GET`** patterns).
- **Public anonymous `GET`** (discovery): **`/api/categories`**, **`GET /api/challenges`** (list), **`GET /api/challenges/{id}`** (numeric id), **`/subtasks`** and **`/participants`** under that challenge, **`GET /api/subtasks/{id}`**.
- **Integration:** **`SecurityHttpAuthorizationIT`**, updates to tests that assumed anonymous access to protected invite/check-in routes.

## Acceptance criteria

- Unauthenticated callers cannot invoke protected routes (**401** via **`ProblemJsonAuthenticationEntryPoint`**).
- Integration tests cover allowlisted vs protected paths.

## References

- `SecurityConfig`, `JwtAuthenticationFilter`, `AGENTS.md`
