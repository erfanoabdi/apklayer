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
