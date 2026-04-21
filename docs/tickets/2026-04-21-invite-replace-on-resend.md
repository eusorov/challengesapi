# Invites: delete previous invite when re-inviting same target

**Status:** Planned  
**Source:** [`docs/superpowers/specs/2026-04-21-main-workflows-api-design.md`](../superpowers/specs/2026-04-21-main-workflows-api-design.md) §4

## Problem

Product rule: if an invite already exists for the same **inviter + invitee + challenge** (and optional **subtask** scope), **delete** the old row and **create** a fresh one. `InviteService.create` always inserts a new row.

## Scope

- Define uniqueness (tuple of inviter, invitee, challenge, subtask nullable).
- In `create`, delete or supersede existing rows (pending and/or all non-accepted — product decision).
- Avoid breaking **`Participant`** rows if an **ACCEPTED** invite exists (clarify behavior).
- Tests for duplicate resend.

## Acceptance criteria

- Resending an invite does not leave multiple conflicting pending rows for the same logical invitation.

## References

- `InviteService.create`, `InviteRepository`, `Invite` entity
