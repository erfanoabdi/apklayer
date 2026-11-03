#include "run.h"

#include <cerrno>
#include <cstring>
#include <dirent.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <sys/file.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <unistd.h>

namespace apklayer {

namespace {
const char* kHubReadyProperty = "apklayer.hub.ready";
const char* kWebviewCommandLine = "/data/local/tmp/webview-command-line";

// The host's switch file (tools/host-setup/webview.sh), copied into the instance's own /data, where Chromium reads it through the path layer
// (APKLAYER_DATA). A launch from inside the instance (the hub's component host, with the path layer preloaded) reads that copy under the same name.
bool webviewSandboxOff(const Instance& inst) {
    auto text = readFile(kWebviewCommandLine);
    if (!text || text->find("--no-sandbox") == std::string::npos) return false;
    std::string own = join(inst.dataDir, std::string(kWebviewCommandLine).substr(strlen("/data/")));
    if (readFile(own) != text) { makeDirs(dirName(own)); writeFile(own, *text); }
    return true;
}
}  // namespace

int runApp(const Layout& l, const Instance& inst, const std::string& package, RunMode mode) {
    std::string dir = appDir(l, package);
    if (!exists(join(dir, "info.properties"))) die("not installed: " + package + " (apklayer install <app.apk>)");
    ensureUid(l, dir);
    // the Chromium WebView provider is the default WebView once installed and tools/host-setup/webview.sh ran; APKLAYER_WEBVIEW_PROVIDER=none disables it
    std::string provider = getenvOr("APKLAYER_WEBVIEW_PROVIDER", "\x01");
    if (provider == "\x01") provider = exists(join(l.appsDir, "com.android.webview/classes.jar")) && webviewSandboxOff(inst) ? "com.android.webview" : "";
    if (provider == "none") provider.clear();
    if (!provider.empty() && !webviewSandboxOff(inst))
        die("the WebView provider route needs tools/host-setup/webview.sh once (Chromium's seccomp sandbox cannot run inside an app JVM and is switched off; needs sudo)");

    trace("run " + package);
    NativeEnv ne = nativeEnv(l, inst);
    if (!ensureStack(l, inst)) return 1;
    trace("stack ready");
    ensureShell(l, inst, ne);
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

    std::vector<std::string> jvm = hostJvmArgs(l, inst, ne);
    int vdisplay = 0;
    int self = getpid();
    // one virtual display per app window (its own layer stack and sink per launch); pid-based ids cannot collide, and the title identifies instance and
    // launch. The primary display is headless, so the sink is required (a component host has no window).
    if (mode == RunMode::App) {
        if (!exists(join(l.androidLibs, "libapklayer_vdisplay.so"))) die("the runtime needs libapklayer_vdisplay.so (the primary display is headless)");
        std::string title = ("apklayer:" + std::to_string(self) + ":" + inst.id + ":" + package).substr(0, 60);  // the host display HAL's title limit
        Spawn sink;
        sink.argv = {l.btload(), l.androidLibs, "libapklayer_vdisplay.so", "--main", title, std::to_string(self), getenvOr("APKLAYER_DISPLAY_WIDTH", "720"), getenvOr("APKLAYER_DISPLAY_HEIGHT", "1280")};
        sink.env = ne.env;
        sink.logFile = join(inst.logs(), "vdisplay-" + package + "-" + std::to_string(self) + ".log");
        vdisplay = spawn(sink);
        jvm.push_back("-Dapklayer.layerstack=" + std::to_string(self));
        jvm.push_back("-Dapklayer.displaywindow=" + title);
    }
    ne.env["LD_LIBRARY_PATH"] = l.native + ":" + join(l.native, "glibc") + ":" + ne.env["LD_LIBRARY_PATH"];  // bionic linker + its libs
    for (const std::string& o : {"-Dapklayer.data=" + join(dir, "data"), "-Dapklayer.package=" + package, "-Dapklayer.uid=" + property(join(dir, "info.properties"), "uid"),
                                 "-Dapklayer.libdir=" + join(dir, "lib"), "-Dapklayer.apps=" + l.appsDir, "-Dapklayer.cli=" + l.cliBinary()})
        jvm.push_back(o);
    if (!provider.empty()) jvm.push_back("-Dapklayer.webviewprovider=" + join(l.appsDir, provider));  // its classes get a loader of their own (ShadowHostWebViewFactory)
    if (!getenvOr("APKLAYER_ISOLATED", "").empty()) jvm.push_back("-Dapklayer.isolated=" + getenvOr("APKLAYER_ISOLATED", ""));
    if (!getenvOr("APKLAYER_LOGCAT", "").empty()) jvm.push_back("-Dapklayer.logcat=true");
    for (const std::string& o : split(getenvOr("APKLAYER_JOPTS", ""), ' ')) if (!o.empty()) jvm.push_back(o);
    jvm.insert(jvm.end(), {"-cp", hostClasspath(l), "apklayer.host.Main"});
    if (mode == RunMode::Component) jvm.push_back("--component-host");
    jvm.push_back(join(dir, "base.apk"));
    jvm.push_back(join(dir, "classes.jar"));

    Spawn app;
    app.argv = jvm;
    app.env = ne.env;
    if (mode == RunMode::Component) app.env["APKLAYER_COMPONENT_HOST"] = "1";
    app.cwd = dir;
    if (mode == RunMode::Component) {
        // a service host has no window sink to clean up: become the JVM, so the launcher dies exactly when the host does (the hub restarts
        // a killed host as soon as its launcher is gone)
        writeFile(join(inst.stateDir, "run." + std::to_string(self)), std::to_string(self) + "\n");
        execReplace(app);
    }
    int jvmPid = spawn(app);
    trace("app jvm spawned");
    // `apklayer stop` ends what this launch started: the JVM and its window sink
    std::string record = join(inst.stateDir, "run." + std::to_string(self));
    writeFile(record, std::to_string(jvmPid) + "\n" + std::to_string(vdisplay) + "\n");
    int status = 0;
    while (waitpid(jvmPid, &status, 0) < 0 && errno == EINTR) {}
    unlink(record.c_str());
    if (vdisplay > 0) kill(vdisplay, SIGTERM);
    return WIFEXITED(status) ? WEXITSTATUS(status) : 128 + WTERMSIG(status);
}

// Another launcher holds hub.lock: true once its hub is published (apklayer.hub.ready=1), false when the lock came free first (that hub was
// going down and ended) and is now this launcher's. Both are events: the property's own wait, and a child blocked on the lock.
static bool awaitHubOrLock(const Layout& l, const NativeEnv& ne, const std::string& lockPath, int lock) {
    for (;;) {
        Spawn wait;
        wait.argv = {join(l.native, "bin/aprop"), "wait", kHubReadyProperty, "1"};
        wait.env = ne.env;
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
    if (flock(lock, LOCK_EX | LOCK_NB) != 0 && awaitHubOrLock(l, ne, lockPath, lock)) return 0;
    trace("hub start");
    if (!ensureStack(l, inst)) return 1;
    std::vector<std::string> jvm = hostJvmArgs(l, inst, ne);
    // uid 1000: the hub is the instance's system_server, also as the uid its oneway calls carry (HubProviders.check)
    jvm.insert(jvm.end(), {"-Dapklayer.uid=1000", "-Dapklayer.apps=" + l.appsDir, "-Dapklayer.cli=" + l.cliBinary(), "-cp", hostClasspath(l), "apklayer.host.Main", "--hub"});
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
    NativeEnv ne = nativeEnv(l, inst);
    return waitForProperty(l, ne, kHubReadyProperty, "1", -1) ? 0 : 1;
}

// `apklayer screenshot <package|stack> <out.png>`: SurfaceFlinger's own capture of the app's virtual display (its sink registered the display's token
// as apklayer.vdisplay.<layer stack>; the layer stack is the launcher's pid, the name of its run.<pid> record).
int screenshot(const Layout& l, const Instance& inst, const std::string& target, std::string out) {
    std::string stack = target;
    if (target.empty() || target.find_first_not_of("0123456789") != std::string::npos) {
        stack.clear();
        for (const std::string& f : listDir(inst.stateDir)) {
            if (f.rfind("run.", 0) != 0) continue;
            auto record = readFile(join(inst.stateDir, f));
            std::vector<std::string> pids = record ? split(*record, '\n') : std::vector<std::string>();
            if (pids.size() < 2 || atoi(pids[1].c_str()) <= 0) continue;  // a component host: no window
            auto cmd = readFile("/proc/" + pids[0] + "/cmdline");
            // the JVM's command line names the package, or (once the runtime named the process as Android does) is the process name
            if (cmd && (cmd->find("-Dapklayer.package=" + target + std::string(1, '\0')) != std::string::npos || cmd->rfind(target + std::string(1, '\0'), 0) == 0)) stack = f.substr(4);
        }
        if (stack.empty()) { fprintf(stderr, "apklayer: no window of %s in instance %s\n", target.c_str(), inst.id.c_str()); return 1; }
    }
    if (out.empty() || out[0] != '/') { char cwd[4096]; if (getcwd(cwd, sizeof cwd)) out = join(cwd, out); }
    unlink(out.c_str());
    execTool(l, inst, {"libapklayer_screenshot.so", "apklayer.vdisplay." + stack, out});
    struct stat st {};
    return ::stat(out.c_str(), &st) == 0 && st.st_size > 0 ? 0 : 1;  // btload's exit status is not the tool's: the file is the result
}

}  // namespace apklayer
