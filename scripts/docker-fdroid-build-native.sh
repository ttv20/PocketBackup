#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
docker_cmd="${DOCKER:-docker}"

restore_owner() {
  local uid gid
  uid="$(id -u)"
  gid="$(id -g)"
  "$docker_cmd" run --rm --user root \
    -v "$project_dir":/workspace \
    -w /workspace \
    ubuntu:24.04 \
    sh -c "chown -R $uid:$gid native/out native/fdroid-out .fdroid-native 2>/dev/null || true"
}
trap restore_owner EXIT

case "${1:-}" in
  ""|"--only")
    if [ "${1:-}" = "--only" ] && [ "${2:-}" != "tsnet-nc" ]; then
      printf 'Only tsnet-nc has an F-Droid native source-build path right now; got %s.\n' "${2:-}" >&2
      exit 2
    fi
    "$project_dir/scripts/build-tsnet-helper.sh"
    ;;
  "--from-source")
    "$project_dir/scripts/build-fdroid-native-termux.sh"
    ;;
  *)
    printf 'usage: %s [--only tsnet-nc|--from-source]\n' "$0" >&2
    exit 2
    ;;
esac
