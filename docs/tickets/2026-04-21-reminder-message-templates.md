# Check-in reminders: pluggable message copy (static now, richer later)

**Status:** Planned  
**Source:** [`docs/superpowers/specs/2026-04-21-main-workflows-api-design.md`](../superpowers/specs/2026-04-21-main-workflows-api-design.md) §6.2

## Problem

Reminder **copy** should start as **static** strings and later support **dynamic** content (quotes, challenge name, streaks) without rewriting the delivery pipeline.

## Scope

- Introduce a small **`ReminderMessageBuilder`** (or similar) interface used by the reminder job.
- v1: fixed template(s) per locale or single language.
- v2 hook: optional quote provider / personalization — document extension points only if not implemented in first PR.

## Acceptance criteria

- Changing default reminder text does not require changing scheduler wiring.
- Unit tests for template rendering with sample context.

## References

- Depends on [`2026-04-21-reminder-scheduler-push-notification.md`](2026-04-21-reminder-scheduler-push-notification.md)
