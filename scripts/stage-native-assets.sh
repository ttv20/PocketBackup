#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
src="$project_dir/native/out/arm64-v8a"
dst="$project_dir/app/src/main/assets/native/arm64-v8a"
jni_dst="$project_dir/app/src/main/jniLibs/arm64-v8a"

executables=(rsync ssh ssh-keygen ssh-keyscan tsnet-nc)

missing=()
for name in "${executables[@]}"; do
  if [ ! -x "$src/$name" ]; then
    missing+=("$name")
  fi
done

if [ "${#missing[@]}" -gt 0 ]; then
  printf 'missing native outputs in %s: %s\n' "$src" "${missing[*]}" >&2
  exit 1
fi

rm -rf "$dst"
mkdir -p "$dst"
cp -a "$src"/. "$dst"/
for name in "${executables[@]}"; do
  chmod 0755 "$dst/$name"
done

rm -rf "$jni_dst"
mkdir -p "$jni_dst"
cp "$src/rsync" "$jni_dst/librsync_exec.so"
cp "$src/ssh" "$jni_dst/libssh_exec.so"
cp "$src/ssh-keygen" "$jni_dst/libssh_keygen_exec.so"
cp "$src/ssh-keyscan" "$jni_dst/libssh_keyscan_exec.so"
cp "$src/tsnet-nc" "$jni_dst/libtsnet_nc_exec.so"
chmod 0755 "$jni_dst"/*.so
