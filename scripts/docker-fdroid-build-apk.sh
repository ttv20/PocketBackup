#!/usr/bin/env bash
set -euo pipefail

image="${RSYNC_BACKUP_ANDROID_IMAGE:-cimg/android:2025.12}"
docker_cmd="${DOCKER:-docker}"
android_home="${ANDROID_HOME:-/home/circleci/android-sdk}"
project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
gradle_cache="${RSYNC_BACKUP_GRADLE_CACHE:-$project_dir/.gradle-cache}"
env_args=()

for env_name in \
  POCKETSYNC_VERSION_CODE \
  POCKETSYNC_VERSION_NAME \
  FDROID_NATIVE_SOURCE_REFS \
  GO_ANDROID_IMAGE
do
  if [[ -n "${!env_name:-}" ]]; then
    env_args+=("-e" "$env_name=${!env_name}")
  fi
done

mkdir -p "$gradle_cache"

"$project_dir/scripts/docker-fdroid-build-native.sh" --from-source

$docker_cmd run --rm \
  --user root \
  -e ANDROID_HOME="$android_home" \
  -e ANDROID_SDK_ROOT="$android_home" \
  -e HOME=/workspace \
  -e GRADLE_USER_HOME=/workspace/.gradle-cache \
  -e RSYNC_BACKUP_ANDROID_IMAGE="$image" \
  "${env_args[@]}" \
  -v "$project_dir":/workspace \
  -w /workspace \
  "$image" \
  bash -lc './scripts/fdroid-scan-source.sh --gradle && ./gradlew --no-daemon assembleFdroidRelease && find app/build/outputs/apk/fdroidRelease -type f -name "*.apk" -print0 | sort -z | xargs -0 sha256sum > app/build/outputs/apk/fdroidRelease/SHA256SUMS.txt'

uid="$(id -u)"
gid="$(id -g)"
$docker_cmd run --rm --user root \
  -v "$project_dir":/workspace \
  -w /workspace \
  ubuntu:24.04 \
  sh -c "chown -R $uid:$gid app/build .gradle .gradle-cache native/fdroid-out .fdroid-native 2>/dev/null || true"
