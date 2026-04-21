# Check-in reminders: daily schedule evaluation and push delivery

**Status:** Planned  
**Source:** [`docs/superpowers/specs/2026-04-21-main-workflows-api-design.md`](../superpowers/specs/2026-04-21-main-workflows-api-design.md) §6.1

## Problem

There is no job that evaluates **challenge/subtask schedules** (daily / weekdays / fixed date) to determine “due today” and no integration to send **push notifications** to participants. `CheckInRollupScheduler` only handles post-end rollup, not reminders.

## Scope

- Design batch job (`@Scheduled` or external cron + secured endpoint): load active challenges/subtasks, compute due dates from `Schedule`, dedupe recipients.
- Integrate push provider (FCM/APNs/etc.) behind an interface; configuration via `application.yml`.
- Respect user opt-out when [`2026-04-21-user-reminders-opt-out.md`](2026-04-21-user-reminders-opt-out.md) exists.
- Idempotency / at-most-once per user per challenge per day (product decision).

## Acceptance criteria

- Participants eligible for a scheduled day receive a push (when device tokens exist and opt-in is on).
- Document operational runbook and env vars.

## References

- `Schedule`, `ScheduleService`, `CheckInRollupScheduler` (pattern only)
