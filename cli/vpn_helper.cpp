// apklayer-vpn-helper: the root half of the host-wide VPN where NetworkManager cannot make the TUN (docs/VPN.md). Run by the hub through pkexec (polkit action
// org.apklayer.vpn, data/org.apklayer.vpn.policy: the user's password, once per VPN). It does only this: make the persistent TUN <ifname> owned by the calling user
// (PKEXEC_UID), set its MTU, addresses and routes (metric 50), the DNS servers through systemd-resolved when there is one, say "ready", and remove the
// device (and with it the routes and DNS) when its stdin closes, on SIGTERM/SIGHUP, or after --watchdog <seconds> without a "confirm" line.
// Input, one per line, before "go": ifname tunapk<name> | mtu N | addr CIDR | route CIDR | dns IP. Everything is validated; nothing is passed to a shell.
// The commands are the `ip` tool's, run by absolute path with an empty environment.
#include <arpa/inet.h>
#include <cerrno>
#include <csignal>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <poll.h>
#include <string>
#include <sys/wait.h>
#include <unistd.h>
#include <vector>

namespace {
std::string tool(std::initializer_list<const char*> candidates) {
    for (const char* c : candidates) if (access(c, X_OK) == 0) return c;
    return "";
}

int run(const std::vector<std::string>& argv) {
    pid_t pid = fork();
    if (pid == 0) {
        std::vector<char*> args;
        for (const std::string& a : argv) args.push_back(const_cast<char*>(a.c_str()));
        args.push_back(nullptr);
        char* env[] = {nullptr};
        execve(args[0], args.data(), env);
        _exit(127);
    }
    int status = -1;
    if (pid > 0) waitpid(pid, &status, 0);
    return WIFEXITED(status) ? WEXITSTATUS(status) : -1;
}

bool validName(const std::string& s) {
    if (s.size() < 7 || s.size() > 15 || s.compare(0, 6, "tunapk") != 0) return false;
    for (char c : s) if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'))) return false;
    return true;
}

bool validAddress(const std::string& s, bool cidr) {
    size_t slash = s.find('/');
    if ((slash != std::string::npos) != cidr) return false;
    std::string host = s.substr(0, slash);
    bool v6 = host.find(':') != std::string::npos;
    unsigned char buf[16];
    if (inet_pton(v6 ? AF_INET6 : AF_INET, host.c_str(), buf) != 1) return false;
    if (!cidr) return true;
    std::string prefix = s.substr(slash + 1);
    if (prefix.empty() || prefix.size() > 3 || prefix.find_first_not_of("0123456789") != std::string::npos) return false;
    return atoi(prefix.c_str()) <= (v6 ? 128 : 32);
}

std::string ifname, ip;
volatile sig_atomic_t stop;

void teardown() {
    if (!ifname.empty()) run({ip, "link", "del", "dev", ifname});  // its routes and addresses go with it
    ifname.clear();
}

int fail(const char* why) {
    printf("error %s\n", why);
    fflush(stdout);
    teardown();
    return 1;
}
}  // namespace

int main(int argc, char** argv) {
    int watchdog = 0;
    if (argc == 3 && std::string(argv[1]) == "--watchdog") watchdog = atoi(argv[2]);
    if (!(argc == 1 || (argc == 3 && watchdog >= 1 && watchdog <= 600))) return 2;
    const char* from = getenv("PKEXEC_UID");
    char* end = nullptr;
    long uid = from ? strtol(from, &end, 10) : -1;
    if (!from || *end || uid <= 0 || uid > 0x7fffffff) { printf("error run through pkexec by a user\n"); return 1; }
    ip = tool({"/usr/sbin/ip", "/sbin/ip", "/usr/bin/ip", "/bin/ip"});
    std::string resolvectl = tool({"/usr/bin/resolvectl", "/bin/resolvectl"});
    if (ip.empty()) { printf("error no ip tool\n"); return 1; }
    signal(SIGPIPE, SIG_IGN);

    std::string name, mtu;
    std::vector<std::string> addrs, routes, dns;
    char buf[4096];
    std::string input;
    bool go = false;
    while (!go) {  // the spec, line by line; EOF before "go" is a refusal
        ssize_t got = read(STDIN_FILENO, buf, sizeof buf);
        if (got <= 0) return 1;
        input.append(buf, got);
        if (input.size() > 16384) return fail("input too long");
        for (size_t nl; !go && (nl = input.find('\n')) != std::string::npos; input.erase(0, nl + 1)) {
            std::string line = input.substr(0, nl), key = line.substr(0, line.find(' ')), value = line.find(' ') == std::string::npos ? "" : line.substr(line.find(' ') + 1);
            if (key == "go") go = true;
            else if (key == "ifname" && validName(value)) name = value;
            else if (key == "mtu" && value.size() <= 5 && value.find_first_not_of("0123456789") == std::string::npos && (atoi(value.c_str()) == 0 || (atoi(value.c_str()) >= 576 && atoi(value.c_str()) <= 65535))) mtu = value;
            else if (key == "addr" && validAddress(value, true) && addrs.size() < 32) addrs.push_back(value);
            else if (key == "route" && validAddress(value, true) && routes.size() < 64) routes.push_back(value);
            else if (key == "dns" && validAddress(value, false) && dns.size() < 8) dns.push_back(value);
            else return fail("bad line");
        }
    }
    if (name.empty()) return fail("no ifname");
    struct sigaction sa{};
    sa.sa_handler = [](int) { stop = 1; };
    sigaction(SIGTERM, &sa, nullptr);
    sigaction(SIGHUP, &sa, nullptr);
    sigaction(SIGINT, &sa, nullptr);
    ifname = name;
    if (run({ip, "tuntap", "add", "dev", name, "mode", "tun", "user", std::to_string(uid)}) != 0) { ifname.clear(); return fail("cannot make the TUN (exists?)"); }
    bool ok = (mtu.empty() || mtu == "0" || run({ip, "link", "set", "dev", name, "mtu", mtu}) == 0) && run({ip, "link", "set", "dev", name, "up"}) == 0;
    for (const std::string& a : addrs) ok = ok && run({ip, "addr", "add", a, "dev", name}) == 0;
    for (const std::string& r : routes) ok = ok && run({ip, r.find(':') != std::string::npos ? "-6" : "-4", "route", "add", r, "dev", name, "metric", "50"}) == 0;
    if (!ok) return fail("cannot configure the TUN");
    if (!dns.empty() && !resolvectl.empty()) {
        std::vector<std::string> command = {resolvectl, "dns", name};
        command.insert(command.end(), dns.begin(), dns.end());
        run(command);
        for (const std::string& r : routes) if (r == "0.0.0.0/1" || r == "::/1") { run({resolvectl, "domain", name, "~."}); break; }
    }
    printf("ready\n");
    fflush(stdout);
    // the VPN lives as long as the hub's pipe: a "confirm" line disarms the watchdog; EOF, a signal or the watchdog remove it
    int remaining = watchdog * 1000;
    bool armed = watchdog > 0;
    while (!stop) {
        pollfd p{STDIN_FILENO, POLLIN, 0};
        int r = poll(&p, 1, armed ? remaining : -1);
        if (r == 0) break;  // the watchdog ran out
        if (r < 0) continue;
        ssize_t got = read(STDIN_FILENO, buf, sizeof buf);
        if (got <= 0) break;
        if (std::string(buf, got).find("confirm") != std::string::npos) armed = false;
    }
    teardown();
    return 0;
}
