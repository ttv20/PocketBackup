#!/usr/bin/env bash
set -euo pipefail

adb="${ADB:-adb}"
package="com.ttv20.rsyncbackup"
device_args=()

if [ -n "${ANDROID_SERIAL:-}" ]; then
  device_args=(-s "$ANDROID_SERIAL")
fi

native_lib_dir="$(
  "$adb" "${device_args[@]}" shell dumpsys package "$package" |
    sed -n 's/.*legacyNativeLibraryDir=//p' |
    tr -d '\r' |
    head -1
)"
test -n "$native_lib_dir"
if ! "$adb" "${device_args[@]}" shell run-as "$package" test -f "$native_lib_dir/librsync_exec.so"; then
  native_lib_dir="$native_lib_dir/arm64"
fi

"$adb" "${device_args[@]}" shell run-as "$package" ls -l files/bin/lib/libcrypto.so.3 "$native_lib_dir/librsync_exec.so" "$native_lib_dir/libssh_exec.so" "$native_lib_dir/libtsnet_nc_exec.so" >/dev/null
"$adb" "${device_args[@]}" shell run-as "$package" sh -c "LD_LIBRARY_PATH=files/bin/lib '$native_lib_dir/librsync_exec.so' --version 2>&1" | head -1
"$adb" "${device_args[@]}" shell run-as "$package" sh -c "LD_LIBRARY_PATH=files/bin/lib '$native_lib_dir/libssh_exec.so' -F /dev/null -V 2>&1" | head -1
set +e
"$adb" "${device_args[@]}" shell run-as "$package" sh -c "'$native_lib_dir/libtsnet_nc_exec.so' --state files/tailscale-native-smoke --hostname native-smoke --timeout 1ms --check 127.0.0.1 9 >/dev/null 2>&1"
tsnet_exit=$?
set -e
test "$tsnet_exit" -ne 126
test "$tsnet_exit" -ne 127
