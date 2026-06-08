#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
lock_file="$project_dir/native/fdroid-native-sources.lock"
work_dir="${FDROID_NATIVE_WORK_DIR:-$project_dir/.fdroid-native}"
repo_dir="$work_dir/termux-packages"
termux_output_dir="$work_dir/termux-output"
termux_repo_output_dir="$repo_dir/output"
termux_root_dir="$work_dir/termux-root"
asset_root="$project_dir/native/fdroid-out/assets"
out_dir="$asset_root/native/arm64-v8a"
jni_out_dir="$project_dir/native/fdroid-out/jniLibs/arm64-v8a"
docker_cmd="${DOCKER:-docker}"
termux_prefix="data/data/com.termux/files/usr"
termux_build_mode="${FDROID_NATIVE_TERMUX_MODE:-docker}"

usage() {
  printf 'usage: %s [--termux-mode docker|host]\n' "$0" >&2
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --termux-mode)
      if [ -z "${2:-}" ]; then
        usage
        exit 2
      fi
      termux_build_mode="$2"
      shift 2
      ;;
    --termux-mode=*)
      termux_build_mode="${1#*=}"
      shift
      ;;
    *)
      usage
      exit 2
      ;;
  esac
done

case "$termux_build_mode" in
  docker|host) ;;
  *)
    printf 'Unsupported F-Droid native Termux build mode: %s\n' "$termux_build_mode" >&2
    exit 2
    ;;
esac

read_lock_value() {
  local key="$1"
  awk -v key="$key" '$1 == key { for (i = 2; i <= NF; i++) printf "%s%s", $i, (i == NF ? ORS : OFS) }' "$lock_file"
}

termux_repo_line="$(read_lock_value termux-packages)"
termux_repo_url="$(printf '%s\n' "$termux_repo_line" | awk '{ print $2 }')"
termux_commit="$(printf '%s\n' "$termux_repo_line" | awk '{ print $3 }')"
builder_image="${FDROID_TERMUX_BUILDER_IMAGE:-$(read_lock_value termux-builder-image)}"
termux_arch="$(read_lock_value termux-arch)"
tsnet_ref="$(read_lock_value tsnet-helper)"
mapfile -t root_packages < <(awk '$1 == "termux-package" { print $2 }' "$lock_file")

if [ -z "$termux_repo_url" ] || [ -z "$termux_commit" ] || [ -z "$termux_arch" ]; then
  printf 'Invalid native source lock: %s\n' "$lock_file" >&2
  exit 1
fi

mkdir -p "$work_dir"
if [ "${FDROID_NATIVE_SKIP_TERMUX_BUILD:-0}" != "1" ]; then
  if [ ! -d "$repo_dir/.git" ]; then
    git clone "$termux_repo_url" "$repo_dir"
  fi
  git -C "$repo_dir" fetch --tags --force origin "$termux_commit"
  git -C "$repo_dir" checkout --detach "$termux_commit"
fi

rm -rf "$termux_root_dir" "$project_dir/native/fdroid-out"
if [ "${FDROID_NATIVE_SKIP_TERMUX_BUILD:-0}" != "1" ]; then
  rm -rf "$termux_output_dir"
fi
mkdir -p "$termux_output_dir" "$termux_root_dir" "$out_dir/lib" "$jni_out_dir"

if [ "${FDROID_NATIVE_SKIP_TERMUX_BUILD:-0}" != "1" ]; then
  case "$termux_build_mode" in
    docker)
      "$docker_cmd" run --rm \
        --user root \
        --privileged \
        --device /dev/fuse \
        -v "$repo_dir":/home/builder/termux-packages \
        -v "$termux_output_dir":/home/builder/termux-output \
        -w /home/builder/termux-packages \
        "$builder_image" \
        bash -lc "chown -R builder:builder /home/builder/termux-packages /home/builder/termux-output && runuser -u builder -- bash -lc './build-package.sh -a \"$termux_arch\" -o /home/builder/termux-output ${root_packages[*]} && { cp -a output/*.deb /home/builder/termux-output/ 2>/dev/null || true; }'"
      ;;
    host)
      if [ -n "${ANDROID_NDK_HOME:-}" ] && [ -z "${NDK:-}" ]; then
        export NDK="$ANDROID_NDK_HOME"
      elif [ -n "${ANDROID_NDK_ROOT:-}" ] && [ -z "${NDK:-}" ]; then
        export NDK="$ANDROID_NDK_ROOT"
      elif [ -n "${ANDROID_NDK:-}" ] && [ -z "${NDK:-}" ]; then
        export NDK="$ANDROID_NDK"
      fi
      if [ -n "${ANDROID_SDK_ROOT:-}" ] && [ -z "${ANDROID_HOME:-}" ]; then
        export ANDROID_HOME="$ANDROID_SDK_ROOT"
      fi
      if [ -z "${NDK:-}" ]; then
        (
          cd "$repo_dir"
          ./scripts/setup-android-sdk.sh
        )
      fi
      (
        cd "$repo_dir"
        ./build-package.sh -a "$termux_arch" -o "$termux_output_dir" "${root_packages[@]}"
        cp -a output/*.deb "$termux_output_dir"/ 2>/dev/null || true
      )
      ;;
  esac
fi

extract_deb() {
  local deb="$1"
  local data_member
  data_member="$(ar t "$deb" | awk '/^data[.]tar[.]/ { print; exit }')"
  if [ -z "$data_member" ]; then
    printf 'No data archive in %s\n' "$deb" >&2
    exit 1
  fi
  case "$data_member" in
    *.xz) ar p "$deb" "$data_member" | tar -xJ -C "$termux_root_dir" ;;
    *.gz) ar p "$deb" "$data_member" | tar -xz -C "$termux_root_dir" ;;
    *.zst) ar p "$deb" "$data_member" | tar --zstd -x -C "$termux_root_dir" ;;
    *) ar p "$deb" "$data_member" | tar -x -C "$termux_root_dir" ;;
  esac
}

while IFS= read -r deb; do
  extract_deb "$deb"
done < <(
  find "$termux_output_dir" "$termux_repo_output_dir" -type f -name '*.deb' 2>/dev/null | sort -u
)

prefix="$termux_root_dir/$termux_prefix"
copy_if_exists() {
  local src="$1"
  local dst="$2"
  if [ -e "$src" ]; then
    mkdir -p "$(dirname "$dst")"
    cp -a "$src" "$dst"
  fi
}

copy_file_dereferenced() {
  local src="$1"
  local dst="$2"
  if [ -e "$src" ]; then
    mkdir -p "$(dirname "$dst")"
    cp -L "$src" "$dst"
  fi
}

copy_tree_files() {
  local src="$1"
  local dst="$2"
  [ -d "$src" ] || return 0
  while IFS= read -r file; do
    copy_if_exists "$file" "$dst/${file#$src/}"
  done < <(find "$src" -type f | sort)
}

for executable in rsync ssh ssh-keygen ssh-keyscan scp sftp; do
  copy_if_exists "$prefix/bin/$executable" "$out_dir/$executable"
  if [ -e "$out_dir/$executable" ]; then
    chmod 0755 "$out_dir/$executable"
  fi
done

copy_tree_files "$prefix/etc/ssh" "$out_dir/etc/ssh"

while IFS= read -r lib; do
  rel="${lib#$prefix/lib/}"
  copy_file_dereferenced "$lib" "$out_dir/lib/$rel"
done < <(
  find "$prefix/lib" \( -type f -o -type l \) \( -name '*.so' -o -name '*.so.*' \) | sort
)

if [ -d "$prefix/share/doc" ]; then
  while IFS= read -r doc_file; do
    rel="${doc_file#$prefix/share/doc/}"
    copy_if_exists "$doc_file" "$out_dir/termux-docs/$rel"
  done < <(find "$prefix/share/doc" -type f -name copyright | sort)
fi

if [ "$termux_build_mode" = "host" ]; then
  TSNET_HELPER_BUILD_MODE=host "$project_dir/scripts/build-tsnet-helper.sh"
else
  "$project_dir/scripts/build-tsnet-helper.sh"
fi
copy_if_exists "$project_dir/native/out/arm64-v8a/tsnet-nc" "$out_dir/tsnet-nc"
chmod 0755 "$out_dir/tsnet-nc"

copy_if_exists "$out_dir/rsync" "$jni_out_dir/librsync_exec.so"
copy_if_exists "$out_dir/ssh" "$jni_out_dir/libssh_exec.so"
copy_if_exists "$out_dir/ssh-keygen" "$jni_out_dir/libssh_keygen_exec.so"
copy_if_exists "$out_dir/ssh-keyscan" "$jni_out_dir/libssh_keyscan_exec.so"
copy_if_exists "$out_dir/scp" "$jni_out_dir/libscp_exec.so"
copy_if_exists "$out_dir/sftp" "$jni_out_dir/libsftp_exec.so"
copy_if_exists "$out_dir/tsnet-nc" "$jni_out_dir/libtsnet_nc_exec.so"

while IFS= read -r lib; do
  copy_file_dereferenced "$lib" "$jni_out_dir/$(basename "$lib")"
done < <(
  find "$out_dir/lib" -maxdepth 1 -type f \( -name '*.so' -o -name '*.so.*' \) | sort
)

find "$jni_out_dir" -maxdepth 1 -type f -exec chmod 0755 {} +

missing=()
for executable in rsync ssh ssh-keygen ssh-keyscan scp sftp tsnet-nc; do
  if [ ! -x "$out_dir/$executable" ]; then
    missing+=("$executable")
  fi
done
if [ "${#missing[@]}" -gt 0 ]; then
  printf 'Missing generated F-Droid native executable(s): %s\n' "${missing[*]}" >&2
  exit 1
fi
for native_library in librsync_exec.so libssh_exec.so libssh_keygen_exec.so libssh_keyscan_exec.so libscp_exec.so libsftp_exec.so libtsnet_nc_exec.so; do
  if [ ! -x "$jni_out_dir/$native_library" ]; then
    missing+=("$native_library")
  fi
done
if [ "${#missing[@]}" -gt 0 ]; then
  printf 'Missing generated F-Droid native executable library copy/copies: %s\n' "${missing[*]}" >&2
  exit 1
fi

if command -v readelf >/dev/null 2>&1; then
  dependency_missing=()
  while IFS= read -r elf; do
    while IFS= read -r needed; do
      case "$needed" in
        libc.so|libdl.so|libm.so|liblog.so|libandroid.so)
          continue
          ;;
      esac
      if [ ! -e "$out_dir/lib/$needed" ] && [ ! -e "$jni_out_dir/$needed" ]; then
        dependency_missing+=("$(basename "$elf") -> $needed")
      fi
    done < <(readelf -d "$elf" 2>/dev/null | awk -F'[][]' '/NEEDED/ { print $2 }')
  done < <(find "$jni_out_dir" "$out_dir/lib" -type f -name 'lib*.so*' | sort)

  if [ "${#dependency_missing[@]}" -gt 0 ]; then
    printf 'Missing generated F-Droid native shared librar%s:\n' "$([ "${#dependency_missing[@]}" -eq 1 ] && printf y || printf ies)" >&2
    printf '  %s\n' "${dependency_missing[@]}" >&2
    exit 1
  fi
fi

{
  printf 'Termux package source build for Pocket Backup F-Droid native assets\n'
  printf 'termux-packages: %s %s\n' "$termux_repo_url" "$termux_commit"
  printf 'termux-builder-image: %s\n' "$builder_image"
  printf 'termux-arch: %s\n\n' "$termux_arch"
  for package in "${root_packages[@]}"; do
    printf 'root-package: %s\n' "$package"
  done
  printf '\ntsnet-helper: %s\n' "$tsnet_ref"
} > "$out_dir/fdroid-native-source-refs.txt"

{
  cat "$out_dir/fdroid-native-source-refs.txt"
  printf '\n'
  (
    cd "$out_dir"
    find . -type f ! -name fdroid-native-sha256.txt -print0 |
      sort -z |
      xargs -0 sha256sum |
      sed 's#  [.]\/#  #'
  )
} > "$out_dir/fdroid-native-sha256.txt"

printf 'Generated F-Droid native assets in %s\n' "$asset_root"
