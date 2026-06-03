#!/usr/bin/env bash
set -euo pipefail

host="${AMPERE_HOST:?AMPERE_HOST is required, for example ubuntu@ampere-host}"
serial="${ANDROID_SERIAL:-127.0.0.1:5555}"
package="com.ttv20.rsyncbackup"
action="com.ttv20.rsyncbackup.debug.SCHEDULE_CONSTRAINT_SMOKE"

"$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/ampere-redroid-smoke.sh"

ssh "$host" "set -euo pipefail
  adb connect '$serial' >/dev/null
  adb -s '$serial' shell pm grant '$package' android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
  adb -s '$serial' shell am broadcast \
    -n '$package/.debug.DebugBackupSmokeReceiver' \
    -a '$action' \
    --ei alarmDelayMinutes 1 \
    --es scheduleType EXACT_DAILY >/dev/null

  tmp_dir=\$(mktemp -d)
  cleanup() {
    rm -rf \"\$tmp_dir\"
  }
  trap cleanup EXIT

  state_file=\"\$tmp_dir/app-state.json\"
  adb -s '$serial' shell run-as '$package' cat files/app-state.json > \"\$state_file\"
  grep -q 'debug-schedule-constraint-profile' \"\$state_file\"
  grep -q '\"selectedSsid\": \"debug-ssid-that-should-not-match\"' \"\$state_file\"

  notification_file=\"\$tmp_dir/notification.txt\"
  for _ in \$(seq 1 120); do
    adb -s '$serial' shell dumpsys notification --noredact > \"\$notification_file\" 2>/dev/null || true
    if grep -q 'Backup constraints not met' \"\$notification_file\" &&
       grep -q 'Run anyway' \"\$notification_file\" &&
       grep -Eq 'Current SSID|Selected SSID' \"\$notification_file\"; then
      echo 'Android scheduled constraint notification smoke passed'
      exit 0
    fi
    sleep 2
  done

  cat \"\$notification_file\"
  exit 1
"
