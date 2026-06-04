#!/usr/bin/env bash
set -euo pipefail

image="${GO_ANDROID_IMAGE:-golang:1.26-bookworm}"
docker_cmd="${DOCKER:-docker}"
project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
out_dir="$project_dir/native/out/arm64-v8a"
go_cache="$project_dir/.go-cache"

mkdir -p "$out_dir"
mkdir -p "$go_cache/pkg" "$go_cache/build"

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
  go build -trimpath -ldflags='-s -w' -o /out/tsnet-nc .

uid="$(id -u)"
gid="$(id -g)"
$docker_cmd run --rm --user root \
  -v "$project_dir":/workspace \
  -w /workspace \
  ubuntu:24.04 \
  sh -c "chown -R $uid:$gid native/out 2>/dev/null || true"
