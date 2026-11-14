#include "env.h"

#include "apps.h"
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

// Vulkan presentation (VK_ANDROID_native_buffer in aosp/hals/graphics/vulkan/Driver.cpp) where the host has a GPU device that imports dma-bufs:
// its API version as "major.minor", "" without one. Venus (virtio-gpu Vulkan on the VMs) aborts on imported dma-bufs and CPU devices are no game
// target. shortcut: any such device counts, not only the instance's GPU (APKLAYER_GPU); the driver still checks the extensions on its own device.
std::string hostVulkan(const Layout& layout, const Env& env) {
    std::string cache = join(layout.runtimeHome, "apklayer-host-vulkan");
    if (auto cached = readFile(cache); cached && !trim(*cached).empty()) return trim(*cached) == "-" ? "" : trim(*cached);
    if (!exists("/usr/bin/vulkaninfo")) return "";  // vulkan-tools is only recommended
    Spawn probe;
    probe.argv = {"vulkaninfo", "--summary"};
    probe.env = env;
    int status = 0;
    std::string out = runCapture(probe, &status), version;
    unsigned best = 0;
    for (size_t at = out.find("\nGPU"); at != std::string::npos;) {  // the "GPU0:", "GPU1:" ... blocks
        size_t next = out.find("\nGPU", at + 4);
        std::string gpu = out.substr(at, next == std::string::npos ? std::string::npos : next - at);
        at = next;
        auto field = [&](const std::string& key) {
            size_t at = gpu.find(key + " ");
            if (at == std::string::npos) return std::string();
            size_t eq = gpu.find("= ", at), end = gpu.find('\n', eq);
            return eq == std::string::npos ? std::string() : trim(gpu.substr(eq + 2, end - eq - 2));
        };
        std::string api = field("apiVersion"), type = field("deviceType"), driver = field("driverID");
        unsigned major = 0, minor = 0;
        if (sscanf(api.c_str(), "%u.%u", &major, &minor) != 2 || type.empty() || type == "PHYSICAL_DEVICE_TYPE_CPU" || driver == "DRIVER_ID_MESA_VENUS") continue;
        if ((major << 16 | minor) > best) best = major << 16 | minor, version = std::to_string(major) + "." + std::to_string(minor);
    }
    writeFile(cache, (version.empty() ? "-" : version) + "\n");
    return version;
}


// Class data sharing (docs/history/PLAN-2026-11-10.md "app start speed on phones"): the runtime's JDK image (tools/release/jdk-image.sh, java.base with the patch built in)
// while it was made from this JDK's modules, else "" (the JDK with --patch-module, which turns CDS off)
std::string jdkImage(const Layout& l, const std::string& java) {
    std::string img = join(l.native, "jdk"), home = dirName(dirName(realPath(java)));
    if (!home.empty() && exists(join(home, "apklayer-stamp"))) return java;  // an image itself (the click's own JDK, tools/release/click.sh)
    struct stat modules{};
    if (home.empty() || stat(join(home, "lib/modules").c_str(), &modules) != 0 || !isExecutable(join(img, "bin/java"))) return "";
    return trim(readFile(join(img, "apklayer-stamp")).value_or("")) == std::to_string(modules.st_mtime) ? join(img, "bin/java") : "";
}

// The instance's archive of the classes a start loads, made by the first launch that finds none (apklayer.host.CdsDump), named by the class path's
// files: a new runtime gets its own. The JVM checks it itself and runs without it when it does not match (-Xshare:auto).
void cdsArgs(const Layout& l, const Instance& inst, std::vector<std::string>& a) {
    std::string key = readFile(join(dirName(dirName(realPath(a[0]))), "apklayer-stamp")).value_or("");  // the image a[0] runs
    for (const std::string& entry : split(hostClasspath(l), ':')) {
        struct stat st{};
        if (stat(entry.c_str(), &st) == 0) key += entry + std::to_string(st.st_size) + "." + std::to_string(st.st_mtime) + ":";
    }
    char name[32];
    snprintf(name, sizeof name, "%016zx.jsa", std::hash<std::string>{}(key));
    std::string jsa = join(inst.stateDir, std::string("cds/") + name);
    // at the address it was written for, as JDKs before 20 did: relocated (21's default) its pages are each JVM's own, ~25 MB more per JVM than no
    // archive (instance PSS on UTM: 2237 MB without, 2329 relocated, 2197 here); the JVM relocates it still when that address is taken
    if (exists(jsa)) a.insert(a.end(), {"-XX:SharedArchiveFile=" + jsa, "-XX:+UnlockDiagnosticVMOptions", "-XX:ArchiveRelocationMode=0"});
    else a.insert(a.end(), {"-XX:+RecordDynamicDumpInfo", "-Dapklayer.cdsdump=" + jsa});
}

}  // namespace

std::string findJava(const Layout& l) {
    if (!l.java.empty()) return l.java;  // the 32-bit runtime's (Layout::arm32Layout)
    std::string wanted = getenvOr("APKLAYER_NATIVE_JAVA", "");
    if (!wanted.empty() && isExecutable(wanted)) return wanted;
    // a package with its own JDK image (the click: Ubuntu Touch's rootfs has no Java for it)
    std::string own = join(l.build, "native/jdk/bin/java");
    if (!l.runtimeVersion.empty() && isExecutable(own)) return own;
    glob_t found{};
    std::string result = "java";
    // JDK 21 first (the tested one; the host patches java.base), any architecture's package name (arm64, amd64)
    // (temurin-21-jdk: Adoptium's RPM path, also the Sailfish OS package's: rpm/temurin-21-jdk.spec)
    // (JDK 25 only when no 21 is installed: tools/release/jdk-image.sh builds its image without jmods; the AOT cache is not used, docs/PLAN.md)
    if ((glob("/usr/lib/jvm/java-21-openjdk-*/bin/java", 0, nullptr, &found) == 0 ||
         glob("/usr/lib/jvm/temurin-21-jdk*/bin/java", GLOB_APPEND, nullptr, &found) == 0 ||
         glob("/usr/lib/jvm/java-25-openjdk-*/bin/java", GLOB_APPEND, nullptr, &found) == 0 ||
         glob("/usr/lib/jvm/temurin-25-jdk*/bin/java", GLOB_APPEND, nullptr, &found) == 0) && found.gl_pathc > 0) result = found.gl_pathv[0];
    globfree(&found);
    return result;
}

Env hostToolEnv() {
    Env env = currentEnv();
    env.erase("LD_PRELOAD");
    env.erase("LD_LIBRARY_PATH");
    return env;
}

// A property of the machine's own Android (a Halium device): libhybris's getprop reads its live property area; "" when there is none
std::string deviceProperty(const std::string& key) {
    if (!exists("/usr/bin/getprop")) return "";
    Spawn get;
    get.argv = {"/usr/bin/getprop", key};
    get.env = hostToolEnv();
    int status = 0;
    std::string value = trim(runCapture(get, &status));
    return status == 0 ? value : "";
}

namespace {
// dir first in a search path, once: a launch from inside the instance (the hub's) already has it there, and its spare key must equal a desktop launch's
// dir first in the search path, once: the same result for an environment that went through here before (a launch's and the spare's, which the
// hub started, must be equal: the spare key)
std::string prepend(const std::string& dir, const std::string& path) {
    std::string result = dir;
    for (const std::string& entry : split(path, ':')) if (entry != dir) result += ":" + entry;
    return result;
}
}  // namespace

// A Halium device's graphics (providers.toml: graphics = device, docs/HALIUM.md): its vendor libraries load in bionic_translation's
// vendor namespace (0039) with the VNDK of the vendor's Android version, and Android's own libEGL/libGLES (the device's driver from
// /vendor/lib64/egl) replace apklayer's host-EGL ones: a cfg.d in the instance's directory, first in XDG_DATA_DIRS.
static void deviceGraphicsEnv(const Instance& instance, Env& env, const std::string& lib) {
    // lib: lib64, or lib for the 32-bit app process (its vendor libraries are the device's 32-bit ones)
    // the vendor's VNDK (ro.vndk.version: the Volla X23's system is API 32, its vendor 31, and both apexes exist); else the newest apex
    std::string vndk, version = deviceProperty("ro.vndk.version");
    if (!version.empty() && isDir("/apex/com.android.vndk.v" + version + "/" + lib)) vndk = "/apex/com.android.vndk.v" + version + "/" + lib;
    else
        for (const std::string& f : listDir("/apex"))
            if (f.rfind("com.android.vndk.v", 0) == 0 && isDir(join("/apex", f + "/" + lib))) vndk = join("/apex", f + "/" + lib);
    // shortcut: the vendor libraries' few system dependencies outside the VNDK (libprocessgroup, libcgrouprc) come from the
    // device's /system; a generated list of them per vendor version when another device needs more
    std::string extra = join(instance.root, lib == "lib64" ? "device-graphics/vendor-system" : "device-graphics/vendor-system32");  // the instance root: the runtime directory is made anew per stack
    makeDirs(extra);
    for (const char* so : {"libprocessgroup.so", "libcgrouprc.so"})
        if (exists(join("/system/" + lib, so)) && !exists(join(extra, so)) && symlink(join("/system/" + lib, so).c_str(), join(extra, so).c_str()) != 0)
            fprintf(stderr, "apklayer: cannot link %s into %s\n", so, extra.c_str());
    if (!env.count("BIONIC_VENDOR_LD_LIBRARY_PATH") || lib != "lib64")
        env["BIONIC_VENDOR_LD_LIBRARY_PATH"] = "/vendor/" + lib + ":/vendor/" + lib + "/egl:/vendor/" + lib + "/hw" + (vndk.empty() ? "" : ":" + vndk) + ":" + extra;
    // the vendor's libraries use apklayer's libhidlbase (bionic_translation 0041): one hwbinder state and service lookup per process, through
    // host_hals.conf; the vendor's Android 12 copy waits for a hwservicemanager.ready that never comes (it knows no .disabled)
    if (!env.count("BIONIC_VENDOR_SHARED_LIBS")) env["BIONIC_VENDOR_SHARED_LIBS"] = "libhidlbase.so";
    std::string share = join(instance.root, "device-graphics/share");
    makeDirs(join(share, "bionic_translation/cfg.d"));
    writeFile(join(share, "bionic_translation/cfg.d/device-graphics.cfg"),
              "# graphics = device: Android's own EGL loader and GLES (the device's driver), not apklayer's host EGL\n"
              "libEGL.so libEGL.so\nlibGLESv1_CM.so libGLESv1_CM.so\nlibGLESv2.so libGLESv2.so\nlibGLESv3.so libGLESv3.so\n");
    env["XDG_DATA_DIRS"] = prepend(share, env["XDG_DATA_DIRS"]);
}

// GL extensions hidden from Android code (APKLAYER_GL_HIDE="GL_KHR_texture_compression_astc_ldr ...", from the environment or
// ~/.config/apklayer/env): libGLESv2.so maps to bridge/native/gles_filter.c's library, which answers the extension queries without them,
// through a cfg.d in the instance's directory first in XDG_DATA_DIRS (the runtime's own cfg.d maps libGLESv2.so to the host's directly).
// A driver can advertise a format it decodes wrongly (NVIDIA's ASTC, PGR's home scene): the engine then takes its fallback.
static void glesFilterEnv(const Instance& instance, Env& env, const std::string& native) {
    auto hide = env.find("APKLAYER_GL_HIDE");
    std::string lib = join(native, "libapklayer_gles.so");
    if (hide == env.end() || trim(hide->second).empty() || !exists(lib)) return;
    std::string share = join(instance.root, "gles-filter/share");
    makeDirs(join(share, "bionic_translation/cfg.d"));
    writeFile(join(share, "bionic_translation/cfg.d/gles-filter.cfg"),
              "# APKLAYER_GL_HIDE: the host's GLES with those extensions hidden (cli/env.cpp glesFilterEnv)\n"
              "libGLESv2.so " + lib + "\nlibGLESv3.so " + lib + "\n");
    env["XDG_DATA_DIRS"] = prepend(share, env["XDG_DATA_DIRS"]);
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
    // a domain that is the device's (providers.toml or automatic): libbinder's and libhwbinder's host contexts are the machine's own binder devices
    // (a Halium device's; docs/HALIUM.md), unless the environment names others
    if (anyDeviceDomain(layout, instance) || exists(hostHalsFile(instance))) {
        if (!env.count("APKLAYER_HOST_BINDER")) env["APKLAYER_HOST_BINDER"] = hostBinder("binder");  // the device's own, not the instance's (cli/binder.cpp)
        if (!env.count("APKLAYER_HOST_HWBINDER")) env["APKLAYER_HOST_HWBINDER"] = hostBinder("hwbinder");
    }
    // the userspace binder (docs/BINDER.md): the route in every Android process (bridge/native/binder_route.c) reads these
    if (stackUserspaceBinder(instance)) {
        env["APKLAYER_BINDER"] = "userspace";
        env["APKLAYER_BINDER_DIR"] = binderSocketDir(layout.runtimeHome);
    } else env["APKLAYER_BINDER"] = "kernel";  // the running stack's (or the stack about to start), whatever the caller's environment says
    // bionic_translation's libraries first (the patched ones), then ours; the path layer and bionic's TLS slots come first in every lookup
    env["LD_LIBRARY_PATH"] = layout.bionic + ":" + n;
    env["PATH"] = prepend(join(n, "bin"), getenvOr("PATH", "/usr/local/bin:/usr/bin:/bin"));  // getprop for the apps that run it
    env["XDG_DATA_DIRS"] = prepend(join(n, "share"), getenvOr("XDG_DATA_DIRS", "/usr/local/share:/usr/share"));
    if (deviceGraphics(layout, instance)) deviceGraphicsEnv(instance, env, layout.java.empty() ? "lib64" : "lib");  // a layout with its own JVM: the 32-bit one
    else glesFilterEnv(instance, env, n);
    env["LD_PRELOAD"] = join(n, "libapklayer-preload.so") + ":libgcc_s.so.1";
    if (const char* extra = getenv("APKLAYER_PRELOAD_EXTRA")) env["LD_PRELOAD"] += std::string(":") + extra;  // more preloads (debugging)
    env["BIONIC_LD_LIBRARY_PATH"] = l;
    if (!env.count("APKLAYER_CRASH_TRACE")) env["APKLAYER_CRASH_TRACE"] = "1";
    // system_server's services stay in the JVM; native clients use their existing unavailable-service fallbacks
    if (!env.count("APKLAYER_NO_SYSTEM_SERVER")) env["APKLAYER_NO_SYSTEM_SERVER"] = "1";
    // Android and host Vulkan loaders retain separate dispatch tables once the frontend is installed
    if (exists(join(l, "libapklayer_vulkan.so")) && exists(join(l, "vulkan.apklayer.so"))) env["APKLAYER_VULKAN_HOST"] = "1";
    // Vulkan presentation on where the host can do it (the driver's switch; HostSystemConfig declares android.hardware.vulkan.* from the
    // version); APKLAYER_VULKAN_PRESENT=0 turns it off, =1 keeps it on where the probe finds no device
    if (env.count("APKLAYER_VULKAN_PRESENT") && env["APKLAYER_VULKAN_PRESENT"] == "0") env.erase("APKLAYER_VULKAN_PRESENT");
    else if (env.count("APKLAYER_VULKAN_HOST")) {
        std::string vulkan = hostVulkan(layout, env);
        if (!vulkan.empty()) env["APKLAYER_VULKAN_PRESENT"] = "1", env["APKLAYER_VULKAN_VERSION"] = vulkan;
    }
    // the host side of the graphics, media and audio HALs: every staged library is loaded (services and apps use all of them)
    std::string libs;
    for (const char* hal : {"graphics", "media", "audio"}) {
        std::string lib = join(n, std::string("libapklayer-hal-") + hal + ".so");
        if (std::string(hal) == "audio") lib = audioLibrary(layout, instance);  // the instance's audio provider (cli/providers.cpp)
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
    std::string gles = hostGles(layout, env);
    if (!env.count("MESA_GLES_VERSION_OVERRIDE") && getenvOr("APKLAYER_GLES_OVERRIDE", "auto") != "off") {
        // eglinfo probes a device of its own choosing: with several GPUs the claim is made anyway (a no-op on a 3.2 driver)
        if (gles.rfind("2.", 0) == 0 || gles == "3.0" || gpus.size() > 1) env["MESA_GLES_VERSION_OVERRIDE"] = "3.2";
    }
    if (env.count("MESA_GLES_VERSION_OVERRIDE")) gles = env["MESA_GLES_VERSION_OVERRIDE"];
    if (!gles.empty()) env["APKLAYER_GLES_VERSION"] = gles;  // the stack's ro.opengles.version (cli/stack.cpp)
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
                                  // memory of the always-on JVMs (docs/history/PLAN-2026-11-10.md "app start speed on phones" 8): SerialGC never shrinks below the initial heap
                                  // (1/64 of RAM: 90 MB on a 6 GB phone, ~45 MB live in the hub), and the Android natives' freed malloc stays in glibc's
                                  // arenas (a trim gave back 3-25 MB a JVM). Measured on the phone: no slower start
                                  "-XX:InitialHeapSize=32m", "-XX:TrimNativeHeapInterval=60000",
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
    // per-JVM memory (CHANGELOG 2026-11-14, Parallels): (1) a startup spike (ICU reads, class loading: ~150 MB of garbage) grows NewPipe's old generation to
    // 240 MB; after the Full GC that frees it SerialGC gave back a tenth of the excess per GC (ShrinkHeapInSteps) and kept 70% free: shrink at once, to 20-30%
    // free (app JVM 722 -> 573 MB PSS at its first frame). (2) Two C1 compiler threads instead of the default (their arenas: ~15 MB). Start times within
    // noise. APKLAYER_JVM_MEM=default: the JVM's own sizing. shortcut: the same on every device; an app whose live heap passes ~500 MB sees more Full GCs
    // while it grows: measure on the phones (docs/PLAN.md)
    if (getenvOr("APKLAYER_JVM_MEM", "") != "default")
        a.insert(a.begin() + 1, {"-XX:-ShrinkHeapInSteps", "-XX:MinHeapFreeRatio=20", "-XX:MaxHeapFreeRatio=30", "-XX:CICompilerCount=2"});
    // HotSpot's libjsig: a native library's fault handler (the native bridge's recovery, an app's own) is chained behind the JVM's, which
    // calls it for faults that are not its own (bionic_translation 0042); a spare-born process gets its route only later, so every JVM has it
    std::string image = getenvOr("APKLAYER_CDS", "") == "off" ? "" : jdkImage(l, ne.java);
    if (!image.empty()) { ne.java = a[0] = image; cdsArgs(l, inst, a); }
    std::string jsig = join(dirName(dirName(realPath(ne.java))), "lib/libjsig.so");
    if (exists(jsig)) ne.env["LD_PRELOAD"] += ":" + jsig;
    // libcore-only members Android's natives look up: the image has them in its java.base
    if (image.empty() && isDir(join(n, "jdk-patch"))) { a.push_back("--patch-module"); a.push_back("java.base=" + join(n, "jdk-patch")); }
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
