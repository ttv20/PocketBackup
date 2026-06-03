# Implementation Status

Current checkout: standalone Android project scaffold with core backup app
implementation, native asset bundling, SSH host/key setup, remote target safety,
scheduling / constraints / queue wiring, foreground process cancellation, and
live run progress state.

Verified locally:

```bash
./scripts/fetch-termux-native-binaries.py
./scripts/build-tsnet-helper.sh
./scripts/check-native-deps.sh
./scripts/stage-native-assets.sh
./scripts/docker-test.sh
./scripts/docker-build-debug.sh
./scripts/docker-build-release.sh
./scripts/docker-build-android-test.sh
./scripts/ampere-redroid-smoke.sh
./scripts/ampere-redroid-instrumentation-smoke.sh
./scripts/ampere-redroid-ui-setup-smoke.sh
./scripts/ampere-redroid-e2e-backup-smoke.sh
./scripts/ampere-redroid-run-progress-ui-smoke.sh
./scripts/ampere-redroid-cancellation-smoke.sh
./scripts/ampere-redroid-manual-run-anyway-smoke.sh
./scripts/ampere-redroid-schedule-constraint-smoke.sh
./scripts/ampere-redroid-exact-denial-smoke.sh
./scripts/ampere-redroid-tailscale-failure-smoke.sh
```

Results:

- JVM unit tests pass for command generation, profile validation,
  export/import secret omission, status marker JSON, rsync output parsing,
  constraint evaluation, exact-alarm fallback policy, queue behavior/recovery,
  process cancellation, live progress recovery, SSH runtime key/known_hosts
  files, host-key scan parsing, SSH key generation, remote route connectivity probes,
  passphrase-protected key SSH options, authorized_keys setup script
  generation, source folder picker path mapping, and Tailscale state archive
  restore safety.
- Debug and release APKs build in Docker.
- Android instrumentation APK builds in Docker.
- Current debug APK SHA-256:
  `b5332358c670d15f8be1016ceae1623fb0ae08225316d97c9dced77f20c0bd8f`.
- Current androidTest APK SHA-256:
  `1f53c3e2b17b13874ee55552f7b2407582e20adc51160227caa963f51e07689f`.
- Current release APK SHA-256:
  `82078a279b9e620775cc72b92d1dc99f5c062cf6bfd8807746cd3da2579c6586`.
- APK is filtered to `arm64-v8a`.
- `rsync` and OpenSSH client binaries are fetched from the Termux aarch64
  package repository with SHA-256 verification, along with their shared
  libraries and package manifest.
- `tsnet-nc` builds as an Android arm64 executable in `native/out/arm64-v8a/`.
  The helper supports ProxyCommand streaming plus `--up` auth/status and
  `--check` reachability modes.
- The debug APK includes `rsync`, `ssh`, `tsnet-nc`, and bundled runtime
  libraries under `assets/native/arm64-v8a/`.
- Ampere redroid smoke passes on Android 13 arm64:
  - `rsync --version`
  - `ssh -V`
  - `tsnet-nc --help`
- Ampere redroid instrumentation smoke passes on Android 13 arm64:
  - Dashboard launches
  - core Compose screens are reachable through navigation
  - permission-state screen shows required and optional permission rows
  - profile, server, SSH key, Tailscale, and settings setup screens expose the
    expected end-to-end controls in the compact portrait layout
  - arg-gated UI setup and live Tailscale instrumentation tests skip cleanly
    when their external fixture arguments are absent
- Ampere redroid UI-driven setup smoke passes on Android 13 arm64 through the
  production Compose screens:
  - starts a temporary password-enabled SSH server on Ampere port 2222
  - generates an Ed25519 SSH key from the SSH keys screen
  - edits and saves the default server user, LAN host, port, and remote path
  - scans and trusts all returned LAN host keys from bundled
    `ssh-keyscan`/`ssh-keygen`
  - installs the generated public key through the one-time password setup UI
  - configures the default profile source, remote path, LAN-only target mode,
    and battery constraint through the profile UI
  - starts the backup from the Run screen
  - verifies the transferred file, nested file, `.android-rsync-backup-root`,
    `.backup-status.json`, `.backup-last.log`, and appended `authorized_keys`
- Ampere redroid end-to-end backup smoke passes on Android 13 arm64 through
  the debug harness:
  - starts a temporary password-enabled SSH server on Ampere port 2222
  - trusts the scanned Ed25519/RSA host keys in app state
  - installs the generated public key using one-time SSH password auth
  - stores a passphrase-protected private key and passphrase in app secrets
  - runs the backup from redroid to Ampere with askpass-backed, key-only
    OpenSSH/rsync
  - verifies the transferred file, `.android-rsync-backup-root`,
    `.backup-status.json`, `.backup-last.log`, and appended `authorized_keys`
- Ampere redroid Run-progress UI smoke passes on Android 13 arm64 through the
  debug harness:
  - opens the Run screen before a throttled backup starts
  - verifies the Run screen is selected through the `MainActivity` screen extra
  - verifies visible "Live progress", "Running rsync", and current file text
    while rsync is still running
  - verifies the backup still completes and writes remote marker/status/log
    files
- Ampere redroid cancellation smoke passes on Android 13 arm64 through the
  debug harness:
  - generates a larger deterministic source payload
  - slows rsync with debug-only advanced args
  - requests cancellation from the Android backup service during transfer
  - verifies the app records `CANCELLED` progress/status with rsync output
  - verifies no Android `rsync` or `ssh` process is left after cancellation
- Ampere redroid manual run-anyway constraint smoke passes on Android 13 arm64
  through the debug harness and System UI:
  - configures a valid SSH-backed profile with a failing selected-SSID
    constraint
  - verifies the manual-start constraint notification includes "Run anyway"
  - opens the notification shade and taps the real notification action
  - verifies the run-anyway action bypasses the failed constraint and the backup
    writes remote marker/status/log files
- Ampere redroid scheduled-constraint smoke passes on Android 13 arm64 through
  the debug harness:
  - configures a scheduled profile using the production `BackupScheduler`
  - grants Android 13 notification permission for the test app
  - lets Android deliver the scheduled alarm
  - verifies the constraint warning notification appears with "Run anyway"
- Ampere redroid exact-alarm-denial smoke passes on Android 13 arm64 through
  the debug harness:
  - forces the `SCHEDULE_EXACT_ALARM` app-op to `deny`
  - configures an `EXACT_DAILY` profile using the production `BackupScheduler`
  - verifies the profile remains exact-daily while the scheduler selects the
    best-effort fallback path
  - verifies the fallback alarm posts the constraint warning notification with
    "Run anyway"
- Ampere redroid Tailscale failure notification smoke passes on Android 13
  arm64 through the debug harness:
  - configures a Tailscale-only profile with no configured Tailscale state
  - verifies the profile records a Tailscale setup failure separately from
    ordinary backup failure checks
  - verifies the dedicated Tailscale notification appears with "Open setup"
- Remote target safety command layer is implemented and unit-tested:
  - creates missing remote target directories when profile policy allows it
  - refuses non-directory targets
  - refuses non-empty unmarked targets unless explicitly allowed
  - writes `.android-rsync-backup-root`
  - uploads `.backup-status.json` and `.backup-last.log` after accepted backup
    results
- Scheduling, constraints, and global queue are wired:
  - profile add/save/delete schedules or cancels alarms
  - exact daily schedules use `setExactAndAllowWhileIdle` when exact alarms are
    available, otherwise best-effort `setAndAllowWhileIdle`
  - exact-alarm denial selects the best-effort schedule mode in JVM tests and
    is verified on redroid with the Android app-op forced to `deny`
  - fired daily alarms reschedule the next daily alarm before starting backup
  - scheduled and manual starts both pass through constraint checks
  - failed scheduled constraints post a constraint warning notification with
    "Run anyway" directly from the receiver when Android does not allow a
    background foreground-service start
  - failed manual constraints post the same warning notification through the
    foreground service when the profile allows manual override
  - manual run-anyway from the notification action is verified on redroid
  - accepted starts enqueue into one global queue, with no duplicate queued or
    running profile entries
  - stale persisted `runningProfileId` state is cleared on app load and marked
    as interrupted
  - real scheduled alarm delivery to the constraint-warning path is verified on
    redroid
- Backup execution now has explicit process control and live progress:
  - target-mode route order is probed with key-only SSH before remote safety
    and rsync, so LAN/Tailscale fallback modes use the first reachable route
  - Cancel requests graceful termination of the active native process
  - Force stop hard-kills the active native process after user action or the
    10-second cancellation grace timer
  - cancelled runs produce normal local logs and profile status updates
  - rsync output is parsed while it streams and shown on the Run screen
  - Run screen places live progress before command preview and shows phase,
    current file, discovered/transferred counters, bytes, speed, duration,
    final stats, and recent raw output
  - visible live Run-screen progress during a longer throttled backup is
    verified on redroid
- SSH setup is wired:
  - profile and server setup screens use a stacked list/editor layout in
    compact widths so setup labels and controls are not squeezed into a narrow
    side-by-side pane
  - profile source can be edited as a raw path or selected with Android's
    folder picker for primary shared-storage paths
  - generated Ed25519 private keys use the bundled modern BouncyCastle provider
    and are written as PKCS#8 PEM for OpenSSH at backup runtime
  - pasted PEM private keys are preserved when writing the runtime key file
  - passphrase-protected private keys use an app-private temporary askpass
    script while password and keyboard-interactive auth remain disabled
  - backup execution fails early with clear messages when private key material
    or trusted host-key material is missing
  - Server screen can scan LAN or Tailscale host keys with bundled
    `ssh-keyscan`/`ssh-keygen`, show SHA256 fingerprints, and store all
    confirmed public host keys for `known_hosts`
  - one-time password setup uses SSHJ with the trusted `known_hosts` file to
    append the configured public key to `~/.ssh/authorized_keys` and fix
    standard SSH directory/file permissions
- Tailscale setup is wired and live-tailnet validated on Ampere redroid:
  - auth runs the embedded `tsnet-nc --up` helper with the pasted auth key in
    the process environment, then clears the UI auth key value
  - tsnet state is archived into Android Keystore-backed `SecretStore`
  - reset deletes both plaintext and encrypted tsnet state
  - reachability test runs `tsnet-nc --check <host> <port>`
  - live auth succeeded for app node `android-rsync-live-1780473678`
  - the Tailscale auth key was not present in `files/app-state.json`
  - restored encrypted tsnet state reached `Running` during subsequent
    reachability checks
  - live reachability succeeded from the app node to `bardugo-home`
    (`100.86.75.50:22`) and this workstation (`100.118.251.94:22`)
  - backup execution restores encrypted tsnet state before a Tailscale route
    and persists/clears it after the run exits
  - Tailscale-related backup failures post a dedicated Tailscale problem
    notification with an action that opens the Tailscale screen
  - missing-state Tailscale failure notification delivery is verified on
    redroid
  - `scripts/ampere-redroid-tailscale-live-smoke.sh` runs the live
    auth/reachability validation when a tailnet auth key is supplied; without
    an auth key it fails fast with exit `88`

Artifacts:

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
app/build/outputs/apk/release/app-release-unsigned.apk
native/out/arm64-v8a/tsnet-nc
native/out/arm64-v8a/rsync
native/out/arm64-v8a/ssh
```

Residual notes:

- Tailscale missing-state failure notification/open-setup behavior is verified
  on redroid. Live auth and reachability are verified; invalid-key auth
  failures surface as Tailscale setup errors in app state.
- Cancellation currently controls the active native process and was verified
  against a real Android rsync/OpenSSH transfer. Tailscale ProxyCommand
  command construction is covered and uses the same native process controller;
  it was not separately cancelled during a live tailnet transfer.
