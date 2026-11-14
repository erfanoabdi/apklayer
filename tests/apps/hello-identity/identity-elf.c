/* Native module inspection (handoff item 6): dl_iterate_phdr, the loaded modules' ELF headers and sonames, dladdr of libc/JNI/ART entry points,
 * dlopen/dlsym of runtime libraries, and libart.so opened from its maps path and searched for ART symbols, as SandHook's ElfImg and anti-tamper SDKs do. */
#include "identity.h"
#include <dlfcn.h>
#include <elf.h>
#include <errno.h>
#include <fcntl.h>
#include <link.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#if defined(__aarch64__)
#define OWN_MACHINE EM_AARCH64
#else
#define OWN_MACHINE EM_X86_64
#endif

struct modules { struct buf *b; int count, foreign, unreadable, nosoname; char list[24576], other[8192]; };

static int module(struct dl_phdr_info *info, size_t size, void *data)
{
    (void)size;
    struct modules *m = data;
    m->count++;
    uintptr_t header = 0, dynamic = 0;
    for (int i = 0; i < info->dlpi_phnum; i++) {
        const ElfW(Phdr) *p = info->dlpi_phdr + i;
        if (p->p_type == PT_LOAD && p->p_offset == 0 && !header) header = info->dlpi_addr + p->p_vaddr;
        if (p->p_type == PT_DYNAMIC) dynamic = info->dlpi_addr + p->p_vaddr;
    }
    ElfW(Ehdr) eh;
    int machine = -1;
    if (header && !peek(header, &eh, sizeof eh) && !memcmp(eh.e_ident, ELFMAG, SELFMAG)) machine = eh.e_machine;
    else m->unreadable++;
    if (machine >= 0 && machine != OWN_MACHINE) m->foreign++;
    char soname[256] = "";
    if (dynamic) {
        uintptr_t strtab = 0, off = (uintptr_t)-1;
        for (int i = 0; i < 512; i++) {
            ElfW(Dyn) d;
            if (peek(dynamic + i * sizeof d, &d, sizeof d) || d.d_tag == DT_NULL) break;
            if (d.d_tag == DT_STRTAB) strtab = d.d_un.d_ptr;
            if (d.d_tag == DT_SONAME) off = d.d_un.d_val;
        }
        if (strtab && strtab < info->dlpi_addr) strtab += info->dlpi_addr;  /* bionic keeps link-time addresses, glibc relocates them */
        if (strtab && off != (uintptr_t)-1) peek(strtab + off, soname, sizeof soname - 1);
    }
    if (!soname[0]) m->nosoname++;
    char item[512];
    snprintf(item, sizeof item, "%s[%d,%s];", info->dlpi_name && info->dlpi_name[0] ? info->dlpi_name : "(main)", machine, soname);
    strncat(m->list, item, sizeof m->list - strlen(m->list) - 1);
    if (!info->dlpi_name || strncmp(info->dlpi_name, "/system/lib64/", 14)) strncat(m->other, item, sizeof m->other - strlen(m->other) - 1);
    return 0;
}

static void addresses(struct buf *b, JNIEnv *env)
{
    char w[512];
    const char *libc[] = {"open", "malloc", "pthread_create", "dl_iterate_phdr", "__system_property_get", "android_dlopen_ext", "dlopen",
        "JNI_GetCreatedJavaVMs", "JNI_CreateJavaVM", "__libc_start_main", "gnu_get_libc_version", "__cxa_finalize"};
    for (size_t i = 0; i < sizeof libc / sizeof *libc; i++) {
        char key[96];
        snprintf(key, sizeof key, "dladdr.%s", libc[i]);
        put(b, key, "%s", where(dlsym(RTLD_DEFAULT, libc[i]), w, sizeof w));
    }
    put(b, "dladdr.jni.FindClass", "%s", where((void *)(*env)->FindClass, w, sizeof w));
    put(b, "dladdr.jni.CallStaticObjectMethodV", "%s", where((void *)(*env)->CallStaticObjectMethodV, w, sizeof w));
    put(b, "dladdr.jni.RegisterNatives", "%s", where((void *)(*env)->RegisterNatives, w, sizeof w));
    put(b, "dladdr.jni.functionsTable", "%s", where((void *)*env, w, sizeof w));
    JavaVM *vm = NULL;
    (*env)->GetJavaVM(env, &vm);
    put(b, "dladdr.jni.GetEnv", "%s", where(vm ? (void *)(*vm)->GetEnv : NULL, w, sizeof w));
    /* every function of both tables (JNI 1.6: up to GetObjectRefType) lives in libart.so, as ART's (a check: expect all of them) */
    void **jni = (void **)*env, **invoke = (void **)*vm;
    int in_art = 0, total = 0;
    for (int i = 4; i < 233; i++, total++) {
        Dl_info info;
        in_art += dladdr(jni[i], &info) && info.dli_fname && strstr(info.dli_fname, "/com.android.art/lib64/libart.so");
    }
    for (int i = 3; i < 8; i++, total++) {
        Dl_info info;
        in_art += dladdr(invoke[i], &info) && info.dli_fname && strstr(info.dli_fname, "/com.android.art/lib64/libart.so");
    }
    put(b, "dladdr.jni.inLibart", "%d/%d", in_art, total);
    put(b, "dladdr.self", "%s", where((void *)addresses, w, sizeof w));
}

static void libraries(struct buf *b)
{
    const char *names[] = {"libart.so", "/apex/com.android.art/lib64/libart.so", "libnativehelper.so", "libandroid_runtime.so", "libc.so",
        "libdl.so", "libjavacore.so", "libopenjdk.so", "libandroid.so", "libbinder.so", "libjvm.so", "libjava.so", "libc.so.6", "libnativebridge.so"};
    const char *syms[] = {"JNI_GetCreatedJavaVMs", "_ZN3art7Runtime9instance_E", "art_quick_to_interpreter_bridge", "art_quick_generic_jni_trampoline",
        "_ZN3art9ArtMethod6InvokeEPNS_6ThreadEPjjPNS_6JValueEPKc", "JVM_GetCallerClass", "gnu_get_libc_version"};
    for (size_t i = 0; i < sizeof names / sizeof *names; i++) {
        char key[128], found[1024] = "";
        void *h = dlopen(names[i], RTLD_NOW | RTLD_NOLOAD);
        const char *mode = "loaded";
        if (!h) { h = dlopen(names[i], RTLD_NOW); mode = "opened"; }
        snprintf(key, sizeof key, "dlopen.%s", names[i]);
        if (!h) {
            const char *e = dlerror();
            char err[400];
            snprintf(err, sizeof err, "%s", e ? e : "?");
            char *app = strstr(err, "/data/app/");
            if (app) { char *end = strchr(app, '"'); memmove(app + 15, end ? end : app + strlen(app), strlen(end ? end : "") + 1); memcpy(app, "/data/app/<APP>", 15); }  /* keep the rest of the message */
            put(b, key, "FAIL %s", err);
            continue;
        }
        for (size_t j = 0; j < sizeof syms / sizeof *syms; j++)
            if (dlsym(h, syms[j])) { strncat(found, syms[j], sizeof found - strlen(found) - 2); strcat(found, " "); }
        put(b, key, "%s syms=%s", mode, found);
        if (!strcmp(mode, "opened")) dlclose(h);
    }
}

/* libart.so (or another module) located through maps, opened as a file, its dynsym/symtab searched, and the code at one symbol compared
 * with memory, the way ElfImg-style resolvers find unexported ART symbols. */
static void elf_file(struct buf *b, const char *needle, const char *tag, const char *const *wanted, size_t nwanted)
{
    char *maps = slurp("/proc/self/maps", NULL), path[512] = "", key[96];
    uintptr_t base = 0;
    if (maps) for (char *line = strtok(maps, "\n"); line; line = strtok(NULL, "\n")) {
        char *p = strstr(line, needle);
        unsigned long start, offset;
        if (!p || sscanf(line, "%lx-%*lx %*s %lx", &start, &offset) != 2) continue;
        char *slash = strchr(line, '/');
        if (!slash) continue;
        if (!path[0]) { snprintf(path, sizeof path, "%s", slash); if (offset == 0) base = start; }
        if (offset == 0 && !base) base = start;
    }
    free(maps);
    snprintf(key, sizeof key, "file.%s.path", tag);
    put(b, key, "%s base=%s", path[0] ? path : "not in maps", base ? "found" : "none");
    if (!path[0]) return;
    size_t size;
    char *image = slurp(path, &size);
    snprintf(key, sizeof key, "file.%s.read", tag);
    if (!image) { put(b, key, "ERR %s", strerror(errno)); return; }
    ElfW(Ehdr) *eh = (ElfW(Ehdr) *)image;
    if (size < sizeof *eh || memcmp(eh->e_ident, ELFMAG, SELFMAG)) { put(b, key, "not ELF (%zu bytes)", size); free(image); return; }
    put(b, key, "machine=%d class=%d size=%zu", eh->e_machine, eh->e_ident[EI_CLASS], size);
    if (eh->e_ident[EI_CLASS] != ELFCLASS64 || eh->e_shoff + (size_t)eh->e_shnum * sizeof(ElfW(Shdr)) > size) { free(image); return; }
    ElfW(Shdr) *sh = (ElfW(Shdr) *)(image + eh->e_shoff);
    const char *shstr = image + sh[eh->e_shstrndx].sh_offset;
    char sections[2048] = "", found[2048] = "";
    int verified = 0, compared = 0;
    for (int i = 0; i < eh->e_shnum; i++) {
        const char *name = shstr + sh[i].sh_name;
        if (!strcmp(name, ".symtab") || !strcmp(name, ".dynsym") || !strcmp(name, ".gnu_debugdata") || !strcmp(name, ".note.gnu.build-id")
                || !strcmp(name, ".gnu_debuglink") || !strcmp(name, ".note.android.ident")) {
            strncat(sections, name, sizeof sections - strlen(sections) - 2); strcat(sections, " ");
        }
        if (sh[i].sh_type != SHT_DYNSYM && sh[i].sh_type != SHT_SYMTAB) continue;
        ElfW(Sym) *sym = (ElfW(Sym) *)(image + sh[i].sh_offset);
        const char *str = image + sh[sh[i].sh_link].sh_offset;
        size_t n = sh[i].sh_size / sizeof *sym;
        for (size_t s = 0; s < n; s++)
            for (size_t w = 0; w < nwanted; w++) {
                if (strcmp(str + sym[s].st_name, wanted[w]) || !sym[s].st_value) continue;
                if (!strstr(found, wanted[w])) {
                    strncat(found, wanted[w], sizeof found - strlen(found) - 6);
                    strcat(found, sh[i].sh_type == SHT_DYNSYM ? "(d) " : "(s) ");
                }
                /* the bytes in memory at base + value equal the file's (a PT_LOAD of offset == vaddr - its own delta) */
                if (base && ELF64_ST_TYPE(sym[s].st_info) == STT_FUNC) {
                    ElfW(Phdr) *ph = (ElfW(Phdr) *)(image + eh->e_phoff);
                    for (int p = 0; p < eh->e_phnum; p++)
                        if (ph[p].p_type == PT_LOAD && sym[s].st_value >= ph[p].p_vaddr && sym[s].st_value < ph[p].p_vaddr + ph[p].p_filesz) {
                            size_t off = sym[s].st_value - ph[p].p_vaddr + ph[p].p_offset;
                            unsigned char mem[16];
                            uintptr_t bias = base - (ph[0].p_type == PT_LOAD ? ph[0].p_vaddr : 0);
                            compared++;
                            if (off + 16 <= size && !peek(bias + sym[s].st_value, mem, 16) && !memcmp(mem, image + off, 16)) verified++;
                            break;
                        }
                }
            }
    }
    snprintf(key, sizeof key, "file.%s.sections", tag);
    put(b, key, "%s", sections);
    snprintf(key, sizeof key, "file.%s.symbols", tag);
    put(b, key, "%s", found);
    snprintf(key, sizeof key, "file.%s.codeMatchesMemory", tag);
    put(b, key, "%d/%d", verified, compared);
    free(image);
}

JNIEXPORT jstring JNICALL Java_hello_identity_Main_elf(JNIEnv *env, jclass cls)
{
    (void)cls;
    struct buf b = {0};
    struct modules m = {.b = &b};
    dl_iterate_phdr(module, &m);
    put(&b, "phdr.count", "%d", m.count);
    put(&b, "phdr.foreignMachine", "%d", m.foreign);
    put(&b, "phdr.unreadableHeader", "%d", m.unreadable);
    put(&b, "phdr.noSoname", "%d", m.nosoname);
    put(&b, "phdr.modules", "%s", m.list);
    put(&b, "phdr.modules.other", "%s", m.other);  /* what is not under /system/lib64: the list above is cut off */
    addresses(&b, env);
    libraries(&b);
    static const char *const art[] = {"JNI_GetCreatedJavaVMs", "JNI_CreateJavaVM", "_ZN3art7Runtime9instance_E", "art_quick_to_interpreter_bridge",
        "art_quick_generic_jni_trampoline", "_ZN3art9ArtMethod6InvokeEPNS_6ThreadEPjjPNS_6JValueEPKc", "_ZN3art6Thread14CurrentFromGdbEv",
        "_ZN3art11ClassLinker22FixupStaticTrampolinesEPNS_6ThreadENS_6ObjPtrINS_6mirror5ClassEEE", "_ZN3art2gc4Heap12CollectGarbageEbNS0_7GcCauseE",
        "_ZN3art3jit3Jit11CompileMethodEPNS_9ArtMethodEPNS_6ThreadENS_15CompilationKindEb", "JVM_GetCallerClass", "JNI_OnLoad"};
    elf_file(&b, "libart.so", "libart", art, sizeof art / sizeof *art);
    static const char *const libc[] = {"open", "malloc", "__system_property_get", "android_set_abort_message", "gnu_get_libc_version"};
    elf_file(&b, "/libc.so", "libc", libc, sizeof libc / sizeof *libc);
    static const char *const core[] = {"JNI_OnLoad", "Java_libcore_io_Linux_open"};
    elf_file(&b, "libjavacore.so", "libjavacore", core, sizeof core / sizeof *core);
    return finish(env, &b);
}
