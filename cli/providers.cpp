#include "providers.h"

#include <dlfcn.h>
#include <signal.h>
#include <cstdint>
#include <cstdio>
#include <cstring>

#include "stack.h"

namespace apklayer {

namespace {
// The domains apklayer has an Android-side front for: front library, vintf fragment (repo path)
struct Domain { const char* name; const char* front; const char* manifest; };
const Domain kDomains[] = {
    {"sensors", "libapklayer_sensors.so", "aosp/hals/sensors/manifest.xml"},
    {"gnss", "libapklayer_gnss.so", "aosp/hals/gnss/manifest.xml"},
    {"vibrator", "libapklayer_vibrator.so", "aosp/hals/vibrator/manifest.xml"},
    {"camera", "libapklayer_camera.so", "aosp/hals/camera/manifest.xml"},
};

const Domain* findDomain(const std::string& name) {
    for (const Domain& d : kDomains) if (name == d.name) return &d;
    return nullptr;
}

std::string backendLib(const Layout& l, const std::string& domain, const std::string& backend) {
    return join(l.native, "libapklayer-" + domain + "-" + backend + ".so");
}

// a TOML string or bare value: "a b" -> a b (no escapes: the CLI writes none)
std::string value(const std::string& raw) {
    std::string v = trim(raw);
    return v.size() >= 2 && v.front() == '"' && v.back() == '"' ? v.substr(1, v.size() - 2) : v;
}

std::string quote(const std::string& v) { return "\"" + v + "\""; }

void writeProviders(const std::string& file, const std::vector<ProviderConfig>& domains) {
    std::string text = "# The instance's hardware providers (apklayer providers set|unset): domain -> provider, backend and its options\n";
    for (const ProviderConfig& d : domains) {
        text += "\n[" + d.domain + "]\nprovider = " + quote(d.provider) + "\nbackend = " + quote(d.backend) + "\n";
        for (const std::string& o : d.options) {
            size_t eq = o.find('=');
            text += o.substr(0, eq) + " = " + quote(o.substr(eq + 1)) + "\n";
        }
    }
    if (!writeFile(file, text)) die("cannot write " + file);
}

// probe() of a backend library: <0 unusable here, 0 usable (never a default), >0 quality; INT32_MIN when it cannot be loaded
int probe(const std::string& lib, const std::string& domain) {
    void* handle = dlopen(lib.c_str(), RTLD_NOW | RTLD_LOCAL);
    if (!handle) return INT32_MIN;
    auto fn = reinterpret_cast<int (*)()>(dlsym(handle, ("apklayer_" + domain + "_probe").c_str()));
    int quality = fn ? fn() : INT32_MIN;
    dlclose(handle);
    return quality;
}
}  // namespace

const std::vector<std::string>& providerDomains() {
    static const std::vector<std::string> names = [] { std::vector<std::string> v; for (const Domain& d : kDomains) v.push_back(d.name); return v; }();
    return names;
}

std::string providersFile(const Instance& inst) { return join(inst.root, "providers.toml"); }

std::vector<ProviderConfig> readProviders(const std::string& file) {
    std::vector<ProviderConfig> domains;
    auto text = readFile(file);
    if (!text) return domains;
    int number = 0;
    for (const std::string& raw : split(*text, '\n')) {
        number++;
        std::string line = trim(raw);
        if (line.empty() || line.front() == '#') continue;  // whole-line comments only: a value may hold '#'
        if (line.front() == '[' && line.back() == ']') { domains.push_back({trim(line.substr(1, line.size() - 2)), "apklayer", "", {}}); continue; }
        size_t eq = line.find('=');
        if (eq == std::string::npos || domains.empty()) {  // reported, not fatal: a running stack re-reads the file
            fprintf(stderr, "%s:%d: expected [domain] or key = \"value\" (line ignored)\n", file.c_str(), number);
            continue;
        }
        std::string key = trim(line.substr(0, eq)), v = value(line.substr(eq + 1));
        ProviderConfig& d = domains.back();
        if (key == "provider") d.provider = v;
        else if (key == "backend") d.backend = v;
        else d.options.push_back(key + "=" + v);
    }
    return domains;
}

std::vector<ProviderService> providerServices(const Layout& l, const Instance& inst) {
    std::vector<ProviderService> services;
    for (const ProviderConfig& c : readProviders(providersFile(inst))) {
        const Domain* d = findDomain(c.domain);
        if (!d) { fprintf(stderr, "providers: no domain %s\n", c.domain.c_str()); continue; }
        // shortcut: only apklayer's own fronts; the "device" provider (the device's HAL through the host binder contexts) is plan step 7(d)
        if (c.provider != "apklayer") { fprintf(stderr, "providers: %s: provider %s is not supported yet\n", d->name, c.provider.c_str()); continue; }
        std::string backend = backendLib(l, c.domain, c.backend);
        if (!exists(join(l.androidLibs, d->front)) || !exists(backend)) {
            fprintf(stderr, "providers: %s: %s or %s is not staged\n", d->name, d->front, backend.c_str());
            continue;
        }
        services.push_back({d->name, d->front, backend, c.options, d->manifest});
    }
    return services;
}

int providersCommand(const Layout& l, const Instance& inst, const std::vector<std::string>& args) {
    const std::string file = providersFile(inst);
    auto domains = readProviders(file);
    if (args.empty()) {  // domain, provider, backend, options; then every backend found with its probe() answer
        for (const ProviderConfig& d : domains) {
            std::string options;
            for (const std::string& o : d.options) options += (options.empty() ? "" : " ") + o;
            printf("%s\t%s\t%s\t%s\n", d.domain.c_str(), d.provider.c_str(), d.backend.c_str(), options.c_str());
        }
        for (const Domain& d : kDomains)
            for (const std::string& f : listDir(l.native)) {
                std::string prefix = std::string("libapklayer-") + d.name + "-";
                if (f.rfind(prefix, 0) != 0 || f.size() < prefix.size() + 4 || f.substr(f.size() - 3) != ".so") continue;
                int q = probe(join(l.native, f), d.name);
                std::string name = f.substr(prefix.size(), f.size() - prefix.size() - 3);
                printf("backend\t%s\t%s\t%s\n", d.name, name.c_str(), q == INT32_MIN ? "unloadable" : q < 0 ? "unavailable" : ("quality " + std::to_string(q)).c_str());
            }
        return 0;
    }
    const std::string& action = args[0];
    if (!((action == "set" && args.size() >= 3) || (action == "unset" && args.size() == 2)))
        die("providers [set <domain> <backend> [key=value...] | unset <domain>]");
    if (!findDomain(args[1])) die("no hardware domain " + args[1] + " (sensors, gnss, vibrator, camera)");
    std::vector<ProviderConfig> kept;
    for (const ProviderConfig& d : domains) if (d.domain != args[1]) kept.push_back(d);
    if (action == "set") {
        if (!exists(backendLib(l, args[1], args[2]))) die("no backend " + backendLib(l, args[1], args[2]));
        ProviderConfig d{args[1], "apklayer", args[2], {}};
        for (size_t i = 3; i < args.size(); i++) {
            size_t eq = args[i].find('=');
            if (eq == std::string::npos || eq == 0 || args[i].find_first_of("\"\n") != std::string::npos) die("an option is key=value (no quotes): " + args[i]);
            d.options.push_back(args[i]);
        }
        kept.push_back(d);
    }
    writeProviders(file, kept);
    // a running stack restarts only this domain's service (SIGHUP: runStackDaemon re-reads the file)
    auto pid = readFile(inst.pidFile());
    bool running = pid && alive(atoi(pid->c_str())) && kill(atoi(pid->c_str()), SIGHUP) == 0;
    printf("apklayer: %s %s for instance %s%s\n", args[1].c_str(), action == "set" ? "set" : "unset", inst.id.c_str(), running ? " (its service restarts)" : " (from its next start)");
    return 0;
}

}  // namespace apklayer
