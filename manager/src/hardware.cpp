#include "hardware.h"

#include <algorithm>
#include <cctype>
#include <thread>

namespace manager {

namespace {
std::string field(const std::vector<std::string>& row, size_t i) { return i < row.size() ? row[i] : ""; }

// a domain's title and its live test: the HAL front's own client run against the instance's service (`apklayer exec <front> --probe`), and the
// line of its output to show. GNSS has none: its HAL keeps one callback, a test client would take the fixes from the apps.
struct Known { const char *name, *title, *test, *front, *marker; };
const Known kKnown[] = {
    {"sensors", "Sensors", "Read sensors", "libapklayer_sensors.so", "accelerometer:"},
    {"gnss", "Location (GNSS)", "", "", ""},
    {"vibrator", "Vibrator", "Vibrate", "libapklayer_vibrator.so", "PASS "},
    {"camera", "Camera", "", "", ""},
    {"audio", "Audio", "", "", ""},
    {"graphics", "Graphics", "", "", ""},
    {"media", "Video and audio codecs", "", "", ""},
};
const Known* known(const std::string& name) {
    for (const Known& k : kKnown) if (name == k.name) return &k;
    return nullptr;
}

// the CLI's probe() answer for people; note: the CLI's line on that choice (what it needs, what it is)
std::string probeText(const std::string& name, const std::string& probe, const std::string& note) {
    if (name == "device" && probe == "quality 0") return "found, not working yet";  // detected, never the automatic choice
    if (probe == "quality 0") return "available";
    if (probe.rfind("quality ", 0) == 0) return "available, " + probe;
    if (probe.rfind("refused: ", 0) == 0) return probe;  // a plugin built for another apklayer (its ABI)
    if (probe == "unavailable") return note.empty() ? "not available here" : note;
    return probe == "not staged" ? probe : "cannot be loaded";
}

struct Choice { std::string name, probe, note; };

// a choice's name in the status line: the CLI's words, the two that are not backends spelled out
std::string label(const std::string& name) {
    if (name == "device") return "device (its own HAL)";
    if (name == "apklayer") return "apklayer (its own)";
    return name;
}

struct Domain {
    std::string name, provider, backend, automatic;
    bool named = false;                                                // providers.toml names it (else automatic)
    std::vector<std::string> options;                                  // configured "key=value"
    std::vector<Choice> backends;                                      // every choice the CLI lists, with its probe answer
    std::vector<std::vector<std::string>> declared;                    // option rows: backend, key, default, kind
    // the configured choice's name in the backend rows: the backend, "device", or "apklayer" (the stack's own) for a domain without one
    std::string chosen() const { return provider == "device" ? "device" : backend.empty() ? "apklayer" : backend; }
};

HwOption option(const std::string& key, const std::string& value, const std::string& def, bool file) {
    HwOption o;
    o.key = slint::SharedString(key);
    o.value = slint::SharedString(value);
    o.default_value = slint::SharedString(def);
    o.file = file;
    return o;
}

HwBackend backend(const std::string& name, std::shared_ptr<slint::VectorModel<HwOption>> options) {
    HwBackend b;
    b.name = slint::SharedString(name);
    b.options = options;
    return b;
}

std::shared_ptr<slint::VectorModel<HwDomain>> model(const Rows& rows) {
    std::vector<Domain> domains;
    auto get = [&](const std::string& name) -> Domain& {
        for (Domain& d : domains) if (d.name == name) return d;
        Domain d;
        d.name = name;
        return domains.emplace_back(d);
    };
    for (const auto& r : rows) {  // `apklayer providers`: configured domains, then backend, option and default (the automatic choice) rows
        if (r.empty()) continue;
        if (r[0] == "backend") get(field(r, 1)).backends.push_back({field(r, 2), field(r, 3), field(r, 5)});
        else if (r[0] == "option") get(field(r, 1)).declared.push_back({field(r, 2), field(r, 3), field(r, 4), field(r, 5)});
        else if (r[0] == "default") get(field(r, 1)).automatic = field(r, 2) == "(none: off)" ? "off" : field(r, 2);
        else {
            Domain& d = get(r[0]);
            d.named = true;
            d.provider = field(r, 1);
            d.backend = field(r, 2);
            d.options.assign(r.begin() + std::min<size_t>(3, r.size()), r.end());
        }
    }
    auto rank = [](const Domain& d) { const Known* k = known(d.name); return k ? static_cast<size_t>(k - kKnown) : std::size(kKnown); };  // a fixed order, configured or not
    std::stable_sort(domains.begin(), domains.end(), [&](const Domain& a, const Domain& b) { return rank(a) < rank(b); });
    auto out = std::make_shared<slint::VectorModel<HwDomain>>();
    for (Domain& d : domains) {
        if (d.named && std::none_of(d.backends.begin(), d.backends.end(), [&](const Choice& b) { return b.name == d.chosen(); }))
            d.backends.push_back({d.chosen(), "not staged", ""});
        auto menu = std::make_shared<slint::VectorModel<MenuItem>>();
        auto backends = std::make_shared<slint::VectorModel<HwBackend>>();
        auto notes = std::make_shared<slint::VectorModel<slint::SharedString>>();
        std::string unavailable;  // the choices that cannot be picked here, a line each with why (the menu has no room for it)
        MenuItem automatic;  // `providers unset`: the best this host has, as the CLI picks it (shown with today's pick)
        automatic.text = "Automatic";
        automatic.trailing_text = slint::SharedString(d.automatic);
        automatic.enabled = true;
        menu->push_back(automatic);
        backends->push_back(backend("", std::make_shared<slint::VectorModel<HwOption>>()));
        notes->push_back(slint::SharedString("The best this device has, picked again at every start" +
                                             (d.automatic.empty() ? std::string() : ": now " + d.automatic)));
        int current = 0;
        for (const auto& [name, probe, note] : d.backends) {
            MenuItem m;
            m.text = slint::SharedString(name);
            m.enabled = probe.rfind("quality ", 0) == 0;  // unavailable, refused or unloadable ones are listed with why, never picked
            m.trailing_text = slint::SharedString(m.enabled ? probeText(name, probe, note) : "not available");
            if (!m.enabled) unavailable += (unavailable.empty() ? "" : "\n") + name + ": " + probeText(name, probe, note);
            menu->push_back(m);
            std::string sentence = note;
            if (!sentence.empty()) sentence[0] = static_cast<char>(toupper(static_cast<unsigned char>(sentence[0])));
            notes->push_back(slint::SharedString(sentence));
            bool configured = d.named && name == d.chosen();
            if (configured) current = static_cast<int>(backends->row_count());
            std::vector<std::string> values = configured ? d.options : std::vector<std::string>();
            auto options = std::make_shared<slint::VectorModel<HwOption>>();
            for (const auto& o : d.declared) {
                if (o[0] != name) continue;
                std::string value;
                for (auto v = values.begin(); v != values.end(); ++v)
                    if (v->rfind(o[1] + "=", 0) == 0) { value = v->substr(o[1].size() + 1); values.erase(v); break; }
                options->push_back(option(o[1], value, o[2], o[3] == "file"));
            }
            for (const std::string& v : values) {  // configured options the backend does not declare: kept, so Apply does not drop them
                size_t eq = v.find('=');
                options->push_back(option(v.substr(0, eq), eq == std::string::npos ? "" : v.substr(eq + 1), "", false));
            }
            backends->push_back(backend(name, options));
        }
        const Known* k = known(d.name);
        HwDomain h;
        h.name = slint::SharedString(d.name);
        h.title = slint::SharedString(k ? k->title : d.name);
        // what runs: the user's choice, or the CLI's automatic one
        std::string inUse = d.named ? d.chosen() : d.automatic;
        std::string configured = (d.named ? "Chosen: " : "Automatic: ") + label(inUse.empty() ? "off" : inUse);
        for (const std::string& o : d.options) configured += " · " + o;
        h.configured = slint::SharedString(configured);
        h.current = current;
        h.menu = menu;
        h.backends = backends;
        h.notes = notes;
        h.unavailable = slint::SharedString(unavailable);
        // the live test talks to apklayer's own front: not with the device's HAL, nothing when the domain is off
        h.test = slint::SharedString(k && !inUse.empty() && inUse != "off" && inUse != "device" ? k->test : "");
        out->push_back(h);
    }
    return out;
}

// the options model of a domain's backend in the page's model, nullptr when gone (a refresh replaced it)
std::shared_ptr<slint::Model<HwOption>> options(const std::shared_ptr<slint::Model<HwDomain>>& hardware, const std::string& domain, int backend) {
    for (size_t i = 0; i < hardware->row_count(); i++) {
        auto d = hardware->row_data(i);
        if (!d || d->name != slint::SharedString(domain)) continue;
        auto b = backend >= 0 ? d->backends->row_data(backend) : std::nullopt;
        return b ? b->options : nullptr;
    }
    return nullptr;
}

void setOption(const std::shared_ptr<slint::Model<HwOption>>& opts, const std::string& key, const std::string& value) {
    for (size_t i = 0; opts && i < opts->row_count(); i++)
        if (auto o = opts->row_data(i); o && o->key == slint::SharedString(key)) { o->value = slint::SharedString(value); opts->set_row_data(i, *o); }
}
}  // namespace

Hardware::Hardware(slint::ComponentHandle<AppWindow> ui, const Cli& cli, std::string root, Action action, std::function<void()> refresh,
                   std::function<void(const std::string&)> message)
    : ui_(ui), cli_(cli), root_(std::move(root)), action_(std::move(action)), refresh_(std::move(refresh)), message_(std::move(message)) {
    auto& b = ui_->global<Backend>();
    b.on_provider_option([this](slint::SharedString domain, int backend, slint::SharedString key, slint::SharedString value) {
        setOption(options(ui_->global<Backend>().get_hardware(), std::string(domain), backend), std::string(key), std::string(value));
    });
    b.on_set_provider([this](slint::SharedString domain, int backend) {
        auto& b = ui_->global<Backend>();
        auto opts = options(b.get_hardware(), std::string(domain), backend);
        if (!opts) return;
        std::string name;
        for (size_t i = 0; i < b.get_hardware()->row_count(); i++)
            if (auto d = b.get_hardware()->row_data(i); d && d->name == domain)
                if (auto be = d->backends->row_data(backend)) name = std::string(be->name);
        std::vector<std::pair<std::string, std::string>> values;
        for (size_t i = 0; i < opts->row_count(); i++)
            if (auto o = opts->row_data(i)) values.push_back({std::string(o->key), std::string(o->value)});
        action_("Applying", providerArgs(std::string(domain), name, values));
    });
    b.on_provider_file([this](slint::SharedString domain, int backend, slint::SharedString key) {
        if (root_.empty()) { message_("No file chooser: type the path"); return; }
        std::thread([this, chooser = root_ + "/bridge/portal/file-chooser.py", domain = std::string(domain), backend, key = std::string(key)] {
            Result r = runProgram({"python3", chooser, "open"});
            slint::invoke_from_event_loop([this, r, domain, backend, key] {
                std::string path = chosenFile(r);
                if (path.empty()) { message_(r.out.find("unavailable") != std::string::npos ? "No file chooser portal: type the path" : "No file chosen"); return; }
                setOption(options(ui_->global<Backend>().get_hardware(), domain, backend), key, path);
            });
        }).detach();
    });
    b.on_test_provider([this](slint::SharedString domain) {
        const Known* k = known(std::string(domain));
        if (!k || !*k->test) return;
        message_(std::string(k->test) + "…");
        // timeout: the client waits for the HAL service, which a stack that failed to start it never registers
        std::thread([this, k, argv = std::vector<std::string>{"timeout", "20", cli_.exe(), "--instance", cli_.instance, "exec", k->front, "--probe"}] {
            Result r = runProgram(argv);
            slint::invoke_from_event_loop([this, k, r] {
                std::string shown = r.status == 124 ? std::string("No answer from the ") + k->name + " service" : summary(r);
                for (const auto& row : parseRows(r.out + r.err))
                    if (!row.empty() && row[0].find(k->marker) != std::string::npos) shown = row[0];
                if (shown.rfind("PASS ", 0) == 0) shown = shown.substr(5);
                if (!shown.empty()) shown[0] = static_cast<char>(toupper(static_cast<unsigned char>(shown[0])));
                message_(shown);
                refresh_();  // the instance may have changed (a service that failed); the screenshot mode waits for it
            });
        }).detach();
    });
}

void Hardware::show(const Rows& providers) { ui_->global<Backend>().set_hardware(model(providers)); }

}  // namespace manager
