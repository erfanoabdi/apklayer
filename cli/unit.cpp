#include "unit.h"

#include <cctype>
#include <cerrno>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <unistd.h>

namespace apklayer {

namespace {
std::string managerDir() { return "/run/user/" + std::to_string(getuid()); }

// A unit name of letters, digits and ":_.-" (an app's .apk path gives its file name).
std::string unitName(const std::string& unit) {
    std::string name = baseName(unit);
    for (char& c : name)
        if (!isalnum(static_cast<unsigned char>(c)) && c != ':' && c != '_' && c != '.' && c != '-') c = '_';
    return name + "-" + std::to_string(getpid());
}
}  // namespace

std::string currentUnit() {
    for (const std::string& line : split(readFile("/proc/self/cgroup").value_or(""), '\n'))
        if (line.rfind("0::", 0) == 0) return baseName(line.substr(3));
    return "";
}

void scopeSpawn(Spawn& spec, const std::string& unit) {
    // the session's manager, also when XDG_RUNTIME_DIR is an instance's own (tests): systemd-run finds it there, the command gets its own back
    if (!exists(managerDir() + "/systemd/private")) return;
    std::string name = unitName(unit);
    std::vector<std::string> argv = {"systemd-run", "--user", "--scope", "--quiet", "--collect", "--unit=" + name + ".scope", "-E", "APKLAYER_UNIT=" + name};
    auto own = spec.env.find("XDG_RUNTIME_DIR");
    if (own != spec.env.end() && own->second != managerDir()) argv.push_back("-E"), argv.push_back("XDG_RUNTIME_DIR=" + own->second);
    argv.push_back("--");
    spec.argv.insert(spec.argv.begin(), argv.begin(), argv.end());
    spec.env["XDG_RUNTIME_DIR"] = managerDir();
}

void enterOwnUnit(std::string unit, bool alwaysOnToo) {
    const char* entered = getenv("APKLAYER_UNIT");
    if (entered && unitName(unit) == entered) { unsetenv("APKLAYER_UNIT"); return; }  // in its own scope (systemd-run kept the pid); not its children's
    if (alwaysOnToo && currentUnit().rfind("apklayer-instance@", 0) == 0) return;
    char path[4096];
    ssize_t n = readlink("/proc/self/exe", path, sizeof path - 1);
    std::string exe = n > 0 ? std::string(path, n) : "";
    if (exe.size() > 10 && exe.compare(exe.size() - 10, 10, " (deleted)") == 0) exe.resize(exe.size() - 10);  // replaced by an update meanwhile
    std::vector<std::string> args = split(readFile("/proc/self/cmdline").value_or(""), '\0');
    if (exe.empty() || args.empty()) return;
    Spawn self;
    self.argv = {exe};
    self.argv.insert(self.argv.end(), args.begin() + 1, args.end());
    self.env = currentEnv();
    scopeSpawn(self, unit);
    if (self.argv[0] == exe) return;  // no user manager: stay
    trace("own unit " + unitName(unit));
    fflush(stdout);
    fflush(stderr);
    std::vector<std::string> storage;
    std::vector<char*> argv, envp;
    for (std::string& a : self.argv) argv.push_back(a.data());
    argv.push_back(nullptr);
    execvpe(argv[0], argv.data(), toEnvp(self.env, storage, envp));
    fprintf(stderr, "apklayer: cannot run systemd-run: %s\n", strerror(errno));  // stays in the caller's unit
}

}  // namespace apklayer
