// apklayer's sensors HAL: AOSP's ISensors example (hardware/interfaces/sensors/aidl/default, built from its own sources: patch 0002) with the
// example sensors replaced by the instance backend's (bridge/hals/sensors: libapklayer-sensors-<backend>.so, providers.toml). The example
// keeps the FMQ, batching, flush and injection; a sensor's value comes from the backend each time the example samples it.
#include <sensors-impl/Sensors.h>
#include <apklayer/sensors.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <android-base/logging.h>
#include <cstdio>
#include <cstring>

int sensorsProbe();

namespace aidl::android::hardware::sensors {
namespace {
template <class Base>  // Sensor (continuous) or OnChangeSensor
class BackendSensor final : public Base {
  public:
    BackendSensor(int32_t handle, ISensorsEventCallback* callback, int index, const apklayer_sensor_info& info) : Base(callback), mIndex(index) {
        SensorInfo& s = this->mSensorInfo;
        s.sensorHandle = handle;
        s.name = info.name;
        s.vendor = info.vendor;
        s.version = 1;
        s.type = static_cast<SensorType>(info.type);
        s.maxRange = info.max_range;
        s.resolution = info.resolution;
        s.power = info.power_ma;
        s.minDelayUs = info.min_delay_us;
        s.maxDelayUs = 10 * 1000 * 1000;  // the example's
        s.fifoReservedEventCount = s.fifoMaxEventCount = 0;
        s.flags = info.on_change ? static_cast<uint32_t>(SensorInfo::SENSOR_FLAG_BITS_ON_CHANGE_MODE) : 0;
    }

  protected:
    void readEventPayload(Sensor::EventPayload& payload) override {
        float v[3] = {};
        if (apklayer_sensors_read(mIndex, v) == 3)
            payload.set<Sensor::EventPayload::Tag::vec3>(Sensor::EventPayload::Vec3{v[0], v[1], v[2], SensorStatus::ACCURACY_HIGH});
        else
            payload.set<Sensor::EventPayload::Tag::scalar>(v[0]);
    }

  private:
    int mIndex;
};

class BackendSensors final : public Sensors {
  public:
    BackendSensors() {
        mSensors.clear();  // the example's own sensors (their threads end here)
        mNextHandle = 1;
        apklayer_sensor_info infos[32];
        int n = apklayer_sensors_list(infos, 32);
        for (int i = 0; i < n; i++) {
            std::shared_ptr<Sensor> sensor;
            if (infos[i].on_change) sensor = std::make_shared<BackendSensor<OnChangeSensor>>(mNextHandle, this, i, infos[i]);
            else sensor = std::make_shared<BackendSensor<Sensor>>(mNextHandle, this, i, infos[i]);
            mSensors[mNextHandle++] = sensor;
            fprintf(stderr, "sensors: %s (type %d)\n", infos[i].name, infos[i].type);
        }
    }
};
}  // namespace
}  // namespace aidl::android::hardware::sensors

extern "C" int main(int argc, char** argv) {
    using namespace aidl::android::hardware::sensors;
    setvbuf(stdout, nullptr, _IOLBF, 0);
    setvbuf(stderr, nullptr, _IOLBF, 0);
    if (argc == 2 && !std::strcmp(argv[1], "--probe")) return sensorsProbe();
    if (int error = apklayer_sensors_init(argc - 1, argv + 1)) { fprintf(stderr, "sensors backend: %s\n", strerror(-error)); return 1; }
    ABinderProcess_setThreadPoolMaxThreadCount(0);
    auto sensors = ndk::SharedRefBase::make<BackendSensors>();
    CHECK_EQ(AServiceManager_addService(sensors->asBinder().get(), (std::string(Sensors::descriptor) + "/default").c_str()), STATUS_OK);
    ABinderProcess_joinThreadPool();
    return 1;
}
