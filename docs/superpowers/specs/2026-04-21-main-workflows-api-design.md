# Main product workflows and API mapping

**Date:** 2026-04-21  
**Scope:** Describes end-to-end user workflows and the **challenges API** calls that implement them (or will). For every call, send header **`API-Version: 1`**. Unless noted, paths are under **`/api`**.

**Auth today:** Clients should send **`Authorization: Bearer <JWT>`** after **`POST /api/login`** or register flows. **`SecurityConfig`** currently **`permitAll`**s **`/api/**`**; still wire the token so behavior stays correct when rules tighten.

**Status legend**

| Tag | Meaning |
|-----|--------|
| **OK** | Matches the described workflow in code today (behavior may still lack ideal auth checks — see notes). |
| **Partial** | Endpoint exists but semantics differ from the product story (visibility, scoping, or validation). |
| **Planned** | Not implemented in this repo yet; product intent only. |

**Tickets:** Backlog and done items live under [`docs/tickets/`](../../tickets/) and [`docs/tickets/done/`](../../tickets/done/). The **Tickets** column links the row to the tracking note when one exists; **—** means no dedicated ticket yet.

---

## 0. Auth and current user

| Step | Method | Path | Status | Tickets |
|------|--------|------|--------|---------|
| Register | `POST` | `/api/register` | OK | — |
| Log in | `POST` | `/api/login` | OK | — |
| Current user (JWT) | `GET` | `/api/user` | OK | — |

*Cross-cutting (tighten **`permitAll`** / JWT enforcement):* [`2026-04-21-security-tighten-api-authentication.md`](../../tickets/2026-04-21-security-tighten-api-authentication.md).

---

## 1. View challenges

### 1.1 Search public challenges and open one

| Step | Method | Path | Status | Tickets | Notes |
|------|--------|------|--------|---------|--------|
| Paginated list (public only) | `GET` | `/api/challenges?page=&size=` | **OK** | [`2026-04-21-challenge-public-list-text-search.md` (done)](../../tickets/done/2026-04-21-challenge-public-list-text-search.md) | Lists **`isPrivate = false`** only. Optional **`q`** (title/description substring), **`category`** (enum name), **`city`** (case-insensitive). Omitting filters = previous behavior. |
| Challenge categories | `GET` | `/api/categories` | OK | — | Enum-like listing for UI filters. |
| Single challenge | `GET` | `/api/challenges/{id}` | **OK** | [`2026-04-21-challenge-get-private-visibility.md` (done)](../../tickets/done/2026-04-21-challenge-get-private-visibility.md) | **Public:** any caller. **Private:** **404** unless JWT viewer is **owner**, **any participant**, or has a **usable `PENDING` invite** (same rule as join). Unauthenticated callers never see private challenges. |

### 1.2 Owned private challenges

| Step | Method | Path | Status | Tickets | Notes |
|------|--------|------|--------|---------|--------|
| “My owned challenges” | `GET` | `/api/challenges/mine` | **OK** | [`2026-04-21-challenge-list-owned.md` (done)](../../tickets/done/2026-04-21-challenge-list-owned.md) | **Bearer JWT** required (**401** without). Paged **`ChallengeResponse`** for **`owner_user_id =` current user** (public and private). Same **`page`/`size`** defaults as **`GET /api/challenges`**. |

### 1.3 Private challenges the user was invited to

| Step | Method | Path | Status | Tickets | Notes |
|------|--------|------|--------|---------|--------|
| Discover via invites | `GET` | `/api/invites?role=&challengeId=&page=` | **OK** | [`2026-04-21-invites-list-scope-current-user.md` (done)](../../tickets/done/2026-04-21-invites-list-scope-current-user.md) | **Bearer JWT** required (**401** without). **`role`**: **`RECEIVED`** (default) = you are **invitee**; **`SENT`** = you are **inviter**. Optional **`challengeId`** narrows within that scope. No global enumeration. |
| Open challenge | `GET` | `/api/challenges/{id}` | **OK** | [`2026-04-21-challenge-get-private-visibility.md` (done)](../../tickets/done/2026-04-21-challenge-get-private-visibility.md) | Same visibility as **1.1** (private requires access). |

### 1.4 Participants for a challenge

| Step | Method | Path | Status | Tickets | Notes |
|------|--------|------|--------|---------|--------|
| List participants | `GET` | `/api/challenges/{challengeId}/participants?page=` | **OK** | [`2026-04-21-participants-list-access-control.md` (done)](../../tickets/done/2026-04-21-participants-list-access-control.md) | Same visibility as **`GET /api/challenges/{id}`**: **public** — any caller; **private** — **404** unless viewer is **owner**, **participant**, or has a **usable `PENDING` invite**. Optional JWT via **`UserPrincipal`**. |

### 1.5 Participant sees other participants’ check-ins on one challenge

| Step | Method | Path | Status | Tickets | Notes |
|------|--------|------|--------|---------|--------|
| List check-ins for challenge | `GET` | `/api/challenges/{challengeId}/check-ins?page=` | **OK** | [`2026-04-21-check-ins-read-participant-only.md` (done)](../../tickets/done/2026-04-21-check-ins-read-participant-only.md) | **Bearer JWT** required (**404** without). Viewer must be **owner** or have any **participant** row for the challenge (challenge-wide or subtask-scoped). Challenge must be visible to the viewer via **`ChallengeService.findByIdForViewer`** (stricter than **§1.1**: a **usable `PENDING` invite** alone does **not** grant check-in read). Returns paged check-ins for the challenge. |
| Summaries after rollup | `GET` | `/api/challenges/{challengeId}/check-in-summaries` | **OK** | [`2026-04-21-check-ins-read-participant-only.md` (done)](../../tickets/done/2026-04-21-check-ins-read-participant-only.md) | Same read gate as list check-ins; **404** when rollup has not completed yet (unchanged). |
| Single check-in | `GET` | `/api/check-ins/{id}` | **OK** | [`2026-04-21-check-ins-read-participant-only.md` (done)](../../tickets/done/2026-04-21-check-ins-read-participant-only.md) | Same read gate as list (challenge inferred from the check-in). **404** if check-in missing or viewer not allowed. |

---

## 2. User participates (becomes a participant)

### 2.1 Join challenge

| Step | Method | Path | Status | Tickets | Notes |
|------|--------|------|--------|---------|--------|
| Self-join | `POST` | `/api/challenges/{id}/join` | OK | [`2026-04-21-challenge-join.md` (implementation plan)](../plans/2026-04-21-challenge-join.md) | **Authenticated user** (`UserPrincipal` / Bearer JWT). **Public** challenge: open join (challenge-wide **`Participant`**). **Private**: must have a usable **`PENDING`** **`Invite`** for the current user; **`ParticipantService.joinChallenge`** delegates to **`InviteService.acceptOldestUsablePendingInviteForJoin`** then ensures challenge-wide membership. **201** when a new challenge-wide **`Participant`** row is inserted, **200** when already challenge-wide. Product rules: [`2026-04-21-challenge-join-design.md`](2026-04-21-challenge-join-design.md). |

### 2.2 Accept an invite

| Step | Method | Path | Status | Tickets | Notes |
|------|--------|------|--------|---------|--------|
| Set invite to accepted | `PUT` | `/api/invites/{id}` | OK | [`2026-04-18-05-participant-on-invite-accept.md` (plan)](../plans/2026-04-18-05-participant-on-invite-accept.md) | Body can set **`status`** (e.g. **`ACCEPTED`**). **`InviteService`** syncs a **`Participant`** when status is **`ACCEPTED`** (challenge-wide vs subtask scope per invite; idempotent **`existsBy…`** checks). |
| Create invite (pending) | `POST` | `/api/invites` | OK | [`2026-04-21-invite-create-by-email-and-principal.md`](../../tickets/2026-04-21-invite-create-by-email-and-principal.md) · [`2026-04-18-04-rest-api-controllers.md` (plan, Task 10 — Invite)](../plans/2026-04-18-04-rest-api-controllers.md) | Original REST surface in **Task 10**; **§4** / backlog ticket for email + **`UserPrincipal`**. |

*(Join may also accept pending invites as part of **`POST .../join`** for private challenges.)*

---

## 3. Create and manage challenges

### 3.1 Create challenge + schedule; owner becomes participant

| Step | Method | Path | Status | Tickets | Notes |
|------|--------|------|--------|---------|--------|
| Create challenge | `POST` | `/api/challenges` | OK | — | **`ChallengeRequest`** includes **`private`**, dates, category, **`ownerUserId`**, optional location. **`ChallengeService.create`** adds **challenge-wide** **`Participant`** for owner if missing. |
| Attach schedule to challenge | `POST` | `/api/schedules` | OK | — | **`ScheduleCreateRequest`** with **`challengeId`**, **`kind`**, **`weekDays`** (parsed by service). |
| Attach schedule to subtask | `POST` | `/api/schedules` | OK | — | Use **`subTaskId`** instead of **`challengeId`**. |

### 3.2 Private challenges excluded from public listing

| Step | Method | Path | Status | Tickets | Notes |
|------|--------|------|--------|---------|--------|
| Set private on create/update | body | `POST /api/challenges`, `PUT /api/challenges/{id}` | OK | — | JSON field **`private`**. Owner-only **PUT** / **DELETE** tracked in **§3.4**. |
| Public list | `GET` | `/api/challenges` | OK | [`2026-04-21-challenge-public-list-text-search.md` (done)](../../tickets/done/2026-04-21-challenge-public-list-text-search.md) | **Non-private only** repository query. |

### 3.3 Subtasks (with own schedule)

| Step | Method | Path | Status | Tickets | Notes |
|------|--------|------|--------|---------|--------|
| List subtasks | `GET` | `/api/challenges/{challengeId}/subtasks` | OK | — | |
| Create subtask | `POST` | `/api/subtasks` | **Partial** | [`2026-04-21-subtasks-owner-only-mutations.md`](../../tickets/2026-04-21-subtasks-owner-only-mutations.md) | **`SubTaskRequest`**: **`challengeId`**, **`title`**, **`sortIndex`**. **Owner-only** restriction is **not** enforced in **`SubTaskService`** today. |
| Update / delete subtask | `PUT` `DELETE` | `/api/subtasks/{id}` | **Partial** | [`2026-04-21-subtasks-owner-only-mutations.md`](../../tickets/2026-04-21-subtasks-owner-only-mutations.md) | Same **no owner check**. |

### 3.4 Only owner edits challenge

| Step | Method | Path | Status | Tickets | Notes |
|------|--------|------|--------|---------|--------|
| Replace challenge | `PUT` | `/api/challenges/{id}` | **Partial** | [`2026-04-21-challenge-replace-delete-owner-only.md`](../../tickets/2026-04-21-challenge-replace-delete-owner-only.md) | **`ChallengeService.replace`** does **not** verify caller **owner** (only **`ownerUserId`** in body). **Upload image** **`POST /api/challenges/{id}/image`** **does** check owner vs **`UserPrincipal`**. |
| Delete challenge | `DELETE` | `/api/challenges/{id}` | **Partial** | [`2026-04-21-challenge-replace-delete-owner-only.md`](../../tickets/2026-04-21-challenge-replace-delete-owner-only.md) | No owner check. |

---

## 4. Invites

**Product:** Invite by **email**. **Target behavior:** if an invite already exists for the same pair/challenge (or email), **delete** the old row and **create** a fresh one.

| Step | Method | Path | Status | Tickets | Notes |
|------|--------|------|--------|---------|--------|
| Create invite | `POST` | `/api/invites` | **Partial** | [`2026-04-21-invite-create-by-email-and-principal.md`](../../tickets/2026-04-21-invite-create-by-email-and-principal.md) | **`InviteRequest`** uses **`inviterUserId`**, **`inviteeUserId`**, **`challengeId`**, optional **`subTaskId`**, **`status`**, **`expiresAt`**. **No email field** — client resolves **email → user id** (e.g. **`GET /api/users`**) or a future API adds email. |
| Replace-on-resend | — | — | **Planned** | [`2026-04-21-invite-replace-on-resend.md`](../../tickets/2026-04-21-invite-replace-on-resend.md) | **`InviteService.create`** does **not** delete prior invites for the same inviter/invitee/challenge. |

| Other invite ops | Method | Path | Status | Tickets |
|------------------|--------|------|--------|---------|
| List | `GET` | `/api/invites` | **OK** (see **1.3** — scoped + **`role`**) | [`2026-04-21-invites-list-scope-current-user.md` (done)](../../tickets/done/2026-04-21-invites-list-scope-current-user.md) |
| Get | `GET` | `/api/invites/{id}` | OK | — |
| Update | `PUT` | `/api/invites/{id}` | OK | — |
| Delete | `DELETE` | `/api/invites/{id}` | OK | — |

---

## 5. Participant check-ins

| Step | Method | Path | Status | Tickets | Notes |
|------|--------|------|--------|---------|--------|
| Create check-in | `POST` | `/api/check-ins` | **OK** | [`2026-04-21-check-in-create-participant-validation.md` (done)](../../tickets/done/2026-04-21-check-in-create-participant-validation.md) | **Bearer JWT** required (**401** without). Body **`userId`** must match the authenticated user (**403** otherwise). **Owner** or **participant** only: challenge-wide rows require challenge-wide **`Participant`** (or owner); **`subTaskId`** set requires challenge-wide **or** matching subtask-scoped **`Participant`**. |
| Update / delete | `PUT` `DELETE` | `/api/check-ins/{id}` | OK | — | |

---

## 6. Check-in reminders (push)

| Step | Status | Tickets | Notes |
|------|--------|---------|--------|
| Daily analysis of challenge/subtask schedule | **Planned** | [`2026-04-21-reminder-scheduler-push-notification.md`](../../tickets/2026-04-21-reminder-scheduler-push-notification.md) | No scheduler or integration for “due today” push in this repo. **`CheckInRollupScheduler`** is for **rollup** of ended challenges, not reminders. |
| Static copy now; richer messages later | **Planned** | [`2026-04-21-reminder-message-templates.md`](../../tickets/2026-04-21-reminder-message-templates.md) | |
| User profile toggle to disable reminders | **Planned** | [`2026-04-21-user-reminders-opt-out.md`](../../tickets/2026-04-21-user-reminders-opt-out.md) | **`User`** entity has no **`remindersEnabled`** (or similar) field today. |

---

## 7. Challenge / subtask “group chat”

**Product:** Threaded discussion, reactions/likes, **visible only to participants**.

**Current API:** **Comments** (text **`body`**, no likes).

| Step | Method | Path | Status | Tickets | Notes |
|------|--------|------|--------|---------|--------|
| List comments (challenge-wide) | `GET` | `/api/challenges/{challengeId}/comments?page=` | **Partial** | [`2026-04-21-comments-participant-only-access.md`](../../tickets/2026-04-21-comments-participant-only-access.md) | Optional **`?subTaskId=`** for subtask thread. **No** participant-only gate. |
| Post comment | `POST` | `/api/challenges/{challengeId}/comments` | **Partial** | [`2026-04-21-comments-participant-only-access.md`](../../tickets/2026-04-21-comments-participant-only-access.md) | **`CommentRequest`**: **`userId`**, **`body`**, optional **`subTaskId`**. |
| Get / edit / delete comment | `GET` `PUT` `DELETE` | `/api/comments/{id}` | **Partial** | [`2026-04-21-comments-edit-delete-authorization.md`](../../tickets/2026-04-21-comments-edit-delete-authorization.md) | **AGENTS.md**: edit/delete **not** restricted by auth yet. |
| Likes / reactions | — | — | **Planned** | [`2026-04-21-comments-reactions-likes.md`](../../tickets/2026-04-21-comments-reactions-likes.md) | |

---

## Suggested reading order for implementers

1. **`AGENTS.md`** — vocabulary, JWT paths, join/create semantics.  
2. **`2026-04-21-challenge-join-design.md`** — private join + invite interaction (product contract).  
3. **`2026-04-21-challenge-join.md`** ([`docs/superpowers/plans/`](../plans/2026-04-21-challenge-join.md)) — implementation plan and task checklist for **`POST /api/challenges/{id}/join`**.  
4. **`2026-04-18-05-participant-on-invite-accept.md`** ([`docs/superpowers/plans/`](../plans/2026-04-18-05-participant-on-invite-accept.md)) — **`Participant`** sync when an invite becomes **`ACCEPTED`** (**`PUT /api/invites/{id}`**).  
5. OpenAPI: **`/v3/api-docs`**, Swagger UI **`/swagger-ui.html`**.

---

## Spec self-review (2026-04-21)

- **Placeholders:** None intentional; **Planned** rows capture future work explicitly.  
- **Consistency:** Workflow numbering follows the product brief; duplicate “3.3” in the brief is folded into **3.2** (private) + **3.3** (subtasks).  
- **Scope:** Single-doc map of flows ↔ API; detailed security hardening is out of scope here but gaps are called out.  
- **Ambiguity:** “Search” in **1.1** means **paginated discovery** until a query parameter or search endpoint exists.
