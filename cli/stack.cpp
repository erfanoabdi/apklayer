#include "stack.h"

#include <fcntl.h>
#include <cerrno>
#include <poll.h>
#include <signal.h>
#include <sys/file.h>
#include <sys/signalfd.h>
#include <sys/wait.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>

namespace apklayer {

namespace {
struct Service {
    std::string name;
    int pid;
    int pidfd;
    Spawn spec;        // how it was started: an optional service that exits is started again the same way
    int restarts = 0;
};

// What the stack cannot run without: these ending ends the stack (and the test tool's end is the end of a test run). SurfaceFlinger holds its
// composer and allocator connections for its lifetime, so they are as essential. Every other service is restarted, as init restarts a HAL.
bool essential(const std::string& name) {
    for (const char* n : {"servicemanager", "surfaceflinger", "composer", "allocator", "tool", "test-display"})
        if (name == n) return true;
    return false;
}
const int kMaxRestarts = 5;  // like init's crash loop limit: a service that keeps dying stays down instead of restarting forever

bool staged(const Layout& l, const char* lib) { return exists(join(l.androidLibs, lib)); }

// A vintf manifest is declared only when this stack provides the service behind it: staged into the instance's own root (the path layer's
// APKLAYER_INSTANCE_ROOT, made fresh with each stack), never into the native tree other instances share. That root's manifest directory
// replaces the shared one, so it carries the shared fragments too.
void stageManifest(const Instance& inst, const Layout& l, bool provided, const std::string& name, const std::string& source, const char* partition = "vendor") {
    const std::string dir = std::string(partition) + "/etc/vintf/manifest";
    const std::string own = join(join(inst.runtimeDir, "android-root"), dir), shared = join(l.native, "android-root/" + dir);
    if (!exists(own)) {
        makeDirs(own);
        for (const std::string& f : listDir(shared))
            if (f.rfind("apklayer-", 0) != 0) if (auto text = readFile(join(shared, f))) writeFile(join(own, f), *text);  // the tree's own fragments, not other instances' staging
    }
    if (provided)
        if (auto text = readFile(join(l.root, source))) writeFile(join(own, name), *text);
}

// The pid of the shell listening on this session's socket (0: none): what stops it, never a match by name (other sessions' shells run too).
int shellPid(const Layout& l) {
    int fd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    sockaddr_un addr{};
    addr.sun_family = AF_UNIX;
    snprintf(addr.sun_path, sizeof addr.sun_path, "%s", l.shellSocket().c_str());
    ucred peer{};
    socklen_t len = sizeof peer;
    bool ok = connect(fd, reinterpret_cast<sockaddr*>(&addr), sizeof addr) == 0 && getsockopt(fd, SOL_SOCKET, SO_PEERCRED, &peer, &len) == 0;
    close(fd);
    return ok ? peer.pid : 0;
}

// Android's way: readiness is a system property of the instance (apklayer.stack.ready=1), waited on with the property store's own wait.
const char* kReadyProperty = "apklayer.stack.ready";

bool propertyIs(const Layout& l, const NativeEnv& ne, const char* name, const char* value) {
    Spawn get;
    get.argv = {join(l.native, "bin/aprop"), "get", name};
    get.env = ne.env;
    return trim(runCapture(get)) == value;
}

bool waitReady(const Layout& l, const NativeEnv& ne, int deathPidfd) { return waitForProperty(l, ne, kReadyProperty, "1", deathPidfd); }

bool alive(int pid) { return pid > 0 && kill(pid, 0) == 0; }

// SIGTERM, then wait for the exit on a pidfd (SIGKILL after 5 s): `stop` returns with the processes gone.
void terminate(int pid) {
    if (!alive(pid)) return;
    if (auto cmd = readFile("/proc/" + std::to_string(pid) + "/cmdline"); !cmd || cmd->find("apklayer") == std::string::npos) return;  // a recycled pid is not ours
    int fd = pidfdOpen(pid);
    kill(pid, SIGTERM);
    pollfd p{fd, POLLIN, 0};
    if (fd >= 0 && poll(&p, 1, 5000) == 0) {
        kill(pid, SIGKILL);
        poll(&p, 1, 2000);
    }
    if (fd >= 0) close(fd);
}
}  // namespace

void setProperty(const Layout& l, const NativeEnv& ne, const char* name, const char* value) {
    Spawn set;
    set.argv = {join(l.native, "bin/aprop"), "set", name, value};
    set.env = ne.env;
    runAndWait(set);
}

bool waitForProperty(const Layout& l, const NativeEnv& ne, const char* name, const char* value, int deathPidfd) {
    Spawn wait;
    wait.argv = {join(l.native, "bin/aprop"), "wait", name, value};
    wait.env = ne.env;
    int pid = spawn(wait);
    int fd = pidfdOpen(pid);
    pollfd fds[2] = {{fd, POLLIN, 0}, {deathPidfd, POLLIN, 0}};
    while (poll(fds, deathPidfd >= 0 ? 2 : 1, -1) < 0 && errno == EINTR) {}
    bool ok = fds[0].revents & POLLIN;
    if (!ok) kill(pid, SIGTERM);
    waitpid(pid, nullptr, 0);
    close(fd);
    return ok;
}

// Returns with the session's shell listening. One start at a time (the stack daemon and `run` both ask; two shells would each own half the
// clients), and the started shell reports readiness itself: it writes to APKLAYER_SHELL_READY_FD after listen() (EOF: it died first).
void ensureShell(const Layout& l, const Instance& inst, const NativeEnv& env) {
    makeDirs(join(l.runtimeHome, "apklayer"));
    int lock = open(join(l.runtimeHome, "apklayer/shell.lock").c_str(), O_CREAT | O_RDWR | O_CLOEXEC, 0600);
    flock(lock, LOCK_EX);
    if (shellPid(l) == 0) {
        unlink(l.shellSocket().c_str());
        int ready[2];
        if (pipe2(ready, O_CLOEXEC) != 0) die("pipe failed");
        fcntl(ready[1], F_SETFD, 0);  // the shell inherits the write end
        Spawn shell;
        shell.argv = {l.shellBinary()};
        shell.env = env.env;
        shell.env["APKLAYER_SHELL_READY_FD"] = std::to_string(ready[1]);
        shell.logFile = join(inst.logs(), "shell.log");
        shell.newSession = true;  // outlives any instance; instances never stop it
        shell.detach = true;
        spawn(shell);
        close(ready[1]);
        char c;
        while (read(ready[0], &c, 1) < 0 && errno == EINTR) {}
        close(ready[0]);
    }
    close(lock);
}

// keystore2 keeps its database in /data/misc/keystore, which the path layer serves from the instance's /data
static std::string keystoreDir(const Instance& inst) {
    std::string dir = join(inst.dataDir, "misc/keystore");
    makeDirs(dir);
    return dir;
}

// What Android's init derives at boot (property_service.cpp): the fingerprint of the product the instance presents (bridge/native/apklayer.prop over
// the tree's build.prop, whose own fingerprint names the GSI's "generic" product) and the ABI lists of this machine's CPU. Set before the files
// are loaded: ro.* is write-once, so these win and every reader (natives, apklayer.host.HostBuild) sees one device.
static void setDerivedProperties(const Layout& l, const NativeEnv& ne, const std::vector<std::string>& files) {
    auto get = [&](const char* key) {
        for (const std::string& f : files)
            if (std::string v = property(f, key); !v.empty()) return v;
        return std::string();
    };
    std::string fingerprint = get("ro.product.brand") + "/" + get("ro.product.name") + "/" + get("ro.product.device") + ":" + get("ro.build.version.release_or_codename") +
                              "/" + get("ro.build.id") + "/" + get("ro.build.version.incremental") + ":" + get("ro.build.type") + "/" + get("ro.build.tags");
    std::string abis = std::string(kAbi) + (*kAbi32 ? std::string(",") + kAbi32 : "");
    setProperty(l, ne, "ro.build.fingerprint", fingerprint.c_str());
    setProperty(l, ne, "ro.product.cpu.abi", kAbi);
    setProperty(l, ne, "ro.product.cpu.abilist", abis.c_str());
    setProperty(l, ne, "ro.product.cpu.abilist64", kAbi);
    setProperty(l, ne, "ro.product.cpu.abilist32", kAbi32);
}

int runStackDaemon(const Layout& l, const Instance& inst, const std::vector<std::string>& tool) {
    NativeEnv ne = nativeEnv(l, inst);
    const std::string& n = l.native;
    for (const std::string& f : listDir(inst.logs()))
        if (f.size() > 4 && f.substr(f.size() - 4) == ".log" && f != "stack.log") unlink(join(inst.logs(), f).c_str());  // stack.log: this daemon's own stderr, already open


    // a fresh property store per stack (ro.* is write-once); ensureStack already made it when a client waits on the ready property
    if (getenvOr("APKLAYER_STACK_PREPARED", "").empty()) {
        removeTree(inst.runtimeDir);
        makeDirs(inst.runtimeDir);
    }
    stageManifest(inst, l, staged(l, "libapklayer_codec2.so"), "apklayer-media.xml", "aosp/hals/media/codec2/manifest.xml");
    stageManifest(inst, l, staged(l, "libapklayer_audio.so"), "apklayer-audio.xml", "aosp/hals/audio/manifest.xml");
    stageManifest(inst, l, staged(l, "libapklayer_audioserver.so"), "apklayer-audio-effects.xml", "aosp/hals/audio/effects-manifest.xml");
    stageManifest(inst, l, staged(l, "libapklayer_keymint.so"), "apklayer-keymint.xml", "aosp/hals/security/manifest.xml");
    stageManifest(inst, l, staged(l, "libapklayer_keystore2.so"), "apklayer-keystore2.xml", "aosp/hals/security/keystore2-manifest.xml", "system");  // a framework HAL: keystore2 may register its service
    std::vector<std::string> propFiles = {join(l.root, "bridge/native/apklayer.prop"), join(n, "android-root/props/system_build.prop")};
    setDerivedProperties(l, ne, propFiles);
    Spawn prop;
    prop.argv = {join(n, "bin/aprop"), "load", propFiles[0], propFiles[1]};
    prop.env = ne.env;
    prop.logFile = join(inst.logs(), "aprop.log");
    runAndWait(prop);
    // Android init creates this directory (in the instance's /data) and announces it to RenderEngine.
    std::string cache = join(inst.dataDir, "misc/surfaceflinger");
    makeDirs(cache);
    for (const char* f : {"egl_shaders", "skia_shaders", "skia_graphite_pipelines"}) writeFile(join(cache, f), "");
    prop.argv = {join(n, "bin/aprop"), "set", "service.sf.cache_dir_available", "1"};
    runAndWait(prop);

    if (getenvOr("APKLAYER_START_SHELL", "1") == "1") ensureShell(l, inst, ne);

    std::vector<Service> running;
    auto start = [&](const std::string& name, const std::string& lib, std::vector<std::string> args) {
        Spawn s;
        s.argv = {l.btload(), l.androidLibs, lib, "--main"};
        s.argv.insert(s.argv.end(), args.begin(), args.end());
        s.env = ne.env;
        s.logFile = join(inst.logs(), name + ".log");
        int pid = spawn(s);
        running.push_back({name, pid, pidfdOpen(pid), s});
    };
    const std::string hals = join(l.root, "aosp/hals/audio");
    start("servicemanager", "libapklayer_servicemanager.so", {"/dev/binder"});
    start("permission", "libapklayer_permission.so", {});  // libbinder's checkPermission() asks this (no system_server PermissionController)
    start("allocator", "libapklayer_allocator.so", {});
    start("composer", "libapklayer_composer.so", {});
    if (staged(l, "libapklayer_codec2.so")) start("codec2", "libapklayer_codec2.so", {});
    if (staged(l, "libapklayer_mediaserver.so")) {
        start("media", "libapklayer_mediaserver.so", {});
        start("extractor", "libapklayer_mediaserver.so", {"--extractor"});
    }
    if (staged(l, "libapklayer_audio.so")) start("audio", "libapklayer_audio.so", {join(hals, "audio_policy_configuration.xml")});
    if (staged(l, "libapklayer_audioserver.so")) {
        start("audio-effects", "libapklayer_audio.so", {"--effects", join(hals, "audio_effects_config.xml")});
        start("audio-services", "libapklayer_audioserver.so", {});
    }
    if (staged(l, "libapklayer_keymint.so") && staged(l, "libapklayer_keystore2.so")) {  // the real Android keystore: software KeyMint, then keystore2 over it
        start("keymint", "libapklayer_keymint.so", {});
        keystoreDir(inst);
        start("keystore2", "libapklayer_keystore2.so", {"/data/misc/keystore"});  // as keystore2.rc: the database directory, through the path layer
    }
    start("surfaceflinger", "libapklayer_surfaceflinger.so", {});
    if (!tool.empty()) {
        // smoke tools draw on stack 0: give them the same virtual-display sink an app uses
        if (staged(l, "libapklayer_vdisplay.so"))
            start("test-display", "libapklayer_vdisplay.so", {"apklayer:test", "0", getenvOr("APKLAYER_DISPLAY_WIDTH", "720"), getenvOr("APKLAYER_DISPLAY_HEIGHT", "1280")});
        start("tool", tool[0], std::vector<std::string>(tool.begin() + 1, tool.end()));
    }
    writeFile(inst.pidFile(), std::to_string(getpid()) + "\n");

    // Ready when SurfaceFlinger has registered, and the audio policy when audio services run (an AudioTrack created before it exists gets
    // "server died"). One waiter per service blocks in waitForService; the daemon sets the ready property after all of them returned.
    std::vector<std::string> waitFor = {"SurfaceFlingerAIDL"};
    if (staged(l, "libapklayer_audioserver.so")) waitFor.push_back("media.audio_policy");
    std::vector<int> waiters;
    for (const std::string& svc : waitFor) {
        Spawn w;
        w.argv = {l.btload(), l.androidLibs, "libapklayer_servicetool.so", "--main", "wait", svc};
        w.env = ne.env;
        w.logFile = join(inst.logs(), "ready-" + svc + ".log");
        waiters.push_back(spawn(w));
    }
    std::vector<pollfd> fds;
    for (const Service& s : running) fds.push_back({s.pidfd, POLLIN, 0});
    size_t nServices = fds.size();
    std::vector<int> waiterFds;
    for (int pid : waiters) { waiterFds.push_back(pidfdOpen(pid)); fds.push_back({waiterFds.back(), POLLIN, 0}); }
    sigset_t sigs;
    sigemptyset(&sigs);
    sigaddset(&sigs, SIGTERM);
    sigaddset(&sigs, SIGINT);
    sigprocmask(SIG_BLOCK, &sigs, nullptr);
    int sfd = signalfd(-1, &sigs, SFD_CLOEXEC);
    fds.push_back({sfd, POLLIN, 0});

    size_t pending = waiters.size();
    int status = 0;
    for (bool run = true; run;) {
        if (poll(fds.data(), fds.size(), -1) < 0) continue;
        for (size_t i = 0; i < fds.size() && run; i++) {
            if (!(fds[i].revents & POLLIN)) continue;
            if (i < nServices) {
                Service& s = running[i];
                int st = 0;
                waitpid(s.pid, &st, 0);
                close(s.pidfd);
                fprintf(stderr, "exited: %s (status %d)\n", s.name.c_str(), WIFEXITED(st) ? WEXITSTATUS(st) : 128 + WTERMSIG(st));
                if (!essential(s.name) && s.restarts < kMaxRestarts) {
                    s.restarts++;
                    s.pid = spawn(s.spec);
                    s.pidfd = pidfdOpen(s.pid);
                    fds[i].fd = s.pidfd;
                    fprintf(stderr, "restarted: %s (%d of %d)\n", s.name.c_str(), s.restarts, kMaxRestarts);
                    continue;
                }
                if (!essential(s.name)) { fds[i].fd = -1; s.pid = 0; fprintf(stderr, "%s keeps exiting: left stopped\n", s.name.c_str()); continue; }
                s.pid = 0;  // reaped: never signalled again (its pid may be reused)
                status = s.name == "tool" ? 0 : 1;  // the tool finishing is the normal end of a test run; its verdict is in its log
                run = false;
            } else if (i < nServices + waiters.size()) {
                int st = 0;
                waitpid(waiters[i - nServices], &st, 0);
                fds[i].fd = -1;  // done: poll ignores negative descriptors
                // btload's exit status is not the tool's: the printed "NAME: found" is the result (as the old script grepped it)
                auto out = readFile(join(inst.logs(), "ready-" + waitFor[i - nServices] + ".log"));
                if (out && out->find(": found") != std::string::npos && --pending == 0) {
                    setProperty(l, ne, kReadyProperty, "1");
                }
            } else {
                run = false;  // asked to stop
            }
        }
    }
    for (const Service& s : running) if (s.pid > 0) kill(s.pid, SIGTERM);
    for (int pid : waiters) kill(pid, SIGTERM);
    for (const Service& s : running) {
        int st = 0;
        if (s.pid > 0 && waitpid(s.pid, &st, 0) == s.pid && !(WIFSIGNALED(st) && WTERMSIG(st) == SIGTERM) && !(WIFEXITED(st) && WEXITSTATUS(st) == 0))
            fprintf(stderr, "stopped: %s (status %d)\n", s.name.c_str(), WIFEXITED(st) ? WEXITSTATUS(st) : 128 + WTERMSIG(st));  // a clean stop is SIGTERM's default action
    }
    unlink(inst.pidFile().c_str());
    for (const std::string& name : listDir(inst.tmpDir())) removeTree(join(inst.tmpDir(), name));  // the JVMs of this stack are gone: their extracted native runtimes too (the directory stays: a JVM about to start has it as java.io.tmpdir)
    terminate(shellPid(l));  // the window shell ends with the stack (one instance per session today)
    return status;
}

bool ensureStack(const Layout& l, const Instance& inst) {
    int lock = open(join(inst.stateDir, "start.lock").c_str(), O_CREAT | O_RDWR | O_CLOEXEC, 0644);
    struct Unlock { int fd; ~Unlock() { close(fd); } } unlock{lock};  // closing releases the flock: the hub JVM asks for the stack itself
    flock(lock, LOCK_EX);  // concurrent launches share one start
    auto pid = readFile(inst.pidFile());
    NativeEnv ne = nativeEnv(l, inst);
    if (pid && alive(atoi(pid->c_str())) && propertyIs(l, ne, kReadyProperty, "1")) return true;
    if (!(pid && alive(atoi(pid->c_str())))) {
        removeTree(inst.runtimeDir);  // the new stack's property store: made before the waiter opens it, so both see the same file
        makeDirs(inst.runtimeDir);
        ne.env["APKLAYER_STACK_PREPARED"] = "1";
        Spawn d;
        d.argv = {l.cliBinary(), "stack-daemon", inst.id};
        d.env = ne.env;
        d.logFile = join(inst.logs(), "stack.log");
        d.newSession = true;
        int child = spawn(d);
        int death = pidfdOpen(child);
        bool ok = waitReady(l, ne, death);
        if (!ok) fprintf(stderr, "apklayer: the Android services did not come up; logs in %s\n", inst.logs().c_str());
        return ok;
    }
    return waitReady(l, ne, -1);
}

int execTool(const Layout& l, const Instance& inst, const std::vector<std::string>& args) {
    if (args.empty()) die("exec <library> [args...]");
    NativeEnv ne = nativeEnv(l, inst);
    Spawn s;
    s.argv = {l.btload(), l.androidLibs, args[0], "--main"};
    s.argv.insert(s.argv.end(), args.begin() + 1, args.end());
    s.env = ne.env;
    return runAndWait(s);
}

void stopStack(const Layout& l, const Instance& inst) {
    terminate(shellPid(l));  // one instance per session today: the window shell goes with it
    for (const std::string& f : listDir(inst.stateDir)) {  // launches of this instance: their JVM and window sink
        if (f.rfind("run.", 0) != 0) continue;
        auto record = readFile(join(inst.stateDir, f));
        if (record) for (const std::string& pid : split(*record, '\n')) terminate(atoi(pid.c_str()));
        unlink(join(inst.stateDir, f).c_str());
    }
    // the hub lives on the stack's binder: it ends with it (a stale one would hold the lock for the next stack)
    if (auto hub = readFile(join(inst.stateDir, "hub.pid")))  // the hub JVM, then its launcher: the next hub can take hub.lock as soon as stop returns
        for (const std::string& pid : split(*hub, '\n')) terminate(atoi(pid.c_str()));
    auto pid = readFile(inst.pidFile());
    if (pid) terminate(atoi(pid->c_str()));
}

}  // namespace apklayer
