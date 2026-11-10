// `--probe`: the sensors HAL as native apps reach it, through SensorService (the stack runs it beside the HAL: the HAL serves one client, so
// a probe of its own FMQ would take it from SensorService): the NDK's ASensorManager lists the sensors and reads the accelerometer at 50 Hz
// for one second on an ALooper. Prints the count and the last value (the test compares them with the backend's options).
// libandroid is opened here only: linked, it pulled ~230 more libraries (libandroid_runtime, hwui...) into the always-running HAL, ~85 MB of their
// static allocations (docs/history/PLAN-2026-11-10.md "app start speed on phones" 8).
#include <android/looper.h>
#include <android/sensor.h>
#include <android-base/logging.h>
#include <chrono>
#include <cstdio>
#include <dlfcn.h>

int sensorsProbe() {
    void* ndk = dlopen("libandroid.so", RTLD_NOW);
    CHECK(ndk) << dlerror();
#define NDK(f) auto f = reinterpret_cast<decltype(&::f)>(dlsym(ndk, #f)); CHECK(f) << #f
    NDK(ASensorManager_getInstanceForPackage); NDK(ASensorManager_getSensorList); NDK(ASensor_getType); NDK(ASensor_getName);
    NDK(ASensorManager_getDefaultSensor); NDK(ALooper_prepare); NDK(ASensorManager_createEventQueue); NDK(ASensorEventQueue_registerSensor);
    NDK(ALooper_pollOnce); NDK(ASensorEventQueue_getEvents); NDK(ASensorEventQueue_disableSensor); NDK(ASensorManager_destroyEventQueue);
#undef NDK
    ASensorManager* manager = ASensorManager_getInstanceForPackage("apklayer.sensors.probe");
    CHECK(manager);
    ASensorList list = nullptr;
    int n = ASensorManager_getSensorList(manager, &list);
    for (int i = 0; i < n; i++) fprintf(stderr, "sensor type %d %s\n", ASensor_getType(list[i]), ASensor_getName(list[i]));
    const ASensor* accel = ASensorManager_getDefaultSensor(manager, ASENSOR_TYPE_ACCELEROMETER);
    CHECK(accel);
    ALooper* looper = ALooper_prepare(ALOOPER_PREPARE_ALLOW_NON_CALLBACKS);
    ASensorEventQueue* queue = ASensorManager_createEventQueue(manager, looper, 1, nullptr, nullptr);
    CHECK(queue && ASensorEventQueue_registerSensor(queue, accel, 20000, 0) == 0);
    int count = 0;
    ASensorEvent last{};
    auto start = std::chrono::steady_clock::now();
    while (std::chrono::steady_clock::now() - start < std::chrono::seconds(1)) {
        ALooper_pollOnce(100, nullptr, nullptr, nullptr);
        ASensorEvent events[16];
        for (ssize_t got; (got = ASensorEventQueue_getEvents(queue, events, 16)) > 0;)
            for (ssize_t i = 0; i < got; i++)
                if (events[i].type == ASENSOR_TYPE_ACCELEROMETER) { count++; last = events[i]; }
    }
    ASensorEventQueue_disableSensor(queue, accel);
    ASensorManager_destroyEventQueue(manager, queue);
    fprintf(stderr, "accelerometer: %d events in 1 s at a 20 ms period, last %.3f %.3f %.3f\n", count, last.acceleration.x, last.acceleration.y,
            last.acceleration.z);
    CHECK(count >= 35 && count <= 60);  // 50 expected; the example HAL samples on its own thread
    fprintf(stderr, "PASS sensors through SensorService (NDK): %d sensors, accelerometer events at the requested rate\n", n);
    return 0;
}
