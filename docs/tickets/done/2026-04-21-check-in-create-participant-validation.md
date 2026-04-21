# Check-ins: require participant membership on create

**Status:** Done (2026-04-21)  
**Source:** [`docs/superpowers/specs/2026-04-21-main-workflows-api-design.md`](../../superpowers/specs/2026-04-21-main-workflows-api-design.md) §5

**Implemented:** **`POST /api/check-ins`** requires **Bearer JWT** (**401** without). **`userId`** in JSON must equal **`UserPrincipal.getId()`** (**403** otherwise). **`CheckInService.create`** enforces **owner** or **participant**: challenge-wide check-ins need **`Participant`** with **`subTask` null** (or owner); subtask check-ins need challenge-wide membership **or** subtask-scoped membership for that **`subTaskId`**. **403** when not allowed. Tests: **`CheckInControllerIT`** (auth, mismatch, non-member, subtask-only scope); **`ChallengeDomainWorkflowIT`** uses per-user JWT on create.

## Problem

`POST /api/check-ins` accepts **`userId`** in the body but does not verify that user is a **participant** for **`challengeId`** (challenge-wide or subtask-scoped when **`subTaskId`** is set). Any client can record check-ins for arbitrary users.

## Scope

- Prefer deriving **user** from **`UserPrincipal`** and deprecate trusting **`userId`** from JSON (or require match).
- Validate **`Participant`** exists for (user, challenge) or (user, challenge, subtask) as appropriate.
- Return **403** or **404** when not a participant.
- Integration tests.

## Acceptance criteria

- Only participants can create check-ins for themselves (or document admin override if added later).

## References

- `CheckInController.create`, `CheckInService.create`, `ParticipantRepository`
