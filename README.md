<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="96" alt="PocketBackup app icon">

# PocketBackup

PocketBackup backs up Android shared storage to a server you control. It runs
`rsync` over SSH from the phone, with an optional built-in Tailscale route for
servers that are not reachable on the local network.

## What It Does

- Backs up a selected filesystem path, usually under `/storage/emulated/0`.
- Uses bundled `rsync` and OpenSSH binaries; Termux is not required.
- Supports LAN-only, Tailscale-only, and LAN/Tailscale fallback targets.
- Generates or stores an Ed25519 SSH key and can install the public key with
  temporary password auth.
- Scans and stores SSH host key fingerprints before backup runs.
- Runs profiles manually or on daily exact/best-effort schedules.
- Can require Wi-Fi, unmetered network, charging, battery-not-low, or a selected
  SSID before scheduled backups.
- Shows foreground backup progress, recent output, final stats, and retained
  run logs.
- Exports/imports non-secret configuration.

## Limits

- Android 10/API 29 or newer.
- `arm64-v8a` devices only.
- The app requests all-files access so it can read the configured source tree.
- Private SSH keys, passphrases, passwords, Tailscale auth keys, and Tailscale
  state are stored locally and are not included in export files.
- No remote sudo flow. The configured SSH account must be able to create and
  write the backup directory.

## Remote Directory Safety

Before a delete-enabled backup, PocketBackup checks the remote directory for a
marker file named `.android-rsync-backup-root`. If the directory is non-empty
and unmarked, the profile must explicitly allow it before the app writes there.

PocketBackup writes these files in the backup root:

```text
.android-rsync-backup-root
.backup-status.json
.backup-last.log
```

## Build

Use the Docker build script. It supplies the Android build environment, so the
host does not need a JDK or Android SDK:

```bash
./scripts/docker-build-debug.sh
```

The APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install it on a connected device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Run unit tests:

```bash
./scripts/docker-test.sh
```

Refresh bundled native assets only when changing the rsync/OpenSSH/Tailscale
payload:

```bash
./scripts/fetch-termux-native-binaries.py
./scripts/build-tsnet-helper.sh
./scripts/stage-native-assets.sh
```

## Release Notes For Maintainers

Release builds use the same Docker image:

```bash
./scripts/docker-build-release.sh
```

For a local signed release build, set:

```text
POCKETSYNC_RELEASE_STORE_FILE
POCKETSYNC_RELEASE_STORE_PASSWORD
POCKETSYNC_RELEASE_KEY_ALIAS
POCKETSYNC_RELEASE_KEY_PASSWORD
POCKETSYNC_VERSION_CODE
POCKETSYNC_VERSION_NAME
```

The GitHub release workflow is `.github/workflows/release-apk.yml` and runs on
`v*` tags. Its repository secrets are:

```text
POCKETSYNC_RELEASE_KEYSTORE_B64
POCKETSYNC_RELEASE_STORE_PASSWORD
POCKETSYNC_RELEASE_KEY_ALIAS
POCKETSYNC_RELEASE_KEY_PASSWORD
```

## More

- Setup and server details: [docs/setup.md](docs/setup.md)
- F-Droid readiness notes: [docs/fdroid-readiness-plan.md](docs/fdroid-readiness-plan.md)
- License: [LICENSE.md](LICENSE.md)
- Third-party notices: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)
