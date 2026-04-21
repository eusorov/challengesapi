# Check-ins: restrict read access to challenge participants

**Status:** Done (2026-04-21)  
**Source:** [`docs/superpowers/specs/2026-04-21-main-workflows-api-design.md`](../../superpowers/specs/2026-04-21-main-workflows-api-design.md) §1.5

**Implemented:** `CheckInService` gates **`listForChallenge`** and **`findByIdForViewer`** with **`assertViewerMayReadCheckInsForChallenge`**: **404** when no JWT, when **`findByIdForViewer`** fails, or when the viewer is neither **owner** nor a **participant** (a **pending invite** alone is not enough). **`GET /api/challenges/{id}/check-in-summaries`** was removed from the API; rollup summaries stay internal. `CheckInController` passes **`UserPrincipal`** on the two **GET** handlers. Tests: **`CheckInControllerIT`** (`readCheckInsRequiresAuthAndMembership`, **`pendingInviteeMayReadPrivateChallengeButNotCheckIns`**).

## Problem

`GET /api/challenges/{challengeId}/check-ins` did not verify that the caller is a **participant** (or owner) of the challenge. Anyone could read check-ins for any challenge id.

## Scope

- After **challenge visibility** rules are clear, gate read endpoints: require membership (challenge-wide or subtask-scoped as product defines) or **404**.
- Apply same rule to **`GET /api/check-ins/{id}`** if it can expose another user’s data across challenges.
- Integration tests with multiple users.

## Acceptance criteria

- Non-participants cannot enumerate or read check-ins for a private or non-joined challenge per product rules.
- Participants retain current read behavior.

## References

- `CheckInController`, `CheckInService`, `ChallengeService.findByIdForViewer`, `ParticipantRepository.existsByUser_IdAndChallenge_Id`
