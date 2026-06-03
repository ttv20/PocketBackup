#!/usr/bin/env bash
set -euo pipefail

host="${AMPERE_HOST:?AMPERE_HOST is required, for example ubuntu@ampere-host}"
remote_dir="${AMPERE_PROJECT_DIR:-/home/ubuntu/rsync-backup-android}"
container="${REDROID_CONTAINER:-rsync-redroid}"
serial="${ANDROID_SERIAL:-127.0.0.1:5555}"
package="com.ttv20.rsyncbackup"
test_package="com.ttv20.rsyncbackup.test"
runner="androidx.test.runner.AndroidJUnitRunner"
source_text="${SMOKE_SOURCE_TEXT:-ampere-redroid-ui-setup-smoke}"
remote_suffix="${SMOKE_REMOTE_SUFFIX:-}"
project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
test_apk="$project_dir/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
source_text_b64="$(printf '%s' "$source_text" | base64 -w0)"

test -f "$test_apk"

"$project_dir/scripts/ampere-redroid-smoke.sh"

ssh "$host" "mkdir -p '$remote_dir/app/build/outputs/apk/androidTest/debug'"
scp "$test_apk" "$host:$remote_dir/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"

ssh "$host" "set -euo pipefail
  source_text=\$(printf '%s' '$source_text_b64' | base64 -d)
  remote_suffix='$remote_suffix'
  adb connect '$serial' >/dev/null
  smoke_user=\"rsyncsmoke\$(date +%s)\$\$\"
  smoke_user_created=0
  smoke_password=\"RsyncSmoke-\$(date +%s)-\$\$\"
  sshd_port=2222
  sshd_config=/tmp/rsync-backup-ui-setup-smoke-sshd_config
  sshd_pid=/tmp/rsync-backup-ui-setup-smoke-sshd.pid
  sshd_log=/tmp/rsync-backup-ui-setup-smoke-sshd.log
  firewall_rule_added=0
  tmp_dir=\$(mktemp -d)
  cleanup() {
    adb -s '$serial' uninstall '$test_package' >/dev/null 2>&1 || true
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
  remote_path=\"\$home_dir/android-rsync-ui-setup-smoke-target\$remote_suffix\"

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

  gateway=\$(sudo docker inspect -f '{{range .NetworkSettings.Networks}}{{.Gateway}}{{end}}' '$container')
  test -n \"\$gateway\"

  adb -s '$serial' uninstall '$test_package' >/dev/null 2>&1 || true
  adb -s '$serial' install -r '$remote_dir/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk' >/dev/null
  adb -s '$serial' shell pm grant '$package' android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
  adb -s '$serial' shell am start -W -n '$package/.MainActivity' >/dev/null
  adb -s '$serial' shell am instrument -w -r \
    -e class 'com.ttv20.rsyncbackup.UserDrivenSetupSmokeTest' \
    -e host \"\$gateway\" \
    -e port \"\$sshd_port\" \
    -e user \"\$smoke_user\" \
    -e password \"\$smoke_password\" \
    -e remotePath \"\$remote_path\" \
    -e sourceText \"\$source_text\" \
    '$test_package/$runner'

  state_file=\"\$tmp_dir/app-state.json\"
  adb -s '$serial' shell run-as '$package' cat files/app-state.json > \"\$state_file\"
  grep -q '\"id\": \"profile-phone\"' \"\$state_file\"
  grep -q '\"lastStatus\": \"SUCCESS\"' \"\$state_file\"
  grep -q '\"targetMode\": \"LAN_ONLY\"' \"\$state_file\"

  sudo test -f \"\$remote_path/hello.txt\"
  sudo grep -q \"\$source_text\" \"\$remote_path/hello.txt\"
  sudo test -f \"\$remote_path/nested/info.txt\"
  sudo grep -q 'configured-through-ui' \"\$remote_path/nested/info.txt\"
  sudo grep -q 'ssh-ed25519' \"\$home_dir/.ssh/authorized_keys\"
  sudo test -f \"\$remote_path/.android-rsync-backup-root\"
  sudo test -f \"\$remote_path/.backup-status.json\"
  sudo test -f \"\$remote_path/.backup-last.log\"
  sudo grep -q 'profile-phone' \"\$remote_path/.backup-status.json\"
  sudo grep -q '\\\"status\\\": \\\"success\\\"' \"\$remote_path/.backup-status.json\"
  echo 'Android UI-driven setup smoke passed'
"
