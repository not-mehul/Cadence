#!/usr/bin/env bash
# Installs a release APK on the connected device or emulator, launches it, and checks it
# reached its first screen without crashing. Catches what R8 can break (reflection, native
# callbacks, generated code) and unit tests can't, because they don't run minified code.
# Usage: scripts/smoke-test-release.sh path/to/app-release.apk
set -euo pipefail

apk="${1:?usage: $0 <apk>}"
package=app.cadence

adb install -r "$apk"
# Some emulator images refuse to clear a log buffer; old lines are harmless because the crash check filters by package.
adb logcat -c || true
# Start the launcher entry by name, as a home screen does. An implicit MAIN/LAUNCHER intent doesn't
# resolve from "am start": implicit starts only match filters that also declare the DEFAULT category.
adb shell am start -W -n "$package/app.cadence.LauncherDefault"

# Wait for the first screen. Onboarding is only shown once settings have been read from the
# encrypted database, so seeing it means Hilt, Room, SQLCipher and the Keystore all worked.
for _ in $(seq 1 30); do
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 || true
  # Read into a variable first: with pipefail, grep -q closing the pipe early can fail the whole pipeline.
  ui="$(adb shell cat /sdcard/ui.xml 2>/dev/null || true)"
  if grep -q "Get started" <<< "$ui"; then
    found=1
    break
  fi
  sleep 2
done

# A crash is logged by AndroidRuntime as "FATAL EXCEPTION" followed by "Process: <package>".
crash_log="$(adb logcat -d -s AndroidRuntime:E 2>/dev/null || true)"
crashes="$(grep -A 2 "FATAL EXCEPTION" <<< "$crash_log" || true)"
if grep -q "Process: $package" <<< "$crashes"; then
  echo "The app crashed:" >&2
  echo "$crash_log" >&2
  exit 1
fi
if [[ -z "${found:-}" ]]; then
  echo "The first screen never appeared." >&2
  echo "Text on screen:" >&2
  grep -o 'text="[^"]\+"' <<< "${ui:-}" | head -40 >&2 || true
  adb logcat -d | grep -i "$package\|cadence" | tail -50 >&2 || true
  exit 1
fi
if [[ -z "$(adb shell pidof "$package")" ]]; then
  echo "The app isn't running." >&2
  exit 1
fi
echo "OK: $apk starts and shows its first screen."
