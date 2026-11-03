# Sourced by the VM test scripts: APKLY_VM and APKLY_VM_PASS from the environment or tests/vm/local.conf (untracked, see local.conf.example);
# APKLY_VM_CONF=<name> reads tests/vm/<name>.conf instead. vm_auth ssh|scp ARGS...: through sshpass with a password, else key authentication.
conf="$(dirname "${BASH_SOURCE[0]}")/${APKLY_VM_CONF:-local}.conf"
if [ -f "$conf" ]; then
    while IFS='=' read -r key value; do
        case "$key" in APKLY_VM|APKLY_VM_PASS) [ -n "${!key:-}" ] || export "$key=$value" ;; esac
    done < "$conf"
fi
: "${APKLY_VM:?set APKLY_VM (tests/vm/local.conf.example)}"
VM="$APKLY_VM"
PASS="${APKLY_VM_PASS:-}"
vm_auth() { if [ -n "$PASS" ]; then sshpass -p "$PASS" "$@"; else c="$1"; shift; "$c" -o BatchMode=yes "$@"; fi; }
