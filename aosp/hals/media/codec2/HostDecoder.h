#pragma once
#include <C2Component.h>
#include <cstring>
#include <util/C2InterfaceHelper.h>
namespace apklayer {
inline constexpr char kDecoderName[] = "c2.apklayer.vp8.decoder";
inline constexpr char kDecoderMime[] = "video/x-vnd.on2.vp8";
struct DecoderInfo {
    enum Type { VP8, VP9, AVC };
    const char *name; const char *mime; Type type;
};
inline constexpr DecoderInfo kDecoders[] = {
    {kDecoderName, kDecoderMime, DecoderInfo::VP8},
    {"c2.apklayer.vp9.decoder", "video/x-vnd.on2.vp9", DecoderInfo::VP9},
    {"c2.apklayer.avc.decoder", "video/avc", DecoderInfo::AVC},
};
inline const DecoderInfo *findDecoder(const C2String& name) {
    for (const auto& info : kDecoders) if (name == info.name) return &info;
    return nullptr;
}
std::shared_ptr<C2Component> makeDecoder(c2_node_id_t, const std::shared_ptr<C2ReflectorHelper>&, const DecoderInfo&);
std::shared_ptr<C2ComponentInterface> makeDecoderInterface(c2_node_id_t, const std::shared_ptr<C2ReflectorHelper>&, const DecoderInfo&);
std::shared_ptr<C2Component> makeReorderedDecoder(c2_node_id_t, const std::shared_ptr<C2ReflectorHelper>&, const DecoderInfo&);
std::shared_ptr<C2ComponentStore> makeStore();
}
