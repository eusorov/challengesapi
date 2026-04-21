# User profile: opt out of check-in reminders

**Status:** Planned  
**Source:** [`docs/superpowers/specs/2026-04-21-main-workflows-api-design.md`](../superpowers/specs/2026-04-21-main-workflows-api-design.md) §6.3

## Problem

The **`User`** entity has no **reminders enabled** (or similar) flag. Product requires users to **disable** push reminders from their profile.

## Scope

- Add persistent column (e.g. `check_in_reminders_enabled` default **true**) + Flyway migration.
- Expose on user profile **`GET/PUT`** (or dedicated patch) consistent with existing user APIs.
- Reminder job skips users who opted out.

## Acceptance criteria

- User can toggle preference; reminders respect it on next run.
- Integration or repository tests.

## References

- `User` entity, `UserController`, Flyway `db/migration`
