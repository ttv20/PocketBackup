#!/usr/bin/env bash
set -euo pipefail

host="${AMPERE_HOST:-ubuntu@ampere-host}"
serial="${ANDROID_SERIAL:-127.0.0.1:5555}"
package="com.ttv20.rsyncbackup"
action="com.ttv20.rsyncbackup.debug.TAILSCALE_FAILURE_SMOKE"

"$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/ampere-redroid-smoke.sh"

ssh "$host" "set -euo pipefail
  adb connect '$serial' >/dev/null
  adb -s '$serial' shell pm grant '$package' android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
  adb -s '$serial' shell am broadcast \
    -n '$package/.debug.DebugBackupSmokeReceiver' \
    -a '$action' >/dev/null

  tmp_dir=\$(mktemp -d)
  cleanup() {
    rm -rf \"\$tmp_dir\"
  }
  trap cleanup EXIT

  state_file=\"\$tmp_dir/app-state.json\"
  notification_file=\"\$tmp_dir/notification.txt\"
  for _ in \$(seq 1 90); do
    adb -s '$serial' shell run-as '$package' cat files/app-state.json > \"\$state_file\" 2>/dev/null || true
    adb -s '$serial' shell dumpsys notification --noredact > \"\$notification_file\" 2>/dev/null || true
    if grep -q 'debug-tailscale-failure-profile' \"\$state_file\" &&
       grep -q '\"lastStatus\": \"FAILED\"' \"\$state_file\" &&
       grep -q 'Tailscale is not configured' \"\$state_file\" &&
       grep -q 'Tailscale setup problem' \"\$notification_file\" &&
       grep -q 'Open setup' \"\$notification_file\"; then
      echo 'Android Tailscale failure notification smoke passed'
      exit 0
    fi
    sleep 2
  done

  cat \"\$state_file\"
  cat \"\$notification_file\"
  exit 1
"
