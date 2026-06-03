# Setup And Server Requirements

Rsync Backup Android is a personal sideload app for backup-only rsync-over-SSH
workflows. It does not provide a restore workflow in the first release.

## Android Setup

- Install the APK on an Android 10/API 29 or newer arm64 device.
- Grant all-files access so the app can read the configured source tree.
- Grant notification access on Android versions that require it.
- Grant battery optimization exemption if scheduled/background backups should be
  reliable.
- Grant exact alarm access when exact daily schedules are required. If exact
  alarm access is denied, the app uses best-effort alarms.
- Wi-Fi/SSID permission is optional and only needed for the selected-SSID
  constraint.

Android background limits still apply. Keep at least one foreground backup
notification visible while a backup is running.

Profile source paths can be entered as raw filesystem paths. The profile editor
also includes an Android folder picker for primary shared-storage folders,
which maps selected folders to `/storage/emulated/0/...` paths for rsync.

## SSH Server

The remote account must be able to create and write the configured backup path.
No remote sudo support is required or expected.

Required server capabilities:

- OpenSSH-compatible SSH service reachable over the selected LAN and/or
  Tailscale address.
- Password auth available temporarily if using the app's one-time public-key
  install flow.
- Public-key auth available for all future backups.
- A writable target directory, or permission to create it when the profile
  allows creating missing targets.

Before a delete-enabled backup, the app checks the remote target for
`.android-rsync-backup-root`. If the directory is non-empty and unmarked, the
profile must explicitly allow that target before the app writes the marker.

The app writes these remote files:

```text
.android-rsync-backup-root
.backup-status.json
.backup-last.log
```

## Tailscale

Tailscale is optional and global to the app. The embedded tsnet identity uses
the configured node name, normally `<hostname>-rsync`.

- Paste an auth key once in the Tailscale screen.
- The auth key is passed to the helper process for login and then cleared from
  UI state; it is not exported.
- tsnet state is stored locally with Android Keystore-backed encryption.
- Reset removes both plaintext and encrypted tsnet state.
- Tailscale runs on demand for tests and backup operations, then exits.

After setup, disable key expiry for this app machine in the Tailscale admin
console when long-term unattended backups are expected. Tailscale node/device
key expiry can still break future backups until the app is reauthenticated.

## Export And Import

Export includes non-secret configuration: profiles, servers, trusted host keys,
public SSH key, excludes, schedules, and settings.

Export excludes private SSH keys, passphrases, passwords, Tailscale auth keys,
and Tailscale state.
