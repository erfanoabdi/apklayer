/* The write stream of a BINDER_WRITE_READ, marshalled for the userspace binder's daemon (protocol.h "struct bd_txn") into the thread's shared
 * buffer: what the kernel would copy out of the caller's memory goes inline, the fds of BINDER_TYPE_FD and BINDER_TYPE_FDA objects go as
 * SCM_RIGHTS with the request. The lists are the thread's, reused. Included once, by binder_route.c. */

struct wbuf {
    uint8_t *data; /* the shared buffer */
    size_t len, cap;
    int *fds;
    uint32_t nfds, fcap;
    uint64_t *mend, *oend; /* per command: where it ends in the marshalled and in the caller's stream */
    size_t ncmd, ccap;
    int err;
};

static void wbuf_reset(struct wbuf *w, uint8_t *data, size_t cap)
{
    w->data = data;
    w->cap = cap;
    w->len = 0;
    w->nfds = 0;
    w->ncmd = 0;
    w->err = 0;
}

static void wbuf_free(struct wbuf *w)
{
    free(w->fds);
    free(w->mend);
    free(w->oend);
}

static void put(struct wbuf *w, const void *p, size_t n)
{
    if (w->err) return;
    if (n > w->cap - w->len) { w->err = -E2BIG; return; }  /* larger than any receive area: the kernel would refuse it too */
    if (p) memcpy(w->data + w->len, p, n);
    else memset(w->data + w->len, 0, n);
    w->len += n;
}

static void put_padded(struct wbuf *w, const void *p, size_t n)
{
    put(w, p, n);
    put(w, NULL, ((n + 7) & ~(size_t)7) - n);
}

static void put_fd(struct wbuf *w, int fd)
{
    if (w->err) return;
    if (w->nfds == w->fcap) {
        uint32_t cap = w->fcap ? w->fcap * 2 : 8;
        int *f = realloc(w->fds, cap * sizeof *f);
        if (!f) { w->err = -ENOMEM; return; }
        w->fds = f;
        w->fcap = cap;
    }
    w->fds[w->nfds++] = fd;
}

static void end_cmd(struct wbuf *w, uint64_t orig_end)
{
    if (w->err) return;
    if (w->ncmd == w->ccap) {
        size_t cap = w->ccap ? w->ccap * 2 : 16;
        uint64_t *m = realloc(w->mend, cap * sizeof *m), *o = m ? realloc(w->oend, cap * sizeof *o) : NULL;
        if (m) w->mend = m;
        if (!o) { w->err = -ENOMEM; return; }
        w->oend = o;
        w->ccap = cap;
    }
    w->mend[w->ncmd] = w->len;
    w->oend[w->ncmd++] = orig_end;
}

static uint64_t unmarshal_consumed(const struct wbuf *w, uint64_t consumed)
{
    uint64_t orig = 0;
    for (size_t i = 0; i < w->ncmd && w->mend[i] <= consumed; i++) orig = w->oend[i];
    return orig;
}

static size_t object_size(uint32_t type)
{
    switch (type) {
    case BINDER_TYPE_BINDER: case BINDER_TYPE_WEAK_BINDER: case BINDER_TYPE_HANDLE: case BINDER_TYPE_WEAK_HANDLE: return sizeof(struct flat_binder_object);
    case BINDER_TYPE_FD: return sizeof(struct binder_fd_object);
    case BINDER_TYPE_FDA: return sizeof(struct binder_fd_array_object);
    case BINDER_TYPE_PTR: return sizeof(struct binder_buffer_object);
    default: return 0;
    }
}

/* the object at index i of a transaction, or NULL when it does not lie inside the data */
static const struct binder_object_header *object_at(const struct binder_transaction_data *tr, uint64_t i)
{
    const binder_size_t *offs = (const binder_size_t *)(uintptr_t)tr->data.ptr.offsets;
    if (i >= tr->offsets_size / sizeof(binder_size_t)) return NULL;
    binder_size_t at = offs[i];
    if (at + sizeof(struct binder_object_header) > tr->data_size) return NULL;
    const struct binder_object_header *h = (const void *)((const uint8_t *)(uintptr_t)tr->data.ptr.buffer + at);
    size_t n = object_size(h->type);
    return n && at + n <= tr->data_size ? h : NULL;
}

static void marshal_transaction(struct wbuf *w, const uint8_t *cmd_and_tr, size_t psize, const struct binder_transaction_data *tr)
{
    put(w, cmd_and_tr, sizeof(uint32_t) + psize);
    uint32_t fds_before = w->nfds;
    uint64_t sg = 0, n = tr->offsets_size / sizeof(binder_size_t);
    for (uint64_t i = 0; i < n; i++) {
        const struct binder_object_header *h = object_at(tr, i);
        if (!h) continue;  /* the daemon refuses the transaction */
        if (h->type == BINDER_TYPE_PTR) sg += (((const struct binder_buffer_object *)h)->length + 7) & ~(uint64_t)7;
        else if (h->type == BINDER_TYPE_FD) put_fd(w, ((const struct binder_fd_object *)h)->fd);
        else if (h->type == BINDER_TYPE_FDA) {
            const struct binder_fd_array_object *fda = (const void *)h;
            const struct binder_object_header *ph = fda->parent < i ? object_at(tr, fda->parent) : NULL;
            const struct binder_buffer_object *parent = ph && ph->type == BINDER_TYPE_PTR ? (const void *)ph : NULL;
            if (!parent || fda->parent_offset + fda->num_fds * sizeof(uint32_t) > parent->length) continue;
            const uint32_t *src = (const uint32_t *)(uintptr_t)(parent->buffer + fda->parent_offset);
            for (binder_size_t k = 0; k < fda->num_fds; k++) put_fd(w, (int)src[k]);
        }
    }
    struct bd_txn x = {.nfds = w->nfds - fds_before, .sg_size = sg};
    put(w, &x, sizeof x);
    put_padded(w, (const void *)(uintptr_t)tr->data.ptr.buffer, tr->data_size);
    put_padded(w, (const void *)(uintptr_t)tr->data.ptr.offsets, tr->offsets_size);
    for (uint64_t i = 0; i < n; i++) {
        const struct binder_object_header *h = object_at(tr, i);
        if (h && h->type == BINDER_TYPE_PTR) {
            const struct binder_buffer_object *bp = (const void *)h;
            put_padded(w, (const void *)(uintptr_t)bp->buffer, bp->length);
        }
    }
}

static int marshal(struct conn *c, const uint8_t *buf, size_t size, struct wbuf *w)
{
    size_t pos = 0;
    while (pos < size && !w->err) {
        uint32_t cmd;
        if (size - pos < sizeof cmd) { put(w, buf + pos, size - pos); break; }  /* the daemon answers -EFAULT, as the kernel would */
        memcpy(&cmd, buf + pos, sizeof cmd);
        size_t psize = _IOC_SIZE(cmd);
        if (size - pos - sizeof cmd < psize) { put(w, buf + pos, size - pos); break; }
        const uint8_t *p = buf + pos + sizeof cmd;
        switch (cmd) {
        case BC_TRANSACTION: case BC_REPLY: case BC_TRANSACTION_SG: case BC_REPLY_SG: {
            struct binder_transaction_data tr;
            memcpy(&tr, p, sizeof tr);
            marshal_transaction(w, buf + pos, psize, &tr);
        } break;
        case BC_FREE_BUFFER: {
            binder_uintptr_t ptr;
            memcpy(&ptr, p, sizeof ptr);
            fda_free(c, ptr);
            put(w, buf + pos, sizeof cmd + psize);
        } break;
        default:
            put(w, buf + pos, sizeof cmd + psize);
        }
        pos += sizeof cmd + psize;
        end_cmd(w, pos);
    }
    return w->err;
}
