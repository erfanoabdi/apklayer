#include "addons.h"

#include <thread>

#include "setup.h"

namespace manager {

namespace {
struct Addon {
    const char *id, *title, *description;
    std::vector<std::string> packages;  // the first one's version is the add-on's
    // microG and Google's own share package names: the Services Framework (Google's only) tells whose they are
    const char* needs = nullptr;     // counted as installed only with this package
    const char* unless = nullptr;    // not counted as installed with this package
};
// the first run's optional apps (setup.slint, cli/setup.cpp) and Google's own: microG is its Services and its Companion (Play Store stand-in), apps need both
const std::vector<Addon> kAddons = {
    {"microg", "microG", "Google's services, for apps that need them (Services and Companion)", {"com.google.android.gms", "com.android.vending"}, nullptr, "com.google.android.gsf"},
    // Google's own (cli/gapps.cpp, `setup --gapps`): one or the other with microG
    {"gapps", "Google Play", "Google's own Play services and Play Store (replaces microG)",
     {"com.google.android.gms", "com.google.android.gsf", "com.android.vending", "com.google.android.syncadapters.contacts", "com.google.android.syncadapters.calendar"},
     "com.google.android.gsf"},
    {"fdroid", "F-Droid", "A store of free software apps", {"org.fdroid.fdroid"}},
};
const Addon* find(const std::string& id) {
    for (const Addon& a : kAddons) if (id == a.id || id == a.title) return &a;  // the confirmation dialog names it by its title
    return nullptr;
}
std::string field(const std::vector<std::string>& row, size_t i) { return i < row.size() ? row[i] : ""; }
void post(std::function<void()> f) { slint::invoke_from_event_loop(std::move(f)); }
}  // namespace

Addons::Addons(slint::ComponentHandle<AppWindow> ui, const Cli& cli, std::function<void()> refresh, std::function<void(const std::string&)> message)
    : ui_(ui), cli_(cli), refresh_(std::move(refresh)), message_(std::move(message)) {
    auto& b = ui_->global<Backend>();
    b.on_addon_install([this](slint::SharedString id) { run(std::string(id), false); });
    b.on_addon_remove([this](slint::SharedString id) { run(std::string(id), true); });
}

void Addons::show(const Rows& apps) {
    apps_ = apps;
    auto& b = ui_->global<Backend>();
    std::string running(b.get_addon_running());
    auto model = std::make_shared<slint::VectorModel<AddonEntry>>();
    for (const Addon& a : kAddons) {
        AddonEntry e;
        e.id = a.id;
        e.title = a.title;
        e.description = a.description;
        int have = 0;
        std::string version, dir;
        auto has = [&](const char* pkg) { for (const auto& row : apps) if (field(row, 0) == pkg) return true; return false; };
        const bool theirs = (a.needs && !has(a.needs)) || (a.unless && has(a.unless));
        for (const std::string& pkg : a.packages)
            for (const auto& row : apps)
                if (!theirs && field(row, 0) == pkg) {
                    have++;
                    if (pkg == a.packages[0] || dir.empty()) { version = field(row, 2); dir = field(row, 3); }
                }
        if (!dir.empty()) e.icon = slint::Image::load_from_path(slint::SharedString(dir + "/icon.png"));
        e.installed = have > 0;
        e.complete = have == static_cast<int>(a.packages.size());
        e.version = slint::SharedString(version);
        e.running = running == a.id;
        e.detail = e.running ? b.get_addon_detail() : slint::SharedString(a.description);
        model->push_back(e);
    }
    b.set_addons(model);
}

void Addons::run(const std::string& id, bool remove) {
    const Addon* a = find(id);
    auto& b = ui_->global<Backend>();
    if (!a || !std::string(b.get_addon_running()).empty()) return;
    b.set_addon_running(slint::SharedString(id));
    b.set_addon_progress(-1);
    b.set_addon_detail(remove ? "Removing" : "Starting");
    show(apps_);
    auto report = [this](std::string text) {
        post([this, text] { ui_->global<Backend>().set_addon_detail(slint::SharedString(text)); show(apps_); });
    };
    std::vector<std::string> installed;  // a removal: the packages there are
    for (const std::string& pkg : a->packages)
        for (const auto& row : apps_) if (field(row, 0) == pkg) installed.push_back(pkg);
    std::thread([this, cli = cli_, a, remove, report, installed] {
        std::string error;
        if (remove) {
            for (const std::string& pkg : installed) {
                report("Removing " + pkg);
                if (Result r = cli.run({"uninstall", pkg}); r.status != 0 && error.empty()) error = summary(r);
            }
        } else {
            // the first run's step for this add-on; the others (binder, runtime...) are done already and only checked again. A failing install
            // ends the CLI before its step says so: its last other line is the reason then
            std::string state, last;
            runCliSetup(cli, {std::string("--") + a->id}, [&](const std::string& step, const std::string& s, const std::string& detail) {
                if (step != a->id) return;
                state = s;
                if (s == "running") report(detail);
                if (s == "failed") error = detail;
            }, [this](double p) { post([this, p] { ui_->global<Backend>().set_addon_progress(static_cast<float>(p)); }); },
               [&](const std::string& line) { if (line.rfind("apklayer: ", 0) == 0) last = line.substr(10); });
            if (state != "done" && error.empty()) error = last.empty() ? "Not installed" : last;
        }
        post([this, a, remove, error] {
            auto& b = ui_->global<Backend>();
            b.set_addon_running("");
            message_(!error.empty() ? std::string(a->title) + ": " + error : std::string(a->title) + (remove ? " removed" : " is installed and up to date"));
            refresh_();
        });
    }).detach();
}

}  // namespace manager
