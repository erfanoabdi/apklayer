// `apklayer vpn-guard <sock> <ifname> <underlying> <protect-sock> <nonblock> [-- teardown command...]`: the native half of the host-wide VPN (docs/VPN.md).
// Started by the hub (HubVpn) with its stdin a pipe, after the privileged side made the persistent TUN <ifname> owned by this user (NetworkManager or the
// polkit helper). It opens the TUN (TUNSETIFF needs no capability on a device owned by the user), hands the descriptor to the hub over <sock> (the hub passes
// it to the VPN app: Builder.establish), then serves VpnService.protect(): the hub sends it a socket (SCM_RIGHTS) on <protect-sock> and it binds the socket to the
// underlying interface (SO_BINDTODEVICE, allowed unprivileged since Linux 5.7), so the app's tunnel sockets leave through it and not into the VPN.
// It ends the VPN on its own: when the TUN loses its carrier (the app closed it or died) or its stdin closes (the hub ended, however it did), it runs the
// teardown command (nmcli connection delete ...) and exits. stdout: "ready <ifname>", "gone <ifname>", "error <why>".
#include "vpn.h"

#include <cerrno>
#include <cstdio>
#include <cstring>
#include <fcntl.h>
#include <linux/if_tun.h>
#include <linux/netlink.h>
#include <linux/rtnetlink.h>
#include <net/if.h>
#include <poll.h>
#include <sys/ioctl.h>
#include <csignal>
#include <sys/signalfd.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/un.h>
#include <sys/wait.h>
#include <unistd.h>

#ifndef IFF_LOWER_UP
#define IFF_LOWER_UP 0x10000  // linux/if.h's (it clashes with net/if.h)
#endif

namespace apklayer {

namespace {
bool fail(const std::string& why) {
    printf("error %s\n", why.c_str());
    fflush(stdout);
    return false;
}

sockaddr_un unixAddr(const std::string& path) {
    sockaddr_un a{};
    a.sun_family = AF_UNIX;
    snprintf(a.sun_path, sizeof a.sun_path, "%s", path.c_str());
    return a;
}

// The TUN <name> (made persistent and owned by this user by the privileged side), non-blocking when asked.
int openTun(const std::string& name, bool nonblock) {
    int fd = open("/dev/net/tun", O_RDWR | O_CLOEXEC);
    if (fd < 0) { fail("/dev/net/tun: " + std::string(strerror(errno))); return -1; }
    ifreq r{};
    snprintf(r.ifr_name, IFNAMSIZ, "%s", name.c_str());
    r.ifr_flags = IFF_TUN | IFF_NO_PI;
    if (ioctl(fd, TUNSETIFF, &r) != 0) { fail("TUNSETIFF " + name + ": " + strerror(errno)); close(fd); return -1; }
    if (nonblock) fcntl(fd, F_SETFL, fcntl(fd, F_GETFL) | O_NONBLOCK);
    return fd;
}

// fd (or -1: none) with a text over a connected unix socket
bool sendWithFd(int sock, const std::string& text, int fd) {
    iovec iov{const_cast<char*>(text.data()), text.size()};
    msghdr msg{};
    char control[CMSG_SPACE(sizeof(int))] = {};
    msg.msg_iov = &iov;
    msg.msg_iovlen = 1;
    if (fd >= 0) {
        msg.msg_control = control;
        msg.msg_controllen = sizeof control;
        cmsghdr* c = CMSG_FIRSTHDR(&msg);
        c->cmsg_level = SOL_SOCKET;
        c->cmsg_type = SCM_RIGHTS;
        c->cmsg_len = CMSG_LEN(sizeof(int));
        memcpy(CMSG_DATA(c), &fd, sizeof(int));
    }
    return sendmsg(sock, &msg, MSG_NOSIGNAL) == static_cast<ssize_t>(text.size());
}

bool handOver(const std::string& sock, int fd, const std::string& name) {
    int s = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    sockaddr_un addr = unixAddr(sock);
    if (s < 0 || connect(s, reinterpret_cast<sockaddr*>(&addr), sizeof addr) != 0) return fail("cannot reach the hub: " + std::string(strerror(errno)));
    char ack;
    bool ok = sendWithFd(s, name + "\n", fd) && read(s, &ack, 1) >= 0;  // the hub holds its copy before this one closes
    close(s);
    return ok || fail("cannot hand the TUN over");
}

// One protect request: a socket arrives, it is bound to the underlying interface; the answer is one byte, 1 for done.
void serveProtect(int listener, const std::string& underlying) {
    int peer = accept4(listener, nullptr, nullptr, SOCK_CLOEXEC);
    if (peer < 0) return;
    char byte;
    iovec iov{&byte, 1};
    msghdr msg{};
    char control[CMSG_SPACE(sizeof(int))] = {};
    msg.msg_iov = &iov;
    msg.msg_iovlen = 1;
    msg.msg_control = control;
    msg.msg_controllen = sizeof control;
    char ok = 0;
    if (recvmsg(peer, &msg, MSG_CMSG_CLOEXEC) == 1)
        for (cmsghdr* c = CMSG_FIRSTHDR(&msg); c; c = CMSG_NXTHDR(&msg, c))
            if (c->cmsg_level == SOL_SOCKET && c->cmsg_type == SCM_RIGHTS) {
                int sock;
                memcpy(&sock, CMSG_DATA(c), sizeof sock);
                ok = setsockopt(sock, SOL_SOCKET, SO_BINDTODEVICE, underlying.c_str(), underlying.size() + 1) == 0;
                if (!ok) fprintf(stderr, "vpn-guard: protect: %s\n", strerror(errno));
                close(sock);
            }
    if (write(peer, &ok, 1) < 0) { /* the hub went */ }
    close(peer);
}

void teardown(const std::vector<std::string>& command) {
    if (command.empty()) return;
    pid_t pid = fork();
    if (pid == 0) {
        std::vector<char*> argv;
        for (const std::string& a : command) argv.push_back(const_cast<char*>(a.c_str()));
        argv.push_back(nullptr);
        execvp(argv[0], argv.data());
        _exit(127);
    }
    int status;
    if (pid > 0) waitpid(pid, &status, 0);
}
}  // namespace

int vpnGuard(const std::vector<std::string>& args) {
    if (args.size() < 5) { fprintf(stderr, "usage: apklayer vpn-guard <sock> <ifname> <underlying> <protect-sock> <nonblock> [-- teardown...]\n"); return 2; }
    const std::string &sock = args[0], &name = args[1], &underlying = args[2], &protectSock = args[3];
    std::vector<std::string> command;
    if (args.size() > 6 && args[5] == "--") command.assign(args.begin() + 6, args.end());
    signal(SIGPIPE, SIG_IGN);
    sigset_t term;
    sigemptyset(&term);
    sigaddset(&term, SIGTERM);
    sigaddset(&term, SIGINT);
    sigprocmask(SIG_BLOCK, &term, nullptr);
    int signals = signalfd(-1, &term, SFD_CLOEXEC);
    int link = socket(AF_NETLINK, SOCK_RAW | SOCK_CLOEXEC, NETLINK_ROUTE);  // before the TUN attaches: the carrier going up is seen
    sockaddr_nl nl{};
    nl.nl_family = AF_NETLINK;
    nl.nl_groups = RTMGRP_LINK;
    if (link < 0 || bind(link, reinterpret_cast<sockaddr*>(&nl), sizeof nl) != 0) return fail("netlink: " + std::string(strerror(errno))), 1;
    int tun = openTun(name, args[4] == "1");
    if (tun < 0) return 1;
    unsigned index = if_nametoindex(name.c_str());
    int listener = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    unlink(protectSock.c_str());
    sockaddr_un addr = unixAddr(protectSock);
    if (listener < 0 || bind(listener, reinterpret_cast<sockaddr*>(&addr), sizeof addr) != 0 || listen(listener, 4) != 0) return fail("protect socket: " + std::string(strerror(errno))), 1;
    chmod(protectSock.c_str(), 0600);
    if (!handOver(sock, tun, name)) { teardown(command); return 1; }
    close(tun);  // the hub's and then the app's descriptors keep the device up
    printf("ready %s\n", name.c_str());
    fflush(stdout);
    bool seenUp = false;
    pollfd fds[4] = {{STDIN_FILENO, POLLIN, 0}, {link, POLLIN, 0}, {listener, POLLIN, 0}, {signals, POLLIN, 0}};
    for (;;) {
        while (poll(fds, 4, -1) < 0 && errno == EINTR) {}
        if (fds[2].revents) serveProtect(listener, underlying);
        if (fds[3].revents) break;
        if (fds[0].revents) {
            char buf[256];
            if (read(STDIN_FILENO, buf, sizeof buf) <= 0) break;  // the hub ended
        }
        if (fds[1].revents) {
            char buf[8192];
            ssize_t got = recv(link, buf, sizeof buf, 0);
            bool gone = false;
            for (nlmsghdr* h = reinterpret_cast<nlmsghdr*>(buf); got > 0 && NLMSG_OK(h, static_cast<size_t>(got)); h = NLMSG_NEXT(h, got)) {
                if (h->nlmsg_type != RTM_NEWLINK && h->nlmsg_type != RTM_DELLINK) continue;
                const ifinfomsg* info = reinterpret_cast<ifinfomsg*>(NLMSG_DATA(h));
                if (static_cast<unsigned>(info->ifi_index) != index) continue;
                if (h->nlmsg_type == RTM_DELLINK || (seenUp && !(info->ifi_flags & IFF_LOWER_UP))) gone = true;
                else if (info->ifi_flags & IFF_LOWER_UP) seenUp = true;
            }
            if (gone) { printf("gone %s\n", name.c_str()); fflush(stdout); break; }
        }
    }
    teardown(command);
    unlink(protectSock.c_str());
    return 0;
}

}  // namespace apklayer
