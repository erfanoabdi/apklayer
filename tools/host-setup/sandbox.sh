#!/bin/sh
# sandbox.sh [apklayer binary...]: one-time host setup (as root) for the per-app sandbox (APKLAYER_SANDBOX=1, cli/sandbox.cpp). Run as root by the user who runs apklayer
# (sudo keeps SUDO_USER). Default binary: bin/apklayer's build of this tree; a packaged install names /usr/bin/apklayer.
#  - uidmap (newuidmap/newgidmap) and a /etc/subuid and /etc/subgid range for the user: the app uids (10000 and up) of each instance's user namespace
#  - an AppArmor profile per binary that allows its user namespaces: Ubuntu 23.10+ (kernel.apparmor_restrict_unprivileged_userns=1) leaves a namespace
#    made by an unprofiled program without capabilities, so no mount view could be built (crun's and bwrap's profiles are the same, flags=(unconfined))
#  - GPU render nodes 0666, systemd's upstream default (Debian and Ubuntu make them 0660 group render with an ACL for the seat's user, which an app uid is not)
set -eu
user=${SUDO_USER:-$(id -un)}
root=$(cd "$(dirname "$0")/../.." && pwd)
[ $# -gt 0 ] || set -- "$root/b/cli/apklayer"
command -v newuidmap >/dev/null || DEBIAN_FRONTEND=noninteractive apt-get install -y uidmap
grep -q "^$user:" /etc/subuid || usermod --add-subuids 100000-165535 "$user"
grep -q "^$user:" /etc/subgid || usermod --add-subgids 100000-165535 "$user"
if [ -d /etc/apparmor.d ] && command -v apparmor_parser >/dev/null; then
    n=0
    for bin in "$@"; do
        bin=$(readlink -f "$bin"); n=$((n + 1))
        f=/etc/apparmor.d/apklayer${n#1}
        printf 'abi <abi/4.0>,\ninclude <tunables/global>\n\n# apklayer makes a user namespace per instance and a mount namespace per app process (cli/sandbox.cpp)\nprofile apklayer%s %s flags=(unconfined) {\n  userns,\n\n  include if exists <local/apklayer>\n}\n' "${n#1}" "$bin" > "$f"
        apparmor_parser -r "$f"
        echo "sandbox-setup: AppArmor profile $f for $bin"
    done
fi
echo 'SUBSYSTEM=="drm", KERNEL=="renderD*", MODE="0666"' > /etc/udev/rules.d/70-apklayer-render.rules
udevadm control --reload && udevadm trigger --subsystem-match=drm --action=change
echo "sandbox-setup: subordinate ids of $user: $(grep "^$user:" /etc/subuid)"
