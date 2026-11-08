/* AOSP media/codec2/hal/services/vendor.cpp AIDL registration, without Android
 * init/minijail: the normal Linux launcher owns this process. */
#include "HostDecoder.h"
#include "DecodeProbe.h"
#include "ReorderProbe.h"
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <codec2/aidl/ComponentStore.h>
#include <cstdio>
#include <algorithm>
#include <cstring>
#include <C2Config.h>
// whether the interface's field may take the value (its possible values: a range or a list); true when it does not say
static bool takes(const std::shared_ptr<C2ComponentInterface>& interface, const C2ParamField& field, uint32_t value) {
    std::vector<C2FieldSupportedValuesQuery> query = {C2FieldSupportedValuesQuery::Possible(field)};
    if (interface->querySupportedValues_vb(query, C2_MAY_BLOCK) != C2_OK || query[0].status != C2_OK) return true;
    const C2FieldSupportedValues& v = query[0].values;
    if (v.type == C2FieldSupportedValues::RANGE) return v.range.min.u32 <= value && value <= v.range.max.u32;
    if (v.type == C2FieldSupportedValues::VALUES) return std::any_of(v.values.begin(), v.values.end(), [&](const C2Value::Primitive& p) { return p.u32 == value; });
    return v.type != C2FieldSupportedValues::EMPTY;
}
// --probe of one audio codec: 48 kHz stereo configured and read back; a fixed-rate codec (AMR-NB 8 kHz mono, AMR-WB 16 kHz, GSM) cannot take it, as on a device: skipped
template <typename Rate, typename Channels>
static int probeAudio(const std::shared_ptr<C2ComponentInterface>& interface, const C2String& name) {
    Rate rate(0u, 48000);
    Channels channels(0u, 2);
    if (!takes(interface, C2ParamField(&rate, &Rate::value), 48000) || !takes(interface, C2ParamField(&channels, &Channels::value), 2)) {
        fprintf(stderr, "codec2: SKIP %s interface config/query: no 48000Hz 2ch\n", name.c_str());
        return 0;
    }
    std::vector<std::unique_ptr<C2SettingResult>> failures;
    std::vector<std::unique_ptr<C2Param>> heap;
    bool ok = interface->config_vb({&rate, &channels}, C2_MAY_BLOCK, &failures) == C2_OK && failures.empty();
    rate.value = channels.value = 0;
    ok = ok && interface->query_vb({&rate, &channels}, {}, C2_MAY_BLOCK, &heap) == C2_OK && rate.value == 48000 && channels.value == 2;
    fprintf(stderr, "codec2: %s %s interface config/query %uHz %uch\n", ok ? "PASS" : "FAIL", name.c_str(), rate.value, channels.value);
    return ok ? 0 : 1;
}

// --probe of one video codec: a 64x48 picture configured and read back; a codec of fixed sizes (the H.263 encoder: CIF family only) skipped
template <typename Size>
static int probeVideo(const std::shared_ptr<C2ComponentInterface>& interface, const C2String& name) {
    Size size(0u, 64, 48);
    if (!takes(interface, C2ParamField(&size, &Size::width), 64) || !takes(interface, C2ParamField(&size, &Size::height), 48)) {
        fprintf(stderr, "codec2: SKIP %s interface config/query: no 64x48\n", name.c_str());
        return 0;
    }
    std::vector<std::unique_ptr<C2SettingResult>> failures;
    std::vector<std::unique_ptr<C2Param>> heap;
    bool ok = interface->config_vb({&size}, C2_MAY_BLOCK, &failures) == C2_OK && failures.empty();
    size.width = size.height = 0;
    ok = ok && interface->query_vb({&size}, {}, C2_MAY_BLOCK, &heap) == C2_OK && size.width == 64 && size.height == 48;
    fprintf(stderr, "codec2: %s %s interface config/query %ux%u\n", ok ? "PASS" : "FAIL", name.c_str(), size.width, size.height);
    return ok ? 0 : 1;
}

extern "C" int main(int argc, char** argv) {
    if (argc == 3 && !strcmp(argv[1], "--reorder-probe")) {
        ABinderProcess_setThreadPoolMaxThreadCount(2);
        ABinderProcess_startThreadPool();
        return apklayer::reorderProbe(argv[2]);
    }
    if (argc >= 2 && !strcmp(argv[1], "--remote-probe")) {
        using namespace aidl::android::hardware::media::c2;
        ABinderProcess_setThreadPoolMaxThreadCount(2);
        ABinderProcess_startThreadPool();
        const std::string name = std::string(IComponentStore::descriptor) + "/apklayer";
        ndk::SpAIBinder binder;
        if (__builtin_available(android 31, *)) {
            binder = ndk::SpAIBinder(AServiceManager_waitForService(name.c_str()));
        } else return 1;
        auto store = IComponentStore::fromBinder(binder);
        std::vector<IComponentStore::ComponentTraits> traits;
        if (!store || !store->listComponents(&traits).isOk() || traits.empty() ||
            !apklayer::findDecoder(traits[0].name)) return 1;
        std::shared_ptr<IComponentInterface> interface;
        if (!store->createInterface(traits[0].name, &interface).isOk() || !interface) return 1;
        fprintf(stderr, "codec2: PASS remote AIDL discovery/interface %s\n", traits[0].name.c_str());
        if (argc < 3) return 0;
        int multiIndex = argc == 4 && !strcmp(argv[3], "multi") ? 0 :
            argc == 4 && !strcmp(argv[3], "multi-last") ? 2 : -1;
        int result = apklayer::decodeProbe(argv[2], true, argc == 4 && !strcmp(argv[3], "hide-first"), multiIndex);
        return result || argc == 4 ? result : apklayer::decodeProbe(argv[2], true, true);
    }
    if (argc == 3 && !strcmp(argv[1], "--decode")) return apklayer::decodeProbe(argv[2]);
    if (argc == 2 && !strcmp(argv[1], "--probe")) {
        auto store = apklayer::makeStore();
        auto traits = store->listComponents();
        if (traits.empty()) return 1;
        for (const auto& trait : traits) {
            std::shared_ptr<C2ComponentInterface> interface;
            if (store->createInterface(trait->name, &interface) != C2_OK) return 1;
            // a decoder's stream format is its output's, an encoder's its input's (C2Config.h)
            bool encoder = trait->kind == C2Component::KIND_ENCODER;
            int status = trait->domain == C2Component::DOMAIN_AUDIO
                    ? (encoder ? probeAudio<C2StreamSampleRateInfo::input, C2StreamChannelCountInfo::input>(interface, trait->name)
                               : probeAudio<C2StreamSampleRateInfo::output, C2StreamChannelCountInfo::output>(interface, trait->name))
                    : (encoder ? probeVideo<C2StreamPictureSizeInfo::input>(interface, trait->name) : probeVideo<C2StreamPictureSizeInfo::output>(interface, trait->name));
            if (status != 0) return status;
        }
        return 0;
    }
    using namespace aidl::android::hardware::media::c2;
    ABinderProcess_setThreadPoolMaxThreadCount(8);
    ABinderProcess_startThreadPool();
    auto service = ndk::SharedRefBase::make<utils::ComponentStore>(apklayer::makeStore());
    const std::string name = std::string(IComponentStore::descriptor) + "/apklayer";
    auto result = AServiceManager_addService(service->asBinder().get(), name.c_str());
    if (result != EX_NONE) { fprintf(stderr, "codec2: registration failed %d\n", result); return 1; }
    fprintf(stderr, "codec2: registered %s\n", name.c_str());
    ABinderProcess_joinThreadPool();
    return 0;
}
