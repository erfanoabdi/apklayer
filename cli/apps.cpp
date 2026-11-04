#include "apps.h"
#include "sandbox.h"

#include "desktop.h"
#include "version.h"

#include <algorithm>
#include <iostream>
#include <cstdlib>
#include <sys/stat.h>
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
std::string cacheKey(const Layout& l, const std::string& apk, const std::vector<std::string>& splits) {
    std::string text = sha256File(apk);
    for (const std::string& s : splits) text += " " + sha256File(s);  // a split's code converts with the base (dex2jvm reads split_*.apk beside base.apk)
    text += "\nconverter-abi " + std::to_string(kConverterAbi);
    text += "\npost-processing v14: verified, Android classloader adapter, java.* own classes relocated (no library relocation: apps have their own class loader), static finals written outside clinit, native loader, multidex no-op, in-process services, Unsafe.getUnsafe, Method.invoke\n";
    std::vector<std::string> jars = listDir(l.frameworkDir());
    std::sort(jars.begin(), jars.end());
    for (const std::string& j : jars)
        if (j.size() > 4 && j.substr(j.size() - 4) == ".jar") text += sha256File(join(l.frameworkDir(), j)) + "\n";
    if (auto commit = readFile(join(l.root, "runtime/dex2jvm/.dex2jvm-commit"))) text += *commit;
    text += sha256File(l.dex2jvmJar()) + "\n";  // the converter as built: a change to its code converts again
    return sha256Text(text);
}

// The installed APKs: base.apk, then its splits (split_<name>.apk), as Android keeps them.
std::vector<std::string> apkFiles(const std::string& dir) {
    std::vector<std::string> found;
    for (const std::string& f : listDir(dir))
        if (f.rfind("split_", 0) == 0 && f.size() > 4 && f.substr(f.size() - 4) == ".apk") found.push_back(f);
    std::sort(found.begin(), found.end());
    found.insert(found.begin(), "base.apk");
    return found;
}

void extractLibs(const std::string& dir) {  // from the base and its splits (an ABI split carries them)
    removeTree(join(dir, "lib"));
    const std::string prefix = std::string("lib/") + kAbi + "/";
    for (const std::string& apk : apkFiles(dir)) {
        Spawn list;
        list.argv = {"unzip", "-Z1", join(dir, apk)};
        list.env = currentEnv();
        int st = 0;
        std::string names = runCapture(list, &st);
        if (names.find("\n" + prefix) == std::string::npos && names.rfind(prefix, 0) != 0) continue;
        makeDirs(join(dir, "lib"));
        sh({"unzip", "-o", "-j", "-q", join(dir, apk), prefix + "*.so", "-d", join(dir, "lib")});
    }
    if (isDir(join(dir, "lib"))) sh({"chmod", "-R", "0755", join(dir, "lib")});  // as Android's installer leaves them: some apps run a bundled lib*.so as a program (Puzzles' libpuzzlesgen.so)
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

std::string hostClasspath(const Layout& l) {  // no app classes: they get a loader of their own (apklayer.host.AppClassLoader)
    const std::string& n = l.native;
    return n + "/native-runtime.jar:" + l.hostClasses() + ":" + n + "/aosp/sim-classes:" + n + "/aosp/Robolectric_all.jar:" + l.deps +
           "/android-stub.jar:" + l.dex2jvmJar();
}

std::string installApp(const Layout& l, const Instance& inst, const std::vector<std::string>& apks, bool allowDowngrade, bool system) {
    for (const std::string& apk : apks) if (!exists(apk)) die("no such file: " + apk);
    l.requireRuntime();
    if (!isDir(l.hostClasses()) || !exists(l.dex2jvmJar()))
        die("not built: run 'meson setup b && ninja -C b' and tools/build host " + l.native + " in " + l.root);
    makeDirs(l.appsDir);
    char pattern[4096];
    snprintf(pattern, sizeof pattern, "%s/.install.XXXXXX", l.appsDir.c_str());
    if (!mkdtemp(pattern)) die("cannot create a temporary directory in " + l.appsDir);
    std::string tmp = pattern;
    static std::string pending;  // die() exits without running destructors: the temporary directory goes at exit
    if (pending.empty()) std::atexit([] { if (!pending.empty()) removeTree(pending); });
    pending = tmp;

    // base.apk and split_<name>.apk, as a device installs a package (apklayer.tools.ApkSet: one APK, a base and its splits, or an .apks/.xapk/.apkm)
    Spawn stage;
    stage.argv = {"java", "-cp", l.hostClasses(), "apklayer.tools.ApkSet", tmp, kAbi};
    stage.argv.insert(stage.argv.end(), apks.begin(), apks.end());
    stage.env = currentEnv();
    if (runAndWait(stage) != 0) die("not an installable package: " + apks[0]);
    std::vector<std::string> splits;
    for (const std::string& f : apkFiles(tmp)) if (f != "base.apk") splits.push_back(join(tmp, f));
    std::string key = cacheKey(l, join(tmp, "base.apk"), splits);
    std::string reuse;
    for (const std::string& app : listDir(l.appsDir)) {
        std::string d = join(l.appsDir, app);
        if (auto k = readFile(join(d, "key")); k && trim(*k) == key && exists(join(d, "classes.jar"))) { reuse = join(d, "classes.jar"); break; }
    }
    std::string name = baseName(apks[0]);
    if (!reuse.empty()) {
        sh({"cp", reuse, join(tmp, "classes.jar")});
    } else {
        printf("apklayer: converting %s\n", name.c_str());
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
        // every DEX class in a well-formed class file and the APK's java resources reachable: nothing half-converted is installed or cached
        // ASM's CheckClassAdapter comes from Robolectric_all.jar, which lacks asm-analysis: on the boot class path it is not verified, so the data-flow classes it names are never loaded
        if (sh({"java", "-Xbootclasspath/a:" + l.native + "/aosp/Robolectric_all.jar", "-cp", l.hostClasses() + ":" + l.dex2jvmJar(), "apklayer.tools.VerifyInstall", join(tmp, "base.apk"), join(tmp, "classes.jar")}) != 0)
            die("the converted classes are incomplete or malformed (see above); " + name + " is not installed");
        unlink(join(tmp, "raw.jar").c_str());
    }
    writeFile(join(tmp, "key"), key + "\n");
    // describe renders the icon with the device natives, so the services must be up
    NativeEnv ne = nativeEnv(l, inst);
    if (!ensureStack(l, inst)) die("the Android services are not available");
    std::vector<std::string> argv = hostJvmArgs(l, inst, ne);
    argv.insert(argv.end(), {"-Djava.awt.headless=true", "-Dapklayer.apps=" + l.appsDir, "-cp", hostClasspath(l), "apklayer.host.Main", "--describe", tmp, join(tmp, "base.apk"), join(tmp, "classes.jar")});
    Spawn d;
    d.argv = argv;
    d.env = ne.env;
    for (const std::string& line : split(runCapture(d), '\n'))
        if (!line.empty() && line.rfind("[apklayer] described", 0) != 0) puts(line.c_str());
    if (!exists(join(tmp, "info.properties"))) die("could not read " + name);

    std::string info = join(tmp, "info.properties");
    std::string pkg = property(info, "package");
    std::string label = property(info, "label");
    std::string dir = appDir(l, pkg);
    std::string old = join(dir, "info.properties");
    // an update as Android allows it (INSTALL_FAILED_UPDATE_INCOMPATIBLE, INSTALL_FAILED_VERSION_DOWNGRADE): the same signer, no lower version unless asked
    if (property(info, "signer").empty()) {  // PackageManagerService refuses an APK without a readable signing certificate
        std::cerr << "apklayer: INSTALL_PARSE_FAILED_NO_CERTIFICATES: " << name << " is not signed" << std::endl;
        std::exit(kInstallUnsigned);
    }
    if (!property(info, "signerMismatch").empty()) {
        std::cerr << "apklayer: INSTALL_FAILED_UPDATE_INCOMPATIBLE: " << pkg << " is installed with another signature" << std::endl;
        std::exit(kInstallIncompatible);
    }
    if (exists(old) && !allowDowngrade && atoll(property(info, "versionCode").c_str()) < atoll(property(old, "versionCode").c_str())) {
        std::cerr << "apklayer: INSTALL_FAILED_VERSION_DOWNGRADE: " << pkg << " " << property(old, "versionCode") << " is installed, this is "
                  << property(info, "versionCode") << " (install -d allows it)" << std::endl;
        std::exit(kInstallDowngrade);
    }
    makeDirs(dir);
    std::string oldUid = property(old, "uid");
    for (const std::string& f : apkFiles(dir)) if (f != "base.apk") unlink(join(dir, f).c_str());  // the update's own splits replace the old ones
    for (const std::string& f : apkFiles(tmp)) {
        sh({"mv", "-f", join(tmp, f), join(dir, f)});
        chmod(join(dir, f).c_str(), 0644);  // readable by every app, as Android's /data/app (a sandboxed app's own uid too); a copy keeps its source's 0600
    }
    for (const char* f : {"classes.jar", "key", "icon.png"}) sh({"mv", "-f", join(tmp, f), join(dir, f)});
    extractLibs(dir);
    // info.properties, with its uid, is the commit: a hub or JVM that meets the package while it is installed sees all of it or none
    if (!oldUid.empty()) writeFile(info, readFile(info).value_or("") + "uid=" + oldUid + "\n");
    // an update keeps the system mark and the app drawer choice; --system (the staging of the tree's system apps) marks it
    if (system || isSystemApp(old)) setProperty(info, "system", "true");
    if (std::string drawer = property(old, "drawer"); !drawer.empty()) setProperty(info, "drawer", drawer);
    setProperty(info, "converterAbi", std::to_string(kConverterAbi));
    ensureUid(l, tmp);
    sh({"mv", "-f", info, join(dir, "info.properties")});
    writeDesktop(l, pkg);
    removeTree(tmp);  // now, not at exit: reconvert --all-stale installs several in one run
    pending.clear();
    printf("apklayer: installed %s (%s) -> %s\n", pkg.c_str(), label.c_str(), dir.c_str());
    return pkg;
}

// none recorded: converted before the number existed, which is ABI 1 (installs from then are not stale under 1)
bool isStale(const std::string& info) {
    std::string abi = property(info, "converterAbi");
    return (abi.empty() ? 1 : atoi(abi.c_str())) < kConverterAbi;
}

int reconvert(const Layout& l, const Instance& inst, const std::string& target) {
    std::vector<std::string> packages;
    if (target != "--all-stale") packages.push_back(target);
    else for (const std::string& app : listDir(l.appsDir)) if (exists(join(appDir(l, app), "info.properties")) && isStale(join(appDir(l, app), "info.properties"))) packages.push_back(app);
    for (const std::string& pkg : packages) {
        std::string dir = appDir(l, pkg);
        if (!exists(join(dir, "base.apk"))) die("not installed: " + pkg);
        std::vector<std::string> apks;
        for (const std::string& f : apkFiles(dir)) apks.push_back(join(dir, f));
        // an update with the same APKs: the converter ABI is in the cache key, so they convert again; uid, system mark and data stay
        installApp(l, inst, apks, true);
    }
    if (packages.empty()) puts("apklayer: no stale apps");
    return 0;
}

std::string appDataDir(const Layout& l, const Instance& inst, const std::string& package) {
    std::string own = inst.appData(package), shared = join(appDir(l, package), "data");
    // before app data was per instance it lived beside the app's code, shared by every instance: the default instance keeps it
    if (inst.id == "default" && !exists(own) && isDir(shared)) { makeDirs(dirName(own)); if (rename(shared.c_str(), own.c_str()) != 0) die("cannot move " + shared + " to " + own); }
    makeDirs(own);
    return own;
}

int inputMethod(const Layout& l, const Instance& inst, const std::string& action, const std::string& name) {
    if (action != "enable" && action != "disable") die("ime enable|disable <package or ime id>");
    if (action == "enable" && !isDir(appDir(l, name.substr(0, name.find('/'))))) die("not installed: " + name);
    std::string file = join(inst.root, "input-methods"), kept;  // read by the hub at its start (HubBootSettings)
    for (const std::string& line : split(readFile(file).value_or(""), '\n'))
        if (!trim(line).empty() && trim(line) != name) kept += trim(line) + "\n";
    if (action == "enable") kept += name + "\n";
    if (!writeFile(file, kept)) die("cannot write " + file);
    printf("apklayer: %s %sd for instance %s (from its next start)\n", name.c_str(), action.c_str(), inst.id.c_str());
    return 0;
}

void listApps(const Layout& l) {
    for (const std::string& app : listDir(l.appsDir)) {
        std::string f = join(join(l.appsDir, app), "info.properties");
        if (!exists(f)) continue;
        printf("%-40s %-24s %-16s %s\n", property(f, "package").c_str(), property(f, "label").c_str(), property(f, "versionName").c_str(), isSystemApp(f) ? "system" : "");
    }
}

void uninstallApp(const Layout& l, const std::string& package) {
    if (!isDir(appDir(l, package))) die("not installed: " + package);
    removeTree(appDir(l, package));
    for (const std::string& instance : listDir(join(l.dataHome, "apklayer"))) {  // the app's data in every instance of this data home
        std::string data = join(l.dataHome, "apklayer/" + instance + "/app-data/" + package);
        if (isDir(data)) { reclaim(data); removeTree(data); }  // reclaim: files the sandbox's app uid owns
        // and its revoked permissions (cli/manage.cpp): a reinstall starts with the grants of a new install, as on Android
        std::string revoked = join(l.dataHome, "apklayer/" + instance + "/permissions-revoked"), kept;
        if (!exists(revoked)) continue;
        for (const std::string& line : split(readFile(revoked).value_or(""), '\n')) if (!line.empty() && line.rfind(package + "\t", 0) != 0) kept += line + "\n";
        writeFile(revoked, kept);
    }
    unlink(join(l.dataHome, "applications/apklayer-" + package + ".desktop").c_str());
    printf("apklayer: removed %s\n", package.c_str());
}

}  // namespace apklayer
