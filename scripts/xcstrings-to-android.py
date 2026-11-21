#!/usr/bin/env python3
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.
"""Converts the iOS String Catalog into Android string resources, so both apps say the same thing
in the same four languages.

    python3 scripts/xcstrings-to-android.py [path/to/Localizable.xcstrings]

Default input: ../MedRemind/Vzato/Resources/Localizable.xcstrings. Writes
app/src/main/res/values{,-cs,-sk,-hu}/strings_ios.xml (generated, do not edit by hand; Android-only
texts go to strings.xml). Resource names come from the English text ("Taken" -> taken,
"%lld tablets" -> n_tablets). Keys missing a translation are left out and listed.
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEFAULT = ROOT.parent / "MedRemind" / "Vzato" / "Resources" / "Localizable.xcstrings"
LANGS = {"en": "values", "cs": "values-cs", "sk": "values-sk", "hu": "values-hu"}
PLURAL_ORDER = ["zero", "one", "two", "few", "many", "other"]
JAVA_KEYWORDS = set("""abstract assert boolean break byte case catch char class const continue default do double
else enum extends final finally float for goto if implements import instanceof int interface long native new
package private protected public return short static strictfp super switch synchronized this throw throws
transient try void volatile while true false null""".split())


def android_format(text: str) -> str:
    """%lld/%d -> %d, %@ -> %s, %.1f stays; several placeholders become positional."""
    text = re.sub(r"%(\d+\$)?lld", lambda m: f"%{m.group(1) or ''}d", text)
    text = re.sub(r"%(\d+\$)?@", lambda m: f"%{m.group(1) or ''}s", text)
    specs = list(re.finditer(r"%(?!%)(\d+\$)?[-+ 0#]*\d*(?:\.\d+)?[dsf]", text))
    if len(specs) > 1 and not any(m.group(1) for m in specs):
        out, last = [], 0
        for i, m in enumerate(specs, 1):
            out.append(text[last:m.start()])
            out.append("%" + f"{i}$" + m.group(0)[1:])
            last = m.end()
        out.append(text[last:])
        text = "".join(out)
    return text


def escape(text: str) -> str:
    text = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    text = text.replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"').replace("\n", "\\n")
    if text.startswith(("@", "?")):
        text = "\\" + text
    return text


def resource_name(english: str, used: set) -> str:
    base = english.replace("%lld", "n").replace("%@", "x").replace("%d", "n")
    base = re.sub(r"[^a-z0-9]+", "_", base.lower()).strip("_")[:50].strip("_") or "text"
    if base[0].isdigit() or base in JAVA_KEYWORDS:
        base = "s_" + base
    name, i = base, 2
    while name in used:
        name, i = f"{base}_{i}", i + 1
    used.add(name)
    return name


def value_of(entry: dict, lang: str, key: str):
    """A plain string, a dict of plural forms, or None when missing."""
    loc = entry.get("localizations", {}).get(lang)
    if loc is None:
        return key if lang == "en" else None
    if "stringUnit" in loc:
        return loc["stringUnit"]["value"]
    plural = loc.get("variations", {}).get("plural")
    if plural:
        return {form: v["stringUnit"]["value"] for form, v in plural.items()}
    return None


def main() -> None:
    source = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT
    catalog = json.loads(source.read_text(encoding="utf-8"))
    used, skipped = set(), []
    lines = {lang: [] for lang in LANGS}
    for key in sorted(catalog["strings"], key=str.lower):
        entry = catalog["strings"][key]
        if entry.get("shouldTranslate") is False or not key.strip():
            continue
        values = {lang: value_of(entry, lang, key) for lang in LANGS}
        if any(v is None for v in values.values()):
            skipped.append(key)
            continue
        is_plural = any(isinstance(v, dict) for v in values.values())
        english = values["en"] if isinstance(values["en"], str) else values["en"].get("other", key)
        name = resource_name(english if isinstance(english, str) else key, used)
        for lang, value in values.items():
            if is_plural:
                forms = value if isinstance(value, dict) else {"other": value}
                items = "".join(
                    f'\n        <item quantity="{form}">{escape(android_format(forms[form]))}</item>'
                    for form in PLURAL_ORDER if form in forms)
                lines[lang].append(f'    <plurals name="{name}">{items}\n    </plurals>')
            else:
                lines[lang].append(f'    <string name="{name}">{escape(android_format(value))}</string>')
    for lang, folder in LANGS.items():
        out = ROOT / "app" / "src" / "main" / "res" / folder / "strings_ios.xml"
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(
            '<?xml version="1.0" encoding="utf-8"?>\n'
            "<!-- Generated by scripts/xcstrings-to-android.py from the iOS String Catalog. Do not edit. -->\n"
            '<resources xmlns:tools="http://schemas.android.com/tools" tools:ignore="UnusedResources">\n'
            + "\n".join(lines[lang]) + "\n</resources>\n", encoding="utf-8")
    print(f"{len(used)} strings written; {len(skipped)} without all four languages left out")
    for key in skipped:
        print("  missing:", key.replace("\n", "\\n")[:80])


if __name__ == "__main__":
    main()
