#pragma once
#include <apklayer/audio.h>
#include <core-impl/Stream.h>

namespace aidl::android::hardware::audio::core {
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
    apklayer_audio* audio_ = nullptr;
    int64_t epoch_ = 0;
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
}
