#include "apps.h"

#include <algorithm>
#include <cstdlib>
#include <unistd.h>

namespace apklayer {

namespace {
int sh(std::vector<std::string> argv, const Env& env = currentEnv(), const std::string& cwd = "") {
    Spawn s;
    s.argv = std::move(argv);
    s.env = env;
    s.cwd = cwd;
    return runAndWait(s);
}

// The cache key of a conversion: same APK, same framework, same converter -> same classes.
std::string cacheKey(const Layout& l, const std::string& apk) {
    std::string text = sha256File(apk) + "\npost-processing v6: Android classloader adapter, guava relocation, native loader, multidex no-op, in-process services, Unsafe.getUnsafe, Method.invoke\n";
    std::vector<std::string> jars = listDir(l.frameworkDir());
    std::sort(jars.begin(), jars.end());
    for (const std::string& j : jars)
        if (j.size() > 4 && j.substr(j.size() - 4) == ".jar") text += sha256File(join(l.frameworkDir(), j)) + "\n";
    if (auto commit = readFile(join(l.root, "runtime/dex2jvm/.dex2jvm-commit"))) text += *commit;
    return sha256Text(text);
}

void writeDesktop(const Layout& l, const std::string& pkg, const std::string& label, const std::string& dir) {
    std::string apps = join(l.dataHome, "applications");
    makeDirs(apps);
    std::string cli = l.cliBinary();
    writeFile(join(apps, "apklayer-" + pkg + ".desktop"),
              "[Desktop Entry]\nType=Application\nName=" + label + "\nComment=Android app (" + pkg + ") via apklayer\nExec=" + cli + " run " + pkg +
                  "\nIcon=" + dir + "/icon.png\nStartupWMClass=" + pkg + "\nCategories=Utility;\n");
    writeFile(join(apps, "apklayer-open-apk.desktop"),
              "[Desktop Entry]\nType=Application\nName=Android app (apklayer)\nExec=" + cli + " run %f\nMimeType=application/vnd.android.package-archive;\nNoDisplay=true\n");
    sh({"update-desktop-database", apps});
    sh({"xdg-mime", "default", "apklayer-open-apk.desktop", "application/vnd.android.package-archive"});
}

// The device libraries are arm64: apps run their arm64-v8a code.
void extractLibs(const std::string& dir) {
    removeTree(join(dir, "lib"));
    Spawn list;
    list.argv = {"unzip", "-Z1", join(dir, "base.apk")};
    list.env = currentEnv();
    int st = 0;
    std::string names = runCapture(list, &st);
    if (names.find("\nlib/arm64-v8a/") == std::string::npos && names.rfind("lib/arm64-v8a/", 0) != 0) return;
    makeDirs(join(dir, "lib"));
    sh({"unzip", "-o", "-j", "-q", join(dir, "base.apk"), "lib/arm64-v8a/*.so", "-d", join(dir, "lib")});
}
}  // namespace

std::string appDir(const Layout& l, const std::string& package) { return join(l.appsDir, package); }

// Every package gets its own Android app uid (10000 and up) in its info.properties: the framework treats a caller's uid as its identity and
// refuses the system range; the Linux uid of every app JVM is the desktop user's.
void ensureUid(const Layout& l, const std::string& dir) {
    std::string file = join(dir, "info.properties");
    if (!property(file, "uid").empty()) return;
    int max = 9999;
    for (const std::string& app : listDir(l.appsDir)) {
        std::string u = property(join(join(l.appsDir, app), "info.properties"), "uid");
        if (!u.empty() && atoi(u.c_str()) > max) max = atoi(u.c_str());
    }
    std::string text = readFile(file).value_or("");
    writeFile(file, text + "uid=" + std::to_string(max + 1) + "\n");
}

std::string hostClasspath(const Layout& l, const std::string& appClasses) {
    const std::string& n = l.native;
    return n + "/native-runtime.jar:" + l.hostClasses() + ":" + n + "/aosp/sim-classes:" + appClasses + ":" + n + "/aosp/Robolectric_all.jar:" + l.deps +
           "/android-stub.jar:" + l.dex2jvmJar();
}

std::string installApp(const Layout& l, const Instance& inst, const std::string& apk) {
    if (!exists(apk)) die("no such file: " + apk);
    if (!isDir(l.hostClasses()) || !exists(l.dex2jvmJar()))
        die("not built: run 'meson setup b && ninja -C b' and scripts/build-tree-host.sh " + l.native + " in " + l.root);
    makeDirs(l.appsDir);
    std::string key = cacheKey(l, apk);
    char pattern[4096];
    snprintf(pattern, sizeof pattern, "%s/.install.XXXXXX", l.appsDir.c_str());
    if (!mkdtemp(pattern)) die("cannot create a temporary directory in " + l.appsDir);
    std::string tmp = pattern;
    static std::string pending;  // die() exits without running destructors: the temporary directory goes at exit
    pending = tmp;
    std::atexit([] { removeTree(pending); });

    std::string reuse;
    for (const std::string& app : listDir(l.appsDir)) {
        std::string d = join(l.appsDir, app);
        if (auto k = readFile(join(d, "key")); k && trim(*k) == key && exists(join(d, "classes.jar"))) { reuse = join(d, "classes.jar"); break; }
    }
    sh({"cp", apk, join(tmp, "base.apk")});
    if (!reuse.empty()) {
        sh({"cp", reuse, join(tmp, "classes.jar")});
    } else {
        printf("apklayer: converting %s\n", baseName(apk).c_str());
        // dex2jvm reads the class hierarchy from its classpath (the framework we run on); keep it away from any installed Android SDK
        Env env = currentEnv();
        env["ANDROID_HOME"] = tmp;
        env["ANDROID_SDK_ROOT"] = tmp;
        Spawn conv;
        conv.argv = {"java", "-Duser.home=" + tmp, "-cp", l.dex2jvmJar() + ":" + l.frameworkDir() + "/*", "io.github.kksimp.dex2jvm.Main", join(tmp, "base.apk"), join(tmp, "raw.jar")};
        conv.env = env;
        conv.logFile = join(inst.logs(), "convert.log");
        if (runAndWait(conv) != 0) die("conversion failed (see " + conv.logFile + ")");
        if (sh({"java", "-cp", l.deps + "/libs/*:" + l.hostClasses(), "apklayer.tools.DenyInstrument", join(tmp, "raw.jar"), join(tmp, "classes.jar")}) != 0)
            die("preparing the converted classes failed (a half-written classes.jar is not installed)");
        unlink(join(tmp, "raw.jar").c_str());
    }
    writeFile(join(tmp, "key"), key + "\n");
    // describe renders the icon with the device natives, so the services must be up
    NativeEnv ne = nativeEnv(l, inst);
    if (!ensureStack(l, inst)) die("the Android services are not available");
    std::vector<std::string> argv = hostJvmArgs(l, inst, ne);
    argv.insert(argv.end(), {"-Djava.awt.headless=true", "-cp", hostClasspath(l, join(tmp, "classes.jar")), "apklayer.host.Main", "--describe", tmp, join(tmp, "base.apk"), join(tmp, "classes.jar")});
    Spawn d;
    d.argv = argv;
    d.env = ne.env;
    for (const std::string& line : split(runCapture(d), '\n'))
        if (!line.empty() && line.rfind("[apklayer] described", 0) != 0) puts(line.c_str());
    if (!exists(join(tmp, "info.properties"))) die("could not read " + baseName(apk));

    std::string pkg = property(join(tmp, "info.properties"), "package");
    std::string label = property(join(tmp, "info.properties"), "label");
    std::string dir = appDir(l, pkg);
    makeDirs(join(dir, "data"));
    std::string oldUid = property(join(dir, "info.properties"), "uid");
    for (const char* f : {"base.apk", "classes.jar", "key", "info.properties", "icon.png"}) sh({"mv", "-f", join(tmp, f), join(dir, f)});
    if (!oldUid.empty()) writeFile(join(dir, "info.properties"), readFile(join(dir, "info.properties")).value_or("") + "uid=" + oldUid + "\n");
    ensureUid(l, dir);
    extractLibs(dir);
    writeDesktop(l, pkg, label, dir);
    printf("apklayer: installed %s (%s) -> %s\n", pkg.c_str(), label.c_str(), dir.c_str());
    return pkg;
}

void listApps(const Layout& l) {
    for (const std::string& app : listDir(l.appsDir)) {
        std::string f = join(join(l.appsDir, app), "info.properties");
        if (!exists(f)) continue;
        printf("%-40s %-24s %s\n", property(f, "package").c_str(), property(f, "label").c_str(), property(f, "versionName").c_str());
    }
}

void uninstallApp(const Layout& l, const std::string& package) {
    if (!isDir(appDir(l, package))) die("not installed: " + package);
    removeTree(appDir(l, package));
    unlink(join(l.dataHome, "applications/apklayer-" + package + ".desktop").c_str());
    printf("apklayer: removed %s\n", package.c_str());
}

}  // namespace apklayer
