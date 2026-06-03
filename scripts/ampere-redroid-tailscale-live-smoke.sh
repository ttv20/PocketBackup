#!/usr/bin/env bash
set -euo pipefail

host="${AMPERE_HOST:-ubuntu@ampere-host}"
remote_dir="${AMPERE_PROJECT_DIR:-/home/ubuntu/rsync-backup-android}"
serial="${ANDROID_SERIAL:-127.0.0.1:5555}"
package="com.ttv20.rsyncbackup"
test_package="com.ttv20.rsyncbackup.test"
runner="androidx.test.runner.AndroidJUnitRunner"
project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
test_apk="$project_dir/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"

auth_key="${TS_AUTHKEY:-${TAILSCALE_AUTHKEY:-${RSYNC_BACKUP_TS_AUTHKEY:-}}}"
if [ -z "$auth_key" ]; then
  echo "TS_AUTHKEY, TAILSCALE_AUTHKEY, or RSYNC_BACKUP_TS_AUTHKEY is required for live Tailscale smoke" >&2
  exit 88
fi

test -f "$test_apk"

tmp_dir="$(mktemp -d)"
server_pid=""
remote_auth_key_file=""
cleanup() {
  if [ -n "$server_pid" ]; then
    kill "$server_pid" >/dev/null 2>&1 || true
  fi
  if [ -n "$remote_auth_key_file" ]; then
    ssh "$host" "rm -f '$remote_auth_key_file'" >/dev/null 2>&1 || true
  fi
  rm -rf "$tmp_dir"
}
trap cleanup EXIT

node_name="${TAILSCALE_NODE_NAME:-android-rsync-live-$(date +%s)}"
test_host="${TAILSCALE_TEST_HOST:-${RSYNC_BACKUP_TAILSCALE_TEST_HOST:-}}"
test_port="${TAILSCALE_TEST_PORT:-${RSYNC_BACKUP_TAILSCALE_TEST_PORT:-}}"

if [ -z "$test_host" ]; then
  if ! command -v tailscale >/dev/null 2>&1; then
    echo "TAILSCALE_TEST_HOST is required when the local tailscale CLI is unavailable" >&2
    exit 89
  fi
  test_host="$(tailscale ip -4 | head -1)"
  if [ -z "$test_host" ]; then
    echo "Could not discover local Tailscale IPv4 address; set TAILSCALE_TEST_HOST and TAILSCALE_TEST_PORT" >&2
    exit 89
  fi
  python3 - "$test_host" "$tmp_dir/port" <<'PY' &
import http.server
import socketserver
import sys

host = sys.argv[1]
port_file = sys.argv[2]

class QuietHandler(http.server.SimpleHTTPRequestHandler):
    def log_message(self, format, *args):
        pass

class ReusableServer(socketserver.TCPServer):
    allow_reuse_address = True

with ReusableServer((host, 0), QuietHandler) as server:
    with open(port_file, "w", encoding="utf-8") as handle:
        handle.write(str(server.server_address[1]))
    server.serve_forever()
PY
  server_pid="$!"
  for _ in $(seq 1 50); do
    [ -s "$tmp_dir/port" ] && break
    sleep 0.1
  done
  test_port="$(cat "$tmp_dir/port")"
elif [ -z "$test_port" ]; then
  echo "TAILSCALE_TEST_PORT is required when TAILSCALE_TEST_HOST is supplied" >&2
  exit 89
fi

node_name_b64="$(printf '%s' "$node_name" | base64 -w0)"
test_host_b64="$(printf '%s' "$test_host" | base64 -w0)"

"$project_dir/scripts/ampere-redroid-smoke.sh"

ssh "$host" "mkdir -p '$remote_dir/app/build/outputs/apk/androidTest/debug'"
scp "$test_apk" "$host:$remote_dir/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
remote_auth_key_file="$(ssh "$host" "umask 077; mktemp /tmp/rsync-tailscale-auth.XXXXXX")"
printf '%s' "$auth_key" | ssh "$host" "cat > '$remote_auth_key_file'"

ssh "$host" "set -euo pipefail
  auth_key_file='$remote_auth_key_file'
  cleanup_secret() {
    rm -f \"\$auth_key_file\"
  }
  trap cleanup_secret EXIT
  auth_key=\$(cat \"\$auth_key_file\")
  rm -f \"\$auth_key_file\"
  trap - EXIT
  node_name=\$(printf '%s' '$node_name_b64' | base64 -d)
  test_host=\$(printf '%s' '$test_host_b64' | base64 -d)
  test_port='$test_port'
  adb connect '$serial' >/dev/null
  adb -s '$serial' uninstall '$test_package' >/dev/null 2>&1 || true
  adb -s '$serial' install -r '$remote_dir/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk' >/dev/null
  adb -s '$serial' shell am start -W -n '$package/.MainActivity' >/dev/null
  instrument_output=\$(mktemp)
  set +e
  adb -s '$serial' shell am instrument -w -r \
    -e class 'com.ttv20.rsyncbackup.TailscaleLiveSmokeTest' \
    -e authKey \"\$auth_key\" \
    -e nodeName \"\$node_name\" \
    -e testHost \"\$test_host\" \
    -e testPort \"\$test_port\" \
    '$test_package/$runner' 2>&1 | tee \"\$instrument_output\"
  instrument_status=\${PIPESTATUS[0]}
  set -e
  state_file=\$(mktemp)
  adb -s '$serial' shell run-as '$package' cat files/app-state.json > \"\$state_file\" 2>/dev/null || true
  if grep -q \"\$auth_key\" \"\$state_file\"; then
    echo 'Tailscale auth key leaked into app state' >&2
    exit 1
  fi
  if [ \"\$instrument_status\" != 0 ] ||
     grep -Eq 'FAILURES!!!|INSTRUMENTATION_STATUS_CODE: -2' \"\$instrument_output\"; then
    echo 'Android live Tailscale instrumentation failed' >&2
    grep -E '\"isConfigured\"|\"lastLoginAt\"|\"lastReachabilityTestAt\"|\"lastError\"' \"\$state_file\" |
      sed -E 's/tskey-auth-[A-Za-z0-9_-]+/<redacted-auth-key>/g; s/API key [A-Za-z0-9_-]+/API key <redacted>/g' >&2 || true
    exit 1
  fi
  grep -q '\"isConfigured\": true' \"\$state_file\"
  grep -Eq '\"lastLoginAt\": \"[^\"]+\"' \"\$state_file\"
  grep -Eq '\"lastReachabilityTestAt\": \"[^\"]+\"' \"\$state_file\"
  grep -q '\"lastError\": null' \"\$state_file\"
  rm -f \"\$state_file\" \"\$instrument_output\"
  adb -s '$serial' uninstall '$test_package' >/dev/null 2>&1 || true
  adb -s '$serial' uninstall '$package' >/dev/null 2>&1 || true
  echo 'Android live Tailscale auth/reachability smoke passed'
"
