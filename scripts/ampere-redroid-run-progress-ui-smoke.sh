#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

SMOKE_SOURCE_TEXT=ampere-redroid-run-progress-ui-smoke \
SMOKE_ADVANCED_ARGS='--bwlimit=32 --out-format=%n' \
SMOKE_SOURCE_FILE_COUNT=1 \
SMOKE_SOURCE_FILE_BYTES=2097152 \
SMOKE_REMOTE_SUFFIX=-progress-ui \
SMOKE_VERIFY_RUN_UI=1 \
"$script_dir/ampere-redroid-e2e-backup-smoke.sh"
