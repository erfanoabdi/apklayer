#include "Stream.h"
#include <aidl/android/media/audio/common/PcmType.h>
#include <Utils.h>
#include <cerrno>
#include <cstdlib>
#include <limits>

namespace aidl::android::hardware::audio::core {
using ::aidl::android::media::audio::common::AudioFormatType;
using ::aidl::android::media::audio::common::PcmType;
using ::aidl::android::hardware::audio::common::getChannelCount;
::android::status_t PipeWireStream::init(DriverCallbackInterface*) {
    const auto format = context_.getFormat();
    if (context_.getAsyncCallback() || context_.isMmap() || context_.isOffload() ||
        format.type != AudioFormatType::PCM ||
        (format.pcm != PcmType::INT_16_BIT && format.pcm != PcmType::FLOAT_32_BIT))
        return ::android::INVALID_OPERATION;
    apklayer_audio_config config{static_cast<uint32_t>(context_.getSampleRate()),
        static_cast<uint32_t>(getChannelCount(context_.getChannelLayout())),
        format.pcm == PcmType::INT_16_BIT ? APKLAYER_AUDIO_S16 : APKLAYER_AUDIO_F32,
        std::getenv("APKLAYER_AUDIO_TARGET")};
    int status = apklayer_audio_open(&config, &audio_);
    if (status == 0) status = apklayer_audio_pause(audio_);
    return status;
}
::android::status_t PipeWireStream::drain(StreamDescriptor::DrainMode mode) {
    return mode == StreamDescriptor::DrainMode::DRAIN_ALL ? apklayer_audio_drain(audio_)
                                                         : ::android::INVALID_OPERATION;
}
::android::status_t PipeWireStream::flush() {
    int status = apklayer_audio_flush(audio_);
    if (!status) epoch_ = context_.getFrameCount();
    return status ? status : apklayer_audio_start(audio_);
}
::android::status_t PipeWireStream::pause() { return apklayer_audio_pause(audio_); }
::android::status_t PipeWireStream::standby() {
    int status = pause();
    if (!status) status = apklayer_audio_flush(audio_);
    if (!status) epoch_ = context_.getFrameCount();
    return status;
}
::android::status_t PipeWireStream::start() { return apklayer_audio_start(audio_); }
::android::status_t PipeWireStream::transfer(void* buffer, size_t frames, size_t* actual, int32_t* latency) {
    *actual = 0;
    ssize_t written = apklayer_audio_write(audio_, buffer, frames);
    if (written == -EAGAIN) return ::android::OK;
    if (written < 0) return written;
    *actual = written;
    struct apklayer_audio_position position;
    int status = apklayer_audio_position(audio_, &position);
    if (!status) *latency = std::min<uint64_t>(std::numeric_limits<int32_t>::max(),
        (position.pending_frames * 1000 + context_.getSampleRate() - 1) / context_.getSampleRate());
    return status;
}
::android::status_t PipeWireStream::refinePosition(StreamDescriptor::Position* out) {
    struct apklayer_audio_position position;
    int status = apklayer_audio_position(audio_, &position);
    if (!status) { out->frames = epoch_ + position.frames; out->timeNs = position.timestamp_ns; }
    return status;
}
void PipeWireStream::shutdown() { apklayer_audio_close(audio_); audio_ = nullptr; }
}
