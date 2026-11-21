#!/bin/sh
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.
#
# Copies the reminder sounds of the iOS app (Core Audio .caf, made by its Scripts/make-sounds.py)
# into app/src/main/res/raw as 16-bit WAV, which Android plays in notifications. macOS only
# (afconvert). Run from the repository root.
#
#   sh scripts/convert-sounds.sh [path to MedRemind/Vzato/Resources/Sounds]
set -e
SRC="${1:-../MedRemind/Vzato/Resources/Sounds}"
OUT=app/src/main/res/raw
mkdir -p "$OUT"
for f in "$SRC"/*.caf; do
    name=$(basename "$f" .caf | tr '-' '_')
    afconvert -f WAVE -d LEI16 "$f" "$OUT/$name.wav"
done
ls "$OUT"
