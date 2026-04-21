# Challenges: owner-only replace and delete

**Status:** Partial  
**Source:** [`docs/superpowers/specs/2026-04-21-main-workflows-api-design.md`](../superpowers/specs/2026-04-21-main-workflows-api-design.md) §3.4

## Problem

`PUT /api/challenges/{id}` trusts **`ownerUserId`** in the JSON body and does not verify the **authenticated** user is the owner. `DELETE /api/challenges/{id}` has no owner check. This is inconsistent with `POST /api/challenges/{id}/image`, which checks owner against **`UserPrincipal`**.

## Scope

- **`replace`:** Require authenticated user to match existing challenge owner (or allow explicit admin role later); ignore or reject body `ownerUserId` mismatch per contract decision.
- **`delete`:** Only owner (or admin) may delete.
- Document breaking change if request body semantics change.
- Integration tests.

## Acceptance criteria

- Arbitrary users cannot replace or delete others’ challenges.

## References

- `ChallengeController.replace`, `ChallengeController.delete`, `ChallengeService.replace`, `ChallengeService.delete`
