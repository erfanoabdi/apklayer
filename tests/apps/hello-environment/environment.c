#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>

extern char **environ;
static char *put_storage;
static size_t put_value;

JNIEXPORT jstring JNICALL Java_hello_environment_Main_read(JNIEnv *env, jclass cls, jstring key, jboolean scan)
{
    (void)cls;
    const char *name = (*env)->GetStringUTFChars(env, key, NULL);
    if (!name) return NULL;
    const char *value = NULL;
    if (scan) {
        size_t len = strlen(name);
        for (char **entry = environ; entry && *entry; ++entry)
            if (!strncmp(*entry, name, len) && (*entry)[len] == '=') { value = *entry + len + 1; break; }
    } else value = getenv(name);
    jstring result = value ? (*env)->NewStringUTF(env, value) : NULL;
    (*env)->ReleaseStringUTFChars(env, key, name);
    return result;
}

JNIEXPORT jint JNICALL Java_hello_environment_Main_set(JNIEnv *env, jclass cls, jstring key, jstring val, jboolean overwrite)
{
    (void)cls;
    const char *name = (*env)->GetStringUTFChars(env, key, NULL);
    if (!name) return -1;
    const char *value = (*env)->GetStringUTFChars(env, val, NULL);
    if (!value) { (*env)->ReleaseStringUTFChars(env, key, name); return -1; }
    int result = setenv(name, value, overwrite);
    (*env)->ReleaseStringUTFChars(env, val, value);
    (*env)->ReleaseStringUTFChars(env, key, name);
    return result;
}

JNIEXPORT jint JNICALL Java_hello_environment_Main_unset(JNIEnv *env, jclass cls, jstring key)
{
    (void)cls;
    const char *name = (*env)->GetStringUTFChars(env, key, NULL);
    if (!name) return -1;
    int result = unsetenv(name);
    (*env)->ReleaseStringUTFChars(env, key, name);
    return result;
}

JNIEXPORT jint JNICALL Java_hello_environment_Main_put(JNIEnv *env, jclass cls, jstring key, jstring val)
{
    (void)cls;
    const char *name = (*env)->GetStringUTFChars(env, key, NULL);
    if (!name) return -1;
    const char *value = (*env)->GetStringUTFChars(env, val, NULL);
    if (!value) { (*env)->ReleaseStringUTFChars(env, key, name); return -1; }
    int result = -1;
    if (!put_storage && strlen(name) + strlen(value) + 2 < 1024) {
        put_storage = malloc(1024);
        if (put_storage) {
            snprintf(put_storage, 1024, "%s=%s", name, value);
            put_value = strlen(name) + 1;
            result = putenv(put_storage);
        }
    }
    (*env)->ReleaseStringUTFChars(env, val, value);
    (*env)->ReleaseStringUTFChars(env, key, name);
    return result;
}

JNIEXPORT void JNICALL Java_hello_environment_Main_mutatePut(JNIEnv *env, jclass cls, jstring val)
{
    (void)cls;
    const char *value = (*env)->GetStringUTFChars(env, val, NULL);
    if (!value) return;
    if (put_storage) snprintf(put_storage + put_value, 1024 - put_value, "%s", value);
    (*env)->ReleaseStringUTFChars(env, val, value);
}

JNIEXPORT void JNICALL Java_hello_environment_Main_releasePut(JNIEnv *env, jclass cls)
{
    (void)env; (void)cls;
    /* Java unsets this entry before releasing the storage retained by putenv. */
    free(put_storage);
    put_storage = NULL;
}
