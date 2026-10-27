#!/bin/sh
# apklayer patch: preserve Android FD semantics while accepting the actual HotSpot FD layout.
set -eu
python3 - "$1" <<'PY'
from pathlib import Path
import sys
p = Path(sys.argv[1]) / 'libnativehelper/file_descriptor_jni.c'
s = p.read_text()
marker = '/* apklayer patch: descriptor APIs do not initialize unrelated Android-only NIO constants. */'
if marker not in s:
    start = s.index('static void EnsureArgumentIsFileDescriptor(')
    s = s[:start] + '''/* apklayer patch: descriptor APIs do not initialize unrelated Android-only NIO constants. */
static jclass FileDescriptorClass(JNIEnv* env) {
    jclass cls = (*env)->FindClass(env, "java/io/FileDescriptor");
    ALOG_ALWAYS_FATAL_IF(cls == NULL, "FileDescriptor class not found");
    return cls;
}

static jfieldID DescriptorField(JNIEnv* env, jclass cls) {
    jfieldID field = (*env)->GetFieldID(env, cls, "descriptor", "I");
    if (field == NULL) {
        (*env)->ExceptionClear(env); /* Android name absent on HotSpot. */
        field = (*env)->GetFieldID(env, cls, "fd", "I");
    }
    ALOG_ALWAYS_FATAL_IF(field == NULL, "FileDescriptor integer field not found");
    return field;
}

static jclass EnsureArgumentIsFileDescriptor(JNIEnv* env, jobject instance) {
    ALOG_ALWAYS_FATAL_IF(instance == NULL, "FileDescriptor is NULL");
    jclass cls = FileDescriptorClass(env);
    ALOG_ALWAYS_FATAL_IF(!(*env)->IsInstanceOf(env, instance, cls), "Argument is not a FileDescriptor");
    return cls;
}

JNIEXPORT _Nullable jobject AFileDescriptor_create(JNIEnv* env) {
    jclass cls = FileDescriptorClass(env);
    jobject result = (*env)->NewObject(env, cls, (*env)->GetMethodID(env, cls, "<init>", "()V"));
    (*env)->DeleteLocalRef(env, cls);
    return result;
}

JNIEXPORT int AFileDescriptor_getFd(JNIEnv* env, jobject instance) {
    jclass cls = EnsureArgumentIsFileDescriptor(env, instance);
    int result = (*env)->GetIntField(env, instance, DescriptorField(env, cls));
    (*env)->DeleteLocalRef(env, cls);
    return result;
}

JNIEXPORT void AFileDescriptor_setFd(JNIEnv* env, jobject instance, int fd) {
    jclass cls = EnsureArgumentIsFileDescriptor(env, instance);
    jmethodID setter = (*env)->GetMethodID(env, cls, "setInt$", "(I)V");
    if (setter != NULL) (*env)->CallVoidMethod(env, instance, setter, fd);
    else {
        (*env)->ExceptionClear(env); /* HotSpot uses its real fd field. */
        (*env)->SetIntField(env, instance, DescriptorField(env, cls), fd);
    }
    (*env)->DeleteLocalRef(env, cls);
}
'''
    p.write_text(s)
PY
# apklayer patch: use the JVM's public Buffer API and JNI direct-buffer address.
python3 - "$1" <<'PY'
from pathlib import Path
import sys
p = Path(sys.argv[1]) / 'libnativehelper/JNIPlatformHelp.c'
s = p.read_text()
marker = '/* apklayer patch: Buffer layout differs between libcore and OpenJDK. */'
start = s.index('#include "JniConstants.h"') if '#include "JniConstants.h"' in s else s.index(marker)
s = s[:start] + '''/* apklayer patch: Buffer layout differs between libcore and OpenJDK. */
static jint BufferInt(JNIEnv* env, jobject buffer, const char* name) {
    jclass cls = (*env)->GetObjectClass(env, buffer);
    jmethodID method = (*env)->GetMethodID(env, cls, name, "()I");
    jint value = (*env)->CallIntMethod(env, buffer, method);
    (*env)->DeleteLocalRef(env, cls);
    return value;
}

static jint ElementShift(JNIEnv* env, jobject buffer) {
    const char* types[] = {"java/nio/ByteBuffer", "java/nio/CharBuffer",
        "java/nio/ShortBuffer", "java/nio/IntBuffer", "java/nio/FloatBuffer",
        "java/nio/LongBuffer", "java/nio/DoubleBuffer"};
    const int shifts[] = {0, 1, 1, 2, 2, 3, 3};
    for (int i = 0; i < 7; i++) {
        jclass cls = (*env)->FindClass(env, types[i]);
        jboolean match = (*env)->IsInstanceOf(env, buffer, cls);
        (*env)->DeleteLocalRef(env, cls);
        if (match) return shifts[i];
    }
    return 0;
}

jarray jniGetNioBufferBaseArray(JNIEnv* env, jobject buffer) {
    jclass cls = (*env)->GetObjectClass(env, buffer);
    jmethodID has = (*env)->GetMethodID(env, cls, "hasArray", "()Z");
    jarray array = NULL;
    if ((*env)->CallBooleanMethod(env, buffer, has)) {
        jmethodID get = (*env)->GetMethodID(env, cls, "array", "()Ljava/lang/Object;");
        array = (jarray)(*env)->CallObjectMethod(env, buffer, get);
    }
    (*env)->DeleteLocalRef(env, cls);
    return array;
}

int jniGetNioBufferBaseArrayOffset(JNIEnv* env, jobject buffer) {
    return (BufferInt(env, buffer, "arrayOffset") + BufferInt(env, buffer, "position"))
        << ElementShift(env, buffer);
}

jlong jniGetNioBufferFields(JNIEnv* env, jobject buffer,
        jint* position, jint* limit, jint* shift) {
    *position = BufferInt(env, buffer, "position");
    *limit = BufferInt(env, buffer, "limit");
    *shift = ElementShift(env, buffer);
    return (jlong)(uintptr_t)(*env)->GetDirectBufferAddress(env, buffer);
}

jlong jniGetNioBufferPointer(JNIEnv* env, jobject buffer) {
    jint position, limit, shift;
    jlong address = jniGetNioBufferFields(env, buffer, &position, &limit, &shift);
    return address ? address + ((jlong)position << shift) : 0;
}
'''
if '#include <stdint.h>' not in s:
    s = s.replace('#include <stddef.h>', '#include <stddef.h>\n#include <stdint.h>')
p.write_text(s)
PY
