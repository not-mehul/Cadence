#!/usr/bin/env bash
# Installs a release APK on the connected device or emulator, launches it, and checks it
# reached its first screen without crashing. Catches what R8 can break (reflection, native
# callbacks, generated code) and unit tests can't, because they don't run minified code.
# Usage: scripts/smoke-test-release.sh path/to/app-release.apk
set -euo pipefail

apk="${1:?usage: $0 <apk>}"
package=app.cadence

adb install -r "$apk"
adb logcat -c
adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p "$package"

# Wait for the first screen. Onboarding is only shown once settings have been read from the
# encrypted database, so seeing it means Hilt, Room, SQLCipher and the Keystore all worked.
for _ in $(seq 1 30); do
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 || true
  if adb shell cat /sdcard/ui.xml 2>/dev/null | grep -q "Get started"; then
    found=1
    break
  fi
  sleep 2
done

if adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime" | grep -q .; then
  echo "The app crashed:" >&2
  adb logcat -d | grep -A 30 "FATAL EXCEPTION" >&2 || true
  exit 1
fi
if [[ -z "${found:-}" ]]; then
  echo "The first screen never appeared." >&2
  adb logcat -d | grep -i "$package\|cadence" | tail -50 >&2 || true
  exit 1
fi
if [[ -z "$(adb shell pidof "$package")" ]]; then
  echo "The app isn't running." >&2
  exit 1
fi
echo "OK: $apk starts and shows its first screen."
