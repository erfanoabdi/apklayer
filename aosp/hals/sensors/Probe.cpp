// `--probe`: the sensors HAL over remote binder and its event FMQ, as SensorService (SensorDevice) uses it: the list, then the
// accelerometer at 50 Hz for one second. Prints the count and the last value (the test compares them with the backend's options).
#include <aidl/android/hardware/sensors/BnSensorsCallback.h>
#include <aidl/android/hardware/sensors/ISensors.h>
#include <android/binder_manager.h>
#include <android-base/logging.h>
#include <fmq/AidlMessageQueue.h>
#include <fmq/EventFlag.h>
#include <chrono>
#include <cstdio>

using namespace aidl::android::hardware::sensors;
using aidl::android::hardware::common::fmq::SynchronizedReadWrite;

namespace {
struct Callback : public BnSensorsCallback {
    ndk::ScopedAStatus onDynamicSensorsConnected(const std::vector<SensorInfo>&) override { return ndk::ScopedAStatus::ok(); }
    ndk::ScopedAStatus onDynamicSensorsDisconnected(const std::vector<int32_t>&) override { return ndk::ScopedAStatus::ok(); }
};
}  // namespace

int sensorsProbe() {
    auto sensors = ISensors::fromBinder(ndk::SpAIBinder(AServiceManager_waitForService("android.hardware.sensors.ISensors/default")));
    CHECK(sensors && AIBinder_isRemote(sensors->asBinder().get()));
    ::android::AidlMessageQueue<Event, SynchronizedReadWrite> events(256, true);
    ::android::AidlMessageQueue<int32_t, SynchronizedReadWrite> wakeLocks(256, true);
    CHECK(sensors->initialize(events.dupeDesc(), wakeLocks.dupeDesc(), ndk::SharedRefBase::make<Callback>()).isOk());
    std::vector<SensorInfo> list;
    CHECK(sensors->getSensorsList(&list).isOk());
    int accel = -1;
    for (const SensorInfo& s : list) {
        printf("sensor %d type %d %s\n", s.sensorHandle, static_cast<int>(s.type), s.name.c_str());
        if (s.type == SensorType::ACCELEROMETER) accel = s.sensorHandle;
    }
    CHECK(accel > 0);
    ::android::hardware::EventFlag* flag = nullptr;
    CHECK(::android::hardware::EventFlag::createEventFlag(events.getEventFlagWord(), &flag) == ::android::OK);
    CHECK(sensors->batch(accel, 20 * 1000 * 1000, 0).isOk());
    CHECK(sensors->activate(accel, true).isOk());
    int count = 0;
    Event last;
    auto start = std::chrono::steady_clock::now();
    while (std::chrono::steady_clock::now() - start < std::chrono::seconds(1)) {
        uint32_t bits = 0;
        flag->wait(static_cast<uint32_t>(ISensors::EVENT_QUEUE_FLAG_BITS_READ_AND_PROCESS), &bits, 100 * 1000 * 1000);
        Event buffer[64];
        size_t n = std::min(events.availableToRead(), sizeof buffer / sizeof *buffer);
        if (n == 0 || !events.read(buffer, n)) continue;
        for (size_t i = 0; i < n; i++)
            if (buffer[i].sensorHandle == accel && buffer[i].payload.getTag() == Event::EventPayload::Tag::vec3) { count++; last = buffer[i]; }
    }
    CHECK(sensors->activate(accel, false).isOk());
    ::android::hardware::EventFlag::deleteEventFlag(&flag);
    CHECK(count > 0);
    const auto& v = last.payload.get<Event::EventPayload::Tag::vec3>();
    printf("accelerometer: %d events in 1 s at a 20 ms period, last %.3f %.3f %.3f\n", count, v.x, v.y, v.z);
    CHECK(count >= 35 && count <= 60);  // 50 expected; the example samples on its own thread
    printf("PASS sensors AIDL: %zu sensors, accelerometer events over the FMQ at the batched rate\n", list.size());
    return 0;
}
