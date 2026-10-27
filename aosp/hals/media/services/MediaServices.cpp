/* AOSP media/mediaserver/main_mediaserver.cpp service registration, run as a
 * shared-library entrypoint under btload. No replacement media/player policy. */
#include <MediaPlayerService.h>
#include <MediaExtractorService.h>
#include <ResourceManagerService.h>
#include <android/binder_process.h>
#include <binder/IPCThreadState.h>
#include <binder/ProcessState.h>
#include <cstdio>
#include <cstring>
extern "C" int main(int argc, char** argv) {
    ABinderProcess_setThreadPoolMaxThreadCount(16);
    if (argc == 2 && !strcmp(argv[1], "--extractor")) {
        android::MediaExtractorService::instantiate();
        fprintf(stderr, "media: registered extractor service\n");
    } else {
        android::MediaPlayerService::instantiate();
        android::ResourceManagerService::instantiate();
        fprintf(stderr, "media: registered player/resource-manager services\n");
    }
    android::ProcessState::self()->startThreadPool();
    android::IPCThreadState::self()->joinThreadPool();
    return 0;
}
