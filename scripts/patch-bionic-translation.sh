#!/bin/sh
# patch-bionic-translation: the two apklayer patches to a bionic_translation checkout (docs/PATCHES.md), idempotent.
#  1. SO_MAX 128 -> 512: Android's libandroid_runtime closure is 237 libraries (the soinfo pool was 128)
#  2. remove its three system-property stubs (they print "not handled yet" and return nothing): bionic libraries then
#     resolve __system_property_* to apklayer's real store (bridge/native/properties.c) through the glibc-namespace fallback
set -e
bt="$1"
sed -i 's/#define SO_MAX[[:space:]]*128/#define SO_MAX 512/' "$bt"/linker/linker.h "$bt"/linker/linker.c 2>/dev/null || true
python3 - "$bt" <<'PY'
import re, sys
bt = sys.argv[1]
p = bt + '/libc/libc-misc.c'
s = open(p).read()
a = s.index('// this seems to not be a stable ABI')
b = s.index('// actually misc stuff')
if 'apklayer patch' not in s:
    s = s[:a] + '// apklayer patch: the property functions are provided by apklayer (bridge/native/properties.c)\n\n' + s[b:]
    open(p, 'w').write(s)
p = bt + '/libc/libc.c'
s = open(p).read()
m = re.search(r'int __system_property_get\(const char \*name, char \*value\)\n\{.*?\n\}\n\n', s, re.S)
if m:
    s = s[:m.start()] + '/* apklayer patch: __system_property_get is provided by apklayer (bridge/native/properties.c) */\n\n' + s[m.end():]
    open(p, 'w').write(s)
PY
