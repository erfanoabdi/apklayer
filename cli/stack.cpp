#include "stack.h"

#include <fcntl.h>
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
};

bool staged(const Layout& l, const char* lib) { return exists(join(l.androidLibs, lib)); }

// A vintf manifest is declared only when this stack provides the service behind it.
void stageManifest(const Layout& l, bool provided, const std::string& name, const std::string& source) {
    std::string target = join(l.native, "android-root/vendor/etc/vintf/manifest/" + name);
    if (provided) {
        makeDirs(dirName(target));
        if (auto text = readFile(join(l.root, source))) writeFile(target, *text);
    } else {
        unlink(target.c_str());
    }
}

bool shellListening(const Layout& l) {
    int fd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    sockaddr_un addr{};
    addr.sun_family = AF_UNIX;
    snprintf(addr.sun_path, sizeof addr.sun_path, "%s", l.shellSocket().c_str());
    bool ok = connect(fd, reinterpret_cast<sockaddr*>(&addr), sizeof addr) == 0;
    close(fd);
    return ok;
}

bool alive(int pid) { return pid > 0 && kill(pid, 0) == 0; }
}  // namespace

void ensureShell(const Layout& l, const NativeEnv& env) {
    if (shellListening(l)) return;
    makeDirs(join(l.runtimeHome, "apklayer"));
    unlink(l.shellSocket().c_str());
    Spawn shell;
    shell.argv = {l.shellBinary()};
    shell.env = env.env;
    shell.logFile = join(l.stateHome, "apklayer/shell.log");
    makeDirs(dirName(shell.logFile));
    shell.newSession = true;  // outlives any instance; instances never stop it
    shell.detach = true;
    spawn(shell);
}

int runStackDaemon(const Layout& l, const Instance& inst) {
    NativeEnv ne = nativeEnv(l, inst);
    const std::string& n = l.native;
    for (const std::string& f : listDir(inst.logs()))
        if (f.size() > 4 && f.substr(f.size() - 4) == ".log") unlink(join(inst.logs(), f).c_str());
    unlink(inst.readyFile().c_str());

    stageManifest(l, staged(l, "libapklayer_codec2.so"), "apklayer-media.xml", "aosp/hals/media/codec2/manifest.xml");
    stageManifest(l, staged(l, "libapklayer_audio.so"), "apklayer-audio.xml", "aosp/hals/audio/manifest.xml");
    stageManifest(l, staged(l, "libapklayer_audioserver.so"), "apklayer-audio-effects.xml", "aosp/hals/audio/effects-manifest.xml");

    removeTree(inst.runtimeDir);
    makeDirs(inst.runtimeDir);
    Spawn prop;
    prop.argv = {join(n, "bin/aprop"), "load", join(l.root, "bridge/native/apklayer.prop"), join(n, "android-root/props/system_build.prop")};
    prop.env = ne.env;
    prop.logFile = join(inst.logs(), "aprop.log");
    runAndWait(prop);
    // Android init creates this directory and announces it to RenderEngine; the path overlay resolves existing files only.
    std::string cache = join(getenvOr("APKLAYER_ANDROID_ROOT", join(n, "android-root")), "data/misc/surfaceflinger");
    makeDirs(cache);
    for (const char* f : {"egl_shaders", "skia_shaders", "skia_graphite_pipelines"}) writeFile(join(cache, f), "");
    prop.argv = {join(n, "bin/aprop"), "set", "service.sf.cache_dir_available", "1"};
    runAndWait(prop);

    if (getenvOr("APKLAYER_START_SHELL", "1") == "1") ensureShell(l, ne);

    std::vector<Service> running;
    auto start = [&](const std::string& name, const std::string& lib, std::vector<std::string> args) {
        Spawn s;
        s.argv = {l.btload(), l.androidLibs, lib, "--main"};
        s.argv.insert(s.argv.end(), args.begin(), args.end());
        s.env = ne.env;
        s.logFile = join(inst.logs(), name + ".log");
        int pid = spawn(s);
        running.push_back({name, pid, pidfdOpen(pid)});
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
    start("surfaceflinger", "libapklayer_surfaceflinger.so", {});
    writeFile(inst.pidFile(), std::to_string(getpid()) + "\n");

    // Ready when SurfaceFlinger has registered, and the audio policy when audio services run (an AudioTrack created before it exists gets
    // "server died"). One waiter per service blocks in waitForService; the daemon touches `ready` after all of them returned.
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
                fprintf(stderr, "exited: %s\n", running[i].name.c_str());
                status = 1;
                run = false;
            } else if (i < nServices + waiters.size()) {
                int st = 0;
                waitpid(waiters[i - nServices], &st, 0);
                fds[i].fd = -1;  // done: poll ignores negative descriptors
                // btload's exit status is not the tool's: the printed "NAME: found" is the result (as the old script grepped it)
                auto out = readFile(join(inst.logs(), "ready-" + waitFor[i - nServices] + ".log"));
                if (out && out->find(": found") != std::string::npos && --pending == 0) writeFile(inst.readyFile(), "");
            } else {
                run = false;  // asked to stop
            }
        }
    }
    for (const Service& s : running) kill(s.pid, SIGTERM);
    for (int pid : waiters) kill(pid, SIGTERM);
    for (const Service& s : running) waitpid(s.pid, nullptr, 0);
    unlink(inst.readyFile().c_str());
    unlink(inst.pidFile().c_str());
    return status;
}

bool ensureStack(const Layout& l, const Instance& inst) {
    int lock = open(join(inst.stateDir, "start.lock").c_str(), O_CREAT | O_RDWR | O_CLOEXEC, 0644);
    struct Unlock { int fd; ~Unlock() { close(fd); } } unlock{lock};  // closing releases the flock: the hub JVM asks for the stack itself
    flock(lock, LOCK_EX);  // concurrent launches share one start
    auto pid = readFile(inst.pidFile());
    if (pid && alive(atoi(pid->c_str())) && exists(inst.readyFile())) return true;
    if (!(pid && alive(atoi(pid->c_str())))) {
        NativeEnv ne = nativeEnv(l, inst);
        Spawn d;
        d.argv = {l.cliBinary(), "stack-daemon", inst.id};
        d.env = ne.env;
        d.logFile = join(inst.logs(), "stack.log");
        d.newSession = true;
        int child = spawn(d);
        int death = pidfdOpen(child);
        bool ok = waitForFile(inst.readyFile(), death);
        if (!ok) fprintf(stderr, "apklayer: the Android services did not come up; logs in %s\n", inst.logs().c_str());
        return ok;
    }
    return waitForFile(inst.readyFile(), -1);
}

void stopStack(const Layout&, const Instance& inst) {
    for (const std::string& f : listDir(inst.stateDir)) {  // launches of this instance: their JVM and window sink
        if (f.rfind("run.", 0) != 0) continue;
        auto record = readFile(join(inst.stateDir, f));
        if (record) for (const std::string& pid : split(*record, '\n')) if (alive(atoi(pid.c_str()))) kill(atoi(pid.c_str()), SIGTERM);
        unlink(join(inst.stateDir, f).c_str());
    }
    // the hub lives on the stack's binder: it ends with it (a stale one would hold the lock for the next stack)
    if (auto hub = readFile(join(inst.stateDir, "hub.pid")); hub && alive(atoi(hub->c_str()))) kill(atoi(hub->c_str()), SIGTERM);
    auto pid = readFile(inst.pidFile());
    if (pid && alive(atoi(pid->c_str()))) kill(atoi(pid->c_str()), SIGTERM);
}

}  // namespace apklayer
