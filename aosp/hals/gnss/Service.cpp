// apklayer's GNSS HAL: AOSP's IGnss example (hardware/interfaces/gnss/aidl/default, built from its own sources: patch 0002) with its fixes
// from the instance's backend (bridge/hals/gnss: libapklayer-gnss-<backend>.so, providers.toml). The example keeps the session, the
// status, satellite and NMEA reports and the reporting interval; getLocationFromHW (its /dev/gnss0 reader) asks the backend instead.
#include "Gnss.h"
#include <apklayer/gnss.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <android-base/logging.h>
#include <cstdio>
#include <cstdlib>
#include <cstring>

int gnssClient(int fixes);
// weak: the client runs this library without a backend loaded (bionic's linker binds every symbol at load)
#pragma weak apklayer_gnss_init
#pragma weak apklayer_gnss_next_fix

namespace aidl::android::hardware::gnss {
namespace {
class BackendGnss final : public Gnss {
  protected:
    // shortcut: a backend without a fix yet (-EAGAIN) gets the example's mock location reported; a real backend needs the example to skip the report
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
    CHECK_EQ(AServiceManager_addService(gnss->asBinder().get(), (std::string(Gnss::descriptor) + "/default").c_str()), STATUS_OK);
    ABinderProcess_joinThreadPool();
    return 1;
}
