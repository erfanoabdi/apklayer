/* apklayer-prop: the property store's command line (PLAN P7.1 step 0.4).
 *   aprop get NAME | set NAME VALUE | wait NAME VALUE | dump | load FILE...   (FILE: Android build.prop format, KEY=VALUE lines) */
#include <stdint.h>
#include <time.h>
#include <stdio.h>
#include <string.h>

struct prop_info;
extern const struct prop_info *__system_property_find_nth(unsigned n);
extern int __system_property_read(const struct prop_info *pi, char *name, char *value);
extern const struct prop_info *__system_property_find(const char *name);
extern int __system_property_wait(const struct prop_info *pi, uint32_t old_serial, uint32_t *new_serial, const struct timespec *timeout);
extern uint32_t __system_property_serial(const struct prop_info *pi);
extern int __system_property_get(const char *name, char *value);
extern int __system_property_set(const char *name, const char *value);

int main(int argc, char **argv)
{
    if (argc >= 3 && !strcmp(argv[1], "get")) {
        char v[92];
        int n = __system_property_get(argv[2], v);
        printf("%s\n", v);
        return n > 0 ? 0 : 1;
    }
    if (argc >= 4 && !strcmp(argv[1], "set")) return __system_property_set(argv[2], argv[3]) == 0 ? 0 : 1;
    if (argc >= 4 && !strcmp(argv[1], "wait")) {  /* like Android's WaitForProperty: returns when NAME equals VALUE, blocking on the property's serial */
        const struct timespec none = {0, 1};
        for (;;) {
            uint32_t area = 0;
            __system_property_wait(NULL, 0, &area, &none);  /* the area's serial before looking: a creation after this wakes the wait below */
            const struct prop_info *pi = __system_property_find(argv[2]);
            char v[92] = "";
            if (pi) {
                uint32_t serial = __system_property_serial(pi);
                __system_property_get(argv[2], v);
                if (!strcmp(v, argv[3])) return 0;
                __system_property_wait(pi, serial, &serial, NULL);
            } else {
                __system_property_wait(NULL, area, &area, NULL);
            }
        }
    }
    if (argc >= 2 && !strcmp(argv[1], "dump")) {
        char n[64], v[92];
        for (unsigned i = 0; __system_property_find_nth(i); i++) {
            __system_property_read(__system_property_find_nth(i), n, v);
            printf("[%s]: [%s]\n", n, v);
        }
        return 0;
    }
    if (argc >= 3 && !strcmp(argv[1], "load")) {
        int set = 0, skipped = 0;
        for (int i = 2; i < argc; i++) {
            FILE *f = fopen(argv[i], "r");
            if (!f) { perror(argv[i]); return 1; }
            char line[512];
            while (fgets(line, sizeof line, f)) {
                char *eq = strchr(line, '=');
                if (line[0] == '#' || !eq) continue;
                *eq = 0;
                char *v = eq + 1;
                v[strcspn(v, "\r\n")] = 0;
                if (__system_property_set(line, v) == 0) set++; else skipped++; /* too long, or a ro.* already set */
            }
            fclose(f);
        }
        fprintf(stderr, "aprop: %d set, %d skipped\n", set, skipped);
        return 0;
    }
    fprintf(stderr, "usage: aprop get NAME | set NAME VALUE | wait NAME VALUE | dump | load FILE...\n");
    return 2;
}
