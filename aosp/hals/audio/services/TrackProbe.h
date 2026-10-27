#pragma once
#include <android-base/logging.h>
#include <binder/Binder.h>
#include <media/AudioTrack.h>
#include <media/AudioSystem.h>
#include <chrono>
#include <cmath>
#include <condition_variable>
#include <cstdio>
#include <mutex>
#include <unistd.h>

class ToneCallback final : public android::AudioTrack::IAudioTrackCallback {
  public:
    bool wait() {
        std::unique_lock lock(mutex_);
        return changed_.wait_for(lock, std::chrono::seconds(3), [&] { return reached_; });
    }
  private:
    void onMarker(uint32_t position) override {
        std::lock_guard lock(mutex_);
        reached_ = position == 52800;
        changed_.notify_all();
    }
    std::mutex mutex_;
    std::condition_variable changed_;
    bool reached_ = false;
};
inline int audioTrackProbe() {
    using namespace android;
    // Framework AudioService normally initializes these stream range/index values.
    CHECK(AudioSystem::initStreamVolume(AUDIO_STREAM_MUSIC, 0, 15) == OK);
    CHECK(AudioSystem::setStreamVolumeIndex(AUDIO_STREAM_MUSIC, 15, false, AUDIO_DEVICE_OUT_SPEAKER) == OK);
    content::AttributionSourceState source;
    source.uid = getuid(); source.pid = getpid(); source.token = sp<BBinder>::make();
    auto callback = sp<ToneCallback>::make();
    wp<AudioTrack::IAudioTrackCallback> weak = callback;
    auto track = sp<AudioTrack>::make(source);
    status_t status = track->set(AUDIO_STREAM_MUSIC, 48000, AUDIO_FORMAT_PCM_16_BIT,
        AUDIO_CHANNEL_OUT_STEREO, 4096, AUDIO_OUTPUT_FLAG_NONE, weak, 0, nullptr, false,
        AUDIO_SESSION_ALLOCATE, AudioTrack::TRANSFER_SYNC, nullptr, source);
    if (status != OK) fprintf(stderr, "AudioTrack set failed %d\n", status);
    CHECK(status == OK && track->initCheck() == OK);
    CHECK(track->setMarkerPosition(52800) == OK);
    CHECK(track->start() == OK);
    for (int offset = 0; offset < 57600; offset += 960) {
        int16_t pcm[1920] = {};
        if (offset < 48000) for (int n = 0; n < 960; n++) {
            pcm[n * 2] = std::sin(2 * M_PI * 440 * (offset + n) / 48000) * 6000;
            pcm[n * 2 + 1] = std::sin(2 * M_PI * 880 * (offset + n) / 48000) * 4000;
        }
        size_t done = 0;
        while (done < sizeof pcm) {
            ssize_t count = track->write(reinterpret_cast<char*>(pcm) + done, sizeof pcm - done, true);
            CHECK(count > 0); done += count;
        }
        if ((offset + 960) % 9600 == 0) printf("audio: AudioTrack submitted=%d\n", offset + 960);
    }
    // Actual silence frames after the tone keep the graph alive through presentation;
    // the real callback, rather than a wall-clock delay, signals completion.
    CHECK(callback->wait());
    uint32_t position = 0;
    CHECK(track->getPosition(&position) == OK && position >= 52800 && position <= 57600);
    AudioTimestamp timestamp;
    status = track->getTimestamp(timestamp);
    if (status != OK) fprintf(stderr, "AudioTrack timestamp failed %d\n", status);
    CHECK(status == OK && timestamp.mPosition > 0 && timestamp.mPosition <= 57600);
    track->stop(); track->flush(); track.clear();
    printf("PASS original AudioTrack PCM/marker position=%u timestamp=%u, stop/flush/release\n", position, timestamp.mPosition);
    return 0;
}
