/* PLT hooking as bytehook does it (FCL's exit hook), through bionic_translation's linker (0054): dl_iterate_phdr reports a library the
 * shim linker loaded with its load bias and phdrs, and libdl.so as a real ELF whose dlopen reaches __loader_dlopen through a GOT slot
 * (bytehook's dlopen monitor hooks it); getauxval(AT_BASE) is the linker's ELF header. GOT slots are found from .dynamic as a hook
 * library finds them: its pointers unrelocated, plus the load bias. */
#define _GNU_SOURCE
#include <assert.h>
#include <dlfcn.h>
#include <elf.h>
#include <link.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/auxv.h>
#include <sys/mman.h>
#include <unistd.h>

int plt_hook_target(void) { return 1; }  /* the library's import, resolved from this program */
static int hooked(void) { return 2; }

static void *(*loader_dlopen)(const char *, int, const void *);
static int dlopens;
static void *count_dlopen(const char *f, int flags, const void *caller) { dlopens++; return loader_dlopen(f, flags, caller); }

struct find { const char *suffix; struct dl_phdr_info info; int found; };
static int find_cb(struct dl_phdr_info *info, size_t size, void *data)
{
    struct find *f = data;
    size_t n = info->dlpi_name ? strlen(info->dlpi_name) : 0, k = strlen(f->suffix);
    (void)size;
    if (n >= k && !strcmp(info->dlpi_name + n - k, f->suffix)) f->info = *info, f->found++;
    return 0;
}

static void **got_slot(const struct dl_phdr_info *info, const char *name)
{
    const ElfW(Dyn) *dyn = NULL;
    const ElfW(Sym) *symtab = NULL;
    const char *strtab = NULL;
    const ElfW(Rela) *rel[2] = {0};
    size_t relsz[2] = {0};
    for (int i = 0; i < info->dlpi_phnum; i++)
        if (info->dlpi_phdr[i].p_type == PT_DYNAMIC) dyn = (const void *)(info->dlpi_addr + info->dlpi_phdr[i].p_vaddr);
    assert(dyn);
    for (; dyn->d_tag != DT_NULL; dyn++) {
        const void *p = (const void *)(info->dlpi_addr + dyn->d_un.d_ptr);
        switch (dyn->d_tag) {
        case DT_SYMTAB: symtab = p; break;
        case DT_STRTAB: strtab = p; break;
        case DT_JMPREL: rel[0] = p; break;
        case DT_PLTRELSZ: relsz[0] = dyn->d_un.d_val; break;
        case DT_RELA: rel[1] = p; break;
        case DT_RELASZ: relsz[1] = dyn->d_un.d_val; break;
        }
    }
    assert(symtab && strtab);
    for (int r = 0; r < 2; r++)
        for (size_t i = 0; rel[r] && i < relsz[r] / sizeof(ElfW(Rela)); i++)
            if (ELF64_R_SYM(rel[r][i].r_info) && !strcmp(strtab + symtab[ELF64_R_SYM(rel[r][i].r_info)].st_name, name))
                return (void **)(info->dlpi_addr + rel[r][i].r_offset);
    return NULL;
}

static void patch(void **slot, void *value)
{
    uintptr_t page = (uintptr_t)slot & ~(uintptr_t)(sysconf(_SC_PAGESIZE) - 1);
    assert(mprotect((void *)page, (size_t)sysconf(_SC_PAGESIZE), PROT_READ | PROT_WRITE) == 0);
    *slot = value;
}

int main(int argc, char **argv)  /* libdl_bio, the library's name (found in BIONIC_LD_LIBRARY_PATH) */
{
    assert(argc == 3);
    void *dl = dlopen(argv[1], RTLD_NOW | RTLD_GLOBAL);
    assert(dl);
    void *(*b_dlopen)(const char *, int) = (void *(*)(const char *, int))dlsym(dl, "bionic_dlopen");
    void *(*b_dlsym)(void *, const char *) = (void *(*)(void *, const char *))dlsym(dl, "bionic_dlsym");
    int (*b_iterate)(int (*)(struct dl_phdr_info *, size_t, void *), void *) =
        (int (*)(int (*)(struct dl_phdr_info *, size_t, void *), void *))dlsym(dl, "bionic_dl_iterate_phdr");
    unsigned long (*b_getauxval)(unsigned long) = (unsigned long (*)(unsigned long))dlsym(dl, "bionic_getauxval");
    struct r_debug **r_debug_ptr = (struct r_debug **)dlsym(dl, "_r_debug_ptr");
    assert(b_dlopen && b_dlsym && b_iterate && b_getauxval && r_debug_ptr);
    *r_debug_ptr = &_r_debug;  /* the shim linker tells gdb of its loads there (btload and the runtime set it so) */

    /* the linker: AT_BASE is the ELF header of the library that implements dlopen; other entries are the kernel's */
    Dl_info linker;
    assert(dladdr((void *)b_dlopen, &linker) && b_getauxval(AT_BASE) == (unsigned long)linker.dli_fbase);
    assert(!memcmp(linker.dli_fbase, ELFMAG, SELFMAG) && b_getauxval(AT_PAGESZ) == getauxval(AT_PAGESZ));

    void *lib = b_dlopen(argv[2], RTLD_NOW);
    assert(lib);
    int (*call)(void) = (int (*)(void))b_dlsym(lib, "plt_hook_call");
    assert(call && call() == 1);

    /* the library: dlpi_addr is its load bias (the ELF header at the bias plus the first PT_LOAD's address, the phdrs in it) */
    char suffix[256];
    snprintf(suffix, sizeof suffix, "/%s", argv[2]);
    struct find f = { suffix };
    b_iterate(find_cb, &f);
    assert(f.found == 1);
    ElfW(Addr) first = (ElfW(Addr))-1;
    for (int i = 0; i < f.info.dlpi_phnum; i++)
        if (f.info.dlpi_phdr[i].p_type == PT_LOAD && f.info.dlpi_phdr[i].p_vaddr < first) first = f.info.dlpi_phdr[i].p_vaddr;
    const ElfW(Ehdr) *ehdr = (const ElfW(Ehdr) *)(f.info.dlpi_addr + first);
    assert(!memcmp(ehdr->e_ident, ELFMAG, SELFMAG) && (uintptr_t)f.info.dlpi_phdr == (uintptr_t)ehdr + ehdr->e_phoff);
    void **slot = got_slot(&f.info, "plt_hook_target");
    assert(slot && *slot);
    patch(slot, (void *)hooked);
    assert(call() == 2);

    /* libdl.so: reported once, as a real ELF whose dlopen calls __loader_dlopen through a GOT slot (bytehook's dlopen monitor) */
    struct find d = { "/libdl.so" };
    b_iterate(find_cb, &d);
    assert(d.found == 1);
    slot = got_slot(&d.info, "__loader_dlopen");
    assert(slot && *slot == dlsym(dl, "__loader_dlopen"));
    assert(got_slot(&d.info, "__loader_android_dlopen_ext") && got_slot(&d.info, "__loader_dlclose"));
    int (*b_dladdr)(const void *, Dl_info *) = (int (*)(const void *, Dl_info *))dlsym(dl, "bionic_dladdr");
    Dl_info value;  /* dladdr names the slot's target as dl_iterate_phdr does (bytehook verifies a GOT value so) */
    assert(b_dladdr && b_dladdr(*slot, &value) && !strcmp(value.dli_fname, d.info.dlpi_name));
    loader_dlopen = (void *(*)(const char *, int, const void *))*slot;
    patch(slot, (void *)count_dlopen);
    assert(b_dlopen(argv[2], RTLD_NOW) == lib && dlopens == 1);

    /* a host library (glibc's libc): listed with its code (unwinders), without PT_DYNAMIC (the runtime's imports are not Android's to hook) */
    struct find c = { "/libc.so.6" };
    b_iterate(find_cb, &c);
    assert(c.found == 1);
    int loads = 0;
    for (int i = 0; i < c.info.dlpi_phnum; i++)
        assert(c.info.dlpi_phdr[i].p_type != PT_DYNAMIC), loads += c.info.dlpi_phdr[i].p_type == PT_LOAD;
    assert(loads > 0);
    puts("PASS PLT hooks through dl_iterate_phdr: a loaded library's import, libdl's __loader_dlopen, no host imports; AT_BASE is the linker");
    return 0;
}
