#!/bin/sh
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.
#
# Store icon (512 × 512) and feature graphic (1024 × 500) from the iOS icon source, for every
# store language. Needs Inkscape and swift (macOS). Run from the repository root.
set -e
SVG="${1:-../MedRemind/Design/icon/E-pulena-tableta-fajfka.svg}"
mkdir -p build
inkscape "$SVG" --export-type=png --export-filename=build/icon-512.png -w 512 -h 512 2>/dev/null
swift scripts/store-graphics.swift build/icon-512.png build/feature-graphic.png
for locale in cs-CZ sk en-US hu-HU; do
    dir="fastlane/metadata/android/$locale/images"
    mkdir -p "$dir"
    cp build/icon-512.png "$dir/icon.png"
    cp build/feature-graphic.png "$dir/featureGraphic.png"
done
echo "store graphics written"
