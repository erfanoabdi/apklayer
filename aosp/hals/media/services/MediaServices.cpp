/* AOSP media/mediaserver/main_mediaserver.cpp service registration, run as a
 * shared-library entrypoint under btload. No replacement media/player policy. */
#include "../../HostEnvironment.h"
#include <MediaPlayerService.h>
#include <MediaExtractorService.h>
#include <ResourceManagerService.h>
#include <android/binder_process.h>
#include <binder/IPCThreadState.h>
#include <binder/IServiceManager.h>
#include <binder/LazyServiceRegistrar.h>
#include <binder/ProcessState.h>
#include <linux/android/binder.h>
#include <sys/ioctl.h>
#include <atomic>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <mutex>
#include <set>
#include <thread>
#include <unistd.h>

// Started on first use (cli/stack.cpp sets APKLAYER_LAZY_SERVICE): the services are registered again with client counting, and the process exits
// once no client holds any of them (an AOSP lazy service); the stack starts it again on the next lookup.
//
// The extractor's clients (MediaExtractorFactory::CreateFromService) let go of media.extractor once they have an IMediaExtractor and keep using it
// and its IMediaSources: servicemanager's count alone ended the process under them (DEAD_OBJECT). So the extractor also stays while another
// process holds a binder object it handed out: a node of this process with a strong reference (BINDER_GET_NODE_DEBUG_INFO, the kernel's and
// apklayer-binderd's) that did not exist at registration (those are the service's and the registrar's callback). The media server needs no such
// check: its clients keep media.player for their process's life (IMediaDeathNotifier, MediaMetadataRetriever::sService), and the callbacks it
// registers with audioserver would keep it forever.
static int gDriver = -1;
static std::set<uintptr_t> gRegistered;
static std::atomic<bool> gClients{true};
static std::mutex gExitLock;

static std::set<uintptr_t> nodes(bool heldOnly) {
    std::set<uintptr_t> out;
    binder_node_debug_info info{};
    while (ioctl(gDriver, BINDER_GET_NODE_DEBUG_INFO, &info) == 0 && info.ptr)
        if (!heldOnly || info.has_strong_ref) out.insert(info.cookie);
    return out;
}

static bool handedOutHeld() {
    if (gDriver < 0) return false;
    for (uintptr_t node : nodes(true))
        if (!gRegistered.count(node)) return true;
    return false;
}

// _exit, not the registrar's exit(): static destructors under btload abort while binder threads still run. The watcher thread calls the registrar
// outside its callback too (AOSP wants it only there, b/191781736: its lock is not taken); gExitLock keeps the callback and the watcher apart.
static void tryExit(android::binder::LazyServiceRegistrar& registrar) {
    std::lock_guard<std::mutex> lock(gExitLock);
    if (handedOutHeld()) return;
    if (registrar.tryUnregister() && !handedOutHeld()) _exit(0);
    registrar.reRegister();
}

static void lazy(const char* name) {
    if (!apklayer::hostGetenv("APKLAYER_LAZY_SERVICE")) return;
    auto& registrar = android::binder::LazyServiceRegistrar::getInstance();
    registrar.setActiveServicesCallback([&registrar](bool clients) {
        gClients = clients;
        if (!clients) tryExit(registrar);
        return true;
    });
    auto sm = android::defaultServiceManager();
    if (auto binder = sm->checkService(android::String16(name))) (void)registrar.registerService(binder, name);
    // a lookup sets servicemanager's client guarantee, which registering again cleared: the registrar then hears of its clients, or of none
    (void)sm->checkService(android::String16(name));
}

// The extractor's own check (above): servicemanager reports the service's clients only, so the handed-out objects' end is noticed by polling.
static void watchHandedOut() {
    if (!apklayer::hostGetenv("APKLAYER_LAZY_SERVICE")) return;
    // the driver's fd (ProcessState has no other accessor); this thread joins the pool as a looper below anyway
    if (android::IPCThreadState::self()->setupPolling(&gDriver) != 0) return;
    gRegistered = nodes(false);  // before the thread pool starts: no client has an object yet
    std::thread([] {
        for (;;) {
            sleep(5);
            if (!gClients) tryExit(android::binder::LazyServiceRegistrar::getInstance());
        }
    }).detach();
}

extern "C" int main(int argc, char** argv) {
    ABinderProcess_setThreadPoolMaxThreadCount(16);
    if (argc == 2 && !strcmp(argv[1], "--extractor")) {
        android::MediaExtractorService::instantiate();
        lazy("media.extractor");
        watchHandedOut();
        fprintf(stderr, "media: registered extractor service\n");
    } else {
        android::MediaPlayerService::instantiate();
        android::ResourceManagerService::instantiate();
        for (const char* name : {"media.player", "media.resource_manager", "media.resource_observer"}) lazy(name);
        fprintf(stderr, "media: registered player/resource-manager services\n");
    }
    android::ProcessState::self()->startThreadPool();
    android::IPCThreadState::self()->joinThreadPool();
    return 0;
}
