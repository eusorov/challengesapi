# List challenges owned by the current user

**Status:** Planned  
**Source:** [`docs/superpowers/specs/2026-04-21-main-workflows-api-design.md`](../superpowers/specs/2026-04-21-main-workflows-api-design.md) §1.2

## Problem

There is no API to list **my owned** challenges (including **private**). Clients must remember ids from `POST /api/challenges` or use workarounds.

## Scope

- Add `GET /api/challenges/mine` (or `?owner=self` with strict auth) returning paged **`ChallengeResponse`** for **`owner_user_id = current user`**.
- Include private challenges in this list only.
- Integration tests with JWT.

## Acceptance criteria

- Authenticated user sees all challenges they own; others’ challenges never appear.
- Pagination matches existing challenge list conventions.

## References

- `ChallengeController`, `ChallengeService`, `ChallengeRepository`
