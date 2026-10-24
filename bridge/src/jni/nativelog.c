/*
 * apklayer JNI bridge — seed.
 *
 * The java_mac framework base expects a small set of natives implemented by Mac.apk's
 * ObjC runtime. On Linux we provide them ourselves, one by one, in this library.
 * First symbol: com.macapk.loader.NativeLog.print — the very first native hit when a real
 * app Activity instantiates (Log.i -> NativeLog.i -> print).
 */
#include <jni.h>
#include <stdio.h>

/* level mirrors Android log priorities (2=VERBOSE..7=ASSERT). */
static const char *level_name(int level)
{
    switch (level) {
        case 2: return "V";
        case 3: return "D";
        case 4: return "I";
        case 5: return "W";
        case 6: return "E";
        case 7: return "A";
        default: return "?";
    }
}

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeLog_print(JNIEnv *env, jclass clazz,
                                       jint level, jstring tag, jstring msg)
{
    (void)clazz;
    const char *c_tag = tag ? (*env)->GetStringUTFChars(env, tag, NULL) : "";
    const char *c_msg = msg ? (*env)->GetStringUTFChars(env, msg, NULL) : "";
    fprintf(stderr, "%s/%s: %s\n", level_name(level), c_tag, c_msg);
    if (tag) (*env)->ReleaseStringUTFChars(env, tag, c_tag);
    if (msg) (*env)->ReleaseStringUTFChars(env, msg, c_msg);
}
