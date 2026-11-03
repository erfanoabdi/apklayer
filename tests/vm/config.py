"""APKLY_VM, APKLY_VM_PASS, APKLY_VM_ROOT (the repo copy on the VM) and APKLY_VM_ENV (extra VM environment for tests/run), APKLY_SLOTS (tests/run's instance ids, default tests tests2 ...) from the environment or tests/vm/local.conf (untracked, see local.conf.example).
APKLY_VM_CONF=<name> reads tests/vm/<name>.conf instead (a second VM, e.g. parallels.conf). No APKLY_VM_PASS: ssh key authentication."""
import os
from pathlib import Path

KEYS = ('APKLY_VM', 'APKLY_VM_PASS', 'APKLY_VM_ROOT', 'APKLY_VM_ENV', 'APKLY_SLOTS')


def _read():
    values = {}
    conf = Path(__file__).with_name(os.environ.get('APKLY_VM_CONF', 'local') + '.conf')
    if conf.exists():
        for line in conf.read_text().splitlines():
            key, sep, value = line.partition('=')
            if sep and key in KEYS:
                values[key] = value
    for key in KEYS:
        values[key] = os.environ.get(key) or values.get(key, '')
    if not values['APKLY_VM']:
        raise SystemExit('set APKLY_VM (tests/vm/local.conf.example)')
    return values


_values = _read()
VM = _values['APKLY_VM']
PASS = _values['APKLY_VM_PASS']
ROOT = _values['APKLY_VM_ROOT'] or '~/apklayer'
ENV = _values['APKLY_VM_ENV']
SLOTS = _values['APKLY_SLOTS'].split()


def auth(tool, password=None):
    """The command prefix of ssh or scp to the VM: through sshpass with a password, else key authentication that fails instead of prompting."""
    password = PASS if password is None else password
    return (['sshpass', '-p', password, tool] if password else [tool, '-o', 'BatchMode=yes']) + ['-o', 'StrictHostKeyChecking=no']
