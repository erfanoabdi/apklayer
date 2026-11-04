// servicetool list | check NAME | wait NAME | add NAME | call NAME CODE DESCRIPTOR ARGS: talks to the service manager like Android's `service` tool. `add` registers a
// local do-nothing binder under NAME and stays alive (checks that registration and lookup work end to end).
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

using namespace android;

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
    } else {
        fprintf(stderr, "usage: servicetool list | check NAME | wait NAME | dump NAME [args] | add NAME [--stay] | call NAME CODE DESCRIPTOR [s16 STR | i32 N | binder null]...\n");
        return 2;
    }
    return 0;
}
