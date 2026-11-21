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

## Release

```bash
./gradlew :app:bundleRelease    # Google Play (app/build/outputs/bundle/release)
./gradlew :app:assembleRelease  # GitHub and F-Droid (app/build/outputs/apk/release)
```

The signing key is not in the repository. The build reads `keystore.properties` in the root
(ignored by git) or `~/.android-keys/vzato-keystore.properties`:

```properties
storeFile=/path/to/vzato-release.jks
storePassword=…
keyAlias=vzato
keyPassword=…
```

Without it, release builds are unsigned. Google Play re-signs with its own key (Play App Signing),
so the APKs from Google Play and from GitHub or F-Droid cannot update each other.

Google Play is on hold until there is an organization account; see
[docs/google-play.md](docs/google-play.md) (in Czech) for the state and the steps.

Store texts and pictures follow the fastlane layout that Google Play tools and F-Droid read:
`fastlane/metadata/android/<locale>/`. `scripts/screenshots.sh` takes the screenshots from a
running emulator, `scripts/store-graphics.sh` makes the icon and feature graphic.

## License

[MPL-2.0](LICENSE). The name Vzato and the app icon are not covered by the license.
