# Comments: participant-only list and create

**Status:** Partial  
**Source:** [`docs/superpowers/specs/2026-04-21-main-workflows-api-design.md`](../superpowers/specs/2026-04-21-main-workflows-api-design.md) §7

## Problem

`GET` and `POST /api/challenges/{challengeId}/comments` do not verify the caller is a **participant** (or owner) of the challenge. **`CommentRequest.userId`** allows posting as another user.

## Scope

- Gate list/create on challenge membership (align with [`2026-04-21-challenge-get-private-visibility.md`](2026-04-21-challenge-get-private-visibility.md)).
- Set **author** from **`UserPrincipal`**; stop trusting **`userId`** in body or require equality.
- Optional: scope **subtask** thread to participants of that subtask if product requires it.

## Acceptance criteria

- Non-participants cannot read or post challenge/subtask comments.

## References

- `CommentController`, `CommentService`, `CommentRequest`
