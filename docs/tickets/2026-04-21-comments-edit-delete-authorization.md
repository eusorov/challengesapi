# Comments: authorize edit and delete

**Status:** Partial  
**Source:** [`docs/superpowers/specs/2026-04-21-main-workflows-api-design.md`](../superpowers/specs/2026-04-21-main-workflows-api-design.md) §7; `AGENTS.md`

## Problem

`PUT` and `DELETE /api/comments/{id}` are not restricted: any caller may edit or remove any comment.

## Scope

- Allow **author** or **challenge owner** (and optional admin role) to update/delete; return **403** otherwise.
- Use authenticated principal in controller/service.
- Integration tests: stranger denied; author allowed.

## Acceptance criteria

- Comments cannot be mutated by arbitrary users.

## References

- `CommentController`, `CommentService`, `Comment` model (`author`)
