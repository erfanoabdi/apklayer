#define _GNU_SOURCE
#include <assert.h>
#include <dlfcn.h>
#include <errno.h>
#include <link.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

extern char **environ;
static uint64_t host_hash(void)
{
    uint64_t hash = 1469598103934665603ULL;
    for (char **p = environ; p && *p; p++)
        for (const unsigned char *c = (const unsigned char *)*p;; c++) {
            hash = (hash ^ *c) * 1099511628211ULL;
            if (!*c) break;
        }
    return hash;
}
int main(int argc, char **argv)
{
    assert(argc == 4);
    void *c = dlopen(argv[1], RTLD_NOW | RTLD_GLOBAL), *dl = dlopen(argv[2], RTLD_NOW | RTLD_GLOBAL); assert(c && dl);
    char *(*get)(const char *) = dlsym(c, "bionic_getenv");
    int (*set)(const char *, const char *, int) = dlsym(c, "bionic_setenv");
    int (*unset)(const char *) = dlsym(c, "bionic_unsetenv");
    int (*put)(char *) = dlsym(c, "bionic_putenv");
    int (*clear)(void) = dlsym(c, "bionic_clearenv");
    char ***(*slot_fn)(void) = dlsym(c, "bionic_environment_slot");
    const char *(*host_get)(const char *) = dlsym(c, "libc_bio_host_getenv");
    int (*initialize)(char *const [], int) = dlsym(c, "libc_bio_init_guest_environment");
    const char *(*initial)(size_t *) = dlsym(c, "libc_bio_initial_environment");
    assert(initial);
    void *(*b_open)(const char *, int) = dlsym(dl, "bionic_dlopen");
    void *(*b_sym)(void *, const char *) = dlsym(dl, "bionic_dlsym");
    struct r_debug **debug = dlsym(dl, "_r_debug_ptr"); assert(debug); *debug = &_r_debug;
    assert(get && set && unset && put && clear && slot_fn && host_get && initialize && b_open && b_sym);
    int app = getenv("APKLAYER_OWN_DATA") && !getenv("APKLAYER_PROC_VIEW");
    char ***slot = slot_fn(); assert(slot);
    assert(b_sym(RTLD_DEFAULT, "environ") == slot && b_sym(RTLD_DEFAULT, "__environ") == slot);
    void *lib = b_open(argv[3], 2); assert(lib);
    char ***(*module_slot)(void) = b_sym(lib, "environment_slot");
    char ***(*module_alias)(void) = b_sym(lib, "environment_alias");
    char *(*module_get)(const char *) = b_sym(lib, "environment_get");
    int (*module_set)(const char *, const char *) = b_sym(lib, "environment_set");
    assert(module_slot && module_alias && module_get && module_set);
    assert(module_slot() == slot && module_alias() == slot);
    uint64_t original_host = host_hash();
    if (!app) {
        size_t size = 123; assert(!initial(&size) && size == 0);
        assert(slot == &environ && !strcmp(get("HOME"), getenv("HOME")));
        assert(!set("ENV_VIEW_TEST", "host", 1) && !strcmp(getenv("ENV_VIEW_TEST"), "host"));
        assert(!module_set("ENV_VIEW_TEST", "module") && !strcmp(getenv("ENV_VIEW_TEST"), "module"));
        assert(!unset("ENV_VIEW_TEST") && !getenv("ENV_VIEW_TEST"));
        puts("PASS environment service/host: actual host slot and libc behavior"); return 0;
    }
    size_t initial_size = 0;
    const char *seed = initial(&initial_size); assert(seed && initial_size && seed[initial_size - 1] == 0);
    char *saved_seed = malloc(initial_size); assert(saved_seed); memcpy(saved_seed, seed, initial_size);
    size_t enumerated = 0;
    for (char **entry = *slot; *entry; entry++) {
        size_t length = strlen(*entry) + 1;
        assert(length <= initial_size - enumerated && !memcmp(seed + enumerated, *entry, length));
        enumerated += length;
    }
    assert(enumerated == initial_size);
    errno = 0; assert(!initial(NULL) && errno == EINVAL);
    assert(slot != &environ && !get("HOME") && !get("APKLAYER_OWN_DATA"));
    assert(!get("LD_PRELOAD") && !get("JAVA_HOME") && !get("ENV_HOST_ONLY"));
    assert(!strcmp(get("ANDROID_ROOT"), "/system") && strstr(get("PATH"), "/system/bin"));
    assert(!strcmp(host_get("HOME"), getenv("HOME")));
    assert(!set("HOME", getenv("HOME"), 1) && !strcmp(get("HOME"), getenv("HOME")));
    assert(!set("VALUE", "first", 1) && !set("VALUE", "ignored", 0) && !strcmp(get("VALUE"), "first"));
    assert(!module_set("VALUE", "second") && !strcmp(get("VALUE"), "second") && !strcmp(module_get("VALUE"), "second"));
    assert(!set("EMPTY", "", 1) && get("EMPTY") && !*get("EMPTY"));
    char mutable[] = "MUTABLE=first";
    assert(!put(mutable) && get("MUTABLE") == strchr(mutable, '=') + 1);
    strcpy(strchr(mutable, '=') + 1, "later"); assert(!strcmp(get("MUTABLE"), "later"));
    char one[] = "DUP=one", two[] = "DUP=two", other[] = "OTHER=yes";
    char *replacement[] = {one, two, other, NULL};
    *slot = replacement;
    assert(!strcmp(get("DUP"), "one") && !strcmp(module_get("OTHER"), "yes"));
    assert(!unset("DUP") && !get("DUP") && *slot == replacement && !strcmp((*slot)[0], "OTHER=yes") && !(*slot)[1]);
    assert(!set("NEW", "value", 1) && !strcmp(get("OTHER"), "yes") && !strcmp(get("NEW"), "value"));
    const char *invalid[] = {"", "BAD=NAME", NULL};
    for (size_t i = 0; i < 3; i++) {
        errno = 0; assert(!get(invalid[i]) && errno == EINVAL);
        errno = 0; assert(set(invalid[i], "x", 1) == -1 && errno == EINVAL);
        errno = 0; assert(unset(invalid[i]) == -1 && errno == EINVAL);
    }
    char no_equal[] = "MUTABLE", no_name[] = "=value";
    errno = 0; assert(put(no_equal) == -1 && errno == EINVAL);
    errno = 0; assert(put(no_name) == -1 && errno == EINVAL);
    errno = 0; assert(put(NULL) == -1 && errno == EINVAL);
    assert(!clear() && !*slot && !get("ANDROID_ROOT"));
    assert(!set("AFTER_CLEAR", "works", 1) && !strcmp(get("AFTER_CLEAR"), "works"));
    size_t unchanged_size = 0;
    assert(initial(&unchanged_size) == seed && unchanged_size == initial_size && !memcmp(seed, saved_seed, initial_size));
    free(saved_seed);
    char home[] = "HOME=/explicit", custom[] = "APKLAYER_CUSTOM=app-owned", root[] = "ANDROID_ROOT=/explicit-system";
    char *explicit_env[] = {home, custom, root, NULL};
    assert(!initialize(explicit_env, 1));
    assert(!strcmp(get("HOME"), "/explicit") && !strcmp(get("APKLAYER_CUSTOM"), "app-owned"));
    assert(!strcmp(get("ANDROID_ROOT"), "/explicit-system") && !get("PATH"));
    size_t explicit_size = 0;
    const char expected[] = "HOME=/explicit\0APKLAYER_CUSTOM=app-owned\0ANDROID_ROOT=/explicit-system\0";
    const char *explicit_snapshot = initial(&explicit_size);
    assert(explicit_snapshot && explicit_size == sizeof expected - 1 && !memcmp(explicit_snapshot, expected, explicit_size));
    home[5] = 'X'; assert(!strcmp(get("HOME"), "/explicit")); /* seed owns a copy */
    assert(!unset("HOME") && !set("CHILD_CHANGE", "changed", 1) && !clear());
    size_t still_explicit;
    assert(initial(&still_explicit) == explicit_snapshot && still_explicit == explicit_size && !memcmp(explicit_snapshot, expected, explicit_size));
    assert(!initialize(NULL, 1) && *slot && !**slot);
    size_t empty_size = 123; assert(initial(&empty_size) && empty_size == 0);
    assert(!memcmp(explicit_snapshot, expected, explicit_size)); /* published snapshots outlive reseeding */
    assert(host_hash() == original_host);
    puts("PASS environment: Android seed, mutable putenv, direct slots, errors, clear, explicit child, host isolation");
}
