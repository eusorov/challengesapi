# Challenge list: text search and discovery filters

**Status:** Done (2026-04-21)  
**Source:** [`docs/superpowers/specs/2026-04-21-main-workflows-api-design.md`](../superpowers/specs/2026-04-21-main-workflows-api-design.md) §1.1

**Implemented:** Branch `feature/challenge-search-and-private-visibility`, commit `1e800b4`. `GET /api/challenges` optional query params **`q`**, **`category`** (enum name), **`city`**; OpenAPI via `@Parameter` on `ChallengeController.list`; tests in `ChallengeControllerIT`, `ChallengeRepositoryTest`.

## Problem

`GET /api/challenges` returns only non-private challenges with pagination. There is no text search (title/description), category filter beyond client-side, or location-based discovery aligned with product “search public challenges.”

## Scope

- Add query parameters (or a dedicated search endpoint) for at least **title/description** match and optionally **category**, **city**, and pagination stability.
- Keep **private** challenges out of this listing (existing rule).
- Document OpenAPI behavior and add integration tests.

## Acceptance criteria

- Authenticated (or public, per product) callers can narrow the public challenge catalog without loading full pages client-side.
- Existing clients that omit new parameters behave as today (backward compatible).

## References

- `ChallengeController.list`, `ChallengeService.listChallenges`, `ChallengeRepository.findNonPrivateIdsOrderByIdAsc`
