// Original audioserver startup ordering: construct locally, then publish both.
#include <AudioFlinger.h>
#include <AudioPolicyService.h>
#include <binder/IPCThreadState.h>
#include <binder/ProcessState.h>
#include <binder/IServiceManager.h>
#include <media/AudioSystem.h>
#include <cstdio>
#include <csignal>
#include <cstring>
#include "Probe.h"

extern "C" int main(int argc, char** argv) {
    if (argc == 2 && !std::strcmp(argv[1], "--probe")) return audioServicesProbe();
    using namespace android;
    signal(SIGPIPE, SIG_IGN);
    ProcessState::self()->startThreadPool();
    fprintf(stderr, "audio services: constructing AudioFlinger\n");
    auto flinger = sp<AudioFlinger>::make();
    auto adapter = sp<AudioFlingerServerAdapter>::make(flinger);
    if (AudioSystem::setLocalAudioFlinger(flinger) != OK) return 1;
    fprintf(stderr, "audio services: constructing AudioPolicy\n");
    auto policy = sp<AudioPolicyService>::make();
    flinger->initAudioPolicyLocal(policy);
    if (AudioSystem::setLocalAudioPolicyService(policy) != OK) return 1;
    policy->onAudioSystemReady();
    auto manager = defaultServiceManager();
    if (manager->addService(String16(IAudioFlinger::DEFAULT_SERVICE_NAME), adapter) != OK ||
        manager->addService(String16(AudioPolicyService::getServiceName()), policy) != OK) return 1;
    flinger->startupFinished();
    fprintf(stderr, "apklayer original AudioFlinger/AudioPolicy ready\n");
    IPCThreadState::self()->joinThreadPool();
    return 1;
}
