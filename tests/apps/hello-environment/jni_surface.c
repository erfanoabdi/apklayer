/* The JNI surface ART gives app code (JniSurfaceChecks compares with Android 13): GetVersion, libnativehelper's invocation API
 * (JNI_GetCreatedJavaVMs returns the app's VM, JNI_GetDefaultJavaVMInitArgs and JNI_CreateJavaVM fail), FindClass on a natively
 * attached thread (the system class loader: no app classes) and the native name an attached thread gets. */
#define _GNU_SOURCE  /* pthread_getname_np */
#include <dlfcn.h>
#include <jni.h>
#include <pthread.h>
#include <stdio.h>
#include <string.h>

enum { VERSION, CREATED, DEFAULT_ARGS, CREATE, THREAD_APP, THREAD_FRAMEWORK, THREAD_STRING, THREAD_NAME, THREAD_NAMED, FRAME_APP, RESULTS };

struct probe { JavaVM *vm; const char *name; char out[RESULTS][160]; };

static void find(JNIEnv *env, const char *name, char *out, size_t size)
{
    jclass c = (*env)->FindClass(env, name);
    if (c) { snprintf(out, size, "found"); (*env)->DeleteLocalRef(env, c); return; }
    jthrowable t = (*env)->ExceptionOccurred(env);
    (*env)->ExceptionClear(env);
    jclass tc = t ? (*env)->GetObjectClass(env, t) : NULL;
    jclass cc = (*env)->FindClass(env, "java/lang/Class");
    jstring n = tc ? (jstring)(*env)->CallObjectMethod(env, tc, (*env)->GetMethodID(env, cc, "getName", "()Ljava/lang/String;")) : NULL;
    const char *chars = n ? (*env)->GetStringUTFChars(env, n, NULL) : NULL;
    snprintf(out, size, "NOT FOUND %s", chars ? chars : "(no exception)");
    if (chars) (*env)->ReleaseStringUTFChars(env, n, chars);
}

static void java_and_native_name(JNIEnv *env, char *out, size_t size)
{
    jclass thread = (*env)->FindClass(env, "java/lang/Thread");
    jobject self = (*env)->CallStaticObjectMethod(env, thread, (*env)->GetStaticMethodID(env, thread, "currentThread", "()Ljava/lang/Thread;"));
    jstring name = (jstring)(*env)->CallObjectMethod(env, self, (*env)->GetMethodID(env, thread, "getName", "()Ljava/lang/String;"));
    const char *chars = (*env)->GetStringUTFChars(env, name, NULL);
    char comm[32] = "";
    pthread_getname_np(pthread_self(), comm, sizeof comm);
    snprintf(out, size, "java=%.15s native=%s", chars, comm);
    (*env)->ReleaseStringUTFChars(env, name, chars);
}

static void *attached(void *arg)
{
    struct probe *p = arg;
    JNIEnv *env = NULL;
    JavaVMAttachArgs named = { JNI_VERSION_1_6, (char *)p->name, NULL };
    if ((*p->vm)->AttachCurrentThread(p->vm, &env, p->name ? &named : NULL) != JNI_OK) {
        snprintf(p->out[p->name ? THREAD_NAMED : THREAD_APP], sizeof p->out[0], "attach failed");
        return NULL;
    }
    if (p->name) java_and_native_name(env, p->out[THREAD_NAMED], sizeof p->out[0]);
    else {
        find(env, "hello/environment/JniSurfaceChecks", p->out[THREAD_APP], sizeof p->out[0]);
        find(env, "android/os/Build", p->out[THREAD_FRAMEWORK], sizeof p->out[0]);
        find(env, "java/lang/String", p->out[THREAD_STRING], sizeof p->out[0]);
        java_and_native_name(env, p->out[THREAD_NAME], sizeof p->out[0]);
    }
    (*p->vm)->DetachCurrentThread(p->vm);
    return NULL;
}

JNIEXPORT jobjectArray JNICALL Java_hello_environment_JniSurfaceChecks_probe(JNIEnv *env, jclass cls)
{
    (void)cls;
    static struct probe p;
    memset(&p, 0, sizeof p);
    (*env)->GetJavaVM(env, &p.vm);
    snprintf(p.out[VERSION], sizeof p.out[0], "0x%x", (unsigned)(*env)->GetVersion(env));
    void *helper = dlopen("libnativehelper.so", RTLD_NOW);
    jint (*created)(JavaVM **, jsize, jsize *) = helper ? (jint (*)(JavaVM **, jsize, jsize *))dlsym(helper, "JNI_GetCreatedJavaVMs") : NULL;
    jint (*default_args)(void *) = helper ? (jint (*)(void *))dlsym(helper, "JNI_GetDefaultJavaVMInitArgs") : NULL;
    jint (*create)(JavaVM **, JNIEnv **, void *) = helper ? (jint (*)(JavaVM **, JNIEnv **, void *))dlsym(helper, "JNI_CreateJavaVM") : NULL;
    if (created) {
        JavaVM *vms[2] = {0};
        jsize n = -1;
        jint r = created(vms, 2, &n);
        snprintf(p.out[CREATED], sizeof p.out[0], "ret=%d count=%d same=%d", r, n, n == 1 && vms[0] == p.vm);
    } else snprintf(p.out[CREATED], sizeof p.out[0], "no symbol");
    if (default_args) {
        JavaVMInitArgs args = { JNI_VERSION_1_6, 0, NULL, JNI_FALSE };
        snprintf(p.out[DEFAULT_ARGS], sizeof p.out[0], "%d", default_args(&args));
    } else snprintf(p.out[DEFAULT_ARGS], sizeof p.out[0], "no symbol");
    if (create) {
        JavaVMInitArgs args = { JNI_VERSION_1_6, 0, NULL, JNI_FALSE };
        JavaVM *vm = NULL;
        JNIEnv *e = NULL;
        snprintf(p.out[CREATE], sizeof p.out[0], "%d", create(&vm, &e, &args));
    } else snprintf(p.out[CREATE], sizeof p.out[0], "no symbol");
    pthread_t t;
    if (pthread_create(&t, NULL, attached, &p) == 0) pthread_join(t, NULL);
    p.name = "jni-surface";
    if (pthread_create(&t, NULL, attached, &p) == 0) pthread_join(t, NULL);
    find(env, "hello/environment/JniSurfaceChecks", p.out[FRAME_APP], sizeof p.out[0]);
    jclass string = (*env)->FindClass(env, "java/lang/String");
    jobjectArray out = (*env)->NewObjectArray(env, RESULTS, string, NULL);
    for (int i = 0; i < RESULTS; i++) (*env)->SetObjectArrayElement(env, out, i, (*env)->NewStringUTF(env, p.out[i]));
    return out;
}
