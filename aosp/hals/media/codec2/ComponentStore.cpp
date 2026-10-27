/* AOSP ComponentStore frontend over the host's actual available decoders. */
#include "HostDecoder.h"
#include "AudioDecoderParams.h"
#include "SoftCodecs.h"
#include "SoftCodecs.cpp"  // one translation unit with the store (the incremental module build replays known compile commands)
#include <apklayer/codec.h>
#include <C2PlatformSupport.h>
#include <atomic>
namespace apklayer {
class HostStore : public C2ComponentStore {
    std::shared_ptr<C2ComponentStore> mPlatform = android::GetCodec2PlatformComponentStore();
    std::shared_ptr<C2ReflectorHelper> mHelper = std::static_pointer_cast<C2ReflectorHelper>(mPlatform->getParamReflector());
    std::atomic<c2_node_id_t> mNext{1};
public:
    C2String getName() const override { return "apklayer"; }
    c2_status_t createComponent(C2String name, std::shared_ptr<C2Component>* const out) override {
        out->reset();
        if (name == kAudioDecoderName) {
            if (!apklayer_codec_supports(kAudioDecoderMime)) return C2_NOT_FOUND;
            *out = makeAudioDecoder(mNext++, mHelper); return C2_OK;
        }
        const auto *info = findDecoder(name);
        if (!info) return createSoftComponent(name, mNext++, out);  // the tree's own software codecs (c2.android.*)
        if (!apklayer_codec_supports(info->mime)) return C2_NOT_FOUND;
        *out = makeDecoder(mNext++, mHelper, *info); return C2_OK;
    }
    c2_status_t createInterface(C2String name, std::shared_ptr<C2ComponentInterface>* const out) override {
        out->reset();
        if (name == kAudioDecoderName) {
            if (!apklayer_codec_supports(kAudioDecoderMime)) return C2_NOT_FOUND;
            *out = makeAudioDecoderInterface(mNext++, mHelper); return C2_OK;
        }
        const auto *info = findDecoder(name);
        if (!info) return createSoftInterface(name, mNext++, out);
        if (!apklayer_codec_supports(info->mime)) return C2_NOT_FOUND;
        *out = makeDecoderInterface(mNext++, mHelper, *info); return C2_OK;
    }
    std::vector<std::shared_ptr<const C2Component::Traits>> listComponents() override {
        std::vector<std::shared_ptr<const C2Component::Traits>> result;
        for (const auto& info : kDecoders) {
            if (!apklayer_codec_supports(info.mime)) continue;
            auto traits = std::make_shared<C2Component::Traits>();
            traits->name = info.name; traits->mediaType = info.mime;
            traits->domain = C2Component::DOMAIN_VIDEO; traits->kind = C2Component::KIND_DECODER;
            traits->rank = 128; traits->owner = getName();
            result.push_back(traits);
        }
        if (apklayer_codec_supports(kAudioDecoderMime)) {
            auto traits = std::make_shared<C2Component::Traits>();
            traits->name = kAudioDecoderName; traits->mediaType = kAudioDecoderMime;
            traits->domain = C2Component::DOMAIN_AUDIO; traits->kind = C2Component::KIND_DECODER;
            traits->rank = 128; traits->owner = getName(); result.push_back(traits);
        }
        // apklayer patch: the tree's software codecs (libcodec2_soft_*: MP3, Opus, Vorbis, FLAC, AMR, G.711, raw, HEVC, MPEG-2/4, H.263 and the
        // encoders) are served as on a phone. The host decoders above stay first.
        for (const auto& traits : listSoftCodecs()) result.push_back(traits);
        return result;
    }
    c2_status_t copyBuffer(std::shared_ptr<C2GraphicBuffer>, std::shared_ptr<C2GraphicBuffer>) override { return C2_OMITTED; }
    c2_status_t query_sm(const std::vector<C2Param*>& stack, const std::vector<C2Param::Index>& heap,
                        std::vector<std::unique_ptr<C2Param>>* const out) const override {
        return mPlatform->query_sm(stack, heap, out);
    }
    c2_status_t config_sm(const std::vector<C2Param*>& params,
                         std::vector<std::unique_ptr<C2SettingResult>>* const failures) override {
        return mPlatform->config_sm(params, failures);
    }
    std::shared_ptr<C2ParamReflector> getParamReflector() const override { return mHelper; }
    c2_status_t querySupportedParams_nb(std::vector<std::shared_ptr<C2ParamDescriptor>>* const out) const override {
        return mPlatform->querySupportedParams_nb(out);
    }
    c2_status_t querySupportedValues_sm(std::vector<C2FieldSupportedValuesQuery>& fields) const override {
        return mPlatform->querySupportedValues_sm(fields);
    }
};
std::shared_ptr<C2ComponentStore> makeStore() { return std::make_shared<HostStore>(); }
}
