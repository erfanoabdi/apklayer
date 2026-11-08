// The receive area of a proc (binder_alloc.c): buffers carved out of the memfd, best fit, neighbours merged when freed; oneway buffers may
// take half of the area at most (free_async_space), as the kernel's. The area is shared memory from the start: no page bookkeeping.
#include <cerrno>

#include "driver.h"

namespace binderd {

Buffer* Driver::allocBuf(Proc* p, size_t data_size, size_t offsets_size, size_t extra_size, bool is_async, int* err) {
    if (!p->area) { *err = -ESRCH; return nullptr; }  // not mapped (yet, or any more): the kernel's "vma cleared, target dead or dying"
    size_t a = align8(data_size), b = align8(offsets_size), c = align8(extra_size);
    if (a < data_size || b < offsets_size || c < extra_size || a + b < a || a + b + c < a + b) { *err = -EINVAL; return nullptr; }
    size_t size = a + b + c;
    if (size < sizeof(void*)) size = sizeof(void*);
    if (is_async && p->free_async_space < size) { *err = -ENOSPC; return nullptr; }
    Buffer* best = nullptr;
    for (auto& [off, buf] : p->buffers)
        if (buf->free && buf->size >= size && (!best || buf->size < best->size)) best = buf;
    if (!best) { *err = -ENOSPC; return nullptr; }
    if (best->size > size) {
        Buffer* rest = new Buffer;
        rest->off = best->off + size;
        rest->size = best->size - size;
        p->buffers[rest->off] = rest;
        best->size = size;
    }
    best->free = false;
    best->async = is_async;
    best->allow_user_free = false;
    best->data_size = data_size;
    best->offsets_size = offsets_size;
    if (is_async) p->free_async_space -= size;
    return best;
}

void Driver::freeAlloc(Proc* p, Buffer* b) {
    if (b->async) p->free_async_space += b->size;
    b->free = true;
    b->async = b->allow_user_free = false;
    b->transaction = nullptr;
    b->target_node = nullptr;
    b->objects.clear();
    auto it = p->buffers.find(b->off);
    auto next = std::next(it);
    if (next != p->buffers.end() && next->second->free) {
        b->size += next->second->size;
        delete next->second;
        p->buffers.erase(next);
    }
    if (it != p->buffers.begin()) {
        auto prev = std::prev(it);
        if (prev->second->free) {
            prev->second->size += b->size;
            delete b;
            p->buffers.erase(it);
        }
    }
}

Buffer* Driver::bufferForUser(Proc* p, binder_uintptr_t ptr) {
    if (ptr < p->user_base) return nullptr;
    auto it = p->buffers.find(ptr - p->user_base);
    if (it == p->buffers.end() || it->second->free || !it->second->allow_user_free) return nullptr;
    return it->second;
}

}  // namespace binderd
