#!/usr/bin/env python3
"""Makes the CleanYourTV launcher's Home clean: the apps stay hidden until a remote key asks for them (CleanHome.java),
and the Premier League's current round runs as a strip along the bottom, crests and kick-off times (Fixtures.java),
with the club crests bundled from crests/.

    python3 apply-fixtures.py <cleanyourtv folder>

Safe to run twice. Afterwards build and install the launcher (launcher/build-and-install.sh) and push the theme
again (installing the launcher clears it).
"""
import pathlib, shutil, sys

if len(sys.argv) != 2:
    sys.exit(__doc__)
root = pathlib.Path(sys.argv[1])
here = pathlib.Path(__file__).resolve().parent
src = root / "launcher/app/src/main"
for name in ("Fixtures.java", "CleanHome.java"):
    shutil.copy(here / name, src / "java/com/gathin/tvhome" / name)
res = src / "res/drawable-nodpi"
res.mkdir(exist_ok=True)
for png in (here / "crests").glob("*.png"):
    shutil.copy(png, res / f"crest_{png.name}")
# The crests are looked up by name, so the release build's resource shrinker must be told to keep them.
(src / "res/raw").mkdir(exist_ok=True)
(src / "res/raw/fixtures_keep.xml").write_text(
    '<?xml version="1.0" encoding="utf-8"?>\n'
    '<resources xmlns:tools="http://schemas.android.com/tools" tools:keep="@drawable/crest_*" />\n', encoding="utf-8")


def patch(path, old, new, marker):
    text = path.read_text(encoding="utf-8")
    if marker in text:
        return
    if old not in text:
        sys.exit(f"Could not find the spot to change in {path} - the launcher changed upstream; patch it by hand.")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


main = src / "java/com/gathin/tvhome/MainActivity.java"
patch(main, "        // Liquid feel: soft springs on the highlight's position.",
      "        Fixtures.attach(this, grid);\n\n        // Liquid feel: soft springs on the highlight's position.",
      "Fixtures.attach(")
patch(main, "        if (lunar) lunarDate.setText(Lunar.today(getResources(), lunarOffset));",
      "        Fixtures.tick();\n        if (lunar) lunarDate.setText(Lunar.today(getResources(), lunarOffset));",
      "Fixtures.tick()")
patch(src / "AndroidManifest.xml",
      '    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />',
      '    <uses-permission android:name="android.permission.INTERNET" />\n'
      '    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />',
      'android.permission.INTERNET')
patch(main, "        Fixtures.attach(this, grid);\n",
      "        Fixtures.attach(this, grid);\n        CleanHome.attach(this, grid);\n",
      "CleanHome.attach(")
patch(main, "        if (event.getAction() == KeyEvent.ACTION_DOWN) HomeKeyService.keyOnHome();\n",
      "        if (event.getAction() == KeyEvent.ACTION_DOWN) HomeKeyService.keyOnHome();\n"
      "        if (!reordering && CleanHome.key(event)) return true;\n",
      "CleanHome.key(")
patch(main, "        restartIdleClock();\n        promptsPaused = false;\n",
      "        restartIdleClock();\n        CleanHome.rest();\n        promptsPaused = false;\n",
      "CleanHome.rest()")
print("Clean Home and the match strip added")
