// servicetool list | check NAME | wait NAME | add NAME | call NAME CODE DESCRIPTOR ARGS: talks to the service manager like Android's `service` tool. `add` registers a
// local do-nothing binder under NAME and stays alive (checks that registration and lookup work end to end). `bench serve|run` measures binder itself
// (kernel or apklayer's userspace binder, docs/BINDER.md): round trips and calls per second through libbinder.
#include <binder/Binder.h>
#include <binder/IPCThreadState.h>
#include <binder/IServiceManager.h>
#include <binder/Parcel.h>
#include <binder/ProcessState.h>
#include <stdlib.h>
#include <unistd.h>
#include <utils/Vector.h>
#include <utils/String16.h>
#include <stdio.h>
#include <string.h>

#include <algorithm>
#include <chrono>
#include <thread>
#include <vector>

using namespace android;

namespace {
// bench serve: code 1 answers empty, code 2 echoes the data
class BenchService : public BBinder {
    status_t onTransact(uint32_t code, const Parcel& data, Parcel* reply, uint32_t flags) override {
        if (code == 1) return OK;
        if (code == 2) return reply->appendFrom(&data, 0, data.dataSize());
        return BBinder::onTransact(code, data, reply, flags);
    }
};

// bench run NAME COUNT SIZE [THREADS] [call|echo|oneway]: COUNT transactions of SIZE bytes from each thread, after 200 unmeasured ones
int benchRun(const sp<IBinder>& b, int count, int size, int threads, const char* mode) {
    bool oneway = !strcmp(mode, "oneway"), echo = !strcmp(mode, "echo");
    std::vector<std::vector<double>> lat(threads);
    std::vector<uint8_t> payload(size, 0x5a);
    auto one = [&](std::vector<double>* out) {
        Parcel data, reply;
        data.write(payload.data(), payload.size());
        auto t0 = std::chrono::steady_clock::now();
        status_t st = b->transact(echo ? 2 : 1, data, &reply, oneway ? IBinder::FLAG_ONEWAY : 0);
        if (out) out->push_back(std::chrono::duration<double, std::micro>(std::chrono::steady_clock::now() - t0).count());
        return st;
    };
    for (int i = 0; i < 200; i++)
        if (one(nullptr) != OK) { fprintf(stderr, "bench: transaction failed\n"); return 1; }
    if (oneway) b->pingBinder();
    auto t0 = std::chrono::steady_clock::now();
    std::vector<std::thread> pool;
    for (int t = 0; t < threads; t++)
        pool.emplace_back([&, t] { lat[t].reserve(count); for (int i = 0; i < count; i++) one(&lat[t]); });
    for (auto& th : pool) th.join();
    if (oneway) b->pingBinder();  // the oneway queue drained: the service answers in order
    double secs = std::chrono::duration<double>(std::chrono::steady_clock::now() - t0).count();
    std::vector<double> all;
    for (auto& v : lat) all.insert(all.end(), v.begin(), v.end());
    std::sort(all.begin(), all.end());
    double sum = 0;
    for (double v : all) sum += v;
    printf("bench: mode=%s size=%d threads=%d calls=%zu avg_us=%.1f p50_us=%.1f p99_us=%.1f rate=%.0f/s\n", mode, size, threads, all.size(),
           sum / all.size(), all[all.size() / 2], all[all.size() * 99 / 100], all.size() / secs);
    return 0;
}
}  // namespace

extern "C" int main(int argc, char** argv) {
    ProcessState::self()->startThreadPool();
    sp<IServiceManager> sm = defaultServiceManager();
    if (sm == nullptr) { fprintf(stderr, "servicetool: no service manager\n"); return 1; }
    const char* cmd = argc > 1 ? argv[1] : "list";
    if (!strcmp(cmd, "list")) {
        for (const auto& name : sm->listServices()) printf("%s\n", String8(name).c_str());
    } else if (!strcmp(cmd, "wait") && argc > 2) {  // blocks until the service registers (the stack supervisor's readiness, no polling)
        sp<IBinder> b = sm->waitForService(String16(argv[2]));
        printf("%s: %s\n", argv[2], b ? "found" : "not found");
        return b ? 0 : 1;
    } else if (!strcmp(cmd, "check") && argc > 2) {
        sp<IBinder> b = sm->checkService(String16(argv[2]));
        printf("%s: %s\n", argv[2], b ? "found" : "not found");
        return b ? 0 : 1;
    } else if (!strcmp(cmd, "dump") && argc > 2) {  // dump NAME [args...]: the service's dump() on stdout (SurfaceFlinger: layers, displays, composition)
        sp<IBinder> b = sm->checkService(String16(argv[2]));
        if (b == nullptr) { printf("%s: not found\n", argv[2]); return 1; }
        Vector<String16> args;
        for (int i = 3; i < argc; i++) args.add(String16(argv[i]));
        fflush(stdout);
        return b->dump(STDOUT_FILENO, args) == OK ? 0 : 1;
    } else if (!strcmp(cmd, "add") && argc > 2) {
        status_t st = sm->addService(String16(argv[2]), sp<BBinder>::make());
        printf("add %s: %d\n", argv[2], st);
        fflush(stdout);
        if (st != OK) return 1;
        if (argc > 3 && !strcmp(argv[3], "--stay")) IPCThreadState::self()->joinThreadPool();
    } else if (!strcmp(cmd, "call") && argc > 4) {  // call NAME CODE DESCRIPTOR [s16 STR | i32 N | binder null]...: as `service call`, prints the reply's first int
        sp<IBinder> b = sm->checkService(String16(argv[2]));
        if (b == nullptr) { printf("%s: not found\n", argv[2]); return 1; }
        Parcel data, reply;
        data.writeInterfaceToken(String16(argv[4]));
        for (int i = 5; i + 1 < argc; i += 2) {
            if (!strcmp(argv[i], "s16")) data.writeString16(String16(argv[i + 1]));
            else if (!strcmp(argv[i], "i32")) data.writeInt32(atoi(argv[i + 1]));
            else if (!strcmp(argv[i], "binder") && !strcmp(argv[i + 1], "null")) data.writeStrongBinder(nullptr);
            else { fprintf(stderr, "servicetool call: unknown argument type %s\n", argv[i]); return 2; }
        }
        status_t st = b->transact(atoi(argv[3]), data, &reply);
        int32_t exception = st == OK ? reply.readExceptionCode() : 0;
        if (st != OK || exception != 0) { fprintf(stderr, "servicetool call: transaction failed (%d, exception %d)\n", st, exception); return 1; }
        int32_t value = reply.dataAvail() >= sizeof(int32_t) ? reply.readInt32() : 0;
        printf("reply: %d\n", value);
        return value;  // the exit status: the reply's first int after its exception header
    } else if (!strcmp(cmd, "bench") && argc > 3 && !strcmp(argv[2], "serve")) {  // bench serve NAME: libbinder's default thread pool
        status_t st = sm->addService(String16(argv[3]), sp<BenchService>::make());
        printf("bench serve %s: %d\n", argv[3], st);
        fflush(stdout);
        if (st != OK) return 1;
        IPCThreadState::self()->joinThreadPool();
    } else if (!strcmp(cmd, "bench") && argc > 5 && !strcmp(argv[2], "run")) {
        sp<IBinder> b = sm->waitForService(String16(argv[3]));
        if (b == nullptr) { printf("%s: not found\n", argv[3]); return 1; }
        return benchRun(b, atoi(argv[4]), atoi(argv[5]), argc > 6 ? atoi(argv[6]) : 1, argc > 7 ? argv[7] : "call");
    } else {
        fprintf(stderr, "usage: servicetool list | check NAME | wait NAME | dump NAME [args] | add NAME [--stay] | call NAME CODE DESCRIPTOR [s16 STR | i32 N | binder null]... | bench serve NAME | bench run NAME COUNT SIZE [THREADS] [call|echo|oneway]\n");
        return 2;
    }
    return 0;
}
