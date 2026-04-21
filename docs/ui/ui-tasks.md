# React app guide: screens, actions, and API mapping

**Date:** 2026-04-21  
**Purpose:** When you build the **React** client (separate repo), use this doc to decide **which screens to add**, **what users can click or submit**, and **which HTTP endpoints load or persist data**. Canonical workflow and API semantics: [`docs/superpowers/specs/2026-04-21-main-workflows-api-design.md`](superpowers/specs/2026-04-21-main-workflows-api-design.md). Shorthand for endpoints and visibility: [`docs/ui-description.md`](ui-description.md). Contract detail: OpenAPI (`/v3/api-docs`, `/swagger-ui.html`).

---

## How to use this with the API

- **Headers (every request):** `API-Version: 1`.  
- **Authenticated requests:** `Authorization: Bearer <JWT>` after login/register.  
- **React-oriented:** use a small API client (fetch/axios) that injects those headers; keep the JWT in memory + refresh strategy as you prefer (this API does not document a refresh token flow here).  
- **401 / 403 / 404:** map to “login required,” “no permission,” and “not found or hidden” UX. **`SecurityConfig`** returns **401** for anonymous calls to protected **`/api/**`** routes; application logic may still return **404** for hidden private resources when a JWT is present—do not assume **403** for “exists but private.”

---

## App shell (cross-cutting)

**Suggested routes (examples):** `/`, `/login`, `/register`, `/challenges`, `/challenges/:id`, `/my-challenges`, `/invites`, `/invites/:id`, `/profile` (optional), password reset paths as needed.

| Area | UI | APIs (data) |
|------|----|-------------|
| **Top nav / drawer** | Links: Discover, My challenges, Invites (when logged in); Log in / Register or user menu + Log out | After login: **`GET /api/user`** to show name/email and to obtain **`id`** for bodies like **`ownerUserId`** / **`userId`** |
| **Session** | Login form → store JWT; logout clears token | **`POST /api/login`** (accept), **`POST /api/register`** (accept) |
| **Protected areas** | Redirect anonymous users to `/login` with return URL | Any route that calls **401**-requiring endpoints (e.g. **`GET /api/challenges/mine`**) |

---

## 0. Auth and current user — Spec §0

**Product goal:** Register or log in, then know the current user for all later screens.

### Screen: Register

| Action | Behavior | API |
|--------|----------|-----|
| Submit registration form | Validate fields client-side; on success navigate to login or auto-login | **`POST /api/register`** (body per OpenAPI) |

### Screen: Log in

| Action | Behavior | API |
|--------|----------|-----|
| Submit credentials | Store JWT; navigate to home or `returnUrl` | **`POST /api/login`** |

### Screen: Forgot password / Reset password (if product includes)

| Action | Behavior | API |
|--------|----------|-----|
| Request reset | Show confirmation (no email enumeration promises unless API documents them) | **`POST /api/forgot-password`** |
| Submit new password | After token/link from email | **`POST /api/reset-password`** |

### Screen: Email verification landing

| Action | Behavior | API |
|--------|----------|-----|
| Open link from email | Show success/failure message | **`GET /api/email/verify/{id}/{hash}`** (browser navigation or `window.location`) |

### Global (post-auth bootstrap)

| Action | Behavior | API |
|--------|----------|-----|
| Load current user | Run once after login (or on app load if token present); cache in React context/query client | **`GET /api/user`** |

*Security hardening note:* [`docs/tickets/2026-04-21-security-tighten-api-authentication.md`](tickets/2026-04-21-security-tighten-api-authentication.md) — still send Bearer on protected routes.

---

## 1. View challenges — Spec §1

**Product goal:** Discover public challenges, list owned and invite-related challenges, open detail when allowed, see roster and (if member) check-ins.

### Screen: Discover (public browse) — §1.1

| UI element | Behavior | API |
|------------|----------|-----|
| Search / filters | Optional text **`q`**, category dropdown, city field; “Apply” or debounced fetch | **`GET /api/challenges`** with `page`, `size`, optional `q`, `category`, `city` (**read** — public only) |
| Category chips | Populate filter from server | **`GET /api/categories`** (**read**) |
| Challenge cards | Each card **link** → `/challenges/:id` | List payload from **`GET /api/challenges`** |
| Pagination | Next/previous or numbered pages | Same endpoint, `page` / `size` |

### Screen: Challenge detail (read-only core) — §1.1, §1.4, §1.5

| UI element | Behavior | API |
|------------|----------|-----|
| Page load | Fetch challenge; handle **404** as “not found or private” for logged-out users | **`GET /api/challenges/{id}`** (**read**) |
| Participants section | List roster with pagination | **`GET /api/challenges/{id}/participants`** (**read** — visibility matches challenge) |
| Check-ins / activity | **Only if** user is allowed: show list; otherwise hide section or show “join to see activity” | **`GET /api/challenges/{id}/check-ins`** (**read** — **Bearer** required; anonymous **401**; **404** if not owner/participant with correct visibility) |
| Subtasks list | Show steps (order, titles) | **`GET /api/challenges/{id}/subtasks`** (**read** — unauthenticated OK) |

### Screen: Check-in detail (modal or `/check-ins/:id`)

| UI element | Behavior | API |
|------------|----------|-----|
| Open one check-in | From activity list row **link** or tap | **`GET /api/check-ins/{id}`** (**read** — same gate as list) |

**Do not** add a UI surface for rollup summary tables: there is **no** public HTTP list for internal rollups; use **`GET .../check-ins`** only (§1.5).

### Screen: My challenges (owned) — §1.2

| UI element | Behavior | API |
|------------|----------|-----|
| Nav link | “My challenges” visible when authenticated | — |
| List | Paginated cards → detail | **`GET /api/challenges/mine`** (**read** — **401** without JWT) |

### Screen: Invites — received / sent — §1.3

| UI element | Behavior | API |
|------------|----------|-----|
| Tabs or sub-routes | “Inbox” vs “Sent” | **`GET /api/invites?role=RECEIVED`** (default) vs **`role=SENT`** (**read**); optional `challengeId`, pagination |
| Row tap | **Link** to challenge if user may open it | **`GET /api/challenges/{id}`** (**read**) |
| Row tap | **Link** to invite detail if you build **`/invites/:id`** | **`GET /api/invites/{id}`** (**read**) |

---

## 2. User participates — Spec §2

**Product goal:** Become a **challenge-wide** participant via **Join** or **Accept invite**.

### Screen: Challenge detail — Join — §2.1

| UI element | Behavior | API |
|------------|----------|-----|
| **Button** “Join challenge” | Show when product rules allow (e.g. public, or private + user has usable pending invite per design doc); hide if already challenge-wide participant | **`POST /api/challenges/{id}/join`** (**write** — **201** first time, **200** already member) |
| Error states | Map **403** / validation to clear copy (private without invite, etc.) | Same endpoint |

Design reference: [`docs/superpowers/specs/2026-04-21-challenge-join-design.md`](superpowers/specs/2026-04-21-challenge-join-design.md).

### Screen: Invite detail or inbox row actions — §2.2

| UI element | Behavior | API |
|------------|----------|-----|
| **Button** “Accept” | Sets invite accepted; refresh challenge + participant state | **`PUT /api/invites/{id}`** with body including **`status: ACCEPTED`** (**write**) |
| Optional: Decline / cancel | If product needs | **`PUT /api/invites/{id}`** with other **`status`** values (**write**) |

---

## 3. Create and manage challenges — Spec §3

**Product goal:** Owner creates challenge + schedules + subtasks; toggles privacy; edits or deletes only as owner.

### Screen: Create challenge (wizard or single form) — §3.1, §3.2

| UI element | Behavior | API |
|------------|----------|-----|
| Submit challenge | Include **`ownerUserId`** = current user **`id`** from **`GET /api/user`**; set **`private`** as needed | **`POST /api/challenges`** (**write**) |
| Add schedule(s) | After challenge exists, create schedule for challenge | **`POST /api/schedules`** with **`challengeId`**, cadence fields (**write**) |
| Explain privacy | Help text: private challenges **do not** appear on Discover | — |

### Screen: Add / edit subtasks and their schedules — §3.3

| UI element | Behavior | API |
|------------|----------|-----|
| List subtasks on detail/edit | Read-only list on detail; editable on owner “manage” mode | **`GET /api/challenges/{id}/subtasks`** (**read**) |
| **Button** “Add subtask” (owner) | Form: title, sort index | **`POST /api/subtasks`** (**write** — owner-only) |
| **Button** “Edit” / “Delete” (owner) | Per row | **`PUT /api/subtasks/{id}`**, **`DELETE /api/subtasks/{id}`** (**write**) |
| Attach schedule to subtask | After subtask exists | **`POST /api/schedules`** with **`subTaskId`** (**write**) |

### Screen: Edit challenge (owner) — §3.4

| UI element | Behavior | API |
|------------|----------|-----|
| **Button** “Edit challenge” | Form pre-filled from **`GET /api/challenges/{id}`** | **`PUT /api/challenges/{id}`** (**write** — body **`ownerUserId`** must match JWT subject; no ownership transfer) |
| **Button** “Delete challenge” | Confirm dialog; then navigate away | **`DELETE /api/challenges/{id}`** (**write**) |

### Screen: Challenge cover image (optional product feature)

| UI element | Behavior | API |
|------------|----------|-----|
| **Button** “Upload image” (owner) | `multipart/form-data` | **`POST /api/challenges/{id}/image`** (**write**) |

---

## 4. Invites (owner creates) — Spec §4

**Product goal:** Owner invites a **registered** user by email; invitee sees it in §1.3 / §2.2.

### Screen: Challenge detail — invite flow (owner only) — §4

| UI element | Behavior | API |
|------------|----------|-----|
| **Button** “Invite someone” | Modal: email, optional subtask, optional expiry | **`POST /api/invites`** (**write** — owner-only; **404** unknown email; **403** self-invite or not owner) |
| List pending invites (optional) | Could reuse **`GET /api/invites?role=SENT&challengeId=`** | **read** |

### Screen: Invite detail (owner or invitee)

| UI element | Behavior | API |
|------------|----------|-----|
| Load | Show status, challenge link | **`GET /api/invites/{id}`** (**read**) |
| **Button** “Revoke” / delete | If product needs | **`DELETE /api/invites/{id}`** (**write**) |

**Planned API behavior:** replace-on-resend — [`docs/tickets/2026-04-21-invite-replace-on-resend.md`](tickets/2026-04-21-invite-replace-on-resend.md). Until then, handle duplicate invites per current server behavior.

---

## 5. Participant check-ins (write) — Spec §5

**Product goal:** Members log progress; only the **author** edits or deletes their check-in.

### Screen: Challenge detail — log check-in — §5

| UI element | Behavior | API |
|------------|----------|-----|
| **Button** “Log check-in” | Form: date, optional note fields per DTO, optional **`subTaskId`**; **`userId`** must be current user | **`POST /api/check-ins`** (**write**) |
| Disable if not participant | Use participant/roster + rules from API docs | — |

### Screen: Check-in detail — edit own row — §5

| UI element | Behavior | API |
|------------|----------|-----|
| **Button** “Edit” | Only if **`checkIn.userId`** (or equivalent) matches current user | **`PUT /api/check-ins/{id}`** (**write**) |
| **Button** “Delete” | Confirm; same visibility as read | **`DELETE /api/check-ins/{id}`** (**write**) |

**Note:** Challenge **owner** cannot edit another user’s check-in (**403**).

---

## 6. Reminders (push) — Spec §6 — **not in API yet**

**Product goal (future):** Notifications when a check-in is due, opt-out, copy.

| Screen (future) | UI | API today |
|-----------------|----|-----------|
| Settings → Notifications | Toggles | **Planned** — see [`docs/tickets/2026-04-21-user-reminders-opt-out.md`](tickets/2026-04-21-user-reminders-opt-out.md) |
| In-app “due today” | Optional dashboard widget | **Planned** — [`docs/tickets/2026-04-21-reminder-scheduler-push-notification.md`](tickets/2026-04-21-reminder-scheduler-push-notification.md) |

No React implementation required for v1 unless you add purely client-side reminders without server truth.

---

## 7. Comments / group chat — Spec §7 — **partial alignment**

**Product goal (target):** Participant-only threads; **today** comments exist but access/authorization is only **partially** aligned—verify tickets before hard-coding strict UX.

### Screen: Challenge detail — comments — §7

| UI element | Behavior | API |
|------------|----------|-----|
| Thread list | Pagination; optional filter for subtask thread | **`GET /api/challenges/{challengeId}/comments`** optional `subTaskId` (**read**) |
| **Button** “Post” | Form: **`body`**, **`userId`** = self, optional **`subTaskId`** | **`POST /api/challenges/{challengeId}/comments`** (**write**) |
| Comment detail / edit | If you expose single-comment view | **`GET /api/comments/{id}`**, **`PUT /api/comments/{id}`**, **`DELETE /api/comments/{id}`** |

**Tickets:** [`docs/tickets/2026-04-21-comments-participant-only-access.md`](tickets/2026-04-21-comments-participant-only-access.md), [`docs/tickets/2026-04-21-comments-edit-delete-authorization.md`](tickets/2026-04-21-comments-edit-delete-authorization.md). **Reactions:** planned — [`docs/tickets/2026-04-21-comments-reactions-likes.md`](tickets/2026-04-21-comments-reactions-likes.md).

---

## Optional: user directory

If the product needs picking users by id (usually you **invite by email** instead):

| Screen | UI | API |
|--------|----|-----|
| Admin / dev only | Paginated table | **`GET /api/users`** (**read** — confirm auth requirements in OpenAPI) |

---

## Suggested implementation order (React)

1. API client + auth context (**§0**).  
2. Discover + challenge detail read path + participants + subtasks (**§1.1, §1.4**, §3.3 read).  
3. My challenges + Invites lists (**§1.2, §1.3**).  
4. Join + accept invite (**§2**).  
5. Create / edit / delete challenge + schedules + subtasks (**§3**).  
6. Owner invite create (**§4**).  
7. Check-ins read when member + create / edit / delete own (**§1.5, §5**).  
8. Comments behind a feature flag until tickets close (**§7**).  
9. Reminders when API exists (**§6**).

---

## Traceability

| This guide | Spec |
|------------|------|
| Sections **0–7** | [`2026-04-21-main-workflows-api-design.md`](superpowers/specs/2026-04-21-main-workflows-api-design.md) same numbers |

---

## Doc self-review (2026-04-21)

- Each workflow maps **screens → actions (links/buttons/forms) → HTTP method + path**.  
- **§6** / **§7** scoped so React work does not over-commit to unfinished API behavior.  
- Rollup summaries explicitly excluded from UI (no list endpoint).
