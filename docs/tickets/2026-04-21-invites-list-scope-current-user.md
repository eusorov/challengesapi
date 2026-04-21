# Invites list: scope to current user (inviter / invitee)

**Status:** Partial  
**Source:** [`docs/superpowers/specs/2026-04-21-main-workflows-api-design.md`](../superpowers/specs/2026-04-21-main-workflows-api-design.md) §1.3, §4

## Problem

`GET /api/invites` without `challengeId` lists **all** invites in the system (paginated). There is no filter by **current invitee** or **inviter**, so it is unsafe for production “my invites” or “invites I sent.”

## Scope

- Require authentication for list (or return only caller-scoped data).
- Default or explicit query: e.g. `role=sent|received` or `inviteeUserId` / `inviterUserId` derived from **`UserPrincipal`** only (do not trust arbitrary user ids from query for cross-user data).
- Optional retain `challengeId` filter in combination.
- Integration tests: user A never sees user B’s invites.

## Acceptance criteria

- No global invite enumeration for normal API users.
- Document query parameters and defaults in OpenAPI.

## References

- `InviteController.list`, `InviteService.list`, `InviteRepository`
