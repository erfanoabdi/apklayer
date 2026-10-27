#include <effectFactory-impl/EffectFactory.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <cstdio>

int audioEffects(const char* file) {
    using aidl::android::hardware::audio::effect::Factory;
    auto factory = ndk::SharedRefBase::make<Factory>(file);
    ABinderProcess_setThreadPoolMaxThreadCount(4);
    ABinderProcess_startThreadPool();
    if (AServiceManager_addService(factory->asBinder().get(),
            "android.hardware.audio.effect.IFactory/default") != STATUS_OK) return 1;
    fprintf(stderr, "apklayer original audio effect factory ready\n");
    ABinderProcess_joinThreadPool();
    return 1;
}
