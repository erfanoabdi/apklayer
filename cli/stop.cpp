// `apklayer stop`: the instance's recorded processes, then whatever of the instance is left (what started while the stop ran).
#include "stack.h"
#include "binder.h"

#include <fcntl.h>
#include <set>
#include <sys/file.h>
#include <unistd.h>

namespace apklayer {

namespace {
std::string userNs(int pid) {
    char link[64] = {};
    ssize_t n = readlink(("/proc/" + std::to_string(pid) + "/ns/user").c_str(), link, sizeof link - 1);
    return n > 0 ? std::string(link, n) : "";
}

// This process and its callers (`stop` run by the unit's ExecStopPost, by apklayerd, from a shell that exports APKLAYER_INSTANCE): never ended.
std::set<int> selfAndCallers() {
    std::set<int> own;
    for (int pid = getpid(); pid > 1 && own.insert(pid).second;) {
        std::string stat = readFile("/proc/" + std::to_string(pid) + "/stat").value_or("");
        size_t end = stat.rfind(')');
        if (end == std::string::npos || stat.size() < end + 4) break;
        pid = atoi(stat.c_str() + end + 4);  // ") S <ppid>"
    }
    return own;
}

// Every process of the instance: what apklayer starts carries the instance's APKLAYER_INSTANCE and APKLAYER_LOGS (nativeEnv; a shell that only
// exports APKLAYER_INSTANCE is no such process), a sandboxed one (its environment private) lives in the stack's user namespace (sandboxNs).
std::vector<int> instanceProcesses(const Layout& l, const Instance& inst, const std::string& sandboxNs) {
    const std::string id = std::string(1, '\0') + "APKLAYER_INSTANCE=" + inst.id + '\0', logs = std::string(1, '\0') + "APKLAYER_LOGS=" + inst.logs() + '\0';
    std::set<int> spared = selfAndCallers();
    spared.insert(shellPid(l));  // the session's, shared with other instances' windows (endShellUnlessShared ended it when it was not)
    std::vector<int> found;
    for (const std::string& name : listDir("/proc")) {
        int pid = atoi(name.c_str());
        if (pid <= 0 || spared.count(pid)) continue;
        std::string env = std::string(1, '\0') + readFile("/proc/" + name + "/environ").value_or("") + '\0';
        if ((env.find(id) != std::string::npos && env.find(logs) != std::string::npos) || (!sandboxNs.empty() && userNs(pid) == sandboxNs))
            found.push_back(pid);
    }
    return found;
}
}  // namespace

void stopStack(const Layout& l, const Instance& inst) {
    endShellUnlessShared(l, inst.id);
    auto stack = readFile(inst.pidFile());
    std::string sandboxNs = stack ? userNs(atoi(stack->c_str())) : "";
    if (sandboxNs == userNs(getpid())) sandboxNs.clear();  // a stack outside the sandbox: its namespace is everyone's
    // the hub first: it restarts a component host that ends (before, one this stop had ended came back and outlived it with its spare holder).
    // The hub JVM, then its launcher: the next hub can take hub.lock as soon as stop returns
    if (auto hub = readFile(join(inst.stateDir, "hub.pid")))
        for (const std::string& pid : split(*hub, '\n')) terminate(atoi(pid.c_str()));
    for (const std::string& f : listDir(inst.stateDir)) {  // launches of this instance: their JVM and window sink
        if (f.rfind("run.", 0) != 0) continue;
        auto record = readFile(join(inst.stateDir, f));
        if (record) for (const std::string& pid : split(*record, '\n')) terminate(atoi(pid.c_str()));
        unlink(join(inst.stateDir, f).c_str());
    }
    if (stack) terminate(atoi(stack->c_str()));
    // the userspace binder (docs/BINDER.md): it would end by itself once its clients are gone; stop means now
    if (auto binderd = readFile(join(binderSocketDir(l.runtimeHome), inst.id + ".pid"))) terminate(atoi(binderd->c_str()));
    // what started while the above ran (a launch racing the stop, the spare holder it started): the instance's processes until none is left.
    // start.lock held: no launch starts a stack meanwhile (one blocked on it is an instance process and ends here)
    int lock = open(join(inst.stateDir, "start.lock").c_str(), O_CREAT | O_RDWR | O_CLOEXEC, 0644);
    flock(lock, LOCK_EX);
    for (int round = 0; round < 10; round++) {  // bounded: a process SIGKILL does not end (D state) must not hang stop
        std::vector<int> left = instanceProcesses(l, inst, sandboxNs);
        if (left.empty()) break;
        for (int pid : left) terminate(pid);
    }
    for (const std::string& f : listDir(inst.stateDir))  // the records of what the sweep ended
        if (f.rfind("run.", 0) == 0) unlink(join(inst.stateDir, f).c_str());
    endShellUnlessShared(l, inst.id);  // a shell a racing launch started after the first check (the sweep spares the session's shell)
    close(lock);
}

}  // namespace apklayer
