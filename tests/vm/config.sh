# Sourced by the VM test scripts: APKLY_VM and APKLY_VM_PASS from the environment or tests/vm/local.conf (untracked, see local.conf.example).
conf="$(dirname "${BASH_SOURCE[0]}")/local.conf"
if [ -f "$conf" ]; then
    while IFS='=' read -r key value; do
        case "$key" in APKLY_VM|APKLY_VM_PASS) [ -n "${!key:-}" ] || export "$key=$value" ;; esac
    done < "$conf"
fi
: "${APKLY_VM:?set APKLY_VM (tests/vm/local.conf.example)}"
VM="$APKLY_VM"
PASS="${APKLY_VM_PASS:-}"
