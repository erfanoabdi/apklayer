/* Diagnostic (APKLAYER_JNI_TRACE=1): the JNI calls guest code makes, on stderr (the app's log) as "[jni-trace] ...". For finding what an
 * engine asks of Java and what it gets back (Tencent ACE starting its GP7Service: docs/PLAN.md "Native bridge (x86_64)"). Traced, for
 * guest callers only (jni_system_route.c's classification): FindClass, Get(Static)MethodID, NewObject, NewStringUTF and the common
 * Call(Static)<Object|Void|Boolean|Int>Method forms; each call names the class and method, and an exception it leaves pending (class and
 * message). "attached" marks a thread with no Java frame (one the native code attached itself). Each distinct call is logged three
 * times at most, 6000 lines in all. Installed over the JNI function table the JVM has when the native bridge starts (native_bridge.c). */
#define _GNU_SOURCE
#include <jni.h>
#include <jvmti.h>
#include <pthread.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/syscall.h>

int apklayer_jni_guest_caller(JNIEnv *env, const void *pc);  /* jni_system_route.c */

static jniNativeInterface next;  /* the table under this layer */
static jvmtiEnv *ti;
static jmethodID to_string;      /* Throwable.toString */
static pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;
static int lines;

/* --- seen-counts of distinct calls (open addressing on a 64-bit key) --- */

#define SEEN_SLOTS 16384
static uint64_t seen_key[SEEN_SLOTS];
static unsigned char seen_count[SEEN_SLOTS];

static uint64_t mix(uint64_t h, uint64_t v) { h ^= v + 0x9e3779b97f4a7c15ull + (h << 6) + (h >> 2); return h ? h : 1; }
static uint64_t hash_str(uint64_t h, const char *s) { while (s && *s) h = mix(h, (unsigned char)*s++); return h; }

/* whether this call is still to be logged (a lock held by the caller) */
static int fresh(uint64_t key)
{
    if (lines >= 6000) return 0;
    for (uint64_t i = key % SEEN_SLOTS, n = 0; n < SEEN_SLOTS; i = (i + 1) % SEEN_SLOTS, n++) {
        if (seen_key[i] == key) return seen_count[i] < 3 ? ++seen_count[i] : 0;
        if (!seen_key[i]) { seen_key[i] = key; seen_count[i] = 1; return 1; }
    }
    return 0;
}

/* already logged three times (read without the lock: a stale answer only logs or skips one more line) */
static int saturated(uint64_t key)
{
    for (uint64_t i = key % SEEN_SLOTS, n = 0; n < SEEN_SLOTS; i = (i + 1) % SEEN_SLOTS, n++) {
        if (seen_key[i] == key) return seen_count[i] >= 3;
        if (!seen_key[i]) return 0;
    }
    return 1;
}

static int attached(void)
{
    jint frames = -1;
    return (*ti)->GetFrameCount(ti, NULL, &frames) == JVMTI_ERROR_NONE && frames == 0;
}

static void emit(JNIEnv *env, uint64_t key, const char *what)
{
    (void)env;
    pthread_mutex_lock(&lock);
    int log = fresh(key);
    if (log) lines++;
    pthread_mutex_unlock(&lock);
    if (!log) return;
    char name[16] = "";
    pthread_getname_np(pthread_self(), name, sizeof name);
    fprintf(stderr, "[jni-trace] tid %ld (%s)%s %s\n", (long)syscall(SYS_gettid), name, attached() ? " attached" : "", what);
}

/* "Lpkg/Class;.name(sig)" of a method ID */
static void method_text(jmethodID m, char *out, size_t size)
{
    char *mname = NULL, *msig = NULL, *csig = NULL;
    jclass c = NULL;
    (*ti)->GetMethodName(ti, m, &mname, &msig, NULL);
    (*ti)->GetMethodDeclaringClass(ti, m, &c);
    if (c) (*ti)->GetClassSignature(ti, c, &csig, NULL);
    snprintf(out, size, "%s.%s%s", csig ? csig : "?", mname ? mname : "?", msig ? msig : "");
    if (mname) (*ti)->Deallocate(ti, (unsigned char *)mname);
    if (msig) (*ti)->Deallocate(ti, (unsigned char *)msig);
    if (csig) (*ti)->Deallocate(ti, (unsigned char *)csig);
}

static void class_text(jclass c, char *out, size_t size)
{
    char *csig = NULL;
    if (c) (*ti)->GetClassSignature(ti, c, &csig, NULL);
    snprintf(out, size, "%s", csig ? csig : "null");
    if (csig) (*ti)->Deallocate(ti, (unsigned char *)csig);
}

/* " -> <exception text>" when the call left one pending (it stays pending for the caller), else "" */
static void pending_text(JNIEnv *env, char *out, size_t size)
{
    out[0] = 0;
    jthrowable ex = next.ExceptionOccurred(env);
    if (!ex) return;
    next.ExceptionClear(env);
    jstring s = to_string ? (jstring)next.CallObjectMethod(env, ex, to_string) : NULL;
    if (next.ExceptionCheck(env)) next.ExceptionClear(env);
    const char *chars = s ? next.GetStringUTFChars(env, s, NULL) : NULL;
    snprintf(out, size, " -> THROWS %s", chars ? chars : "(no text)");
    if (chars) next.ReleaseStringUTFChars(env, s, chars);
    if (s) next.DeleteLocalRef(env, s);
    next.Throw(env, ex);
    next.DeleteLocalRef(env, ex);
}

static void trace_method(JNIEnv *env, const void *pc, const char *kind, jmethodID m, const char *result)
{
    uint64_t key = hash_str(mix(1, (uintptr_t)m), kind);
    if (lines >= 6000 || (saturated(key) && saturated(key ^ 2)) || !apklayer_jni_guest_caller(env, pc)) return;
    char text[600], ex[400], line[1100];
    method_text(m, text, sizeof text);
    pending_text(env, ex, sizeof ex);
    snprintf(line, sizeof line, "%s %s%s%s", kind, text, result ? result : "", ex);
    emit(env, key ^ (ex[0] ? 2 : 0), line);
}

/* --- lookups --- */

static jclass JNICALL t_find_class(JNIEnv *env, const char *name)
{
    const void *pc = __builtin_return_address(0);
    jclass c = next.FindClass(env, name);
    if (apklayer_jni_guest_caller(env, pc)) {
        char ex[400], line[700];
        pending_text(env, ex, sizeof ex);
        snprintf(line, sizeof line, "FindClass %s%s%s", name ? name : "(null)", c ? "" : " = null", ex);
        emit(env, hash_str(3, name), line);
    }
    return c;
}

static jmethodID lookup(JNIEnv *env, const void *pc, jclass c, const char *name, const char *sig, int is_static)
{
    jmethodID m = is_static ? next.GetStaticMethodID(env, c, name, sig) : next.GetMethodID(env, c, name, sig);
    if (apklayer_jni_guest_caller(env, pc)) {
        char cls[300], ex[400], line[1100];
        class_text(c, cls, sizeof cls);
        pending_text(env, ex, sizeof ex);
        snprintf(line, sizeof line, "%s %s.%s%s%s%s", is_static ? "GetStaticMethodID" : "GetMethodID", cls, name ? name : "?", sig ? sig : "?", m ? "" : " = null", ex);
        emit(env, hash_str(hash_str(hash_str(5 + is_static, cls), name), sig), line);
    }
    return m;
}
static jmethodID JNICALL t_get_method(JNIEnv *env, jclass c, const char *n, const char *s) { return lookup(env, __builtin_return_address(0), c, n, s, 0); }
static jmethodID JNICALL t_get_static_method(JNIEnv *env, jclass c, const char *n, const char *s) { return lookup(env, __builtin_return_address(0), c, n, s, 1); }

static jstring JNICALL t_new_string_utf(JNIEnv *env, const char *utf)
{
    const void *pc = __builtin_return_address(0);
    jstring s = next.NewStringUTF(env, utf);
    if (utf && apklayer_jni_guest_caller(env, pc)) {
        char line[400];
        snprintf(line, sizeof line, "NewStringUTF \"%.300s\"", utf);
        emit(env, hash_str(7, utf), line);
    }
    return s;
}

/* --- object creation and calls --- */

static jobject JNICALL t_new_object_v(JNIEnv *env, jclass c, jmethodID m, va_list ap)
{
    jobject o = next.NewObjectV(env, c, m, ap);
    trace_method(env, __builtin_return_address(0), "NewObject", m, o ? NULL : " = null");
    return o;
}
static jobject JNICALL t_new_object_a(JNIEnv *env, jclass c, jmethodID m, const jvalue *a)
{
    jobject o = next.NewObjectA(env, c, m, a);
    trace_method(env, __builtin_return_address(0), "NewObject", m, o ? NULL : " = null");
    return o;
}
static jobject JNICALL t_new_object(JNIEnv *env, jclass c, jmethodID m, ...)
{
    va_list ap;
    va_start(ap, m);
    jobject o = next.NewObjectV(env, c, m, ap);
    va_end(ap);
    trace_method(env, __builtin_return_address(0), "NewObject", m, o ? NULL : " = null");
    return o;
}

/* Call<Type>Method(V|A) and CallStatic<Type>Method(V|A): the call, then its trace with an object result's null-ness or an int's value */
#define RESULT_object(r, buf) ((r) ? NULL : " = null")
#define RESULT_bool(r, buf) ((r) ? " = true" : " = false")
#define RESULT_int(r, buf) (snprintf(buf, sizeof buf, " = %d", (int)(r)), buf)
#define CALLS(Type, type, res, Inst, Static) \
static type JNICALL t_call_##Type##_v(JNIEnv *env, jobject o, jmethodID m, va_list ap) \
{ type r = next.Call##Type##MethodV(env, o, m, ap); char b[24] __attribute__((unused)); trace_method(env, __builtin_return_address(0), "Call", m, RESULT_##res(r, b)); return r; } \
static type JNICALL t_call_##Type##_a(JNIEnv *env, jobject o, jmethodID m, const jvalue *a) \
{ type r = next.Call##Type##MethodA(env, o, m, a); char b[24] __attribute__((unused)); trace_method(env, __builtin_return_address(0), "Call", m, RESULT_##res(r, b)); return r; } \
static type JNICALL t_call_##Type(JNIEnv *env, jobject o, jmethodID m, ...) \
{ va_list ap; va_start(ap, m); type r = next.Call##Type##MethodV(env, o, m, ap); va_end(ap); char b[24] __attribute__((unused)); trace_method(env, __builtin_return_address(0), "Call", m, RESULT_##res(r, b)); return r; } \
static type JNICALL t_scall_##Type##_v(JNIEnv *env, jclass c, jmethodID m, va_list ap) \
{ type r = next.CallStatic##Type##MethodV(env, c, m, ap); char b[24] __attribute__((unused)); trace_method(env, __builtin_return_address(0), "CallStatic", m, RESULT_##res(r, b)); return r; } \
static type JNICALL t_scall_##Type##_a(JNIEnv *env, jclass c, jmethodID m, const jvalue *a) \
{ type r = next.CallStatic##Type##MethodA(env, c, m, a); char b[24] __attribute__((unused)); trace_method(env, __builtin_return_address(0), "CallStatic", m, RESULT_##res(r, b)); return r; } \
static type JNICALL t_scall_##Type(JNIEnv *env, jclass c, jmethodID m, ...) \
{ va_list ap; va_start(ap, m); type r = next.CallStatic##Type##MethodV(env, c, m, ap); va_end(ap); char b[24] __attribute__((unused)); trace_method(env, __builtin_return_address(0), "CallStatic", m, RESULT_##res(r, b)); return r; }
CALLS(Object, jobject, object, , )
CALLS(Boolean, jboolean, bool, , )
CALLS(Int, jint, int, , )

static void JNICALL t_call_Void_v(JNIEnv *env, jobject o, jmethodID m, va_list ap) { next.CallVoidMethodV(env, o, m, ap); trace_method(env, __builtin_return_address(0), "Call", m, NULL); }
static void JNICALL t_call_Void_a(JNIEnv *env, jobject o, jmethodID m, const jvalue *a) { next.CallVoidMethodA(env, o, m, a); trace_method(env, __builtin_return_address(0), "Call", m, NULL); }
static void JNICALL t_call_Void(JNIEnv *env, jobject o, jmethodID m, ...)
{ va_list ap; va_start(ap, m); next.CallVoidMethodV(env, o, m, ap); va_end(ap); trace_method(env, __builtin_return_address(0), "Call", m, NULL); }
static void JNICALL t_scall_Void_v(JNIEnv *env, jclass c, jmethodID m, va_list ap) { next.CallStaticVoidMethodV(env, c, m, ap); trace_method(env, __builtin_return_address(0), "CallStatic", m, NULL); }
static void JNICALL t_scall_Void_a(JNIEnv *env, jclass c, jmethodID m, const jvalue *a) { next.CallStaticVoidMethodA(env, c, m, a); trace_method(env, __builtin_return_address(0), "CallStatic", m, NULL); }
static void JNICALL t_scall_Void(JNIEnv *env, jclass c, jmethodID m, ...)
{ va_list ap; va_start(ap, m); next.CallStaticVoidMethodV(env, c, m, ap); va_end(ap); trace_method(env, __builtin_return_address(0), "CallStatic", m, NULL); }

#define SET(Type) \
    table.Call##Type##Method = t_call_##Type; table.Call##Type##MethodV = t_call_##Type##_v; table.Call##Type##MethodA = t_call_##Type##_a; \
    table.CallStatic##Type##Method = t_scall_##Type; table.CallStatic##Type##MethodV = t_scall_##Type##_v; table.CallStatic##Type##MethodA = t_scall_##Type##_a;

void apklayer_jni_trace_install(JNIEnv *env, jvmtiEnv *jvmti)
{
    static jniNativeInterface table;
    const char *on = getenv("APKLAYER_JNI_TRACE");
    jniNativeInterface *current;
    if (!on || strcmp(on, "1") || ti || !env || !jvmti || (*jvmti)->GetJNIFunctionTable(jvmti, &current) != JVMTI_ERROR_NONE) return;
    ti = jvmti;
    next = *current;
    (*jvmti)->Deallocate(jvmti, (unsigned char *)current);
    jclass throwable = next.FindClass(env, "java/lang/Throwable");
    to_string = throwable ? next.GetMethodID(env, throwable, "toString", "()Ljava/lang/String;") : NULL;
    if (next.ExceptionCheck(env)) next.ExceptionClear(env);
    table = next;
    table.FindClass = t_find_class;
    table.GetMethodID = t_get_method;
    table.GetStaticMethodID = t_get_static_method;
    table.NewStringUTF = t_new_string_utf;
    table.NewObject = t_new_object; table.NewObjectV = t_new_object_v; table.NewObjectA = t_new_object_a;
    SET(Object) SET(Boolean) SET(Int) SET(Void)
    (*jvmti)->SetJNIFunctionTable(jvmti, &table);
    fprintf(stderr, "[jni-trace] on: guest JNI calls are logged\n");
}
