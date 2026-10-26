/* apklayer-prop: the property store's command line (PLAN P7.1 step 0.4).
 *   aprop get NAME | set NAME VALUE | dump | load FILE...   (FILE: Android build.prop format, KEY=VALUE lines) */
#include <stdint.h>
#include <stdio.h>
#include <string.h>

struct prop_info;
extern const struct prop_info *__system_property_find_nth(unsigned n);
extern int __system_property_read(const struct prop_info *pi, char *name, char *value);
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
    fprintf(stderr, "usage: aprop get NAME | set NAME VALUE | dump | load FILE...\n");
    return 2;
}
