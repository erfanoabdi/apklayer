/* Loads libart.so (libart_jni.c, installed at the Android root's /apex/com.android.art/lib64/libart.so) into the guest process and hands it the JNIEnv
 * and JavaVM tables: their entries become jumps inside that library. Best effort: without the file the tables stay the JVM's own. */
#define _GNU_SOURCE
#include <jni.h>
#include <jvmti.h>
#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

extern void *bionic_dlopen(const char *filename, int flag);

struct art_tables { void *vm; void **jni; void **invoke; unsigned long jni_entries; void *jni_copy, *invoke_copy; };

/* The JNIEnv's function table is libart.so's own data (libart_jni.c): every thread's env points to it, as ART's do. The tables of the JVM stay behind it. */
void *apklayer_art_jni_table;

static void JNICALL thread_start(jvmtiEnv *ti, JNIEnv *env, jthread thread)
{
    (void)ti; (void)thread;
    *(void **)env = apklayer_art_jni_table;
}

void apklayer_art_load(JavaVM *vm, void *jni_table, void *invoke_table)
{
#if !defined(__aarch64__)
    (void)vm; (void)jni_table; (void)invoke_table;  /* x86_64: the guest is translated arm64 code with the translator's own tables; libart.so there is an aarch64 file the host cannot load */
    return;
#endif
    static const char suffix[] = "/apex/com.android.art/lib64/libart.so";
    const char *roots[2] = { getenv("APKLAYER_ANDROID_ROOT"), NULL };
    const char *native = getenv("APKLAYER_NATIVE");
    char fallback[PATH_MAX], path[PATH_MAX];
    if (native) { snprintf(fallback, sizeof fallback, "%s/android-root", native); roots[1] = fallback; }
    for (int i = 0; i < 2; i++) {
        if (!roots[i] || snprintf(path, sizeof path, "%s%s", roots[i], suffix) >= (int)sizeof path || access(path, R_OK)) continue;
        struct art_tables tables = { vm, jni_table, invoke_table, sizeof(jniNativeInterface) / sizeof(void *), NULL, NULL };
        char address[32];
        snprintf(address, sizeof address, "%lx", (unsigned long)&tables);
        setenv("APKLAYER_ART_TABLES", address, 1);
        void *lib = bionic_dlopen(path, 2 /* RTLD_NOW */);
        unsetenv("APKLAYER_ART_TABLES");
        if (!lib) fprintf(stderr, "[runtime] libart.so did not load: the JNI tables keep the JVM's functions\n");
        else if (tables.jni_copy) {
            JNIEnv *env = NULL;
            jvmtiEnv *ti = NULL;
            apklayer_art_jni_table = tables.jni_copy;
            *(void **)vm = tables.invoke_copy;  /* the one JavaVM */
            if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6) == JNI_OK) *(void **)env = tables.jni_copy;
            if ((*vm)->GetEnv(vm, (void **)&ti, JVMTI_VERSION_1_2) == JNI_OK) {  /* threads started later (attached ones: jni_guest_surface.c) */
                jvmtiEventCallbacks cb = { .ThreadStart = thread_start };
                (*ti)->SetEventCallbacks(ti, &cb, sizeof cb);
                (*ti)->SetEventNotificationMode(ti, JVMTI_ENABLE, JVMTI_EVENT_THREAD_START, NULL);
            }
        }
        return;
    }
}
