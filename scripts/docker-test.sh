#!/usr/bin/env bash
set -euo pipefail

image="${RSYNC_BACKUP_ANDROID_IMAGE:-cimg/android:2025.12}"
docker_cmd="${DOCKER:-docker}"
android_home="${ANDROID_HOME:-/home/circleci/android-sdk}"
project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
gradle_cache="${RSYNC_BACKUP_GRADLE_CACHE:-$project_dir/.gradle-cache}"

mkdir -p "$gradle_cache"

$docker_cmd run --rm \
  --user root \
  -e ANDROID_HOME="$android_home" \
  -e ANDROID_SDK_ROOT="$android_home" \
  -e HOME=/workspace \
  -e GRADLE_USER_HOME=/workspace/.gradle-cache \
  -v "$project_dir":/workspace \
  -w /workspace \
  "$image" \
  bash -lc './gradlew --no-daemon testDebugUnitTest'

uid="$(id -u)"
gid="$(id -g)"
$docker_cmd run --rm --user root \
  -v "$project_dir":/workspace \
  -w /workspace \
  ubuntu:24.04 \
  sh -c "chown -R $uid:$gid app/build .gradle .gradle-cache 2>/dev/null || true"
