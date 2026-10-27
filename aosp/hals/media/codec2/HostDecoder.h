#pragma once
#include <C2Component.h>
#include <util/C2InterfaceHelper.h>
namespace apklayer {
inline constexpr char kDecoderName[] = "c2.apklayer.vp8.decoder";
inline constexpr char kDecoderMime[] = "video/x-vnd.on2.vp8";
std::shared_ptr<C2Component> makeDecoder(c2_node_id_t, const std::shared_ptr<C2ReflectorHelper>&);
std::shared_ptr<C2ComponentInterface> makeDecoderInterface(c2_node_id_t, const std::shared_ptr<C2ReflectorHelper>&);
std::shared_ptr<C2ComponentStore> makeStore();
}
