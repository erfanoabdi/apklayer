/* Guest native code reading java.lang.System's properties and environment through JNI, in every call form an app library uses:
 * GetStaticMethodID with the varargs, V and A calls, FromReflectedMethod, Method.invoke through JNI, a natively created and attached
 * thread, a RegisterNatives-bound callback, JNI_OnLoad and a sibling-call (tail) JNI call. JniSystemChecks compares the answers with
 * Android's (the app's libcore property bag and guest libc environment). */
#include <jni.h>
#include <pthread.h>
#include <stdarg.h>
#include <string.h>

enum { FORM_VARARGS, FORM_V, FORM_A, FORM_REFLECTED, FORM_INVOKE, FORM_THREAD, FORM_TAIL };

static const struct op { const char *cls, *name, *sig; int argc; char ret; } ops[] = {
    {"java/lang/System", "getProperty", "(Ljava/lang/String;)Ljava/lang/String;", 1, 'L'},
    {"java/lang/System", "getenv", "(Ljava/lang/String;)Ljava/lang/String;", 1, 'L'},
    {"java/lang/System", "getProperties", "()Ljava/util/Properties;", 0, 'L'},
    {"java/lang/System", "getenv", "()Ljava/util/Map;", 0, 'L'},
    {"java/lang/System", "setProperty", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", 2, 'L'},
    {"java/lang/System", "clearProperty", "(Ljava/lang/String;)Ljava/lang/String;", 1, 'L'},
    {"java/lang/System", "getProperty", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", 2, 'L'},
    {"java/lang/Boolean", "getBoolean", "(Ljava/lang/String;)Z", 1, 'Z'},
    {"java/lang/System", "setProperties", "(Ljava/util/Properties;)V", 1, 'V'},
};
enum { OPS = sizeof ops / sizeof *ops };

static JavaVM *vm;
static jclass classes[OPS];
static jmethodID ids[OPS];
static jstring onload_vm_version, onload_backend;

static jobject boxed(JNIEnv *env, jboolean value)
{
    return (*env)->NewStringUTF(env, value ? "true" : "false");
}

static jobject call_v(JNIEnv *env, int op, jmethodID id, ...)
{
    va_list ap;
    va_start(ap, id);
    jobject result = NULL;
    if (ops[op].ret == 'Z') result = boxed(env, (*env)->CallStaticBooleanMethodV(env, classes[op], id, ap));
    else if (ops[op].ret == 'V') (*env)->CallStaticVoidMethodV(env, classes[op], id, ap);
    else result = (*env)->CallStaticObjectMethodV(env, classes[op], id, ap);
    va_end(ap);
    return result;
}

static jobject call_varargs(JNIEnv *env, int op, jmethodID id, const jvalue *a)
{
    jclass c = classes[op];
    switch (ops[op].ret) {
    case 'Z': return boxed(env, (*env)->CallStaticBooleanMethod(env, c, id, a[0].l));
    case 'V': (*env)->CallStaticVoidMethod(env, c, id, a[0].l); return NULL;
    }
    switch (ops[op].argc) {
    case 0: return (*env)->CallStaticObjectMethod(env, c, id);
    case 1: return (*env)->CallStaticObjectMethod(env, c, id, a[0].l);
    default: return (*env)->CallStaticObjectMethod(env, c, id, a[0].l, a[1].l);
    }
}

static jobject call_a(JNIEnv *env, int op, jmethodID id, const jvalue *a)
{
    if (ops[op].ret == 'Z') return boxed(env, (*env)->CallStaticBooleanMethodA(env, classes[op], id, a));
    if (ops[op].ret == 'V') { (*env)->CallStaticVoidMethodA(env, classes[op], id, a); return NULL; }
    return (*env)->CallStaticObjectMethodA(env, classes[op], id, a);
}

/* Method.invoke(null, args) called through JNI on the Method object Java passed */
static jobject call_invoke(JNIEnv *env, int op, jobject method, const jvalue *a)
{
    jclass object = (*env)->FindClass(env, "java/lang/Object");
    jclass reflected = (*env)->GetObjectClass(env, method);
    jmethodID invoke = (*env)->GetMethodID(env, reflected, "invoke", "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");
    jobjectArray args = (*env)->NewObjectArray(env, ops[op].argc, object, NULL);
    for (int i = 0; i < ops[op].argc; i++) (*env)->SetObjectArrayElement(env, args, i, a[i].l);
    jobject result = (*env)->CallObjectMethod(env, method, invoke, NULL, args);
    if (result && ops[op].ret == 'Z') {
        jclass b = (*env)->GetObjectClass(env, result);
        jmethodID value = (*env)->GetMethodID(env, b, "booleanValue", "()Z");
        result = boxed(env, (*env)->CallBooleanMethod(env, result, value));
    }
    return result;
}

static jobject dispatch(JNIEnv *env, int form, int op, jobject method, const jvalue *a)
{
    switch (form) {
    case FORM_VARARGS: return call_varargs(env, op, ids[op], a);
    case FORM_V: return call_v(env, op, ids[op], a[0].l, a[1].l);
    case FORM_A: return call_a(env, op, ids[op], a);
    case FORM_REFLECTED: return method ? call_a(env, op, (*env)->FromReflectedMethod(env, method), a) : NULL;
    case FORM_INVOKE: return method ? call_invoke(env, op, method, a) : NULL;
    }
    return NULL;
}

/* A thread this library creates, attached for the call and detached again; its result crosses as a global reference. */
struct attached { int op; jvalue a[2]; jobject result; jthrowable failure; };
static void *attached_thread(void *arg)
{
    struct attached *t = arg;
    JNIEnv *env;
    JavaVMAttachArgs attach = { JNI_VERSION_1_6, "jni-system-attached", NULL };
    if ((*vm)->AttachCurrentThread(vm, &env, &attach) != JNI_OK) return NULL;
    /* reads run in both forms on this thread (the A result is kept), a mutation once */
    if (ops[t->op].name[0] == 'g') call_varargs(env, t->op, ids[t->op], t->a);
    jobject second = call_a(env, t->op, ids[t->op], t->a);
    jthrowable failure = (*env)->ExceptionOccurred(env);
    (*env)->ExceptionClear(env);
    t->result = second ? (*env)->NewGlobalRef(env, second) : NULL;
    t->failure = failure ? (*env)->NewGlobalRef(env, failure) : NULL;
    (*vm)->DetachCurrentThread(vm);
    return NULL;
}

static jobject from_thread(JNIEnv *env, int op, const jvalue *a)
{
    struct attached t = { op, { a[0], a[1] }, NULL, NULL };
    for (int i = 0; i < 2; i++) if (t.a[i].l) t.a[i].l = (*env)->NewGlobalRef(env, t.a[i].l);
    pthread_t thread;
    if (pthread_create(&thread, NULL, attached_thread, &t) == 0) pthread_join(thread, NULL);
    for (int i = 0; i < 2; i++) if (t.a[i].l) (*env)->DeleteGlobalRef(env, t.a[i].l);
    if (t.failure) { (*env)->Throw(env, t.failure); (*env)->DeleteGlobalRef(env, t.failure); }
    jobject local = t.result ? (*env)->NewLocalRef(env, t.result) : NULL;
    if (t.result) (*env)->DeleteGlobalRef(env, t.result);
    return local;
}

JNIEXPORT jobject JNICALL Java_hello_environment_JniSystemChecks_call(JNIEnv *env, jclass cls, jint form, jint op, jstring first, jstring second, jobject method)
{
    (void)cls;
    if (op < 0 || op >= OPS) return NULL;
    jvalue a[2] = { { .l = first }, { .l = second } };
    if (ops[op].name[0] == 's' && !strcmp(ops[op].name, "setProperties")) a[0].l = method;  /* the Properties object */
    if (form == FORM_THREAD) return from_thread(env, op, a);
    return dispatch(env, form, op, ops[op].ret == 'V' ? NULL : method, a);
}

/* The JNI call in tail position: the guest frame is gone when the JNI function runs, its return address is the JVM's native wrapper. */
JNIEXPORT jobject JNICALL Java_hello_environment_JniSystemChecks_tailGetProperty(JNIEnv *env, jclass cls, jstring key)
{
    (void)cls;
    return (*env)->CallStaticObjectMethod(env, classes[0], ids[0], key);
}

/* Bound by RegisterNatives below rather than by its exported name. */
static jobject JNICALL registered(JNIEnv *env, jclass cls, jint form, jint op, jstring first, jstring second, jobject method)
{
    return Java_hello_environment_JniSystemChecks_call(env, cls, form, op, first, second, method);
}

static jobject JNICALL onload_values(JNIEnv *env, jclass cls)
{
    (void)cls;
    jclass object = (*env)->FindClass(env, "java/lang/Object");
    jobjectArray values = (*env)->NewObjectArray(env, 2, object, NULL);
    (*env)->SetObjectArrayElement(env, values, 0, onload_vm_version);
    (*env)->SetObjectArrayElement(env, values, 1, onload_backend);
    return values;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *jvm, void *reserved)
{
    (void)reserved;
    vm = jvm;
    JNIEnv *env;
    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    for (int i = 0; i < OPS; i++) {
        jclass c = (*env)->FindClass(env, ops[i].cls);
        if (!c) return JNI_ERR;
        classes[i] = (*env)->NewGlobalRef(env, c);
        ids[i] = (*env)->GetStaticMethodID(env, c, ops[i].name, ops[i].sig);
        if (!ids[i]) return JNI_ERR;
    }
    jstring key = (*env)->NewStringUTF(env, "java.vm.version");
    jobject value = (*env)->CallStaticObjectMethod(env, classes[0], ids[0], key);
    onload_vm_version = value ? (*env)->NewGlobalRef(env, value) : NULL;
    key = (*env)->NewStringUTF(env, "APKLAYER_NO_SYSTEM_SERVER");
    value = (*env)->CallStaticObjectMethod(env, classes[1], ids[1], key);
    onload_backend = value ? (*env)->NewGlobalRef(env, value) : NULL;
    jclass checks = (*env)->FindClass(env, "hello/environment/JniSystemChecks");
    if (!checks) return JNI_ERR;
    JNINativeMethod natives[] = {
        { "registered", "(IILjava/lang/String;Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;", (void *)registered },
        { "onloadValues", "()[Ljava/lang/Object;", (void *)onload_values },
    };
    if ((*env)->RegisterNatives(env, checks, natives, 2) != 0) return JNI_ERR;
    return JNI_VERSION_1_6;
}
