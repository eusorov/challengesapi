# Challenge self-join API — design

## Context

- **`Participant`** links a **user** to a **challenge**, optionally scoped to a **`SubTask`** (`subtask_id` null = whole challenge).
- Today, a **`Participant`** row is created when an **`Invite`** moves to **`ACCEPTED`** (`InviteService`).
- **`Challenge.isPrivate`:** public listing shows non-private challenges; private challenges still exist and may be loaded by id when the client knows it.
- **Check-ins** do not currently require a `Participant` row; this spec does not change check-in rules.

## Goals

1. Expose **`POST /api/challenges/{id}/join`** so an **authenticated user** can become a **challenge-wide** participant (or subtask-scoped when joining via a pending invite), with rules aligned to product choice **C2 + C3** (see below).
2. Ensure the **challenge owner** is always represented as a **challenge-wide** participant without relying on a separate join call.
3. Keep **invite accept** semantics consistent: joining on a **private** challenge with a **pending** invite **accepts** that invite and creates the same **`Participant`** shape as today’s **`ACCEPTED`** path.

## Non-goals

- **Open join** to private challenges **without** a pending invite (for non-owners).
- **Unauthenticated** join.
- **Admin** or **moderation** APIs (cap participants, ban, etc.).
- Changing **GET** challenge list visibility rules.
- **Request body** on join in v1 (no `userId` in JSON; caller is always the JWT subject).

## Product rules (approved)

| Actor | `isPrivate` | Rule |
|--------|-------------|------|
| **Owner** | any | Always allowed. Ensure **challenge-wide** `Participant` (`subtask_id` null). Idempotent. |
| **Non-owner** | `false` | Self-join: ensure **challenge-wide** `Participant`. Idempotent. |
| **Non-owner** | `true` | Join only if there is a **usable** **`PENDING`** **invite** for **`(invitee = caller, challenge = id)`**. On success: set invite **`ACCEPTED`**, persist, ensure **`Participant`** with **same subtask scope** as the invite (`null` = whole challenge). If **no** such invite → **403 Forbidden**. |

**Usable pending invite:** status is **`PENDING`**, and **`expires_at`** is **null** or **strictly after** the server’s current instant. If `expires_at` is in the past, treat as **no valid invite** (same outcome as missing invite for a non-owner on a private challenge).

**Multiple pending invites** for the same `(invitee_id, challenge_id)` are possible (no unique DB constraint today). **Deterministic rule:** select the pending row with the **smallest `id`** (oldest). Accept that row only; others remain **PENDING** until cancelled or updated separately (documented limitation; product may add a uniqueness rule later).

## Idempotency and participant shape

- **Challenge-wide membership:** before inserting, use the same existence checks as today: `ParticipantRepository.existsByUser_IdAndChallenge_IdAndSubTaskIsNull`.
- If the user **already** has a challenge-wide row, **`POST join`** returns **200 OK** with **`ParticipantResponse`** for that row (no duplicate insert).
- If the user has **only** subtask-scoped rows and joins a **public** challenge: **add** a **challenge-wide** row (they may then have both scopes).
- **HTTP status:** **201 Created** when a **new** `Participant` row was inserted; **200 OK** when the user was **already** a challenge-wide participant. Tests must assert this policy.

## API

- **Method / path:** `POST /api/challenges/{id}/join`
- **Headers:** `API-Version: 1`, `Authorization: Bearer <JWT>`
- **Body:** none
- **Success:** `ParticipantResponse` JSON (same shape as listing participants).
- **404:** challenge id does not exist.
- **403:** private challenge, caller is not owner, and no usable pending invite.
- **401:** unauthenticated (existing security behavior).

**Note:** Path spelling matches existing controllers: **`/api/challenges`**, not `challange`.

## Architecture

- **`ChallengeController`:** new handler delegating to **`ParticipantService.joinChallenge(challengeId, userId)`** (or equivalent name), passing **`UserPrincipal.getId()`**.
- **`ParticipantService`:** orchestrates: load challenge (or fail 404), apply owner / public / private rules, call **`InviteService`** for “find and accept pending invite + sync participant” on the private non-owner path, and **`ParticipantRepository`** for idempotent inserts on open join and owner paths.
- **`InviteService`:** add a focused method (e.g. accept pending invite by invitee + challenge for join) that: resolves the deterministic pending invite, sets **`ACCEPTED`**, saves, then reuses the **same** participant-sync logic as today (extract shared private method if needed to avoid duplication with **`ensureParticipantForAcceptedInvite`**).
- **`ChallengeService.create`:** after saving a new **`Challenge`**, ensure **`Participant(owner, challenge)`** exists if not already present (mirrors **C2** at creation time).

## Error handling

- Prefer **`ResponseStatusException`** or existing project patterns for 403/404 from services, consistent with other APIs (e.g. check-in summaries).
- Do not leak whether a **private** challenge exists to unauthorized joiners beyond what **`GET /api/challenges/{id}`** already exposes; **403** on join for “private and not allowed” is acceptable and clear.

## Security

- Caller identity **must** come from the JWT only; never trust a user id from the request body on this endpoint.

## Testing

- **Integration tests** (Spring MVC + security):  
  - Owner: participant exists after **create challenge**; **`POST join`** idempotent **200**.  
  - Public challenge: non-owner **201** then **200** on repeat.  
  - Private challenge: non-owner **403** without invite; with **PENDING** invite **201** and invite **ACCEPTED**; **`GET …/participants`** includes user.  
  - Expired **`expires_at`:** **403** for non-owner private join.  
  - Optional: two pending invites → oldest **`id`** is accepted.

## Documentation

- Update **`AGENTS.md`** REST section: document **`POST …/join`** and the owner auto-participant rule on create.

## Open decisions (resolved in this spec)

- **403** for disallowed private join (not **404**).
- **201 vs 200:** prefer **201** on first membership, **200** when already challenge-wide participant.
