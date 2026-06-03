#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

SMOKE_MANUAL_RUN_ANYWAY=1 \
SMOKE_SOURCE_TEXT="${SMOKE_SOURCE_TEXT:-ampere-redroid-manual-run-anyway-smoke}" \
SMOKE_REMOTE_SUFFIX="${SMOKE_REMOTE_SUFFIX:--manual-run-anyway}" \
  "$project_dir/scripts/ampere-redroid-e2e-backup-smoke.sh"

echo 'Android manual run-anyway constraint smoke passed'
