# Invites: create by invitee email and authenticated inviter

**Status:** Done (2026-04-21)  
**Source:** [`docs/superpowers/specs/2026-04-21-main-workflows-api-design.md`](../../superpowers/specs/2026-04-21-main-workflows-api-design.md) §4

**Implemented:** **`InviteCreateRequest`** replaces **`InviteRequest`** for **`POST /api/invites`**. **`InviteService.createForAuthenticatedInviter`** resolves **`inviteeEmail`** via **`UserRepository.findByEmailIgnoreCase`** (**404** if missing), sets **inviter** from **`UserPrincipal`** only, and requires the actor to be the **challenge owner** (**403** otherwise). Self-invite **403**. **`InviteController.create`** returns **401** without JWT. Tests: **`InviteControllerIT`**, updated **`ChallengeJoinControllerIT`**, **`ChallengeControllerIT`**, **`ChallengeDomainWorkflowIT`**.

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

- `InviteController.create`, `InviteService.createForAuthenticatedInviter`, `InviteCreateRequest`, `UserRepository`

## Breaking change

Clients must send **`inviteeEmail`** + **`challengeId`** (and optional **`subTaskId`**, **`status`**, **`expiresAt`**) with a **Bearer** token. **`inviterUserId`** / **`inviteeUserId`** in the body are **removed**.
