// The instance's hardware providers (docs/PLAN.md "Plan: hardware providers"): <instance root>/providers.toml names, per domain, the
// provider and its backend with options; the stack starts the configured HAL fronts, `apklayer providers` shows and changes them.
#pragma once

#include "layout.h"

namespace apklayer {

struct ProviderConfig {
    std::string domain, provider, backend;
    std::vector<std::string> options;  // "key=value", in file order
};

// What the stack runs for one configured domain: its HAL front (a staged library), the backend library (host side), the manifest it declares.
struct ProviderService {
    std::string name;      // the domain: its log is <name>.log
    std::string front;     // libapklayer_<domain>.so
    std::string backend;   // <native>/libapklayer-<domain>-<backend>.so
    std::vector<std::string> options;
    std::string manifest;  // repo path of the vintf fragment
};

std::string providersFile(const Instance& instance);
// The file's domains; a missing file is no domains, a malformed line is reported and skipped.
std::vector<ProviderConfig> readProviders(const std::string& file);
// The services of the configured domains that can run here (front staged, backend present); a domain that cannot is reported on stderr.
std::vector<ProviderService> providerServices(const Layout& layout, const Instance& instance);
// The domains apklayer has fronts for (a reload of the stack compares each).
const std::vector<std::string>& providerDomains();
// The device's HAL names for the domains whose provider is "device" into <instance data>/apklayer/host_hals.conf (/data/apklayer/
// host_hals.conf for libbinder and libhidl: those names come from the host binder contexts); removed when there are none. True if any.
bool writeHostHals(const Instance& instance);
std::string hostHalsFile(const Instance& instance);
// providers.toml sets graphics to the device: a Halium device's gralloc and GPU driver instead of the stack's own (docs/HALIUM.md)
bool deviceGraphics(const Instance& instance);
// providers.toml sets this domain to the device (its HAL through the host contexts, no front)
bool deviceDomain(const Instance& instance, const std::string& domain);
// `apklayer providers [set <domain> <backend> [opt=val...] | set <domain> device | unset <domain>]`
int providersCommand(const Layout& layout, const Instance& instance, const std::vector<std::string>& args);

}  // namespace apklayer
