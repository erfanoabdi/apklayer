#!/bin/sh
# sandbox.sh [apklayer binary...]: one-time host setup (as root) for the per-app sandbox (APKLAYER_SANDBOX=1, cli/sandbox.cpp) of a source install
# (the deb does the same in its postinst). Run as root by the user who runs apklayer (sudo keeps SUDO_USER). Default binary: bin/apklayer's build
# of this tree; a packaged install names /usr/bin/apklayer.
#  - uidmap (newuidmap/newgidmap) and a /etc/subuid and /etc/subgid range for the user: the app uids (10000 and up) of each instance's user namespace
#  - an AppArmor profile per binary that allows its user namespaces (apklayer.apparmor)
#  - GPU render nodes 0666 (70-apklayer-render.rules)
set -eu
user=${SUDO_USER:-$(id -un)}
here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../.." && pwd)
[ $# -gt 0 ] || set -- "$root/b/cli/apklayer"
command -v newuidmap >/dev/null || DEBIAN_FRONTEND=noninteractive apt-get install -y uidmap
grep -q "^$user:" /etc/subuid || usermod --add-subuids 100000-165535 "$user"
grep -q "^$user:" /etc/subgid || usermod --add-subgids 100000-165535 "$user"
if [ -d /etc/apparmor.d ] && command -v apparmor_parser >/dev/null; then
    n=0
    for bin in "$@"; do
        bin=$(readlink -f "$bin"); n=$((n + 1))
        f=/etc/apparmor.d/apklayer${n#1}
        sed "s#@NAME@#${n#1}#; s#@BIN@#$bin#" "$here/apklayer.apparmor" > "$f"
        apparmor_parser -r "$f"
        echo "sandbox-setup: AppArmor profile $f for $bin"
    done
fi
cp "$here/70-apklayer-render.rules" /etc/udev/rules.d/70-apklayer-render.rules
udevadm control --reload && udevadm trigger --subsystem-match=drm --action=change
echo "sandbox-setup: subordinate ids of $user: $(grep "^$user:" /etc/subuid)"
