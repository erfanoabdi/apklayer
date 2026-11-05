#include "env.h"

#include "gpus.h"
#include "binder.h"
#include "halium.h"
#include "providers.h"

#include <glob.h>
#include <sys/stat.h>
#include <unistd.h>

namespace apklayer {

namespace {
// The framework jars as the build instrumented them (runtime/android-all-instrumented) (apklayer.tools.PreInstrument: the sandbox loader's own instrumentation, done once) while each is
// at least as new as what it was made from; else the plain jars, which the sandbox loader instruments class by class on every start.
std::string frameworkDependencyDir(const Layout& l) {
    std::string dir = join(l.native, "aosp/android-all-instrumented");
    std::vector<std::string> jars;
    for (const std::string& name : listDir(l.frameworkDir()))
        if (name.size() > 4 && name.substr(name.size() - 4) == ".jar") jars.push_back(name);
    if (jars.empty()) return l.frameworkDir();
    for (const std::string& jar : jars) {
        struct stat made{};
        if (stat(join(dir, jar).c_str(), &made) != 0) return l.frameworkDir();
        for (const std::string& input : {join(l.frameworkDir(), jar), join(l.native, "native-runtime.jar"), join(l.native, "aosp/Robolectric_all.jar")}) {
            struct stat st{};
            if (stat(input.c_str(), &st) == 0 && st.st_mtime > made.st_mtime) return l.frameworkDir();
        }
    }
    return dir;
}


// Android apps ask for the GLES level a device gives (3.2; Off The Road 2 needs 3.1). A host driver that stops lower (virgl: ES 3.0) makes
// eglCreateContext fail with EGL_BAD_MATCH; the driver's own version override then claims the Android level: what the driver truly lacks
// (compute shaders...) fails when used, as on a device without it. Probed once per session; APKLAYER_GLES_OVERRIDE=off disables it.
std::string hostGles(const Layout& layout, const Env& env) {
    std::string cache = join(layout.runtimeHome, "apklayer-host-gles");
    if (auto cached = readFile(cache); cached && !trim(*cached).empty()) return trim(*cached);
    if (!exists("/usr/bin/eglinfo")) return "";  // mesa-utils is only recommended (a Halium phone has none, and its GLES is the device's)
    Spawn probe;
    probe.argv = {"eglinfo", "-p", "gbm"};
    probe.env = env;
    int status = 0;
    std::string out = runCapture(probe, &status);
    const std::string key = "OpenGL ES profile version: OpenGL ES ";
    size_t at = out.find(key);
    if (at == std::string::npos) return "";
    size_t end = out.find_first_of(" \n", at + key.size());
    std::string version = out.substr(at + key.size(), end - at - key.size());
    writeFile(cache, version + "\n");
    if (version.rfind("2.", 0) == 0 || version == "3.0")
        fprintf(stderr, "apklayer: the host GLES is %s; apps get a 3.2 claim (APKLAYER_GLES_OVERRIDE=off to disable)\n", version.c_str());
    return version;
}

}  // namespace

std::string findJava(const Layout&) {
    std::string wanted = getenvOr("APKLAYER_NATIVE_JAVA", "");
    if (!wanted.empty() && isExecutable(wanted)) return wanted;
    glob_t found{};
    std::string result = "java";
    // JDK 21 first (the tested one; the host patches java.base), any architecture's package name (arm64, amd64)
    if (glob("/usr/lib/jvm/java-21-openjdk-*/bin/java", 0, nullptr, &found) == 0 && found.gl_pathc > 0) result = found.gl_pathv[0];
    globfree(&found);
    return result;
}

// A property of the machine's own Android (a Halium device): libhybris's getprop reads its live property area; "" when there is none
std::string deviceProperty(const std::string& key) {
    if (!exists("/usr/bin/getprop")) return "";
    Spawn get;
    get.argv = {"/usr/bin/getprop", key};
    get.env = currentEnv();
    int status = 0;
    std::string value = trim(runCapture(get, &status));
    return status == 0 ? value : "";
}

namespace {
// dir first in a search path, once: a launch from inside the instance (the hub's) already has it there, and its spare key must equal a desktop launch's
std::string prepend(const std::string& dir, const std::string& path) {
    return path == dir || path.rfind(dir + ":", 0) == 0 ? path : dir + ":" + path;
}
}  // namespace

// A Halium device's graphics (providers.toml: graphics = device, docs/HALIUM.md): its vendor libraries load in bionic_translation's
// vendor namespace (0039) with the VNDK of the vendor's Android version, and Android's own libEGL/libGLES (the device's driver from
// /vendor/lib64/egl) replace apklayer's host-EGL ones: a cfg.d in the instance's directory, first in XDG_DATA_DIRS.
static void deviceGraphicsEnv(const Instance& instance, Env& env) {
    // the vendor's VNDK (ro.vndk.version: the Volla X23's system is API 32, its vendor 31, and both apexes exist); else the newest apex
    std::string vndk, version = deviceProperty("ro.vndk.version");
    if (!version.empty() && isDir("/apex/com.android.vndk.v" + version + "/lib64")) vndk = "/apex/com.android.vndk.v" + version + "/lib64";
    else
        for (const std::string& f : listDir("/apex"))
            if (f.rfind("com.android.vndk.v", 0) == 0 && isDir(join("/apex", f + "/lib64"))) vndk = join("/apex", f + "/lib64");
    // shortcut: the vendor libraries' few system dependencies outside the VNDK (libprocessgroup, libcgrouprc) come from the
    // device's /system; a generated list of them per vendor version when another device needs more
    std::string extra = join(instance.root, "device-graphics/vendor-system");  // the instance root: the runtime directory is made anew per stack
    makeDirs(extra);
    for (const char* lib : {"libprocessgroup.so", "libcgrouprc.so"})
        if (exists(join("/system/lib64", lib)) && !exists(join(extra, lib)) && symlink(join("/system/lib64", lib).c_str(), join(extra, lib).c_str()) != 0)
            fprintf(stderr, "apklayer: cannot link %s into %s\n", lib, extra.c_str());
    if (!env.count("BIONIC_VENDOR_LD_LIBRARY_PATH"))
        env["BIONIC_VENDOR_LD_LIBRARY_PATH"] = "/vendor/lib64:/vendor/lib64/egl:/vendor/lib64/hw" + (vndk.empty() ? "" : ":" + vndk) + ":" + extra;
    // the vendor's libraries use apklayer's libhidlbase (bionic_translation 0041): one hwbinder state and service lookup per process, through
    // host_hals.conf; the vendor's Android 12 copy waits for a hwservicemanager.ready that never comes (it knows no .disabled)
    if (!env.count("BIONIC_VENDOR_SHARED_LIBS")) env["BIONIC_VENDOR_SHARED_LIBS"] = "libhidlbase.so";
    std::string share = join(instance.root, "device-graphics/share");
    makeDirs(join(share, "bionic_translation/cfg.d"));
    writeFile(join(share, "bionic_translation/cfg.d/device-graphics.cfg"),
              "# graphics = device: Android's own EGL loader and GLES (the device's driver), not apklayer's host EGL\n"
              "libEGL.so libEGL.so\nlibGLESv1_CM.so libGLESv1_CM.so\nlibGLESv2.so libGLESv2.so\nlibGLESv3.so libGLESv3.so\n");
    env["XDG_DATA_DIRS"] = share + ":" + env["XDG_DATA_DIRS"];
}

NativeEnv nativeEnv(const Layout& layout, const Instance& instance) {
    layout.requireRuntime();
    NativeEnv result;
    Env& env = result.env;
    env = currentEnv();
    const std::string& n = layout.native;
    const std::string& l = layout.androidLibs;
    env["XDG_RUNTIME_DIR"] = layout.runtimeHome;
    if (!env.count("WAYLAND_DISPLAY")) env["WAYLAND_DISPLAY"] = "wayland-0";
    haliumEnvDefaults(env, layout);
    env["APKLAYER_INSTANCE"] = instance.id;
    env["APKLAYER_NATIVE"] = n;
    env["APKLAYER_LOGS"] = instance.logs();
    env["APKLAYER_INSTANCE_ROOT"] = join(instance.runtimeDir, "android-root");  // what this instance's stack stages for itself (cli/stack.cpp), before the shared root
    env["APKLAYER_STORAGE"] = realPath(instance.storageDir);  // the path layer serves Android's /storage, /sdcard and /data/media/0 from it (bridge/native/paths.c)
    env["APKLAYER_DATA"] = realPath(instance.dataDir);  // and the rest of /data
    // a domain set to the device (providers.toml): libbinder's and libhwbinder's host contexts are the machine's own binder devices
    // (a Halium device's; docs/HALIUM.md), unless the environment names others
    if (exists(hostHalsFile(instance))) {
        if (!env.count("APKLAYER_HOST_BINDER")) env["APKLAYER_HOST_BINDER"] = hostBinder("binder");  // the device's own, not the instance's (cli/binder.cpp)
        if (!env.count("APKLAYER_HOST_HWBINDER")) env["APKLAYER_HOST_HWBINDER"] = hostBinder("hwbinder");
    }
    // bionic_translation's libraries first (the patched ones), then ours; the path layer and bionic's TLS slots come first in every lookup
    env["LD_LIBRARY_PATH"] = layout.bionic + ":" + n;
    env["PATH"] = prepend(join(n, "bin"), getenvOr("PATH", "/usr/local/bin:/usr/bin:/bin"));  // getprop for the apps that run it
    env["XDG_DATA_DIRS"] = prepend(join(n, "share"), getenvOr("XDG_DATA_DIRS", "/usr/local/share:/usr/share"));
    if (deviceGraphics(instance)) deviceGraphicsEnv(instance, env);
    env["LD_PRELOAD"] = join(n, "libapklayer-preload.so") + ":libgcc_s.so.1";
    if (const char* extra = getenv("APKLAYER_PRELOAD_EXTRA")) env["LD_PRELOAD"] += std::string(":") + extra;  // e.g. the JVM's libjsig.so for apps whose native code installs signal handlers
    env["BIONIC_LD_LIBRARY_PATH"] = l;
    if (!env.count("APKLAYER_CRASH_TRACE")) env["APKLAYER_CRASH_TRACE"] = "1";
    // system_server's services stay in the JVM; native clients use their existing unavailable-service fallbacks
    if (!env.count("APKLAYER_NO_SYSTEM_SERVER")) env["APKLAYER_NO_SYSTEM_SERVER"] = "1";
    // Android and host Vulkan loaders retain separate dispatch tables once the frontend is installed
    if (exists(join(l, "libapklayer_vulkan.so")) && exists(join(l, "vulkan.apklayer.so"))) env["APKLAYER_VULKAN_HOST"] = "1";
    // the host side of the graphics, media and audio HALs: every staged library is loaded (services and apps use all of them)
    std::string libs;
    for (const char* hal : {"graphics", "media", "audio"}) {
        std::string lib = join(n, std::string("libapklayer-hal-") + hal + ".so");
        if (std::string(hal) == "audio") lib = audioLibrary(layout);
        if (exists(lib)) libs += (libs.empty() ? "" : ":") + lib;
    }
    env["APKLAYER_HOST_LIBS"] = libs;
    result.java = findJava(layout);
    // the instance's GPU (cli/gpus.cpp): the graphics HAL's GBM device and with it EGL's (Egl.cpp), Vulkan's device (vulkan/Driver.cpp),
    // the codecs' VA device (bridge/hals/media); the compositor's node tells the HAL when window buffers cross GPUs (graphics.c)
    std::vector<Gpu> gpus = listGpus(layout);
    if (const Gpu* gpu = instanceGpu(gpus, instance)) {
        env["APKLAYER_RENDER_NODE"] = gpu->node;
        // shortcut: gstreamer-vaapi reads its device from here; the newer va plugin names elements per device (varenderD129h264dec) and decodebin
        // takes the first device's: a rank change in bridge/hals/media/codec.c would follow the choice (untested: no machine with two VA GPUs)
        env["GST_VAAPI_DRM_DEVICE"] = gpu->node;
        for (const Gpu& g : gpus)
            if (g.compositor) env["APKLAYER_COMPOSITOR_NODE"] = g.node;
    }
    if (!env.count("MESA_GLES_VERSION_OVERRIDE") && getenvOr("APKLAYER_GLES_OVERRIDE", "auto") != "off") {
        std::string version = hostGles(layout, env);
        // eglinfo probes a device of its own choosing: with several GPUs the claim is made anyway (a no-op on a 3.2 driver)
        if (version.rfind("2.", 0) == 0 || version == "3.0" || gpus.size() > 1) env["MESA_GLES_VERSION_OVERRIDE"] = "3.2";
    }
    return result;
}

std::vector<std::string> hostJvmArgs(const Layout& l, const Instance& inst, NativeEnv& ne) {
    const std::string& n = l.native;
    std::string nr = join(n, "android-root");
    makeDirs(inst.tmpDir());
    std::vector<std::string> a = {ne.java, "-XX:TieredStopAtLevel=1", "-XX:+UseSerialGC",
                                  // C1 only gets a 48 MB code cache; Meet filled it in a call ("Out of space in CodeCache for method handle intrinsic", the
                                  // JVM exits). 240 MB is the tiered default; it is reserved address space, committed as code is compiled
                                  "-XX:ReservedCodeCacheSize=240m",
                                  // a JVM defines ~5400 framework classes at start: with the default 32K buckets the symbol lookups of their constant pools
                                  // take about half of the class definition time (perf: SymbolTable::do_lookup), the table grows only later
                                  "-XX:+UnlockExperimentalVMOptions", "-XX:SymbolTableSize=262144",
                                  "@" + join(l.root, "runtime/host/jvm.args"), "-Drobolectric.offline=true",
                                  "-Drobolectric.dependency.dir=" + frameworkDependencyDir(l), "-Dapklayer.native=" + n, "-Dapklayer.androidlibs=" + l.androidLibs,
                                  "-Drobolectric.windowManager.useRequestedSize=true",
                                  "-Drobolectric.allowPlatformPermissions=true",  // system apps (ContactsProvider...) declare platform permissions of their own
                                  // Robolectric's native runtime: its library per JVM, its fonts and ICU data once (robolectric patch 0001), in the instance's state directory (disk)
                                  "-Djava.io.tmpdir=" + inst.tmpDir(),
                                  "-Dapklayer.storage=" + inst.storageDir};  // volumes: Internal storage (emulated/0 below), Home, Linux root
    if (isDir(join(n, "jdk-patch"))) { a.push_back("--patch-module"); a.push_back("java.base=" + join(n, "jdk-patch")); }  // libcore-only members Android's natives look up
    // the fonts of the device build, once a fonts.xml is staged beside them (Robolectric's font shadows read <fontdir>/fonts.xml); Robolectric's own otherwise
    if (exists(join(nr, "system/fonts/fonts.xml"))) a.push_back("-Drobolectric.nativeruntime.fontdir=" + join(nr, "system/fonts/"));
    std::string res = join(nr, "system/framework/org.lineageos.platform-res.apk");
    if (exists(res)) a.push_back("-Dapklayer.platformres=" + res);
    if (exists(join(n, "native-runtime.jar"))) a.push_back("-Dapklayer.nativejar=" + join(n, "native-runtime.jar"));
    if (isDir(join(nr, "apex"))) {
        ne.env["ANDROID_ROOT"] = join(nr, "system");
        ne.env["ANDROID_DATA"] = "/data";  // the instance's own (APKLAYER_DATA through the path layer): SettingsProvider's files, not the shared native root's
        ne.env["ANDROID_I18N_ROOT"] = join(nr, "apex/com.android.i18n");
        ne.env["ANDROID_TZDATA_ROOT"] = join(nr, "apex/com.android.tzdata");
    }
    return a;
}

}  // namespace apklayer
