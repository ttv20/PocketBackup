# Android Rsync Backup App Build Plan

This plan describes the standalone Android app that will replace the current
Termux-based Android backup flow with a full APK.

The Android project should be created as a separate repository or project
directory, not inside this repository. This document is the implementation brief
for the next agent.

## Product Scope

- Package name: `com.ttv20.rsyncbackup`
- Minimum Android version: Android 10 / API 29
- Primary tested target: Android 11+
- ABI: `arm64-v8a` only
- UI: Kotlin + Jetpack Compose
- App type: personal/sideload app, suitable for open-source publication
- First release: backup-only, no restore workflow

The app must be standalone. It should not depend on Termux.

Bundled native components:

- `rsync`
- OpenSSH client
- Go `tsnet` helper, used like `tailscale nc` / SSH `ProxyCommand`

## Build And Test Environment Requirement

Use a prebuilt Docker container that includes:

- Android SDK
- Android build tools
- Gradle
- Java/Kotlin toolchain
- NDK or native build dependencies needed for bundled binaries
- Build cache included or mounted for repeated fast builds

The build should not require manually installed Android Studio on the host.

Test the app on Docker-hosted QEMU Android. Use the Ampere server if it helps
performance, especially for ARM64 emulator/device images and native binary
testing.

Expected build/test assets:

- Dockerfile or documented prebuilt image name
- Gradle cache strategy
- Android SDK cache strategy
- Native binary cache strategy
- Script to build debug APK in Docker
- Script to run emulator/QEMU smoke tests in Docker

## Stage 1: Project Bootstrap

Deliverables:

- New Android project with package `com.ttv20.rsyncbackup`
- Kotlin + Jetpack Compose configured
- Min SDK 29
- Target SDK set to current stable Android SDK available in the build image
- Basic app navigation shell
- CI/build script that produces a debug APK inside Docker

Screens:

- Dashboard
- Profiles
- Servers
- Tailscale
- Settings
- Logs

Acceptance checks:

- Docker build succeeds from a clean checkout
- Debug APK installs on QEMU Android
- App opens to Dashboard without crashes

## Stage 2: Data Model And Storage

Create persistent models for:

- Global app settings
- Global SSH key settings
- Global Tailscale state metadata
- Server records
- Backup profiles
- Schedules
- Constraint settings
- Trusted SSH host fingerprints
- Local logs

Use encrypted storage for private material:

- Generated SSH private key
- Pasted custom SSH private key
- Custom key passphrase, if retained
- Tailscale state where practical

Use Android Keystore-backed encryption.

Export/import support is part of v1.0. Export profile/server config,
fingerprints, public SSH key, excludes, schedules, and settings. Do not export
private SSH keys, passwords, Tailscale auth keys, or Tailscale state.

Acceptance checks:

- Profiles and servers persist across app restart
- Export omits secrets
- Import restores non-secret configuration

## Stage 3: Permissions And First-Run Setup

First-run setup should ask for:

- All-files access
- Battery optimization exemption
- Exact alarm access
- Wi-Fi/SSID permission, optional and non-blocking

Android 11+ is the main storage target. Android 10 should be supported but can
be tested second.

SSID permission is only needed for the selected-SSID constraint. It should not
block app setup if denied.

If exact alarm access is denied, fall back to best-effort scheduling.

Acceptance checks:

- App can detect missing permissions and guide user to settings
- Denying optional SSID permission does not block backup setup
- Denying exact alarm access keeps best-effort scheduling available

## Stage 4: SSH Key And Server Setup

Default SSH model:

- App generates an Ed25519 SSH key
- Public key is shown in UI
- Private key is stored encrypted

Also support pasted custom private keys, including passphrase-protected keys.

For initial server setup, use a Kotlin SSH library rather than driving OpenSSH
password auth from the command line.

Setup flow:

1. User enters server user, LAN host, optional Tailscale host, port, and remote
   path.
2. App connects over whichever configured path works.
3. App shows SSH host fingerprint and requires confirmation.
4. User enters SSH password once.
5. App creates `~/.ssh` if needed.
6. App appends the generated public key to `~/.ssh/authorized_keys`.
7. App fixes normal `~/.ssh` and `authorized_keys` permissions.
8. App discards the password.
9. Future backups use key-only OpenSSH.

No remote sudo support is required. The server user must be able to create and
write the configured backup path.

Acceptance checks:

- Host fingerprint confirmation is required before trust
- Generated public key can be installed with one-time password auth
- Password is not retained
- Future SSH test uses key-only auth

## Stage 5: Tailscale Integration

Tailscale is global and optional.

The app owns one embedded Tailscale identity:

- Node name: `<hostname>-rsync`
- Auth method: pasted Tailscale auth key
- Auth key is discarded after successful login
- Reauth uses a new auth key
- Provide a destructive "reset Tailscale state" action

Show setup advice: disable key expiry for this app machine in the Tailscale
admin console after setup.

The Go helper should provide the equivalent of:

```text
tailscale nc <host> <port>
```

The backup process should use it through SSH `ProxyCommand`.

Tailscale runs on demand only during test/backup operations. It should not stay
connected in the background.

Acceptance checks:

- App can authenticate using an auth key
- Auth key is erased after successful login
- Tailscale test can reach the configured server host/port
- Reset removes local Tailscale state
- Tailscale auth/state failure produces a specific notification

## Stage 6: Profile And Target Configuration

Profiles support:

- Name
- Source path
- Server record
- Remote path
- Target mode
- Schedule
- Constraints
- Delete toggle
- Excludes
- Advanced rsync args

Default source:

```text
/storage/emulated/0
```

Allow changing source with both:

- Android folder picker
- Raw path text

Target model:

```text
user: ttv20
path: /mnt/backup/phone
primary LAN host: 192.168.3.200
fallback Tailscale host: 100.x.y.z or MagicDNS name
port: 22
```

LAN and Tailscale addresses for the same server should share one trusted server
identity/fingerprint record.

Target modes:

- LAN only
- LAN first, Tailscale fallback
- Tailscale first, LAN fallback
- Tailscale only

Fallback specifically means Tailscale fallback. If Tailscale is disabled, modes
requiring Tailscale must be unavailable or fail with a clear setup error.

Acceptance checks:

- Multiple profiles can share one server
- Each profile has independent source, path, schedule, constraints, excludes,
  and args
- Invalid Tailscale-dependent profile state is visible before scheduled runs

## Stage 7: Remote Safety And Markers

Delete is enabled by default, but the app must protect obvious mistakes.

Before first real backup to a profile target:

1. Connect to the server.
2. Create the remote directory after warning/confirmation.
3. Check for `.android-rsync-backup-root`.
4. If missing and the directory is non-empty, warn before proceeding.
5. After confirmation, write `.android-rsync-backup-root`.

Remote files written by the app:

```text
.android-rsync-backup-root
.backup-status.json
.backup-last.log
```

Status JSON should include:

- Profile id
- Profile name
- Phone hostname
- App version
- Source path
- Target host used
- Target mode
- Status
- Finish time
- Rsync exit code

Acceptance checks:

- Non-empty unmarked target warns before delete-enabled backup
- Status marker is written only after successful real backup
- Last log is uploaded after backup

## Stage 8: Backup Engine

Run one rsync process per backup.

Use a foreground service for backup execution. Native processes are controlled
by the Android service:

- `rsync`
- OpenSSH
- `tsnet` helper when needed

The app captures stdout/stderr, parses progress, and writes local logs.

Cancellation behavior:

1. User taps cancel.
2. App sends graceful interrupt.
3. App waits 10 seconds.
4. During the 10 seconds, user can force stop.
5. After timeout, app hard-kills the process tree.

Use rsync partial support so interrupted backups can converge on the next run.

Acceptance checks:

- Manual backup starts from UI
- Backup continues while app is backgrounded with visible notification
- Cancel performs graceful interrupt and then hard stop if needed
- Exit code `24` is treated as accepted warning, matching current scripts

## Stage 9: Rsync Options

Base rsync behavior should match the current Termux script where practical:

```text
-rt --links
--delete
--delete-delay
--delete-excluded
--partial
--partial-dir=.rsync-partial
--modify-window=2
--compress
--compress-choice=zstd
--compress-level=3
--info=stats2
```

Delete is controlled by a profile toggle.

Use the repository's existing Android excludes as defaults. Allow raw editable
rsync exclude text per profile.

Advanced rsync args are free text. Before saving/running, show a final command
preview so the user can see the effective command.

Acceptance checks:

- Default excludes are loaded for new profiles
- User can edit excludes
- Advanced args appear in command preview
- Delete toggle changes the generated rsync command

## Stage 10: Scheduling, Constraints, And Queue

Schedules are per profile.

Support:

- Exact daily schedule
- Best-effort schedule

If exact alarm permission is denied, use best-effort schedule.

If an exact alarm fires and constraints are not met, notify with "Run anyway".
Missed schedules do not catch up; wait for the next scheduled run.

Constraint toggles per profile:

- Wi-Fi only
- Unmetered only
- Charging only
- Battery not low
- Selected SSID only
- Manual override allowed

No storage-low toggle is needed.

Backups run in one global queue. Parallel backup jobs are not allowed. If one
profile is running and another profile fires, queue the second job.

Acceptance checks:

- Manual backup can run despite failed constraints after warning
- Scheduled backup checks constraints
- Queue runs one backup at a time
- Exact-alarm denial falls back cleanly

## Stage 11: Notifications

Notification channels:

- Running backup
- Backup result
- Tailscale auth/state problem
- Constraint warning

Notifications should be actionable where useful:

- Cancel running backup
- Force stop during cancellation grace period
- Run anyway when constraints fail
- Open Tailscale setup on auth/state problem

Acceptance checks:

- Foreground notification is present during backup
- Backup success/failure notification appears
- Tailscale failure and backup failure are reported separately

## Stage 12: Progress And Logs

Parse rsync output for:

- Files discovered/transferred so far
- Current file
- Bytes transferred
- Transfer speed
- Duration
- Final stats

Do not add a pre-scan just to estimate files remaining.

Keep the last 20 local logs by default.

Acceptance checks:

- Run screen shows live parsed progress
- Raw log remains available
- Old local logs are pruned after retention limit

## Stage 13: Full Compose UI

Build the full UI in v1 rather than a minimal placeholder UI.

Required screens:

- Dashboard: profile status, current run, queue
- Profile list
- Profile editor
- Server list
- Server setup/test
- SSH key management
- Tailscale setup/status/test/reset
- Permission setup/status
- Backup run screen
- Logs
- Import/export
- Settings

Avoid hiding destructive choices. Delete-enabled backup, non-empty target, reset
Tailscale state, and custom advanced args must be visible decisions.

Acceptance checks:

- User can configure a profile end-to-end without editing files
- User can run and monitor a backup from the app
- User can diagnose missing permissions, SSH failures, and Tailscale failures

## Stage 14: Docker/QEMU Testing

Build and test inside Docker.

Required test layers:

- JVM/unit tests for command generation, profile validation, export/import, and
  status marker JSON
- Android instrumentation tests for UI and permission-state flows
- Docker QEMU Android smoke test that installs the APK and launches the app
- Native binary smoke test on Android/QEMU:
  - `rsync --version`
  - `ssh -V`
  - `tsnet` helper starts and reports a useful failure before auth

If the Ampere server improves performance, use it for ARM64 emulator/QEMU runs.

Acceptance checks:

- A documented command builds the APK in Docker
- A documented command runs QEMU smoke tests in Docker
- Test output is reproducible from a clean checkout plus caches

## Stage 15: Release Packaging

Release artifacts:

- Debug APK
- Release APK
- Source repository
- Native binary build scripts
- Prebuilt arm64 native binaries for release builds
- Documentation for setup and server requirements

Original app code can be permissive, but release packaging must preserve
third-party license obligations because bundled components include GPL-covered
software such as `rsync`.

Documentation should clearly state:

- The app is personal/sideload oriented
- It uses all-files access
- It stores private SSH/Tailscale state locally
- Tailscale auth keys are discarded after login
- Tailscale node/device key expiry can still break future backups
- Android background limits still apply
