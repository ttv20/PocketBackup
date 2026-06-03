#!/usr/bin/env bash
set -euo pipefail

host="${AMPERE_HOST:?AMPERE_HOST is required, for example ubuntu@ampere-host}"
remote_dir="${AMPERE_PROJECT_DIR:-/home/ubuntu/rsync-backup-android}"
serial="${ANDROID_SERIAL:-127.0.0.1:5555}"
package="com.ttv20.rsyncbackup"
test_package="com.ttv20.rsyncbackup.test"
runner="androidx.test.runner.AndroidJUnitRunner"
project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
test_apk="$project_dir/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"

test -f "$test_apk"

"$project_dir/scripts/ampere-redroid-smoke.sh"

ssh "$host" "mkdir -p '$remote_dir/app/build/outputs/apk/androidTest/debug'"
scp "$test_apk" "$host:$remote_dir/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"

ssh "$host" "set -euo pipefail
  adb connect '$serial' >/dev/null
  adb -s '$serial' uninstall '$test_package' >/dev/null 2>&1 || true
  adb -s '$serial' install -r '$remote_dir/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk' >/dev/null
  adb -s '$serial' shell am instrument -w -r '$test_package/$runner'
  adb -s '$serial' uninstall '$test_package' >/dev/null 2>&1 || true
  adb -s '$serial' uninstall '$package' >/dev/null 2>&1 || true
"
