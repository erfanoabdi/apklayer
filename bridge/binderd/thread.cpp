// Threads (binder.c: binder_thread_write, binder_thread_read, the wakeup helpers). A thread "sleeps" by not getting its reply yet: its
// BINDER_WRITE_READ request stays pending (Thread::waiting) until work is queued for it, then flush() answers it.
#include <sys/socket.h>
#include <unistd.h>

#include <cerrno>
#include <cstring>

#include "driver.h"
#include "protocol.h"

namespace binderd {

void WorkList::push(Work* w) {
    w->list = this;
    w->prev = tail;
    w->next = nullptr;
    if (tail) tail->next = w;
    else head = w;
    tail = w;
}

Work* WorkList::pop() {
    Work* w = head;
    if (w) dequeue(w);
    return w;
}

void dequeue(Work* w) {
    WorkList* l = w->list;
    if (!l) return;
    if (w->prev) w->prev->next = w->next;
    else l->head = w->next;
    if (w->next) w->next->prev = w->prev;
    else l->tail = w->prev;
    w->list = nullptr;
    w->prev = w->next = nullptr;
}

void Driver::enqueueThreadWork(Thread* t, Work* w) {
    t->todo.push(w);
    t->process_todo = true;
}

void Driver::enqueueDeferredThreadWork(Thread* t, Work* w) { t->todo.push(w); }

bool Driver::availableForProcWork(Thread* t) {
    return !t->transaction_stack && t->todo.empty() && (t->looper & (LOOPER_ENTERED | LOOPER_REGISTERED));
}

bool Driver::hasWork(Thread* t, bool proc_work) {
    return t->process_todo || t->looper_need_return || (proc_work && !t->proc->todo.empty());
}

Thread* Driver::selectThread(Proc* p) {
    if (p->waiting_threads.empty()) return nullptr;
    Thread* t = p->waiting_threads.front();
    p->waiting_threads.pop_front();
    return t;
}

void Driver::wakeThread(Thread* t) {
    if (t->waiting) ready.insert(t);
    else dirty.insert(t->proc);  // a thread that polls the fd (Looper): woken through the proc socket
}

void Driver::wakeupProc(Proc* p) {
    if (Thread* t = selectThread(p)) wakeThread(t);
    else dirty.insert(p);
}

// binder_poll's view: the proc socket is readable (one wake byte at a time) while a polling thread would find work
void Driver::wakePoll(Proc* p) {
    if (p->is_dead || p->wakes) return;
    bool need = false;
    for (Thread* t : p->threads) {
        if (!(t->looper & LOOPER_POLL) || t->waiting) continue;
        if (t->process_todo || (p->waiting_threads.empty() && !p->todo.empty() && availableForProcWork(t))) need = true;
    }
    if (!need) return;
    char byte = 0;
    if (send(p->sock, &byte, 1, MSG_DONTWAIT | MSG_NOSIGNAL) == 1) p->wakes++;
}

void Driver::flush() {
    while (!ready.empty()) {
        Thread* t = *ready.begin();
        ready.erase(ready.begin());
        if (!t->waiting) continue;
        Proc* p = t->proc;
        p->waiting_threads.remove(t);
        Reply r;
        r.write_consumed = t->pending.write_consumed;
        if (!readWork(t, r)) {
            if (availableForProcWork(t)) p->waiting_threads.push_back(t);
            continue;
        }
        t->waiting = false;
        t->looper_need_return = false;
        if (!p->todo.empty()) wakeupProc(p);
        dirty.insert(p);
        sendReply(t, r);
    }
    for (Proc* p : dirty) wakePoll(p);
    dirty.clear();
}

namespace {
template <typename T> bool take(const uint8_t* buf, size_t size, size_t* pos, T* out) {
    if (size - *pos < sizeof(T)) return false;
    memcpy(out, buf + *pos, sizeof(T));
    *pos += sizeof(T);
    return true;
}
}  // namespace

int Driver::writeCommands(Thread* t, const uint8_t* buf, size_t size, uint64_t* consumed, std::vector<int>& fds, size_t* fd_next) {
    Proc* proc = t->proc;
    size_t pos = *consumed;
    while (pos < size && t->return_error.cmd == BR_OK) {
        uint32_t cmd;
        if (!take(buf, size, &pos, &cmd)) return -EFAULT;
        switch (cmd) {
            case BC_INCREFS: case BC_ACQUIRE: case BC_RELEASE: case BC_DECREFS: {
                uint32_t target;
                if (!take(buf, size, &pos, &target)) return -EFAULT;
                bool strong = cmd == BC_ACQUIRE || cmd == BC_RELEASE, increment = cmd == BC_INCREFS || cmd == BC_ACQUIRE;
                int ret = -1;
                if (increment && !target && proc->context->mgr) {
                    if (proc->context->mgr->proc == proc) return -EINVAL;
                    ret = incRefForNode(proc, proc->context->mgr, strong, nullptr, nullptr);
                }
                if (ret) updateRefForHandle(proc, target, increment, strong);
            } break;
            case BC_INCREFS_DONE: case BC_ACQUIRE_DONE: {
                binder_ptr_cookie pc;
                if (!take(buf, size, &pos, &pc)) return -EFAULT;
                Node* node = getNode(proc, pc.ptr);
                if (!node || node->cookie != pc.cookie) break;
                bool& pending = cmd == BC_ACQUIRE_DONE ? node->pending_strong_ref : node->pending_weak_ref;
                if (!pending) break;
                pending = false;
                decNode(node, cmd == BC_ACQUIRE_DONE, false);
            } break;
            case BC_FREE_BUFFER: {
                binder_uintptr_t ptr;
                if (!take(buf, size, &pos, &ptr)) return -EFAULT;
                if (Buffer* b = bufferForUser(proc, ptr)) freeBuf(proc, b, false);
            } break;
            case BC_TRANSACTION: case BC_REPLY: case BC_TRANSACTION_SG: case BC_REPLY_SG: {
                binder_transaction_data_sg trs{};
                bool sg = cmd == BC_TRANSACTION_SG || cmd == BC_REPLY_SG;
                if (sg ? !take(buf, size, &pos, &trs) : !take(buf, size, &pos, &trs.transaction_data)) return -EFAULT;
                bd_txn x;
                if (!take(buf, size, &pos, &x)) return -EFAULT;
                const binder_transaction_data& tr = trs.transaction_data;
                size_t need = align8(tr.data_size) + align8(tr.offsets_size);
                if (need < tr.data_size || size - pos < need || size - pos - need < x.sg_size || *fd_next + x.nfds > fds.size()) return -EFAULT;
                const uint8_t* data = buf + pos;
                std::vector<int> mine(fds.begin() + *fd_next, fds.begin() + *fd_next + x.nfds);
                for (uint32_t i = 0; i < x.nfds; i++) fds[*fd_next + i] = -1;
                *fd_next += x.nfds;
                pos += need + x.sg_size;
                transaction(t, tr, cmd == BC_REPLY || cmd == BC_REPLY_SG, sg ? trs.buffers_size : 0, data, data + align8(tr.data_size),
                            data + need, x.sg_size, std::move(mine));
            } break;
            case BC_REGISTER_LOOPER:
                if (t->looper & LOOPER_ENTERED) {
                    t->looper |= LOOPER_INVALID;
                } else if (proc->requested_threads == 0) {
                    t->looper |= LOOPER_INVALID;
                } else {
                    proc->requested_threads--;
                    proc->requested_threads_started++;
                }
                t->looper |= LOOPER_REGISTERED;
                break;
            case BC_ENTER_LOOPER:
                if (t->looper & LOOPER_REGISTERED) t->looper |= LOOPER_INVALID;
                t->looper |= LOOPER_ENTERED;
                // libbinder's setupPolling enters the looper without reading, then polls the fd (servicemanager's Looper): binder_poll's thread
                if (t->pending.size == 0) t->looper |= LOOPER_POLL;
                break;
            case BC_EXIT_LOOPER:
                t->looper |= LOOPER_EXITED;
                break;
            case BC_REQUEST_DEATH_NOTIFICATION: case BC_CLEAR_DEATH_NOTIFICATION: {
                binder_handle_cookie hc;
                if (!take(buf, size, &pos, &hc)) return -EFAULT;
                deathCommand(t, cmd, hc.handle, hc.cookie);
            } break;
            case BC_DEAD_BINDER_DONE: {
                binder_uintptr_t cookie;
                if (!take(buf, size, &pos, &cookie)) return -EFAULT;
                deadBinderDone(t, cookie);
            } break;
            default:
                return -EINVAL;  // unknown (BC_ATTEMPT_ACQUIRE, BC_ACQUIRE_RESULT, freeze notifications): -EINVAL, the stream stops
        }
        *consumed = pos;
    }
    return 0;
}

bool Driver::readWork(Thread* t, Reply& out) {
    Proc* proc = t->proc;
    bool first = t->pending.flags & BD_RD_FIRST;
    size_t size = t->pending.size;
    bool wait_proc = availableForProcWork(t);
    if (!hasWork(t, wait_proc)) {
        if (!(t->pending.flags & BD_RD_NONBLOCK)) return false;
        out.ret = -EAGAIN;
        return true;
    }
    std::vector<uint8_t>& o = out.data;
    o.clear();
    auto put = [&o](const void* p, size_t n) { o.insert(o.end(), (const uint8_t*)p, (const uint8_t*)p + n); };
    auto put32 = [&put](uint32_t v) { put(&v, sizeof v); };
    if (first) put32(BR_NOOP);
    for (bool done = false; !done;) {
        WorkList* list;
        if (!t->todo.empty()) list = &t->todo;
        else if (!proc->todo.empty() && wait_proc) list = &proc->todo;
        else {
            if (o.size() == (first ? 4u : 0u) && !t->looper_need_return) return false;  // nothing added: keep waiting
            break;
        }
        if (size - o.size() < sizeof(binder_transaction_data_secctx) + 4) break;
        Work* w = list->pop();
        if (t->todo.empty()) t->process_todo = false;
        switch (w->type) {
            case Work::TRANSACTION: {
                auto* tx = static_cast<Transaction*>(w);
                Buffer* b = tx->buffer;
                binder_transaction_data trd{};
                uint32_t cmd = BR_REPLY;
                if (b->target_node) {
                    trd.target.ptr = b->target_node->ptr;
                    trd.cookie = b->target_node->cookie;
                    cmd = BR_TRANSACTION;
                }
                trd.code = tx->code;
                trd.flags = tx->flags;
                trd.sender_euid = tx->sender_euid;
                trd.sender_pid = tx->from ? tx->from->proc->pid : 0;
                // binder_apply_fd_fixups: the fds go with this reply, the route writes their numbers into the buffer
                for (const FdFixup& f : tx->fds) {
                    out.fixes.push_back({f.offset, uint32_t(out.fds.size()), f.fda, proc->user_base + b->off});
                    out.fds.push_back(f.fd);
                }
                tx->fds.clear();
                trd.data_size = b->data_size;
                trd.offsets_size = b->offsets_size;
                trd.data.ptr.buffer = proc->user_base + b->off;
                trd.data.ptr.offsets = trd.data.ptr.buffer + align8(b->data_size);
                put32(cmd);
                put(&trd, sizeof trd);
                b->allow_user_free = true;
                if (cmd == BR_TRANSACTION && !(tx->flags & TF_ONE_WAY)) {
                    tx->to_parent = t->transaction_stack;
                    tx->to_thread = t;
                    t->transaction_stack = tx;
                } else {
                    freeTransaction(tx);
                }
                done = true;
            } break;
            case Work::RETURN_ERROR: {
                auto* e = static_cast<Error*>(w);
                put32(e->cmd);
                e->cmd = BR_OK;
            } break;
            case Work::TRANSACTION_COMPLETE: case Work::TRANSACTION_PENDING:
                put32(w->type == Work::TRANSACTION_PENDING ? BR_TRANSACTION_PENDING_FROZEN : BR_TRANSACTION_COMPLETE);
                delete w;
                break;
            case Work::NODE: {
                auto* node = static_cast<Node*>(w);
                binder_ptr_cookie pc{node->ptr, node->cookie};
                bool strong = node->internal_strong_refs || node->local_strong_refs;
                bool weak = !node->refs.empty() || node->local_weak_refs || strong;
                bool has_strong = node->has_strong_ref, has_weak = node->has_weak_ref;
                if (weak && !has_weak) { node->has_weak_ref = node->pending_weak_ref = true; node->local_weak_refs++; }
                if (strong && !has_strong) { node->has_strong_ref = node->pending_strong_ref = true; node->local_strong_refs++; }
                if (!strong && has_strong) node->has_strong_ref = false;
                if (!weak && has_weak) node->has_weak_ref = false;
                if (!weak && !strong) {
                    proc->nodes.erase(node->ptr);
                    delete node;
                }
                auto cmdOut = [&](uint32_t c) { put32(c); put(&pc, sizeof pc); };
                if (weak && !has_weak) cmdOut(BR_INCREFS);
                if (strong && !has_strong) cmdOut(BR_ACQUIRE);
                if (!strong && has_strong) cmdOut(BR_RELEASE);
                if (!weak && has_weak) cmdOut(BR_DECREFS);
            } break;
            case Work::DEAD_BINDER: case Work::DEAD_BINDER_AND_CLEAR: case Work::CLEAR_DEATH: {
                auto* death = static_cast<Death*>(w);
                uint32_t cmd = w->type == Work::CLEAR_DEATH ? BR_CLEAR_DEATH_NOTIFICATION_DONE : BR_DEAD_BINDER;
                binder_uintptr_t cookie = death->cookie;
                if (w->type == Work::CLEAR_DEATH) delete death;
                else proc->delivered_death.push(death);
                put32(cmd);
                put(&cookie, sizeof cookie);
                if (cmd == BR_DEAD_BINDER) done = true;  // DEAD_BINDER notifications can cause transactions
            } break;
        }
    }
    if (first && proc->requested_threads == 0 && proc->waiting_threads.empty() && uint32_t(proc->requested_threads_started) < proc->max_threads &&
        (t->looper & (LOOPER_REGISTERED | LOOPER_ENTERED))) {
        proc->requested_threads++;
        uint32_t spawn = BR_SPAWN_LOOPER;
        memcpy(o.data(), &spawn, sizeof spawn);  // over the leading BR_NOOP, as the kernel does
    }
    return true;
}

}  // namespace binderd
