#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
keystore="$project_dir/.android/debug.keystore"

if [ -f "$keystore" ]; then
  exit 0
fi

mkdir -p "$(dirname "$keystore")"
keytool -genkeypair \
  -storetype PKCS12 \
  -keystore "$keystore" \
  -storepass android \
  -keypass android \
  -alias androiddebugkey \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -dname "CN=Android Debug,O=Android,C=US" >/dev/null
