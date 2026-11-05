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
};
const Known* known(const std::string& name) {
    for (const Known& k : kKnown) if (name == k.name) return &k;
    return nullptr;
}

// the CLI's probe() answer for people
std::string probeText(const std::string& probe) {
    if (probe == "quality 0") return "available";
    if (probe.rfind("quality ", 0) == 0) return "available, " + probe;
    if (probe.rfind("refused: ", 0) == 0) return probe;  // a plugin built for another apklayer (its ABI)
    return probe == "unavailable" ? "not available here" : "cannot be loaded";
}

struct Domain {
    std::string name, provider, backend;
    std::vector<std::string> options;                                  // configured "key=value"
    std::vector<std::pair<std::string, std::string>> backends;         // name, probe
    std::vector<std::vector<std::string>> declared;                    // option rows: backend, key, default, kind
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
        return domains.emplace_back(Domain{name, "", "", {}, {}, {}});
    };
    for (const auto& r : rows) {  // `apklayer providers`: configured domains, then backend and option rows
        if (r.empty()) continue;
        if (r[0] == "backend") get(field(r, 1)).backends.push_back({field(r, 2), field(r, 3)});
        else if (r[0] == "option") get(field(r, 1)).declared.push_back({field(r, 2), field(r, 3), field(r, 4), field(r, 5)});
        else {
            Domain& d = get(r[0]);
            d.provider = field(r, 1);
            d.backend = field(r, 2);
            d.options.assign(r.begin() + std::min<size_t>(3, r.size()), r.end());
        }
    }
    auto rank = [](const Domain& d) { const Known* k = known(d.name); return k ? static_cast<size_t>(k - kKnown) : std::size(kKnown); };  // a fixed order, configured or not
    std::stable_sort(domains.begin(), domains.end(), [&](const Domain& a, const Domain& b) { return rank(a) < rank(b); });
    auto out = std::make_shared<slint::VectorModel<HwDomain>>();
    for (Domain& d : domains) {
        if (!d.backend.empty() && std::none_of(d.backends.begin(), d.backends.end(), [&](const auto& b) { return b.first == d.backend; }))
            d.backends.push_back({d.backend, "not staged"});
        auto menu = std::make_shared<slint::VectorModel<MenuItem>>();
        auto backends = std::make_shared<slint::VectorModel<HwBackend>>();
        MenuItem none;
        none.text = "Not set";
        none.enabled = true;
        menu->push_back(none);
        backends->push_back(backend("", std::make_shared<slint::VectorModel<HwOption>>()));
        int current = 0;
        for (const auto& [name, probe] : d.backends) {
            MenuItem m;
            m.text = slint::SharedString(name);
            m.trailing_text = slint::SharedString(probe == "not staged" ? probe : probeText(probe));
            m.enabled = probe.rfind("quality ", 0) == 0;
            menu->push_back(m);
            bool configured = name == d.backend;
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
        std::string configured = d.backend.empty() ? "" : d.provider + " · " + d.backend;
        for (const std::string& o : d.options) configured += " · " + o;
        h.configured = slint::SharedString(configured);
        h.current = current;
        h.menu = menu;
        h.backends = backends;
        h.test = slint::SharedString(k && current > 0 ? k->test : "");
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
