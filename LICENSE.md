# License

Original PocketSync source code and documentation are released under the MIT
License.

SPDX-License-Identifier: MIT

## MIT License

Copyright (c) 2026 PocketSync contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

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
