# Check-ins: only the check-in author may update or delete

**Status:** Done (2026-04-21)  
**Source:** [`docs/superpowers/specs/2026-04-21-main-workflows-api-design.md`](../../superpowers/specs/2026-04-21-main-workflows-api-design.md) §5

**Implemented:** **`PUT`** / **`DELETE /api/check-ins/{id}`** require **Bearer JWT** (**401** without). **`CheckInService.replace` / `delete`** take **`actorUserId`**, reuse **`assertViewerMayReadCheckInsForChallenge`** (**404** when the viewer may not read check-ins for that challenge), then require **`CheckIn.user`** = **`actorUserId`** (**403** otherwise, including challenge **owner** editing another participant’s row). Missing check-in → **404**. Tests: **`CheckInControllerIT`**.

## Problem

`PUT /api/check-ins/{id}` and `DELETE /api/check-ins/{id}` did not use **`UserPrincipal`**. Any client that knew a check-in id could replace or delete another user’s row.

## Product rule

Only the **user who owns the check-in** (the persisted **`user_id`** / **`CheckIn.user`**) may **update** or **delete** it. This is **not** “challenge owner” unless they are the same person.

## References

- `CheckInController.replace`, `CheckInController.delete`, `CheckInService.replace`, `CheckInService.delete`, `CheckIn` model
