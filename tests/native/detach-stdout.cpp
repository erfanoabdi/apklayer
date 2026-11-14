// cli/util.cpp spawn(): a detached process without a log file does not hold the launcher's stdout (`flatpak run apklayer | cat` waited for it).
// Build: g++ -std=c++17 -I cli tests/native/detach-stdout.cpp cli/util.cpp -o detach-stdout
#include <cstdio>
#include <poll.h>
#include <signal.h>
#include <unistd.h>
#include "util.h"
using namespace apklayer;
int main() {
    int p[2];
    if (pipe(p) != 0) return 2;
    dup2(p[1], 1);  // the launcher's stdout is the pipe
    Spawn s;
    s.argv = {"sleep", "30"};
    s.detach = true;
    int pid = spawn(s);
    close(p[1]);
    close(1);
    struct pollfd f = {p[0], POLLIN, 0};
    int r = poll(&f, 1, 3000);  // POLLHUP at once when nothing holds the write end
    if (pid > 0) kill(pid, SIGTERM);
    bool ok = r == 1 && (f.revents & POLLHUP);
    fprintf(stderr, "%s\n", ok ? "PASS" : "FAIL");
    return ok ? 0 : 1;
}
