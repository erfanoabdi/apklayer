#include "autostart.h"

#include <cstdio>
#include <unistd.h>

namespace apklayer {

namespace {
// The user's systemd manager reads units from its own XDG_CONFIG_HOME, not this shell's (a private one, as the tests set, would hide the unit);
// systemctl --user enable resolves the same paths on the client side, so it runs with the manager's value too.
Env managerEnv() {
    Env env = currentEnv();
    env.erase("XDG_CONFIG_HOME");
    Spawn s;
    s.argv = {"systemctl", "--user", "show-environment"};
    s.env = env;
    int status = 0;
    for (const std::string& line : split(runCapture(s, &status), '\n'))
        if (line.rfind("XDG_CONFIG_HOME=", 0) == 0) env["XDG_CONFIG_HOME"] = line.substr(16);
    return env;
}
std::string unitDir() {
    Env env = managerEnv();
    return join(env.count("XDG_CONFIG_HOME") ? env["XDG_CONFIG_HOME"] : join(homeDir(), ".config"), "systemd/user");
}
std::string unitName(const std::string& id) { return "apklayer-instance@" + id + ".service"; }

// A user unit per instance (a template: %i is the instance id): `apklayer hub` runs the instance's stack and hub in the foreground for as long as the
// hub lives; the hub never exits when idle while the marker file exists (HubDirectory.alwaysOn). A stop by `apklayer stop` or systemd (SIGTERM, exit
// 143) is a success, a crash restarts it.
std::string unitText(const Layout& l) {
    std::string cli = l.cliBinary();
    return "[Unit]\n"
           "Description=apklayer instance %i: Android services and hub, always on (background push, alarms, jobs)\n"
           "\n"
           "[Service]\n"
           "Type=simple\n"
           "ExecStart=" + cli + " --instance %i hub\n"
           "ExecStopPost=" + cli + " --instance %i stop\n"
           "SuccessExitStatus=143\n"
           "Restart=on-failure\n"
           "RestartSec=5\n"
           "TimeoutStopSec=15\n"
           "\n"
           "[Install]\n"
           "WantedBy=default.target\n";
}

int systemctl(std::vector<std::string> args) {
    Spawn s;
    s.argv = {"systemctl", "--user"};
    s.argv.insert(s.argv.end(), args.begin(), args.end());
    s.env = managerEnv();
    return runAndWait(s);
}
}  // namespace

bool autostartOn(const Instance& inst) { return exists(join(inst.stateDir, "autostart")); }

int autostartCommand(const Layout& l, const std::vector<std::string>& args) {
    const std::string id = args[0], state = args.size() > 1 ? args[1] : "";
    bool now = !(args.size() > 2 && args[2] == "--no-start");
    if (id.empty() || id.find_first_not_of("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_") != std::string::npos)
        die("not an instance name: " + id + " (letters, digits, - and _)");
    Instance inst = Instance::open(l, id);
    std::string marker = join(inst.stateDir, "autostart");
    if (state.empty()) { puts(autostartOn(inst) ? "on" : "off"); return 0; }
    if (state != "on" && state != "off") die("instance autostart <id> [on|off]");
    if (state == "on") {
        makeDirs(unitDir());
        if (!writeFile(join(unitDir(), "apklayer-instance@.service"), unitText(l))) die("cannot write " + join(unitDir(), "apklayer-instance@.service"));
        writeFile(marker, "1\n");
        if (systemctl({"daemon-reload"}) != 0) fprintf(stderr, "apklayer: systemctl --user daemon-reload failed: the unit is written, starting it at login needs a systemd user session\n");
        else if (systemctl(now ? std::vector<std::string>{"enable", "--now", unitName(id)} : std::vector<std::string>{"enable", unitName(id)}) != 0) fprintf(stderr, "apklayer: could not enable %s\n", unitName(id).c_str());
        printf("apklayer: instance %s is always on: it starts at login and its hub stays up (unit %s)\n", id.c_str(), unitName(id).c_str());
        return 0;
    }
    unlink(marker.c_str());
    systemctl({"disable", unitName(id)});  // the running hub ends when idle (without --now: apps running in it keep running)
    printf("apklayer: instance %s is no longer always on (its hub ends when idle)\n", id.c_str());
    return 0;
}

}  // namespace apklayer
