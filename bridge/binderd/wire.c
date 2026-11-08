/* Messages on the userspace binder's sockets (protocol.h): framing over SOCK_STREAM with fds as SCM_RIGHTS. Compiled into the daemon and
 * into the route of every Android process (bridge/native/binder_route.c). */
#define _GNU_SOURCE
#include "protocol.h"

#include <errno.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/uio.h>
#include <unistd.h>

static int send_iov(int sock, struct iovec *iov, int n, const int *fds, uint32_t nfds)
{
    char cbuf[CMSG_SPACE(sizeof(int) * BD_MAX_FDS)];
    struct msghdr m = {0};
    m.msg_iov = iov;
    m.msg_iovlen = n;
    if (nfds) {
        memset(cbuf, 0, sizeof cbuf);
        m.msg_control = cbuf;
        m.msg_controllen = CMSG_SPACE(sizeof(int) * nfds);
        struct cmsghdr *c = CMSG_FIRSTHDR(&m);
        c->cmsg_level = SOL_SOCKET;
        c->cmsg_type = SCM_RIGHTS;
        c->cmsg_len = CMSG_LEN(sizeof(int) * nfds);
        memcpy(CMSG_DATA(c), fds, sizeof(int) * nfds);
    }
    for (;;) {
        ssize_t r = sendmsg(sock, &m, MSG_NOSIGNAL);
        if (r < 0) {
            if (errno == EINTR) continue;
            return -errno;
        }
        m.msg_control = NULL; /* the fds went with the first part */
        m.msg_controllen = 0;
        while (r > 0 && m.msg_iovlen) {
            if ((size_t)r >= m.msg_iov->iov_len) { r -= m.msg_iov->iov_len; m.msg_iov++; m.msg_iovlen--; }
            else { m.msg_iov->iov_base = (char *)m.msg_iov->iov_base + r; m.msg_iov->iov_len -= r; r = 0; }
        }
        while (m.msg_iovlen && m.msg_iov->iov_len == 0) { m.msg_iov++; m.msg_iovlen--; }
        if (!m.msg_iovlen) return 0;
    }
}

int bd_send(int sock, const void *hdr, uint32_t hlen, const void *payload, uint32_t plen, const int *fds, uint32_t nfds)
{
    struct iovec iov[2] = {{(void *)hdr, hlen}, {(void *)payload, plen}};
    uint32_t first = nfds < BD_MAX_FDS ? nfds : BD_MAX_FDS;
    int r = send_iov(sock, iov, plen ? 2 : 1, fds, first);
    for (uint32_t done = first; !r && done < nfds;) {
        uint32_t n = nfds - done < BD_MAX_FDS ? nfds - done : BD_MAX_FDS;
        char byte = 0;
        struct iovec one = {&byte, 1};
        r = send_iov(sock, &one, 1, fds + done, n);
        done += n;
    }
    return r;
}

static int recv_part(int sock, void *buf, uint32_t len, int *fds, uint32_t *got, uint32_t max)
{
    char cbuf[CMSG_SPACE(sizeof(int) * BD_MAX_FDS)];
    struct iovec iov = {buf, len};
    struct msghdr m = {0};
    m.msg_iov = &iov;
    m.msg_iovlen = 1;
    m.msg_control = cbuf;
    m.msg_controllen = sizeof cbuf;
    ssize_t r;
    do r = recvmsg(sock, &m, MSG_CMSG_CLOEXEC);
    while (r < 0 && errno == EINTR);
    if (r < 0) return -errno;
    if (r == 0) return -EPIPE;
    for (struct cmsghdr *c = CMSG_FIRSTHDR(&m); c; c = CMSG_NXTHDR(&m, c)) {
        if (c->cmsg_level != SOL_SOCKET || c->cmsg_type != SCM_RIGHTS) continue;
        uint32_t n = (c->cmsg_len - CMSG_LEN(0)) / sizeof(int);
        int *in = (int *)CMSG_DATA(c);
        for (uint32_t i = 0; i < n; i++) {
            if (fds && *got < max) fds[(*got)++] = in[i];
            else close(in[i]); /* more than announced: not ours to keep */
        }
    }
    return (int)r;
}

int bd_recv_hdr(int sock, void *hdr, uint32_t hlen, int *fds, uint32_t *got)
{
    *got = 0;
    int r = recv_part(sock, hdr, hlen, fds, got, BD_MAX_FDS);
    if (r < 0) return r;
    return (uint32_t)r < hlen ? bd_recv_all(sock, (char *)hdr + r, hlen - r) : 0;
}

int bd_recv_all(int sock, void *buf, uint64_t len)
{
    char *p = buf;
    while (len) {
        ssize_t r = recv(sock, p, len, 0);
        if (r < 0 && errno == EINTR) continue;
        if (r < 0) return -errno;
        if (r == 0) return -EPIPE;
        p += r;
        len -= r;
    }
    return 0;
}

int bd_recv_more_fds(int sock, int *fds, uint32_t have, uint32_t total)
{
    while (have < total) {
        char byte;
        uint32_t got = have;
        int r = recv_part(sock, &byte, 1, fds, &got, total);
        if (r < 0) return r;
        have = got;
    }
    return 0;
}
