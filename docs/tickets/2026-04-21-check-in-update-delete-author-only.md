# Check-ins: only the check-in author may update or delete

**Status:** Partial  
**Source:** [`docs/superpowers/specs/2026-04-21-main-workflows-api-design.md`](../superpowers/specs/2026-04-21-main-workflows-api-design.md) §5

## Problem

`PUT /api/check-ins/{id}` and `DELETE /api/check-ins/{id}` do not use **`UserPrincipal`**. Any client that knows a check-in id can replace or delete another user’s row. That is inconsistent with **`POST /api/check-ins`**, which ties creation to the authenticated user.

## Product rule

Only the **user who owns the check-in** (the persisted **`user_id`** / **`CheckIn.user`**, i.e. the person who logged that progress) may **update** or **delete** it. This is **not** the same as “challenge owner”: a challenge **owner** must **not** gain the right to edit or remove **another participant’s** check-ins via these endpoints unless product explicitly adds that later.

## Scope

- **`CheckInController.replace` / `delete`:** require **`UserPrincipal`** (**401** if missing).
- **`CheckInService.replace` / `delete`:** pass **`actorUserId`**; load check-in with associations; if missing → **404**; if **`checkIn.getUser().getId()`** ≠ **`actorUserId`** → **403** (`ResponseStatusException` or equivalent).
- Optionally align **`CheckInUpdateRequest`** with create semantics if the body ever carries a user id (today it may not; do not broaden trust in client-supplied user ids).
- Integration tests: author succeeds; other participant, challenge owner (non-author), and unauthenticated callers denied appropriately.

## Acceptance criteria

- Strangers and other challenge members cannot **`PUT`** or **`DELETE`** someone else’s check-in.
- The author can still update/delete their own rows when they pass existing read gates for the challenge (reuse or mirror patterns from **`create`** / **`findByIdForViewer`** as needed).

## References

- `CheckInController.replace`, `CheckInController.delete`, `CheckInService.replace`, `CheckInService.delete`, `CheckIn` model
