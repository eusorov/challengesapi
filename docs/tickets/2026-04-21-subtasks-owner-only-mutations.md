# Subtasks: owner-only create, update, delete

**Status:** Partial  
**Source:** [`docs/superpowers/specs/2026-04-21-main-workflows-api-design.md`](../superpowers/specs/2026-04-21-main-workflows-api-design.md) §3.3

## Problem

`POST /api/subtasks`, `PUT /api/subtasks/{id}`, and `DELETE /api/subtasks/{id}` do not verify that the caller is the **challenge owner**. Any client can mutate subtasks if they know ids.

## Scope

- Pass **`UserPrincipal`** (or user id) into service layer; compare to **`challenge.owner`** for create (via `challengeId`) and for update/delete (via subtask → challenge).
- Return **403** or **404** per API style guide.
- Integration tests: non-owner denied; owner succeeds.

## Acceptance criteria

- Only the owning user can create, update, or delete subtasks for their challenge.

## References

- `SubTaskController`, `SubTaskService`, `Challenge` / `SubTask` model
