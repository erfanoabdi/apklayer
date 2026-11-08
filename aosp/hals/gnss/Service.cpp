// apklayer's GNSS HAL: AOSP's IGnss example (hardware/interfaces/gnss/aidl/default, built from its own sources: patch 0002) with its fixes
// from the instance's backend (bridge/hals/gnss: libapklayer-gnss-<backend>.so, providers.toml). The example keeps the session, the
// status, satellite and NMEA reports and the reporting interval; getLocationFromHW (its /dev/gnss0 reader) asks the backend instead.
// A client that dies without close() (the hub ends its GNSS client with a signal) is closed for it, as the framework's restart would: the
// example kept reporting to the dead callback, and a later client's setCallback raced its thread (it crashed once after a client died).
#include "Gnss.h"
#include <apklayer/gnss.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <android-base/logging.h>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <mutex>

int gnssClient(int fixes);
// weak: the client runs this library without a backend loaded (bionic's linker binds every symbol at load)
#pragma weak apklayer_gnss_init
#pragma weak apklayer_gnss_next_fix

namespace aidl::android::hardware::gnss {
namespace {
class BackendGnss final : public Gnss {
  public:
    ndk::ScopedAStatus setCallback(const std::shared_ptr<IGnssCallback>& callback) override {
        std::lock_guard hold(mSession);
        auto status = Gnss::setCallback(callback);
        mClient = status.isOk() && callback ? callback->asBinder().get() : nullptr;
        if (mClient && AIBinder_isRemote(mClient)) AIBinder_linkToDeath(mClient, mDeath.get(), new Death{this, mClient});
        return status;
    }
    ndk::ScopedAStatus close() override {
        std::lock_guard hold(mSession);
        mClient = nullptr;
        return Gnss::close();
    }

  private:
    struct Death { BackendGnss* gnss; AIBinder* client; };
    static void onDied(void* cookie) {
        auto* death = static_cast<Death*>(cookie);
        BackendGnss* self = death->gnss;
        std::lock_guard hold(self->mSession);
        if (self->mClient != death->client) return;  // an earlier client: the session is a newer one's
        fprintf(stderr, "gnss: the client died without close(): session closed\n");  // the stack keeps stderr in gnss.log
        self->mClient = nullptr;
        self->stop();
        self->Gnss::close();
    }
    static void onUnlinked(void* cookie) { delete static_cast<Death*>(cookie); }
    static AIBinder_DeathRecipient* recipient() {
        AIBinder_DeathRecipient* r = AIBinder_DeathRecipient_new(onDied);
        AIBinder_DeathRecipient_setOnUnlinked(r, onUnlinked);
        return r;
    }
    std::mutex mSession;  // the example's setCallback and close join its reporting thread: one at a time, a death included
    AIBinder* mClient = nullptr;  // the current session's callback binder (identity only; the example holds the reference)
    ndk::ScopedAIBinder_DeathRecipient mDeath{recipient()};

  protected:
    bool reportsMockLocation() const override { return false; }  // a backend without a fix yet (-EAGAIN): no report (patch 0004)
    std::unique_ptr<GnssLocation> getLocationFromHW() override {
        apklayer_gnss_fix fix{};
        if (apklayer_gnss_next_fix(&fix)) return nullptr;
        auto location = std::make_unique<GnssLocation>(::android::hardware::gnss::common::Utils::getMockLocation());  // flags and clocks
        location->latitudeDegrees = fix.latitude;
        location->longitudeDegrees = fix.longitude;
        location->altitudeMeters = fix.altitude;
        location->speedMetersPerSec = fix.speed;
        location->bearingDegrees = fix.bearing;
        location->horizontalAccuracyMeters = fix.accuracy;
        location->timestampMillis = static_cast<int64_t>(time(nullptr)) * 1000;  // UTC, as a receiver reports it
        return location;
    }
};
}  // namespace
}  // namespace aidl::android::hardware::gnss

extern "C" int main(int argc, char** argv) {
    using namespace aidl::android::hardware::gnss;
    setvbuf(stdout, nullptr, _IOLBF, 0);
    setvbuf(stderr, nullptr, _IOLBF, 0);
    ABinderProcess_setThreadPoolMaxThreadCount(1);
    ABinderProcess_startThreadPool();
    // `--client [N]`: a client of the HAL printing each fix (`location LAT LON ACCURACY`; N fixes, then PASS; 0 = until killed)
    if (argc >= 2 && !std::strcmp(argv[1], "--client")) return gnssClient(argc == 3 ? atoi(argv[2]) : 0);
    if (!apklayer_gnss_init) { fprintf(stderr, "gnss: no backend library (APKLAYER_HOST_LIBS)\n"); return 1; }
    if (int error = apklayer_gnss_init(argc - 1, argv + 1)) { fprintf(stderr, "gnss backend: %s\n", strerror(-error)); return 1; }
    auto gnss = ndk::SharedRefBase::make<BackendGnss>();
    // the example makes these only when a client asks (the framework's GnssNative does, first) but uses them unchecked from setCallback/start:
    // made here, so any client order works
    std::shared_ptr<IGnssConfiguration> configuration;
    std::shared_ptr<IGnssPowerIndication> power;
    std::shared_ptr<IGnssMeasurementInterface> measurement;
    CHECK(gnss->getExtensionGnssConfiguration(&configuration).isOk() && gnss->getExtensionGnssPowerIndication(&power).isOk() &&
          gnss->getExtensionGnssMeasurement(&measurement).isOk());
    CHECK_EQ(AServiceManager_addService(gnss->asBinder().get(), (std::string(Gnss::descriptor) + "/default").c_str()), STATUS_OK);
    ABinderProcess_joinThreadPool();
    return 1;
}
