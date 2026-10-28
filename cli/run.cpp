#include "run.h"

#include <cerrno>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <sys/file.h>
#include <sys/wait.h>
#include <unistd.h>

namespace apklayer {

namespace {
const char* kHubReadyProperty = "apklayer.hub.ready";
const char* kWebviewCommandLine = "/data/local/tmp/webview-command-line";

bool webviewSandboxOff() {
    auto text = readFile(kWebviewCommandLine);
    return text && text->find("--no-sandbox") != std::string::npos;
}
}  // namespace

int runApp(const Layout& l, const Instance& inst, const std::string& package, RunMode mode) {
    std::string dir = appDir(l, package);
    if (!exists(join(dir, "info.properties"))) die("not installed: " + package + " (apklayer install <app.apk>)");
    ensureUid(l, dir);
    // the Chromium WebView provider is the default WebView once installed and scripts/webview-setup.sh ran; APKLAYER_WEBVIEW_PROVIDER=none disables it
    std::string provider = getenvOr("APKLAYER_WEBVIEW_PROVIDER", "\x01");
    if (provider == "\x01") provider = exists(join(l.appsDir, "com.android.webview/classes.jar")) && webviewSandboxOff() ? "com.android.webview" : "";
    if (provider == "none") provider.clear();
    if (!provider.empty() && !webviewSandboxOff())
        die("the WebView provider route needs scripts/webview-setup.sh once (Chromium's seccomp sandbox cannot run inside an app JVM and is switched off; needs sudo)");

    NativeEnv ne = nativeEnv(l, inst);
    if (!ensureStack(l, inst)) return 1;
    ensureShell(l, inst, ne);

    std::vector<std::string> jvm = hostJvmArgs(l, ne);
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
    std::string classes = join(dir, "classes.jar");
    if (!provider.empty()) {
        jvm.push_back("-Dapklayer.webviewprovider=" + join(l.appsDir, provider));
        classes += ":" + join(l.appsDir, provider + "/classes.jar");
    }
    if (!getenvOr("APKLAYER_ISOLATED", "").empty()) jvm.push_back("-Dapklayer.isolated=" + getenvOr("APKLAYER_ISOLATED", ""));
    if (!getenvOr("APKLAYER_LOGCAT", "").empty()) jvm.push_back("-Dapklayer.logcat=true");
    for (const std::string& o : split(getenvOr("APKLAYER_JOPTS", ""), ' ')) if (!o.empty()) jvm.push_back(o);
    jvm.insert(jvm.end(), {"-cp", hostClasspath(l, classes), "apklayer.host.Main"});
    if (mode == RunMode::Component) jvm.push_back("--component-host");
    jvm.push_back(join(dir, "base.apk"));
    jvm.push_back(join(dir, "classes.jar"));

    Spawn app;
    app.argv = jvm;
    app.env = ne.env;
    app.cwd = dir;
    if (mode == RunMode::Component) {
        // a service host has no window sink to clean up: become the JVM, so the launcher dies exactly when the host does (the hub restarts
        // a killed host as soon as its launcher is gone)
        writeFile(join(inst.stateDir, "run." + std::to_string(self)), std::to_string(self) + "\n");
        execReplace(app);
    }
    int jvmPid = spawn(app);
    // `apklayer stop` ends what this launch started: the JVM and its window sink
    std::string record = join(inst.stateDir, "run." + std::to_string(self));
    writeFile(record, std::to_string(jvmPid) + "\n" + std::to_string(vdisplay) + "\n");
    int status = 0;
    while (waitpid(jvmPid, &status, 0) < 0 && errno == EINTR) {}
    unlink(record.c_str());
    if (vdisplay > 0) kill(vdisplay, SIGTERM);
    return WIFEXITED(status) ? WEXITSTATUS(status) : 128 + WTERMSIG(status);
}

// Foreground hub daemon: one per instance, the lock and its JVM live for the instance lifetime.
int runHub(const Layout& l, const Instance& inst) {
    int lock = open(join(inst.stateDir, "hub.lock").c_str(), O_CREAT | O_RDWR | O_CLOEXEC, 0644);
    if (flock(lock, LOCK_EX | LOCK_NB) != 0) return 0;
    NativeEnv ne = nativeEnv(l, inst);
    if (!ensureStack(l, inst)) return 1;
    std::vector<std::string> jvm = hostJvmArgs(l, ne);
    jvm.insert(jvm.end(), {"-Dapklayer.apps=" + l.appsDir, "-Dapklayer.cli=" + l.cliBinary(), "-cp", hostClasspath(l, ""), "apklayer.host.Main", "--hub"});
    Spawn hub;
    hub.argv = jvm;
    hub.env = ne.env;
    int pid = spawn(hub);
    writeFile(join(inst.stateDir, "hub.pid"), std::to_string(pid) + "\n" + std::to_string(getpid()) + "\n");  // the JVM, then this launcher (it holds hub.lock until the JVM is gone)
    // Like init after a service registers: once the hub is published to the service manager, apklayer.hub.ready=1 (clients block on it)
    setProperty(l, ne, kHubReadyProperty, "0");
    Spawn published;
    published.argv = {l.btload(), l.androidLibs, "libapklayer_servicetool.so", "--main", "wait", "apklayer.instance"};
    published.env = ne.env;
    published.logFile = join(inst.logs(), "ready-hub.log");
    int waiter = spawn(published);
    int jvmFd = pidfdOpen(pid), waiterFd = pidfdOpen(waiter);
    pollfd fds[2] = {{jvmFd, POLLIN, 0}, {waiterFd, POLLIN, 0}};
    int status = 0;
    for (bool ended = false; !ended;) {
        if (poll(fds, 2, -1) < 0) continue;
        if (fds[1].revents & POLLIN) {
            waitpid(waiter, nullptr, 0);
            fds[1].fd = -1;
            auto out = readFile(join(inst.logs(), "ready-hub.log"));
            if (out && out->find(": found") != std::string::npos) setProperty(l, ne, kHubReadyProperty, "1");
        }
        if (fds[0].revents & POLLIN) {
            waitpid(pid, &status, 0);  // keeps the lock until the JVM ends
            ended = true;
        }
    }
    kill(waiter, SIGTERM);
    return WIFEXITED(status) ? WEXITSTATUS(status) : 1;
}

// `apklayer wait hub`: blocks until the hub is published (what a client does after launching `apklayer hub`).
int waitHub(const Layout& l, const Instance& inst) {
    NativeEnv ne = nativeEnv(l, inst);
    return waitForProperty(l, ne, kHubReadyProperty, "1", -1) ? 0 : 1;
}

}  // namespace apklayer
