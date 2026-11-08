/* apklayer's userspace binder (docs/BINDER.md): the messages between a process's binder route (bridge/native/binder_route.c, in every
 * Android process through LD_PRELOAD) and the instance's binder daemon (bridge/binderd). One stream socket per opened binder fd (the
 * process: its close is the process's death) and one per thread that uses it (a thread's ioctls block on its own socket). */
#pragma once
#include <stdint.h>

#define BD_VERSION 1
#define BD_MAX_FDS 250 /* fds per sendmsg (SCM_MAX_FD is 253); more go in follow-up one-byte messages */

enum bd_op {
    BD_HELLO_PROC = 1, /* payload: the context name ("binder", "hwbinder", "vndbinder"); a0: BD_VERSION. Reply: write_consumed = the proc cookie */
    BD_HELLO_THREAD,   /* a0: proc cookie, a1: tid. The daemon checks SO_PEERCRED's pid against the proc's */
    BD_IOCTL,          /* a0: ioctl command; payload: the argument (BINDER_WRITE_READ: the marshalled write stream, a1 read size, a2 BD_RD_* flags) */
    BD_MMAP,           /* a0: size. Reply: one fd, the receive area (a memfd the daemon maps too) */
    BD_MAPPED,         /* a0: the address the process mapped the receive area at (the daemon writes pointers into it) */
};

#define BD_RD_NONBLOCK 1u /* the fd was opened O_NONBLOCK */
#define BD_RD_FIRST 2u    /* read_consumed was 0: the read starts with BR_NOOP, as the kernel's */

struct bd_req {
    uint32_t len; /* the whole message, header included */
    uint32_t op;
    uint32_t nfds; /* fds sent with it, in order */
    uint32_t pad;
    uint64_t a0, a1, a2;
};

struct bd_rep {
    uint32_t len; /* header + payload + nfix fixups */
    int32_t ret;  /* 0 or -errno */
    uint32_t nfds;
    uint32_t ndrain; /* wake bytes the daemon wrote to the proc socket since this proc's last reply: the route reads them away */
    uint64_t write_consumed, read_consumed; /* BINDER_WRITE_READ: write_consumed in marshalled bytes; read_consumed = payload size */
    uint32_t nfix;
    uint32_t pad;
};

/* after the read bytes: store fds[fd_index] as a 32-bit value at `offset` of the receive area (the kernel's fd fixups). An fd of an fd array
 * (fda) is closed by the route when the buffer at `buffer` (a receive-area address) is freed, as the kernel closes them on BC_FREE_BUFFER */
struct bd_fix {
    uint64_t offset;
    uint32_t fd_index;
    uint32_t fda;
    uint64_t buffer;
};

/* BC_TRANSACTION(_SG) and BC_REPLY(_SG) in the marshalled write stream: the command, the binder_transaction_data(_sg) as written, this, then the
 * data (padded to 8), the offsets (padded to 8) and the scatter-gather buffers of the BINDER_TYPE_PTR objects in offset order (each padded to 8).
 * The transaction's fds (BINDER_TYPE_FD, then each BINDER_TYPE_FDA's) come with the message, nfds of them, in object order. */
struct bd_txn {
    uint32_t nfds;
    uint32_t pad;
    uint64_t sg_size;
};

#ifdef __cplusplus
extern "C" {
#endif
/* wire.c: one message out (header and payload in one sendmsg, the fds after it) and in. 0 or -errno; EINTR is retried. */
int bd_send(int sock, const void *hdr, uint32_t hlen, const void *payload, uint32_t plen, const int *fds, uint32_t nfds);
/* reads exactly hlen header bytes, taking the fds that came with the first part (at most BD_MAX_FDS) into fds; *got: how many */
int bd_recv_hdr(int sock, void *hdr, uint32_t hlen, int *fds, uint32_t *got);
int bd_recv_all(int sock, void *buf, uint64_t len);
/* the rest of a message's fds (beyond the first BD_MAX_FDS) */
int bd_recv_more_fds(int sock, int *fds, uint32_t have, uint32_t total);
#ifdef __cplusplus
}
#endif
