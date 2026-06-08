# Native Components

Release APKs need arm64-v8a executables:

- `rsync`
- `ssh`
- `tsnet-nc`

Expected local output directory:

```text
native/out/arm64-v8a/
```

The app packages binaries from:

```text
app/src/main/assets/native/arm64-v8a/
```

Build the Go helper:

```bash
./scripts/build-tsnet-helper.sh
```

Fetch `rsync`, `ssh`, and their Android arm64 shared libraries from the Termux
package repository:

```bash
./scripts/fetch-termux-native-binaries.py
```

After all three executables exist in `native/out/arm64-v8a/`, stage them into
the APK assets:

```bash
./scripts/stage-native-assets.sh
```

Original Pocket Backup code is MIT, but bundled native components keep their
own upstream licenses. `rsync` is GPL-covered; release source and notices must
preserve the GPL and other third-party license obligations.
