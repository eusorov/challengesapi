# Comments: reactions / likes

**Status:** Planned  
**Source:** [`docs/superpowers/specs/2026-04-21-main-workflows-api-design.md`](../superpowers/specs/2026-04-21-main-workflows-api-design.md) §7

## Problem

Product envisions **likes** or reactions on chat messages. Current **`Comment`** model is text-only **`body`**.

## Scope

- Data model: e.g. `comment_reactions` (user_id, comment_id, emoji or enum) with uniqueness per user per comment.
- REST: `POST/DELETE` toggle or `PUT` aggregate counts — pick one style; document in OpenAPI.
- Authorization: only **participants** can react; same visibility as comments.
- Pagination: include reaction summary on `CommentResponse` or separate endpoint.

## Acceptance criteria

- Participants can add/remove a like/reaction; counts or lists return in read APIs.
- Non-participants cannot react.

## References

- `Comment`, `CommentResponse`, `CommentController`
