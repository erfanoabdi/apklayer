#!/bin/sh
# apklayer patch: original listener callbacks need real payload Parcels on the host.
set -eu
python3 - "$1" <<'PY'
from pathlib import Path
import sys
p = Path(sys.argv[1]) / 'frameworks/base/media/jni/android_media_MediaPlayer.cpp'
s = p.read_text()
if 'apklayer patch: selected native callback Parcels' not in s:
    s = '#include <cstdlib>\n' + s
    s = s.replace('struct fields_t {', '''// apklayer patch: selected native callback Parcels; ordinary Android path is unchanged.
struct fields_t {
    jclass hostParcelClass;
    jmethodID hostParcelObtain;''', 1)
    s = s.replace('jobject jParcel = createJavaParcelObject(env);', '''jobject jParcel = fields.hostParcelClass
                ? env->CallStaticObjectMethod(fields.hostParcelClass, fields.hostParcelObtain)
                : createJavaParcelObject(env);''', 1)
    anchor = 'android_media_MediaPlayer_native_init(JNIEnv *env)\n{'
    assert s.count(anchor) == 1
    s = s.replace(anchor, anchor + '''
    // apklayer patch: cache the factory on the initializing Java class loader,
    // not on a subsequently attached native binder thread.
    if (getenv("APKLAYER_NATIVE_MEDIA_PARCELS")) {
        jclass factory = env->FindClass("apklayer/host/NativeParcel");
        if (!factory) return;
        fields.hostParcelObtain = env->GetStaticMethodID(factory, "obtain", "()Landroid/os/Parcel;");
        if (!fields.hostParcelObtain) return;
        fields.hostParcelClass = static_cast<jclass>(env->NewGlobalRef(factory));
        jmethodID ready = env->GetStaticMethodID(factory, "markCallbackFactoryReady", "()V");
        if (!ready) return;
        env->CallStaticVoidMethod(factory, ready);
        env->DeleteLocalRef(factory);
        if (env->ExceptionCheck()) return;
    }
''', 1)
    p.write_text(s)
PY
