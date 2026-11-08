// Procs and threads, their ioctls and their release (binder.c: binder_ioctl and its helpers, binder_mmap, binder_thread_release,
// binder_deferred_release, binder_release_work).
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/random.h>
#include <unistd.h>

#include <algorithm>
#include <cerrno>
#include <cstdio>
#include <cstring>

#include "driver.h"
#include "protocol.h"

namespace binderd {

namespace {
template <typename T> bool argIn(const std::vector<uint8_t>& arg, T* out) {
    if (arg.size() < sizeof(T)) return false;
    memcpy(out, arg.data(), sizeof(T));
    return true;
}
template <typename T> void argOut(Reply& out, const T& v) { out.data.assign((const uint8_t*)&v, (const uint8_t*)&v + sizeof v); }
}  // namespace

Proc* Driver::newProc(int sock, pid_t pid, uid_t euid) {
    Proc* p = new Proc;
    p->context = &context;
    p->sock = sock;
    p->pid = pid;
    p->euid = euid;
    do {
        if (getrandom(&p->cookie, sizeof p->cookie, 0) != sizeof p->cookie) p->cookie = (uint64_t(pid) << 32) ^ uint64_t(sock) ^ uint64_t(next_debug_id++);
        p->cookie = (p->cookie & ~(uint64_t(0xff) << 56)) | (uint64_t(index) << 56);
    } while (procs.count(p->cookie));
    procs[p->cookie] = p;
    return p;
}

Proc* Driver::procByCookie(uint64_t cookie) {
    auto it = procs.find(cookie);
    return it == procs.end() ? nullptr : it->second;
}

Thread* Driver::newThread(Proc* p, int sock, int tid) {
    Thread* t = new Thread;
    t->proc = p;
    t->sock = sock;
    t->tid = tid;
    p->threads.push_back(t);
    return t;
}

int Driver::mmap(Thread* t, size_t size, int* memfd) {
    Proc* p = t->proc;
    if (p->area) return -EBUSY;
    size = std::min<size_t>(size, 4 * 1024 * 1024);  // the kernel's limit
    if (size < sizeof(void*)) return -EINVAL;
    int fd = memfd_create("binder", MFD_CLOEXEC | MFD_ALLOW_SEALING);
    if (fd < 0) return -errno;
    // sealed at its size: the process gets this fd, and a shrunk area would fault the daemon's writes (SIGBUS)
    bool sized = ftruncate(fd, size) == 0 && fcntl(fd, F_ADD_SEALS, F_SEAL_SHRINK | F_SEAL_GROW | F_SEAL_SEAL) == 0;
    void* area = sized ? ::mmap(nullptr, size, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0) : MAP_FAILED;
    if (area == MAP_FAILED) {
        int e = errno;
        close(fd);
        return -e;
    }
    p->area = static_cast<uint8_t*>(area);
    p->area_size = size;
    Buffer* b = new Buffer;
    b->size = size;
    p->buffers[0] = b;
    p->free_async_space = size / 2;
    *memfd = fd;
    return 0;
}

int Driver::mapped(Thread* t, uint64_t base) {
    if (!t->proc->area || t->proc->user_base) return -EINVAL;
    t->proc->user_base = base;
    return 0;
}

bool Driver::ioctl(Thread* t, uint32_t cmd, const std::vector<uint8_t>& arg, uint64_t a1, uint64_t a2, uint64_t a3, std::vector<int>& fds, Reply& out) {
    Proc* p = t->proc;
    auto pendingTxns = [](Proc* q) {
        if (q->outstanding_txns > 0) return true;
        for (Thread* th : q->threads)
            if (th->transaction_stack) return true;
        return false;
    };
    switch (cmd) {
        case BINDER_WRITE_READ: {
            t->pending = {std::min<uint64_t>(a1, BD_MAX_READ), 0, uint32_t(a2)};  // a reply is one message
            uint64_t consumed = 0;
            size_t fd_next = 0;
            int r = a3 == 0 ? 0 : !t->tx || a3 > BD_TX_SIZE ? -EINVAL : writeCommands(t, t->tx, a3, &consumed, fds, &fd_next);
            for (int fd : fds) if (fd >= 0) close(fd);  // fds of commands the stream did not reach
            fds.clear();
            out.write_consumed = consumed;
            if (r == 0 && a1 > 0) {
                t->pending.write_consumed = consumed;
                if (!readWork(t, out)) {
                    t->waiting = true;
                    if (availableForProcWork(t)) p->waiting_threads.push_back(t);
                    dirty.insert(p);
                    return false;
                }
                if (!p->todo.empty()) wakeupProc(p);
            }
            out.ret = r ? r : out.ret;
            t->looper_need_return = false;
            dirty.insert(p);
            return true;
        }
        case BINDER_SET_MAX_THREADS: {
            uint32_t n;
            if (!argIn(arg, &n)) out.ret = -EINVAL;
            else p->max_threads = n;
        } break;
        case BINDER_SET_CONTEXT_MGR_EXT: case BINDER_SET_CONTEXT_MGR: {
            flat_binder_object fbo{};
            Context* c = p->context;
            if (cmd == BINDER_SET_CONTEXT_MGR_EXT && !argIn(arg, &fbo)) out.ret = -EINVAL;
            else if (c->mgr) out.ret = -EBUSY;
            else if (c->mgr_uid_set && c->mgr_uid != p->euid) out.ret = -EPERM;
            else {
                c->mgr_uid_set = true;
                c->mgr_uid = p->euid;
                Node* n = newNode(p, fbo.binder, fbo.cookie, fbo.flags);
                n->local_weak_refs++;
                n->local_strong_refs++;
                n->has_strong_ref = n->has_weak_ref = true;
                c->mgr = n;
            }
        } break;
        case BINDER_VERSION:
            argOut(out, binder_version{BINDER_CURRENT_PROTOCOL_VERSION});
            break;
        case BINDER_GET_NODE_DEBUG_INFO: {
            binder_node_debug_info info{};
            if (!argIn(arg, &info)) { out.ret = -EINVAL; break; }
            binder_uintptr_t ptr = info.ptr;
            info = {};
            auto it = p->nodes.upper_bound(ptr);
            if (it != p->nodes.end()) info = {it->second->ptr, it->second->cookie, it->second->has_strong_ref, it->second->has_weak_ref};
            argOut(out, info);
        } break;
        case BINDER_GET_NODE_INFO_FOR_REF: {
            binder_node_info_for_ref info{};
            if (!argIn(arg, &info) || info.strong_count || info.weak_count) { out.ret = -EINVAL; break; }
            if (!p->context->mgr || p->context->mgr->proc != p) { out.ret = -EPERM; break; }
            Ref* ref = getRef(p, info.handle, true);
            if (!ref) { out.ret = -EINVAL; break; }
            info.strong_count = ref->node->local_strong_refs + ref->node->internal_strong_refs;
            info.weak_count = ref->node->local_weak_refs;
            argOut(out, info);
        } break;
        case BINDER_FREEZE: {
            // no timeout wait: a process with transactions in flight is refused at once (-EAGAIN), as the kernel's after its timeout. Only this
            // context's procs of that pid: each context has a thread of its own (the kernel freezes them in all binder devices)
            binder_freeze_info info{};
            if (!argIn(arg, &info)) { out.ret = -EINVAL; break; }
            bool found = false;
            for (auto& [k, q] : procs) {
                if (q->pid != pid_t(info.pid)) continue;
                found = true;
                q->sync_recv = q->async_recv = false;
                q->is_frozen = info.enable;
                if (info.enable && pendingTxns(q)) {
                    q->is_frozen = false;
                    out.ret = -EAGAIN;
                }
            }
            if (!found) out.ret = -EINVAL;
        } break;
        case BINDER_GET_FROZEN_INFO: {
            binder_frozen_status_info info{};
            if (!argIn(arg, &info)) { out.ret = -EINVAL; break; }
            bool found = false;
            info.sync_recv = info.async_recv = 0;
            for (auto& [k, q] : procs) {
                if (q->pid != pid_t(info.pid)) continue;
                found = true;
                info.sync_recv |= q->sync_recv | (pendingTxns(q) << 1);
                info.async_recv |= q->async_recv;
            }
            if (!found) out.ret = -EINVAL;
            else argOut(out, info);
        } break;
        case BINDER_ENABLE_ONEWAY_SPAM_DETECTION: {
            uint32_t on;
            if (!argIn(arg, &on)) out.ret = -EINVAL;
            else p->oneway_spam_detection = on;
        } break;
        case BINDER_GET_EXTENDED_ERROR:
            argOut(out, t->ee);
            t->ee = {0, BR_OK, 0};
            break;
        default:
            out.ret = -EINVAL;
    }
    t->looper_need_return = false;
    return true;
}

void Driver::releaseWork(Proc*, WorkList& list) {
    while (Work* w = list.pop()) {
        switch (w->type) {
            case Work::TRANSACTION: cleanupTransaction(static_cast<Transaction*>(w), BR_DEAD_REPLY); break;
            case Work::TRANSACTION_COMPLETE: case Work::TRANSACTION_PENDING: case Work::DEAD_BINDER_AND_CLEAR: case Work::CLEAR_DEATH: delete w; break;
            default: break;  // errors are the thread's, nodes the proc's, a plain death its ref's
        }
    }
}

void Driver::releaseThread(Thread* thread) {
    Proc* proc = thread->proc;
    Transaction* send_reply = nullptr;
    Transaction* t = thread->transaction_stack;
    if (t && t->to_thread == thread) send_reply = t;
    thread->is_dead = true;
    while (t) {
        if (t->to_thread == thread) {
            proc->outstanding_txns--;
            t->to_proc = nullptr;
            t->to_thread = nullptr;
            if (t->buffer) {
                t->buffer->transaction = nullptr;
                t->buffer = nullptr;
            }
            t = t->to_parent;
        } else if (t->from == thread) {
            t->from = nullptr;
            t = t->from_parent;
        } else {
            break;  // a broken stack (the kernel's BUG): stop walking
        }
    }
    if (send_reply) sendFailedReply(send_reply, BR_DEAD_REPLY);
    releaseWork(proc, thread->todo);
    dequeue(&thread->return_error);
    dequeue(&thread->reply_error);
    proc->threads.remove(thread);
    proc->waiting_threads.remove(thread);
    ready.erase(thread);
    closeSocket(thread->sock);
    if (thread->tx) munmap(const_cast<uint8_t*>(thread->tx), BD_TX_SIZE);
    delete thread;
}

void Driver::releaseProc(Proc* p) {
    if (debug) fprintf(stderr, "binderd: %s: proc %d ends (%zu nodes, %zu refs)\n", context.name.c_str(), p->pid, p->nodes.size(), p->refs_by_desc.size());
    p->is_dead = true;
    if (p->context->mgr && p->context->mgr->proc == p) p->context->mgr = nullptr;
    while (!p->threads.empty()) releaseThread(p->threads.front());
    while (!p->nodes.empty()) nodeRelease(p->nodes.begin()->second);
    while (!p->refs_by_desc.empty()) cleanupRef(p->refs_by_desc.begin()->second);
    releaseWork(p, p->todo);
    releaseWork(p, p->delivered_death);
    for (auto& [off, b] : p->buffers) {
        if (b->transaction) b->transaction->buffer = nullptr;
        delete b;
    }
    if (p->area) munmap(p->area, p->area_size);
    procs.erase(p->cookie);
    dirty.erase(p);
    closeSocket(p->sock);
    delete p;
}

}  // namespace binderd
