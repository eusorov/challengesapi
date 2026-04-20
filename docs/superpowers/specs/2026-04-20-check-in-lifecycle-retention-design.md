# Check-in lifecycle retention and storage reduction

## Context

- **Current model:** One row per **actual** check-in in `check_ins` (`user_id`, `challenge_id`, `check_date`, optional `subtask_id`). Absences are not stored.
- **Risk:** At large scale (many participants × long-running or numerous challenges × frequent check-ins), the fact table grows without bound.
- **Product choice (approved):** **Challenge-lifecycle (C)** — keep **full per-day detail** for **active** challenges and for a **configurable grace period after a challenge ends**, then **reduce** footprint via **summaries** and **removal** of raw rows from the primary database. (Optional export to object storage before delete was **removed** from scope.)

## Goals

1. **Bound** hot-path storage for historical closed challenges while preserving **useful** stats for UX (totals, streaks if needed, first/last check-in dates).
2. **Configurable** retention; no hard-coded magic numbers in code beyond safe defaults.
3. **Safe** background processing: idempotent jobs, clear eligibility rules, no data loss beyond what policy explicitly allows.

## Non-goals (initial phase)

- **Forever** per-day heatmaps for challenges closed years ago **without** storing compact encodings or cold archives (if product later requires this, add **bitmap compaction** or **S3 replay** as a follow-up).
- Changing the **write path** for new check-ins (still append rows to `check_ins` while challenge is in the “detail” window).

## Policy

| Concept | Definition |
|--------|------------|
| **Challenge ended** | `Challenge.endDate` is **non-null** and **before today** (see `Challenge` entity). There is no separate “closed” enum in the current model. |
| **Detail window** | Raw `check_ins` rows are retained while the challenge is **active** OR **ended within the last `N` days**, where `N` = `challenges.check-ins.retention-days-after-challenge-end` (working name; align with `application.yml` conventions). |
| **Eligible for rollup** | Challenge has **ended**, and `today > challenge_end_date + N` (or equivalent), and rollup job has not yet completed for that challenge (track with a **per-challenge watermark** or **processing state** column/table). |

**Default suggestion for `N`:** **90** days — long enough for disputes, support, and “recent history” UIs; tune per environment.

## Data model additions

### Summary store (PostgreSQL)

New table, e.g. **`check_in_summaries`**, keyed by **participant scope** the product needs:

- `user_id`, `challenge_id`, `subtask_id` (nullable for whole-challenge-only rows — **either** one summary row per (user, challenge) with subtask breakdown in JSONB **or** separate rows per subtask; **recommendation:** mirror the granularity of `check_ins` queries: one row per **(user_id, challenge_id, subtask_id)** with `subtask_id` nullable meaning whole challenge).

**Suggested columns:**

- `total_check_ins` (bigint)
- `first_check_in_date`, `last_check_in_date` (date)
- Optional: `longest_streak`, `current_streak_at_close` if product requires — **populate only if** the client already shows these; otherwise YAGNI in v1.
- `rolled_up_at` (timestamptz)
- Unique constraint on the natural key to make upserts safe.

### Job bookkeeping

- Either **`challenges.check_ins_rollup_status`** enum on `challenges` (`PENDING`, `COMPLETE`, `FAILED`) **or** a small **`check_in_rollup_runs`** table with `challenge_id`, `status`, `error`, `updated_at`.
- **Recommendation:** status on `challenges` only if the column is nullable/default for existing rows; otherwise a dedicated small table avoids widening the hot `challenges` row if that is a concern.

## Processing flow

1. **Scheduler** (Spring `@Scheduled` or external cron invoking an admin endpoint): select challenges **eligible for rollup** in small batches.
2. **Per challenge (transactional steps):**
   - **Aggregate** from `check_ins` into `check_in_summaries` (SQL `INSERT … SELECT`).
   - **Delete** `check_ins` for that `challenge_id` (and all participants) **after** successful summary commit.
   - **Mark** rollup complete.
3. **Failure:** Retry on next run; job must be **idempotent** (re-aggregate over remaining rows, or skip if already complete).

## API and read path

- **List/detail for dates in the detail window:** unchanged — query `check_ins`.
- **Closed challenge past the window:** serve **`check_in_summaries`** (and challenge metadata). If the client needs a **sparse list of dates** for a limited UI, either **do not support** day-level lists for archived challenges **or** add compaction later (bitmaps) — **v1:** summaries only unless product insists otherwise.

Document breaking behavior in **OpenAPI** release notes when shipped.

## Physical database (phase 2, optional)

- **Range partition** `check_ins` by `check_date` (monthly) to make mass deletes cheaper and to prepare for **detach + archive** of old partitions.
- Not required for the first implementation if delete-by-`challenge_id` is acceptable at expected volumes.

## Testing

- **Unit:** aggregation logic (given rows → expected summary).
- **Integration:** end-to-end rollup for a closed challenge past `N`, assert `check_ins` count 0 for that challenge and summary row present.
- **Concurrency:** two job runs for same challenge should not corrupt data (unique keys + transactional delete).

## Open follow-ups (not in v1 spec)

- **Heatmap forever** for archived challenges → bitmap or cold-store replay.
- **Open-ended challenges** (`endDate == null`): rollup **does not apply** until an end date exists or product adds **manual archive / soft-close** that sets `endDate` (or a dedicated flag). Document this in API/UX so users know history stays in raw form until the challenge is bounded.

## Approval

- **Product:** Challenge-lifecycle (C) with summary after grace period — **approved** (user confirmation 2026-04-20).
- **Engineering:** Implement per this document; default `N` via config.
