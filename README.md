# Vzato for Android

Vzato is a free, open source medication reminder. This is the Android version for phones and
tablets; the iPhone, iPad and Mac app lives in a separate repository. No accounts, no ads, no
analytics, no server.

Work in progress: see [implementation-plan.md](implementation-plan.md) (in Czech).

Vzato is not a medical device. It reminds you of what you entered; it does not recommend doses or
check interactions.

## Build

Needs Android Studio (or its JDK) and the Android SDK.

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"   # macOS
./gradlew :core:test :app:assembleDebug
```

- `core/` – plain Kotlin: dose plan, statuses, supply and reminders, tested on the JVM.
- `app/` – the Android app (Jetpack Compose).
- Texts shared with the iOS app come from its String Catalog:
  `python3 scripts/xcstrings-to-android.py <path to Localizable.xcstrings>` writes
  `app/src/main/res/values*/strings_ios.xml`.

Dependencies are limited to Kotlin, kotlinx and AndroidX. No Google Play services.

## License

[MPL-2.0](LICENSE). The name Vzato and the app icon are not covered by the license.
