#!/bin/sh
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.
#
# Store screenshots in four languages from a running emulator (Pixel 9a), with demo data, framed
# with the headlines of docs/store-headlines.tsv at 1080 × 2160 (Google Play: at most 2:1), into
# fastlane/metadata/android/<locale>/images/phoneScreenshots. Installs the debug build. macOS
# (swift for the frames). Run from the repository root.
set -e
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
RAW=build/store-shots
rm -rf "$RAW" build/store-framed
./gradlew -q :app:assembleDebug
"$ADB" install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null
"$ADB" shell settings put global sysui_demo_allowed 1
"$ADB" shell am broadcast -a com.android.systemui.demo -e command enter >/dev/null
"$ADB" shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm "$(date +%H%M)" >/dev/null   # matches the demo data, which follows the real time
"$ADB" shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false >/dev/null
"$ADB" shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false >/dev/null

# Taps the first element with this text (uiautomator).
tap_text() {
    "$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    xy=$("$ADB" exec-out cat /sdcard/ui.xml | python3 -c "
import re,sys
x=sys.stdin.read()
m=re.search(r'text=\"$1\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"',x)
print((int(m.group(1))+int(m.group(3)))//2,(int(m.group(2))+int(m.group(4)))//2) if m else None")
    [ -n "$xy" ] && [ "$xy" != None ] && "$ADB" shell input tap $xy
    sleep 1.5
}
shot() { "$ADB" exec-out screencap -p > "$RAW/$1/$2.png"; }

for lang in cs sk en hu; do
    mkdir -p "$RAW/$lang"
    "$ADB" shell pm clear app.vzato >/dev/null
    "$ADB" shell cmd locale set-app-locales app.vzato --locales "$lang"
    "$ADB" shell pm grant app.vzato android.permission.POST_NOTIFICATIONS
    "$ADB" shell appops set app.vzato SCHEDULE_EXACT_ALARM allow
    "$ADB" shell am start -n app.vzato/.MainActivity --ez sample-data true >/dev/null
    sleep 12
    shot "$lang" 01-today
    # Bottom tabs: Today, Medications, History, Settings.
    "$ADB" shell input tap 405 2290; sleep 1.5; shot "$lang" 02-medications
    "$ADB" shell input tap 675 2290; sleep 1.5; shot "$lang" 03-history
    "$ADB" shell input tap 405 2290; sleep 1.5; tap_text Letrox; shot "$lang" 04-form
    "$ADB" shell input keyevent KEYCODE_BACK
    echo "$lang done"
done
"$ADB" shell am broadcast -a com.android.systemui.demo -e command exit >/dev/null

swift scripts/frame-screenshots.swift docs/store-headlines.tsv "$RAW" build/store-framed 1080x2160
for pair in cs:cs-CZ sk:sk en:en-US hu:hu-HU; do
    lang=${pair%%:*}; locale=${pair#*:}
    out="fastlane/metadata/android/$locale/images/phoneScreenshots"
    rm -rf "$out"; mkdir -p "$out"
    cp build/store-framed/"$lang"/*.png "$out/"
done
echo "screenshots in fastlane/metadata/android/*/images/phoneScreenshots"
