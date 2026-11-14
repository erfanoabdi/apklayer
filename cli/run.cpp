#include "run.h"
#include "apps.h"

#include "config.h"
#include "logs.h"
#include "manage.h"
#include "sandbox.h"
#include "spare.h"
#include "stack.h"
#include "unit.h"

#include <algorithm>
#include <cerrno>
#include <cstdint>
#include <cstring>
#include <dirent.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <sys/file.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/un.h>
#include <sys/wait.h>
#include <unistd.h>

namespace apklayer {

namespace {
const char* kHubReadyProperty = "apklayer.hub.ready";
const char* kSystemProcessPackage = "com.android.providers.settings";  // runs in the hub (runHub, HubHost)
// Chromium's switch file (/data/local/tmp/webview-command-line) in the instance's own /data, where Chromium reads it through the path layer
// (APKLAYER_DATA); a launch from inside the instance (the hub's component host) reads the same file. Its seccomp sandbox cannot run in an app JVM
// and is off for the provider route (user decision, 2026-10-27; tools/host-setup/webview.sh). Written here, as the user: no root, and never the
// host's /data (on a Halium phone that is the device's own Android data).
void webviewSandboxOff(const Instance& inst) {
    const std::string text = "webview --no-sandbox --disable-seccomp-filter-sandbox\n";
    std::string own = join(inst.dataDir, "local/tmp/webview-command-line");
    if (readFile(own) != text) { makeDirs(dirName(own)); writeFile(own, text); }
}

// The title of a launch's window (its virtual display's): identifies instance and launch. Cut to the host display HAL's title limit.
std::string windowTitle(const Instance& inst, const std::string& launcher, const std::string& package) {
    return ("apklayer:" + launcher + ":" + inst.id + ":" + package).substr(0, 60);
}

// The Android process a window JVM of the package runs: its command line once named (AppEnvironment.startRuntime), its option before that, else the package's.
std::string processOf(const std::string& pid, const std::string& package) {
    std::string cmd = readFile("/proc/" + pid + "/cmdline").value_or("");
    if (cmd.rfind(package + ":", 0) == 0) return cmd.substr(0, cmd.find('\0'));
    const std::string key = "-Dapklayer.processname=";
    if (size_t at = cmd.find(key); at != std::string::npos) return cmd.substr(at + key.size(), cmd.find('\0', at) - at - key.size());
    return package;
}

// The launcher pid (its run.<pid> record, the layer stack) of a running window JVM of the package, or of one of its android:process names
// ("<package>:jvm"); empty: none (component hosts have no window sink).
std::string windowLaunch(const Layout& l, const Instance& inst, const std::string& target) {
    std::string found, package = target.substr(0, target.find(':'));
    auto running = launches(l, inst);  // kept: a range-for over a member of a temporary would outlive it
    for (const std::string& record : running[package]) {
        std::vector<std::string> pids = split(readFile(join(inst.stateDir, record)).value_or(""), '\n');
        if (pids.size() >= 2 && atoi(pids[1].c_str()) > 0 && processOf(pids[0], package) == target) found = record.substr(strlen("run."));
    }
    return found;
}

// `apklayer run` of an app whose window JVM runs in this instance (Android: a launcher or `am start` reaches the running process, never a second
// one): the hub hands the intent (APKLAYER_INTENT_FILE) or a launcher start to it, and the shell raises its window. False: start a process.
bool relaunch(const Layout& l, const Instance& inst, const NativeEnv& ne, const std::string& package) {
    std::string launcher = windowLaunch(l, inst, package);
    if (launcher.empty()) return false;
    // a launch that is still starting may have started the hub only now: the hub takes the intent once it is up (queued for the app: HubActivities.relaunch)
    if (!waitForProperty(l, ne, kHubReadyProperty, "1", -1)) return false;
    std::string window = windowTitle(inst, launcher, package);
    std::string intent = getenvOr("APKLAYER_INTENT_FILE", "");
    if (!intent.empty()) intent = realPath(intent);
    Spawn call;  // HubProtocol.RELAUNCH (37) of apklayer.IInstanceHub: 0 when the running app has it
    call.argv = {l.btload(), l.androidLibs, "libapklayer_servicetool.so", "--main", "call", "apklayer.instance", "37", "apklayer.IInstanceHub", "s16", package, "s16", intent};
    call.env = ne.env;
    call.logFile = join(inst.logs(), "relaunch.log");
    if (runAndWait(call) != 0) return false;
    // MSG_ACTIVATE (bridge/shell/activation.c) with the token a desktop launcher gave this command, if any
    std::string payload = window + std::string(1, '\0') + getenvOr("XDG_ACTIVATION_TOKEN", "");
    uint32_t header[2] = {12, (uint32_t)payload.size()};
    int fd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    sockaddr_un addr{};
    addr.sun_family = AF_UNIX;
    snprintf(addr.sun_path, sizeof addr.sun_path, "%s", l.shellSocket().c_str());
    bool raised = connect(fd, reinterpret_cast<sockaddr*>(&addr), sizeof addr) == 0 && write(fd, header, sizeof header) == sizeof header
                  && write(fd, payload.data(), payload.size()) == (ssize_t)payload.size();
    close(fd);
    printf("apklayer: %s runs in instance %s: launch handed to it%s\n", package.c_str(), inst.id.c_str(), raised ? "" : " (its window not raised: no shell)");
    return true;
}
}  // namespace

// The JVM options that make a host JVM this package's process (its data, uid, library dir), then its APK and classes jar: an app launch and a component
// host start with them; the instance's spare takes them with its launch (cli/spare.cpp).
std::vector<std::string> packageJvmArgs(const Layout& l, const Instance& inst, const std::string& package) {
    std::string dir = appDir(l, package);
    if (!exists(join(dir, "info.properties"))) die("not installed: " + package + " (apklayer install <app.apk>)");
    if (isStale(join(dir, "info.properties"))) fprintf(stderr, "apklayer: %s was converted by an older converter ABI; run `apklayer reconvert %s` (its data stays) for the current runtime identity\n", package.c_str(), package.c_str());
    ensureUid(l, dir);
    std::string data = appDataDir(l, inst, package);
    if (!sandboxOn()) reclaim(data);  // the sandbox ran it as the app's own uid; without it the app runs as the user
    std::vector<std::string> a = {"-Dapklayer.data=" + data, "-Dapklayer.package=" + package, "-Dapklayer.uid=" + property(join(dir, "info.properties"), "uid"),
                                  "-Dapklayer.libdir=" + join(dir, "lib")};
    if (property(join(dir, "info.properties"), "system") == "true") a.push_back("-Dapklayer.system=true");  // ApplicationInfo.FLAG_SYSTEM of its own package
    if (std::string route = property(join(dir, "info.properties"), "nativeRoute"); route.rfind("bridge-", 0) == 0)
        a.push_back("-Dapklayer.nativebridge=" + route.substr(7));  // its libraries load through the native bridge (NativeLoader)
    if (!getenvOr("APKLAYER_ISOLATED", "").empty()) a.push_back("-Dapklayer.isolated=" + getenvOr("APKLAYER_ISOLATED", ""));
    // a window process the hub starts for an activity of another android:process ("<package>:jvm"): its name (HostProcessName.windowProcess)
    if (!getenvOr("APKLAYER_PROCESS", "").empty()) a.push_back("-Dapklayer.processname=" + getenvOr("APKLAYER_PROCESS", ""));
    a.push_back(join(dir, "base.apk"));
    a.push_back(join(dir, "classes.jar"));
    return a;
}

std::vector<std::string> windowJvmArgs(const Layout& l, const Instance& inst, NativeEnv& ne) {
    // the Chromium WebView provider is the default WebView once installed; APKLAYER_WEBVIEW_PROVIDER=none disables it
    std::string provider = getenvOr("APKLAYER_WEBVIEW_PROVIDER", "\x01");
    if (provider == "\x01") provider = exists(join(l.appsDir, "com.android.webview/classes.jar")) ? "com.android.webview" : "";
    if (provider == "none") provider.clear();
    if (!provider.empty()) webviewSandboxOff(inst);
    std::vector<std::string> jvm = hostJvmArgs(l, inst, ne);
    ne.env["LD_LIBRARY_PATH"] = l.native + ":" + join(l.native, "glibc") + ":" + ne.env["LD_LIBRARY_PATH"];  // bionic linker + its libs
    jvm.push_back("-Dapklayer.apps=" + l.appsDir);
    jvm.push_back("-Dapklayer.cli=" + l.cliBinary());
    if (!provider.empty()) jvm.push_back("-Dapklayer.webviewprovider=" + join(l.appsDir, provider));  // its classes get a loader of their own (ShadowHostWebViewFactory)
    if (!getenvOr("APKLAYER_LOGCAT", "").empty()) jvm.push_back("-Dapklayer.logcat=true");
    for (const std::string& o : split(getenvOr("APKLAYER_JOPTS", ""), ' '))
        if (!o.empty() && o.rfind("-Dapklayer.runms=", 0) != 0) jvm.push_back(o);  // the run time is the launch's own (launchOptions)
    jvm.insert(jvm.end(), {"-cp", hostClasspath(l), "apklayer.host.Main"});
    return jvm;
}

namespace {
// What a launch adds before -cp (an app spare sets them as properties with its target): its window, its package, and the JOPTS' properties again
// (after the package's, as on the command line).
std::vector<std::string> launchOptions(const std::vector<std::string>& window, const std::vector<std::string>& own) {
    std::vector<std::string> a = window;
    a.insert(a.end(), own.begin(), own.end());
    for (const std::string& o : split(getenvOr("APKLAYER_JOPTS", ""), ' ')) if (o.rfind("-D", 0) == 0) a.push_back(o);
    return a;
}
}  // namespace

int runApp(const Layout& l, const Instance& inst, const std::string& package, RunMode mode) {
    applySettings(package);  // the app's own settings over the global ones (cli/config.cpp)
    std::vector<std::string> own = packageJvmArgs(l, inst, package);
    std::vector<std::string> files(own.end() - 2, own.end());  // the APK and classes jar: Main's arguments
    own.resize(own.size() - files.size());
    if (mode == RunMode::Component) own.push_back("-Dapklayer.componenthost=true");
    std::string dir = appDir(l, package);

    trace("run " + package);
    std::string appShell = getenvOr("APKLAYER_APP_SHELL", "");  // a Lomiri drawer launch's own shell (cli/halium.cpp): this window's only, not what the launch starts
    unsetenv("APKLAYER_APP_SHELL");
    NativeEnv ne = nativeEnv(l, inst);
    if (!ensureStack(l, inst)) return 1;
    trace("stack ready");
    if (!ensureShell(l, inst, ne)) return 1;
    // a process for another android:process is always a new one (the hub starts it only when that process is not running)
    std::string process = getenvOr("APKLAYER_PROCESS", "");
    if (mode == RunMode::App && process.empty() && relaunch(l, inst, ne, package)) return 0;
    if (mode == RunMode::App) {  // the launch's output and the app's android.util.Log go to <logs>/app-<package or process>.log too (`apklayer logs`, `apklayer report`)
        teeLaunchOutput(inst, process.empty() ? package : process);
        own.push_back("-Dapklayer.applog=" + appLogPath(inst, process.empty() ? package : process));
    }
    if (mode == RunMode::App) {
        // the hub's JVM boots alongside the app's instead of after its Application.onCreate (HubStartup then finds it); an `apklayer hub` that finds
        // hub.lock taken (the hub is up) returns at once
        Spawn hub;
        hub.argv = {l.cliBinary(), "--instance", inst.id, "hub"};
        hub.env = ne.env;
        hub.logFile = join(inst.logs(), "hub.log");
        hub.detach = true;
        spawn(hub);
    }

    // an arm32 app's JVM is the 32-bit runtime's (its JVM, native dir, libraries: Layout::arm32Layout); stack, shell and window sink stay 64-bit.
    // Its spare key never matches the 64-bit spare's.
    bool arm32 = property(join(dir, "info.properties"), "nativeRoute") == "arm32";
    if (arm32 && !l.hasArm32()) die(package + " needs the 32-bit runtime (" + l.arm32 + ": tools/armhf/build.sh), which is not staged");
    Layout lj = arm32 ? l.arm32Layout() : l;
    NativeEnv ne32 = arm32 ? nativeEnv(lj, inst) : NativeEnv{};
    if (arm32) ne32.env["APKLAYER_NATIVE"] = l.native;  // the CLI it runs (other launches, the hub) is the 64-bit one, with the 64-bit runtime
    NativeEnv& nj = arm32 ? ne32 : ne;
    std::vector<std::string> jvm = windowJvmArgs(lj, inst, nj), window;
    if (arm32) {  // its armhf preloads reach the JVM through its loader (bin/java), not the 64-bit shell and tools on the way
        ne32.env["APKLAYER_ARM32_PRELOAD"] = ne32.env["LD_PRELOAD"];
        ne32.env.erase("LD_PRELOAD");
    }
    if (mode == RunMode::Component) nj.env["APKLAYER_COMPONENT_HOST"] = "1";  // the launch's own environment (a spare gets it with its target)
    // under the sandbox a launch's intent file goes with it as text (its place is not in the app's view, and the app could not delete it there):
    // one line, newlines as \x1f (spare.cpp hands launch variables over as lines; Properties.store never writes control characters raw)
    std::string intentFile = getenvOr("APKLAYER_INTENT_FILE", "");
    if (sandboxOn() && mode == RunMode::App && !intentFile.empty())
        if (auto text = readFile(intentFile); text && text->size() < 65536) {  // an environment string's limit is 128 KiB
            std::replace(text->begin(), text->end(), '\n', '\x1f');
            nj.env["APKLAYER_INTENT_TEXT"] = *text;
            nj.env.erase("APKLAYER_INTENT_FILE");
            unlink(intentFile.c_str());
        }
    // under the sandbox a spare is one app's process from its start (cli/spare.cpp): an app window's launch takes the spare of its own package, or
    // starts it for the next; component hosts and launches with a large intent file start cold
    bool spare = !sandboxOn() || (mode == RunMode::App && !nj.env.count("APKLAYER_INTENT_FILE"));
    std::string sandboxPackage = sandboxOn() ? package : "";
    std::string spareKey = appSpareKey(l, inst, jvm, nj.env, sandboxPackage);
    int vdisplay = 0;
    int self = getpid();
    // one virtual display per app window (its own layer stack and sink per launch); pid-based ids cannot collide, and the title identifies instance and
    // launch. The primary display is headless, so the sink is required (a component host has no window).
    if (mode == RunMode::App) {
        if (!exists(join(l.androidLibs, "libapklayer_vdisplay.so"))) die("the runtime needs libapklayer_vdisplay.so (the primary display is headless)");
        std::string title = windowTitle(inst, std::to_string(self), package);
        {   // the window style (`apklayer settings`: window-mode, the app's own window-mode.<package> first), so the window is created in it; changes reach it live (the hub)
            std::string general, specific;
            for (const std::string& line : split(readFile(join(inst.root, "settings")).value_or(""), '\n')) {
                size_t eq = line.find('=');
                if (eq == std::string::npos) continue;
                if (line.compare(0, eq, "window-mode") == 0) general = line.substr(eq + 1);
                else if (line.compare(0, eq, "window-mode." + package) == 0) specific = line.substr(eq + 1);
            }
            if (!specific.empty() || !general.empty()) ne.env["APKLAYER_WINDOW_MODE"] = nj.env["APKLAYER_WINDOW_MODE"] = specific.empty() ? general : specific;
        }
        Spawn sink;
        sink.argv = {l.btload(), l.androidLibs, "libapklayer_vdisplay.so", "--main", title, std::to_string(self), getenvOr("APKLAYER_DISPLAY_WIDTH", "720"), getenvOr("APKLAYER_DISPLAY_HEIGHT", "1280")};
        sink.env = ne.env;
        if (!appShell.empty()) sink.env["APKLAYER_SHELL"] = appShell;
        sink.logFile = join(inst.logs(), "vdisplay-" + package + "-" + std::to_string(self) + ".log");
        vdisplay = spawn(sink);
        window = {"-Dapklayer.layerstack=" + std::to_string(self), "-Dapklayer.displaywindow=" + title};
        if (!appShell.empty()) window.push_back("-Dapklayer.shell=" + appShell);
    }
    std::vector<std::string> late = launchOptions(window, own);
    std::string record = join(inst.stateDir, "run." + std::to_string(self));
    // started: the launch's process is up (AppSpare.launchStarted), upon which the next spare prepares, as zygote forks the next app process
    int conn = -1, jvmPid = -1, started[2] = {-1, -1};
    if (spare && pipe2(started, O_CLOEXEC) == 0) {  // the instance's spare takes the launch when it matches (cli/spare.cpp)
        fchmod(started[1], 0622);  // the JVM reopens it through /proc/self/fd (AppSpare.launchStarted): a sandboxed one is another uid than the pipe's
        std::vector<std::string> target = late;
        target.insert(target.end(), files.begin(), files.end());
        jvmPid = takeAppSpare(inst, spareKey, target, nj.env, started[1], &conn);
        if (jvmPid > 0) trace("app spare taken");
    }
    jvm.insert(jvm.end() - 3, late.begin(), late.end());
    jvm.insert(jvm.end(), files.begin(), files.end());

    Spawn app;
    app.argv = jvm;
    app.env = nj.env;
    app.cwd = dir;
    app.blockSigpipe = true;
    if (sandboxOn()) sandboxSpawn(l, inst, app, package);  // the JVM as the app's own uid in its own view (cli/sandbox.cpp)
    if (jvmPid <= 0 && started[1] >= 0) { fcntl(started[1], F_SETFD, 0); app.env["APKLAYER_STARTED_FD"] = std::to_string(started[1]); }
    if (started[0] >= 0) { startAppSpare(l, inst, started[0], sandboxPackage); close(started[0]); }
    if (mode == RunMode::Component && jvmPid <= 0) {
        // a cold service host has no window sink to clean up: become the JVM, so the launcher dies exactly when the host does (the hub restarts
        // a killed host as soon as its launcher is gone)
        writeFile(record, std::to_string(self) + "\n");
        execReplace(app);
    }
    if (jvmPid <= 0) {
        jvmPid = spawn(app);
        trace("app jvm spawned");
    }
    if (started[1] >= 0) close(started[1]);
    // `apklayer stop` ends what this launch started: the JVM and its window sink
    writeFile(record, std::to_string(jvmPid) + "\n" + std::to_string(vdisplay) + "\n");
    int status = 0;
    // shortcut: SIGKILL to this waiter (the hub's stuck-host path) leaves a spare-born JVM to `apklayer stop` (its record); upgrade when the hub tracks JVM pids
    if (conn >= 0) status = awaitAppSpare(conn, jvmPid);
    else {
        passSignalsTo(jvmPid);
        while (waitpid(jvmPid, &status, 0) < 0 && errno == EINTR) {}
        status = WIFEXITED(status) ? WEXITSTATUS(status) : 128 + WTERMSIG(status);
    }
    unlink(record.c_str());
    if (vdisplay > 0) kill(vdisplay, SIGTERM);
    if (!appShell.empty()) unlink(appShell.c_str());  // the shell itself ends with this process (its parent)
    return status;
}

// Another launcher holds hub.lock: true once its hub is published (apklayer.hub.ready=1), false when the lock came free first (that hub was
// going down and ended) and is now this launcher's. Both are events: the property's own wait, and a child blocked on the lock.
static bool awaitHubOrLock(const Layout& l, const NativeEnv& ne, const std::string& lockPath, int lock) {
    for (;;) {
        Spawn wait;
        wait.argv = {join(l.native, "bin/aprop"), "wait", kHubReadyProperty, "1"};
        wait.env = ne.env;
        wait.dieWithParent = true;
        int waiter = spawn(wait);
        pid_t locker = fork();
        if (locker == 0) { int fd = open(lockPath.c_str(), O_RDWR | O_CLOEXEC); flock(fd, LOCK_EX); _exit(0); }  // ends when the holder is gone
        int wfd = pidfdOpen(waiter), lfd = pidfdOpen(locker);
        pollfd fds[2] = {{wfd, POLLIN, 0}, {lfd, POLLIN, 0}};
        while (poll(fds, 2, -1) < 0 && errno == EINTR) {}
        bool ready = fds[0].revents & POLLIN;
        kill(waiter, SIGTERM);
        kill(locker, SIGKILL);
        waitpid(waiter, nullptr, 0);
        waitpid(locker, nullptr, 0);
        close(wfd);
        close(lfd);
        if (ready) return true;
        if (flock(lock, LOCK_EX | LOCK_NB) == 0) return false;  // another launcher may have been faster: wait for its hub then
    }
}

// Foreground hub daemon: one per instance, the lock and its JVM live for the instance lifetime. A launcher that finds the lock held returns
// once that hub is ready, or takes over when it was a hub going down.
int runHub(const Layout& l, const Instance& inst) {
    const std::string lockPath = join(inst.stateDir, "hub.lock");
    int lock = open(lockPath.c_str(), O_CREAT | O_RDWR | O_CLOEXEC, 0644);
    NativeEnv ne = nativeEnv(l, inst);
    // the always-on unit's hub is the instance's (cli/autostart.cpp): were it to return beside another launcher's hub, systemd would count the
    // unit done and its ExecStopPost stop would end the instance. That launcher (one an app of the previous stack started during a restart) ends instead.
    bool unit = currentUnit() == "apklayer-instance@" + inst.id + ".service";
    while (flock(lock, LOCK_EX | LOCK_NB) != 0) {
        if (!unit) { if (awaitHubOrLock(l, ne, lockPath, lock)) return 0; break; }
        printf("[apklayer] another hub launcher holds the instance's hub: the always-on unit's replaces it\n");
        fflush(stdout);
        if (auto other = readFile(join(inst.stateDir, "hub.pid")))
            for (const std::string& pid : split(*other, '\n')) terminate(atoi(pid.c_str()));
        // hub.pid did not name it yet (an older CLI writes it only with its JVM): its hub's ready, or the lock
        if (flock(lock, LOCK_EX | LOCK_NB) == 0 || !awaitHubOrLock(l, ne, lockPath, lock)) break;
    }
    writeFile(join(inst.stateDir, "hub.pid"), std::to_string(getpid()) + "\n");  // the lock's holder: the unit's hub ends it (above), `stop` too
    trace("hub start");
    if (!ensureStack(l, inst)) return 1;
    std::vector<std::string> jvm = hostJvmArgs(l, inst, ne);
    // uid 1000: the hub is the instance's system_server, also as the uid its oneway calls carry (HubProviders.check)
    jvm.insert(jvm.end(), {"-Dapklayer.uid=1000", "-Dapklayer.apps=" + l.appsDir, "-Dapklayer.cli=" + l.cliBinary()});
    // SettingsProvider is system_server's own (android:process="system"): it runs in the hub's JVM, as on a device, not in a JVM of its own (docs/history/PLAN-2026-11-10.md
    // "app start speed on phones" 8). Its data and package as for its own process, the hub's uid. Under the sandbox it keeps its own host (its app uid's files).
    std::vector<std::string> system;
    if (!sandboxOn() && exists(join(appDir(l, kSystemProcessPackage), "classes.jar"))) {
        system = packageJvmArgs(l, inst, kSystemProcessPackage);
        system.erase(std::remove_if(system.begin(), system.end(), [](const std::string& a) { return a.rfind("-Dapklayer.uid=", 0) == 0; }), system.end());
        jvm.insert(jvm.end(), system.begin(), system.end() - 2);
    }
    jvm.insert(jvm.end(), {"-cp", hostClasspath(l), "apklayer.host.Main", "--hub"});
    if (!system.empty()) jvm.insert(jvm.end(), system.end() - 2, system.end());  // its APK and classes jar
    setProperty(l, ne, kHubReadyProperty, "0");
    // Like init after a service registers: once this JVM has published the hub to the service manager it says so on this pipe (HubHost), and
    // apklayer.hub.ready=1 (clients block on it). Its own word, not a service-manager lookup, which can still find the previous hub's binder.
    int ready[2];
    if (pipe2(ready, O_CLOEXEC) != 0) die("pipe failed");
    fcntl(ready[1], F_SETFD, 0);
    Spawn hub;
    hub.argv = jvm;
    hub.env = ne.env;
    hub.env["APKLAYER_HUB_READY_FD"] = std::to_string(ready[1]);
    if (sandboxOn()) sandboxSpawn(l, inst, hub, "-");  // in the instance's user namespace: the kernel names its callers by their app uids
    int pid = spawn(hub);
    close(ready[1]);
    writeFile(join(inst.stateDir, "hub.pid"), std::to_string(pid) + "\n" + std::to_string(getpid()) + "\n");  // the JVM, then this launcher (it holds hub.lock until the JVM is gone)
    int jvmFd = pidfdOpen(pid);
    pollfd fds[2] = {{jvmFd, POLLIN, 0}, {ready[0], POLLIN, 0}};
    int status = 0;
    for (bool ended = false; !ended;) {
        if (poll(fds, 2, -1) < 0) continue;
        if (fds[1].revents & (POLLIN | POLLHUP)) {
            char byte;
            if (read(ready[0], &byte, 1) == 1) { setProperty(l, ne, kHubReadyProperty, "1"); trace("hub ready"); }
            fds[1].fd = -1;  // said once (or the JVM closed it unsaid)
        }
        if (fds[0].revents & POLLIN) {
            waitpid(pid, &status, 0);  // keeps the lock until the JVM ends
            ended = true;
        }
    }
    close(ready[0]);
    setProperty(l, ne, kHubReadyProperty, "0");  // before the lock is released: a waiting launcher never sees a gone hub as ready
    return WIFEXITED(status) ? WEXITSTATUS(status) : 1;
}

// `apklayer wait hub`: blocks until the hub is published (what a client does after launching `apklayer hub`).
int waitHub(const Layout& l, const Instance& inst) {
    // the live stack's property store: a wait that began before the stack started watched the store its start removes, and never saw the hub
    if (!ensureStack(l, inst)) return 1;
    NativeEnv ne = nativeEnv(l, inst);
    return waitForProperty(l, ne, kHubReadyProperty, "1", -1) ? 0 : 1;
}

// `apklayer screenshot <package|stack> <out.png>`: SurfaceFlinger's own capture of the app's virtual display (its sink registered the display's token
// as apklayer.vdisplay.<layer stack>; the layer stack is the launcher's pid, the name of its run.<pid> record).
int screenshot(const Layout& l, const Instance& inst, const std::string& target, std::string out) {
    std::string stack = target;
    if (target.empty() || target.find_first_not_of("0123456789") != std::string::npos) {
        stack = windowLaunch(l, inst, target);
        if (stack.empty()) { fprintf(stderr, "apklayer: no window of %s in instance %s\n", target.c_str(), inst.id.c_str()); return 1; }
    }
    if (out.empty() || out[0] != '/') { char cwd[4096]; if (getcwd(cwd, sizeof cwd)) out = join(cwd, out); }
    unlink(out.c_str());
    execTool(l, inst, {"libapklayer_screenshot.so", "apklayer.vdisplay." + stack, out});
    struct stat st {};
    return ::stat(out.c_str(), &st) == 0 && st.st_size > 0 ? 0 : 1;  // btload's exit status is not the tool's: the file is the result
}

}  // namespace apklayer
