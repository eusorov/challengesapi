# Challenge detail: enforce private visibility

**Status:** Done (2026-04-21)  
**Source:** [`docs/superpowers/specs/2026-04-21-main-workflows-api-design.md`](../superpowers/specs/2026-04-21-main-workflows-api-design.md) §1.1, §1.3

**Implemented:** Same branch/commit as list-filters ticket: `feature/challenge-search-and-private-visibility`, `1e800b4`. `GET /api/challenges/{id}` uses `findByIdForViewer` (owner, any participant, or usable `PENDING` invite); **404** for unauthorized private access; `InviteService.hasUsablePendingInvite`; tests in `ChallengeControllerIT`; spec updated in `2026-04-21-main-workflows-api-design.md`.

## Problem

`GET /api/challenges/{id}` returns **404** only when the id does not exist. **Private** challenges are readable by anyone who knows the id; there is no check for **owner**, **participant**, or **pending/usable invite**.

## Scope

- Define visibility rules (e.g. public → anyone; private → owner, any participant, or user with a relevant pending invite — align with join design).
- Return **404** (preferred) or **403** consistently when the caller must not see the challenge.
- Use **`UserPrincipal`** / authenticated user id; clarify behavior for unauthenticated requests to private resources.
- Update integration tests and workflow spec.

## Acceptance criteria

- Private challenges are not leaked to arbitrary users.
- Public challenges remain readable as today.
- OpenAPI / error contract documented.

## References

- `ChallengeController.get`, `ChallengeService.findByIdForViewer`
- [`2026-04-21-challenge-join-design.md`](../superpowers/specs/2026-04-21-challenge-join-design.md)
