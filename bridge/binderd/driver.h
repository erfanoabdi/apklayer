// The binder driver model of apklayer's userspace binder (docs/BINDER.md): procs, threads, nodes, refs, transactions, buffers and work
// queues, after Linux's drivers/android/binder.c (written from it, structure and names kept so the two can be compared). One Driver per context
// (binder, hwbinder, vndbinder), each run by a thread of its own (bridge/binderd/main.cpp): no locks inside; a thread that would sleep in the
// kernel is a request the daemon answers later.
#pragma once

#include <linux/android/binder.h>
#include <sys/types.h>

#include <cstdint>
#include <functional>
#include <list>
#include <map>
#include <set>
#include <string>
#include <vector>

namespace binderd {

struct Proc;
struct Thread;
struct Node;
struct Transaction;

// work items on a queue (binder_work): intrusive, a work item is on at most one list
struct WorkList;
struct Work {
    enum Type { TRANSACTION, TRANSACTION_COMPLETE, TRANSACTION_PENDING, RETURN_ERROR, NODE, DEAD_BINDER, DEAD_BINDER_AND_CLEAR, CLEAR_DEATH } type;
    WorkList* list = nullptr;
    Work *prev = nullptr, *next = nullptr;
    explicit Work(Type t) : type(t) {}
    virtual ~Work() = default;
    bool queued() const { return list != nullptr; }
};
struct WorkList {
    Work *head = nullptr, *tail = nullptr;
    bool empty() const { return head == nullptr; }
    void push(Work* w);
    Work* pop();
};
void dequeue(Work* w);

struct Error : Work {
    uint32_t cmd = BR_OK;
    Error() : Work(RETURN_ERROR) {}
};

struct Death : Work {
    binder_uintptr_t cookie = 0;
    Death() : Work(DEAD_BINDER) {}
};

struct Ref;
struct Node : Work {
    int debug_id;
    Proc* proc;  // nullptr once the owner died
    binder_uintptr_t ptr, cookie;
    int internal_strong_refs = 0, local_strong_refs = 0, local_weak_refs = 0;
    std::set<Ref*> refs;
    bool has_strong_ref = false, pending_strong_ref = false, has_weak_ref = false, pending_weak_ref = false;
    bool accept_fds = false, has_async_transaction = false;
    WorkList async_todo;
    Node() : Work(NODE) {}
};

struct Ref {
    Proc* proc;
    Node* node;
    uint32_t desc;
    int strong = 0, weak = 0;
    Death* death = nullptr;
};

// an object the buffer holds a reference for (released with the buffer, binder_transaction_buffer_release)
struct BufferObject {
    uint32_t type;      // BINDER_TYPE_BINDER/WEAK_BINDER (a local ref on node) or HANDLE/WEAK_HANDLE (a ref of the buffer's proc)
    Node* node = nullptr;
    uint32_t desc = 0;
};

struct Buffer {
    size_t off = 0, size = 0;  // in the receive area
    bool free = true, async = false, allow_user_free = false;
    Transaction* transaction = nullptr;
    Node* target_node = nullptr;  // holds a local strong ref while the buffer lives (a transaction's buffer)
    size_t data_size = 0, offsets_size = 0;
    std::vector<BufferObject> objects;
};

// an fd that travels with a transaction: the daemon holds it until it is delivered (then closed here, the receiver got its own) or the
// transaction fails (closed: nothing leaks)
struct FdFixup {
    int fd;
    size_t offset;  // in the receive area
    bool fda;
};

struct Transaction : Work {
    int debug_id;
    Thread* from = nullptr;  // the waiting sender of a two-way transaction
    Transaction* from_parent = nullptr;
    Proc* to_proc = nullptr;
    Thread* to_thread = nullptr;
    Transaction* to_parent = nullptr;
    bool need_reply = false;
    Buffer* buffer = nullptr;
    uint32_t code = 0, flags = 0;
    uid_t sender_euid = 0;
    pid_t from_pid = 0;
    std::vector<FdFixup> fds;
    Transaction() : Work(TRANSACTION) {}
    ~Transaction() override;
};

enum { LOOPER_REGISTERED = 1, LOOPER_ENTERED = 2, LOOPER_EXITED = 4, LOOPER_INVALID = 8, LOOPER_WAITING = 16, LOOPER_POLL = 32 };

// a read the thread is blocked in (the reply to its BINDER_WRITE_READ request is sent once there is work)
struct PendingRead {
    uint64_t size = 0, write_consumed = 0;
    uint32_t flags = 0;
};

struct Thread {
    Proc* proc;
    int sock;
    int tid;
    const uint8_t* tx = nullptr;  // the thread's shared buffer, read-only here (protocol.h BD_TX_SIZE): its write streams
    uint32_t looper = 0;
    bool looper_need_return = true;
    Transaction* transaction_stack = nullptr;
    WorkList todo;
    bool process_todo = false;
    Error return_error, reply_error;
    bool is_dead = false;
    bool waiting = false;  // a read is pending
    PendingRead pending;
    binder_extended_error ee{};
};

struct Context {
    std::string name;
    Node* mgr = nullptr;
    bool mgr_uid_set = false;
    uid_t mgr_uid = 0;
};

struct Proc {
    Context* context;
    int sock;  // the process's binder fd: its end is the process's death
    uint64_t cookie;
    pid_t pid;
    uid_t euid;
    // the receive area: a memfd mapped here and (read-only for libbinder) in the process
    int memfd = -1;
    uint8_t* area = nullptr;
    size_t area_size = 0;
    uint64_t user_base = 0;
    std::map<size_t, Buffer*> buffers;  // by offset, free and used, covering the area
    size_t free_async_space = 0;
    std::map<binder_uintptr_t, Node*> nodes;
    std::map<uint32_t, Ref*> refs_by_desc;
    std::map<Node*, Ref*> refs_by_node;
    std::list<Thread*> threads;
    WorkList todo, delivered_death;
    std::list<Thread*> waiting_threads;
    uint32_t max_threads = 0;
    int requested_threads = 0, requested_threads_started = 0;
    int outstanding_txns = 0;
    bool is_dead = false, is_frozen = false, sync_recv = false, async_recv = false, oneway_spam_detection = false;
    uint32_t wakes = 0;  // wake bytes written to sock and not yet reported (bd_rep.ndrain)
};

// what a request's reply carries besides its header: read bytes, fds and their fixups (a read delivers at most one transaction)
struct FixOut {
    uint64_t offset;
    uint32_t fd_index, fda;
    uint64_t buffer;
};
struct Reply {
    int ret = 0;
    uint64_t write_consumed = 0;
    std::vector<uint8_t> data;
    std::vector<int> fds;  // closed by the sender once sent (the receiver has its own)
    std::vector<FixOut> fixes;
};

class Driver {
public:
    // connections (main.cpp): a proc's hello, a thread's hello, a request, a closed socket
    Proc* newProc(int sock, pid_t pid, uid_t euid);
    Thread* newThread(Proc* proc, int sock, int tid);
    Proc* procByCookie(uint64_t cookie);
    void releaseProc(Proc* proc);      // binder_deferred_release
    void releaseThread(Thread* t);     // binder_thread_release, then the thread is gone
    // returns false when the reply waits (a blocking read with no work yet); the reply is then sent by flush()
    bool ioctl(Thread* t, uint32_t cmd, const std::vector<uint8_t>& arg, uint64_t a1, uint64_t a2, uint64_t a3, std::vector<int>& fds, Reply& out);
    int mmap(Thread* t, size_t size, int* memfd);
    int mapped(Thread* t, uint64_t base);
    // after each request: answer the threads whose reads now have work, wake polling processes
    void flush();
    // main.cpp sends replies and closes sockets
    std::function<void(Thread*, Reply&)> sendReply;
    std::function<void(int)> closeSocket;
    uint8_t index = 0;  // this context's number, the top byte of its procs' cookies (a thread's hello finds its context by it)

    // the model (binder.c names)
    int writeCommands(Thread* t, const uint8_t* buf, size_t size, uint64_t* consumed, std::vector<int>& fds, size_t* fd_next);
    bool readWork(Thread* t, Reply& out);
    void transaction(Thread* t, const binder_transaction_data& tr, bool reply, uint64_t extra_size, const uint8_t* data,
                     const uint8_t* offsets, const uint8_t* sg, uint64_t sg_size, std::vector<int> fds);
    int procTransaction(Transaction* t, Proc* proc, Thread* thread);
    void sendFailedReply(Transaction* t, uint32_t error);
    void cleanupTransaction(Transaction* t, uint32_t error);
    void freeTransaction(Transaction* t);
    void popTransaction(Thread* target, Transaction* t);
    void releaseWork(Proc* proc, WorkList& list);
    void freeBuf(Proc* proc, Buffer* b, bool failure);
    void releaseBufferObjects(Proc* proc, Buffer* b, bool failure);
    // threads and wakeups
    bool availableForProcWork(Thread* t);
    bool hasWork(Thread* t, bool proc_work);
    void enqueueThreadWork(Thread* t, Work* w);          // binder_enqueue_thread_work_ilocked
    void enqueueDeferredThreadWork(Thread* t, Work* w);  // binder_enqueue_deferred_thread_work_ilocked
    Thread* selectThread(Proc* p);
    void wakeThread(Thread* t);
    void wakeupProc(Proc* p);
    void wakePoll(Proc* p);
    // nodes and refs (node.cpp)
    Node* newNode(Proc* p, binder_uintptr_t ptr, binder_uintptr_t cookie, uint32_t flags);
    Node* getNode(Proc* p, binder_uintptr_t ptr);
    int incNode(Node* n, bool strong, bool internal, Thread* target);
    void decNode(Node* n, bool strong, bool internal);
    Ref* getRef(Proc* p, uint32_t desc, bool need_strong);
    int incRefForNode(Proc* p, Node* n, bool strong, Thread* target, uint32_t* desc);
    int updateRefForHandle(Proc* p, uint32_t desc, bool increment, bool strong);
    bool decRef(Ref* r, bool strong);
    void cleanupRef(Ref* r);
    void nodeRelease(Node* n);
    void deathCommand(Thread* t, uint32_t cmd, uint32_t target, binder_uintptr_t cookie);
    void deadBinderDone(Thread* t, binder_uintptr_t cookie);
    // the receive area (alloc.cpp)
    Buffer* allocBuf(Proc* p, size_t data_size, size_t offsets_size, size_t extra_size, bool is_async, int* err);
    void freeAlloc(Proc* p, Buffer* b);
    Buffer* bufferForUser(Proc* p, binder_uintptr_t ptr);

    Context context;
    std::map<uint64_t, Proc*> procs;
    std::set<Thread*> ready;  // threads whose pending read may now be answered
    std::set<Proc*> dirty;    // procs whose poll wakeup is to be checked
    int next_debug_id = 1;
    bool debug = false;
};

inline size_t align8(size_t n) { return (n + 7) & ~size_t(7); }

}  // namespace binderd
