# Main product workflows and API mapping

**Date:** 2026-04-21  
**Scope:** Describes end-to-end user workflows and the **challenges API** calls that implement them (or will). For every call, send header **`API-Version: 1`**. Unless noted, paths are under **`/api`**.

**Auth today:** Clients should send **`Authorization: Bearer <JWT>`** after **`POST /api/login`** or register flows. **`SecurityConfig`** currently **`permitAll`**s **`/api/**`**; still wire the token so behavior stays correct when rules tighten.

**Status legend**

| Tag | Meaning |
|-----|---------|
| **OK** | Matches the described workflow in code today (behavior may still lack ideal auth checks — see notes). |
| **Partial** | Endpoint exists but semantics differ from the product story (visibility, scoping, or validation). |
| **Planned** | Not implemented in this repo yet; product intent only. |

---

## 0. Auth and current user

| Step | Method | Path | Status |
|------|--------|------|--------|
| Register | `POST` | `/api/register` | OK |
| Log in | `POST` | `/api/login` | OK |
| Current user (JWT) | `GET` | `/api/user` | OK |

---

## 1. View challenges

### 1.1 Search public challenges and open one

| Step | Method | Path | Status | Notes |
|------|--------|------|--------|--------|
| Paginated list (public only) | `GET` | `/api/challenges?page=&size=` | **Partial** | Lists **`isPrivate = false`** only (`ChallengeService.listChallenges`). Not a text “search” filter yet — paging only. |
| Challenge categories | `GET` | `/api/categories` | OK | Enum-like listing for UI filters. |
| Single challenge | `GET` | `/api/challenges/{id}` | **Partial** | Returns **404** only if id missing. **Private** challenges are **not** hidden by membership today (no visibility gate on `get`). |

### 1.2 Owned private challenges

| Step | Method | Path | Status | Notes |
|------|--------|------|--------|--------|
| “My owned challenges” | — | — | **Planned** | No **`ownerUserId`** or “mine” query on **`GET /api/challenges`**. Workaround: persist ids from **`POST /api/challenges`** or add a dedicated endpoint / filter later. |

### 1.3 Private challenges the user was invited to

| Step | Method | Path | Status | Notes |
|------|--------|------|--------|--------|
| Discover via invites | `GET` | `/api/invites?challengeId=&page=` | **Partial** | **`InviteController`** only filters by **`challengeId`**, not by **current invitee**. **`GET /api/invites`** without filter lists invites globally — **not suitable** for production “my invites” until scoped to the authenticated user. |
| Open challenge | `GET` | `/api/challenges/{id}` | **Partial** | Same visibility note as **1.1**. |

### 1.4 Participants for a challenge

| Step | Method | Path | Status | Notes |
|------|--------|------|--------|--------|
| List participants | `GET` | `/api/challenges/{challengeId}/participants?page=` | OK | Paged list; no participant-only guard documented in controller. |

### 1.5 Participant sees other participants’ check-ins on one challenge

| Step | Method | Path | Status | Notes |
|------|--------|------|--------|--------|
| List check-ins for challenge | `GET` | `/api/challenges/{challengeId}/check-ins?page=` | **Partial** | Returns **all** check-ins for that challenge (good for “see others”). **No** check that caller is a participant. |
| Summaries after rollup | `GET` | `/api/challenges/{challengeId}/check-in-summaries` | **Partial** | Only when rollup has run for ended challenges; **404** otherwise. Same lack of participant gate. |
| Single check-in | `GET` | `/api/check-ins/{id}` | OK | |

---

## 2. User participates (becomes a participant)

### 2.1 Join challenge

| Step | Method | Path | Status | Notes |
|------|--------|------|--------|--------|
| Self-join | `POST` | `/api/challenges/{id}/join` | OK | **Public** challenge: open join. **Private**: requires usable **`PENDING`** invite; service may auto-accept oldest valid invite. **201** first time, **200** if already challenge-wide participant. See `docs/superpowers/specs/2026-04-21-challenge-join-design.md`. |

### 2.2 Accept an invite

| Step | Method | Path | Status | Notes |
|------|--------|------|--------|--------|
| Set invite to accepted | `PUT` | `/api/invites/{id}` | OK | Body can set **`status`** (e.g. **`ACCEPTED`**). **`InviteService`** creates **`Participant`** when status is **`ACCEPTED`**. |
| Create invite (pending) | `POST` | `/api/invites` | OK | See **§4**. |

*(Join may also accept pending invites as part of **`POST .../join`** for private challenges.)*

---

## 3. Create and manage challenges

### 3.1 Create challenge + schedule; owner becomes participant

| Step | Method | Path | Status | Notes |
|------|--------|------|--------|--------|
| Create challenge | `POST` | `/api/challenges` | OK | **`ChallengeRequest`** includes **`private`**, dates, category, **`ownerUserId`**, optional location. **`ChallengeService.create`** adds **challenge-wide** **`Participant`** for owner if missing. |
| Attach schedule to challenge | `POST` | `/api/schedules` | OK | **`ScheduleCreateRequest`** with **`challengeId`**, **`kind`**, **`weekDays`** (parsed by service). |
| Attach schedule to subtask | `POST` | `/api/schedules` | OK | Use **`subTaskId`** instead of **`challengeId`**. |

### 3.2 Private challenges excluded from public listing

| Step | Method | Path | Status | Notes |
|------|--------|------|--------|--------|
| Set private on create/update | body | `POST /api/challenges`, `PUT /api/challenges/{id}` | OK | JSON field **`private`**. |
| Public list | `GET` | `/api/challenges` | OK | **Non-private only** repository query. |

### 3.3 Subtasks (with own schedule)

| Step | Method | Path | Status | Notes |
|------|--------|------|--------|--------|
| List subtasks | `GET` | `/api/challenges/{challengeId}/subtasks` | OK | |
| Create subtask | `POST` | `/api/subtasks` | **Partial** | **`SubTaskRequest`**: **`challengeId`**, **`title`**, **`sortIndex`**. **Owner-only** restriction is **not** enforced in **`SubTaskService`** today. |
| Update / delete subtask | `PUT` `DELETE` | `/api/subtasks/{id}` | **Partial** | Same **no owner check**. |

### 3.4 Only owner edits challenge

| Step | Method | Path | Status | Notes |
|------|--------|------|--------|--------|
| Replace challenge | `PUT` | `/api/challenges/{id}` | **Partial** | **`ChallengeService.replace`** does **not** verify caller **owner** (only **`ownerUserId`** in body). **Upload image** **`POST /api/challenges/{id}/image`** **does** check owner vs **`UserPrincipal`**. |
| Delete challenge | `DELETE` | `/api/challenges/{id}` | **Partial** | No owner check. |

---

## 4. Invites

**Product:** Invite by **email**. **Target behavior:** if an invite already exists for the same pair/challenge (or email), **delete** the old row and **create** a fresh one.

| Step | Method | Path | Status | Notes |
|------|--------|------|--------|--------|
| Create invite | `POST` | `/api/invites` | **Partial** | **`InviteRequest`** uses **`inviterUserId`**, **`inviteeUserId`**, **`challengeId`**, optional **`subTaskId`**, **`status`**, **`expiresAt`**. **No email field** — client resolves **email → user id** (e.g. **`GET /api/users`**) or a future API adds email. |
| Replace-on-resend | — | — | **Planned** | **`InviteService.create`** does **not** delete prior invites for the same inviter/invitee/challenge. |

| Other invite ops | Method | Path | Status |
|------------------|--------|------|--------|
| List | `GET` | `/api/invites` | **Partial** (see **1.3**) |
| Get | `GET` | `/api/invites/{id}` | OK |
| Update | `PUT` | `/api/invites/{id}` | OK |
| Delete | `DELETE` | `/api/invites/{id}` | OK |

---

## 5. Participant check-ins

| Step | Method | Path | Status | Notes |
|------|--------|------|--------|--------|
| Create check-in (challenge-wide) | `POST` | `/api/check-ins` | **Partial** | **`CheckInRequest`**: **`userId`**, **`challengeId`**, **`checkDate`**, optional **`subTaskId`**. **Does not** verify **`userId`** is a **participant** for that challenge/subtask. |
| Create check-in (subtask) | `POST` | `/api/check-ins` | **Partial** | Set **`subTaskId`**; subtask must belong to **`challengeId`**. |
| Update / delete | `PUT` `DELETE` | `/api/check-ins/{id}` | OK | |

---

## 6. Check-in reminders (push)

| Step | Status | Notes |
|------|--------|--------|
| Daily analysis of challenge/subtask schedule | **Planned** | No scheduler or integration for “due today” push in this repo. **`CheckInRollupScheduler`** is for **rollup** of ended challenges, not reminders. |
| Static copy now; richer messages later | **Planned** | |
| User profile toggle to disable reminders | **Planned** | **`User`** entity has no **`remindersEnabled`** (or similar) field today. |

---

## 7. Challenge / subtask “group chat”

**Product:** Threaded discussion, reactions/likes, **visible only to participants**.

**Current API:** **Comments** (text **`body`**, no likes).

| Step | Method | Path | Status | Notes |
|------|--------|------|--------|--------|
| List comments (challenge-wide) | `GET` | `/api/challenges/{challengeId}/comments?page=` | **Partial** | Optional **`?subTaskId=`** for subtask thread. **No** participant-only gate. |
| Post comment | `POST` | `/api/challenges/{challengeId}/comments` | **Partial** | **`CommentRequest`**: **`userId`**, **`body`**, optional **`subTaskId`**. |
| Get / edit / delete comment | `GET` `PUT` `DELETE` | `/api/comments/{id}` | **Partial** | **AGENTS.md**: edit/delete **not** restricted by auth yet. |
| Likes / reactions | — | — | **Planned** | |

---

## Suggested reading order for implementers

1. **`AGENTS.md`** — vocabulary, JWT paths, join/create semantics.  
2. **`2026-04-21-challenge-join-design.md`** — private join + invite interaction.  
3. OpenAPI: **`/v3/api-docs`**, Swagger UI **`/swagger-ui.html`**.

---

## Spec self-review (2026-04-21)

- **Placeholders:** None intentional; **Planned** rows capture future work explicitly.  
- **Consistency:** Workflow numbering follows the product brief; duplicate “3.3” in the brief is folded into **3.2** (private) + **3.3** (subtasks).  
- **Scope:** Single-doc map of flows ↔ API; detailed security hardening is out of scope here but gaps are called out.  
- **Ambiguity:** “Search” in **1.1** means **paginated discovery** until a query parameter or search endpoint exists.
