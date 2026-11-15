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
#include <condition_variable>
#include <mutex>
#include <thread>
#include <vector>

// weak: the probe runs this library without a backend loaded (bionic's linker binds every symbol at load)
#pragma weak apklayer_vibrator_init
#pragma weak apklayer_vibrator_on
#pragma weak apklayer_vibrator_off
#pragma weak apklayer_vibrator_amplitude
#pragma weak apklayer_vibrator_compose

namespace aidl::android::hardware::vibrator {
namespace {
// A composition is played as timed steps (delay from the start, length, amplitude) through the backend's on and amplitude; a backend that
// can change amplitude between steps says so with apklayer_vibrator_compose() (vibrator.h), and only then the HAL reports the compose capabilities.
struct Step { int64_t atMs; int32_t ms; float amplitude; };

class BackendVibrator final : public Vibrator {
  public:
    ndk::ScopedAStatus on(int32_t timeoutMs, const std::shared_ptr<IVibratorCallback>& callback) override {
        cancelSteps();
        apklayer_vibrator_on(timeoutMs);
        return Vibrator::on(timeoutMs, callback);
    }
    ndk::ScopedAStatus off() override {
        cancelSteps();
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
        cancelSteps();
        auto status = Vibrator::perform(effect, strength, callback, durationMs);
        if (status.isOk()) apklayer_vibrator_on(*durationMs);
        return status;
    }
    ndk::ScopedAStatus getCapabilities(int32_t* caps) override {
        auto status = Vibrator::getCapabilities(caps);
        if (!status.isOk()) return status;
        *caps &= ~IVibrator::CAP_COMPOSE_PWLE_EFFECTS_V2;  // not played
        if (!composes()) *caps &= ~(IVibrator::CAP_COMPOSE_EFFECTS | IVibrator::CAP_COMPOSE_PWLE_EFFECTS);
        return status;
    }
    ndk::ScopedAStatus compose(const std::vector<CompositeEffect>& composite, const std::shared_ptr<IVibratorCallback>& callback) override {
        if (!composes()) return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
        auto status = Vibrator::compose(composite, callback);  // validates and runs the completion callback
        if (!status.isOk()) return status;
        std::vector<Step> steps;
        int64_t at = 0;
        for (auto& e : composite) {
            int32_t ms = 0;
            getPrimitiveDuration(e.primitive, &ms);
            at += e.delayMs;
            if (ms > 0 && e.scale > 0) steps.push_back({at, ms, e.scale});
            at += ms;
        }
        playSteps(std::move(steps));
        return status;
    }
    ndk::ScopedAStatus composePwle(const std::vector<PrimitivePwle>& composite, const std::shared_ptr<IVibratorCallback>& callback) override {
        if (!composes()) return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
        auto status = Vibrator::composePwle(composite, callback);
        if (!status.isOk()) return status;
        // shortcut: the frequency is ignored; an active segment is 20 ms steps along its amplitude ramp, a braking segment is silence
        std::vector<Step> steps;
        int64_t at = 0;
        for (auto& e : composite) {
            if (e.getTag() == PrimitivePwle::braking) { at += e.get<PrimitivePwle::braking>().duration; continue; }
            auto a = e.get<PrimitivePwle::active>();
            int n = std::max(1, a.duration / 20), slice = a.duration / n;
            for (int i = 0; i < n && a.duration > 0; i++) {
                float amplitude = a.startAmplitude + (a.endAmplitude - a.startAmplitude) * (i + 0.5f) / n;
                if (amplitude > 0) steps.push_back({at + (int64_t)i * slice, i == n - 1 ? a.duration - slice * (n - 1) : slice, std::min(amplitude, 1.0f)});
            }
            at += a.duration;
        }
        playSteps(std::move(steps));
        return status;
    }

  private:
    static bool composes() { return apklayer_vibrator_compose && apklayer_vibrator_compose(); }
    void cancelSteps() {
        { std::lock_guard<std::mutex> lock(mStepMutex); mStepGeneration++; }
        mStepWake.notify_all();
    }
    void playSteps(std::vector<Step> steps) {
        uint64_t generation;
        { std::lock_guard<std::mutex> lock(mStepMutex); generation = ++mStepGeneration; }
        mStepWake.notify_all();
        std::thread([this, steps = std::move(steps), generation] {
            auto start = std::chrono::steady_clock::now();
            for (auto& step : steps) {
                std::unique_lock<std::mutex> lock(mStepMutex);
                if (mStepWake.wait_until(lock, start + std::chrono::milliseconds(step.atMs), [&] { return mStepGeneration != generation; })) return;
                lock.unlock();
                apklayer_vibrator_amplitude(step.amplitude);
                apklayer_vibrator_on(step.ms);
            }
        }).detach();
    }
    std::mutex mStepMutex;
    std::condition_variable mStepWake;
    uint64_t mStepGeneration = 0;
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
    if (caps & IVibrator::CAP_COMPOSE_EFFECTS) {  // a backend that composes: two primitives (100 ms each, 50 ms apart), then a 60 ms PWLE ramp
        CHECK(caps & IVibrator::CAP_COMPOSE_PWLE_EFFECTS);
        auto composed = ndk::SharedRefBase::make<Done>();
        std::vector<CompositeEffect> effects(2);
        effects[0].primitive = CompositePrimitive::CLICK, effects[0].scale = 0.5f;
        effects[1].delayMs = 50, effects[1].primitive = CompositePrimitive::THUD, effects[1].scale = 1.0f;
        CHECK(vibrator->compose(effects, composed).isOk());
        for (int i = 0; i < 400 && !composed->done; i++) std::this_thread::sleep_for(std::chrono::milliseconds(5));
        CHECK(composed->done);
        float hz = 0;
        CHECK(vibrator->getFrequencyMinimum(&hz).isOk());
        ActivePwle ramp;
        ramp.startAmplitude = 0.2f, ramp.endAmplitude = 0.8f, ramp.startFrequency = ramp.endFrequency = hz, ramp.duration = 60;
        auto pwle = ndk::SharedRefBase::make<Done>();
        CHECK(vibrator->composePwle({PrimitivePwle::make<PrimitivePwle::active>(ramp)}, pwle).isOk());
        for (int i = 0; i < 400 && !pwle->done; i++) std::this_thread::sleep_for(std::chrono::milliseconds(5));
        CHECK(pwle->done);
        printf("PASS vibrator compose and PWLE\n");
    } else {
        std::vector<CompositeEffect> effects(1);
        effects[0].primitive = CompositePrimitive::CLICK, effects[0].scale = 1.0f;
        CHECK(!vibrator->compose(effects, nullptr).isOk());
        printf("PASS vibrator without compose\n");
    }
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
    if (!apklayer_vibrator_init) { fprintf(stderr, "vibrator: no backend library (APKLAYER_HOST_LIBS)\n"); return 1; }
    if (int error = apklayer_vibrator_init(argc - 1, argv + 1)) { fprintf(stderr, "vibrator backend: %s\n", strerror(-error)); return 1; }
    auto vibrator = ndk::SharedRefBase::make<BackendVibrator>();
    CHECK_EQ(AServiceManager_addService(vibrator->asBinder().get(), Vibrator::makeServiceName("default").c_str()), STATUS_OK);
    ABinderProcess_joinThreadPool();
    return 1;
}
