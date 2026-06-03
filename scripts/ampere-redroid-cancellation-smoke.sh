#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

SMOKE_EXPECT_STATUS=CANCELLED \
SMOKE_SOURCE_TEXT=ampere-redroid-cancellation-smoke \
SMOKE_ADVANCED_ARGS='--bwlimit=16 --out-format=%n' \
SMOKE_SOURCE_FILE_COUNT=1 \
SMOKE_SOURCE_FILE_BYTES=2097152 \
SMOKE_AUTO_CANCEL_AFTER_MS=15000 \
SMOKE_REMOTE_SUFFIX=-cancel \
"$script_dir/ampere-redroid-e2e-backup-smoke.sh"
