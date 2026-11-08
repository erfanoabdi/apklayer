#include "apps.h"
#include "sandbox.h"

#include "desktop.h"
#include "manage.h"  // settingsChanged: `ime` applies to a running instance
#include "flatpak.h"
#include "env.h"  // findJava: the converter and the install tools run on the apps' JVM (a phone names it in ~/.config/apklayer/env)
#include "version.h"
#include "autostart.h"  // microG Services turns the instance always on

#include <algorithm>
#include <iostream>
#include <cstdlib>
#include <sys/wait.h>
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

// Extracts the app's libraries of one ABI, from the base and its splits (an ABI split carries them), in the order Android's installer picks from
// ro.product.cpu.abilist: the CPU's own, the native bridge's when it is staged, then 32-bit ARM when the 32-bit runtime is staged (its own JVM,
// Layout::arm32Layout). Returns the route (info.properties nativeRoute): "native", "bridge-<isa>", "arm32", or "" when the APK has native
// libraries but none of a runnable ABI (Android's INSTALL_FAILED_NO_MATCHING_ABIS).
std::string extractLibs(const Layout& l, const std::string& dir) {
    removeTree(join(dir, "lib"));
    std::vector<std::string> names;
    for (const std::string& apk : apkFiles(dir)) {
        Spawn list;
        list.argv = {"unzip", "-Z1", join(dir, apk)};
        list.env = currentEnv();
        int st = 0;
        names.push_back("\n" + runCapture(list, &st));
    }
    auto any = [&](const std::string& prefix) {
        for (const std::string& n : names) if (n.find("\n" + prefix) != std::string::npos) return true;
        return false;
    };
    std::vector<std::pair<std::string, std::string>> routes = {{kAbi, "native"}};  // ABI, route
    if (*kBridgeAbi && exists(join(l.androidLibs, kBridgeLib))) routes.push_back({kBridgeAbi, std::string("bridge-") + kBridgeIsa});
    if (l.hasArm32()) routes.insert(routes.end(), {{"armeabi-v7a", "arm32"}, {"armeabi", "arm32"}});
    std::string abi, route;
    for (const auto& [a, r] : routes)
        if (abi.empty() && any("lib/" + a + "/")) abi = a, route = r;
    if (abi.empty()) return any("lib/") ? "" : "native";
    const std::string prefix = "lib/" + abi + "/";
    std::vector<std::string> apks = apkFiles(dir);
    for (size_t i = 0; i < apks.size(); i++) {
        if (names[i].find("\n" + prefix) == std::string::npos) continue;
        makeDirs(join(dir, "lib"));
        sh({"unzip", "-o", "-j", "-q", join(dir, apks[i]), prefix + "*.so", "-d", join(dir, "lib")});
    }
    sh({"chmod", "-R", "0755", join(dir, "lib")});  // as Android's installer leaves them: some apps run a bundled lib*.so as a program (Puzzles' libpuzzlesgen.so)
    return route;
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
    // the classes' jar when there is one (tools/release/jdk-image.sh): a class data sharing dump exits the JVM on a non-empty directory here
    std::string sim = n + (exists(n + "/aosp/sim-classes.jar") ? "/aosp/sim-classes.jar" : "/aosp/sim-classes");
    // the 32-bit runtime's JNI shim (native/linux/arm/libandroid_runtime.so: Robolectric's loader looks it up here and in the sandbox) first
    std::string shim = exists(join(n, "shim.jar")) ? join(n, "shim.jar") + ":" : "";
    return shim + n + "/native-runtime.jar:" + l.hostClasses() + ":" + sim + ":" + n + "/aosp/Robolectric_all.jar:" + l.deps +
           "/android-stub.jar:" + l.dex2jvmJar();
}

std::string installApp(const Layout& l, const Instance& inst, const std::vector<std::string>& apks, bool allowDowngrade, bool system) {
    for (const std::string& apk : apks) if (!exists(apk)) die("no such file: " + apk);
    l.requireRuntime();
    if (!exists(l.hostClasses()) || !exists(l.dex2jvmJar()))
        die("not built: no " + l.hostClasses() + " (docs/INSTALL.md: meson setup, ninja, meson install)");
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
    // the ABIs this machine runs, in extractLibs' order: a staged native bridge's and the 32-bit runtime's too
    std::string abis = kAbi;
    if (*kBridgeAbi && exists(join(l.androidLibs, kBridgeLib))) abis += std::string(",") + kBridgeAbi;
    if (l.hasArm32()) abis += ",armeabi-v7a,armeabi";
    stage.argv = {findJava(l), "-cp", l.hostClasses(), "apklayer.tools.ApkSet", tmp, abis};
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
        conv.argv = {findJava(l), "-Duser.home=" + tmp, "-cp", l.dex2jvmJar() + ":" + l.frameworkDir() + "/*", "io.github.kksimp.dex2jvm.Main", join(tmp, "base.apk"), join(tmp, "raw.jar")};
        conv.env = env;
        conv.logFile = join(inst.logs(), "convert.log");
        struct stat logStat{};
        size_t logStart = stat(conv.logFile.c_str(), &logStat) == 0 ? logStat.st_size : 0;  // the log is appended to: only this conversion's lines
        if (runAndWait(conv) != 0) die("conversion failed (see " + conv.logFile + ")");
        // a class dex2jvm could not convert is installed as a stub that throws VerifyError when used (as ART's verifier fails one): say which
        std::vector<std::string> stubbed;
        std::string log = readFile(conv.logFile).value_or("");
        for (const std::string& line : split(log.substr(std::min(logStart, log.size())), '\n'))
            if (size_t at = line.find("] STUBBED class "); at != std::string::npos) {
                std::string cls = line.substr(at + 16, line.find(':', at + 16) - at - 16);
                std::replace(cls.begin(), cls.end(), '/', '.');
                stubbed.push_back(cls);
            }
        if (!stubbed.empty()) {
            std::string names;
            for (size_t i = 0; i < stubbed.size() && i < 5; i++) names += (i ? ", " : "") + stubbed[i];
            printf("apklayer: warning: %zu class%s of %s could not be converted, a use of one throws VerifyError (%s%s; see %s)\n", stubbed.size(),
                   stubbed.size() == 1 ? "" : "es", name.c_str(), names.c_str(), stubbed.size() > 5 ? ", ..." : "", conv.logFile.c_str());
        }
        if (sh({findJava(l), "-cp", l.deps + "/libs/*:" + l.hostClasses(), "apklayer.tools.DenyInstrument", join(tmp, "raw.jar"), join(tmp, "classes.jar")}) != 0)
            die("preparing the converted classes failed (a half-written classes.jar is not installed)");
        // every DEX class in a well-formed class file and the APK's java resources reachable: nothing half-converted is installed or cached
        // ASM's CheckClassAdapter comes from Robolectric_all.jar, which lacks asm-analysis: on the boot class path it is not verified, so the data-flow classes it names are never loaded
        if (sh({findJava(l), "-Xbootclasspath/a:" + l.native + "/aosp/Robolectric_all.jar", "-cp", l.hostClasses() + ":" + l.dex2jvmJar(), "apklayer.tools.VerifyInstall", join(tmp, "base.apk"), join(tmp, "classes.jar")}) != 0)
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
    if (!property(info, "missingLibraries").empty()) {  // PackageManagerService: a required <uses-library> the device does not have
        std::cerr << "apklayer: INSTALL_FAILED_MISSING_SHARED_LIBRARY: " << pkg << " requires " << property(info, "missingLibraries") << std::endl;
        std::exit(kInstallMissingLibrary);
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
    // the route of its native code (extracted into the staging directory, so a refused app leaves nothing behind)
    std::string route = extractLibs(l, tmp);
    if (route.empty()) {
        std::cerr << "apklayer: INSTALL_FAILED_NO_MATCHING_ABIS: " << pkg << " has native libraries only for ABIs this machine cannot run (it runs "
                  << kAbi << (*kBridgeAbi && exists(join(l.androidLibs, kBridgeLib)) ? std::string(", ") + kBridgeAbi : std::string())
                  << (l.hasArm32() ? ", armeabi-v7a, armeabi" : "") << ")" << std::endl;
        std::exit(kInstallNoMatchingAbis);
    }
    makeDirs(dir);
    std::string oldUid = property(old, "uid");
    for (const std::string& f : apkFiles(dir)) if (f != "base.apk") unlink(join(dir, f).c_str());  // the update's own splits replace the old ones
    for (const std::string& f : apkFiles(tmp)) {
        sh({"mv", "-f", join(tmp, f), join(dir, f)});
        chmod(join(dir, f).c_str(), 0644);  // readable by every app, as Android's /data/app (a sandboxed app's own uid too); a copy keeps its source's 0600
    }
    for (const char* f : {"classes.jar", "key", "icon.png"}) sh({"mv", "-f", join(tmp, f), join(dir, f)});
    removeTree(join(dir, "lib"));
    if (isDir(join(tmp, "lib"))) sh({"mv", join(tmp, "lib"), join(dir, "lib")});
    // info.properties, with its uid, is the commit: a hub or JVM that meets the package while it is installed sees all of it or none
    if (!oldUid.empty()) writeFile(info, readFile(info).value_or("") + "uid=" + oldUid + "\n");
    // the route of its native code: the CPU's own, translated through the native bridge, or a 32-bit process (the manager shows it)
    setProperty(info, "nativeRoute", route);
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
    // microG Services holds the cloud-messaging connection for every app: pushes reach closed apps only while the instance runs. Once per instance
    // (its marker), so an update or a user's later `off` stays as it is
    const std::string asked = join(inst.stateDir, "autostart-microg");
    if (pkg == "com.google.android.gms" && !exists(asked)) {
        writeFile(asked, "1\n");
        if (!autostartOn(inst)) {
            printf("apklayer: microG Services delivers push messages to closed apps: turning instance %s always on (`apklayer instance autostart %s off` undoes it)\n",
                   inst.id.c_str(), inst.id.c_str());
            autostartCommand(l, {inst.id, "on"});
        }
    }
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
    std::string failed;
    for (const std::string& pkg : packages) {
        std::string dir = appDir(l, pkg);
        if (!exists(join(dir, "base.apk"))) die("not installed: " + pkg);
        std::vector<std::string> apks;
        for (const std::string& f : apkFiles(dir)) apks.push_back(join(dir, f));
        // an update with the same APKs: the converter ABI is in the cache key, so they convert again; uid, system mark and data stay.
        if (target != "--all-stale") { installApp(l, inst, apks, true); break; }  // its failure message is the command's last line
        // Each in a child: installApp die()s on a failure, which then ends only this package's attempt (its old install is untouched).
        fflush(stdout);
        pid_t child = fork();
        if (child == 0) { installApp(l, inst, apks, true); fflush(stdout); _exit(0); }
        int st = -1;
        if (child < 0 || waitpid(child, &st, 0) != child || !WIFEXITED(st) || WEXITSTATUS(st) != 0) failed += " " + pkg;
    }
    if (packages.empty()) puts("apklayer: no stale apps");
    if (failed.empty()) return 0;
    fprintf(stderr, "apklayer: not converted again (their old install stays):%s\n", failed.c_str());
    return 1;
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
    std::string file = join(inst.root, "input-methods"), kept;  // read by the hub (HubBootSettings)
    for (const std::string& line : split(readFile(file).value_or(""), '\n'))
        if (!trim(line).empty() && trim(line) != name) kept += trim(line) + "\n";
    if (action == "enable") kept += name + "\n";
    if (!writeFile(file, kept)) die("cannot write " + file);
    printf("apklayer: %s %sd for instance %s (%s)\n", name.c_str(), action.c_str(), inst.id.c_str(), settingsChanged(l, inst) ? "applied now" : "from its next start");
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
    // DeletePackageHelper: a static or SDK library an installed app uses keeps its provider (AppMetadata: "staticLibraries", "usesStaticLibraries")
    for (const std::string& lib : split(property(join(appDir(l, package), "info.properties"), "staticLibraries"), ';')) {
        if (lib.empty()) continue;
        for (const std::string& app : listDir(l.appsDir)) {
            if (app == package) continue;
            for (const std::string& used : split(property(join(join(l.appsDir, app), "info.properties"), "usesStaticLibraries"), ';')) {
                if (used != lib) continue;
                std::cerr << "apklayer: DELETE_FAILED_USED_SHARED_LIBRARY: " << package << " provides " << lib << " to " << app << std::endl;
                std::exit(kUninstallUsedLibrary);
            }
        }
    }
    removeTree(appDir(l, package));
    for (const std::string& instance : listDir(join(l.dataHome, "apklayer"))) {  // the app's data in every instance of this data home
        std::string data = join(l.dataHome, "apklayer/" + instance + "/app-data/" + package);
        if (isDir(data)) { reclaim(data); removeTree(data); }  // reclaim: files the sandbox's app uid owns
        setHomeAccess(join(l.dataHome, "apklayer/" + instance), package, false);
        unlink(join(l.dataHome, "apklayer/" + instance + "/notification-channels/" + package).c_str());  // the hub's channels of the app (HubNotificationChannels)
        // and its permission answers (cli/manage.cpp): a reinstall starts as a new install, asked again, as on Android
        for (const char* name : {"/permissions-revoked", "/permissions-granted"}) {
            std::string answers = join(l.dataHome, "apklayer/" + instance + name), kept;
            if (!exists(answers)) continue;
            for (const std::string& line : split(readFile(answers).value_or(""), '\n')) if (!line.empty() && line.rfind(package + "\t", 0) != 0) kept += line + "\n";
            writeFile(answers, kept);
        }
    }
    unlink(join(hostXdg("XDG_DATA_HOME", ".local/share"), "applications/apklayer-" + package + ".desktop").c_str());
    printf("apklayer: removed %s\n", package.c_str());
}

}  // namespace apklayer
