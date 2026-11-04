// apklayer's vibrator HAL: AOSP's IVibrator example (hardware/interfaces/vibrator/aidl/default, built from its own sources: patch 0002)
// with its motor commands also sent to the instance's backend (bridge/hals/vibrator: libapklayer-vibrator-<backend>.so, providers.toml).
// The example keeps the timing and the callbacks; the backend only moves (or logs) the motor.
#include <vibrator-impl/Vibrator.h>
#include <aidl/android/hardware/vibrator/BnVibratorCallback.h>
#include <apklayer/vibrator.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <android-base/logging.h>
#include <atomic>
#include <chrono>
#include <cstdio>
#include <cstring>
#include <thread>

namespace aidl::android::hardware::vibrator {
namespace {
class BackendVibrator final : public Vibrator {
  public:
    ndk::ScopedAStatus on(int32_t timeoutMs, const std::shared_ptr<IVibratorCallback>& callback) override {
        apklayer_vibrator_on(timeoutMs);
        return Vibrator::on(timeoutMs, callback);
    }
    ndk::ScopedAStatus off() override {
        apklayer_vibrator_off();
        return Vibrator::off();
    }
    ndk::ScopedAStatus setAmplitude(float amplitude) override {
        auto status = Vibrator::setAmplitude(amplitude);  // the example validates the range
        if (status.isOk()) apklayer_vibrator_amplitude(amplitude);
        return status;
    }
    ndk::ScopedAStatus perform(Effect effect, EffectStrength strength, const std::shared_ptr<IVibratorCallback>& callback,
                               int32_t* durationMs) override {
        auto status = Vibrator::perform(effect, strength, callback, durationMs);
        if (status.isOk()) apklayer_vibrator_on(*durationMs);
        return status;
    }
    // shortcut: compose/composePwle reach the example only (no backend motor command), until a backend can play primitives
};

// `--probe`: the HAL over remote binder as the framework uses it: capabilities, amplitude, a timed on with its completion callback, off
int probe() {
    auto vibrator = IVibrator::fromBinder(ndk::SpAIBinder(AServiceManager_waitForService("android.hardware.vibrator.IVibrator/default")));
    CHECK(vibrator && AIBinder_isRemote(vibrator->asBinder().get()));
    int32_t caps = 0;
    CHECK(vibrator->getCapabilities(&caps).isOk());
    CHECK(caps & IVibrator::CAP_ON_CALLBACK);
    CHECK(vibrator->setAmplitude(0.5f).isOk());
    struct Done : public BnVibratorCallback {
        std::atomic<bool> done{false};
        ndk::ScopedAStatus onComplete() override { done = true; return ndk::ScopedAStatus::ok(); }
    };
    auto done = ndk::SharedRefBase::make<Done>();
    auto start = std::chrono::steady_clock::now();
    CHECK(vibrator->on(120, done).isOk());
    while (!done->done && std::chrono::steady_clock::now() - start < std::chrono::seconds(2)) std::this_thread::sleep_for(std::chrono::milliseconds(5));
    long ms = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - start).count();
    CHECK(done->done && ms >= 120);
    CHECK(vibrator->off().isOk());
    printf("PASS vibrator AIDL capabilities 0x%x, on 120 ms completed after %ld ms\n", caps, ms);
    return 0;
}
}  // namespace
}  // namespace aidl::android::hardware::vibrator

extern "C" int main(int argc, char** argv) {
    using namespace aidl::android::hardware::vibrator;
    setvbuf(stdout, nullptr, _IOLBF, 0);
    setvbuf(stderr, nullptr, _IOLBF, 0);
    ABinderProcess_setThreadPoolMaxThreadCount(4);
    ABinderProcess_startThreadPool();
    if (argc == 2 && !std::strcmp(argv[1], "--probe")) return probe();
    if (int error = apklayer_vibrator_init(argc - 1, argv + 1)) { fprintf(stderr, "vibrator backend: %s\n", strerror(-error)); return 1; }
    auto vibrator = ndk::SharedRefBase::make<BackendVibrator>();
    CHECK_EQ(AServiceManager_addService(vibrator->asBinder().get(), Vibrator::makeServiceName("default").c_str()), STATUS_OK);
    ABinderProcess_joinThreadPool();
    return 1;
}
