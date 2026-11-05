#include "providers.h"

#include <dlfcn.h>
#include <signal.h>
#include <sys/wait.h>
#include <unistd.h>
#include <cstdint>
#include <cstdio>
#include <cstring>

#include "stack.h"

#include <apklayer/plugin.h>

namespace apklayer {

namespace {
// The domains apklayer has an Android-side front for: front library, vintf fragment (repo path), and for the "device" provider the
// device's HAL names reached through the host binder contexts (AIDL interfaces, HIDL packages: docs/HALIUM.md). Sensors and GNSS have
// none: those HALs take one client, which on a Halium device is the OS's own (sensorfw, its location service).
struct Domain { const char* name; const char* front; const char* manifest; const char* device; };
const Domain kDomains[] = {
    {"sensors", "libapklayer_sensors.so", "aosp/hals/sensors/manifest.xml", nullptr},
    {"gnss", "libapklayer_gnss.so", "aosp/hals/gnss/manifest.xml", nullptr},
    {"vibrator", "libapklayer_vibrator.so", "aosp/hals/vibrator/manifest.xml",
     "android.hardware.vibrator.IVibrator android.hardware.vibrator.IVibratorManager android.hardware.vibrator"},
    {"camera", "libapklayer_camera.so", "aosp/hals/camera/manifest.xml",
     "android.hardware.camera.provider.ICameraProvider android.hardware.camera.provider"},
    // the stack's own graphics (allocator, mapper, EGL on the host's GPU) need no front here; "device" is a Halium device's gralloc
    // (allocator@4.0 through the host context, the passthrough mapper@4.0) and its GPU driver (docs/HALIUM.md)
    {"graphics", nullptr, nullptr, "android.hardware.graphics.allocator android.hardware.graphics.mapper"},
    // media codecs: the stack's own Codec2 store (libapklayer_codec2) over the host's decoders; "device" is a Halium device's Codec2 (HIDL
    // media.c2) and OMX stores through the host hwbinder context, the stack's own store is not started then
    {"media", nullptr, nullptr, "android.hardware.media.c2 android.hardware.media.omx"},
};

const Domain* findDomain(const std::string& name) {
    for (const Domain& d : kDomains) if (name == d.name) return &d;
    return nullptr;
}

// Where plugins (backends) are found, in this order; a name found twice is the first one's (docs/PLUGINS.md): the runtime's native dir
// (apklayer's own), $APKLAYER_PLUGIN_PATH (colon separated: a plugin under development), the user's, this install's <libdir>/apklayer/plugins
// (third-party packages: the plugindir of apklayer-plugin.pc)
std::vector<std::string> pluginDirs(const Layout& l) {
    std::vector<std::string> dirs = {l.native};
    for (const std::string& d : split(getenvOr("APKLAYER_PLUGIN_PATH", ""), ':')) if (!d.empty()) dirs.push_back(d);
    dirs.push_back(join(homeDir(), ".local/lib/apklayer/plugins"));
    dirs.push_back(APKLAYER_PLUGINDIR);
    return dirs;
}

// the library of a domain's backend, "" when no plugin dir has it
std::string backendLib(const Layout& l, const std::string& domain, const std::string& backend) {
    for (const std::string& dir : pluginDirs(l))
        if (exists(join(dir, "libapklayer-" + domain + "-" + backend + ".so"))) return join(dir, "libapklayer-" + domain + "-" + backend + ".so");
    return "";
}

// Why a loaded plugin must not be used: built against other headers than this apklayer's (apklayer/plugin.h); "" when it can be
std::string abiRefusal(void* handle) {
    auto abi = static_cast<const int*>(dlsym(handle, "apklayer_plugin_abi"));
    const std::string ours = std::to_string(APKLAYER_PLUGIN_ABI);
    if (!abi) return "no apklayer_plugin_abi (built for an apklayer before plugin ABI " + ours + ")";
    return *abi == APKLAYER_PLUGIN_ABI ? "" : "plugin ABI " + std::to_string(*abi) + ", this apklayer takes " + ours;
}

// "" when the plugin library loads here and has this apklayer's ABI, else why not (it is then never probed nor started)
std::string refusal(const std::string& lib) {
    void* handle = dlopen(lib.c_str(), RTLD_NOW | RTLD_LOCAL);
    if (!handle) return dlerror();
    std::string why = abiRefusal(handle);
    dlclose(handle);
    return why;
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

// probe() of a backend library: <0 unusable here, 0 usable (never a default), >0 quality; INT32_MIN when it cannot be loaded or is refused
// (refused: why, see abiRefusal; probe() is not called then). Run in a child
// process: a backend's probe may start threads (GDBus) or block on a bus, which must not stay in the stack daemon that forks services.
// options: the backend's apklayer_<domain>_options, a line "key<TAB>default<TAB>kind" per option it takes (kind text or file), "" without
// (read in this process: a constant string, nothing to start).
int probe(const std::string& lib, const std::string& domain, std::string& options, std::string& refused) {
    options.clear();
    refused.clear();
    if (void* handle = dlopen(lib.c_str(), RTLD_NOW | RTLD_LOCAL)) {
        refused = abiRefusal(handle);
        if (auto declared = static_cast<const char*>(dlsym(handle, ("apklayer_" + domain + "_options").c_str()))) options = declared;
        // not dlclose'd before the fork below? The child loads it again: a constructor of the library runs only there
        dlclose(handle);
        if (!refused.empty()) return INT32_MIN;
    }
    pid_t pid = fork();
    if (pid == 0) {
        void* handle = dlopen(lib.c_str(), RTLD_NOW | RTLD_LOCAL);
        auto fn = handle ? reinterpret_cast<int (*)()>(dlsym(handle, ("apklayer_" + domain + "_probe").c_str())) : nullptr;
        if (!fn) _exit(255);
        int q = fn();
        _exit(q < 0 ? 0 : 1 + (q > 253 ? 253 : q));  // exit status: 0 unusable, 1 + quality, 255 unloadable
    }
    int st = 0;
    if (pid < 0 || waitpid(pid, &st, 0) != pid || !WIFEXITED(st) || WEXITSTATUS(st) == 255) return INT32_MIN;
    return WEXITSTATUS(st) == 0 ? -1 : WEXITSTATUS(st) - 1;
}

// a domain's backends in the plugin dirs: name -> library (the first of a name)
std::vector<std::pair<std::string, std::string>> backends(const Layout& l, const std::string& domain) {
    std::vector<std::pair<std::string, std::string>> found;
    const std::string prefix = "libapklayer-" + domain + "-";
    for (const std::string& dir : pluginDirs(l))
        for (const std::string& f : listDir(dir)) {
            if (f.rfind(prefix, 0) != 0 || f.size() <= prefix.size() + 3 || f.substr(f.size() - 3) != ".so") continue;
            std::string name = f.substr(prefix.size(), f.size() - prefix.size() - 3);
            bool seen = false;
            for (const auto& b : found) seen |= b.first == name;
            if (!seen) found.push_back({name, join(dir, f)});
        }
    return found;
}

// the default of a domain providers.toml does not name: the staged backend with the highest probe() above 0, or "" (the domain stays off)
std::string defaultBackend(const Layout& l, const std::string& domain) {
    std::string best;
    int bestQuality = 0;
    for (const auto& [name, lib] : backends(l, domain)) {
        std::string unusedOptions, unusedRefusal;
        int q = probe(lib, domain, unusedOptions, unusedRefusal);
        if (q > bestQuality) best = name, bestQuality = q;
    }
    return best;
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
    std::vector<ProviderConfig> configs = readProviders(providersFile(inst));
    for (const Domain& d : kDomains) {  // a domain the file does not name: the best backend this host has, if any (docs/PLAN.md, providers 3)
        bool named = false;
        for (const ProviderConfig& c : configs) named |= c.domain == d.name;
        std::string backend = named ? "" : defaultBackend(l, d.name);
        if (!backend.empty()) configs.push_back({d.name, "apklayer", backend, {}});
    }
    for (const ProviderConfig& c : configs) {
        const Domain* d = findDomain(c.domain);
        if (!d) { fprintf(stderr, "providers: no domain %s\n", c.domain.c_str()); continue; }
        if (c.provider == "device") continue;  // the device's own HAL: no front here (writeHostHals lists it)
        if (c.provider != "apklayer") { fprintf(stderr, "providers: %s: no provider %s\n", d->name, c.provider.c_str()); continue; }
        if (!d->front) continue;  // graphics: the stack's own
        std::string backend = backendLib(l, c.domain, c.backend);
        if (!exists(join(l.androidLibs, d->front)) || backend.empty()) {
            fprintf(stderr, "providers: %s: %s or the plugin libapklayer-%s-%s.so is not staged\n", d->name, d->front, d->name, c.backend.c_str());
            continue;
        }
        if (std::string why = refusal(backend); !why.empty()) {  // never into a front: a mismatched ABI would crash it
            fprintf(stderr, "providers: %s: %s refused: %s\n", d->name, backend.c_str(), why.c_str());
            continue;
        }
        services.push_back({d->name, d->front, backend, c.options, d->manifest});
    }
    return services;
}

std::string hostHalsFile(const Instance& inst) { return join(inst.dataDir, "apklayer/host_hals.conf"); }

bool deviceDomain(const Instance& inst, const std::string& domain) {
    for (const ProviderConfig& c : readProviders(providersFile(inst)))
        if (c.domain == domain && c.provider == "device") return true;
    return false;
}

bool deviceGraphics(const Instance& inst) { return deviceDomain(inst, "graphics"); }

bool writeHostHals(const Instance& inst) {
    std::string text;
    for (const ProviderConfig& c : readProviders(providersFile(inst))) {
        const Domain* d = findDomain(c.domain);
        if (c.provider != "device" || !d) continue;
        if (!d->device) { fprintf(stderr, "providers: %s: the device provider is not offered (one client: the OS's own)\n", d->name); continue; }
        text += std::string("# ") + d->name + "\n";
        for (const std::string& name : split(d->device, ' ')) text += name + "\n";
    }
    if (text.empty()) { unlink(hostHalsFile(inst).c_str()); return false; }
    makeDirs(join(inst.dataDir, "apklayer"));
    if (!writeFile(hostHalsFile(inst), "# written by apklayer from providers.toml (domains with provider = \"device\")\n" + text))
        die("cannot write " + hostHalsFile(inst));
    return true;
}

int providersCommand(const Layout& l, const Instance& inst, const std::vector<std::string>& args) {
    const std::string file = providersFile(inst);
    auto domains = readProviders(file);
    if (args.empty()) {  // domain, provider, backend, its options (a field each); then every backend found with its probe() answer and the options it takes
        for (const ProviderConfig& d : domains) {
            printf("%s\t%s\t%s", d.domain.c_str(), d.provider.c_str(), d.backend.c_str());
            for (const std::string& o : d.options) printf("\t%s", o.c_str());
            printf("\n");
        }
        for (const Domain& d : kDomains) {
            std::string best;
            int bestQuality = 0;
            for (const auto& [name, lib] : backends(l, d.name)) {
                std::string options, refused;
                int q = probe(lib, d.name, options, refused);
                if (q > bestQuality) best = name, bestQuality = q;
                std::string state = !refused.empty() ? "refused: " + refused : q == INT32_MIN ? "unloadable" : q < 0 ? "unavailable" : "quality " + std::to_string(q);
                printf("backend\t%s\t%s\t%s\t%s\n", d.name, name.c_str(), state.c_str(), lib.c_str());
                for (const std::string& o : split(options, '\n'))
                    if (!o.empty()) printf("option\t%s\t%s\t%s\n", d.name, name.c_str(), o.c_str());
            }
            bool named = false;
            for (const ProviderConfig& c : domains) named |= c.domain == d.name;
            if (!named) printf("default\t%s\t%s\n", d.name, best.empty() ? "(none: off)" : best.c_str());  // what an unset domain runs
        }
        return 0;
    }
    const std::string& action = args[0];
    if (!((action == "set" && args.size() >= 3) || (action == "unset" && args.size() == 2)))
        die("providers [set <domain> <backend> [key=value...] | set <domain> device | unset <domain>]");
    if (!findDomain(args[1])) die("no hardware domain " + args[1] + " (sensors, gnss, vibrator, camera, graphics, media)");
    std::vector<ProviderConfig> kept;
    for (const ProviderConfig& d : domains) if (d.domain != args[1]) kept.push_back(d);
    if (action == "set" && args[2] == "device") {  // the device's own HAL through the host binder contexts (a Halium device)
        if (!findDomain(args[1])->device) die("the device provider is not offered for " + args[1] + " (one client: the OS's own)");
        if (args.size() > 3) die("the device provider takes no options");
        kept.push_back({args[1], "device", "", {}});
    } else if (action == "set") {
        std::string lib = backendLib(l, args[1], args[2]);
        if (lib.empty()) {
            std::string dirs;
            for (const std::string& dir : pluginDirs(l)) dirs += (dirs.empty() ? "" : ", ") + dir;
            die("no plugin libapklayer-" + args[1] + "-" + args[2] + ".so in " + dirs);
        }
        if (std::string why = refusal(lib); !why.empty()) die(lib + " refused: " + why);
        ProviderConfig d{args[1], "apklayer", args[2], {}};
        for (size_t i = 3; i < args.size(); i++) {
            size_t eq = args[i].find('=');
            if (eq == std::string::npos || eq == 0 || args[i].find_first_of("\"\n") != std::string::npos) die("an option is key=value (no quotes): " + args[i]);
            d.options.push_back(args[i]);
        }
        kept.push_back(d);
    }
    writeProviders(file, kept);
    writeHostHals(inst);  // processes started from now on (apps, `apklayer exec`) reach a device domain's HAL; the stack writes it at start too
    // a running stack restarts only this domain's service (SIGHUP: runStackDaemon re-reads the file)
    auto pid = readFile(inst.pidFile());
    bool running = pid && alive(atoi(pid->c_str())) && kill(atoi(pid->c_str()), SIGHUP) == 0;
    printf("apklayer: %s %s for instance %s%s\n", args[1].c_str(), action == "set" ? "set" : "unset", inst.id.c_str(), running ? " (its service restarts)" : " (from its next start)");
    return 0;
}

}  // namespace apklayer
