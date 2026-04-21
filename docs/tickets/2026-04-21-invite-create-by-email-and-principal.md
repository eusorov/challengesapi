# Invites: create by invitee email and authenticated inviter

**Status:** Partial  
**Source:** [`docs/superpowers/specs/2026-04-21-main-workflows-api-design.md`](../superpowers/specs/2026-04-21-main-workflows-api-design.md) §4

## Problem

`POST /api/invites` requires **`inviterUserId`** and **`inviteeUserId`**. Product flow is **invite by email**; clients must resolve email to user id elsewhere. Spoofing **inviterUserId** is possible while **`SecurityConfig`** permits broad access.

## Scope

- Extend or add DTO: e.g. **`inviteeEmail`** (and resolve to user, or return **404** if no user).
- Set **inviter** from **`UserPrincipal`** only; remove or ignore client-supplied **`inviterUserId`** on authenticated route.
- Enforce inviter is **challenge owner** (or allowed role) before creating invite.
- Migration / backward compatibility if old clients still send user ids.

## Acceptance criteria

- Typical flow: logged-in user invites an email; inviter cannot be forged.
- Document OpenAPI and error responses (unknown email, not owner).

## References

- `InviteController.create`, `InviteService.create`, `InviteRequest`, `UserRepository`
