# Android Build And Test

The project builds in Docker and does not require Android Studio on the host.

Default prebuilt image:

```text
cimg/android:2025.12
```

That image currently provides Java 21, Gradle 9.2.1, Android build tools, and
SDK platforms through Android 36. The project target SDK is 36 and min SDK is
29.

## Caches

- Gradle cache: `.gradle-cache/` by default, override with
  `RSYNC_BACKUP_GRADLE_CACHE`.
- Android SDK cache: included in the Docker image.
- Debug signing: `scripts/ensure-debug-keystore.sh` is called by the Docker
  build scripts and creates a persistent
  ignored keystore at `.android/debug.keystore` so repeated debug builds can be
  installed with `adb install -r` without signature churn.
- Termux package cache: `.termux-package-cache/`, populated by
  `scripts/fetch-termux-native-binaries.py`.
- Go cache: `.go-cache/`, populated by `scripts/build-tsnet-helper.sh`.
- Native binary output: `native/out/arm64-v8a/`, staged into
  `app/src/main/assets/native/arm64-v8a/`.

## Commands

The Ampere/redroid smoke scripts require `AMPERE_HOST`, for example
`AMPERE_HOST=ubuntu@your-ampere-host ./scripts/ampere-redroid-smoke.sh`.

```bash
./scripts/fetch-termux-native-binaries.py
./scripts/build-tsnet-helper.sh
./scripts/check-native-deps.sh
./scripts/stage-native-assets.sh
./scripts/docker-build-debug.sh
./scripts/docker-build-release.sh
./scripts/docker-test.sh
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
AMPERE_HOST=ubuntu@your-ampere-host TS_AUTHKEY=... ./scripts/ampere-redroid-tailscale-live-smoke.sh
```

Build artifacts:

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release-unsigned.apk
app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
```

`scripts/ampere-redroid-smoke.sh` uses the ARM64 Ampere host to run
`redroid/redroid:13.0.0-latest`, installs the debug APK, launches the app, and
executes the native smoke through ADB. It requires passwordless sudo on the
host for binderfs setup and Docker.

`scripts/docker-qemu-smoke.sh` is retained as an optional Android SDK emulator
experiment. The requested Android runtime validation uses Ampere/redroid,
because the local Docker host is `x86_64` while this APK is ARM64-only.

`scripts/ampere-redroid-e2e-backup-smoke.sh` builds on the redroid smoke by
starting a temporary SSH server on Ampere, using the app debug harness to
install a public key with one-time password auth, running a key-only rsync
backup from Android to Ampere, and verifying the transferred file plus remote
marker/status/log files.

`scripts/ampere-redroid-run-progress-ui-smoke.sh` reuses the same end-to-end
setup with a throttled transfer, opens the Run screen before the backup starts,
and verifies visible live progress while rsync is running.

`scripts/ampere-redroid-cancellation-smoke.sh` reuses the same end-to-end
setup, slows rsync with debug-only advanced args, requests cancellation from the
Android service, and verifies the app records a cancelled run with no leftover
Android `rsync` or `ssh` process.

`scripts/ampere-redroid-manual-run-anyway-smoke.sh` reuses the same
end-to-end setup, configures a failing selected-SSID constraint, verifies the
manual constraint notification, taps the real "Run anyway" notification action
through System UI, and verifies the backup completes.

`scripts/ampere-redroid-schedule-constraint-smoke.sh` configures a debug-only
scheduled profile, lets the production scheduler deliver an Android alarm, and
verifies the constraint warning notification includes the run-anyway action.

`scripts/ampere-redroid-exact-denial-smoke.sh` forces the Android
`SCHEDULE_EXACT_ALARM` app-op to `deny`, configures an exact daily profile, and
verifies the best-effort fallback alarm still posts the constraint warning with
the run-anyway action.

`scripts/ampere-redroid-tailscale-failure-smoke.sh` configures a debug-only
Tailscale-only profile with no Tailscale state and verifies the dedicated
Tailscale setup notification includes the open-setup action.

`scripts/ampere-redroid-tailscale-live-smoke.sh` requires `TS_AUTHKEY`,
`TAILSCALE_AUTHKEY`, or `RSYNC_BACKUP_TS_AUTHKEY`. It starts redroid on Ampere,
runs an arg-gated Compose instrumentation test against the production Tailscale
screen, authenticates with the supplied auth key, verifies the key is not
persisted in app state, and tests TCP reachability over tsnet. If
`TAILSCALE_TEST_HOST` and `TAILSCALE_TEST_PORT` are not supplied, it starts a
temporary listener on the local machine's Tailscale IPv4 address and uses that
as the reachability target.

`scripts/ampere-redroid-instrumentation-smoke.sh` installs the debug and
androidTest APKs on the same redroid device and runs the Compose instrumentation
smoke tests through `AndroidJUnitRunner`, including dashboard launch, core
navigation, permission rows, and setup-control traversal in compact layout.

`scripts/ampere-redroid-ui-setup-smoke.sh` starts a temporary password-enabled
SSH server on Ampere, then drives the real Compose UI on redroid to generate an
SSH key, edit server fields, scan and trust host keys, install the public key
with one-time password auth, configure a LAN-only profile, run backup from the
Run screen, and verify remote files/status/log output.
