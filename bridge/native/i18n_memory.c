/* com.android.i18n.timezone.internal.Memory's natives, which ART takes from libicu_jni (not loaded here): reads and writes at a raw address, as
 * libcore's Memory. The time zone database (ZoneInfoDb, android.util.TimeUtils.getTimeZoneDatabaseVersion) reads its mapped tzdata through them;
 * without them Play services' trusted-time service threw UnsatisfiedLinkError (peekByteArray) and its account sign-in waited for ever.
 * The class is outside Robolectric's instrumented packages, so its natives keep their own names. The array forms with `swap` reverse each element's bytes. */
#include <jni.h>
#include <stdint.h>
#include <string.h>

static void swap_bytes(void *p, size_t width, jint count)
{
    unsigned char *b = p;
    for (jint i = 0; i < count; i++, b += width)
        for (size_t lo = 0, hi = width - 1; lo < hi; lo++, hi--) { unsigned char t = b[lo]; b[lo] = b[hi]; b[hi] = t; }
}

#define ADDR(a) ((void *)(uintptr_t)(a))

static jbyte JNICALL peek_byte(JNIEnv *e, jclass c, jlong a) { (void)e; (void)c; return *(jbyte *)ADDR(a); }
static jshort JNICALL peek_short(JNIEnv *e, jclass c, jlong a) { (void)e; (void)c; jshort v; memcpy(&v, ADDR(a), sizeof v); return v; }
static jint JNICALL peek_int(JNIEnv *e, jclass c, jlong a) { (void)e; (void)c; jint v; memcpy(&v, ADDR(a), sizeof v); return v; }
static jlong JNICALL peek_long(JNIEnv *e, jclass c, jlong a) { (void)e; (void)c; jlong v; memcpy(&v, ADDR(a), sizeof v); return v; }
static void JNICALL poke_byte(JNIEnv *e, jclass c, jlong a, jbyte v) { (void)e; (void)c; *(jbyte *)ADDR(a) = v; }
static void JNICALL poke_short(JNIEnv *e, jclass c, jlong a, jshort v) { (void)e; (void)c; memcpy(ADDR(a), &v, sizeof v); }
static void JNICALL poke_int(JNIEnv *e, jclass c, jlong a, jint v) { (void)e; (void)c; memcpy(ADDR(a), &v, sizeof v); }
static void JNICALL poke_long(JNIEnv *e, jclass c, jlong a, jlong v) { (void)e; (void)c; memcpy(ADDR(a), &v, sizeof v); }

static void JNICALL peek_byte_array(JNIEnv *e, jclass c, jlong a, jbyteArray dst, jint off, jint n)
{ (void)c; (*e)->SetByteArrayRegion(e, dst, off, n, ADDR(a)); }
static void JNICALL poke_byte_array(JNIEnv *e, jclass c, jlong a, jbyteArray src, jint off, jint n)
{ (void)c; (*e)->GetByteArrayRegion(e, src, off, n, ADDR(a)); }

/* element arrays: copy through the array's elements (Get/Release, no copy-back on peek's source), then swap in place when asked */
#define ARRAY_PAIR(Name, JType, JArray, Kind)                                                                        \
    static void JNICALL peek_##Name(JNIEnv *e, jclass c, jlong a, JArray dst, jint off, jint n, jboolean swap)      \
    {                                                                                                               \
        (void)c;                                                                                                    \
        JType *d = (*e)->Get##Kind##ArrayElements(e, dst, NULL);                                                    \
        if (!d) return;                                                                                             \
        memcpy(d + off, ADDR(a), (size_t)n * sizeof(JType));                                                        \
        if (swap) swap_bytes(d + off, sizeof(JType), n);                                                            \
        (*e)->Release##Kind##ArrayElements(e, dst, d, 0);                                                           \
    }                                                                                                               \
    static void JNICALL poke_##Name(JNIEnv *e, jclass c, jlong a, JArray src, jint off, jint n, jboolean swap)      \
    {                                                                                                               \
        (void)c;                                                                                                    \
        JType *s = (*e)->Get##Kind##ArrayElements(e, src, NULL);                                                    \
        if (!s) return;                                                                                             \
        memcpy(ADDR(a), s + off, (size_t)n * sizeof(JType));                                                        \
        if (swap) swap_bytes(ADDR(a), sizeof(JType), n);                                                            \
        (*e)->Release##Kind##ArrayElements(e, src, s, JNI_ABORT);                                                   \
    }
ARRAY_PAIR(char_array, jchar, jcharArray, Char)
ARRAY_PAIR(short_array, jshort, jshortArray, Short)
ARRAY_PAIR(int_array, jint, jintArray, Int)
ARRAY_PAIR(long_array, jlong, jlongArray, Long)
ARRAY_PAIR(float_array, jfloat, jfloatArray, Float)
ARRAY_PAIR(double_array, jdouble, jdoubleArray, Double)

/* 0 on success; the class missing (another framework) is no error */
int register_i18n_memory(JNIEnv *env)
{
    static const JNINativeMethod methods[] = {
        {"peekByte", "(J)B", (void *)peek_byte},
        {"peekShortNative", "(J)S", (void *)peek_short},
        {"peekIntNative", "(J)I", (void *)peek_int},
        {"peekLongNative", "(J)J", (void *)peek_long},
        {"pokeByte", "(JB)V", (void *)poke_byte},
        {"pokeShortNative", "(JS)V", (void *)poke_short},
        {"pokeIntNative", "(JI)V", (void *)poke_int},
        {"pokeLongNative", "(JJ)V", (void *)poke_long},
        {"peekByteArray", "(J[BII)V", (void *)peek_byte_array},
        {"pokeByteArray", "(J[BII)V", (void *)poke_byte_array},
        {"peekCharArray", "(J[CIIZ)V", (void *)peek_char_array},
        {"pokeCharArray", "(J[CIIZ)V", (void *)poke_char_array},
        {"peekShortArray", "(J[SIIZ)V", (void *)peek_short_array},
        {"pokeShortArray", "(J[SIIZ)V", (void *)poke_short_array},
        {"peekIntArray", "(J[IIIZ)V", (void *)peek_int_array},
        {"pokeIntArray", "(J[IIIZ)V", (void *)poke_int_array},
        {"peekLongArray", "(J[JIIZ)V", (void *)peek_long_array},
        {"pokeLongArray", "(J[JIIZ)V", (void *)poke_long_array},
        {"peekFloatArray", "(J[FIIZ)V", (void *)peek_float_array},
        {"pokeFloatArray", "(J[FIIZ)V", (void *)poke_float_array},
        {"peekDoubleArray", "(J[DIIZ)V", (void *)peek_double_array},
        {"pokeDoubleArray", "(J[DIIZ)V", (void *)poke_double_array},
    };
    jclass memory = (*env)->FindClass(env, "com/android/i18n/timezone/internal/Memory");
    if (!memory) { (*env)->ExceptionClear(env); return 0; }
    int rc = (*env)->RegisterNatives(env, memory, methods, (jint)(sizeof methods / sizeof methods[0])) == 0 ? 0 : -1;
    if (rc) (*env)->ExceptionClear(env);
    (*env)->DeleteLocalRef(env, memory);
    return rc;
}
