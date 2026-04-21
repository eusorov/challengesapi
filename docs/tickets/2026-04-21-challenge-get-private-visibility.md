# Challenge detail: enforce private visibility

**Status:** Partial  
**Source:** [`docs/superpowers/specs/2026-04-21-main-workflows-api-design.md`](../superpowers/specs/2026-04-21-main-workflows-api-design.md) §1.1, §1.3

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

- `ChallengeController.get`, `ChallengeService.findById`
- [`2026-04-21-challenge-join-design.md`](../superpowers/specs/2026-04-21-challenge-join-design.md)
