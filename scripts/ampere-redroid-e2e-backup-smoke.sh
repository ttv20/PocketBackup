#!/usr/bin/env bash
set -euo pipefail

host="${AMPERE_HOST:?AMPERE_HOST is required, for example ubuntu@ampere-host}"
remote_dir="${AMPERE_PROJECT_DIR:-/home/ubuntu/rsync-backup-android}"
container="${REDROID_CONTAINER:-rsync-redroid}"
serial="${ANDROID_SERIAL:-127.0.0.1:5555}"
package="com.ttv20.rsyncbackup"
action="com.ttv20.rsyncbackup.debug.RUN_BACKUP_SMOKE"
expect_status="${SMOKE_EXPECT_STATUS:-SUCCESS}"
source_text="${SMOKE_SOURCE_TEXT:-ampere-redroid-e2e-smoke}"
advanced_args="${SMOKE_ADVANCED_ARGS:-}"
source_file_count="${SMOKE_SOURCE_FILE_COUNT:-0}"
source_file_bytes="${SMOKE_SOURCE_FILE_BYTES:-0}"
auto_cancel_after_ms="${SMOKE_AUTO_CANCEL_AFTER_MS:-0}"
remote_suffix="${SMOKE_REMOTE_SUFFIX:-}"
verify_run_ui="${SMOKE_VERIFY_RUN_UI:-0}"
manual_run_anyway="${SMOKE_MANUAL_RUN_ANYWAY:-0}"

"$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/ampere-redroid-smoke.sh"

ssh "$host" "set -euo pipefail
  expect_status='$expect_status'
  source_text='$source_text'
  advanced_args='$advanced_args'
  source_file_count='$source_file_count'
  source_file_bytes='$source_file_bytes'
  auto_cancel_after_ms='$auto_cancel_after_ms'
  remote_suffix='$remote_suffix'
  verify_run_ui='$verify_run_ui'
  manual_run_anyway='$manual_run_anyway'
  adb connect '$serial' >/dev/null
  smoke_user=\"rsyncsmoke\$(date +%s)\$\$\"
  smoke_user_created=0
  smoke_password=\"RsyncSmoke-\$(date +%s)-\$\$\"
  key_passphrase=\"RsyncKey-\$(date +%s)-\$\$\"
  sshd_port=2222
  sshd_config=/tmp/rsync-backup-smoke-sshd_config
  sshd_pid=/tmp/rsync-backup-smoke-sshd.pid
  sshd_log=/tmp/rsync-backup-smoke-sshd.log
  firewall_rule_added=0
  tmp_dir=\$(mktemp -d)
  cleanup() {
    if [ \"\$firewall_rule_added\" = 1 ]; then
      sudo iptables -D INPUT -i docker0 -p tcp --dport \"\$sshd_port\" -j ACCEPT >/dev/null 2>&1 || true
    fi
    if [ -f \"\$sshd_pid\" ]; then
      sudo kill \"\$(cat \"\$sshd_pid\")\" >/dev/null 2>&1 || true
      sudo rm -f \"\$sshd_pid\"
    fi
    if [ \"\$smoke_user_created\" = 1 ]; then
      sudo userdel -r \"\$smoke_user\" >/dev/null 2>&1 || true
    fi
    rm -rf \"\$tmp_dir\"
  }
  trap cleanup EXIT

  sudo useradd -m -s /bin/bash \"\$smoke_user\"
  smoke_user_created=1
  printf '%s:%s\n' \"\$smoke_user\" \"\$smoke_password\" | sudo chpasswd
  home_dir=\$(getent passwd \"\$smoke_user\" | cut -d: -f6)
  remote_path=\"\$home_dir/android-rsync-smoke-target\$remote_suffix\"

	  ssh-keygen -t ed25519 -N \"\$key_passphrase\" -f \"\$tmp_dir/id_ed25519\" -C android-rsync-smoke >/dev/null
	  sudo rm -rf \"\$remote_path\"
	  sudo install -d -m 700 -o \"\$smoke_user\" -g \"\$smoke_user\" \"\$home_dir/.ssh\"
	  sudo rm -f \"\$home_dir/.ssh/authorized_keys\"

	  if [ -f \"\$sshd_pid\" ]; then
	    sudo kill \"\$(cat \"\$sshd_pid\")\" >/dev/null 2>&1 || true
	    sudo rm -f \"\$sshd_pid\"
	  fi
	  sudo iptables -I INPUT -i docker0 -p tcp --dport \"\$sshd_port\" -j ACCEPT
	  firewall_rule_added=1
	  sudo sh -c \"cat > '\$sshd_config'\" <<EOF
Port \$sshd_port
ListenAddress 0.0.0.0
HostKey /etc/ssh/ssh_host_ed25519_key
HostKey /etc/ssh/ssh_host_rsa_key
PasswordAuthentication yes
KbdInteractiveAuthentication no
ChallengeResponseAuthentication no
PubkeyAuthentication yes
AuthorizedKeysFile .ssh/authorized_keys
PermitRootLogin no
UsePAM yes
PidFile \$sshd_pid
LogLevel ERROR
EOF
	  sudo /usr/sbin/sshd -t -f \"\$sshd_config\"
	  sudo /usr/sbin/sshd -f \"\$sshd_config\" -E \"\$sshd_log\"

	  host_key_scan=\$(ssh-keyscan -T 10 -p \"\$sshd_port\" -t ed25519,rsa 127.0.0.1 2>/dev/null | awk 'NF >= 3 && \$1 !~ /^#/ { print }')
  test -n \"\$host_key_scan\"
  printf '%s\n' \"\$host_key_scan\" > \"\$tmp_dir/known_hosts\"
  host_keys_file=\"\$tmp_dir/host-keys.tsv\"
  : > \"\$host_keys_file\"
  while read -r _host_pattern host_key_algorithm host_key_public_key _rest; do
    [ -n \"\$host_key_algorithm\" ] || continue
    key_line=\"\$tmp_dir/host-key-\$host_key_algorithm\"
    printf '%s %s %s\n' \"\$_host_pattern\" \"\$host_key_algorithm\" \"\$host_key_public_key\" > \"\$key_line\"
    host_key_fingerprint=\$(ssh-keygen -l -E sha256 -f \"\$key_line\" | awk '{ print \$2; exit }')
    printf '%s\t%s\t%s\n' \"\$host_key_algorithm\" \"\$host_key_public_key\" \"\$host_key_fingerprint\" >> \"\$host_keys_file\"
  done <<HOSTKEYS
\$host_key_scan
HOSTKEYS
  host_keys_b64=\$(base64 -w0 \"\$host_keys_file\")
  if [ -n \"\$advanced_args\" ]; then
    advanced_args_payload=\"\$advanced_args\"
  else
    advanced_args_payload=' '
  fi
  advanced_args_b64=\$(printf '%s' \"\$advanced_args_payload\" | base64 -w0)

	  gateway=\$(sudo docker inspect -f '{{range .NetworkSettings.Networks}}{{.Gateway}}{{end}}' '$container')
	  test -n \"\$gateway\"
	  private_key_b64=\$(base64 -w0 \"\$tmp_dir/id_ed25519\")
	  public_key_b64=\$(base64 -w0 \"\$tmp_dir/id_ed25519.pub\")

  if [ \"\$verify_run_ui\" = 1 ]; then
    adb -s '$serial' shell am start -W -n '$package/.MainActivity' --es screen Run >/dev/null
    sleep 2
  fi

  selected_ssid_constraint=false
  if [ \"\$manual_run_anyway\" = 1 ]; then
    selected_ssid_constraint=true
    adb -s '$serial' shell pm grant '$package' android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
  fi

  adb -s '$serial' shell am broadcast \
    -n '$package/.debug.DebugBackupSmokeReceiver' \
	    -a '$action' \
	    --es host \"\$gateway\" \
	    --ei port \"\$sshd_port\" \
	    --es user \"\$smoke_user\" \
	    --es remotePath \"\$remote_path\" \
	    --es privateKeyBase64 \"\$private_key_b64\" \
	    --es privateKeyPassphrase \"\$key_passphrase\" \
	    --es publicKeyBase64 \"\$public_key_b64\" \
	    --es hostKeysBase64 \"\$host_keys_b64\" \
	    --es setupPassword \"\$smoke_password\" \
	    --es sourceText \"\$source_text\" \
	    --es advancedArgsBase64 \"\$advanced_args_b64\" \
	    --ei sourceFileCount \"\$source_file_count\" \
	    --el sourceFileBytes \"\$source_file_bytes\" \
	    --el autoCancelAfterMs \"\$auto_cancel_after_ms\" \
	    --ez selectedSsidConstraint \"\$selected_ssid_constraint\" >/dev/null

  state_file=\"\$tmp_dir/app-state.json\"
  if [ \"\$manual_run_anyway\" = 1 ]; then
    notification_file=\"\$tmp_dir/notification.txt\"
    notification_ready=0
    for _ in \$(seq 1 60); do
      adb -s '$serial' shell dumpsys notification --noredact > \"\$notification_file\" 2>/dev/null || true
      if grep -q 'Backup constraints not met' \"\$notification_file\" &&
         grep -q 'Run anyway' \"\$notification_file\" &&
         grep -Eq 'Current SSID|Selected SSID' \"\$notification_file\"; then
        notification_ready=1
        break
      fi
      sleep 2
    done
    if [ \"\$notification_ready\" != 1 ]; then
      cat \"\$notification_file\"
      exit 1
    fi

    ui_dump=\"\$tmp_dir/notification-window.xml\"
    run_anyway_clicked=0
    adb -s '$serial' shell cmd statusbar expand-notifications >/dev/null 2>&1 || true
    for _ in \$(seq 1 20); do
      adb -s '$serial' shell uiautomator dump /sdcard/rsync-notification.xml >/dev/null 2>&1 || true
      adb -s '$serial' shell cat /sdcard/rsync-notification.xml > \"\$ui_dump\" 2>/dev/null || true
      bounds=\$(grep -io 'text=\"run anyway\"[^>]*bounds=\"\\[[0-9]*,[0-9]*\\]\\[[0-9]*,[0-9]*\\]\"' \"\$ui_dump\" | head -1 | sed -E 's/.*bounds=\"\\[([0-9]*),([0-9]*)\\]\\[([0-9]*),([0-9]*)\\]\".*/\\1 \\2 \\3 \\4/' || true)
      if [ -n \"\$bounds\" ]; then
        set -- \$bounds
        x=\$(((\$1 + \$3) / 2))
        y=\$(((\$2 + \$4) / 2))
        adb -s '$serial' shell input tap \"\$x\" \"\$y\"
        run_anyway_clicked=1
        break
      fi
      sleep 1
    done
    if [ \"\$run_anyway_clicked\" != 1 ]; then
      cat \"\$ui_dump\"
      exit 1
    fi
    adb -s '$serial' shell cmd statusbar collapse >/dev/null 2>&1 || true
  fi

  if [ \"\$verify_run_ui\" = 1 ]; then
    ui_dump=\"\$tmp_dir/window.xml\"
    ui_verified=0
    for _ in \$(seq 1 45); do
      adb -s '$serial' shell uiautomator dump /sdcard/rsync-window.xml >/dev/null
      adb -s '$serial' shell cat /sdcard/rsync-window.xml > \"\$ui_dump\"
      if grep -q 'Backup run screen' \"\$ui_dump\" &&
         grep -q 'Live progress' \"\$ui_dump\" &&
         grep -q 'Running rsync' \"\$ui_dump\" &&
         grep -q 'large-0.bin' \"\$ui_dump\"; then
        ui_verified=1
        break
      fi
      sleep 2
    done
    if [ \"\$ui_verified\" != 1 ]; then
      cat \"\$ui_dump\"
      exit 1
    fi
  fi

  for _ in \$(seq 1 120); do
    adb -s '$serial' shell run-as '$package' cat files/app-state.json > \"\$state_file\" 2>/dev/null || true
    if grep -q '\"profileId\": \"debug-smoke-profile\"' \"\$state_file\" &&
       grep -q \"\\\"lastStatus\\\": \\\"\$expect_status\\\"\" \"\$state_file\"; then
      break
    fi
    if grep -q '\"lastStatus\": \"FAILED\"' \"\$state_file\" ||
       grep -q '\"lastStatus\": \"CANCELLED\"' \"\$state_file\" ||
       grep -q '\"lastStatus\": \"SUCCESS\"' \"\$state_file\"; then
      if ! grep -q \"\\\"lastStatus\\\": \\\"\$expect_status\\\"\" \"\$state_file\"; then
      cat \"\$state_file\"
      exit 1
      fi
    fi
    sleep 2
  done

  adb -s '$serial' shell run-as '$package' cat files/app-state.json > \"\$state_file\"
  grep -q '\"profileId\": \"debug-smoke-profile\"' \"\$state_file\"
  grep -q \"\\\"lastStatus\\\": \\\"\$expect_status\\\"\" \"\$state_file\"

  if [ \"\$expect_status\" = 'CANCELLED' ]; then
    grep -q '\"phase\": \"CANCELLED\"' \"\$state_file\"
    grep -q 'large-0.bin' \"\$state_file\"
    sleep 2
    if adb -s '$serial' shell 'pidof rsync >/dev/null 2>&1 || pidof ssh >/dev/null 2>&1'; then
      adb -s '$serial' shell 'ps -A | grep -E \"rsync|ssh\"' || true
      exit 1
    fi
    echo 'Android-to-Ampere rsync cancellation smoke passed'
    exit 0
  fi

	  sudo test -f \"\$remote_path/hello.txt\"
	  sudo grep -q \"\$source_text\" \"\$remote_path/hello.txt\"
	  sudo grep -q \"\$(awk '{ print \$2 }' \"\$tmp_dir/id_ed25519.pub\")\" \"\$home_dir/.ssh/authorized_keys\"
	  sudo test -f \"\$remote_path/.android-rsync-backup-root\"
  sudo test -f \"\$remote_path/.backup-status.json\"
  sudo test -f \"\$remote_path/.backup-last.log\"
  sudo grep -q 'debug-smoke-profile' \"\$remote_path/.backup-status.json\"
  sudo grep -q '\\\"status\\\": \\\"success\\\"' \"\$remote_path/.backup-status.json\"
  echo 'Android-to-Ampere rsync backup smoke passed'
"
