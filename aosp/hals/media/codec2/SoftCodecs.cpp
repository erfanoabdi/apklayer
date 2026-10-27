#include "SoftCodecs.h"
#include <C2Config.h>
#include <C2ComponentFactory.h>
#include <dlfcn.h>
#include <cstring>
#include <mutex>
namespace apklayer {
namespace {
// the codecs the host decoders do not cover, plus all encoders; VP8/VP9/H.264/AAC decoding is c2.apklayer.* (host), their soft ones are not listed
constexpr const char *kLibraries[] = {
    "libcodec2_soft_mp3dec.so", "libcodec2_soft_opusdec.so", "libcodec2_soft_vorbisdec.so", "libcodec2_soft_flacdec.so",
    "libcodec2_soft_amrnbdec.so", "libcodec2_soft_amrwbdec.so", "libcodec2_soft_g711alawdec.so", "libcodec2_soft_g711mlawdec.so",
    "libcodec2_soft_gsmdec.so", "libcodec2_soft_rawdec.so", "libcodec2_soft_hevcdec.so", "libcodec2_soft_mpeg2dec.so",
    "libcodec2_soft_mpeg4dec.so", "libcodec2_soft_h263dec.so", "libcodec2_soft_av1dec_dav1d.so",
    "libcodec2_soft_aacenc.so", "libcodec2_soft_amrnbenc.so", "libcodec2_soft_amrwbenc.so", "libcodec2_soft_flacenc.so",
    "libcodec2_soft_opusenc.so", "libcodec2_soft_avcenc.so", "libcodec2_soft_hevcenc.so", "libcodec2_soft_vp8enc.so",
    "libcodec2_soft_vp9enc.so", "libcodec2_soft_h263enc.so", "libcodec2_soft_mpeg4enc.so",
};
struct Module { C2ComponentFactory *factory; std::shared_ptr<const C2Component::Traits> traits; };
std::vector<Module>& modules() {
    static std::vector<Module> loaded;
    static std::once_flag once;
    std::call_once(once, [] {
        for (const char *lib : kLibraries) {
            void *handle = dlopen(lib, RTLD_NOW);
            if (!handle) continue;
            auto create = reinterpret_cast<C2ComponentFactory::CreateCodec2FactoryFunc>(dlsym(handle, "CreateCodec2Factory"));
            C2ComponentFactory *factory = create ? create() : nullptr;
            if (!factory) continue;
            std::shared_ptr<C2ComponentInterface> intf;
            if (factory->createInterface(0, &intf) != C2_OK || !intf) continue;
            C2ComponentKindSetting kind;
            C2ComponentDomainSetting domain;
            std::vector<C2Param *> stack = {&kind, &domain};
            if (intf->query_vb(stack, {}, C2_MAY_BLOCK, nullptr) != C2_OK) continue;
            std::vector<std::unique_ptr<C2Param>> heap;
            bool decoder = kind.value == C2Component::KIND_DECODER;
            if (intf->query_vb({}, {(decoder ? C2PortMediaTypeSetting::input::PARAM_TYPE : C2PortMediaTypeSetting::output::PARAM_TYPE)},
                               C2_MAY_BLOCK, &heap) != C2_OK || heap.empty()) continue;
            std::string mediaType;
            if (decoder) {
                auto *mime = C2PortMediaTypeSetting::input::From(heap[0].get());
                if (!mime) continue;
                mediaType = std::string(mime->m.value, strnlen(mime->m.value, mime->flexCount()));
            } else {
                auto *mime = C2PortMediaTypeSetting::output::From(heap[0].get());
                if (!mime) continue;
                mediaType = std::string(mime->m.value, strnlen(mime->m.value, mime->flexCount()));
            }
            auto traits = std::make_shared<C2Component::Traits>();
            traits->name = intf->getName();
            traits->mediaType = mediaType;
            traits->kind = kind.value;
            traits->domain = domain.value;
            traits->rank = 256;  // after the host decoders (128)
            traits->owner = "apklayer";
            loaded.push_back({factory, traits});
        }
    });
    return loaded;
}
const Module *find(const std::string& name) {
    for (const auto& m : modules()) if (m.traits->name == name) return &m;
    return nullptr;
}
}
std::vector<std::shared_ptr<const C2Component::Traits>> listSoftCodecs() {
    std::vector<std::shared_ptr<const C2Component::Traits>> result;
    for (const auto& m : modules()) result.push_back(m.traits);
    return result;
}
c2_status_t createSoftComponent(const std::string& name, c2_node_id_t id, std::shared_ptr<C2Component>* out) {
    const Module *m = find(name);
    return m ? m->factory->createComponent(id, out) : C2_NOT_FOUND;
}
c2_status_t createSoftInterface(const std::string& name, c2_node_id_t id, std::shared_ptr<C2ComponentInterface> *out) {
    const Module *m = find(name);
    return m ? m->factory->createInterface(id, out) : C2_NOT_FOUND;
}
}
