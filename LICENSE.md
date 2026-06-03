# License

Original PocketSync source code and documentation are released under the BSD
Zero Clause License (`0BSD`).

SPDX-License-Identifier: 0BSD

No attribution is required for original PocketSync code.

## BSD Zero Clause License

Permission to use, copy, modify, and/or distribute this software for any
purpose with or without fee is hereby granted.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH
REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND
FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT,
INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM
LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR
OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
PERFORMANCE OF THIS SOFTWARE.

## Third-Party Software

This license applies only to original PocketSync code and documentation. It
does not relicense bundled third-party software, generated binaries, Android
libraries, Gradle/Maven dependencies, or Go module dependencies.

PocketSync release APKs bundle native components such as `rsync`, OpenSSH, and
the `tsnet-nc` helper. Those components remain under their upstream licenses.
The full GPLv3 text is kept in [COPYING](COPYING) for GPL-covered components
such as `rsync`, and is also packaged with APK native assets as
`app/src/main/assets/native/arm64-v8a/COPYING-GPL-3.0`.

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for bundled component
locations and notice pointers.
