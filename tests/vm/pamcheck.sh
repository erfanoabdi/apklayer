#!/bin/bash
# apklayer-pamcheck (the lock screen's password check, HubDesktopPassword): any PAM stack answers through its conversation. Temporary services in
# /etc/pam.d (sudo -n; removed on exit): pam_permit accepts, pam_deny refuses, pam_exec gets the typed password from the conversation (expose_authtok).
# On the VM, from the repo copy (tests/run pamcheck): the helper is the build's (inst/).
set -u
helper=$(find "$PWD/inst" -name apklayer-pamcheck -type f | head -1)
[ -x "$helper" ] || { echo "FAIL no apklayer-pamcheck in inst/"; exit 1; }
tag=apklayer-pamtest-$$
dir=$(mktemp -d "${TMPDIR:-/tmp}/apklayer-pamcheck.XXXXXX")
trap 'sudo -n rm -f /etc/pam.d/$tag-permit /etc/pam.d/$tag-deny /etc/pam.d/$tag-exec; rm -rf "$dir"' EXIT
printf '#!/bin/sh\nread pw; [ "$pw" = "right horse" ]\n' > "$dir/check.sh"; chmod 755 "$dir/check.sh"
mk() { printf "$2" | sudo -n tee /etc/pam.d/$tag-$1 >/dev/null; }
mk permit 'auth required pam_permit.so\naccount required pam_permit.so\n'
mk deny 'auth required pam_deny.so\naccount required pam_permit.so\n'
mk exec "auth required pam_exec.so quiet expose_authtok $dir/check.sh\naccount required pam_permit.so\n"
user=$(id -un)
# expect NAME EXIT SERVICE PASSWORD
expect() { printf '%s\0' "$4" | "$helper" "$user" "$3" >/dev/null 2>&1; got=$?; [ $got = "$2" ] || { echo "FAIL $1: exit $got, wanted $2"; exit 1; }; echo "ok $1"; }
expect "pam_permit accepts" 0 $tag-permit anything
expect "pam_deny refuses" 1 $tag-deny anything
expect "the conversation carries the password (right)" 0 $tag-exec "right horse"
expect "the conversation carries the password (wrong)" 1 $tag-exec "wrong horse"
expect "an empty password is refused" 1 $tag-permit ""
expect "an unknown service falls to 'other' (deny)" 1 $tag-missing x
# the default service (login: pam_unix and whatever the distribution added) for a throwaway user with a known password, the helper running as that user
account=aplpam$$
trap 'sudo -n userdel $account >/dev/null 2>&1; sudo -n rm -f /etc/pam.d/$tag-permit /etc/pam.d/$tag-deny /etc/pam.d/$tag-exec; rm -rf "$dir"' EXIT
sudo -n useradd -M -s /usr/sbin/nologin $account && echo "$account:pw-$$" | sudo -n chpasswd || { echo "FAIL no throwaway user"; exit 1; }
cp "$helper" "$dir/pamcheck"; chmod 755 "$dir"  # the throwaway user must reach it (mktemp makes the directory 0700)
login() { printf '%s\0' "$2" | sudo -n -u $account "$dir/pamcheck" $account >/dev/null 2>&1; got=$?; [ $got = "$1" ] || { echo "FAIL login service: exit $got, wanted $1"; exit 1; }; }
login 0 "pw-$$"; echo "ok login service accepts the right password"
login 1 "pw-wrong"; echo "ok login service refuses a wrong one"
echo "PASS pamcheck"
