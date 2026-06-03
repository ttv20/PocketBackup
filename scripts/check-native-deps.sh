#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
native_dir="$project_dir/native/out/arm64-v8a"
lib_dir="$native_dir/lib"
system_libs='^(libc|libdl|liblog|libm|libandroid)\.so($|\.|$)'
missing=0

for binary in rsync ssh tsnet-nc; do
  path="$native_dir/$binary"
  if [ ! -x "$path" ]; then
    echo "missing executable: $path" >&2
    missing=1
    continue
  fi
  while read -r lib; do
    [ -n "$lib" ] || continue
    if printf '%s\n' "$lib" | grep -Eq "$system_libs"; then
      continue
    fi
    if [ ! -e "$lib_dir/$lib" ]; then
      echo "$binary needs missing library: $lib" >&2
      missing=1
    fi
  done < <(readelf -d "$path" 2>/dev/null | sed -n 's/.*Shared library: \\[\\(.*\\)\\].*/\\1/p')
done

exit "$missing"
