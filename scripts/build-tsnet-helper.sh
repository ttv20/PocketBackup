#!/usr/bin/env bash
set -euo pipefail

image="${GO_ANDROID_IMAGE:-golang:1.26-bookworm}"
docker_cmd="${DOCKER:-docker}"
project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
out_dir="$project_dir/native/out/arm64-v8a"
go_cache="$project_dir/.go-cache"
build_mode="${TSNET_HELPER_BUILD_MODE:-docker}"

mkdir -p "$out_dir"
mkdir -p "$go_cache/pkg" "$go_cache/build"

case "$build_mode" in
  docker)
    $docker_cmd run --rm \
      -e CGO_ENABLED=0 \
      -e GOOS=android \
      -e GOARCH=arm64 \
      -v "$go_cache/pkg":/go/pkg/mod \
      -v "$go_cache/build":/root/.cache/go-build \
      -v "$project_dir/native/tsnet-helper":/src \
      -v "$out_dir":/out \
      -w /src \
      "$image" \
      go build -trimpath -buildvcs=false -ldflags='-s -w' -o /out/tsnet-nc .
    ;;
  host)
    (
      cd "$project_dir/native/tsnet-helper"
      export CGO_ENABLED=0
      export GOOS=android
      export GOARCH=arm64
      export GOMODCACHE="${GOMODCACHE:-$go_cache/pkg}"
      export GOCACHE="${GOCACHE:-$go_cache/build}"
      go build -trimpath -buildvcs=false -ldflags='-s -w' -o "$out_dir/tsnet-nc" .
    )
    ;;
  *)
    printf 'Unsupported TSNET_HELPER_BUILD_MODE: %s\n' "$build_mode" >&2
    exit 2
    ;;
esac

uid="$(id -u)"
gid="$(id -g)"
if [ "$build_mode" = "docker" ]; then
  $docker_cmd run --rm --user root \
    -v "$project_dir":/workspace \
    -w /workspace \
    ubuntu:24.04 \
    sh -c "chown -R $uid:$gid native/out 2>/dev/null || true"
fi
