# PocketSync

PocketSync backs up your Android phone to your own server using rsync, SSH, and
a built-in Tailscale client. No root or Android VPN connection is required.

PocketSync is a standalone personal/sideload Android app. It does not depend on
Termux and it does not send backups to a vendor cloud. The first release is
backup-only.

- Package: `com.ttv20.rsyncbackup`
- Min SDK: 29
- Target SDK: 36
- ABI: `arm64-v8a`
- UI: Kotlin and Jetpack Compose
- Native sync: bundled `rsync` and OpenSSH
- Tailscale: bundled `tsnet-nc` helper for Tailscale host access

Private SSH and Tailscale material is stored locally with Android
Keystore-backed encryption where practical. Export/import intentionally omits
private SSH keys, passwords, Tailscale auth keys, and Tailscale state.

## Build

Native assets:

```bash
./scripts/fetch-termux-native-binaries.py
./scripts/build-tsnet-helper.sh
./scripts/stage-native-assets.sh
```

Build APKs:

```bash
./scripts/docker-build-debug.sh
./scripts/docker-build-release.sh
```

Run tests:

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

## License

Original PocketSync code and documentation are released under `0BSD`: use,
copy, modify, publish, sell, or relicense it without attribution. Bundled
third-party components keep their own licenses; see [LICENSE.md](LICENSE.md)
and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Release

The GitHub Actions workflow in `.github/workflows/release-apk.yml` runs on
`v*` tags. It runs unit tests, builds the release APK through the Docker release
script, uploads the APK as a workflow artifact, and publishes it to a GitHub
Release with SHA-256 checksums.

Configure these repository secrets before the first release:

- `POCKETSYNC_RELEASE_KEYSTORE_B64`: base64-encoded release keystore
- `POCKETSYNC_RELEASE_STORE_PASSWORD`: keystore password
- `POCKETSYNC_RELEASE_KEY_ALIAS`: key alias
- `POCKETSYNC_RELEASE_KEY_PASSWORD`: key password

Create a release keystore:

```bash
keytool -genkeypair \
  -v \
  -keystore pocketsync-release.jks \
  -alias pocketsync \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000

base64 -w0 pocketsync-release.jks
```

To publish a release:

```bash
git tag v0.1.0
git push origin v0.1.0
```

The workflow sets `versionName` from the tag without the leading `v`, and sets
`versionCode` from the GitHub Actions run number. A release can also be started
manually from the workflow page with a `v*` tag name.
