/* The tree's own software codecs (libcodec2_soft_*), loaded one by one. The platform store's fixed library list aborts or crashes when a
 * library is missing or declines to create its factory (xheaacenc); this loader takes what is installed and skips the rest. */
#pragma once
#include <C2Component.h>
#include <memory>
#include <string>
#include <vector>
namespace apklayer {
std::vector<std::shared_ptr<const C2Component::Traits>> listSoftCodecs();
c2_status_t createSoftComponent(const std::string& name, c2_node_id_t id, std::shared_ptr<C2Component>* out);
c2_status_t createSoftInterface(const std::string& name, c2_node_id_t id, std::shared_ptr<C2ComponentInterface>* out);
}
