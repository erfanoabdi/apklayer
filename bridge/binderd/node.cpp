// Nodes, refs and death notifications (binder.c: binder_new_node, binder_inc/dec_node_nilocked, binder_get_ref_for_node_olocked,
// binder_inc/dec_ref_olocked, binder_cleanup_ref_olocked, binder_node_release, BC_(CLEAR|REQUEST)_DEATH_NOTIFICATION, BC_DEAD_BINDER_DONE).
// No tmp_refs: one thread runs the model, nothing is freed under a caller's feet.
#include <cerrno>

#include "driver.h"

namespace binderd {

Node* Driver::getNode(Proc* p, binder_uintptr_t ptr) {
    auto it = p->nodes.find(ptr);
    return it == p->nodes.end() ? nullptr : it->second;
}

Node* Driver::newNode(Proc* p, binder_uintptr_t ptr, binder_uintptr_t cookie, uint32_t flags) {
    if (Node* n = getNode(p, ptr)) return n;
    Node* n = new Node;
    n->debug_id = next_debug_id++;
    n->proc = p;
    n->ptr = ptr;
    n->cookie = cookie;
    n->accept_fds = flags & FLAT_BINDER_FLAG_ACCEPTS_FDS;
    // FLAT_BINDER_FLAG_TXN_SECURITY_CTX is not kept: a desktop has no SELinux context to send (docs/BINDER.md), as with kernel binder here
    // where the context manager's BINDER_SET_CONTEXT_MGR_EXT is refused (bridge/native/binder_compat.c)
    p->nodes[ptr] = n;
    return n;
}

int Driver::incNode(Node* node, bool strong, bool internal, Thread* target) {
    Proc* proc = node->proc;
    if (strong) {
        if (internal) {
            if (!target && node->internal_strong_refs == 0 && !(proc && node == proc->context->mgr && node->has_strong_ref)) return -EINVAL;
            node->internal_strong_refs++;
        } else {
            node->local_strong_refs++;
        }
        if (!node->has_strong_ref && target) {
            dequeue(node);
            enqueueDeferredThreadWork(target, node);
        }
    } else {
        if (!internal) node->local_weak_refs++;
        if (!node->has_weak_ref && !node->queued()) {
            if (!target) return -EINVAL;
            target->todo.push(node);
        }
    }
    return 0;
}

void Driver::decNode(Node* node, bool strong, bool internal) {
    Proc* proc = node->proc;
    if (strong) {
        if (internal) node->internal_strong_refs--;
        else node->local_strong_refs--;
        if (node->local_strong_refs || node->internal_strong_refs) return;
    } else {
        if (!internal) node->local_weak_refs--;
        if (node->local_weak_refs || !node->refs.empty()) return;
    }
    if (proc && (node->has_strong_ref || node->has_weak_ref)) {
        if (!node->queued()) {
            proc->todo.push(node);
            wakeupProc(proc);
        }
        return;
    }
    if (node->refs.empty() && !node->local_strong_refs && !node->local_weak_refs) {
        if (proc) {
            dequeue(node);
            proc->nodes.erase(node->ptr);
        }
        delete node;
    }
}

Ref* Driver::getRef(Proc* p, uint32_t desc, bool need_strong) {
    auto it = p->refs_by_desc.find(desc);
    if (it == p->refs_by_desc.end()) return nullptr;
    if (need_strong && !it->second->strong) return nullptr;
    return it->second;
}

int Driver::incRefForNode(Proc* p, Node* n, bool strong, Thread* target, uint32_t* desc) {
    Ref* ref;
    auto it = p->refs_by_node.find(n);
    if (it != p->refs_by_node.end()) {
        ref = it->second;
    } else {
        ref = new Ref;
        ref->proc = p;
        ref->node = n;
        // the lowest free descriptor; 0 is the context manager's
        uint32_t d = n == p->context->mgr ? 0 : 1;
        for (auto& [k, r] : p->refs_by_desc) {
            if (k > d) break;
            if (k == d) d++;
        }
        ref->desc = d;
        p->refs_by_desc[d] = ref;
        p->refs_by_node[n] = ref;
        n->refs.insert(ref);
    }
    int ret = 0;
    if (strong) {
        if (ref->strong == 0) ret = incNode(n, true, true, target);
        if (!ret) ref->strong++;
    } else {
        if (ref->weak == 0) ret = incNode(n, false, true, target);
        if (!ret) ref->weak++;
    }
    if (ret && !ref->strong && !ref->weak) {
        cleanupRef(ref);
        return ret;
    }
    if (desc) *desc = ref->desc;
    return ret;
}

void Driver::cleanupRef(Ref* ref) {
    Proc* p = ref->proc;
    p->refs_by_desc.erase(ref->desc);
    p->refs_by_node.erase(ref->node);
    Node* n = ref->node;
    if (ref->strong) decNode(n, true, true);  // n stays: the ref is still in n->refs
    n->refs.erase(ref);
    decNode(n, false, true);  // may delete n
    if (ref->death) {
        dequeue(ref->death);
        delete ref->death;
    }
    delete ref;
}

bool Driver::decRef(Ref* ref, bool strong) {
    if (strong) {
        if (ref->strong == 0) return false;
        ref->strong--;
        if (ref->strong == 0) decNode(ref->node, true, true);
    } else {
        if (ref->weak == 0) return false;
        ref->weak--;
    }
    if (ref->strong == 0 && ref->weak == 0) {
        cleanupRef(ref);
        return true;
    }
    return false;
}

int Driver::updateRefForHandle(Proc* p, uint32_t desc, bool increment, bool strong) {
    Ref* ref = getRef(p, desc, strong);
    if (!ref) return -EINVAL;
    if (increment) {
        if (strong) {
            if (ref->strong == 0 && incNode(ref->node, true, true, nullptr)) return -EINVAL;
            ref->strong++;
        } else {
            if (ref->weak == 0 && incNode(ref->node, false, true, nullptr)) return -EINVAL;
            ref->weak++;
        }
    } else {
        decRef(ref, strong);
    }
    return 0;
}

void Driver::nodeRelease(Node* node) {
    Proc* proc = node->proc;
    releaseWork(proc, node->async_todo);
    dequeue(node);
    proc->nodes.erase(node->ptr);
    if (node->refs.empty()) {
        delete node;
        return;
    }
    node->proc = nullptr;
    node->local_strong_refs = node->local_weak_refs = 0;
    for (Ref* ref : node->refs) {
        if (!ref->death) continue;
        ref->death->type = Work::DEAD_BINDER;
        ref->proc->todo.push(ref->death);
        wakeupProc(ref->proc);
    }
}

void Driver::deathCommand(Thread* t, uint32_t cmd, uint32_t target, binder_uintptr_t cookie) {
    Proc* proc = t->proc;
    Ref* ref = getRef(proc, target, false);
    if (!ref) return;
    if (cmd == BC_REQUEST_DEATH_NOTIFICATION) {
        if (ref->death) return;
        ref->death = new Death;
        ref->death->cookie = cookie;
        if (!ref->node->proc) {  // already dead
            ref->death->type = Work::DEAD_BINDER;
            proc->todo.push(ref->death);
            wakeupProc(proc);
        }
        return;
    }
    Death* death = ref->death;
    if (!death || death->cookie != cookie) return;
    ref->death = nullptr;
    if (!death->queued()) {
        death->type = Work::CLEAR_DEATH;
        if (t->looper & (LOOPER_REGISTERED | LOOPER_ENTERED)) {
            enqueueThreadWork(t, death);
        } else {
            proc->todo.push(death);
            wakeupProc(proc);
        }
    } else {
        death->type = Work::DEAD_BINDER_AND_CLEAR;
    }
}

void Driver::deadBinderDone(Thread* t, binder_uintptr_t cookie) {
    Proc* proc = t->proc;
    Death* death = nullptr;
    for (Work* w = proc->delivered_death.head; w; w = w->next)
        if (static_cast<Death*>(w)->cookie == cookie) { death = static_cast<Death*>(w); break; }
    if (!death) return;
    dequeue(death);
    if (death->type == Work::DEAD_BINDER_AND_CLEAR) {
        death->type = Work::CLEAR_DEATH;
        if (t->looper & (LOOPER_REGISTERED | LOOPER_ENTERED)) {
            enqueueThreadWork(t, death);
        } else {
            proc->todo.push(death);
            wakeupProc(proc);
        }
    }  // else the ref still owns it (freed when cleared or with the ref)
}

}  // namespace binderd
