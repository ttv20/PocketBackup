#!/usr/bin/env bash
set -euo pipefail

host="${AMPERE_HOST:?AMPERE_HOST is required, for example ubuntu@ampere-host}"
project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
remote_dir="${AMPERE_PROJECT_DIR:-/home/ubuntu/rsync-backup-android}"
container="${REDROID_CONTAINER:-rsync-redroid}"
image="${REDROID_IMAGE:-redroid/redroid:13.0.0-latest}"
serial="${ANDROID_SERIAL:-127.0.0.1:5555}"
redroid_width="${REDROID_WIDTH:-1440}"
redroid_height="${REDROID_HEIGHT:-3120}"
redroid_dpi="${REDROID_DPI:-560}"

ssh "$host" "mkdir -p '$remote_dir/app/build/outputs/apk/debug'"
rsync -az --delete \
  --exclude='.git/' \
  --exclude='.gradle/' \
  --exclude='.gradle-cache/' \
  --exclude='.android/' \
  --exclude='.android-sdk-cache/' \
  --exclude='.go-cache/' \
  --exclude='.termux-package-cache/' \
  --exclude='app/build/' \
  --exclude='build/' \
  --exclude='native/out/' \
  "$project_dir/" "$host:$remote_dir/"
scp "$project_dir/app/build/outputs/apk/debug/app-debug.apk" \
  "$host:$remote_dir/app/build/outputs/apk/debug/app-debug.apk"

ssh "$host" "set -euo pipefail
  sudo modprobe binder_linux devices=binder,hwbinder,vndbinder 2>/dev/null || true
  sudo mkdir -p /dev/binderfs
  if ! mount | grep -q '/dev/binderfs'; then
    sudo mount -t binder binder /dev/binderfs
  fi
  sudo ln -sf /dev/binderfs/binder /dev/binder
  sudo ln -sf /dev/binderfs/hwbinder /dev/hwbinder
  sudo ln -sf /dev/binderfs/vndbinder /dev/vndbinder
  if ! command -v adb >/dev/null 2>&1; then
    sudo apt-get install -y adb
  fi
  if ! sudo docker ps --format '{{.Names}}' | grep -qx '$container'; then
    sudo docker rm -f '$container' >/dev/null 2>&1 || true
    sudo mkdir -p /home/ubuntu/redroid-rsync-data
    sudo docker run -d --name '$container' --privileged \
      -v /home/ubuntu/redroid-rsync-data:/data \
      -p 127.0.0.1:5555:5555 \
      '$image' \
      androidboot.redroid_width='$redroid_width' \
      androidboot.redroid_height='$redroid_height' \
      androidboot.redroid_dpi='$redroid_dpi' >/dev/null
  fi
  adb connect '$serial' >/dev/null
  for i in \$(seq 1 90); do
    boot=\$(adb -s '$serial' shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)
    [ \"\$boot\" = 1 ] && break
    sleep 2
  done
  test \"\$(adb -s '$serial' shell getprop sys.boot_completed | tr -d '\r')\" = 1
  test \"\$(adb -s '$serial' shell getprop ro.product.cpu.abi | tr -d '\r')\" = arm64-v8a
  cd '$remote_dir'
  adb -s '$serial' uninstall com.ttv20.rsyncbackup >/dev/null 2>&1 || true
  adb -s '$serial' install -r app/build/outputs/apk/debug/app-debug.apk
  adb -s '$serial' shell am start -W -n com.ttv20.rsyncbackup/.MainActivity >/dev/null
  sleep 3
  adb -s '$serial' shell pidof com.ttv20.rsyncbackup >/dev/null
  ANDROID_SERIAL='$serial' ./scripts/docker-native-smoke.sh
"
