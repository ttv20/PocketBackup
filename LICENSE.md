# License

SPDX-License-Identifier: GPL-3.0-or-later

This project is intended to be distributed under the GNU General Public License
version 3 or later so APK distribution remains compatible with the bundled
`rsync` binary.

The full GPLv3 text is included in [COPYING](COPYING).

Native component package metadata is staged with the APK assets under:

```text
app/src/main/assets/native/arm64-v8a/termux-packages.txt
app/src/main/assets/native/arm64-v8a/termux-docs/
```

Release source archives should include this source tree, native build/fetch
scripts, the staged package manifest, staged native notices, and
[COPYING](COPYING).
