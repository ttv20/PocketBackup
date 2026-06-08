# Third-Party Notices

Pocket Backup includes third-party software. Original Pocket Backup code is MIT, but
these third-party components keep their own upstream licenses and notice
requirements.

This file is a pointer map for source and APK distributors. It is not a
replacement for the upstream license texts.

## Bundled Native Tools

Release APKs package Android arm64 native tools and libraries under:

```text
app/src/main/assets/native/arm64-v8a/
app/src/main/jniLibs/arm64-v8a/
```

The Termux package snapshot for bundled native tools is recorded at:

```text
app/src/main/assets/native/arm64-v8a/termux-packages.txt
```

That manifest records package names, versions, upstream homepages, package
filenames, and SHA-256 hashes for the staged Termux packages. Extracted native
copyright files are stored at:

```text
app/src/main/assets/native/arm64-v8a/termux-docs/
```

Bundled native components include `rsync`, OpenSSH client tools, OpenSSL,
zstd, and their runtime libraries. `rsync` is GPL-covered; the GPLv3 text is
included in [COPYING](COPYING) and packaged with APK native assets as:

```text
app/src/main/assets/native/arm64-v8a/COPYING-GPL-3.0
```

Keep the package manifest, native notices, native build/fetch scripts, and GPL
text with source and binary releases.

## Built-In Tailscale Client Helper

The `tsnet-nc` binary is built from:

```text
native/tsnet-helper/
```

It depends on `tailscale.com` and other Go modules listed in:

```text
native/tsnet-helper/go.mod
native/tsnet-helper/go.sum
```

Tailscale source is BSD-3-Clause licensed. Its Go module dependencies remain
under their own upstream licenses. Preserve those notices when redistributing
the helper binary.

## Android And JVM Dependencies

The Android app also uses Gradle/Maven dependencies declared in:

```text
app/build.gradle.kts
build.gradle.kts
```

Those libraries are not relicensed by Pocket Backup. Keep their upstream license
terms in mind when distributing modified APKs.
