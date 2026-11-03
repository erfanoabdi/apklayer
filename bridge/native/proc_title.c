#define _GNU_SOURCE
/* The process's command line and name as Android sets them (Process.setArgV0, called by ActivityThread with the app's process name, and
 * prctl(PR_SET_NAME)): native code reads its process name from /proc/self/cmdline (GeckoView's mozglue asserts a ":media" suffix in its media
 * process), so a JVM that hosts an Android process rewrites its own argument area, as setproctitle does, within the bytes it has. */
#include <jni.h>
#include <stdio.h>
#include <string.h>
#include <sys/prctl.h>

/* arg_start and arg_end of this process (fields 48 and 49 of /proc/self/stat, counted after the parenthesised command name). */
static int arg_area(unsigned long *start, unsigned long *end)
{
    char buf[4096];
    FILE *f = fopen("/proc/self/stat", "r");
    if (!f) return -1;
    size_t n = fread(buf, 1, sizeof buf - 1, f);
    fclose(f);
    buf[n] = 0;
    char *p = strrchr(buf, ')');
    if (!p) return -1;
    p += 2;  /* field 3 */
    for (int field = 3; field < 48 && p; field++) p = strchr(p, ' '), p = p ? p + 1 : NULL;
    return p && sscanf(p, "%lu %lu", start, end) == 2 && *end > *start ? 0 : -1;
}

void apklayer_set_process_name(const char *name)
{
    unsigned long start, end;
    if (arg_area(&start, &end) == 0) {
        size_t room = end - start;  /* the kernel shows the area up to its first NUL as argv[0] */
        memset((void *)start, 0, room);
        strncpy((char *)start, name, room - 1);
    }
    const char *tail = strlen(name) > 15 ? name + strlen(name) - 15 : name;  /* comm holds 15 bytes: Android keeps the end (the ":suffix") */
    prctl(PR_SET_NAME, tail, 0, 0, 0);
}

void Java_apklayer_host_NativeLoader_setProcessName(JNIEnv *env, jclass cls, jstring name)
{
    (void)cls;
    const char *s = (*env)->GetStringUTFChars(env, name, NULL);
    if (!s) return;
    apklayer_set_process_name(s);
    (*env)->ReleaseStringUTFChars(env, name, s);
}
