#!/usr/bin/env bash
set -euo pipefail

host="${AMPERE_HOST:?AMPERE_HOST is required, for example ubuntu@ampere-host}"
remote_dir="${AMPERE_PROJECT_DIR:-/home/ubuntu/rsync-backup-android}"
serial="${ANDROID_SERIAL:-127.0.0.1:5555}"
package="com.ttv20.rsyncbackup"
test_package="com.ttv20.rsyncbackup.test"
runner="androidx.test.runner.AndroidJUnitRunner"
test_class="com.ttv20.rsyncbackup.OnboardingScreenshotSmokeTest"
project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
test_apk="$project_dir/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
timestamp="$(date +%Y%m%d-%H%M%S)"
artifact_dir="$project_dir/artifacts/onboarding-screenshots-$timestamp"
latest_file="$project_dir/artifacts/onboarding-screenshots-latest.txt"

test -f "$test_apk"

"$project_dir/scripts/ampere-redroid-smoke.sh"

ssh "$host" "mkdir -p '$remote_dir/app/build/outputs/apk/androidTest/debug' '$remote_dir/artifacts'"
scp "$test_apk" "$host:$remote_dir/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"

remote_capture_dir="$remote_dir/artifacts/onboarding-screenshots-$timestamp"
device_capture_dir="/sdcard/Android/data/$test_package/files/onboarding-screenshots"

ssh "$host" "set -euo pipefail
  adb connect '$serial' >/dev/null
  adb -s '$serial' uninstall '$test_package' >/dev/null 2>&1 || true
  adb -s '$serial' install -r '$remote_dir/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk' >/dev/null
  adb -s '$serial' shell rm -rf '$device_capture_dir' >/dev/null 2>&1 || true
  adb -s '$serial' shell am instrument -w -r \
    -e class '$test_class' \
    '$test_package/$runner'
  rm -rf '$remote_capture_dir'
  mkdir -p '$remote_capture_dir'
  adb -s '$serial' pull '$device_capture_dir/.' '$remote_capture_dir' >/dev/null
  test -f '$remote_capture_dir/01-welcome.png'
  test -f '$remote_capture_dir/08-dry-run-result.png'
  adb -s '$serial' uninstall '$test_package' >/dev/null 2>&1 || true
  adb -s '$serial' uninstall '$package' >/dev/null 2>&1 || true
"

mkdir -p "$artifact_dir"
scp "$host:$remote_capture_dir/"'*.png' "$artifact_dir/"
printf '%s\n' "$artifact_dir" > "$latest_file"
printf 'Onboarding screenshots written to %s\n' "$artifact_dir"
