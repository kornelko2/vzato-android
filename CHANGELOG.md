# Changelog

All notable changes to Vzato for Android are listed here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), versions follow
[Semantic Versioning](https://semver.org/). Behavior follows the iOS app's specification;
differences are noted.

## [Unreleased]

### Added
- Project setup: Kotlin and Jetpack Compose, a plain Kotlin `core` module tested on the JVM,
  dependencies limited to Kotlin, kotlinx and AndroidX (no Google Play services).
- App icon (pill with a check mark) as an adaptive icon with a monochrome layer.
- All 490 texts of the iOS app in English, Czech, Slovak and Hungarian, converted from its String
  Catalog by `scripts/xcstrings-to-android.py`, with Czech and Slovak plural forms.
- Core logic ported from iOS with its tests: dose plan (weekdays, every N days, weeks or months,
  start, end, pause, archive), dose statuses, protection against a double dose, recording doses
  with the amount taken, supply and run-out date, supply check, one reminder per time with
  follow-ups and snooze, the Today card's choice of dose, day summaries and "you can eat now".
- Today: one large card for the dose that needs attention with a big Taken button and Skip, the
  whole day below (touch and hold for Taken, Skip or Reset), Undo after Taken, a question before a
  second dose or an early one, extra doses, low-supply and "eat from" notes.
- Medications: list with supply and get-more-by date, archived medications with Restore, and a
  form for name, strength, unit, amount in quarters, times, days or every N days, weeks or months,
  start and end, meals, color, notes and supply.
- First-start guide: welcome, notification permission with the reason, and the first medication
  in three steps.
- Reminders: one notification per time with every medication due then, Taken, Snooze and Skip
  in the notification, follow-ups until the dose counts as missed, snooze from the Today card,
  "you can eat now", low-supply and run-out warnings, "not recorded" when Taken comes right after
  another dose. One exact alarm at a time, set again after a restart or a time change. Today warns
  when notifications or "Alarms & reminders" are off.
- Bottom tabs on phones; a side rail and a readable column on tablets.
- Database (Room) with the same fields as the iOS data model, a default profile, settings with
  the iOS defaults and an activity log that records every dose and supply action.
