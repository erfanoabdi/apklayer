# Userspace binder (design note)

apklayer without kernel binder: no `binder_linux` module, no binderfs mount, no root step, no per-instance devices that live until reboot, no
Flatpak host step. Plan and state: `docs/PLAN.md` "Plan: binder in userspace, no kernel binder". This note is what was built (steps 1 and 2).

Select it with `APKLAYER_BINDER=userspace` (`=kernel` forces the kernel's), or per instance with a file `<instance data root>/binder` that says
`userspace`. Kernel binder stays the default.

## Pieces

| Piece | Where | What |
|---|---|---|
| daemon | `bridge/binderd/` (`apklayer-binderd`, C++, Meson) | the binder driver model, one per instance, one thread |
| route | `bridge/native/binder_route.c`, `binder_route_write.h` (in `libapklayer-preload.so`) | a process's side: opens, ioctls, mmap, close of binder fds |
| fd hooks | `patches/bionic_translation/0047-fd-hooks.patch` | libc_bio asks `bionic_fd_hooks` (the route) for open/ioctl/mmap/close |
| wire | `bridge/binderd/protocol.h`, `wire.c` | the messages, shared by both sides |
| CLI | `cli/binder.cpp` (`userspaceBinder`, `ensureBinderDaemon`), `cli/stack.cpp`, `cli/env.cpp` | selection, starting the daemon, the environment |

libbinder, libhwbinder, servicemanager, Java and native Android code are unchanged. Binder objects move freely between processes, as with
the kernel driver (what RPC binder cannot do, PLAN).

## How a process reaches the daemon

Every Android process runs on bionic_translation with `libapklayer-preload.so` preloaded, and the path layer maps `/dev/binder` (the
instance's `/dev/binderfs/binder-<id>` for an instance other than default). The route takes over a binder path *after* the path layer:

- `/dev/{binder,hwbinder,vndbinder}`: this instance's daemon, socket `$APKLAYER_BINDER_DIR/<instance>.sock` (`$XDG_RUNTIME_DIR/apklayer-binder`).
- `/dev/binderfs/<ctx>-<id>`: instance `<id>`'s daemon when it runs one (the host binder contexts of the `host-binder` test, two instances).
- `/dev/binderfs/<ctx>` stays the kernel's: on a Halium phone that is the device's own binder, which the device's HALs serve.
- `/dev/binderfs/features/<name>`: a memfd holding `1` for `oneway_spam_detection` and `extended_error`, `0` otherwise (libbinder's
  `ProcessState::isDriverFeatureEnabled`; `freeze_notification` is off, so libbinder never sends the freeze-notification commands).

Two entry points, because bionic code reaches `open` two ways on glibc: libc_bio's `bionic_open`/`openat` (patch 0047 asks the hook after its
path override) and, for the fortified `__open_2` that libc_bio leaves to glibc, the preload's own glibc-level `open`/`__open_2`/`openat`
wrappers (`paths_preload.c`). `access()` of a routed node succeeds. `ioctl`, `close` and LP64 `mmap`/`mmap64` exist only in libc_bio (patch
0047); its `bionic_ioctl` falls through to the global `ioctl`, so btload's own (`binder_compat.c`) still sees kernel binder ioctls.

## Protocol (`protocol.h`)

`SOCK_SEQPACKET` sockets: one `sendmsg` and one `recvmsg` per message (`bd_req`, `bd_rep`, at most 64 KiB), fds as `SCM_RIGHTS` (250 per
message, more in follow-up one-byte messages). Bulk data never goes through a socket: it goes through the thread's shared buffer (below).

- **Proc socket**: the fd `open()` returns *is* a Unix socket connected to the daemon. Its hello names the context and gets a cookie. After that the
  process never writes to it; the daemon writes one wake byte when a thread that polls (below) has work. Closing it, or the process dying, is the
  proc's death (`binder_deferred_release`). `SO_PEERCRED` gives pid and euid: `sender_pid` and `sender_euid` are kernel-attested, so the
  per-app sandbox keeps real uids (innkeeper's broker gives that up).
- **Thread sockets**: each thread connects its own socket on first use (hello: cookie and tid; the daemon checks that `SO_PEERCRED`'s pid is the
  proc's, so a leaked cookie is useless to another process). The hello carries the thread's **shared buffer**: an 8 MiB memfd (sparse: only
  written pages exist), sealed against shrinking (`F_SEAL_SHRINK`, checked by the daemon: a shrunk file would fault the daemon's reads), mapped
  read-write by the route and read-only by the daemon. A thread's ioctls go over it and block on it: a thread sleeping in
  `binder_thread_read` is a request the daemon answers later. A thread's exit closes it (a pthread key destructor) and BINDER_THREAD_EXIT does the
  same explicitly; either releases the thread (`binder_thread_release`).
- **ioctl**: a generic request carries `_IOC_SIZE` bytes in (for `_IOC_WRITE`) and returns them out (for `_IOC_READ`). BINDER_WRITE_READ is
  marshalled into the shared buffer (the request only says how long it is): the write stream as is, except BC_TRANSACTION(_SG)/BC_REPLY(_SG),
  which are followed by a `bd_txn` header, the data, the offsets and the scatter-gather buffers (BINDER_TYPE_PTR objects, in offset order) copied
  out of the process, and their fds (BINDER_TYPE_FD, then each BINDER_TYPE_FDA's) attached to the request. The daemon copies the data from the
  shared buffer straight into the target's receive area: two copies in all (the kernel makes one), none through a socket. A read is capped at
  32 KiB per call so that its reply is one message (libbinder reads 256 bytes). The daemon never reads process memory (no `/proc/<pid>/mem`, no ptrace, no ancestry). It answers with
  `write_consumed` in marshalled bytes (the route maps it back command by command), the read bytes, the fds of the delivered transaction and
  their fixups.
- **mmap**: the daemon creates the receive area (a memfd, at most 4 MiB as the kernel's) and maps it read-write; the route maps it read-only
  where libbinder asked (so libbinder sees what it sees on the kernel) and once more read-write for itself, and tells the daemon the read-only
  address: BR_TRANSACTION pointers are real addresses in the process, computed by the daemon as the kernel does. The area is sealed at its size
  (`F_SEAL_SHRINK|F_SEAL_GROW|F_SEAL_SEAL`) before the process gets it.
- **Memory a process can write is read once**: the sender's shared buffer and the target's (writable for the route) receive area can change
  under the daemon. Every value the daemon acts on (offsets, each object, each scatter-gather buffer's place, the parents of fd arrays and
  pointers) is copied into daemon memory once, checked and translated there, and the translated object written into the area; nothing is read
  back from the area.

## Driver model scope (`driver.h`)

Written from Linux's `drivers/android/binder.c` (v6.6), keeping its names and structure so the two can be compared function by function
(`binder_transaction`, `binder_thread_write`, `binder_thread_read`, `binder_proc_transaction`, `binder_inc/dec_node_nilocked`, refs,
`binder_send_failed_reply`, `binder_thread_release`, `binder_node_release`, `binder_deferred_release`). Innkeeper's driver (AGPL) was read
about, never copied (apklayer is also sold under a commercial license).

Kept: procs per context (binder, hwbinder, vndbinder: separate context managers in one daemon); nodes, refs with the lowest free descriptor
(0 is the context manager's), strong/weak counts and the BR_INCREFS/BR_ACQUIRE/BR_RELEASE/BR_DECREFS handshake with pending refs; transactions,
replies, transaction stacks and the "call back into a waiting thread" rule (a nested call goes to the thread that waits on us); deferred
BR_TRANSACTION_COMPLETE for two-way calls (the sender returns with completion and reply together); BR_DEAD_REPLY/BR_FAILED_REPLY paths;
death notifications (request, clear, dead binder done, the AND_CLEAR state); fd and fd-array objects; scatter-gather buffers with parent fixups;
BINDER_SET_CONTEXT_MGR(_EXT), BINDER_VERSION (8), BINDER_SET_MAX_THREADS, BINDER_THREAD_EXIT, BINDER_GET_NODE_DEBUG_INFO,
BINDER_GET_NODE_INFO_FOR_REF, BINDER_ENABLE_ONEWAY_SPAM_DETECTION, BINDER_GET_EXTENDED_ERROR, BINDER_FREEZE, BINDER_GET_FROZEN_INFO.

## Threading and BR_SPAWN_LOOPER

One thread per context runs that context's model (binder, hwbinder and vndbinder share no state: a Driver each); the main thread accepts
connections and hands each to its context by its hello (a thread's cookie carries the context in its top byte). Inside a context: no locks,
nothing freed under a caller (so no `tmp_refs`). After a request the daemon answers the threads it woke (a reply's waiting caller) before the
requester's own answer: the caller is the one on the critical path. Waiting is not a sleeping thread but a
pending reply: a thread whose read finds no work is marked waiting (and joins `waiting_threads` when it may take proc work, the kernel's
`binder_available_for_proc_work_ilocked`); queuing work for it puts it on a ready list, and after each request the daemon answers every ready
thread (`Driver::flush`). Proc work picks the first waiting thread (`binder_select_thread_ilocked`). A new thread's first read returns at once
(`looper_need_return`). BR_SPAWN_LOOPER follows the kernel: at the end of a read, when no thread was requested yet, none waits, fewer than
`max_threads` were started and the reader is a looper, the leading BR_NOOP becomes BR_SPAWN_LOOPER (`requested_threads`,
BC_REGISTER_LOOPER counts it started).

Polling (`IPCThreadState::setupPolling`, servicemanager's Looper): the kernel's `binder_poll` marks the polling thread. Here the thread that
sends BC_ENTER_LOOPER in a write without a read is taken as the poller (that is what setupPolling does), and the proc socket is made readable
(one wake byte at a time) when that thread would find work: its own todo, or proc work that no waiting thread takes. Every reply to the proc
tells the route how many wake bytes to read away (`ndrain`); any reply that drains bytes (a new thread's hello, an mmap, a plain ioctl too)
marks the proc, and the condition is checked again after the request, so a poller never sleeps with work queued (a lost wake byte was the
cause of occasional missing death notifications under load).

Still one thread for all traffic of one context (step 4, PLAN). BINDER_FREEZE now reaches only the procs of the caller's context.

## Oneway ordering

As the kernel: a node has at most one oneway transaction outstanding (`has_async_transaction`); later ones wait in the node's `async_todo` and
the next is queued when the process frees the previous one's buffer (BC_FREE_BUFFER of an async buffer). Oneway buffers may take half the
receive area at most (`free_async_space`); a full area answers BR_FAILED_REPLY. Oneway spam detection is accepted but never reports a suspect.

## Buffer allocation (`alloc.cpp`)

Best fit over the area's buffers (kept by offset, free and used), the remainder split off, neighbours merged on free; sizes are data, offsets
and extra buffers each rounded to 8 bytes. The area is shared memory from the start, so there is no page bookkeeping (the kernel's
`binder_alloc` installs pages lazily; a memfd does that by itself). A buffer keeps the references it took (the translated binder and handle
objects and the target node's local strong ref) in a list on the daemon side, released from there on BC_FREE_BUFFER, not re-read from the
process's memory.

## fd lifetime on failed transactions

Fds that arrive with a transaction are the daemon's until delivered: a transaction that fails at any point (dead target, bad object, no space,
frozen) closes them with the transaction, and fds the stream never reached are closed after the request. On delivery the fds go with the
reply (the receiver gets its own copies), the daemon closes its copies after sending, and the route writes the received numbers into the
buffer (the kernel's fd fixups). Fds of fd arrays are closed by the route on BC_FREE_BUFFER of that buffer, as the kernel does
(`binder_deferred_fd_close`); plain fd objects are the receiver's (libbinder closes them), as with the kernel. Innkeeper documents leaking fds
when a transaction fails after some were installed; here nothing is installed before delivery, so that case does not exist.

## Freezer

BINDER_FREEZE marks every proc of that pid frozen: two-way calls to it get BR_FROZEN_REPLY, oneway ones are queued (BR_TRANSACTION_PENDING_FROZEN
to the sender) and BINDER_GET_FROZEN_INFO reports `sync_recv`/`async_recv` and pending transactions. apklayer has no ActivityManager freezer
(no cgroup freezer in use), so this is kept for completeness.

## Left out, and why

- **Timeout wait of BINDER_FREEZE**: a proc with transactions in flight is refused at once (-EAGAIN), as the kernel's after its timeout runs out.
  Waiting would need a timer per request; nobody freezes here.
- **TF_UPDATE_TXN** (a newer oneway superseding an outdated one queued for a frozen proc): queued normally.
- **Security contexts**: FLAT_BINDER_FLAG_TXN_SECURITY_CTX is dropped, so BR_TRANSACTION_SEC_CTX is never sent; kernel binder on these hosts
  behaves the same way (btload refuses BINDER_SET_CONTEXT_MGR_EXT, `binder_compat.c`) since there is no SELinux context to report.
- **Priority inheritance and nice values** (`binder_set_nice`, min_priority): the daemon cannot set another process's thread priority.
- **Freeze notifications** (BC_REQUEST_FREEZE_NOTIFICATION, newer kernels): the feature file says 0, libbinder does not use them.
- **Transaction logs, debugfs, stats, `BINDER_GET_EXTENDED_ERROR` on the sender of a failed reply**: debugging aids only.
- **fork() without exec**: a child shares the parent's proc socket and receive area, as it shares the parent's binder fd with the kernel (where
  that is also unusable); exec closes them (they are close-on-exec when libbinder opens with O_CLOEXEC, as it does).
- **32-bit processes**: the protocol is the 64-bit binder ABI only (arm32 apps are closed, PLAN).
- **A process stopped in the middle of a message** cannot stall the daemon for more than 5 s (socket timeouts end that connection).

## Daemon lifetime

The CLI starts it for an instance (`ensureBinderDaemon`: the stack daemon before servicemanager, `apklayer exec` before its tool) by binding
`<id>.sock` itself under `<id>.lock` and handing the listening socket to a detached `apklayer-binderd` (socket activation: clients can connect
before the daemon runs). The daemon ends 3 s after its last connection closed, deciding under the same lock with nothing left to accept, so a
CLI that found it listening can count on it. `apklayer stop` ends it at once (`<id>.pid`).

## Numbers

See `docs/PLAN.md` (binder section) for the measured round trips and throughput against kernel binder.
