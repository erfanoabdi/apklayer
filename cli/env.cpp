#include "env.h"

#include <glob.h>

namespace apklayer {

namespace {
std::string findJava(const Layout&) {
    std::string wanted = getenvOr("APKLAYER_NATIVE_JAVA", "");
    if (!wanted.empty() && isExecutable(wanted)) return wanted;
    std::string standard = "/usr/lib/jvm/java-21-openjdk-arm64/bin/java";
    if (isExecutable(standard)) return standard;
    glob_t found{};
    std::string result = "java";
    if (glob("/usr/lib/jvm/*arm64*/bin/java", 0, nullptr, &found) == 0 && found.gl_pathc > 0) result = found.gl_pathv[0];
    globfree(&found);
    return result;
}

// Android apps ask for the GLES level a device gives (3.2; Off The Road 2 needs 3.1). A host driver that stops lower (virgl: ES 3.0) makes
// eglCreateContext fail with EGL_BAD_MATCH; the driver's own version override then claims the Android level: what the driver truly lacks
// (compute shaders...) fails when used, as on a device without it. Probed once per session; APKLAYER_GLES_OVERRIDE=off disables it.
std::string hostGles(const Layout& layout, const Env& env) {
    std::string cache = join(layout.runtimeHome, "apklayer-host-gles");
    if (auto cached = readFile(cache); cached && !trim(*cached).empty()) return trim(*cached);
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

NativeEnv nativeEnv(const Layout& layout, const Instance& instance) {
    NativeEnv result;
    Env& env = result.env;
    env = currentEnv();
    const std::string& n = layout.native;
    const std::string& l = layout.androidLibs;
    env["XDG_RUNTIME_DIR"] = layout.runtimeHome;
    if (!env.count("WAYLAND_DISPLAY")) env["WAYLAND_DISPLAY"] = "wayland-0";
    env["APKLAYER_INSTANCE"] = instance.id;
    env["APKLAYER_NATIVE"] = n;
    env["APKLAYER_LOGS"] = instance.logs();
    // bionic_translation's libraries first (the patched ones), then ours; the path layer and bionic's TLS slots come first in every lookup
    env["LD_LIBRARY_PATH"] = layout.bionic + ":" + n;
    env["XDG_DATA_DIRS"] = join(n, "share") + ":" + getenvOr("XDG_DATA_DIRS", "/usr/local/share:/usr/share");
    env["LD_PRELOAD"] = join(n, "libapklayer-preload.so") + ":libgcc_s.so.1";
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
        if (exists(lib)) libs += (libs.empty() ? "" : ":") + lib;
    }
    env["APKLAYER_HOST_LIBS"] = libs;
    result.java = findJava(layout);
    if (!env.count("MESA_GLES_VERSION_OVERRIDE") && getenvOr("APKLAYER_GLES_OVERRIDE", "auto") != "off") {
        std::string version = hostGles(layout, env);
        if (version.rfind("2.", 0) == 0 || version == "3.0") env["MESA_GLES_VERSION_OVERRIDE"] = "3.2";
    }
    return result;
}

std::vector<std::string> hostJvmArgs(const Layout& l, NativeEnv& ne) {
    const std::string& n = l.native;
    std::string nr = join(n, "android-root");
    std::vector<std::string> a = {ne.java, "-XX:TieredStopAtLevel=1", "-XX:+UseSerialGC", "@" + join(l.root, "runtime/host/jvm.args"), "-Drobolectric.offline=true",
                                  "-Drobolectric.dependency.dir=" + l.frameworkDir(), "-Dapklayer.native=" + n, "-Dapklayer.androidlibs=" + join(n, "android-libs"),
                                  "-Drobolectric.windowManager.useRequestedSize=true"};
    if (isDir(join(n, "jdk-patch"))) { a.push_back("--patch-module"); a.push_back("java.base=" + join(n, "jdk-patch")); }  // libcore-only members Android's natives look up
    if (isDir(join(nr, "system/fonts"))) a.push_back("-Drobolectric.nativeruntime.fontdir=" + join(nr, "system/fonts/"));
    std::string res = join(nr, "system/framework/org.lineageos.platform-res.apk");
    if (exists(res)) a.push_back("-Dapklayer.platformres=" + res);
    if (exists(join(n, "native-runtime.jar"))) a.push_back("-Dapklayer.nativejar=" + join(n, "native-runtime.jar"));
    if (isDir(join(nr, "apex"))) {
        ne.env["ANDROID_ROOT"] = join(nr, "system");
        ne.env["ANDROID_DATA"] = join(nr, "data");
        ne.env["ANDROID_I18N_ROOT"] = join(nr, "apex/com.android.i18n");
        ne.env["ANDROID_TZDATA_ROOT"] = join(nr, "apex/com.android.tzdata");
    }
    return a;
}

}  // namespace apklayer
