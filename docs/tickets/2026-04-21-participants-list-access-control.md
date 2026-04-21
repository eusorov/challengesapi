# Participants: restrict list to eligible viewers

**Status:** Partial  
**Source:** [`docs/superpowers/specs/2026-04-21-main-workflows-api-design.md`](../superpowers/specs/2026-04-21-main-workflows-api-design.md) §1.4 (noted gap)

## Problem

`GET /api/challenges/{challengeId}/participants` is marked **OK** in the workflow table but the spec notes there is **no participant-only guard**. For **private** challenges, participant lists should not be public.

## Scope

- Align with challenge **visibility**: only **owner**, **participants**, or users with appropriate **invite** access can list participants (same rules as `GET /api/challenges/{id}` after visibility ticket).
- Return **404** or **403** for unauthorized callers.
- Integration tests.

## Acceptance criteria

- Strangers cannot enumerate members of a private challenge.

## References

- `ParticipantController`, `ParticipantService`, [`2026-04-21-challenge-get-private-visibility.md`](2026-04-21-challenge-get-private-visibility.md)
