# Agent Notes

## Build Debug APK

Use the Docker-backed build script so the build does not depend on a host JDK or Android SDK:

```bash
./scripts/docker-build-debug.sh
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

To install the rebuilt APK on a connected device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

If an ADB device is available after building the app, install the rebuilt APK
and relaunch the app:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop com.ttv20.rsyncbackup
adb shell monkey -p com.ttv20.rsyncbackup -c android.intent.category.LAUNCHER 1
```

## F-Droid Release Checks

Full F-Droid/server-style builds are slow because the native Termux payload is
rebuilt from source. Use the debug build above for normal feature work, and run
the F-Droid path only before release or metadata changes.

Before submitting a release to F-Droid, make sure
`metadata/com.ttv20.rsyncbackup.yml` points at a pushed commit or tag that
contains the current F-Droid source-build changes.

Build the local F-Droid buildserver image with:

```bash
docker build -t pocketbackup-fdroid-buildserver:latest docker/fdroid-buildserver
```

The Docker image is for local `fdroid build --on-server` proof builds. The
literal official `fdroid build --server` path still requires F-Droid's Vagrant
build server setup.
