# Security: require authentication for mutating and sensitive API routes

**Status:** Partial  
**Source:** [`docs/superpowers/specs/2026-04-21-main-workflows-api-design.md`](../superpowers/specs/2026-04-21-main-workflows-api-design.md) intro (Auth today)

## Problem

`SecurityConfig` uses **`requestMatchers("/api/**").permitAll()`**, so JWT is optional at the HTTP authorization layer. Controllers that assume **`UserPrincipal`** may break or behave inconsistently; **user id spoofing** in JSON is unchecked.

## Scope

- Replace blanket **permitAll** with explicit **permit** list: login, register, email verify, forgot/reset password, actuator as needed, optional public read endpoints if product requires.
- Require **authenticated** for everything else; align with `AGENTS.md` description.
- Audit each controller for **`@AuthenticationPrincipal`** on protected operations.
- Document CORS + anonymous reads if any remain public.

## Acceptance criteria

- Unauthenticated callers cannot invoke protected routes (**401** with existing entry point).
- Integration tests cover allowlisted vs protected paths.

## References

- `SecurityConfig`, `JwtAuthenticationFilter`, `AGENTS.md`
