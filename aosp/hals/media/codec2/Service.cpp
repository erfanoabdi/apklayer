/* AOSP media/codec2/hal/services/vendor.cpp AIDL registration, without Android
 * init/minijail: the normal Linux launcher owns this process. */
#include "HostDecoder.h"
#include "DecodeProbe.h"
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <codec2/aidl/ComponentStore.h>
#include <cstdio>
#include <cstring>
#include <C2Config.h>
extern "C" int main(int argc, char** argv) {
    if (argc == 3 && !strcmp(argv[1], "--decode")) return apklayer::decodeProbe(argv[2]);
    if (argc == 2 && !strcmp(argv[1], "--probe")) {
        auto store = apklayer::makeStore();
        auto traits = store->listComponents();
        if (traits.size() != 1) return 1;
        std::shared_ptr<C2ComponentInterface> interface;
        if (store->createInterface(traits[0]->name, &interface) != C2_OK) return 1;
        C2StreamPictureSizeInfo::output size(0u, 64, 48);
        std::vector<std::unique_ptr<C2SettingResult>> failures;
        if (interface->config_vb({&size}, C2_MAY_BLOCK, &failures) != C2_OK || !failures.empty()) return 1;
        size.width = size.height = 0;
        std::vector<std::unique_ptr<C2Param>> heap;
        if (interface->query_vb({&size}, {}, C2_MAY_BLOCK, &heap) != C2_OK || size.width != 64 || size.height != 48) return 1;
        fprintf(stderr, "codec2: PASS %s interface config/query %ux%u\n", traits[0]->name.c_str(), size.width, size.height);
        return 0;
    }
    using namespace aidl::android::hardware::media::c2;
    ABinderProcess_setThreadPoolMaxThreadCount(8);
    ABinderProcess_startThreadPool();
    auto service = ndk::SharedRefBase::make<utils::ComponentStore>(apklayer::makeStore());
    const std::string name = std::string(IComponentStore::descriptor) + "/apklayer";
    if (AServiceManager_addService(service->asBinder().get(), name.c_str()) != EX_NONE) return 1;
    fprintf(stderr, "codec2: registered %s\n", name.c_str());
    ABinderProcess_joinThreadPool();
    return 0;
}
