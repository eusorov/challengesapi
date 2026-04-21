# UI brief — Challenges product

Users create **challenges**, invite others, and record **check-ins** on a **schedule** (**daily** or **weekly on selected weekdays** per `ScheduleKind` in the API). Authoritative workflow mapping: [`docs/superpowers/specs/2026-04-21-main-workflows-api-design.md`](superpowers/specs/2026-04-21-main-workflows-api-design.md). Machine-readable contract: [`build/openapi/openapi.yaml`](../build/openapi/openapi.yaml) (regenerate with `./gradlew generateOpenApiDocs` when needed); live **`/v3/api-docs`** and **`/swagger-ui.html`** when the app runs.

## Product goals

- **Discover** public challenges (paginated list with optional **text**, **category**, **city** filters) and load category enums for filters.
- **Owned challenges** — signed-in owners list **all** challenges they created (public and private) for dashboards.
- **Invites** — onboard via **invites** (whole challenge or one **subtask**); **acceptance** (or **join** on private challenges with a usable pending invite) creates **`Participant`** with the right scope.
- **Self-join** — **`POST /api/challenges/{id}/join`**: public challenges allow open challenge-wide membership; private challenges require a usable **`PENDING`** invite for the current user.
- **Day-to-day** — **check-ins** (by date; optional **subtask**), **comments** (flat thread; product target is richer chat). Rolled-up **`check_in_summaries`** exist for retention/rollup only — **there is no HTTP list** for summaries; clients use **`GET .../check-ins`**.
- **Trust** — **JWT** after login/register; email verification and password reset where exposed; optional **cover image** per challenge when storage is configured.

## Visibility (high level)

- **`GET /api/challenges`** — **public (non-private) only**; optional **`q`**, **`category`**, **`city`**. Any caller.
- **`GET /api/challenges/{id}`** — **Public** challenge: any caller. **Private**: **404** unless the viewer (with JWT) is **owner**, **any participant**, or has a **usable `PENDING` invite** (same idea as join).
- **`GET .../participants`** — Same visibility as **`GET /api/challenges/{id}`** (including optional JWT / `UserPrincipal`).
- **`GET .../check-ins`** and **`GET /api/check-ins/{id}`** — **Stricter**: **Bearer JWT** required (**404** if missing). Viewer must be **owner** or **participant** (challenge-wide or subtask-scoped); challenge must pass **`findByIdForViewer`**. A **pending invite alone does not** grant check-in read.

## Main entities (what screens and models revolve around)

| Entity | UI role |
|--------|---------|
| **User** | Account, profile, auth; creates and owns challenges; inviter, invitee, participant, comment author. |
| **Challenge** | Hub: title, dates, description, owner, **private** flag, optional **city** / map location, subtasks, schedule, participants, check-ins, comments, optional image. |
| **Subtask** | Ordered step; invites and participation can be scoped to one subtask; check-ins and comments can attach to it. |
| **Schedule** | **DAILY** or **WEEKLY_ON_SELECTED_DAYS** + **weekDays** for the challenge or a subtask. |
| **Invite** | Pending / accepted / declined / cancelled; inviter, invitee, challenge, optional subtask, optional **expiresAt**. |
| **Participant** | Membership after accept or join; challenge-wide or subtask-scoped. Owner gets a challenge-wide participant on create. |
| **Check-in** | Progress on a **checkDate**, per user and challenge, optional subtask; **only the author** may update/delete. |
| **Comment** | **body** on challenge or subtask thread; list/filter by optional **`subTaskId`**. Access/author rules are **not fully aligned** with “participant-only” yet (see spec §7). |

## Typical UI flows

1. **Register / log in** → JWT → **`GET /api/user`** for the authenticated profile.
2. **Browse** → **`GET /api/categories`** (filter chips) + **`GET /api/challenges`** with optional **`q`**, **`category`**, **`city`**, **`page`/`size`**. Open **`GET /api/challenges/{id}`** when allowed.
3. **My challenges** → **`GET /api/challenges/mine`** (Bearer; owned public + private).
4. **Invites** → **`GET /api/invites?role=RECEIVED`** (default) or **`role=SENT`**; optional **`challengeId`**. Accept → **`PUT /api/invites/{id}`** with **`ACCEPTED`** (and related statuses as supported).
5. **Join** → **`POST /api/challenges/{id}/join`** (Bearer); **201** first time, **200** if already challenge-wide participant.
6. **Create / edit challenge** → **`POST` / `PUT /api/challenges`**; **`PUT`** requires **`ownerUserId`** matching JWT subject; owner-only **delete**. Then schedules and subtasks as below.
7. **Check-ins** → list **`GET /api/challenges/{challengeId}/check-ins`**; create **`POST /api/check-ins`** with **`userId`** = current user; edit/delete only as **author**.
8. **Comments** → **`GET/POST .../comments`** with optional **`subTaskId`** query on list.

## Main API endpoints

**Common:** base path **`/api`**, header **`API-Version: 1`**. Use **`Content-Type: application/json`** for JSON bodies unless the route is form/multipart (auth forms, register, image upload).

**Auth:** Most **`/api/**`** routes expect **`Authorization: Bearer …`**. **`SecurityConfig`** defines the exact **`permitAll`** set (login, register, **`POST /api/users`**, email verify link, forgot/reset password, etc.). The workflows spec notes **`permitAll` may still apply broadly** in some builds — still send the token so behavior stays correct when rules tighten.

*(Springdoc may emit duplicate paths with and without a trailing slash; callers can use either form.)*

### Authentication and current user

| Method | Path | Notes |
|--------|------|--------|
| POST | `/api/login` | **JSON**, **form**, or **multipart** (see OpenAPI). |
| POST | `/api/logout` | Bearer. |
| POST | `/api/register` | Form or multipart (not JSON in controller). |
| GET | `/api/user` | Bearer; current user. |
| GET | `/api/email/verify/{id}/{hash}` | Public (e-mail link). |
| POST | `/api/email/verification-notification` | Resend verification (form-style in API). |
| POST | `/api/forgot-password` | Form / multipart. |
| POST | `/api/reset-password` | Form / multipart. |

### Users

| Method | Path | Notes |
|--------|------|--------|
| GET | `/api/users` | Paginated (`page`, `size` / `Pageable`). |
| GET | `/api/users/{id}` | |
| POST | `/api/users` | Public registration-style; JSON **`UserRequest`**. |
| PUT | `/api/users/{id}` | |
| DELETE | `/api/users/{id}` | |

### Categories and challenges

| Method | Path | Notes |
|--------|------|--------|
| GET | `/api/categories` | Array of **ChallengeCategory** enum names for filters. |
| GET | `/api/challenges` | **Public catalog** — non-private only. Optional **`q`**, **`category`**, **`city`**; paginated. |
| GET | `/api/challenges/mine` | **Bearer** (**401** without). Owned challenges (public + private). |
| GET | `/api/challenges/{id}` | Public vs private visibility (see above). |
| POST | `/api/challenges` | **Bearer**; create; owner becomes challenge-wide **participant**. |
| PUT | `/api/challenges/{id}` | **Bearer**; **`ownerUserId`** must match JWT; owner-only. |
| DELETE | `/api/challenges/{id}` | **Bearer**; owner-only. |
| POST | `/api/challenges/{id}/join` | **Bearer**; self-join (public or private + usable pending invite). |
| POST | `/api/challenges/{id}/image` | **Bearer**; **`multipart/form-data`** (`file`); owner-only. |

### Subtasks

| Method | Path | Notes |
|--------|------|--------|
| GET | `/api/challenges/{challengeId}/subtasks` | **Unauthenticated** (read). Returns JSON **array**. |
| GET | `/api/subtasks/{id}` | **Unauthenticated** (read). |
| POST | `/api/subtasks` | **Bearer**; **owner-only** mutations (**403** otherwise). |
| PUT | `/api/subtasks/{id}` | **Bearer**; owner-only. |
| DELETE | `/api/subtasks/{id}` | **Bearer**; owner-only. |

### Schedules

| Method | Path | Notes |
|--------|------|--------|
| GET | `/api/schedules/{id}` | |
| POST | `/api/schedules` | Create for **`challengeId`** or **`subTaskId`** + **`kind`** + **`weekDays`**. |
| PUT | `/api/schedules/{id}` | |
| DELETE | `/api/schedules/{id}` | |

### Invites

| Method | Path | Notes |
|--------|------|--------|
| GET | `/api/invites` | **Bearer** (**401** without). **`role`**: **`RECEIVED`** (default) or **`SENT`**; optional **`challengeId`**; paginated. |
| GET | `/api/invites/{id}` | **Bearer**. |
| POST | `/api/invites` | **Bearer**; **`InviteCreateRequest`** (**inviteeEmail**, **`challengeId`**, optional **`subTaskId`**, **`status`**, **`expiresAt`**); **owner-only** create. |
| PUT | `/api/invites/{id}` | **Bearer**; e.g. **`ACCEPTED`** → syncs **participant** per invite scope. |
| DELETE | `/api/invites/{id}` | **Bearer**. |

### Participants

| Method | Path | Notes |
|--------|------|--------|
| GET | `/api/challenges/{challengeId}/participants` | Paginated; visibility matches **`GET /api/challenges/{id}`**. |

### Check-ins

| Method | Path | Notes |
|--------|------|--------|
| GET | `/api/challenges/{challengeId}/check-ins` | **Bearer**; participant/owner read gate; paginated. |
| GET | `/api/check-ins/{id}` | **Bearer**; same read gate. |
| POST | `/api/check-ins` | **Bearer**; **`userId`** must match JWT; participant/owner create rules (challenge-wide vs subtask). |
| PUT | `/api/check-ins/{id}` | **Bearer**; **author-only** mutate (**403** for others). |
| DELETE | `/api/check-ins/{id}` | **Bearer**; **author-only**. |

*(No HTTP endpoint for rollup **summaries**; use check-in list.)*

### Comments

| Method | Path | Notes |
|--------|------|--------|
| GET | `/api/challenges/{challengeId}/comments` | Paginated; optional **`subTaskId`**. |
| POST | `/api/challenges/{challengeId}/comments` | **`CommentRequest`**: **`userId`**, **`body`**, optional **`subTaskId`**. |
| GET | `/api/comments/{id}` | |
| PUT | `/api/comments/{id}` | |
| DELETE | `/api/comments/{id}` | |

Spec §7: comment **participant-only** visibility and **author-only** edit/delete are **partially** implemented — verify tickets in **`docs/tickets/`** before hard-coding UI assumptions.

---

## API notes for UI builders

- Send **`API-Version: 1`** on requests to versioned controllers.
- Paginated endpoints use Spring **`Page`** / **`Pageable`** (typically **`page`**, **`size`**, **`sort`** — see **`Pageable`** in OpenAPI); responses expose **`content`**, **`totalElements`**, **`totalPages`**, etc.
- **`GET /api/challenges/{challengeId}/subtasks`** returns a **JSON array**, not a page.
- **Reminders / push** (spec §6) and **rich group chat** (spec §7) are **out of scope** for the API today; **rollup** runs for ended challenges, not “due today” notifications.
- Prefer **`build/openapi/openapi.yaml`** or live **`/v3/api-docs`** for schemas, enums, and try-it-out.
