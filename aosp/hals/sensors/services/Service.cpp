// SensorService (frameworks/native/services/sensorservice), which system_server runs on a device, as the stack's own process: AOSP's
// main_sensorservice.cpp in a library (every Android-side process here is a library run by btload --main). It reads the sensors HAL
// (aosp/hals/sensors) as SensorDevice does on a device; Java (SystemSensorManager) and NDK (ASensorManager) clients reach it as "sensorservice".
#include <binder/BinderService.h>
#include <sensorservice/SensorService.h>
#include <csignal>

extern "C" int main(int, char**) {
    signal(SIGPIPE, SIG_IGN);
    android::SensorService::publishAndJoinThreadPool();
    return 0;
}
