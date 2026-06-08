<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="96" alt="Pocket Backup app icon">

# Pocket Backup

Pocket Backup backs up your Android files to a server you own, using `rsync` over
SSH. No root, no Termux, no cloud middleman — `rsync`, OpenSSH, and Tailscale are
all bundled into the APK, so the phone has everything it needs out of the box.

Point a profile at a folder (typically under `/storage/emulated/0`), pick the
server and remote path, and it syncs. If the server isn't on your local network,
it can connect over Tailscale instead.

## Features

- **Key-based auth.** Generates an Ed25519 key, or uses one you provide. It can
  install the public key on the server for you the first time using a temporary
  password, then switches to key-only.
- **Pinned host keys.** SSH fingerprints are scanned and stored before a backup
  runs, so connections are verified rather than trusted blindly.
- **Flexible routing.** LAN-only, Tailscale-only, or LAN with a Tailscale
  fallback for when you're off your home network.
- **Manual or scheduled.** Run a backup on demand, or set a daily schedule —
  exact alarms where Android permits, best-effort otherwise.
- **Conditional runs.** Gate scheduled backups on Wi-Fi, unmetered network,
  charging, battery level, or a specific SSID.
- **Visibility.** Live progress, final transfer stats, and retained logs from
  previous runs.
- **Config export/import.** Move your setup between devices (secrets excluded —
  see below).

## Delete safety

Before any backup that uses `rsync --delete`, Pocket Backup checks the target for
a marker file, `.android-rsync-backup-root`. If the directory is non-empty and
unmarked, the app won't write to it until the profile explicitly allows that
target. This keeps a misconfigured path from wiping a directory that isn't a
backup root.

It maintains three files in the backup root:

```text
.android-rsync-backup-root
.backup-status.json
.backup-last.log
```

## Requirements

- Android 10 (API 29) or newer, `arm64-v8a` only.
- All-files access, so the app can read the source folder.
- An SSH server where the login account can create and write the backup
  directory. There's no remote `sudo` — the app only writes where that account
  already can.

Secrets stay on the device. Private SSH keys, passphrases, passwords, Tailscale
auth keys, and Tailscale state are never written to export files, so an export
is safe to share but carries no credentials.

## Building

The Docker build provides the full Android toolchain, so the host needs no JDK
or Android SDK:

```bash
./scripts/docker-build-debug.sh
```

Output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install on a connected device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Run unit tests:

```bash
./scripts/docker-test.sh
```

Rebuild the bundled native binaries only when changing the
rsync/OpenSSH/Tailscale payload:

```bash
./scripts/fetch-termux-native-binaries.py
./scripts/build-tsnet-helper.sh
./scripts/stage-native-assets.sh
```

## Release builds

Release builds use the same Docker image:

```bash
./scripts/docker-build-release.sh
```

A local signed build reads these from the environment:

```text
POCKETSYNC_RELEASE_STORE_FILE
POCKETSYNC_RELEASE_STORE_PASSWORD
POCKETSYNC_RELEASE_KEY_ALIAS
POCKETSYNC_RELEASE_KEY_PASSWORD
POCKETSYNC_VERSION_CODE
POCKETSYNC_VERSION_NAME
```

The release workflow (`.github/workflows/release-apk.yml`) runs on `v*` tags and
uses these repository secrets:

```text
POCKETSYNC_RELEASE_KEYSTORE_B64
POCKETSYNC_RELEASE_STORE_PASSWORD
POCKETSYNC_RELEASE_KEY_ALIAS
POCKETSYNC_RELEASE_KEY_PASSWORD
```

## Documentation

- Setup and server details: [docs/setup.md](docs/setup.md)
- F-Droid readiness notes: [docs/fdroid-readiness-plan.md](docs/fdroid-readiness-plan.md)
- License: [LICENSE.md](LICENSE.md)
- Third-party notices: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)
