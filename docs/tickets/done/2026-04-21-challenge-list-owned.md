# List challenges owned by the current user

**Status:** Done (2026-04-21)  
**Source:** [`docs/superpowers/specs/2026-04-21-main-workflows-api-design.md`](../../superpowers/specs/2026-04-21-main-workflows-api-design.md) §1.2

**Implemented:** Branch `ticket/challenge-list-owned`, commit `a2721ce`. `GET /api/challenges/mine` — `ChallengeController.listMine`, `ChallengeService.listOwnedByUser`, `ChallengeRepository.findIdsByOwnerUserIdOrderByIdAsc` + `findAllWithSubtasksAndOwnerByIdIn`; OpenAPI `@Operation`; tests in `ChallengeControllerIT`, `ChallengeRepositoryTest`.

## Problem

There is no API to list **my owned** challenges (including **private**). Clients must remember ids from `POST /api/challenges` or use workarounds. Public discovery remains `GET /api/challenges` (non-private only).

## Proposed API

- **`GET /api/challenges/mine`** — authenticated only (`@AuthenticationPrincipal UserPrincipal` required; **401** if absent).
- **`Pageable`** with **`@PageableDefault(size = 20)`** like `ChallengeController.list`.
- Response: **`Page<ChallengeResponse>`** (same mapping as list/detail, including `imagePublicBaseUrl`).

## Scope

- **Repository:** paged ids for **`owner.id = :ownerUserId`** ordered by **`id` asc** (mirror `findNonPrivateIdsOrderByIdAsc` / invite id-page pattern).
- **Service:** load page via existing **`findAllWithSubtasksAndOwnerByIdIn`** (or equivalent) so responses match **`GET /api/challenges/{id}`** shape.
- **OpenAPI:** document the route; no extra query filters in v1 unless product asks (YAGNI).
- **Tests:** `ChallengeControllerIT` — JWT user with two owned challenges (one private, one public) sees both; another user’s challenges never appear; unauthenticated **401**.

## Acceptance criteria

- Authenticated user sees **all** challenges they own (public and private); no other users’ rows.
- Pagination and sort contract match the main challenge list (`page`, `size`, default size 20, stable **`id` asc**).
- Spec §1.2 updated from **Planned** to **OK** with a pointer to this ticket when implemented.

## References

- `ChallengeController.listMine`, `ChallengeService.listOwnedByUser`, `ChallengeRepository.findIdsByOwnerUserIdOrderByIdAsc`
- `UserPrincipal` usage on `ChallengeController.get`
