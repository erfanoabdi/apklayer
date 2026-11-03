"""APKLY_VM, APKLY_VM_PASS, APKLY_VM_ROOT (the repo copy on the VM) and APKLY_VM_ENV (extra VM environment for tests/run) from the environment or tests/vm/local.conf (untracked, see local.conf.example)."""
import os
from pathlib import Path

KEYS = ('APKLY_VM', 'APKLY_VM_PASS', 'APKLY_VM_ROOT', 'APKLY_VM_ENV')


def _read():
    values = {}
    conf = Path(__file__).with_name('local.conf')
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
