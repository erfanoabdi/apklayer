#!/bin/sh
# apklayer patch: libbinder without kernel security contexts. keystore2 (and any service created with set_requesting_sid) asks the kernel for the caller's
# SELinux context (FLAT_BINDER_FLAG_TXN_SECURITY_CTX); this desktop kernel has no SELinux, so it failed every transaction to such a node ("failed to get security
# context", EINVAL). The flag is not set, and getCallingSid() answers the one fixed context of bridge/ndk/libselinux.c (whose access checks allow everything).
# Usage: scripts/patch-libbinder.sh <LineageOS tree>   (idempotent; run before `m libbinder`)
set -eu
python3 - "$1" <<'PY'
from pathlib import Path
import sys
d = Path(sys.argv[1]) / 'frameworks/native/libs/binder'
p = d / 'Parcel.cpp'
s = p.read_text()
old = '''            if (local->isRequestingSid()) {
                obj.flags |= FLAT_BINDER_FLAG_TXN_SECURITY_CTX;
            }
'''
if 'apklayer patch' not in s and old in s:
    p.write_text(s.replace(old, '''            // apklayer patch: no kernel security context on this host (scripts/patch-libbinder.sh)
            (void)local->isRequestingSid();
''', 1))
p = d / 'IPCThreadState.cpp'
s = p.read_text()
old = '''    checkContextIsBinderForUse(__func__);
    return mCallingSid;
'''
if 'apklayer patch' not in s and old in s:
    p.write_text(s.replace(old, '''    checkContextIsBinderForUse(__func__);
    return mCallingSid ? mCallingSid : "u:r:apklayer:s0";  // apklayer patch: the kernel supplies no context here (scripts/patch-libbinder.sh)
''', 1))
PY
