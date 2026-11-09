/* AOSP media/mediaserver/main_mediaserver.cpp service registration, run as a
 * shared-library entrypoint under btload. No replacement media/player policy. */
#include <MediaPlayerService.h>
#include <MediaExtractorService.h>
#include <ResourceManagerService.h>
#include <android/binder_process.h>
#include <binder/IPCThreadState.h>
#include <binder/IServiceManager.h>
#include <binder/LazyServiceRegistrar.h>
#include <binder/ProcessState.h>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <unistd.h>

// Started on first use (cli/stack.cpp sets APKLAYER_LAZY_SERVICE): the services are registered again with client counting, and the process exits
// once no client holds any of them (an AOSP lazy service); the stack starts it again on the next lookup.
static void lazy(const char* name) {
    if (!getenv("APKLAYER_LAZY_SERVICE")) return;
    auto& registrar = android::binder::LazyServiceRegistrar::getInstance();
    // _exit, not the registrar's exit(): static destructors under btload abort while binder threads still run
    registrar.setActiveServicesCallback([&registrar](bool clients) {
        if (!clients && registrar.tryUnregister()) _exit(0);
        if (!clients) registrar.reRegister();
        return true;
    });
    auto sm = android::defaultServiceManager();
    if (auto binder = sm->checkService(android::String16(name))) (void)registrar.registerService(binder, name);
    // a lookup sets servicemanager's client guarantee, which registering again cleared: the registrar then hears of its clients, or of none
    (void)sm->checkService(android::String16(name));
}

extern "C" int main(int argc, char** argv) {
    ABinderProcess_setThreadPoolMaxThreadCount(16);
    if (argc == 2 && !strcmp(argv[1], "--extractor")) {
        android::MediaExtractorService::instantiate();
        lazy("media.extractor");
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
