/* ART internals as native app code inspects them (handoff item 7): jmethodID/jfieldID values, the ArtMethod size from adjacent methods,
 * access flags, data_ and entry-point fields read at the ID (as Pine/SandHook/Epic/LSPlant do), JNI version, reference kinds, critical access
 * copies, JNI_GetCreatedJavaVMs, and FindClass from an attached native thread. Memory is read through peek() only. */
#include "identity.h"
#include <dlfcn.h>
#include <pthread.h>
#include <stdio.h>
#include <string.h>
#include <sys/wait.h>
#include <unistd.h>

static JavaVM *own_vm;

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved)
{
    (void)reserved;
    own_vm = vm;
    return JNI_VERSION_1_6;
}

/* ART 13 (64-bit) ArtMethod: declaring_class_ @0 (4), access_flags_ @4, dex_method_index_ @8, method_index_ @12, hotness @14,
 * data_ @16 (8), entry_point_from_quick_compiled_code_ @24 (8); 32 bytes. */
static void method_fields(struct buf *b, const char *tag, uintptr_t id, int modifiers, const void *native_fn)
{
    unsigned char m[64];
    char key[96], w[512];
    snprintf(key, sizeof key, "%s.read", tag);
    if (id < 4096 || peek(id, m, sizeof m)) { put(b, key, "unreadable"); return; }
    put(b, key, "ok");
    uint32_t flags, dex_index;
    uint64_t data, entry;
    memcpy(&flags, m + 4, 4); memcpy(&dex_index, m + 8, 4); memcpy(&data, m + 16, 8); memcpy(&entry, m + 24, 8);
    snprintf(key, sizeof key, "%s.accessFlags@4", tag);
    put(b, key, "0x%x low16match=%d", flags, (int)((flags & 0xffff) == (uint32_t)(modifiers & 0xffff)));
    int found = -1;
    for (int off = 0; off + 4 <= 64; off += 4) {
        uint32_t v;
        memcpy(&v, m + off, 4);
        if ((v & 0xffff) == (uint32_t)(modifiers & 0xffff) && v != 0 && found < 0) found = off;
    }
    snprintf(key, sizeof key, "%s.accessFlagsScan", tag);
    put(b, key, "%d", found);
    snprintf(key, sizeof key, "%s.dexMethodIndex@8", tag);
    put(b, key, "%u", dex_index);
    snprintf(key, sizeof key, "%s.data@16", tag);
    put(b, key, "%s", native_fn ? (data == (uintptr_t)native_fn ? "== JNI function" : where((void *)(uintptr_t)data, w, sizeof w))
                                : where((void *)(uintptr_t)data, w, sizeof w));
    snprintf(key, sizeof key, "%s.entry@24", tag);
    put(b, key, "%s", where((void *)(uintptr_t)entry, w, sizeof w));
}

static void ids(struct buf *b, JNIEnv *env, jobject ra, jobject rb, jobject f1, jobject f2, jlong art_a, jlong art_b, jint mods)
{
    jmethodID a = (*env)->FromReflectedMethod(env, ra), m2 = (*env)->FromReflectedMethod(env, rb);
    jfieldID fa = (*env)->FromReflectedField(env, f1), fb = (*env)->FromReflectedField(env, f2);
    uintptr_t ia = (uintptr_t)a, ib = (uintptr_t)m2;
    put(b, "jmethodID.shape", "%s low_bit=%d aligned8=%d", ia < 0x100000 ? "small(index?)" : "pointer", (int)(ia & 1), (int)((ia & 7) == 0));
    put(b, "jmethodID.delta", "%ld", (long)(ib - ia));
    put(b, "jmethodID.equalsArtMethod", "%s", art_a == -1 ? "artMethod unreadable" : (ia == (uintptr_t)art_a ? "yes" : "no"));
    if (art_a == -1) put(b, "artMethod.delta", "unreadable");
    else put(b, "artMethod.delta", "%ld", (long)(art_b - art_a));
    put(b, "jfieldID.shape", "%s low_bit=%d", (uintptr_t)fa < 0x100000 ? "small(index?)" : "pointer", (int)((uintptr_t)fa & 1));
    put(b, "jfieldID.delta", "%ld", (long)((uintptr_t)fb - (uintptr_t)fa));
    jclass adjacent = (*env)->GetObjectClass(env, ra) ? (*env)->FindClass(env, "hello/identity/ArtProbes$Adjacent") : NULL;
    if (adjacent) {
        jmethodID again = (*env)->GetStaticMethodID(env, adjacent, "a", "()V");
        put(b, "jmethodID.stable", "%d", again == a);
    } else (*env)->ExceptionClear(env);
    /* the ids are usable as handed out: static and virtual calls through GetStaticMethodID/GetMethodID, ToReflectedMethod of FromReflectedMethod's id */
    jclass integer = (*env)->FindClass(env, "java/lang/Integer"), string = (*env)->FindClass(env, "java/lang/String");
    jmethodID parse = integer ? (*env)->GetStaticMethodID(env, integer, "parseInt", "(Ljava/lang/String;)I") : NULL;
    jmethodID length = string ? (*env)->GetMethodID(env, string, "length", "()I") : NULL;
    jstring forty_two = (*env)->NewStringUTF(env, "42");
    jobject back = adjacent ? (*env)->ToReflectedMethod(env, adjacent, a, JNI_TRUE) : NULL;
    put(b, "jmethodID.callThrough", "parseInt=%d length=%d toReflected=%d", parse ? (*env)->CallStaticIntMethod(env, integer, parse, forty_two) : -1,
        length ? (*env)->CallIntMethod(env, forty_two, length) : -1, back != NULL);
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    method_fields(b, "artMethodAtID", ia, mods, NULL);
    /* a registered native method: ART keeps its JNI function in data_ */
    jclass main = (*env)->FindClass(env, "hello/identity/Main");
    if (main) {
        jmethodID native = (*env)->GetStaticMethodID(env, main, "proc", "()Ljava/lang/String;");
        extern jstring Java_hello_identity_Main_proc(JNIEnv *, jclass);
        method_fields(b, "nativeMethodAtID", (uintptr_t)native, 0x0108 /* static native */, (const void *)Java_hello_identity_Main_proc);
    } else (*env)->ExceptionClear(env);
}

static void jni(struct buf *b, JNIEnv *env)
{
    put(b, "GetVersion", "0x%x", (*env)->GetVersion(env));
    jstring s = (*env)->NewStringUTF(env, "abc");
    jobject g = (*env)->NewGlobalRef(env, s), w = (*env)->NewWeakGlobalRef(env, s);
    put(b, "refs.lowBits", "local=%d global=%d weak=%d", (int)((uintptr_t)s & 7), (int)((uintptr_t)g & 7), (int)((uintptr_t)w & 7));
    put(b, "refs.types", "local=%d global=%d weak=%d", (*env)->GetObjectRefType(env, s), (*env)->GetObjectRefType(env, g), (*env)->GetObjectRefType(env, w));
    jboolean copy = 2;
    const jchar *c = (*env)->GetStringCritical(env, s, &copy);
    (*env)->ReleaseStringCritical(env, s, c);
    int critical = copy;
    copy = 2;
    const jchar *chars = (*env)->GetStringChars(env, s, &copy);
    (*env)->ReleaseStringChars(env, s, chars);
    int string_chars = copy;
    copy = 2;
    const char *utf = (*env)->GetStringUTFChars(env, s, &copy);
    (*env)->ReleaseStringUTFChars(env, s, utf);
    put(b, "isCopy.string", "critical=%d chars=%d utf=%d", critical, string_chars, copy);
    jintArray arr = (*env)->NewIntArray(env, 16);
    copy = 2;
    void *p = (*env)->GetPrimitiveArrayCritical(env, arr, &copy);
    (*env)->ReleasePrimitiveArrayCritical(env, arr, p, 0);
    critical = copy;
    copy = 2;
    jint *e = (*env)->GetIntArrayElements(env, arr, &copy);
    (*env)->ReleaseIntArrayElements(env, arr, e, 0);
    put(b, "isCopy.intArray", "critical=%d elements=%d", critical, copy);
    jintArray big = (*env)->NewIntArray(env, 1 << 18);  /* 1 MB: ART puts it in the large-object space (non-moving) */
    copy = 2;
    e = (*env)->GetIntArrayElements(env, big, &copy);
    (*env)->ReleaseIntArrayElements(env, big, e, 0);
    put(b, "isCopy.largeIntArray", "elements=%d", copy);
    (*env)->DeleteGlobalRef(env, g);
    (*env)->DeleteWeakGlobalRef(env, w);

    JavaVM *vm = NULL;
    (*env)->GetJavaVM(env, &vm);
    put(b, "JavaVM.sameAsOnLoad", "%d", vm == own_vm);
    typedef jint (*created_fn)(JavaVM **, jsize, jsize *);
    created_fn global = (created_fn)dlsym(RTLD_DEFAULT, "JNI_GetCreatedJavaVMs");
    void *helper = dlopen("libnativehelper.so", RTLD_NOW);
    created_fn nh = helper ? (created_fn)dlsym(helper, "JNI_GetCreatedJavaVMs") : NULL;
    void *art = dlopen("libart.so", RTLD_NOW);
    created_fn fromart = art ? (created_fn)dlsym(art, "JNI_GetCreatedJavaVMs") : NULL;
    created_fn fns[] = {global, nh, fromart};
    const char *names[] = {"JNI_GetCreatedJavaVMs.default", "JNI_GetCreatedJavaVMs.libnativehelper", "JNI_GetCreatedJavaVMs.libart"};
    for (int i = 0; i < 3; i++) {
        if (!fns[i]) { put(b, names[i], "no symbol"); continue; }
        /* in a child process: berberis' trampoline CHECK-aborts when the host reports no VM */
        int fds[2];
        char result[600] = "";
        if (pipe(fds)) { put(b, names[i], "pipe failed"); continue; }
        pid_t child = fork();
        if (child == 0) {
            JavaVM *vms[4] = {0};
            jsize n = -1;
            jint r = fns[i](vms, 4, &n);
            char w[512], line[600];
            int len = snprintf(line, sizeof line, "ret=%d count=%d same=%d at=%s", r, n, n > 0 && vms[0] == vm, where((void *)fns[i], w, sizeof w));
            if (write(fds[1], line, (size_t)len) < 0) _exit(2);
            _exit(0);
        }
        close(fds[1]);
        ssize_t got = child > 0 ? read(fds[0], result, sizeof result - 1) : -1;
        close(fds[0]);
        int status = 0;
        if (child > 0) waitpid(child, &status, 0);
        if (got > 0) result[got] = 0;
        if (child < 0) put(b, names[i], "fork failed");
        else if (WIFSIGNALED(status)) put(b, names[i], "CRASH signal %d (in a forked child)", WTERMSIG(status));
        else put(b, names[i], "%s", result);
    }
    /* the rest of libnativehelper's invocation API: ART fails both (JNI_ERR), in a child too (an unbound table aborts) */
    typedef jint (*default_fn)(void *);
    typedef jint (*create_fn)(JavaVM **, JNIEnv **, void *);
    default_fn defaults = helper ? (default_fn)dlsym(helper, "JNI_GetDefaultJavaVMInitArgs") : NULL;
    create_fn create = helper ? (create_fn)dlsym(helper, "JNI_CreateJavaVM") : NULL;
    for (int i = 0; i < 2; i++) {
        const char *name = i ? "JNI_CreateJavaVM.libnativehelper" : "JNI_GetDefaultJavaVMInitArgs.libnativehelper";
        if (!(i ? (void *)create : (void *)defaults)) { put(b, name, "no symbol"); continue; }
        pid_t child = fork();
        if (child == 0) {
            JavaVMInitArgs args = { JNI_VERSION_1_6, 0, NULL, JNI_FALSE };
            JavaVM *new_vm = NULL;
            JNIEnv *new_env = NULL;
            jint r = i ? create(&new_vm, &new_env, &args) : defaults(&args);
            _exit(r & 0xff);
        }
        int status = 0;
        if (child > 0) waitpid(child, &status, 0);
        if (child < 0) put(b, name, "fork failed");
        else if (WIFSIGNALED(status)) put(b, name, "CRASH signal %d (in a forked child)", WTERMSIG(status));
        else put(b, name, "ret=%d", (int)(signed char)WEXITSTATUS(status));
    }
    if (helper) dlclose(helper);
    if (art) dlclose(art);
}

JNIEXPORT jstring JNICALL Java_hello_identity_Main_art(JNIEnv *env, jclass cls, jobject a, jobject b2, jobject f1, jobject f2, jlong art_a, jlong art_b,
                                                       jint mods)
{
    (void)cls;
    struct buf b = {0};
    ids(&b, env, a, b2, f1, f2, art_a, art_b, mods);
    jni(&b, env);
    return finish(env, &b);
}

struct attached { struct buf *b; };

static void *thread_main(void *arg)
{
    struct buf *b = ((struct attached *)arg)->b;
    JNIEnv *env = NULL;
    put(b, "GetEnv.beforeAttach", "%d", (*own_vm)->GetEnv(own_vm, (void **)&env, JNI_VERSION_1_6));
    if ((*own_vm)->AttachCurrentThread(own_vm, &env, NULL) != JNI_OK) { put(b, "attach", "failed"); return NULL; }
    jclass app = (*env)->FindClass(env, "hello/identity/Main");
    if (app) put(b, "FindClass.appClass", "found");
    else {
        jthrowable t = (*env)->ExceptionOccurred(env);
        (*env)->ExceptionClear(env);
        jclass tc = (*env)->GetObjectClass(env, t);
        jmethodID ts = (*env)->GetMethodID(env, (*env)->FindClass(env, "java/lang/Object"), "toString", "()Ljava/lang/String;");
        jstring text = (jstring)(*env)->CallObjectMethod(env, t, ts);
        const char *chars = text ? (*env)->GetStringUTFChars(env, text, NULL) : NULL;
        char msg[300];
        snprintf(msg, sizeof msg, "%s", chars ? chars : "?");
        if (chars) (*env)->ReleaseStringUTFChars(env, text, chars);
        (void)tc;
        put(b, "FindClass.appClass", "NOT FOUND %s", msg);
    }
    jclass thread = (*env)->FindClass(env, "java/lang/Thread");
    jobject current = (*env)->CallStaticObjectMethod(env, thread, (*env)->GetStaticMethodID(env, thread, "currentThread", "()Ljava/lang/Thread;"));
    jstring name = (jstring)(*env)->CallObjectMethod(env, current, (*env)->GetMethodID(env, thread, "getName", "()Ljava/lang/String;"));
    jobject group = (*env)->CallObjectMethod(env, current, (*env)->GetMethodID(env, thread, "getThreadGroup", "()Ljava/lang/ThreadGroup;"));
    jclass tg = (*env)->FindClass(env, "java/lang/ThreadGroup");
    jstring gname = (jstring)(*env)->CallObjectMethod(env, group, (*env)->GetMethodID(env, tg, "getName", "()Ljava/lang/String;"));
    const char *n = (*env)->GetStringUTFChars(env, name, NULL), *g = (*env)->GetStringUTFChars(env, gname, NULL);
    char comm[32] = "";
    pthread_getname_np(pthread_self(), comm, sizeof comm);
    put(b, "attachedThread", "name=%s group=%s comm=%s", n, g, comm);
    (*env)->ReleaseStringUTFChars(env, name, n);
    (*env)->ReleaseStringUTFChars(env, gname, g);
    (*own_vm)->DetachCurrentThread(own_vm);
    return NULL;
}

JNIEXPORT jstring JNICALL Java_hello_identity_Main_jniThread(JNIEnv *env, jclass cls)
{
    (void)cls;
    struct buf b = {0};
    struct attached a = {&b};
    pthread_t t;
    if (!own_vm) put(&b, "JNI_OnLoad", "not called");
    else if (pthread_create(&t, NULL, thread_main, &a) == 0) pthread_join(t, NULL);
    return finish(env, &b);
}
