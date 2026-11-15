#pragma once
#include <apklayer/audio.h>
#include <core-impl/DriverStubImpl.h>
#include <core-impl/Stream.h>
#include <atomic>
#include <optional>

namespace aidl::android::hardware::audio::core {
extern std::atomic<bool> gMicMute;  // IModule.setMicMute (Service.cpp): capture streams deliver silence
// No sound server answers (the open fails): the stream runs on AOSP's DriverStubImpl (the emulator's stub), discarding output and
// delivering silent input at real time, so apps still see a working device (owner's decision 2026-11-08; logged once per process).
bool silentFallback(int status, bool input);
// One backend's functions (bridge/hals/audio): the library the stack was started with is linked; when no sound server answers it, a stream tries the
// other backend (PipeWire <-> PulseAudio, dlopen from the sibling file of APKLAYER_HOST_LIBS' entry), so a server started later is used by the
// next stream, not the next stack start. A stream keeps the table it opened with.
struct AudioApi {
    int (*probe)();  // < 0: no sound server answers it here (a cheap check; an open against a missing PulseAudio server can block for 30 s)
    int (*open)(const apklayer_audio_config*, apklayer_audio**);
    ssize_t (*write)(apklayer_audio*, const void*, size_t);
    int (*position)(apklayer_audio*, struct apklayer_audio_position*);
    int (*pause)(apklayer_audio*);
    int (*start)(apklayer_audio*);
    int (*flush)(apklayer_audio*);
    int (*drain)(apklayer_audio*);
    void (*close)(apklayer_audio*);
    int (*captureOpen)(const apklayer_audio_config*, apklayer_capture**);
    ssize_t (*captureRead)(apklayer_capture*, void*, size_t, uint64_t);
    int (*capturePosition)(apklayer_capture*, struct apklayer_audio_position*);
    int (*capturePause)(apklayer_capture*);
    int (*captureStart)(apklayer_capture*);
    int (*captureFlush)(apklayer_capture*);
    void (*captureClose)(apklayer_capture*);
};
const AudioApi& linkedAudioApi();
const AudioApi* siblingAudioApi();  // the other backend's table, nullptr when it is not staged or does not load
// The transfer thread runs unprivileged; PipeWire owns graph scheduling.
class HostOutputWorker final : public StreamOutWorker {
  public:
    HostOutputWorker(StreamContext* context, DriverInterface* driver) : StreamOutWorker(context, driver) {}
    bool start() override {
        return ::android::hardware::audio::common::StreamWorker<StreamOutWorkerLogic>::start(
            StreamOutWorkerLogic::kThreadName, ANDROID_PRIORITY_DEFAULT);
    }
};
// AOSP owns FMQs, commands, state transitions and worker lifetime.
class PipeWireStream : public StreamCommonImpl {
  public:
    PipeWireStream(StreamContext* context, const Metadata& metadata)
        : StreamCommonImpl(context, metadata, [](StreamContext* ctx, DriverInterface* driver) {
            return new HostOutputWorker(ctx, driver);
        }), context_(*context) {}
    ~PipeWireStream() override { cleanupWorker(); }
    ::android::status_t init(DriverCallbackInterface*) override;
    ::android::status_t drain(StreamDescriptor::DrainMode) override;
    ::android::status_t flush() override;
    ::android::status_t pause() override;
    ::android::status_t standby() override;
    ::android::status_t start() override;
    ::android::status_t transfer(void*, size_t, size_t*, int32_t*) override;
    ::android::status_t refinePosition(StreamDescriptor::Position*) override;
    void shutdown() override;
  private:
    const StreamContext& context_;
    const AudioApi* api_ = &linkedAudioApi();
    int openBackend();  // the linked backend, else the other one; api_ is the one that answered
    apklayer_audio_config config_{};
    apklayer_audio* audio_ = nullptr;
    int64_t epoch_ = 0;
    std::optional<DriverStubImpl> stub_;
};
class PipeWireOutput final : public StreamOut, public PipeWireStream {
  public:
    PipeWireOutput(StreamContext&& context,
                  const ::aidl::android::hardware::audio::common::SourceMetadata& metadata,
                  const std::optional<::aidl::android::media::audio::common::AudioOffloadInfo>& offload)
        : StreamOut(std::move(context), offload), PipeWireStream(&mContextInstance, metadata) {}
  private:
    void onClose(StreamDescriptor::State) override { defaultOnClose(); }
};
// Input: the same AOSP stream plumbing over the host's capture (microphone, or a sink's monitor for tests).
class HostInputWorker final : public StreamInWorker {
  public:
    HostInputWorker(StreamContext* context, DriverInterface* driver) : StreamInWorker(context, driver) {}
    bool start() override {
        return ::android::hardware::audio::common::StreamWorker<StreamInWorkerLogic>::start(
            StreamInWorkerLogic::kThreadName, ANDROID_PRIORITY_DEFAULT);
    }
};
class PipeWireInStream : public StreamCommonImpl {
  public:
    PipeWireInStream(StreamContext* context, const Metadata& metadata)
        : StreamCommonImpl(context, metadata, [](StreamContext* ctx, DriverInterface* driver) {
            return new HostInputWorker(ctx, driver);
        }), context_(*context) {}
    ~PipeWireInStream() override { cleanupWorker(); }
    ::android::status_t init(DriverCallbackInterface*) override;
    ::android::status_t drain(StreamDescriptor::DrainMode mode) override { return stub_ ? stub_->drain(mode) : ::android::INVALID_OPERATION; }
    ::android::status_t flush() override;
    ::android::status_t pause() override;
    ::android::status_t standby() override;
    ::android::status_t start() override;
    ::android::status_t transfer(void*, size_t, size_t*, int32_t*) override;
    ::android::status_t refinePosition(StreamDescriptor::Position*) override;
    void shutdown() override;
  private:
    const StreamContext& context_;
    const AudioApi* api_ = &linkedAudioApi();
    int openBackend();
    apklayer_audio_config config_{};
    apklayer_capture* capture_ = nullptr;
    int64_t epoch_ = 0;
    std::optional<DriverStubImpl> stub_;
};
class PipeWireInput final : public StreamIn, public PipeWireInStream {
  public:
    PipeWireInput(StreamContext&& context, const ::aidl::android::hardware::audio::common::SinkMetadata& metadata,
                  const std::vector<::aidl::android::media::audio::common::MicrophoneInfo>& microphones)
        : StreamIn(std::move(context), microphones), PipeWireInStream(&mContextInstance, metadata) {}
  private:
    void onClose(StreamDescriptor::State) override { defaultOnClose(); }
    // the host owns the gain (the Android volume is pinned at maximum): no hardware gain here
    ndk::ScopedAStatus getHwGain(std::vector<float>*) override { return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION); }
    ndk::ScopedAStatus setHwGain(const std::vector<float>&) override { return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION); }
};
}
