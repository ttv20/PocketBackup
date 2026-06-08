# Tailscale tsnet Version Bisect

This documents the Android `tsnet` regression test from June 4, 2026, so it can
be repeated when updating `native/tsnet-helper`.

## Result

Current fallback for `native/tsnet-helper`:

```text
tailscale.com v1.92.5
golang.org/x/crypto v0.45.0
gvisor.dev/gvisor v0.0.0-20250205023644-9414b50a5633
```

Test boundary:

```text
last good stable version:  v1.96.5
first bad stable version:  v1.98.0
bad through:               v1.100.0
```

`v1.96.5` was the last tested stable version that still populated the peer map,
but the app was later downgraded further to `v1.92.5` after route testing still
failed with the packaged `v1.96.5` helper.

The failure mode in `v1.98.0+` was:

```text
tsnet status state=Running ... peers=0 onlinePeers=0
```

The expected working behavior is:

```text
tsnet status state=Running ... peers=4 onlinePeers=2
```

The test device state at the time could see these peers:

```text
hermes            100.102.73.115
oneplus-le2110    100.112.245.28
oneplus-9         100.83.110.27
elkana-scadasudo  100.118.251.94
```

`bardugo-home` / `100.86.75.50` was not visible to the app node's netmap, so it
was useful as a fast-fail target after `tsnet` had printed its peer map.

## Upstream Context

No exact public Tailscale issue was found for "`tsnet` peers=0 on Android after
v1.98".

Relevant upstream items:

- `tailscale/tailscale#19599`: split `LocalBackend.NetMap` into
  `NetMapNoPeers` / `NetMapWithPeers`. This landed before the `v1.98` line and
  matches the observed boundary.
- `tailscale/tailscale#17311`: `tsnet.(*Server).Start` can hit Android
  `netlinkrib: permission denied`.
- `tailscale/tailscale#19455`: proposed Android `InterfaceGetter` using
  `getifaddrs`; the PR notes successful Android `tsnet` peer-map sync and
  MagicDNS in an `untrusted_app` context.

## Slow Rebuild Method

This method does not require any temporary app code. It rebuilds and installs
the APK once per version.

```bash
version=v1.92.5
go mod download "tailscale.com@${version}"
mod_dir="$(go env GOPATH)/pkg/mod/tailscale.com@${version}"
gvisor_version="$(awk '$1=="gvisor.dev/gvisor" {print $2}' "$mod_dir/go.mod")"
crypto_version="$(awk '$1=="golang.org/x/crypto" {print $2}' "$mod_dir/go.mod")"

cd native/tsnet-helper
go get "tailscale.com@${version}" \
  "gvisor.dev/gvisor@${gvisor_version}" \
  "golang.org/x/crypto@${crypto_version}"
go mod tidy
go test ./...
cd ../..

FDROID_NATIVE_SKIP_TERMUX_BUILD=1 ./scripts/docker-fdroid-build-debug-apk.sh
adb install -r app/build/outputs/apk/fdroidDebug/app-fdroidDebug.apk
```

Then run the app's Tailscale route test and inspect:

```bash
adb logcat -d -v time | rg 'Pocket Backup Tailscale|tsnet status|tsnet target|available peers'
```

A version is good if `tsnet status` reports nonzero peers. It is bad if it
reports `peers=0` after `state=Running`.

## Fast Matrix Method

The fast matrix avoids rebuilding the APK for each version, but it requires a
temporary `fdroidDebug` receiver that:

1. Restores the encrypted Tailscale state through `TailscaleManager`.
2. Runs a helper binary path passed by ADB.
3. Logs the helper output.
4. Clears the plaintext state directory.

Important Android detail: the app cannot execute arbitrary binaries from
`/data/user/0/<package>/files` because SELinux blocks `execute_no_trans` on
`app_data_file`. Put temporary helper binaries in the extracted native library
directory instead, using the same context as `libtsnet_nc_exec.so`:

```bash
adb shell dumpsys package com.ttv20.rsyncbackup | rg 'legacyNativeLibraryDir'
adb shell su -c 'ls -lZ /data/app/.../lib/arm64/libtsnet_nc_exec.so'
```

The expected context is:

```text
u:object_r:apk_data_file:s0
```

Compile candidate helpers:

```bash
versions='v1.90.6 v1.90.7 v1.90.8 v1.90.9 v1.92.0 v1.92.1 v1.92.2 v1.92.3 v1.92.4 v1.92.5 v1.94.0 v1.94.1 v1.94.2 v1.96.0 v1.96.1 v1.96.2 v1.96.3 v1.96.4 v1.96.5 v1.98.0 v1.98.1 v1.98.2 v1.98.3 v1.98.4 v1.98.5 v1.100.0'
out_dir="$PWD/native/tsnet-version-matrix"
mkdir -p "$out_dir"

for version in $versions; do
  go mod download "tailscale.com@${version}"
  mod_dir="$(go env GOPATH)/pkg/mod/tailscale.com@${version}"
  gvisor_version="$(awk '$1=="gvisor.dev/gvisor" {print $2}' "$mod_dir/go.mod")"
  crypto_version="$(awk '$1=="golang.org/x/crypto" {print $2}' "$mod_dir/go.mod")"

  (
    cd native/tsnet-helper
    go get "tailscale.com@${version}" \
      "gvisor.dev/gvisor@${gvisor_version}" \
      "golang.org/x/crypto@${crypto_version}"
    go mod tidy
    GOOS=android GOARCH=arm64 CGO_ENABLED=0 \
      go build -trimpath -ldflags='-s -w' \
      -o "$out_dir/tsnet-nc-${version}" .
  )
done
```

Push them to the device and copy them into the native library directory:

```bash
adb push native/tsnet-version-matrix /data/local/tmp/tsnet-version-matrix

native_dir='/data/app/.../com.ttv20.rsyncbackup-.../lib/arm64'
adb shell "su -c 'cp /data/local/tmp/tsnet-version-matrix/tsnet-nc-v* $native_dir/; chown system:system $native_dir/tsnet-nc-v*; chmod 755 $native_dir/tsnet-nc-v*; chcon u:object_r:apk_data_file:s0 $native_dir/tsnet-nc-v*'"
```

Run each helper through the temporary receiver. Use `bardugo-home` as a
fast-fail target if it remains absent from the app node's peer map:

```bash
for version in $versions; do
  helper="$native_dir/tsnet-nc-${version}"
  adb logcat -c
  adb shell am broadcast --receiver-foreground \
    -n com.ttv20.rsyncbackup/.debug.FdroidDebugTailscaleProbeReceiver \
    -a com.ttv20.rsyncbackup.debug.FDROID_TAILSCALE_HELPER_PROBE \
    --es helperPath "$helper" \
    --es host bardugo-home \
    --ei port 22 >/dev/null
  sleep 12
  adb logcat -d -v time |
    rg 'helper override|tsnet status|tsnet target peer|tsnet available peers|not visible'
done
```

Clean up after the matrix:

```bash
adb shell "su -c 'rm -rf /data/local/tmp/tsnet-version-matrix; rm -f $native_dir/tsnet-nc-v*'"
rm -rf native/tsnet-version-matrix
```

## Final Verification

After pinning the fallback version, rebuild and verify the packaged helper:

```bash
FDROID_NATIVE_SKIP_TERMUX_BUILD=1 ./scripts/docker-fdroid-build-debug-apk.sh
adb install -r app/build/outputs/apk/fdroidDebug/app-fdroidDebug.apk
go version -m native/fdroid-out/assets/native/arm64-v8a/tsnet-nc |
  rg 'go[0-9]|tailscale.com|golang.org/x/crypto|gvisor.dev/gvisor|GOOS|CGO'
```

Expected:

```text
tailscale.com v1.92.5
golang.org/x/crypto v0.45.0
gvisor.dev/gvisor v0.0.0-20250205023644-9414b50a5633
GOOS=android
CGO_ENABLED=0
```
