#pragma once
#include <android-base/logging.h>
#include <binder/IServiceManager.h>
#include <media/AudioSystem.h>
#include <cstdio>
#include "TrackProbe.h"

inline int audioServicesProbe(bool playback = false) {
    using namespace android;
    setvbuf(stdout, nullptr, _IOLBF, 0);
    auto manager = defaultServiceManager();
    CHECK(manager->waitForService(String16("media.audio_flinger")));
    CHECK(manager->waitForService(String16("media.audio_policy")));
    uint32_t rate = 0, latency = 0;
    size_t frames = 0;
    CHECK(AudioSystem::getOutputSamplingRate(&rate, AUDIO_STREAM_MUSIC) == OK);
    CHECK(AudioSystem::getOutputFrameCount(&frames, AUDIO_STREAM_MUSIC) == OK);
    CHECK(AudioSystem::getOutputLatency(&latency, AUDIO_STREAM_MUSIC) == OK);
    CHECK(rate == 48000 && frames > 0);
    printf("PASS original audio services route rate=%u frames=%zu latency=%u\n", rate, frames, latency);
    return playback ? audioTrackProbe() : 0;
}
