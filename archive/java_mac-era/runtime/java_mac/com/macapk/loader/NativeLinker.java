package com.macapk.loader;

/**
 * Map a translated native library the way the Android DYNAMIC LINKER maps a
 * DT_NEEDED dependency: dlopen() it -- constructors run, the image joins the
 * process -- and do NOT run its JNI_OnLoad or register it with any ClassLoader.
 *
 * WHY. System.load() is not "map": the JDK calls JNI_OnLoad inside it
 * (jdk21u NativeLibraries.c:130-139). On Android JNI_OnLoad runs only for a
 * library the APP asks for (art java_vm_ext.cc:1120-1135,
 * JavaVMExt::LoadNativeLibrary); a dependency the linker pulls in only gets its
 * constructors (bionic linker_soinfo.cpp, soinfo::call_constructors).
 * AppClassLoader.premapDependencyLibs used System.load() at Boot, so a SoLoader
 * app's dependency JNI_OnLoads ran before the app had called
 * SoLoader/NativeLoader.init -- fbjni's then threw "NativeLoader has not been
 * initialized" and the process aborted (Efteling 5.24.0).
 *
 * The native lives in runtime/src/native_linker.cpp (registered at boot, soft-
 * fail). docs/soloader-premap-jni-onload-timing_2026-09-29.md.
 */
public final class NativeLinker {
    private NativeLinker() {}

    /** dlopen(absPath, RTLD_LAZY), HotSpot's own mode, so a later System.load()
     *  of the same file gets this image. Returns null when mapped, else the
     *  dlerror() text. Throws UnsatisfiedLinkError when the runtime binary does
     *  not carry the native (a jar newer than the runtime). */
    public static native String nativeLinkOnly(String absPath);
}
