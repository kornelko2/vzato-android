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
- Database (Room) with the same fields as the iOS data model, a default profile, settings with
  the iOS defaults and an activity log that records every dose and supply action.
