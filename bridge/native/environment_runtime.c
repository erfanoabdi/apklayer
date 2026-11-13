/* GuestEnvironment uses the active app ABI's libc environment, never the JVM's cached host map. */
#include <errno.h>
#include <jni.h>
#include <stddef.h>

extern char *bionic_getenv(const char *);
extern int bionic_setenv(const char *, const char *, int);
extern int bionic_unsetenv(const char *);
extern char ***bionic_environment_slot(void);

static jstring get(JNIEnv *env, jclass cls, jstring name)
{
    (void)cls;
    const char *key = (*env)->GetStringUTFChars(env, name, NULL);
    if (!key) return NULL;
    const char *value = bionic_getenv(key);
    jstring result = value ? (*env)->NewStringUTF(env, value) : NULL;
    (*env)->ReleaseStringUTFChars(env, name, key);
    return result;
}

static jobjectArray entries(JNIEnv *env, jclass cls)
{
    (void)cls;
    char ***slot = bionic_environment_slot();
    if (!slot) {
        jclass error = (*env)->FindClass(env, "java/lang/OutOfMemoryError");
        if (error) { (*env)->ThrowNew(env, error, "Cannot initialize guest environment"); (*env)->DeleteLocalRef(env, error); }
        return NULL;
    }
    char **values = *slot;
    jsize count = 0;
    while (values && values[count]) count++;
    jclass string = (*env)->FindClass(env, "java/lang/String");
    if (!string) return NULL;
    jobjectArray result = (*env)->NewObjectArray(env, count, string, NULL);
    (*env)->DeleteLocalRef(env, string);
    if (!result) return NULL;
    for (jsize i = 0; i < count; i++) {
        jstring value = (*env)->NewStringUTF(env, values[i]);
        if (!value) return NULL;
        (*env)->SetObjectArrayElement(env, result, i, value);
        (*env)->DeleteLocalRef(env, value);
        if ((*env)->ExceptionCheck(env)) return NULL;
    }
    return result;
}

static jint set(JNIEnv *env, jclass cls, jstring name, jstring value, jboolean overwrite)
{
    (void)cls;
    const char *key = (*env)->GetStringUTFChars(env, name, NULL);
    if (!key) return ENOMEM;
    const char *text = (*env)->GetStringUTFChars(env, value, NULL);
    int error = ENOMEM;
    if (text) {
        error = bionic_setenv(key, text, overwrite) == 0 ? 0 : errno;
        (*env)->ReleaseStringUTFChars(env, value, text);
    }
    (*env)->ReleaseStringUTFChars(env, name, key);
    return error;
}

static jint unset(JNIEnv *env, jclass cls, jstring name)
{
    (void)cls;
    const char *key = (*env)->GetStringUTFChars(env, name, NULL);
    if (!key) return ENOMEM;
    int error = bionic_unsetenv(key) == 0 ? 0 : errno;
    (*env)->ReleaseStringUTFChars(env, name, key);
    return error;
}

int apklayer_register_environment(JNIEnv *env, jclass cls)
{
    JNINativeMethod methods[] = {
        {"nativeGet", "(Ljava/lang/String;)Ljava/lang/String;", (void *)get},
        {"nativeEntries", "()[Ljava/lang/String;", (void *)entries},
        {"nativeSet", "(Ljava/lang/String;Ljava/lang/String;Z)I", (void *)set},
        {"nativeUnset", "(Ljava/lang/String;)I", (void *)unset},
    };
    return (*env)->RegisterNatives(env, cls, methods, sizeof methods / sizeof *methods);
}
