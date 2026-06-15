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


<!-- headroom:rtk-instructions -->
# RTK (Rust Token Killer) - Token-Optimized Commands

When running shell commands, **always prefix with `rtk`**. This reduces context
usage by 60-90% with zero behavior change. If rtk has no filter for a command,
it passes through unchanged — so it is always safe to use.

## Key Commands
```bash
# Git (59-80% savings)
rtk git status          rtk git diff            rtk git log

# Files & Search (60-75% savings)
rtk ls <path>           rtk read <file>         rtk grep <pattern>
rtk find <pattern>      rtk diff <file>

# Test (90-99% savings) — shows failures only
rtk pytest tests/       rtk cargo test          rtk test <cmd>

# Build & Lint (80-90% savings) — shows errors only
rtk tsc                 rtk lint                rtk cargo build
rtk prettier --check    rtk mypy                rtk ruff check

# Analysis (70-90% savings)
rtk err <cmd>           rtk log <file>          rtk json <file>
rtk summary <cmd>       rtk deps                rtk env

# GitHub (26-87% savings)
rtk gh pr view <n>      rtk gh run list         rtk gh issue list

# Infrastructure (85% savings)
rtk docker ps           rtk kubectl get         rtk docker logs <c>

# Package managers (70-90% savings)
rtk pip list            rtk pnpm install        rtk npm run <script>
```

## Rules
- In command chains, prefix each segment: `rtk git add . && rtk git commit -m "msg"`
- For debugging, use raw command without rtk prefix
- `rtk proxy <cmd>` runs command without filtering but tracks usage
<!-- /headroom:rtk-instructions -->
