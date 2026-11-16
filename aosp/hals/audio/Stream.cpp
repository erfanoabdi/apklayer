#include "../HostEnvironment.h"
#include "Stream.h"
#include <aidl/android/media/audio/common/PcmType.h>
#include <Utils.h>
#include <cerrno>
#include <cstdlib>
#include <cstdio>
#include <cstring>
#include <dlfcn.h>
#include <string>
#include <limits>
#include <mutex>

extern "C" __attribute__((weak)) const char apklayer_audio_needs[];  // the backend's (bridge/hals/audio/probe.c); weak: optional in plugin.h

namespace aidl::android::hardware::audio::core {
using ::aidl::android::media::audio::common::AudioFormatType;
using ::aidl::android::media::audio::common::PcmType;
using ::aidl::android::hardware::audio::common::getChannelCount;
std::atomic<bool> gMicMute{false};
bool silentFallback(int status, bool input) {
    if (status == -EINVAL || status == -ENOMEM) return false;  // a refused config or no memory: a real error, not a missing server
    static std::once_flag logged[2];
    std::call_once(logged[input], [&] {
        fprintf(stderr, "[audio] no sound server answers (%s: %s): %s is silent, at real time\n",
            &apklayer_audio_needs != nullptr ? apklayer_audio_needs : "the backend", strerror(-status),
            input ? "recording" : "playback");
    });
    return true;
}
const AudioApi& linkedAudioApi() {
    static const AudioApi api{apklayer_audio_probe, apklayer_audio_open, apklayer_audio_write, apklayer_audio_position, apklayer_audio_pause, apklayer_audio_start,
        apklayer_audio_flush, apklayer_audio_drain, apklayer_audio_close, apklayer_capture_open, apklayer_capture_read,
        apklayer_capture_position, apklayer_capture_pause, apklayer_capture_start, apklayer_capture_flush, apklayer_capture_close};
    return api;
}
const AudioApi* siblingAudioApi() {
    static const AudioApi* api = []() -> const AudioApi* {
        const char* libs = apklayer::hostGetenv("APKLAYER_HOST_LIBS");
        if (!libs) return nullptr;
        const std::string list = libs;
        for (size_t at = 0; at < list.size();) {
            const size_t end = std::min(list.find(':', at), list.size());
            const std::string path = list.substr(at, end - at);
            at = end + 1;
            for (const auto& [mine, other] : {std::pair{"-audio-pipewire.so", "-audio-pulse.so"}, std::pair{"-audio-pulse.so", "-audio-pipewire.so"}}) {
                const size_t name = path.rfind(mine);
                if (name == std::string::npos || name + strlen(mine) != path.size()) continue;
                void* lib = dlopen((path.substr(0, name) + other).c_str(), RTLD_NOW | RTLD_LOCAL);
                if (!lib) return nullptr;
                static AudioApi table;  // the handle's own symbols: the linked backend's of the same names are not looked at
#define SYM(field, name) if (!(table.field = reinterpret_cast<decltype(table.field)>(dlsym(lib, name)))) return nullptr
                SYM(probe, "apklayer_audio_probe"); SYM(open, "apklayer_audio_open"); SYM(write, "apklayer_audio_write"); SYM(position, "apklayer_audio_position");
                SYM(pause, "apklayer_audio_pause"); SYM(start, "apklayer_audio_start"); SYM(flush, "apklayer_audio_flush");
                SYM(drain, "apklayer_audio_drain"); SYM(close, "apklayer_audio_close"); SYM(captureOpen, "apklayer_capture_open");
                SYM(captureRead, "apklayer_capture_read"); SYM(capturePosition, "apklayer_capture_position");
                SYM(capturePause, "apklayer_capture_pause"); SYM(captureStart, "apklayer_capture_start");
                SYM(captureFlush, "apklayer_capture_flush"); SYM(captureClose, "apklayer_capture_close");
#undef SYM
                return &table;
            }
        }
        return nullptr;
    }();
    return api;
}
// No backend answers: a refused config or no memory are real errors (not "no server"); the linked backend first, then the other one.
int PipeWireStream::openBackend() {
    api_ = &linkedAudioApi();
    int status = api_->open(&config_, &audio_);
    if (status && status != -EINVAL && status != -ENOMEM)
        if (const AudioApi* other = siblingAudioApi(); other && other->probe() >= 0 && other->open(&config_, &audio_) == 0) { api_ = other; status = 0; }
    return status;
}
int PipeWireInStream::openBackend() {
    api_ = &linkedAudioApi();
    int status = api_->captureOpen(&config_, &capture_);
    if (status && status != -EINVAL && status != -ENOMEM)
        if (const AudioApi* other = siblingAudioApi(); other && other->probe() >= 0 && other->captureOpen(&config_, &capture_) == 0) { api_ = other; status = 0; }
    return status;
}
::android::status_t PipeWireStream::init(DriverCallbackInterface*) {
    const auto format = context_.getFormat();
    if (context_.getAsyncCallback() || context_.isMmap() || context_.isOffload() ||
        format.type != AudioFormatType::PCM ||
        (format.pcm != PcmType::INT_16_BIT && format.pcm != PcmType::FLOAT_32_BIT))
        return ::android::INVALID_OPERATION;
    config_ = {static_cast<uint32_t>(context_.getSampleRate()), static_cast<uint32_t>(getChannelCount(context_.getChannelLayout())),
        format.pcm == PcmType::INT_16_BIT ? APKLAYER_AUDIO_S16 : APKLAYER_AUDIO_F32, apklayer::hostGetenv("APKLAYER_AUDIO_TARGET")};
    int status = openBackend();
    if (status && silentFallback(status, false)) return stub_.emplace(context_).init(nullptr);
    if (status == 0) status = api_->pause(audio_);
    return status;
}
::android::status_t PipeWireStream::drain(StreamDescriptor::DrainMode mode) {
    if (stub_) return stub_->drain(mode);
    return mode == StreamDescriptor::DrainMode::DRAIN_ALL ? api_->drain(audio_)
                                                         : ::android::INVALID_OPERATION;
}
::android::status_t PipeWireStream::flush() {
    if (stub_) return stub_->flush();
    int status = api_->flush(audio_);
    if (!status) epoch_ = context_.getFrameCount();
    return status ? status : api_->start(audio_);
}
::android::status_t PipeWireStream::pause() { return stub_ ? stub_->pause() : api_->pause(audio_); }
::android::status_t PipeWireStream::standby() {
    if (stub_) return stub_->standby();
    int status = pause();
    if (!status) status = api_->flush(audio_);
    if (!status) epoch_ = context_.getFrameCount();
    return status;
}
::android::status_t PipeWireStream::start() {
    if (stub_ && openBackend() == 0) {  // a sound server was started since this stream opened (the stack runs for days): play on it from here
        stub_.reset(); epoch_ = context_.getFrameCount();
        fprintf(stderr, "[audio] a sound server answers now: playback leaves the silent output\n");
        if (int status = api_->pause(audio_)) return status;
    }
    return stub_ ? stub_->start() : api_->start(audio_);
}
::android::status_t PipeWireStream::transfer(void* buffer, size_t frames, size_t* actual, int32_t* latency) {
    if (stub_) return stub_->transfer(buffer, frames, actual, latency);
    *actual = 0;
    ssize_t written = api_->write(audio_, buffer, frames);
    if (written == -EAGAIN) return ::android::OK;
    if (written < 0) return written;
    *actual = written;
    struct apklayer_audio_position position;
    int status = api_->position(audio_, &position);
    if (!status) *latency = std::min<uint64_t>(std::numeric_limits<int32_t>::max(),
        (position.pending_frames * 1000 + context_.getSampleRate() - 1) / context_.getSampleRate());
    return status;
}
::android::status_t PipeWireStream::refinePosition(StreamDescriptor::Position* out) {
    if (stub_) return ::android::OK;  // the worker's own frame count, as for AOSP's stub
    struct apklayer_audio_position position;
    int status = api_->position(audio_, &position);
    if (!status) { out->frames = epoch_ + position.frames; out->timeNs = position.timestamp_ns; }
    return status;
}
void PipeWireStream::shutdown() {
    if (stub_) return stub_->shutdown();
    api_->close(audio_); audio_ = nullptr;
}
::android::status_t PipeWireInStream::init(DriverCallbackInterface*) {
    const auto format = context_.getFormat();
    if (context_.getAsyncCallback() || context_.isMmap() || format.type != AudioFormatType::PCM ||
        (format.pcm != PcmType::INT_16_BIT && format.pcm != PcmType::FLOAT_32_BIT))
        return ::android::INVALID_OPERATION;
    config_ = {static_cast<uint32_t>(context_.getSampleRate()), static_cast<uint32_t>(getChannelCount(context_.getChannelLayout())),
        format.pcm == PcmType::INT_16_BIT ? APKLAYER_AUDIO_S16 : APKLAYER_AUDIO_F32, apklayer::hostGetenv("APKLAYER_AUDIO_TARGET_IN")};
    int status = openBackend();
    if (status && silentFallback(status, true)) return stub_.emplace(context_).init(nullptr);
    if (status == 0) status = api_->capturePause(capture_);
    return status;
}
::android::status_t PipeWireInStream::flush() { return stub_ ? stub_->flush() : api_->captureFlush(capture_); }
::android::status_t PipeWireInStream::pause() { return stub_ ? stub_->pause() : api_->capturePause(capture_); }
::android::status_t PipeWireInStream::standby() {
    if (stub_) return stub_->standby();
    int status = pause();
    if (!status) status = api_->captureFlush(capture_);
    return status;
}
::android::status_t PipeWireInStream::start() {
    if (stub_ && openBackend() == 0) {
        stub_.reset(); epoch_ = context_.getFrameCount();
        fprintf(stderr, "[audio] a sound server answers now: recording leaves the silent input\n");
        if (int status = api_->capturePause(capture_)) return status;
    }
    return stub_ ? stub_->start() : api_->captureStart(capture_);
}
::android::status_t PipeWireInStream::transfer(void* buffer, size_t frames, size_t* actual, int32_t* latency) {
    if (stub_) {  // the stub paces like a microphone but fills noise: silence instead
        auto status = stub_->transfer(buffer, frames, actual, latency);
        memset(buffer, 0, frames * context_.getFrameSize());
        return status;
    }
    *actual = 0;
    ssize_t read = api_->captureRead(capture_, buffer, frames, 100000000);  // 100 ms: the worker's cycle must not stall
    if (read == -EAGAIN) return ::android::OK;
    if (read < 0) return read;
    *actual = read;
    if (gMicMute) memset(buffer, 0, read * context_.getFrameSize());
    struct apklayer_audio_position position;
    if (!api_->capturePosition(capture_, &position))
        *latency = std::min<uint64_t>(std::numeric_limits<int32_t>::max(), (position.pending_frames * 1000) / context_.getSampleRate());
    return ::android::OK;
}
::android::status_t PipeWireInStream::refinePosition(StreamDescriptor::Position* out) {
    if (stub_) return ::android::OK;
    struct apklayer_audio_position position;
    int status = api_->capturePosition(capture_, &position);
    if (!status) { out->frames = epoch_ + position.frames; out->timeNs = position.timestamp_ns; }
    return status;
}
void PipeWireInStream::shutdown() {
    if (stub_) return stub_->shutdown();
    api_->captureClose(capture_); capture_ = nullptr;
}
}
