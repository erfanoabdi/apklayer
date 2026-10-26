#!/bin/sh
# patch-bionic-translation: the two apklayer patches to a bionic_translation checkout (docs/PATCHES.md), idempotent.
#  1. SO_MAX 128 -> 512: Android's libandroid_runtime closure is 237 libraries (the soinfo pool was 128)
#  2. remove its three system-property stubs (they print "not handled yet" and return nothing): bionic libraries then
#     resolve __system_property_* to apklayer's real store (bridge/native/properties.c) through the glibc-namespace fallback
#  3. the path hook (libc_bio_set_apply_path_overrides_func, already there for open/fopen) also serves stat, lstat and fstatat, through one
#     exported function, so apklayer's path layer (bridge/native/paths.c) covers the file checks the Android libraries make
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
python3 - "$bt" <<'PY'
import re, sys
bt = sys.argv[1]
p = bt + '/libc/libc-open-overrides.c'
s = open(p).read()
if 'libc_bio_apply_path_overrides' not in s:
    s = s.replace('/* open and fopen overrides */', '/* apklayer patch: the same hook for the stat family (libc.c) */\nbool libc_bio_apply_path_overrides(char **path)\n{\n\treturn (*apply_path_overrides_func)(path);\n}\n\n/* open and fopen overrides */', 1)
    open(p, 'w').write(s)
p = bt + '/libc/libc.c'
s = open(p).read()
if 'apklayer patch: path layer' not in s:
    s = s.replace('#define PROP_NAME_MAX  32', 'bool libc_bio_apply_path_overrides(char **path); /* apklayer patch: path layer */\n#define PROP_NAME_MAX  32', 1) if '#define PROP_NAME_MAX  32' in s else 'bool libc_bio_apply_path_overrides(char **path); /* apklayer patch: path layer */\n' + s
    # stat (non-aarch64 struct variant and the plain one), lstat, fstatat: call through a mapped copy of the path
    s = s.replace('\tint ret = stat(path, &native_stat);', '\tchar *mapped = (char *)path; bool free_it = libc_bio_apply_path_overrides(&mapped);\n\tint ret = stat(mapped, &native_stat);\n\tif (free_it) free(mapped);')
    s = s.replace('\treturn stat(path, buf);', '\tchar *mapped = (char *)path; bool free_it = libc_bio_apply_path_overrides(&mapped);\n\tint ret = stat(mapped, buf);\n\tif (free_it) free(mapped);\n\treturn ret;')
    s = s.replace('\treturn lstat(path, buf);', '\tchar *mapped = (char *)path; bool free_it = libc_bio_apply_path_overrides(&mapped);\n\tint ret = lstat(mapped, buf);\n\tif (free_it) free(mapped);\n\treturn ret;')
    s = s.replace('\treturn fstatat(dirfd, pathname, buf, flags);', '\tchar *mapped = (char *)pathname; bool free_it = pathname && pathname[0] == \'/\' && libc_bio_apply_path_overrides(&mapped);\n\tint ret = fstatat(dirfd, mapped, buf, flags);\n\tif (free_it) free(mapped);\n\treturn ret;')
    open(p, 'w').write(s)
PY

