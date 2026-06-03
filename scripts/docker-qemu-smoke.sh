#!/usr/bin/env bash
set -euo pipefail

image="${RSYNC_BACKUP_ANDROID_IMAGE:-cimg/android:2025.12}"
docker_cmd="${DOCKER:-docker}"
android_home="${ANDROID_HOME:-/home/circleci/android-sdk}"
project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
gradle_cache="${RSYNC_BACKUP_GRADLE_CACHE:-$project_dir/.gradle-cache}"
sdk_cache="${RSYNC_BACKUP_ANDROID_SDK_CACHE:-$project_dir/.android-sdk-cache}"
avd_name="${RSYNC_BACKUP_AVD_NAME:-rsync-api36}"
system_image="${RSYNC_BACKUP_SYSTEM_IMAGE:-system-images;android-36;google_apis;arm64-v8a}"

mkdir -p "$gradle_cache"
mkdir -p "$sdk_cache/system-images"

$docker_cmd run --rm --privileged \
  --user root \
  -e ANDROID_HOME="$android_home" \
  -e ANDROID_SDK_ROOT="$android_home" \
  -e ANDROID_SDK_HOME=/workspace/.android \
  -e ANDROID_AVD_HOME=/workspace/.android/avd \
  -e HOME=/workspace \
  -e GRADLE_USER_HOME=/workspace/.gradle-cache \
  -v "$project_dir":/workspace \
  -v "$sdk_cache/system-images":"$android_home/system-images" \
  -w /workspace \
  "$image" \
  bash -lc "
    set -euo pipefail
    container_arch=\"\$(uname -m)\"
    if [[ '$system_image' == *'arm64-v8a'* && \"\$container_arch\" != 'aarch64' && \"\$container_arch\" != 'arm64' ]]; then
      echo \"ARM64 Android system images require an ARM64 emulator host/container; got \$container_arch\" >&2
      exit 86
    fi
    if ! command -v emulator >/dev/null 2>&1; then
      sdkmanager emulator >/dev/null || true
    fi
    if ! command -v emulator >/dev/null 2>&1; then
      echo \"Android emulator package is unavailable in this build image for \$container_arch\" >&2
      exit 87
    fi
    mkdir -p \"\$ANDROID_AVD_HOME\"
    yes | sdkmanager --licenses >/dev/null || true
    sdkmanager '$system_image' emulator platform-tools >/dev/null
    echo no | avdmanager create avd -n '$avd_name' -k '$system_image' --force >/dev/null
    emulator -avd '$avd_name' -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect >/tmp/rsync-emulator.log 2>&1 &
    emulator_pid=\$!
    trap 'kill \$emulator_pid >/dev/null 2>&1 || true' EXIT
    timeout 240 adb wait-for-device
    booted='' &&
    for _ in {1..120}; do
      booted=\"\$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')\"
      [ \"\$booted\" = 1 ] && break
      sleep 2
    done
    [ \"\$booted\" = 1 ] &&
    ./scripts/ensure-debug-keystore.sh &&
    ./gradlew --no-daemon assembleDebug &&
    adb install -r app/build/outputs/apk/debug/app-debug.apk &&
    adb shell monkey -p com.ttv20.rsyncbackup 1 &&
    sleep 3 &&
    adb shell pidof com.ttv20.rsyncbackup
  "
