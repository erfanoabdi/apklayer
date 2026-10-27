/* AOSP media/mediaserver/main_mediaserver.cpp service registration, run as a
 * shared-library entrypoint under btload. No replacement media/player policy. */
#include <MediaPlayerService.h>
#include <ResourceManagerService.h>
#include <android/binder_process.h>
#include <binder/IPCThreadState.h>
#include <binder/ProcessState.h>
#include <cstdio>
extern "C" int main(int, char**) {
    ABinderProcess_setThreadPoolMaxThreadCount(16);
    android::MediaPlayerService::instantiate();
    android::ResourceManagerService::instantiate();
    android::ProcessState::self()->startThreadPool();
    fprintf(stderr, "media: registered player/resource-manager services\n");
    android::IPCThreadState::self()->joinThreadPool();
    return 0;
}
