# Vzato for Android – instructions for Claude Code

Android version (phones, tablets) of Vzato, a free open source medication reminder. Kotlin +
Jetpack Compose, Room, local notifications and alarms, minSdk 26. The iOS app in `../MedRemind` is
the reference: its `specification.md` (Czech) decides behavior, its tests are the test cases to
port. This repository's plan is `implementation-plan.md` (Czech, phases A0–A7, B1–B8).

## Hard rules (same as the iOS app)

1. **No server, no accounts, no analytics, no ads.** Dependencies only Kotlin, kotlinx and
   AndroidX. No Google Play services, Firebase, ML Kit or third-party libraries (F-Droid). Network
   only for the SÚKL leaflet on a tap and, from phase B8, the user's own Google Drive
   (`drive.appdata`) when they turn sync on.
2. **Not a medical device.** No dose recommendations, interaction checks or health data
   interpretation.
3. **Privacy.** Nothing leaves the device without a user action. Keep the privacy policy on
   vzato.app true for Android.
4. **Secrets stay out of git:** keystores, `keystore.properties`, `local.properties`.
5. **MPL-2.0 header on every Kotlin file.** The name Vzato and the icon are excluded.
6. **Scope discipline.** Behavior changes update `../MedRemind/specification.md` and
   `CHANGELOG.md` here. No phase beyond the plan without the user's decision.
7. **Git.** In this repository, **commit and push after every finished iteration** (a phase or a
   self-contained step of one, once build, lint and tests pass), without asking; decided by the
   user on 21 Nov 2026. This overrides the global "do not commit" preference for this repo only.
   No AI attribution. Short `type: summary` in the existing style.
8. **Changelog.** Every user-visible change and every finished phase gets a line in
   `CHANGELOG.md` (Keep a Changelog, English) in the same commit.

## Build and test

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :core:test :app:assembleDebug :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell cmd locale set-app-locales app.vzato --locales cs
```

`connectedDebugAndroidTest` uninstalls the app when it finishes (data and app language go): reinstall
and seed with `adb shell am start -n app.vzato/.MainActivity --ez sample-data true`.
Emulators: `Pixel_9a`, `Pixel_Tablet` (`~/Library/Android/sdk/emulator/emulator -avd Pixel_9a`).
Before saying a change is done: build and lint pass, all tests pass, new logic in `core` has a test.

## Conventions

- Logic without Android goes to `core` (package `app.vzato.core`), uses `java.time` and never
  manual seconds math. `core` runs on Android 8 (API 26) but lint does not check it: use only Java 8 APIs
  there (no `LocalDate.ofInstant`, `List.of`, `Duration.toSeconds`…). Tests: fixed Europe/Prague zone, Monday 2 Nov 2026, as on iOS.
- Data mirrors the iOS Core Data model (field names, enums as raw strings, lists as "1,2,3"),
  every record has a UUID `id` and `updatedAt`; a dose has no `DoseEvent` until the user acts.
  Stock changes only in `DoseLogger` and `StockService`; every action writes an `ActivityEntry`.
- Strings: texts shared with iOS are generated into `values*/strings_ios.xml` by
  `scripts/xcstrings-to-android.py`; never edit those files. Android-only texts go to
  `strings.xml` in en, cs, sk and hu in the same change. Button copy "Taken" → cs "Vzato",
  sk "Vzaté".
- Amounts go through `DoseFormatter.form` and the plural resources (`n_tablets`, `x_tablet`,
  `x_tablets`…).
- Colors from `ui/Theme.kt` (`Palette`), not wallpaper-based dynamic color.
