// Reuse AOSP Module/Stream plumbing and XML conversion; only the host driver is new.
#include "Stream.h"
#include <core-impl/AudioPolicyConfigXmlConverter.h>
#include <core-impl/Config.h>
#include <core-impl/Module.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <cstdio>
#include <cstring>
int audioProbe();

namespace aidl::android::hardware::audio::core {
class PipeWireModule final : public Module {
  public:
    explicit PipeWireModule(std::unique_ptr<Configuration>&& config)
        : Module(Type::DEFAULT, std::move(config)) {}
  private:
    ndk::ScopedAStatus createInputStream(StreamContext&&,
        const ::aidl::android::hardware::audio::common::SinkMetadata&,
        const std::vector<::aidl::android::media::audio::common::MicrophoneInfo>&,
        std::shared_ptr<StreamIn>*) override {
        return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
    }
    ndk::ScopedAStatus createOutputStream(StreamContext&& context,
        const ::aidl::android::hardware::audio::common::SourceMetadata& metadata,
        const std::optional<::aidl::android::media::audio::common::AudioOffloadInfo>& offload,
        std::shared_ptr<StreamOut>* result) override {
        if (offload || context.getAsyncCallback() || context.isMmap())
            return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
        return createStreamInstance<PipeWireOutput>(result, std::move(context), metadata, offload);
    }
    // Let AudioFlinger apply software master controls instead of acknowledging no-ops.
    ndk::ScopedAStatus onMasterVolumeChanged(float) override {
        return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
    }
    ndk::ScopedAStatus onMasterMuteChanged(bool) override {
        return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
    }
};
}
extern "C" int main(int argc, char** argv) {
    using namespace aidl::android::hardware::audio::core;
    if (argc == 2 && !std::strcmp(argv[1], "--probe")) return audioProbe();
    if (argc != 2) { fprintf(stderr, "usage: audio-hal policy.xml\n"); return 2; }
    internal::AudioPolicyConfigXmlConverter converter(argv[1]);
    if (converter.getStatus() != ::android::OK) {
        fprintf(stderr, "audio policy: %s\n", converter.getError().c_str()); return 1;
    }
    auto configs = converter.releaseModuleConfigs();
    if (configs->size() != 1 || configs->at(0).first != "default") {
        fprintf(stderr, "audio policy must provide one primary module\n"); return 1;
    }
    auto module = ndk::SharedRefBase::make<PipeWireModule>(std::move(configs->at(0).second));
    auto config = ndk::SharedRefBase::make<Config>(converter);
    ABinderProcess_setThreadPoolMaxThreadCount(4);
    ABinderProcess_startThreadPool();
    if (AServiceManager_addService(module->asBinder().get(),
            "android.hardware.audio.core.IModule/default") != STATUS_OK ||
        AServiceManager_addService(config->asBinder().get(),
            "android.hardware.audio.core.IConfig/default") != STATUS_OK) return 1;
    fprintf(stderr, "apklayer PipeWire audio AIDL ready\n");
    ABinderProcess_joinThreadPool();
    return 1;
}
