#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

verify_native() {
  local name="$1"
  local path="$project_dir/native/fdroid-out/assets/native/arm64-v8a/$name"

  if [ ! -e "$path" ]; then
    path="$project_dir/native/out/arm64-v8a/$name"
  fi

  if [ ! -x "$path" ]; then
    printf 'Missing executable native output: %s\n' "$path" >&2
    exit 1
  fi

  if command -v file >/dev/null 2>&1 && ! file -b "$path" | grep -q 'ELF'; then
    printf 'Native output is not an ELF executable: %s\n' "$path" >&2
    exit 1
  fi

  sha256sum "$path"
}

verify_metadata() {
  local metadata="$project_dir/metadata/com.ttv20.rsyncbackup.yml"

  if [ ! -f "$metadata" ]; then
    printf 'Missing F-Droid metadata draft: %s\n' "$metadata" >&2
    exit 1
  fi

  for key in License SourceCode IssueTracker Summary Builds; do
    if ! grep -q "^$key:" "$metadata"; then
      printf 'F-Droid metadata is missing required draft key: %s\n' "$key" >&2
      exit 1
    fi
  done

  printf 'F-Droid metadata draft check passed.\n'
}

case "${1:-}" in
  ""|"--scan-source")
    "$project_dir/scripts/fdroid-scan-source.sh"
    ;;
  "--metadata")
    verify_metadata
    ;;
  "--native")
    if [ -z "${2:-}" ]; then
      printf 'usage: %s --native <rsync|ssh|tsnet-nc>\n' "$0" >&2
      exit 2
    fi
    verify_native "$2"
    ;;
  *)
    printf 'usage: %s [--scan-source|--metadata|--native <name>]\n' "$0" >&2
    exit 2
    ;;
esac
