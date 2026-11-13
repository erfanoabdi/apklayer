/* Guest native code calling java.lang.System's property and environment methods through JNI (System.getProperty/getProperties/
 * setProperty/clearProperty/setProperties/getenv, Boolean.getBoolean, Integer.getInteger, Long.getLong) reaches the app's views
 * (GuestSystemProperties, GuestEnvironment), as the app's Java code does after its DEX transformation; host callers keep the JVM's.
 * The real jmethodIDs stay: GetStaticMethodID and FromReflectedMethod hand out the JVM's own IDs, and the static Call*Method entries
 * (plain, V, A) of the JNI function table the shim installs swap a matching ID for its replacement's (same signature, so the arguments
 * pass unchanged). Method.invoke called through JNI on one of these methods goes through apklayer.host.Reflect.invoke.
 * The caller: guest code is what bionic_translation's linker loaded (an app's or Android's library, or the native bridge that runs
 * translated code: on x86_64 Berberis calls JNI for its guest). The return address of the JNI call is classified: in a glibc-loaded
 * module (the JVM, the shim, JDK libraries) it is the host's; in a bionic-loaded one, the guest's; anywhere else (JIT code, the JVM's
 * native wrappers: a guest JNI call in tail position returns there) the host's. Host control reads keep the host values. */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <jni.h>
#include <jvmti.h>  /* jniNativeInterface */
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>

extern int bionic_dladdr(const void *addr, Dl_info *info);

struct route { jmethodID original, replacement; };
static struct route *routes;
static jint route_count;
static jclass *owners;           /* each replacement's class, passed with its ID */
static jclass reflect_class;     /* apklayer.host.Reflect */
static jmethodID method_invoke, reflect_invoke;
static jniNativeInterface next;  /* the table under this layer */
static int debug;

static int guest_caller(const void *pc)
{
    Dl_info info;
    if (dladdr(pc, &info)) return 0;      /* a host module */
    return bionic_dladdr(pc, &info);      /* a bionic-loaded one (its host fallback finds nothing either) */
}

static jint find(jmethodID m)
{
    for (jint i = 0; i < route_count; i++) if (routes[i].original == m) return i;
    return -1;
}

/* the route for this call, or -1: a routed ID called from guest code */
static jint route(jmethodID m, const void *pc)
{
    jint i = find(m);
    if (i < 0) return -1;
    int guest = guest_caller(pc);
    if (debug) {
        Dl_info info = {0};
        if (!dladdr(pc, &info)) bionic_dladdr(pc, &info);
        fprintf(stderr, "[jni-route] route %d from %p (%s): %s\n", i, pc, info.dli_fname ? info.dli_fname : "no module", guest ? "guest" : "host");
    }
    return guest ? i : -1;
}

#define STATIC_CALLS(Type, type) \
static type JNICALL static_##Type##V(JNIEnv *env, jclass c, jmethodID m, va_list ap) \
{ \
    jint r = route(m, __builtin_return_address(0)); \
    return r < 0 ? next.CallStatic##Type##MethodV(env, c, m, ap) : next.CallStatic##Type##MethodV(env, owners[r], routes[r].replacement, ap); \
} \
static type JNICALL static_##Type##A(JNIEnv *env, jclass c, jmethodID m, const jvalue *a) \
{ \
    jint r = route(m, __builtin_return_address(0)); \
    return r < 0 ? next.CallStatic##Type##MethodA(env, c, m, a) : next.CallStatic##Type##MethodA(env, owners[r], routes[r].replacement, a); \
} \
static type JNICALL static_##Type(JNIEnv *env, jclass c, jmethodID m, ...) \
{ \
    va_list ap; \
    va_start(ap, m); \
    jint r = route(m, __builtin_return_address(0)); \
    type v = r < 0 ? next.CallStatic##Type##MethodV(env, c, m, ap) : next.CallStatic##Type##MethodV(env, owners[r], routes[r].replacement, ap); \
    va_end(ap); \
    return v; \
}
STATIC_CALLS(Object, jobject)
STATIC_CALLS(Boolean, jboolean)

static void JNICALL static_VoidV(JNIEnv *env, jclass c, jmethodID m, va_list ap)
{
    jint r = route(m, __builtin_return_address(0));
    if (r < 0) next.CallStaticVoidMethodV(env, c, m, ap);
    else next.CallStaticVoidMethodV(env, owners[r], routes[r].replacement, ap);
}
static void JNICALL static_VoidA(JNIEnv *env, jclass c, jmethodID m, const jvalue *a)
{
    jint r = route(m, __builtin_return_address(0));
    if (r < 0) next.CallStaticVoidMethodA(env, c, m, a);
    else next.CallStaticVoidMethodA(env, owners[r], routes[r].replacement, a);
}
static void JNICALL static_Void(JNIEnv *env, jclass c, jmethodID m, ...)
{
    va_list ap;
    va_start(ap, m);
    jint r = route(m, __builtin_return_address(0));
    if (r < 0) next.CallStaticVoidMethodV(env, c, m, ap);
    else next.CallStaticVoidMethodV(env, owners[r], routes[r].replacement, ap);
    va_end(ap);
}

/* Method.invoke(receiver, args) on a routed Method, from guest code: Reflect.invoke(method, receiver, args), as app Java's reflection */
static int invoke_routed(JNIEnv *env, jobject method, jmethodID m, const void *pc)
{
    if (m != method_invoke || !method) return 0;
    jmethodID target = next.FromReflectedMethod(env, method);
    if (!target || find(target) < 0) { next.ExceptionClear(env); return 0; }
    return route(target, pc) >= 0;
}
static jobject JNICALL invoke_A(JNIEnv *env, jobject o, jmethodID m, const jvalue *a)
{
    if (!invoke_routed(env, o, m, __builtin_return_address(0))) return next.CallObjectMethodA(env, o, m, a);
    jvalue forwarded[3] = { { .l = o }, a[0], a[1] };
    return next.CallStaticObjectMethodA(env, reflect_class, reflect_invoke, forwarded);
}
static jobject JNICALL invoke_V(JNIEnv *env, jobject o, jmethodID m, va_list ap)
{
    if (!invoke_routed(env, o, m, __builtin_return_address(0))) return next.CallObjectMethodV(env, o, m, ap);
    jvalue forwarded[3] = { { .l = o } };
    forwarded[1].l = va_arg(ap, jobject);
    forwarded[2].l = va_arg(ap, jobject);
    return next.CallStaticObjectMethodA(env, reflect_class, reflect_invoke, forwarded);
}
static jobject JNICALL invoke_plain(JNIEnv *env, jobject o, jmethodID m, ...)
{
    va_list ap;
    va_start(ap, m);
    jobject v;
    if (!invoke_routed(env, o, m, __builtin_return_address(0))) v = next.CallObjectMethodV(env, o, m, ap);
    else {
        jvalue forwarded[3] = { { .l = o } };
        forwarded[1].l = va_arg(ap, jobject);
        forwarded[2].l = va_arg(ap, jobject);
        v = next.CallStaticObjectMethodA(env, reflect_class, reflect_invoke, forwarded);
    }
    va_end(ap);
    return v;
}

int apklayer_route_system_calls(JNIEnv *env, jclass reflect, jniNativeInterface *table)
{
    const char *d = getenv("APKLAYER_JNI_ROUTE_DEBUG");
    debug = d && *d == '1';
    jmethodID pairs_of = (*env)->GetStaticMethodID(env, reflect, "jniRoutes", "()[Ljava/lang/reflect/Method;");
    reflect_invoke = (*env)->GetStaticMethodID(env, reflect, "invoke",
        "(Ljava/lang/reflect/Method;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");
    jclass method_class = (*env)->FindClass(env, "java/lang/reflect/Method");
    method_invoke = method_class ? (*env)->GetMethodID(env, method_class, "invoke", "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;") : NULL;
    jobjectArray pairs = pairs_of ? (*env)->CallStaticObjectMethod(env, reflect, pairs_of) : NULL;
    if (!reflect_invoke || !method_invoke || !pairs) return -1;
    jsize n = (*env)->GetArrayLength(env, pairs) / 2;
    routes = calloc((size_t)n, sizeof *routes);
    owners = calloc((size_t)n, sizeof *owners);
    if (!routes || !owners) return -1;
    jmethodID declaring = (*env)->GetMethodID(env, method_class, "getDeclaringClass", "()Ljava/lang/Class;");
    for (jsize i = 0; i < n; i++) {
        jobject original = (*env)->GetObjectArrayElement(env, pairs, 2 * i), replacement = (*env)->GetObjectArrayElement(env, pairs, 2 * i + 1);
        jobject owner = (*env)->CallObjectMethod(env, replacement, declaring);
        routes[i].original = (*env)->FromReflectedMethod(env, original);
        routes[i].replacement = (*env)->FromReflectedMethod(env, replacement);
        owners[i] = owner ? (*env)->NewGlobalRef(env, owner) : NULL;
        if (!routes[i].original || !routes[i].replacement || !owners[i]) return -1;
        (*env)->DeleteLocalRef(env, original); (*env)->DeleteLocalRef(env, replacement); (*env)->DeleteLocalRef(env, owner);
    }
    route_count = n;
    reflect_class = (*env)->NewGlobalRef(env, reflect);
    next = *table;
    table->CallStaticObjectMethod = static_Object; table->CallStaticObjectMethodV = static_ObjectV; table->CallStaticObjectMethodA = static_ObjectA;
    table->CallStaticBooleanMethod = static_Boolean; table->CallStaticBooleanMethodV = static_BooleanV; table->CallStaticBooleanMethodA = static_BooleanA;
    table->CallStaticVoidMethod = static_Void; table->CallStaticVoidMethodV = static_VoidV; table->CallStaticVoidMethodA = static_VoidA;
    table->CallObjectMethod = invoke_plain; table->CallObjectMethodV = invoke_V; table->CallObjectMethodA = invoke_A;
    if (debug) fprintf(stderr, "[jni-route] %d System/Boolean/Integer/Long methods routed for guest callers\n", (int)n);
    return 0;
}
