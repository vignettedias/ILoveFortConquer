#!/usr/bin/env bash
# Install an APK on a connected device/emulator, launch Fort Conquer, and collect evidence:
# install output, dumpsys package, periodic screenshots, crash buffer and full logcat.
#
# usage: scripts/collect_logcat.sh <apk> <output-dir> [seconds=90] [serial]
#   ADB_SERIAL / the 4th argument selects the device (adb -s). The app is uninstalled first
#   (signatures differ between the original and the preservation build).
set -euo pipefail

APK=${1:?apk}; OUT=${2:?output dir}; SECS=${3:-90}; SERIAL=${4:-${ADB_SERIAL:-}}
PKG=com.droidhen.fortconquer
ACT=com.droidhen.fortconquer/.GameActivity
adb_() { if [ -n "$SERIAL" ]; then adb -s "$SERIAL" "$@"; else adb "$@"; fi; }

mkdir -p "$OUT"
{
  echo "date: $(date -u +%FT%TZ)"
  echo "apk: $APK"
  echo "apk sha256: $(sha256sum "$APK" | cut -d' ' -f1)"
  echo "device: $(adb_ shell getprop ro.build.fingerprint | tr -d '\r')"
  echo "sdk: $(adb_ shell getprop ro.build.version.sdk | tr -d '\r')"
  echo "screen: $(adb_ shell wm size | tr -d '\r' | tail -1) density $(adb_ shell wm density | tr -d '\r' | tail -1)"
} > "$OUT/device.txt"

adb_ uninstall "$PKG" >/dev/null 2>&1 || true
adb_ logcat -c || true
if ! adb_ install "$APK" > "$OUT/install.txt" 2>&1; then
  echo "INSTALL FAILED:"; cat "$OUT/install.txt"; exit 2
fi
adb_ shell dumpsys package "$PKG" > "$OUT/dumpsys_package.txt" 2>&1 || true
adb_ logcat -c || true
adb_ shell am start -W -n "$ACT" > "$OUT/am_start.txt" 2>&1 || true

step=10; t=0; crashed=0
while [ "$t" -lt "$SECS" ]; do
  sleep "$step"; t=$((t + step))
  pid=$(adb_ shell pidof "$PKG" | tr -d '\r' || true)
  adb_ exec-out screencap -p > "$OUT/screen_${t}s.png" 2>/dev/null || true
  if adb_ logcat -d -b crash | grep -q "Process: $PKG"; then crashed=1; echo "t=${t}s crash detected"; break; fi
  echo "t=${t}s pid=${pid:-none}"
done

adb_ logcat -d -b crash > "$OUT/logcat_crash.txt" || true
adb_ logcat -d -v threadtime > "$OUT/logcat_full.txt" || true
adb_ shell dumpsys activity activities > "$OUT/dumpsys_activities.txt" 2>&1 || true
echo "crashed=$crashed" >> "$OUT/device.txt"
echo "evidence written to $OUT (crashed=$crashed)"
