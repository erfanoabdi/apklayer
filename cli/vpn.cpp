#include "vpn.h"
#include "env.h"
#include "stack.h"
#include "util.h"

#include <cstdio>
#include <ctime>
#include <map>
#include <sys/stat.h>
#include <unistd.h>

namespace apklayer {

namespace {
// <state>/vpn/active, written by the hub (HubVpn) while a VPN runs: key=value lines (owner, session, since, iface, default = 1 when its routes take the default traffic)
std::string activeFile(const Instance& inst) { return join(join(inst.stateDir, "vpn"), "active"); }

bool readActive(const Instance& inst, std::map<std::string, std::string>& out) {
    for (const std::string& line : split(readFile(activeFile(inst)).value_or(""), '\n')) {
        size_t eq = line.find('=');
        if (eq != std::string::npos) out[line.substr(0, eq)] = line.substr(eq + 1);
    }
    // the file outlives a hub that died: the device is the proof
    struct stat st;
    return !out["iface"].empty() && stat(("/sys/class/net/" + out["iface"]).c_str(), &st) == 0;
}
}  // namespace

void vpnSweep(const Instance& inst) {
    if (access(activeFile(inst).c_str(), F_OK) != 0) return;  // none was made, or it was removed properly
    Spawn list;
    list.argv = {"nmcli", "-t", "-f", "NAME", "connection", "show"};
    list.env = currentEnv();
    std::string prefix = "apklayer-vpn-" + inst.id + "-";
    for (const std::string& name : split(runCapture(list), '\n')) {
        if (name.rfind(prefix, 0) != 0) continue;
        Spawn drop;
        drop.argv = {"nmcli", "connection", "delete", "id", name};
        drop.env = currentEnv();
        runAndWait(drop);
    }
    unlink(activeFile(inst).c_str());
}

int vpnCommand(const Layout& l, const Instance& inst, const std::vector<std::string>& args) {
    if (args.size() != 1 || (args[0] != "status" && args[0] != "disconnect" && args[0] != "sweep")) die("vpn status | disconnect | sweep");
    if (args[0] == "sweep") { vpnSweep(inst); return 0; }
    std::map<std::string, std::string> a;
    if (!readActive(inst, a)) { printf("no VPN\n"); return 0; }
    if (args[0] == "status") {
        time_t since = atol(a["since"].c_str()) / 1000;
        char when[32] = "";
        strftime(when, sizeof when, "%F %T", localtime(&since));
        printf("VPN\t%s\t%s\tsince %s\t%s\t%s\n", a["owner"].c_str(), a["session"].c_str(), when, a["iface"].c_str(), a["default"] == "1" ? "all traffic" : "some routes");
        return 0;
    }
    // HubProtocol.VPN (68): {op} -> the owner is revoked (onRevoke), the TUN and its routes removed
    Spawn call;
    call.argv = {l.btload(), l.androidLibs, "libapklayer_servicetool.so", "--main", "call", "apklayer.instance", "68", "apklayer.IInstanceHub", "s16", "disconnect"};
    call.env = nativeEnv(l, inst).env;
    call.logFile = join(inst.logs(), "vpn.log");
    if (runAndWait(call) != 0) die("the hub did not take the call (see " + call.logFile + ")");
    printf("VPN disconnected\n");
    return 0;
}

}  // namespace apklayer
