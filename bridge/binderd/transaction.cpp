// Transactions and replies (binder.c: binder_transaction, binder_proc_transaction, binder_send_failed_reply, binder_cleanup_transaction,
// binder_free_buf, binder_transaction_buffer_release). The sender's data, offsets and scatter-gather buffers arrive in the request (the
// route copied them out of its process: no /proc/<pid>/mem); its fds arrive as SCM_RIGHTS and stay the daemon's until delivered.
#include <unistd.h>

#include <cerrno>
#include <cstddef>
#include <cstring>

#include "driver.h"

namespace binderd {

Transaction::~Transaction() {
    for (const FdFixup& f : fds) close(f.fd);
}

void Driver::popTransaction(Thread* target, Transaction* t) {
    if (target->transaction_stack == t) target->transaction_stack = t->from_parent;
    t->from = nullptr;
}

void Driver::freeTransaction(Transaction* t) {
    if (t->to_proc) t->to_proc->outstanding_txns--;
    if (t->buffer) t->buffer->transaction = nullptr;
    dequeue(t);
    delete t;
}

void Driver::sendFailedReply(Transaction* t, uint32_t error) {
    for (;;) {
        if (Thread* target = t->from) {
            popTransaction(target, t);
            if (target->reply_error.cmd == BR_OK) {
                target->reply_error.cmd = error;
                enqueueThreadWork(target, &target->reply_error);
                wakeThread(target);
            }
            freeTransaction(t);
            return;
        }
        Transaction* next = t->from_parent;
        freeTransaction(t);
        if (!next) return;
        t = next;
    }
}

void Driver::cleanupTransaction(Transaction* t, uint32_t error) {
    bool two_way = !(t->flags & TF_ONE_WAY) && t->need_reply;
    if (two_way) sendFailedReply(t, error);
    else freeTransaction(t);
}

void Driver::releaseBufferObjects(Proc* proc, Buffer* b, bool) {
    for (const BufferObject& o : b->objects) {
        if (o.type == BINDER_TYPE_BINDER || o.type == BINDER_TYPE_WEAK_BINDER) decNode(o.node, o.type == BINDER_TYPE_BINDER, false);
        else updateRefForHandle(proc, o.desc, false, o.type == BINDER_TYPE_HANDLE);
    }
    b->objects.clear();
    if (Node* n = b->target_node) {
        b->target_node = nullptr;
        decNode(n, true, false);
    }
}

void Driver::freeBuf(Proc* proc, Buffer* b, bool failure) {
    if (b->transaction) {
        b->transaction->buffer = nullptr;
        b->transaction = nullptr;
    }
    if (b->async && b->target_node) {  // the node's next oneway transaction may go now (oneway ordering)
        Node* n = b->target_node;
        if (Work* w = n->async_todo.pop()) {
            proc->todo.push(w);
            wakeupProc(proc);
        } else {
            n->has_async_transaction = false;
        }
    }
    releaseBufferObjects(proc, b, failure);
    freeAlloc(proc, b);
}

int Driver::procTransaction(Transaction* t, Proc* proc, Thread* thread) {
    Node* node = t->buffer->target_node;
    bool oneway = t->flags & TF_ONE_WAY;
    bool pending_async = false, frozen = false;
    if (oneway) {
        if (node->has_async_transaction) pending_async = true;
        else node->has_async_transaction = true;
    }
    if (proc->is_frozen) {
        frozen = true;
        proc->sync_recv |= !oneway;
        proc->async_recv |= oneway;
    }
    if ((frozen && !oneway) || proc->is_dead || (thread && thread->is_dead)) {
        if (oneway && !pending_async) node->has_async_transaction = false;
        return frozen ? BR_FROZEN_REPLY : BR_DEAD_REPLY;
    }
    if (!thread && !pending_async) thread = selectThread(proc);
    if (thread) {
        enqueueThreadWork(thread, t);
        wakeThread(thread);
    } else if (!pending_async) {
        proc->todo.push(t);
        wakeupProc(proc);
    } else {
        node->async_todo.push(t);  // TF_UPDATE_TXN (superseding an outdated frozen oneway) is not done: docs/BINDER.md
    }
    proc->outstanding_txns++;
    return oneway && frozen ? int(BR_TRANSACTION_PENDING_FROZEN) : 0;
}

namespace {
size_t objectSize(uint32_t type) {
    switch (type) {
        case BINDER_TYPE_BINDER: case BINDER_TYPE_WEAK_BINDER: case BINDER_TYPE_HANDLE: case BINDER_TYPE_WEAK_HANDLE: return sizeof(flat_binder_object);
        case BINDER_TYPE_FD: return sizeof(binder_fd_object);
        case BINDER_TYPE_FDA: return sizeof(binder_fd_array_object);
        case BINDER_TYPE_PTR: return sizeof(binder_buffer_object);
        default: return 0;
    }
}
}  // namespace

void Driver::transaction(Thread* thread, const binder_transaction_data& tr, bool reply, uint64_t extra_size, const uint8_t* data,
                         const uint8_t* offsets, const uint8_t* sg, uint64_t sg_size, std::vector<int> fds) {
    Proc* proc = thread->proc;
    bool oneway = tr.flags & TF_ONE_WAY;
    Transaction* in_reply_to = nullptr;
    Proc* target_proc = nullptr;
    Thread* target_thread = nullptr;
    Node* target_node = nullptr;
    bool node_ref = false;  // the local strong ref on target_node is still ours (not yet the buffer's)
    int debug_id = next_debug_id++;
    thread->ee = {uint32_t(debug_id), BR_OK, 0};

    auto fail = [&](uint32_t error, int32_t param) {
        for (int fd : fds) if (fd >= 0) close(fd);
        if (node_ref) decNode(target_node, true, false);
        if (in_reply_to) {
            thread->return_error.cmd = BR_TRANSACTION_COMPLETE;
            enqueueThreadWork(thread, &thread->return_error);
            sendFailedReply(in_reply_to, error);
        } else {
            thread->ee = {uint32_t(debug_id), error, param};
            thread->return_error.cmd = error;
            enqueueThreadWork(thread, &thread->return_error);
        }
    };

    if (reply) {
        in_reply_to = thread->transaction_stack;
        if (!in_reply_to) return fail(BR_FAILED_REPLY, -EPROTO);
        if (in_reply_to->to_thread != thread) { in_reply_to = nullptr; return fail(BR_FAILED_REPLY, -EPROTO); }
        thread->transaction_stack = in_reply_to->to_parent;
        target_thread = in_reply_to->from;
        if (!target_thread) return fail(BR_DEAD_REPLY, 0);
        if (target_thread->transaction_stack != in_reply_to) { in_reply_to = nullptr; return fail(BR_FAILED_REPLY, -EPROTO); }
        target_proc = target_thread->proc;
    } else {
        uint32_t error = 0;
        Node* n = nullptr;
        if (tr.target.handle) {
            Ref* ref = getRef(proc, tr.target.handle, true);
            if (ref) n = ref->node;
            else error = BR_FAILED_REPLY;
        } else {
            n = proc->context->mgr;
            if (!n) error = BR_DEAD_REPLY;
        }
        if (n && !n->proc) error = BR_DEAD_REPLY;
        if (error) return fail(error, -EINVAL);
        target_node = n;
        target_proc = n->proc;
        incNode(target_node, true, false, nullptr);
        node_ref = true;
        if (target_proc == proc) return fail(BR_FAILED_REPLY, -EINVAL);  // a self transaction, or to the context manager from its owner
        if (!oneway && thread->todo.head && thread->todo.head->type == Work::TRANSACTION) return fail(BR_FAILED_REPLY, -EPROTO);
        if (!oneway && thread->transaction_stack) {
            Transaction* tmp = thread->transaction_stack;
            if (tmp->to_thread != thread) return fail(BR_FAILED_REPLY, -EPROTO);
            // a call back into a process that waits on this thread goes to its waiting thread (no deadlock, no extra thread)
            for (; tmp; tmp = tmp->from_parent)
                if (tmp->from && tmp->from->proc == target_proc) { target_thread = tmp->from; break; }
        }
    }
    if (tr.offsets_size % sizeof(binder_size_t) || extra_size % sizeof(uint64_t)) return fail(BR_FAILED_REPLY, -EINVAL);

    int err = 0;
    Buffer* buf = allocBuf(target_proc, tr.data_size, tr.offsets_size, extra_size, !reply && oneway, &err);
    if (!buf) return fail(err == -ESRCH ? BR_DEAD_REPLY : BR_FAILED_REPLY, err);
    Transaction* t = new Transaction;
    t->debug_id = debug_id;
    t->from = !reply && !oneway ? thread : nullptr;
    t->from_pid = proc->pid;
    t->sender_euid = proc->euid;
    t->to_proc = target_proc;
    t->to_thread = target_thread;
    t->code = tr.code;
    t->flags = tr.flags;
    t->buffer = buf;
    buf->transaction = t;
    buf->target_node = target_node;
    node_ref = false;

    uint8_t* base = target_proc->area + buf->off;
    const uint64_t user = target_proc->user_base + buf->off;
    memcpy(base, data, tr.data_size);
    size_t off_start = align8(tr.data_size);
    memcpy(base + off_start, offsets, tr.offsets_size);
    size_t sg_off = off_start + align8(tr.offsets_size), sg_end = sg_off + extra_size, sg_in = 0, fd_next = 0, off_min = 0;
    bool accept_fds = reply ? (in_reply_to->flags & TF_ACCEPT_FDS) : target_node->accept_fds;
    std::vector<size_t> obj_at;  // offset of the object at each index (parents of PTR and FDA objects)
    auto parentPtr = [&](uint64_t index, size_t below) -> binder_buffer_object* {
        if (index >= below) return nullptr;
        auto* p = reinterpret_cast<binder_buffer_object*>(base + obj_at[index]);
        return p->hdr.type == BINDER_TYPE_PTR ? p : nullptr;
    };
    int32_t bad = 0;
    for (size_t i = 0; !bad && i < tr.offsets_size / sizeof(binder_size_t); i++) {
        binder_size_t at;
        memcpy(&at, base + off_start + i * sizeof at, sizeof at);
        obj_at.push_back(at);
        if (at % sizeof(uint32_t) || at < off_min || at + sizeof(binder_object_header) > tr.data_size) { bad = -EINVAL; break; }
        auto* hdr = reinterpret_cast<binder_object_header*>(base + at);
        size_t size = objectSize(hdr->type);
        if (!size || at + size > tr.data_size) { bad = -EINVAL; break; }
        off_min = at + size;
        switch (hdr->type) {
            case BINDER_TYPE_BINDER: case BINDER_TYPE_WEAK_BINDER: {
                auto* fp = reinterpret_cast<flat_binder_object*>(hdr);
                bool strong = hdr->type == BINDER_TYPE_BINDER;
                Node* node = newNode(proc, fp->binder, fp->cookie, fp->flags);
                uint32_t desc;
                if (fp->cookie != node->cookie) { bad = -EINVAL; break; }
                if (int r = incRefForNode(target_proc, node, strong, thread, &desc)) { bad = r; break; }
                fp->hdr.type = strong ? BINDER_TYPE_HANDLE : BINDER_TYPE_WEAK_HANDLE;
                fp->binder = 0;
                fp->handle = desc;
                fp->cookie = 0;
                buf->objects.push_back({fp->hdr.type, nullptr, desc});
            } break;
            case BINDER_TYPE_HANDLE: case BINDER_TYPE_WEAK_HANDLE: {
                auto* fp = reinterpret_cast<flat_binder_object*>(hdr);
                bool strong = hdr->type == BINDER_TYPE_HANDLE;
                Ref* ref = getRef(proc, fp->handle, strong);
                if (!ref) { bad = -EINVAL; break; }
                Node* node = ref->node;
                if (node->proc == target_proc) {
                    fp->hdr.type = strong ? BINDER_TYPE_BINDER : BINDER_TYPE_WEAK_BINDER;
                    fp->binder = node->ptr;
                    fp->cookie = node->cookie;
                    incNode(node, strong, false, nullptr);
                    buf->objects.push_back({fp->hdr.type, node, 0});
                } else {
                    uint32_t desc;
                    if (int r = incRefForNode(target_proc, node, strong, nullptr, &desc)) { bad = r; break; }
                    fp->binder = 0;
                    fp->handle = desc;
                    fp->cookie = 0;
                    buf->objects.push_back({fp->hdr.type, nullptr, desc});
                }
            } break;
            case BINDER_TYPE_FD: {
                auto* fp = reinterpret_cast<binder_fd_object*>(hdr);
                if (!accept_fds) { bad = -EPERM; break; }
                if (fd_next >= fds.size()) { bad = -EINVAL; break; }
                t->fds.push_back({fds[fd_next], buf->off + at + offsetof(binder_fd_object, fd), false});
                fds[fd_next++] = -1;
                fp->pad_binder = 0;
            } break;
            case BINDER_TYPE_FDA: {
                auto* fda = reinterpret_cast<binder_fd_array_object*>(hdr);
                binder_buffer_object* parent = parentPtr(fda->parent, i);
                if (!accept_fds) { bad = -EPERM; break; }
                if (!parent || fda->parent_offset + fda->num_fds * sizeof(uint32_t) > parent->length || fda->parent_offset % sizeof(uint32_t) ||
                    fd_next + fda->num_fds > fds.size()) { bad = -EINVAL; break; }
                size_t first = parent->buffer - target_proc->user_base + fda->parent_offset;
                for (binder_size_t k = 0; k < fda->num_fds; k++) {
                    t->fds.push_back({fds[fd_next], first + k * sizeof(uint32_t), true});
                    fds[fd_next++] = -1;
                }
            } break;
            case BINDER_TYPE_PTR: {
                auto* bp = reinterpret_cast<binder_buffer_object*>(hdr);
                if (bp->length > sg_end - sg_off || sg_in + align8(bp->length) > sg_size) { bad = -EINVAL; break; }
                memcpy(base + sg_off, sg + sg_in, bp->length);
                sg_in += align8(bp->length);
                bp->buffer = user + sg_off;
                sg_off += align8(bp->length);
                if (bp->flags & BINDER_BUFFER_FLAG_HAS_PARENT) {  // binder_fixup_parent: the parent's copy points at this one's
                    binder_buffer_object* parent = parentPtr(bp->parent, i);
                    if (!parent || bp->parent_offset + sizeof(binder_uintptr_t) > parent->length) { bad = -EINVAL; break; }
                    binder_uintptr_t value = bp->buffer;
                    memcpy(base + (parent->buffer - user) + bp->parent_offset, &value, sizeof value);
                }
            } break;
        }
    }
    Work* tcomplete = nullptr;
    auto failBuffer = [&](uint32_t error, int32_t param) {
        if (tcomplete) { dequeue(tcomplete); delete tcomplete; }
        releaseBufferObjects(target_proc, buf, true);
        buf->transaction = nullptr;
        freeAlloc(target_proc, buf);
        t->buffer = nullptr;
        t->to_proc = nullptr;
        delete t;  // closes the fds it took
        fail(error, param);
    };
    if (bad) return failBuffer(BR_FAILED_REPLY, bad);

    tcomplete = new Work(Work::TRANSACTION_COMPLETE);
    if (reply) {
        enqueueThreadWork(thread, tcomplete);
        if (target_thread->is_dead) return failBuffer(BR_DEAD_REPLY, 0);
        popTransaction(target_thread, in_reply_to);
        enqueueThreadWork(target_thread, t);
        target_proc->outstanding_txns++;
        wakeThread(target_thread);
        freeTransaction(in_reply_to);
    } else if (!oneway) {
        // the TRANSACTION_COMPLETE waits for the reply: the sender's read returns with both (binder.c: defer it, less latency)
        enqueueDeferredThreadWork(thread, tcomplete);
        t->need_reply = true;
        t->from_parent = thread->transaction_stack;
        thread->transaction_stack = t;
        if (int r = procTransaction(t, target_proc, target_thread)) {
            popTransaction(thread, t);
            return failBuffer(r, 0);
        }
    } else {
        int r = procTransaction(t, target_proc, nullptr);
        if (r == BR_TRANSACTION_PENDING_FROZEN) tcomplete->type = Work::TRANSACTION_PENDING;
        enqueueThreadWork(thread, tcomplete);
        if (r && r != BR_TRANSACTION_PENDING_FROZEN) return failBuffer(r, 0);
    }
    for (int fd : fds) if (fd >= 0) close(fd);  // more fds than the objects named
}

}  // namespace binderd
