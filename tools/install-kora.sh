#!/bin/bash
# The football screen (kora), on top of Noor TV: a clean Home with only the clock and a match strip along the
# bottom (Premier League, Saudi Pro League and La Liga taking turns), the apps hidden until Down on the remote,
# and the «مباريات الدوري» card with a tab per league. Run after the launcher patch (launcher-patch/apply.py).
#
#   tools/install-kora.sh <tv-ip>
#
# Afterwards push the kora theme again (installing the launcher clears it):
#   python3 tools/make-theme.py kora ~/noor-theme && cd ~/cleanyourtv && TV_HOST=<ip> tools/theme.sh ~/noor-theme
set -euo pipefail
[ $# -eq 1 ] || { sed -n '2,10p' "$0"; exit 1; }
IP=$1
HERE=$(cd "$(dirname "$0")/.." && pwd)
CYT=${CLEANYOURTV:-$HOME/cleanyourtv}

python3 "$HERE/launcher-patch/apply-fixtures.py" "$CYT"
(cd "$CYT" && TV_HOST="$IP" bash launcher/build-and-install.sh)
(cd "$HERE/epl-app" && ./gradlew assembleRelease -q)
adb -s "$IP:5555" install -r "$HERE/epl-app/app/build/outputs/apk/release/app-release.apk"
echo "Football screen installed. Push the kora theme next."
