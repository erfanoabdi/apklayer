// `--client [N]`: the GNSS HAL over remote binder as the framework's GnssNative uses it (callback, position mode, start), printing
// `ready`, then `status N` and `location LAT LON ACCURACY` lines: the protocol apklayer.host.HostLocation reads (as from bridge/portal/geoclue.py).
// With N > 0 it ends after N fixes with a PASS line (the test probe).
#include <aidl/android/hardware/gnss/BnGnssCallback.h>
#include <aidl/android/hardware/gnss/IGnss.h>
#include <android/binder_manager.h>
#include <android-base/logging.h>
#include <sys/prctl.h>
#include <condition_variable>
#include <csignal>
#include <cstdio>
#include <mutex>

using namespace aidl::android::hardware::gnss;
using ndk::ScopedAStatus;

namespace {
struct Callback : public BnGnssCallback {
    std::mutex lock;
    std::condition_variable changed;
    int fixes = 0;
    ScopedAStatus gnssSetCapabilitiesCb(int32_t) override { return ScopedAStatus::ok(); }
    ScopedAStatus gnssStatusCb(GnssStatusValue status) override { printf("status %d\n", static_cast<int>(status)); return ScopedAStatus::ok(); }
    ScopedAStatus gnssSvStatusCb(const std::vector<GnssSvInfo>&) override { return ScopedAStatus::ok(); }
    ScopedAStatus gnssLocationCb(const GnssLocation& l) override {
        printf("location %.7f %.7f %.1f\n", l.latitudeDegrees, l.longitudeDegrees, l.horizontalAccuracyMeters);
        std::lock_guard<std::mutex> hold(lock);
        fixes++;
        changed.notify_all();
        return ScopedAStatus::ok();
    }
    ScopedAStatus gnssNmeaCb(int64_t, const std::string&) override { return ScopedAStatus::ok(); }
    ScopedAStatus gnssAcquireWakelockCb() override { return ScopedAStatus::ok(); }
    ScopedAStatus gnssReleaseWakelockCb() override { return ScopedAStatus::ok(); }
    ScopedAStatus gnssSetSystemInfoCb(const GnssSystemInfo&) override { return ScopedAStatus::ok(); }
    ScopedAStatus gnssRequestTimeCb() override { return ScopedAStatus::ok(); }
    ScopedAStatus gnssRequestLocationCb(bool, bool) override { return ScopedAStatus::ok(); }
    ScopedAStatus gnssSetSignalTypeCapabilitiesCb(const std::vector<GnssSignalType>&) override { return ScopedAStatus::ok(); }
};
}  // namespace

int gnssClient(int fixes) {
    prctl(PR_SET_PDEATHSIG, SIGTERM);  // ends with its reader (an app JVM through `apklayer exec`)
    auto callback = ndk::SharedRefBase::make<Callback>();
    std::shared_ptr<IGnss> gnss;
    bool connected = false;
    for (int attempt = 0; attempt < 2 && !connected; attempt++) {  // a HAL restarting (providers set) answers DEAD_OBJECT once: look it up again
        gnss = IGnss::fromBinder(ndk::SpAIBinder(AServiceManager_waitForService("android.hardware.gnss.IGnss/default")));
        connected = gnss && gnss->setCallback(callback).isOk();
    }
    CHECK(connected && AIBinder_isRemote(gnss->asBinder().get()));
    // the framework's GnssNative takes the extensions before it starts: the example's start() uses the measurement interface it makes then
    std::shared_ptr<IGnssMeasurementInterface> measurement;
    CHECK(gnss->getExtensionGnssMeasurement(&measurement).isOk());
    IGnss::PositionModeOptions mode;
    mode.mode = IGnss::GnssPositionMode::STANDALONE;
    mode.recurrence = IGnss::GnssPositionRecurrence::RECURRENCE_PERIODIC;
    mode.minIntervalMs = 1000;
    CHECK(gnss->setPositionMode(mode).isOk());
    CHECK(gnss->start().isOk());
    puts("ready");
    std::unique_lock<std::mutex> hold(callback->lock);
    if (fixes <= 0) for (;;) callback->changed.wait(hold);
    bool got = callback->changed.wait_for(hold, std::chrono::seconds(10 + 2 * fixes), [&] { return callback->fixes >= fixes; });
    hold.unlock();
    gnss->stop();
    gnss->close();
    CHECK(got);
    printf("PASS gnss AIDL: %d fixes through IGnssCallback\n", fixes);
    return 0;
}
