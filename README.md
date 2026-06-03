# Rsync Backup Android

Standalone Android APK for personal rsync-over-SSH backups.

- Package: `com.ttv20.rsyncbackup`
- Min SDK: 29
- Target SDK: 36
- ABI: `arm64-v8a`
- UI: Kotlin and Jetpack Compose
- Termux dependency: none

The first release is backup-only. It stores private SSH and Tailscale material
locally with Android Keystore-backed encryption, exports non-secret
configuration only, and runs backups through bundled `rsync`, OpenSSH, and the
`tsnet-nc` helper.

Native assets:

```bash
./scripts/fetch-termux-native-binaries.py
./scripts/build-tsnet-helper.sh
./scripts/stage-native-assets.sh
```

Build:

```bash
./scripts/docker-build-debug.sh
./scripts/docker-build-release.sh
```

Test:

```bash
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
```

Live Tailscale validation requires a tailnet auth key:

```bash
TS_AUTHKEY=... ./scripts/ampere-redroid-tailscale-live-smoke.sh
```

Setup and server requirements are documented in [docs/setup.md](docs/setup.md).
