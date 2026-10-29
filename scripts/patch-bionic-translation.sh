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
original = p.read_text()
s = original
s = s.replace('fprintf(stderr, "calling apkenv_reloc_library with packed reloc:',
              'TRACE(/* apklayer patch: relocation tracing */ "calling apkenv_reloc_library with packed reloc:')
if s != original: p.write_text(s)
PY

# 7. Bionic cond_destroy never waits for sleeping threads. Track translated
#    waiters and defer freeing their native object; glibc destroy would block
#    forever in process-exit destructors while Android's worker threads sleep.
python3 - "$bt" <<'PY'
from pathlib import Path
import sys
p = Path(sys.argv[1]) / 'pthread_wrapper/libpthread.c'
s = p.read_text()
if 'struct apklayer_cond' not in s:
    start = s.index('static void default_pthread_cond_init(')
    end = s.index('int bionic_pthread_atfork(', start)
    part = s[start:end]
    part = part.replace('sizeof(*cond->glibc)', 'sizeof(struct apklayer_cond)')
    a = part.index('int bionic_pthread_cond_destroy(')
    b = part.index('int bionic_pthread_cond_init(', a)
    part = part[:a] + '''int bionic_pthread_cond_destroy(bionic_cond_t *cond)
{
\tassert(cond);
\tif (IS_MAPPED(cond)) {
\t\tstruct apklayer_cond *native = (void *)cond->glibc;
\t\tunsigned old = __atomic_fetch_or(&native->state, APKLAYER_COND_DESTROYED, __ATOMIC_ACQ_REL);
\t\tif (old == 0) apklayer_cond_free(native);
\t}
\treturn 0;
}

''' + part[b:]
    for call in ['pthread_cond_wait(cond->glibc, mutex->glibc)',
                 'pthread_cond_clockwait(cond->glibc, mutex->glibc, clock_id, abs_timeout)',
                 'pthread_cond_timedwait(cond->glibc, mutex->glibc, abs_timeout)']:
        part = part.replace('return ' + call + ';',
                            'int ret;\n\tAPKLAYER_COND_WAIT(ret, ' + call + ');\n\treturn ret;')
    # The musl clockwait branch changes its clock field around timedwait.
    part = part.replace('int ret = pthread_cond_timedwait(cond->glibc, mutex->glibc, abs_timeout);',
                        'int ret;\n\tAPKLAYER_COND_WAIT(ret, pthread_cond_timedwait(cond->glibc, mutex->glibc, abs_timeout));')
    helper = r'''/* apklayer patch: Android's non-blocking condition destruction. */
struct apklayer_cond { pthread_cond_t cond; unsigned state; };
#define APKLAYER_COND_DESTROYED (1u << 31)
static void apklayer_cond_free(struct apklayer_cond *native)
{
\tpthread_cond_destroy(&native->cond);
\tmunmap(native, sizeof(*native));
}
static void apklayer_cond_wait_done(void *opaque)
{
\tstruct apklayer_cond *native = opaque;
\tunsigned old = __atomic_fetch_sub(&native->state, 1, __ATOMIC_ACQ_REL);
\tif (old == (APKLAYER_COND_DESTROYED | 1u)) apklayer_cond_free(native);
}
#define APKLAYER_COND_WAIT(result, call) do { \
\tstruct apklayer_cond *tracked = (void *)cond->glibc; \
\t__atomic_fetch_add(&tracked->state, 1, __ATOMIC_ACQ_REL); \
\tpthread_cleanup_push(apklayer_cond_wait_done, tracked); \
\tresult = (call); \
\tpthread_cleanup_pop(1); \
} while (0)

'''.replace('\\t', '\t')
    s = s[:start] + helper + part + s[end:]
    p.write_text(s)
PY

# 8. Android fortifies positional writes too (MTP in libmedia_jni). Reuse the
#    existing checked-write implementation, preserving both bounds checks.
python3 - "$bt" <<'PY'
from pathlib import Path
import sys
p = Path(sys.argv[1]) / 'libc/libc-chk.c'
s = p.read_text()
if 'bionic___pwrite64_chk(' not in s:
    a = s.index('ssize_t bionic___write_chk(')
    b = s.index('\n}\n', a) + 3
    body = s[a:b].replace('bionic___write_chk(int fd, const void* buf, size_t count, size_t buf_size)',
                          'bionic___pwrite64_chk(int fd, const void* buf, size_t count, off64_t offset, size_t buf_size)')
    body = body.replace('"write:', '"pwrite64:').replace('return write(fd, buf, count);', 'return pwrite64(fd, buf, count, offset);')
    s += '\n/* apklayer patch: Android positional-write fortify ABI. */\n' + body
    p.write_text(s)
PY

# 9. Android plugin loaders use absolute /system or /apex paths. Resolve them
#    through the same instance-root overlay as open/stat, before library search.
python3 - "$bt" <<'PY'
from pathlib import Path
import sys
p = Path(sys.argv[1]) / 'linker/linker.c'
s = p.read_text()
original = s
# Preserve nested absolute plugin paths; legacy direct /system/lib/libc.so
# aliases still strip their prefix before the translation override map.
s = s.replace('if(!strncmp(name, prefix, prefix_len))',
              "if(!strncmp(name, prefix, prefix_len) && !strchr(name + prefix_len, '/'))")
s = s.replace('else if(!strncmp(name, prefix64, prefix64_len))',
              "else if(!strncmp(name, prefix64, prefix64_len) && !strchr(name + prefix64_len, '/'))")
marker = '/* apklayer patch: absolute libraries use the instance path overlay. */'
if marker not in s:
    anchor = '\tstrcpy(fullpath, name);\n'
    assert s.count(anchor) == 1
    s = s.replace(anchor, anchor + '''
\t/* apklayer patch: absolute libraries use the instance path overlay. */
\tif (name[0] == '/') {
\t\tbool (*override)(char **) = dlsym(RTLD_DEFAULT, "apklayer_apply_path_overrides");
\t\tchar *mapped = (char *)name;
\t\tif (override && override(&mapped)) {
\t\t\tif (strlen(mapped) >= 512) { free(mapped); errno = ENAMETOOLONG; return -1; }
\t\t\tfd = apkenv__open_lib(mapped);
\t\t\tif (fd >= 0) strcpy(fullpath, mapped);
\t\t\tfree(mapped);
\t\t\treturn fd;
\t\t}
\t}
''', 1)
if s != original: p.write_text(s)
PY

# 10. Audio's real priority-inheritance mutex must translate its attribute
#     object too; falling back to glibc writes over the mapped bionic pointer.
python3 - "$bt" <<'PY'
from pathlib import Path
import sys
p = Path(sys.argv[1]) / 'pthread_wrapper/libpthread.c'
s = p.read_text()
if 'bionic_pthread_mutexattr_setprotocol(' not in s:
    anchor = 'int bionic_pthread_mutexattr_destroy(bionic_mutexattr_t *attr)'
    assert s.count(anchor) == 1
    s = s.replace(anchor, '''/* apklayer patch: preserve the mapped attribute ABI for audio PI mutexes. */
int bionic_pthread_mutexattr_setprotocol(bionic_mutexattr_t *attr, int protocol)
{
    assert(attr && IS_MAPPED(attr));
    return pthread_mutexattr_setprotocol(attr->glibc, protocol);
}

int bionic_pthread_mutexattr_getprotocol(const bionic_mutexattr_t *attr, int *protocol)
{
    assert(attr && IS_MAPPED(attr));
    return pthread_mutexattr_getprotocol(attr->glibc, protocol);
}

''' + anchor, 1)
    p.write_text(s)
PY

# 11. Android's fortified fread argument order differs from glibc. Reuse the
#     existing Android fwrite wrapper with its overflow and buffer-size checks.
python3 - "$bt" <<'PY'
from pathlib import Path
import sys
p = Path(sys.argv[1]) / 'libc/libc-chk.c'
s = p.read_text()
if 'bionic___fread_chk(' not in s:
    a = s.index('size_t bionic___fwrite_chk(')
    b = s.index('\n}\n', a) + 3
    body = s[a:b].replace('bionic___fwrite_chk(const void *__restrict buf',
                          'bionic___fread_chk(void *__restrict buf')
    body = body.replace('fwrite', 'fread').replace('fread read overflow', 'fread write overflow')
    s += '\n/* apklayer patch: Android fread fortify argument order. */\n' + body
    p.write_text(s)
PY

# 12. Android's monotonic waits take an absolute deadline, regardless of the
# condition attribute. Relative waits also use the monotonic clock.
python3 - "$bt" <<'PYCOND'
from pathlib import Path
import sys
p = Path(sys.argv[1]) / 'pthread_wrapper/libpthread.c'
s = p.read_text()
if 'apklayer patch: explicit monotonic deadlines' not in s:
    a = s.index('int bionic_pthread_cond_timedwait_relative_np(')
    b = s.index('int bionic_pthread_cond_timedwait_monotonic(', a)
    s = s[:a] + '/* apklayer patch: explicit monotonic deadlines, matching bionic pthread_cond.cpp. */\nint bionic_pthread_cond_timedwait_relative_np(bionic_cond_t *cond, bionic_mutex_t *mutex, const struct timespec *reltime)\n{\n    assert(cond && mutex && reltime);\n    struct timespec tv;\n    clock_gettime(CLOCK_MONOTONIC, &tv);\n    tv.tv_sec += reltime->tv_sec;\n    tv.tv_nsec += reltime->tv_nsec;\n    if (tv.tv_nsec >= 1000000000) { ++tv.tv_sec; tv.tv_nsec -= 1000000000; }\n    return bionic_pthread_cond_clockwait(cond, mutex, CLOCK_MONOTONIC, &tv);\n}\n\nint bionic_pthread_cond_timedwait_monotonic_np(bionic_cond_t *cond, bionic_mutex_t *mutex, const struct timespec *abstime)\n{\n    assert(cond && mutex && abstime);\n    return bionic_pthread_cond_clockwait(cond, mutex, CLOCK_MONOTONIC, abstime);\n}\n\n' + s[b:]
    p.write_text(s)
PYCOND

# 8. Statically initialised (all-zero) conditions, semaphores, rwlocks and mutexes get their native object on first use, and two threads
#    arriving together each made one and overwrote the other's: one waited on a condition the other signalled never (lost wakeup). libhwui's
#    WorkQueue, a std::condition_variable first used by the main and the render thread at once, hung the whole app at startup (black window,
#    main thread in HardwareRenderer.nSetStopped). The native object is now made under a lock, only if no other thread already did.
python3 - "$bt" <<'PY'
from pathlib import Path
import sys
p = Path(sys.argv[1]) / 'pthread_wrapper/libpthread.c'
s = p.read_text()
if 'apklayer patch: atomic lazy init' not in s:
    helper = '''/* apklayer patch: atomic lazy init. The native object is made under a lock and only if no other thread already did. (The bionic structs keep the
 * pointer 4-byte aligned: a compare-and-swap on it is a bus error on arm64.) */
static pthread_mutex_t apklayer_install_lock = PTHREAD_MUTEX_INITIALIZER;
#define APKLAYER_INSTALL(field, size, init_stmt) do { \\
\tpthread_mutex_lock(&apklayer_install_lock); \\
\tif (!is_mapped((void *)(field), (size))) { \\
\t\ttypeof(field) native = mmap(NULL, (size), PROT_READ | PROT_WRITE, MAP_ANONYMOUS | MAP_PRIVATE, -1, 0); \\
\t\tinit_stmt; \\
\t\t__atomic_thread_fence(__ATOMIC_RELEASE); \\
\t\t(field) = native; \\
\t} \\
\tpthread_mutex_unlock(&apklayer_install_lock); \\
} while (0)

'''
    s = s.replace('// For handling static initialization.', helper + '// For handling static initialization.', 1)
    s = s.replace('''\tsem->glibc = mmap(NULL, sizeof(*sem->glibc), PROT_READ | PROT_WRITE, MAP_ANONYMOUS | MAP_PRIVATE, -1, 0);
\tmemset(sem->glibc, 0, sizeof(*sem->glibc));''', '''\tAPKLAYER_INSTALL(sem->glibc, sizeof(*sem->glibc), memset(native, 0, sizeof(*native)));''', 1)
    s = s.replace('''\trwlock->glibc = mmap(NULL, sizeof(*rwlock->glibc), PROT_READ | PROT_WRITE, MAP_ANONYMOUS | MAP_PRIVATE, -1, 0);
\tmemset(rwlock->glibc, 0, sizeof(*rwlock->glibc));''', '''\tAPKLAYER_INSTALL(rwlock->glibc, sizeof(*rwlock->glibc), memset(native, 0, sizeof(*native)));''', 1)
    s = s.replace('''\tcond->glibc = mmap(NULL, sizeof(struct apklayer_cond), PROT_READ | PROT_WRITE, MAP_ANONYMOUS | MAP_PRIVATE, -1, 0);
\tmemset(cond->glibc, 0, sizeof(struct apklayer_cond));''', '''\tAPKLAYER_INSTALL(cond->glibc, sizeof(struct apklayer_cond), memset((void *)native, 0, sizeof(struct apklayer_cond)));''', 1)
    s = s.replace('''\t\tmutex->glibc = mmap(NULL, sizeof(*mutex->glibc), PROT_READ | PROT_WRITE, MAP_ANONYMOUS | MAP_PRIVATE, -1, 0);
\t\tmemcpy(mutex->glibc, &bionic_mutex_init_map[i].glibc, sizeof(bionic_mutex_init_map[i].glibc));''', '''\t\tAPKLAYER_INSTALL(mutex->glibc, sizeof(*mutex->glibc), memcpy(native, &bionic_mutex_init_map[i].glibc, sizeof(bionic_mutex_init_map[i].glibc)));''', 1)
    p.write_text(s)
PY

# 9. R_AARCH64_IRELATIVE (ifunc) relocations: the linker rejected them ("unknown reloc type 1032"). Chromium's libwebviewchromium.so has hundreds. As in
#    bionic's linker, the resolver at base+addend is called with the hwcap (plus the _IFUNC_ARG_HWCAP bit) and an __ifunc_arg_t, and its result stored.
python3 - "$bt" <<'PY'
from pathlib import Path
import sys
p = Path(sys.argv[1]) / 'linker/linker.c'
s = p.read_text()
if 'apklayer patch: IRELATIVE' not in s:
    s = s.replace('#include <', '#include <sys/auxv.h>\n#include <', 1)
    anchor = '#if defined(__aarch64__)\n\t\tcase R_AARCH64_ABS64:'
    assert anchor in s
    s = s.replace(anchor, '''#if defined(__aarch64__)
\t\tcase R_AARCH64_IRELATIVE: { /* apklayer patch: IRELATIVE (ifunc), see scripts/patch-bionic-translation.sh step 9 */
\t\t\tstruct { unsigned long size, hwcap, hwcap2; } ifunc_arg = { sizeof(ifunc_arg), getauxval(AT_HWCAP), getauxval(AT_HWCAP2) };
\t\t\tElfW(Addr) (*resolver)(unsigned long, void *) = (void *)(si->base + get_addend(rel));
\t\t\tCOUNT_RELOC(RELOC_RELATIVE);
\t\t\tMARK(rel->r_offset);
\t\t\t*((ElfW(Addr) *)reloc) = resolver(ifunc_arg.hwcap | (1UL << 62), &ifunc_arg);
\t\t\tbreak;
\t\t}
\t\tcase R_AARCH64_ABS64:''', 1)
    p.write_text(s)
PY
# 10. pthread_cond_signal wakes every waiter: with one woken waiter an HWUI CommonPool task could be left queued while both workers slept, and the
#     render thread then waited for its future forever (hang in HardwareRenderer.nSetStopped, 1 run in 10 of tests/vm/pendingintents.sh; 0 in 25 after).
#     Spurious wakeups are allowed by POSIX, so waking all is always correct.
python3 - "$bt" <<'PY'
import sys
from pathlib import Path
p = Path(sys.argv[1]) / 'pthread_wrapper/libpthread.c'
s = p.read_text()
old = '\treturn pthread_cond_signal(cond->glibc);'
if old in s:
    s = s.replace(old, '\treturn pthread_cond_broadcast(cond->glibc); /* apklayer patch: see scripts/patch-bionic-translation.sh step 10 */', 1)
    p.write_text(s)
PY
# 11. android_get_device_api_level in libc.so: Go's cgo runtime (WireGuard, Rethink DNS) looks it up with dlsym on libc.so; its presence selects the Android Q path that uses
#     the free TLS_SLOT_APP (slot 2, which bridge/native/bionic_tls.c keeps at tp+16) instead of scanning pthread keys for a slot, which finds nothing here.
python3 - "$bt" <<'PY'
import sys
from pathlib import Path
p = Path(sys.argv[1]) / 'libc/libc-misc.c'
s = p.read_text()
if 'bionic_android_get_device_api_level' not in s:
    s += '\n/* apklayer patch: see scripts/patch-bionic-translation.sh step 11 */\nint bionic_android_get_device_api_level(void)\n{\n\treturn 36;\n}\n'
    p.write_text(s)
PY
# 12. dlsym(RTLD_NEXT, name) falls back to the host's symbol when no later bionic library has it: Gecko's mozglue interposes putenv/setenv and crashes ("interposition failed")
#     when RTLD_NEXT finds nothing, because the real libc is the host's glibc here.
python3 - "$bt" <<'PY'
import sys
from pathlib import Path
p = Path(sys.argv[1]) / 'linker/dlfcn.c'
s = p.read_text()
old = '''		sym = NULL;
		if (si && si->next) {
			sym = apkenv_lookup(symbol, &found, si->next);
		}
'''
new = old + '''		if (!sym) { /* apklayer patch: see scripts/patch-bionic-translation.sh step 12 */
			void *host = dlsym(RTLD_DEFAULT, symbol);
			if (host) {
				pthread_mutex_unlock(&apkenv_dl_lock);
				return wrapper_create(symbol, host);
			}
		}
'''
if 'step 12' not in s and old in s:
    p.write_text(s.replace(old, new, 1))
PY
# 13. A bionic library does not take over SIGSEGV, SIGBUS, SIGILL and SIGFPE: the JVM owns them (implicit null checks, safepoint polls). Gecko's crash handler installed over the JVM's
#     treated every one of them as a crash and exited with status 11 a few seconds after start. The call succeeds and reports the current action; a crash then ends in the JVM's own report.
python3 - "$bt" <<'PY'
import sys
from pathlib import Path
p = Path(sys.argv[1]) / 'libc/libc.c'
s = p.read_text()
old = '''	struct sigaction goact = {0}, gact = {0};
	if (act) {'''
new = '''	struct sigaction goact = {0}, gact = {0};
	if (act && (sig == SIGSEGV || sig == SIGBUS || sig == SIGILL || sig == SIGFPE)) { /* apklayer patch: see scripts/patch-bionic-translation.sh step 13 */
		if (oact) {
			sigaction(sig, NULL, &goact);
			*oact = (struct bionic_sigaction){0};
			oact->bsa_handler = goact.sa_handler;
			oact->sa_flags = goact.sa_flags;
		}
		return 0;
	}
	if (act) {'''
if 'step 13' not in s and old in s:
    p.write_text(s.replace(old, new, 1))
PY
# 14. kill(): a service host (APKLAYER_COMPONENT_HOST, e.g. a GeckoView content process) cannot signal any process but itself. On Android its isolated uid makes that an EPERM; here every process shares
#     one uid, and a Gecko child ended the app by SIGKILL (AppShutdown/IPC teardown) a few seconds after a page load began.
python3 - "$bt" <<'PY'
import sys
from pathlib import Path
p = Path(sys.argv[1]) / 'libc/libc.c'
s = p.read_text()
old = 'int tgkill(int tgid, int tid, int sig)'
new = '''int bionic_kill(pid_t pid, int sig) /* apklayer patch: see scripts/patch-bionic-translation.sh step 14 */
{
	if (pid != getpid() && getenv("APKLAYER_COMPONENT_HOST")) {
		errno = EPERM;
		return -1;
	}
	return kill(pid, sig);
}

''' + old
if 'step 14' not in s and old in s:
    p.write_text(s.replace(old, new, 1))
PY
# 15. pthread_sigmask / sigprocmask: a bionic thread cannot block SIGSEGV, SIGBUS, SIGILL or SIGFPE (the JVM answers those; blocked, the kernel kills the whole process on a null access in a Gecko
#     thread that blocks every signal, with no JVM report). bionic's sigset_t is the kernel's 64-bit mask.
python3 - "$bt" <<'PY'
import sys
from pathlib import Path
p = Path(sys.argv[1]) / 'libc/libc.c'
s = p.read_text()
old = 'int bionic_kill(pid_t pid, int sig)'
new = '''#define APK_SYNC_SIGNALS ((1UL << (SIGSEGV - 1)) | (1UL << (SIGBUS - 1)) | (1UL << (SIGILL - 1)) | (1UL << (SIGFPE - 1)))
static int apk_sigmask(int thread, int how, const unsigned long *set, unsigned long *oset) /* apklayer patch: see scripts/patch-bionic-translation.sh step 15 */
{
	sigset_t in, out;
	unsigned long bits;
	sigemptyset(&in);
	if (set) {
		bits = *set & ~APK_SYNC_SIGNALS;
		memcpy(&in, &bits, sizeof bits);
	}
	int r = thread ? pthread_sigmask(how, set ? &in : NULL, oset ? &out : NULL) : sigprocmask(how, set ? &in : NULL, oset ? &out : NULL);
	if (oset) memcpy(oset, &out, sizeof *oset);
	return r;
}
int bionic_pthread_sigmask(int how, const unsigned long *set, unsigned long *oset) { return apk_sigmask(1, how, set, oset); }
int bionic_sigprocmask(int how, const unsigned long *set, unsigned long *oset) { return apk_sigmask(0, how, set, oset); }

''' + old
if 'step 15' not in s and old in s:
    p.write_text(s.replace(old, new, 1))
PY
# 16. readdir: the entry it returns lives in a buffer per thread, not one for the process, and the entries "." and ".." are not returned. Gecko lists directories on several threads at once, and its
#     recursive scan of the safebrowsing directory (Classifier::ScanStoreDir) followed "./././..." until the stack was gone, so the dot entries do not reach the callers that forget to skip them.
python3 - "$bt" <<'PY'
import sys
from pathlib import Path
p = Path(sys.argv[1]) / 'libc/libc.c'
s = p.read_text()
old = '	static struct bionic_dirent bde;\n	struct dirent *de;\n	if (!(de = readdir(dirp)))\n		return NULL;\n'
new = '	static __thread struct bionic_dirent bde; /* apklayer patch: see scripts/patch-bionic-translation.sh step 16 */\n	struct dirent *de;\n	do {\n		if (!(de = readdir(dirp)))\n			return NULL;\n	} while (!strcmp(de->d_name, ".") || !strcmp(de->d_name, ".."));\n'
if 'step 16' not in s and old in s:
    p.write_text(s.replace(old, new, 1))
PY
# 17. Fortified stdio: __fprintf_chk, __vfprintf_chk, __fgets_chk and __fread_chk got bionic's FILE* (stderr is &__sF[2]) and passed it to glibc, which aborts with "glibc detected an invalid stdio
#     handle". libc++abi's abort_message prints through __vfprintf_chk, so every uncaught C++ exception ended there instead of showing its message (Joplin).
python3 - "$bt" <<'PY'
import sys
from pathlib import Path
d = Path(sys.argv[1]) / 'libc'
chk = d / 'libc-chk.c'
s = chk.read_text()
if 'step 17' not in s:
    if '#include "libc-stdio.h"' not in s:
        s = s.replace('#include <stdio.h>', '#include <stdio.h>\n#include <stdarg.h>\n#include "libc-stdio.h"', 1)
    s = s.replace('return fgets(dest, supplied_size, stream);', 'return fgets(dest, supplied_size, bionic_file_to_glibc_file(stream)); /* apklayer patch: see scripts/patch-bionic-translation.sh step 17 */')
    s += '''
/* apklayer patch: see scripts/patch-bionic-translation.sh step 17 */
int bionic___vfprintf_chk(FILE *stream, int flag, const char *format, va_list ap)
{
	return __vfprintf_chk(bionic_file_to_glibc_file(stream), flag, format, ap);
}

int bionic___fprintf_chk(FILE *stream, int flag, const char *format, ...)
{
	va_list ap;
	va_start(ap, format);
	int r = __vfprintf_chk(bionic_file_to_glibc_file(stream), flag, format, ap);
	va_end(ap);
	return r;
}
'''
    chk.write_text(s)
chk.write_text(chk.read_text().replace('fread(buf, size, count, stream)', 'fread(buf, size, count, bionic_file_to_glibc_file(stream))'))  # idempotent: the replaced text no longer matches
PY

# 18. Regular expressions: bionic's regex_t (32 bytes), regmatch_t (64-bit offsets) and REG_* flag values differ from glibc's, and nothing wrapped regcomp/regexec/regfree, so a bionic caller's
#     regex_t was overrun by glibc's (64 bytes): keymaster's GetOsPatchlevel (regcomp on the stack) crashed the software KeyMint. The wrappers keep the glibc regex_t behind bionic's re_g.
python3 - "$bt" <<'PY'
import sys
from pathlib import Path
p = Path(sys.argv[1]) / 'libc/libc.c'
s = p.read_text()
if 'step 18' not in s:
    s += '''
/* apklayer patch: see scripts/patch-bionic-translation.sh step 18 */
#include <regex.h>
#include <stdint.h>
struct bionic_regex { int re_magic; size_t re_nsub; const char *re_endp; void *re_g; };
struct bionic_regmatch { int64_t rm_so; int64_t rm_eo; };

int bionic_regcomp(struct bionic_regex *preg, const char *pattern, int cflags)
{
	int gf = (cflags & 1 ? REG_EXTENDED : 0) | (cflags & 2 ? REG_ICASE : 0) | (cflags & 4 ? REG_NOSUB : 0) | (cflags & 8 ? REG_NEWLINE : 0);
	regex_t *g = calloc(1, sizeof(*g));
	int r = g ? regcomp(g, pattern, gf) : REG_ESPACE;
	if (r) {
		free(g);
		return r;
	}
	preg->re_magic = 0x62726567;
	preg->re_nsub = g->re_nsub;
	preg->re_endp = NULL;
	preg->re_g = g;
	return 0;
}

int bionic_regexec(const struct bionic_regex *preg, const char *string, size_t nmatch, struct bionic_regmatch pmatch[], int eflags)
{
	regmatch_t gm[32];
	size_t n = nmatch > 32 ? 32 : nmatch;
	int r = regexec(preg->re_g, string, n, gm, eflags & 3);
	if (!r)
		for (size_t i = 0; i < nmatch; i++) {
			pmatch[i].rm_so = i < n ? gm[i].rm_so : -1;
			pmatch[i].rm_eo = i < n ? gm[i].rm_eo : -1;
		}
	return r;
}

void bionic_regfree(struct bionic_regex *preg)
{
	if (preg->re_g) {
		regfree(preg->re_g);
		free(preg->re_g);
		preg->re_g = NULL;
	}
}

size_t bionic_regerror(int errcode, const struct bionic_regex *preg, char *errbuf, size_t errbuf_size)
{
	return regerror(errcode, preg ? preg->re_g : NULL, errbuf, errbuf_size);
}
'''
    p.write_text(s)
PY

# 19. The path layer also serves the calls that remove or move what open created: unlink, unlinkat, rename, mkdir, rmdir. keystore2's sqlite created its journal under /data/misc/keystore (served from the
#     Android root) and then unlinked the plain /data path: SQLITE_IOERR_DELETE_NOENT, the database could not commit.
python3 - "$bt" <<'PY'
import sys
from pathlib import Path
p = Path(sys.argv[1]) / 'libc/libc.c'
s = p.read_text()
if 'step 19' not in s:
    s += '''
/* apklayer patch: see scripts/patch-bionic-translation.sh step 19 */
#include <unistd.h>
#include <sys/stat.h>
#include <stdio.h>
static char *apk_map(const char *path, bool *owned)
{
	char *m = (char *)path;
	*owned = path && path[0] == '/' && libc_bio_apply_path_overrides(&m);
	return m;
}
int bionic_unlink(const char *path) { bool o; char *m = apk_map(path, &o); int r = unlink(m); if (o) free(m); return r; }
int bionic_unlinkat(int dirfd, const char *path, int flags) { bool o; char *m = apk_map(path, &o); int r = unlinkat(dirfd, m, flags); if (o) free(m); return r; }
int bionic_rmdir(const char *path) { bool o; char *m = apk_map(path, &o); int r = rmdir(m); if (o) free(m); return r; }
int bionic_mkdir(const char *path, mode_t mode) { bool o; char *m = apk_map(path, &o); int r = mkdir(m, mode); if (o) free(m); return r; }
int bionic_rename(const char *from, const char *to)
{
	bool of, ot; char *a = apk_map(from, &of), *b = apk_map(to, &ot);
	int r = rename(a, b);
	if (of) free(a);
	if (ot) free(b);
	return r;
}
'''
    p.write_text(s)
PY
