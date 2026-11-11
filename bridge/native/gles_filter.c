/* libapklayer_gles: the host's libGLESv2 with some extensions hidden from Android code (APKLAYER_GL_HIDE, extension names separated by
 * spaces or commas). bionic_translation maps libGLESv2.so here (a cfg.d line); this library only defines the queries an app learns the
 * extensions from (glGetString(GL_EXTENSIONS), glGetStringi, glGetIntegerv's GL_NUM_EXTENSIONS) and every other GL function comes from the
 * host's libGLESv2.so.2, its DT_NEEDED (dlsym on this library's handle searches its dependencies too).
 * Why: a driver can advertise a texture format it decodes wrongly; an engine falls back when the extension is missing (Unity decompresses
 * ASTC textures on the CPU). Hiding an ASTC extension also drops the ASTC formats from GL_COMPRESSED_TEXTURE_FORMATS.
 * Without APKLAYER_GL_HIDE every query is the host's. */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <pthread.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#define GL_EXTENSIONS 0x1F03
#define GL_NUM_EXTENSIONS 0x821D
#define GL_NUM_COMPRESSED_TEXTURE_FORMATS 0x86A2
#define GL_COMPRESSED_TEXTURE_FORMATS 0x86A3
#define MAX_HIDDEN 32

typedef const unsigned char *(*get_string_fn)(unsigned);
typedef const unsigned char *(*get_stringi_fn)(unsigned, unsigned);
typedef void (*get_integerv_fn)(unsigned, int *);

static get_string_fn host_get_string;
static get_stringi_fn host_get_stringi;
static get_integerv_fn host_get_integerv;
static char *hidden[MAX_HIDDEN];
static int nhidden;
static bool hide_astc;  /* an ASTC extension is hidden: its formats too */
static pthread_once_t once = PTHREAD_ONCE_INIT;

static void init(void)
{
    void *gl = dlopen("libGLESv2.so.2", RTLD_NOW | RTLD_NOLOAD);
    if (!gl) gl = dlopen("libGLESv2.so.2", RTLD_NOW);
    if (gl) {
        host_get_string = (get_string_fn)dlsym(gl, "glGetString");
        host_get_stringi = (get_stringi_fn)dlsym(gl, "glGetStringi");
        host_get_integerv = (get_integerv_fn)dlsym(gl, "glGetIntegerv");
    }
    const char *list = getenv("APKLAYER_GL_HIDE");
    if (!list) return;
    char *copy = strdup(list), *save = NULL;
    for (char *t = strtok_r(copy, " ,", &save); t && nhidden < MAX_HIDDEN; t = strtok_r(NULL, " ,", &save)) {
        hidden[nhidden++] = strdup(t);
        if (strcasestr(t, "astc")) hide_astc = true;
    }
    free(copy);
}

static bool is_hidden(const char *ext, size_t len)
{
    for (int i = 0; i < nhidden; i++)
        if (strlen(hidden[i]) == len && !strncmp(hidden[i], ext, len)) return true;
    return false;
}

/* glGetString(GL_EXTENSIONS) without the hidden names; one filtered copy per host string (a context of another kind has its own) */
static pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;
static struct { const unsigned char *from; char *filtered; } cache[8];

const unsigned char *glGetString(unsigned name)
{
    pthread_once(&once, init);
    if (!host_get_string) return NULL;
    const unsigned char *s = host_get_string(name);
    if (name != GL_EXTENSIONS || !s || !nhidden) return s;
    pthread_mutex_lock(&lock);
    const unsigned char *out = NULL;
    int free_slot = -1;
    for (int i = 0; i < 8 && !out; i++) {
        if (cache[i].from == s) out = (const unsigned char *)cache[i].filtered;
        else if (!cache[i].from && free_slot < 0) free_slot = i;
    }
    if (!out) {
        const char *in = (const char *)s;
        char *f = malloc(strlen(in) + 1), *w = f;
        while (f && *in) {
            while (*in == ' ') in++;
            size_t len = strcspn(in, " ");
            if (len && !is_hidden(in, len)) { if (w != f) *w++ = ' '; memcpy(w, in, len); w += len; }
            in += len;
        }
        if (f) *w = 0;
        if (f && free_slot >= 0) { cache[free_slot].from = s; cache[free_slot].filtered = f; }
        out = f ? (const unsigned char *)f : s;  /* with every slot taken the copy is kept for good (eight context kinds is plenty) */
    }
    pthread_mutex_unlock(&lock);
    return out;
}

/* the host's index of the visible extension i, or -1 (past the end) */
static int host_index(unsigned i)
{
    int n = 0;
    host_get_integerv(GL_NUM_EXTENSIONS, &n);
    for (int k = 0; k < n; k++) {
        const char *e = (const char *)host_get_stringi(GL_EXTENSIONS, (unsigned)k);
        if (e && is_hidden(e, strlen(e))) continue;
        if (i-- == 0) return k;
    }
    return -1;
}

const unsigned char *glGetStringi(unsigned name, unsigned index)
{
    pthread_once(&once, init);
    if (!host_get_stringi) return NULL;
    if (name != GL_EXTENSIONS || !nhidden || !host_get_integerv) return host_get_stringi(name, index);
    int k = host_index(index);
    return host_get_stringi(name, k < 0 ? index + 100000u : (unsigned)k);  /* past the end: the host's GL_INVALID_VALUE and NULL */
}

static bool astc_format(int f) { return (f >= 0x93B0 && f <= 0x93BD) || (f >= 0x93D0 && f <= 0x93DD) || (f >= 0x93C0 && f <= 0x93C9) || (f >= 0x93E0 && f <= 0x93E9); }

void glGetIntegerv(unsigned pname, int *data)
{
    pthread_once(&once, init);
    if (!host_get_integerv) return;
    if (!nhidden || !data) { host_get_integerv(pname, data); return; }
    if (pname == GL_NUM_EXTENSIONS) {
        int n = 0, visible = 0;
        host_get_integerv(GL_NUM_EXTENSIONS, &n);
        for (int k = 0; k < n; k++) {
            const char *e = host_get_stringi ? (const char *)host_get_stringi(GL_EXTENSIONS, (unsigned)k) : NULL;
            if (!e || !is_hidden(e, strlen(e))) visible++;
        }
        *data = visible;
        return;
    }
    if (hide_astc && (pname == GL_NUM_COMPRESSED_TEXTURE_FORMATS || pname == GL_COMPRESSED_TEXTURE_FORMATS)) {
        int n = 0;
        host_get_integerv(GL_NUM_COMPRESSED_TEXTURE_FORMATS, &n);
        int *all = calloc(n > 0 ? (size_t)n : 1, sizeof *all), kept = 0;
        if (!all) { host_get_integerv(pname, data); return; }
        host_get_integerv(GL_COMPRESSED_TEXTURE_FORMATS, all);
        for (int k = 0; k < n; k++)
            if (!astc_format(all[k])) { if (pname == GL_COMPRESSED_TEXTURE_FORMATS) data[kept] = all[k]; kept++; }
        if (pname == GL_NUM_COMPRESSED_TEXTURE_FORMATS) *data = kept;
        free(all);
        return;
    }
    host_get_integerv(pname, data);
}
