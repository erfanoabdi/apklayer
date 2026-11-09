/* The dl* entries of bionic_translation's libdl as adrenotools-style namespace bypasses read them (androidnsbypass in FCL/Pojav, aarch64):
 * the first `bl` of dlopen/android_dlopen_ext/dlclose/dlsym, its 26-bit offset taken unsigned, is called as __loader_<name>(..., caller).
 * Each target must lie in the same library and work with those arguments (bionic_translation 0049). */
#define _GNU_SOURCE
#include <assert.h>
#include <dlfcn.h>
#include <stdint.h>
#include <stdio.h>

static void *branch_target(void *entry)
{
    uint32_t *insn = entry;
    while ((*insn & 0xfc000000u) != 0x94000000u) insn++;  /* as androidnsbypass's find_branch_label */
    return (char *)insn + (*insn & 0x03ffffffu) * 4;
}

static void *same_library(void *library, const char *name)
{
    void *entry = dlsym(library, name), *target;
    Dl_info a, b;
    assert(entry);
    target = branch_target(entry);
    assert(dladdr(entry, &a) && dladdr(target, &b) && a.dli_fbase == b.dli_fbase);
    return target;
}

int main(int argc, char **argv)
{
#if defined(__aarch64__)
    assert(argc == 2);
    void *library = dlopen(argv[1], RTLD_NOW | RTLD_LOCAL);
    assert(library);
    void *(*loader_dlopen)(const char *, int, const void *) = same_library(library, "bionic_dlopen");
    void *(*loader_dlsym)(void *, const char *, const void *) = same_library(library, "bionic_dlsym");
    int (*loader_dlclose)(void *) = same_library(library, "bionic_dlclose");
    same_library(library, "bionic_android_dlopen_ext");
    void *libc = loader_dlopen("libc.so.6", RTLD_NOW, (void *)main);
    assert(libc && loader_dlsym(libc, "malloc", (void *)main));
    assert(loader_dlclose(libc) == 0);
    puts("PASS libdl entries: first bl reaches a working __loader_* in the same library");
#else
    (void)argc, (void)argv;
    puts("PASS (aarch64 only)");
#endif
    return 0;
}
