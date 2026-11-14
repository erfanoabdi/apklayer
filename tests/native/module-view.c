#define _GNU_SOURCE
#include <assert.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <link.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>
#include <unwind.h>

struct module { ElfW(Addr) base; struct dl_phdr_info info; int found; };
static int find_module(struct dl_phdr_info *info, size_t size, void *data)
{
    struct module *module = data;
    assert(size >= __builtin_offsetof(struct dl_phdr_info, dlpi_phnum) + sizeof info->dlpi_phnum);
    if (info->dlpi_addr == module->base) { module->info = *info; module->found++; }
    return 0;
}
static void copy_library(const char *source, const char *target)
{
    int in = open(source, O_RDONLY), out = open(target, O_CREAT | O_EXCL | O_WRONLY, 0600);
    assert(in >= 0 && out >= 0);
    char buffer[8192]; ssize_t n;
    while ((n = read(in, buffer, sizeof buffer)) > 0)
        for (ssize_t at = 0; at < n;) { ssize_t count = write(out, buffer + at, n - at); assert(count > 0); at += count; }
    assert(n == 0); assert(!close(in) && !close(out));
}
struct trace { const struct dl_phdr_info *info; int found; };
static _Unwind_Reason_Code trace_frame(struct _Unwind_Context *context, void *data)
{
    struct trace *trace = data;
    uintptr_t ip = _Unwind_GetIP(context);
    for (int i = 0; i < trace->info->dlpi_phnum; i++) {
        const ElfW(Phdr) *p = trace->info->dlpi_phdr + i;
        uintptr_t start = trace->info->dlpi_addr + p->p_vaddr;
        if (p->p_type == PT_LOAD && ip >= start && ip < start + p->p_memsz) trace->found++;
    }
    return _URC_NO_REASON;
}
static void unwind_module(void *data) { _Unwind_Backtrace(trace_frame, data); }
int main(int argc, char **argv)
{
    assert(argc == 3);
    void *dl = dlopen(argv[1], RTLD_NOW | RTLD_GLOBAL); assert(dl);
    int (*iterate)(int (*)(struct dl_phdr_info *, size_t, void *), void *) = dlsym(dl, "bionic_dl_iterate_phdr");
    int (*address)(const void *, Dl_info *) = dlsym(dl, "bionic_dladdr");
    assert(iterate && address);
    char root[] = "/tmp/apklayer-module-XXXXXX", path[512], names[3][512]; assert(mkdtemp(root));
    snprintf(path, sizeof path, "%s/lib", root); assert(!mkdir(path, 0700));
    snprintf(path, sizeof path, "%s/lib/server", root); assert(!mkdir(path, 0700));
    snprintf(names[0], sizeof names[0], "%s/lib/server/libjvm.so", root);
    snprintf(names[1], sizeof names[1], "%s/lib/libjava.so", root);
    /* The original fixture is outside the synthetic JDK root. */
    assert(strlen(argv[2]) < sizeof names[2]); strcpy(names[2], argv[2]);
    int filtered = getenv("APKLAYER_OWN_DATA") && !getenv("APKLAYER_PROC_VIEW");
    const char *art = sizeof(void *) == 8 ? "/apex/com.android.art/lib64/libopenjdkjvm.so" : "/apex/com.android.art/lib/libopenjdkjvm.so";
    void *handles[3];
    for (int i = 0; i < 3; i++) {
        if (i < 2) copy_library(argv[2], names[i]);
        handles[i] = dlopen(names[i], RTLD_NOW | RTLD_LOCAL); assert(handles[i]);
    }
    for (int i = 0; i < 3; i++) {
        int (*call)(void (*)(void *), void *) = dlsym(handles[i], "module_view_call"); assert(call);
        Dl_info host, guest; assert(dladdr((void *)call, &host) && address((void *)call, &guest));
        const char *expected = filtered && i < 2 ? (i ? "" : art) : host.dli_fname;
        assert(!strcmp(guest.dli_fname, expected));
        assert(guest.dli_fbase == host.dli_fbase && guest.dli_saddr == host.dli_saddr);
        assert(host.dli_sname && guest.dli_sname && !strcmp(host.dli_sname, guest.dli_sname));
        struct module a = {.base = (ElfW(Addr))host.dli_fbase}, b = {.base = (ElfW(Addr))host.dli_fbase};
        assert(!dl_iterate_phdr(find_module, &a) && !iterate(find_module, &b));
        assert(a.found == 1 && b.found == 1 && a.info.dlpi_phnum == b.info.dlpi_phnum);
        assert(!strcmp(b.info.dlpi_name, expected) && !strcmp(a.info.dlpi_name, host.dli_fname));
        assert(!memcmp(host.dli_fbase, ELFMAG, SELFMAG));
        int loads = 0, unwind = 0, dynamic = 0;
        for (int j = 0; j < a.info.dlpi_phnum; j++) {
            const ElfW(Phdr) *p = a.info.dlpi_phdr + j, *q = b.info.dlpi_phdr + j;
            if (p->p_type == PT_DYNAMIC) { assert(q->p_type == PT_NULL); dynamic++; }
            else assert(!memcmp(p, q, sizeof *p));
            loads += p->p_type == PT_LOAD;
            unwind += p->p_type == PT_GNU_EH_FRAME;
        }
        assert(loads && unwind && dynamic);
        struct trace trace = { .info = &b.info };
        assert(call(unwind_module, &trace) == 17 && trace.found > 0);
        /* Re-query the host after guest enumeration: guest projection must never mutate loader-owned names. */
        assert(dladdr((void *)call, &host) && !strcmp(host.dli_fname, names[i]));
    }
    for (int i = 0; i < 3; i++) { assert(!dlclose(handles[i])); if (i < 2) assert(!unlink(names[i])); }
    snprintf(path, sizeof path, "%s/lib/server", root); assert(!rmdir(path));
    snprintf(path, sizeof path, "%s/lib", root); assert(!rmdir(path)); assert(!rmdir(root));
    assert(!dlclose(dl));
    puts("PASS module names: dladdr/phdr agree, real ELF loads/unwind preserved, host names untouched");
}
