#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <errno.h>
#include <fcntl.h>
#include <unistd.h>

extern char **environ;
static char *put_storage;
static size_t put_value;

JNIEXPORT jbyteArray JNICALL Java_hello_environment_Main_procEnviron(JNIEnv *env, jclass cls, jboolean buffered)
{
    (void)cls;
    enum { LIMIT = 65536 };
    unsigned char *data = malloc(LIMIT + 1);
    int error = data ? 0 : ENOMEM;
    size_t size = 0;
    if (data && buffered) {
        FILE *file = fopen("/proc/self/environ", "rb");
        if (!file) error = errno;
        else {
            size = fread(data, 1, LIMIT + 1, file);
            if (ferror(file)) error = errno ? errno : EIO;
            if (fclose(file) && !error) error = errno;
        }
    } else if (data) {
        int fd = open("/proc/self/environ", O_RDONLY | O_CLOEXEC);
        if (fd < 0) error = errno;
        else {
            while (size < LIMIT + 1) {
                ssize_t count = read(fd, data + size, LIMIT + 1 - size);
                if (count > 0) size += (size_t)count;
                else if (count == 0) break;
                else if (errno != EINTR) { error = errno; break; }
            }
            if (close(fd) && !error) error = errno;
        }
    }
    if (size > LIMIT) error = EFBIG;
    jbyteArray result = NULL;
    if (error) {
        char message[160];
        snprintf(message, sizeof message, "%s /proc/self/environ: %s (%d)",
                 buffered ? "fread" : "open/read", strerror(error), error);
        jclass exception = (*env)->FindClass(env, "java/io/IOException");
        if (exception) (*env)->ThrowNew(env, exception, message);
    } else {
        result = (*env)->NewByteArray(env, (jsize)size);
        if (result) (*env)->SetByteArrayRegion(env, result, 0, (jsize)size, (jbyte *)data);
    }
    free(data);
    return result;
}

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
