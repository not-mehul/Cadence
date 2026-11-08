#!/usr/bin/env bash
# Fails if an APK requests any permission outside the allowlist.
# Cadence is device-only: it must never request INTERNET or any network permission
# (docs/PLAN.md §2.7). Usage: scripts/check-apk-permissions.sh path/to/app.apk
set -euo pipefail

apk="${1:?usage: $0 <apk>}"
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
aapt2="$(find "$sdk/build-tools" -name aapt2 -type f 2>/dev/null | sort -V | tail -1)"
[[ -x "$aapt2" ]] || { echo "aapt2 not found under $sdk/build-tools" >&2; exit 2; }

package="$("$aapt2" dump packagename "$apk")"

# Permissions the app may request. Keep this list short and justify every entry.
allowed=(
  # androidx.core's app-private, signature-level permission for non-exported receivers.
  "$package.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
  # Reminders (Phase 4): showing local notifications, and re-arming alarms after a reboot.
  "android.permission.POST_NOTIFICATIONS"
  "android.permission.RECEIVE_BOOT_COMPLETED"
)

mapfile -t requested < <("$aapt2" dump permissions "$apk" | sed -n "s/^uses-permission: name='\([^']*\)'.*/\1/p")

status=0
for permission in "${requested[@]}"; do
  if [[ ! " ${allowed[*]} " =~ " ${permission} " ]]; then
    echo "Disallowed permission requested: $permission" >&2
    status=1
  fi
done

if [[ $status -eq 0 ]]; then
  echo "OK: $apk requests only allowed permissions (${#requested[@]} total)."
fi
exit $status
