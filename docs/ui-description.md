# UI brief — Challenges product

Users create challenges, share them with other users, and record **check-ins** according to a **schedule** (daily or a few specific days per week).

## Product goals

- **Run group habits and goals** as time-bounded **challenges** with clear dates and optional steps (**subtasks**).
- **Onboard people** via **invites** (whole challenge or one subtask); **acceptance** makes someone a **participant** with the right scope.
- **Keep momentum** with **check-ins** (by day; optionally tied to a subtask), **comments** for discussion, and **recurring rules** (**schedules**: e.g. daily or specific weekdays).
- **Identity and trust**: sign-in (**JWT**), email verification and password flows where the API exposes them; optional **cover image** per challenge when storage is configured.

## Main entities (what screens and models revolve around)

| Entity | UI role |
|--------|---------|
| **User** | Account, profile, auth; creates and owns challenges; can be inviter, invitee, participant, comment author. |
| **Challenge** | Central “hub” screen: title, dates, description, owner, subtasks list, schedule, participants, check-ins, comments, optional image. |
| **Subtask** | Ordered step inside a challenge; invites and participation can be scoped to one subtask; check-ins and comments can attach to it. |
| **Schedule** | Defines *when* activity is expected (daily, weekly on selected days, etc.) for the challenge or a subtask. |
| **Invite** | Outbound/inbound requests to join; states (e.g. pending → accepted); ties inviter, invitee, challenge, optional subtask. |
| **Participant** | Confirmed membership (challenge-wide or subtask-scoped) after accept or other flows the product allows. |
| **Check-in** | Logged progress on a date, per user and challenge, optionally per subtask. |
| **Comment** | Thread-style discussion on the challenge or on a specific subtask. |

## Typical UI flows

1. **Register / log in** → session (Bearer token).
2. **Create or open a challenge** → edit details, subtasks, schedule, upload cover image (if enabled).
3. **Invite others** → list/filter **invites**; invitee sees pending invites and **accepts** → appears as **participant**.
4. **Day-to-day** → **check-ins** list (often newest first), **comments** list (paginated in the API).
5. **Users directory** (admin or social features, depending on product) → paginated **users** list.

## Main API endpoints

**Common:** base path **`/api`**, header **`API-Version: 1`**. Unless noted, use **`Content-Type: application/json`** for bodies.

**Auth:** most routes require an **`Authorization: Bearer …`** header with a valid JWT. Typical **public** routes include login, register, user self-registration, email verification link, forgot/reset password, and **`POST /api/users`** (see server **`SecurityConfig`** for the exact allowlist).

### Authentication and current user

| Method | Path | Notes |
|--------|------|--------|
| POST | `/api/login` | JSON or form; returns token payload on success. |
| POST | `/api/logout` | **Bearer**; revokes token/session handling server-side. |
| POST | `/api/register` | Form or multipart (not JSON in controller). |
| GET | `/api/user` | **Bearer**; current user profile. |
| GET | `/api/email/verify/{id}/{hash}` | Public link target (e-mail CTA). |
| POST | `/api/email/verification-notification` | **Bearer**; form; resend verification e-mail. |
| POST | `/api/forgot-password` | Form; request reset e-mail. |
| POST | `/api/reset-password` | Form; complete reset with token. |

### Users

| Method | Path | Notes |
|--------|------|--------|
| GET | `/api/users` | **Bearer**; paginated (`page`, `size`). |
| GET | `/api/users/{id}` | **Bearer**. |
| POST | `/api/users` | Public (registration-style); JSON body. |
| PUT | `/api/users/{id}` | **Bearer**. |
| DELETE | `/api/users/{id}` | **Bearer**. |

### Challenges

| Method | Path | Notes |
|--------|------|--------|
| GET | `/api/challenges` | **Bearer**; paginated. |
| GET | `/api/challenges/{id}` | **Bearer**. |
| POST | `/api/challenges` | **Bearer**; create. |
| PUT | `/api/challenges/{id}` | **Bearer**; replace. |
| DELETE | `/api/challenges/{id}` | **Bearer**. |
| POST | `/api/challenges/{id}/image` | **Bearer**; `multipart/form-data` (`file`); owner-only. |

### Subtasks

| Method | Path | Notes |
|--------|------|--------|
| GET | `/api/challenges/{challengeId}/subtasks` | **Bearer**; list (array, not paged). |
| GET | `/api/subtasks/{id}` | **Bearer**. |
| POST | `/api/subtasks` | **Bearer**; create. |
| PUT | `/api/subtasks/{id}` | **Bearer**. |
| DELETE | `/api/subtasks/{id}` | **Bearer**. |

### Schedules

| Method | Path | Notes |
|--------|------|--------|
| GET | `/api/schedules/{id}` | **Bearer**. |
| POST | `/api/schedules` | **Bearer**; create for challenge or subtask. |
| PUT | `/api/schedules/{id}` | **Bearer**. |
| DELETE | `/api/schedules/{id}` | **Bearer**. |

### Invites

| Method | Path | Notes |
|--------|------|--------|
| GET | `/api/invites` | **Bearer**; paginated; optional query **`challengeId`**. |
| GET | `/api/invites/{id}` | **Bearer**. |
| POST | `/api/invites` | **Bearer**; create invite. |
| PUT | `/api/invites/{id}` | **Bearer**; update status (e.g. accept). |
| DELETE | `/api/invites/{id}` | **Bearer**. |

### Participants

| Method | Path | Notes |
|--------|------|--------|
| GET | `/api/challenges/{challengeId}/participants` | **Bearer**; paginated. |

### Check-ins

| Method | Path | Notes |
|--------|------|--------|
| GET | `/api/challenges/{challengeId}/check-ins` | **Bearer**; paginated; newest-first in API. |
| GET | `/api/check-ins/{id}` | **Bearer**. |
| POST | `/api/check-ins` | **Bearer**; create. |
| PUT | `/api/check-ins/{id}` | **Bearer**. |
| DELETE | `/api/check-ins/{id}` | **Bearer**. |

### Comments

| Method | Path | Notes |
|--------|------|--------|
| GET | `/api/challenges/{challengeId}/comments` | **Bearer**; paginated; optional query **`subTaskId`**. |
| POST | `/api/challenges/{challengeId}/comments` | **Bearer**; create. |
| GET | `/api/comments/{id}` | **Bearer**. |
| PUT | `/api/comments/{id}` | **Bearer**. |
| DELETE | `/api/comments/{id}` | **Bearer**. |

---

## API notes for UI builders

- Send **`API-Version: 1`** on every request.
- Collection endpoints that return a **Spring `Page`** use query parameters **`page`** and **`size`** (default size is often **20** on the server); response shape includes **`content`**, **`totalElements`**, **`totalPages`**, etc.
- **`GET /api/challenges/{challengeId}/subtasks`** still returns a **JSON array** (not a page).
- Exact schemas and try-it-out: **`/swagger-ui.html`** and **`/v3/api-docs`** when the app is running.
