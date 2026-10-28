/* What the shell knows about an installed app: its package and its label (apklayer patch: windows carry the real name and the app id the desktop entries match). */
#define _GNU_SOURCE
#include "appinfo.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

const char *appinfo_package(const char *name)
{
    if (!name || strncmp(name, "apklayer:", 9)) return name ? name : "";
    const char *p = name;
    for (int i = 0; i < 3; i++) { p = strchr(p, ':'); if (!p) return name; p++; }
    return *p ? p : name;
}

/* java.util.Properties escapes: backslash-colon/equals/space/backslash and \uXXXX (written as UTF-8). */
static void unescape(const char *in, char *out, size_t n)
{
    size_t o = 0;
    for (const char *s = in; *s && *s != '\n' && o + 4 < n; s++) {
        if (*s != '\\') { out[o++] = *s; continue; }
        s++;
        if (*s == 'u') {
            unsigned cp = (unsigned)strtoul((char[5]){s[1], s[2], s[3], s[4], 0}, NULL, 16);
            s += 4;
            if (cp < 0x80) out[o++] = (char)cp;
            else if (cp < 0x800) { out[o++] = (char)(0xC0 | cp >> 6); out[o++] = (char)(0x80 | (cp & 0x3F)); }
            else { out[o++] = (char)(0xE0 | cp >> 12); out[o++] = (char)(0x80 | ((cp >> 6) & 0x3F)); out[o++] = (char)(0x80 | (cp & 0x3F)); }
        } else if (*s) out[o++] = *s;
    }
    out[o] = 0;
}

void appinfo_label(const char *package, char *out, size_t n)
{
    snprintf(out, n, "%s", package);
    const char *xdg = getenv("XDG_DATA_HOME"), *home = getenv("HOME");
    char path[1024];
    if (xdg && *xdg) snprintf(path, sizeof path, "%s/apklayer/apps/%s/info.properties", xdg, package);
    else snprintf(path, sizeof path, "%s/.local/share/apklayer/apps/%s/info.properties", home ? home : "", package);
    FILE *f = fopen(path, "r");
    if (!f) return;
    char line[512];
    while (fgets(line, sizeof line, f))
        if (!strncmp(line, "label=", 6)) { char label[256]; unescape(line + 6, label, sizeof label); if (label[0]) snprintf(out, n, "%s", label); break; }
    fclose(f);
}
