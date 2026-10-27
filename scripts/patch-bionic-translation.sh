#!/bin/sh
# patch-bionic-translation: apklayer compatibility patches (docs/PATCHES.md), idempotent.
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
if 'apklayer patch' not in s:
    a = s.index('// this seems to not be a stable ABI')
    b = s.index('// actually misc stuff')
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
s = open(p).read()
if '#include <stdbool.h>' not in s:
    open(p, 'w').write('#include <stdbool.h>\n#include <stdlib.h>\n' + s)
PY
#  4. also open via __open_2 (fortified open, libc-chk.c), openat/__openat_2, access, opendir and readlink go through the path layer
python3 - "$bt" <<'PY'
import sys
bt = sys.argv[1]
DECL = 'bool libc_bio_apply_path_overrides(char **path); /* apklayer patch: path layer */\n'
def edit(path, fn):
    s = open(path).read()
    t = fn(s)
    if t != s: open(path, 'w').write(t)
def mapped(call, path_var, ret_type, ret_stmt):
    return ('\tchar *mapped = (char *)%s; bool free_it = libc_bio_apply_path_overrides(&mapped);\n'
            '\t%s ret = %s;\n\tif (free_it) free(mapped);\n\treturn ret;') % (path_var, ret_type, call)
def chk(s):
    if 'apklayer patch: path layer' in s: return s
    s = '#include <stdbool.h>\n#include <stdlib.h>\n' + DECL + s
    return s.replace('\treturn open(pathname, flags, 0);', mapped('open(mapped, flags, 0)', 'pathname', 'int', ''), 1)
edit(bt + '/libc/libc-chk.c', chk)
def verbose(s):
    if 'apklayer patch: path layer' in s: return s
    s = DECL + s
    s = s.replace('\treturn access(path, amode);', mapped('access(mapped, amode)', 'path', 'int', ''), 1)
    s = s.replace('\treturn opendir(path);', mapped('opendir(mapped)', 'path', 'DIR *', ''), 1)
    s = s.replace('\treturn readlink(path, buf, bufsize);', mapped('readlink(mapped, buf, bufsize)', 'path', 'ssize_t', ''), 1)
    return s
edit(bt + '/libc/libc-verbose.h', verbose)
def overrides(s):
    if 'bionic_openat' in s: return s
    return s + """
/* apklayer patch: openat with an absolute path goes through the path layer */
int bionic_openat(int dirfd, const char *path, int oflag, ...)
{
\tint mode = 0;
\tif (OPEN_NEEDS_MODE(oflag)) { va_list arg; va_start(arg, oflag); mode = va_arg(arg, int); va_end(arg); }
\tchar *mapped = (char *)path;
\tbool free_it = path && path[0] == '/' && (*apply_path_overrides_func)(&mapped);
\tint fd = openat(dirfd, mapped, oflag, mode);
\tif (free_it) free(mapped);
\treturn fd;
}

int bionic___openat_2(int dirfd, const char *path, int oflag)
{
\treturn bionic_openat(dirfd, path, oflag | O_LARGEFILE, 0);
}
"""
edit(bt + '/libc/libc-open-overrides.c', overrides)
PY

# 5. Timed locking must unwrap the same mutex as normal locking. Falling back
#    to glibc passes it a bionic object and overwrites its stored native pointer.
python3 - "$bt" <<'PY'
import sys
from pathlib import Path
p = Path(sys.argv[1]) / 'pthread_wrapper/libpthread.c'
s = p.read_text()
if 'bionic_pthread_mutex_timedlock(' not in s:
    anchor = 'int bionic_pthread_mutex_unlock(bionic_mutex_t *mutex)'
    assert s.count(anchor) == 1
    s = s.replace(anchor, '''/* apklayer patch: Android Mutex::timedLock and libc++ timed_mutex. */
int bionic_pthread_mutex_timedlock(bionic_mutex_t *mutex, const struct timespec *abstime)
{
\tassert(mutex);
\tINIT_IF_NOT_MAPPED(mutex, default_pthread_mutex_init);
\treturn pthread_mutex_timedlock(mutex->glibc, abstime);
}

''' + anchor)
    p.write_text(s)
PY

# 6. Packed relocations use the linker's existing trace facility, like other
#    relocation diagnostics; unconditional output produces megabytes per launch.
python3 - "$bt" <<'PY'
import sys
from pathlib import Path
p = Path(sys.argv[1]) / 'linker/linker.c'
s = p.read_text()
s = s.replace('fprintf(stderr, "calling apkenv_reloc_library with packed reloc:',
              'TRACE(/* apklayer patch: relocation tracing */ "calling apkenv_reloc_library with packed reloc:')
p.write_text(s)
PY
