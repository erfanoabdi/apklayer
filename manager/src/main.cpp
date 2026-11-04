// apklayer-manager: the Slint UI (ui/app.slint) over the apklayer CLI (src/backend.h). The CLI runs on worker threads; results come back to the
// UI thread with slint::invoke_from_event_loop. Usage: apklayer-manager [--instance ID] [--first-run] [--screenshot OUT.png [--size WxH] [--page N] [--app PKG]
// [--dialog install|instance|prop|uninstall] [--dark|--light] [--click LABEL | --type LABEL=TEXT]... [--wait-refresh N] [--expect LABEL]...] (the screenshot mode: presses the labelled
// elements, waits for N refreshes when asked (the actions' results), renders once, writes the PNG and exits: the tests' view and input).
#include <png.h>

#include <cstdio>
#include <cstdlib>
#include <functional>
#include <optional>
#include <thread>
#include <unistd.h>

#include "app.h"
#include "slint-testing.h"
#include "backend.h"
#include "setup.h"

using manager::Cli;
using manager::Result;
using manager::Rows;
using manager::summary;

namespace {

std::string field(const std::vector<std::string>& row, size_t i) { return i < row.size() ? row[i] : ""; }

// What the selected app's detail shows besides the list's row: `info` and `permissions` (whose failure, an install older than the list, is a note there)
struct Detail {
    Rows info, permissions;
    std::string error, permissionsNote;
};

struct Snapshot {
    Rows apps, instances, props, status, gpus;
    std::optional<Detail> detail;
    std::string error;  // the first CLI call that failed: the snackbar shows it instead of leaving its page empty
};

// the rows of one CLI call; its failure into error (the first one kept)
Rows rows(const Cli& cli, const std::vector<std::string>& args, std::string& error) {
    Result r = cli.run(args);
    if (r.status != 0 && error.empty()) error = r.status == 127 && r.err.empty() ? "Cannot run " + cli.exe() : summary(r);
    return manager::parseRows(r.out);
}

Detail loadDetail(const Cli& cli, const std::string& pkg) {
    Detail d;
    d.info = rows(cli, {"info", pkg}, d.error);
    d.permissions = rows(cli, {"permissions", pkg}, d.permissionsNote);
    return d;
}

Snapshot load(const Cli& cli, const std::string& current) {
    Snapshot s;
    s.apps = rows(cli, {"list", "--tsv"}, s.error);
    s.instances = rows(cli, {"instances"}, s.error);
    s.props = rows(cli, {"prop"}, s.error);
    s.status = rows(cli, {"status"}, s.error);
    s.gpus = rows(cli, {"gpus"}, s.error);
    if (!current.empty()) s.detail = loadDetail(cli, current);
    return s;
}

class Manager {
public:
    Manager(slint::ComponentHandle<AppWindow> ui, Cli cli) : ui_(ui), cli_(std::move(cli)) {
        std::string exe = cli_.exe();
        if (size_t bin = exe.rfind("/bin/"); bin != std::string::npos) root_ = exe.substr(0, bin);
        if (access((root_ + "/share/apklayer/tools").c_str(), F_OK) == 0) root_ += "/share/apklayer";  // installed (/usr/bin/apklayer)
        wire();
        setup_ = std::make_unique<manager::Setup>(ui_, cli_, root_, [this] { refresh(); }, [this](const std::string& m) { message(m); });
    }
    manager::Setup& setup() { return *setup_; }

    void apply(const Snapshot& s) {
        auto& b = ui_->global<Backend>();
        auto apps = std::make_shared<slint::VectorModel<AppEntry>>();
        for (const auto& r : s.apps) {
            AppEntry e;
            e.package = slint::SharedString(field(r, 0));
            e.label = slint::SharedString(field(r, 1));
            e.version = slint::SharedString(field(r, 2));
            e.icon = slint::Image::load_from_path(slint::SharedString(field(r, 3) + "/icon.png"));
            e.running = field(r, 5) == "1";
            e.system = field(r, 6) == "1";
            e.in_drawer = field(r, 7) != "0";
            if (field(r, 0) == current_) b.set_current_app(e);
            apps->push_back(e);
        }
        bool found = false;
        for (const auto& r : s.apps) found = found || field(r, 0) == current_;
        if (!found) current_.clear();
        b.set_has_current_app(!current_.empty());
        b.set_apps(apps);
        auto instances = std::make_shared<slint::VectorModel<InstanceEntry>>();
        bool running = false, binder = false;
        for (const auto& r : s.instances) {
            if (field(r, 0) == cli_.instance) binder = field(r, 2) == "1";
            InstanceEntry e;
            e.id = slint::SharedString(field(r, 0));
            e.running = field(r, 1) == "1";
            e.binder = field(r, 2) == "1";
            e.root = slint::SharedString(field(r, 3));
            instances->push_back(e);
            if (field(r, 0) == cli_.instance) running = field(r, 1) == "1";
        }
        b.set_instances(instances);
        b.set_instance_running(running);
        if (setup_) setup_->binder(binder);
        auto props = std::make_shared<slint::VectorModel<PropEntry>>();
        for (const auto& r : s.props) {
            PropEntry e;
            e.key = slint::SharedString(field(r, 0));
            e.value = slint::SharedString(field(r, 1));
            e.source = slint::SharedString(field(r, 2));
            e.live = slint::SharedString(field(r, 3));
            props->push_back(e);
        }
        b.set_props(props);
        b.set_status(info(s.status));
        auto gpus = std::make_shared<slint::VectorModel<MenuItem>>();
        gpuIds_.clear();
        int chosen = -1;
        for (const auto& r : s.gpus) {  // id, node, driver, vendor:device, compositor, instance, name (the first row: the header)
            if (field(r, 0) == "id") continue;
            if (field(r, 5) == "yes") chosen = static_cast<int>(gpuIds_.size());
            gpuIds_.push_back(field(r, 0));
            MenuItem m;
            m.text = slint::SharedString(field(r, 6));
            m.trailing_text = slint::SharedString(field(r, 1) + (field(r, 4) == "yes" ? " (desktop)" : ""));
            m.enabled = true;
            gpus->push_back(m);
        }
        b.set_gpus(gpus);
        b.set_gpu_index(chosen);
        if (!current_.empty() && s.detail) showDetail(*s.detail);
        b.set_load_error(slint::SharedString(s.error));
        if (!s.error.empty()) message(s.error);
        else if (s.detail && !s.detail->error.empty()) message(s.detail->error);
        if (applied) applied();
    }

    std::function<void()> applied;  // the screenshot mode's event: a refresh reached the window

    void refresh() {
        later([cli = cli_, current = current_] { return load(cli, current); }, [this](const Snapshot& s) { apply(s); });
    }
    void refreshNow() { apply(load(cli_, current_)); }  // the screenshot mode: everything in place before the first frame
    void select(const std::string& pkg) {
        current_ = pkg;
        refreshNow();
    }

private:
    static std::shared_ptr<slint::VectorModel<InfoEntry>> info(const Rows& rows) {
        auto m = std::make_shared<slint::VectorModel<InfoEntry>>();
        for (const auto& r : rows) {
            InfoEntry e;
            e.key = slint::SharedString(field(r, 0));
            e.value = slint::SharedString(field(r, 1));
            m->push_back(e);
        }
        return m;
    }

    // `apklayer info` for people: titles, sizes in MB; package, label and version are in the detail's header already
    static std::shared_ptr<slint::VectorModel<InfoEntry>> appInfo(const Rows& rows) {
        static const std::vector<std::pair<std::string, std::string>> titles = {
            {"versionCode", "Version code"}, {"targetSdk", "Target SDK"}, {"launcher", "Launcher activity"}, {"uid", "Android user id"},
            {"codeSize", "App size"}, {"dataSize", "Data in this instance"}, {"dir", "Installed in"}, {"dataDir", "Data directory"}};
        Rows out;
        for (const auto& [key, title] : titles)
            for (const auto& r : rows) {
                if (field(r, 0) != key) continue;
                std::string v = field(r, 1);
                if (key.size() > 4 && key.substr(key.size() - 4) == "Size") {
                    char mb[32];
                    snprintf(mb, sizeof mb, "%.1f MB", atoll(v.c_str()) / 1e6);
                    v = mb;
                }
                out.push_back({title, v});
            }
        return info(out);
    }

    void showDetail(const Detail& d) {
        auto& b = ui_->global<Backend>();
        b.set_app_info(appInfo(d.info));
        b.set_permissions_note(slint::SharedString(d.permissionsNote));
        b.set_app_permissions(permissions(d.permissions));
    }

    // `apklayer permissions` rows (permission, level, granted): the runtime ones first, each with a title ("ACCESS_FINE_LOCATION" -> "Access fine location")
    static std::shared_ptr<slint::VectorModel<PermEntry>> permissions(const Rows& rows) {
        std::vector<PermEntry> runtime, other;
        for (const auto& r : rows) {
            PermEntry e;
            std::string name = field(r, 0), level = field(r, 1), granted = field(r, 2), title = name.substr(name.rfind('.') + 1);
            for (size_t i = 0; i < title.size(); i++)
                title[i] = title[i] == '_' ? ' ' : static_cast<char>(i == 0 ? toupper(static_cast<unsigned char>(title[i])) : tolower(static_cast<unsigned char>(title[i])));
            e.name = slint::SharedString(name);
            e.title = slint::SharedString(title);
            e.runtime = level == "dangerous";
            e.granted = granted == "1";
            e.state = granted == "1" ? "Granted" : granted == "0" ? (level == "signature" ? "System apps only" : "Not granted") : "Set by the app defining it";
            (e.runtime ? runtime : other).push_back(e);
        }
        runtime.insert(runtime.end(), other.begin(), other.end());
        return std::make_shared<slint::VectorModel<PermEntry>>(runtime);
    }

    // work on a thread, then done(result) on the UI thread
    template <class Work, class Done> void later(Work work, Done done) {
        std::thread([work = std::move(work), done = std::move(done)]() mutable {
            auto result = work();
            slint::invoke_from_event_loop([result = std::move(result), done = std::move(done)]() mutable { done(result); });
        }).detach();
    }

    void message(const std::string& text) {
        auto& b = ui_->global<Backend>();
        b.set_message(slint::SharedString(text));
        b.set_message_serial(b.get_message_serial() + 1);
    }

    // a CLI action of the selected instance (or another): busy while it runs, then its result in the snackbar and a refresh
    void action(const std::string& what, std::vector<std::string> args, const std::string& instance = "") {
        auto& b = ui_->global<Backend>();
        b.set_busy(true);
        b.set_busy_text(slint::SharedString(what));
        Cli cli = cli_;
        if (!instance.empty()) cli.instance = instance;
        later([cli, args = std::move(args)] { return cli.run(args); }, [this](const Result& r) {
            ui_->global<Backend>().set_busy(false);
            message(summary(r));
            refresh();
        });
    }

    void wire() {
        auto& b = ui_->global<Backend>();
        b.set_instance(slint::SharedString(cli_.instance));
        b.on_refresh([this] { refresh(); });
        b.on_install_path([this](slint::SharedString path) { action("Installing", {"install", std::string(path)}); });
        b.on_install_chooser([this] {
            if (root_.empty()) { message("No file chooser: type the path of the .apk"); return; }
            later([chooser = root_ + "/bridge/portal/file-chooser.py"] { return manager::runProgram({"python3", chooser, "open"}); }, [this](const Result& r) {
                for (const auto& row : manager::parseRows(r.out))
                    if (row[0].rfind("uri ", 0) == 0) { ui_->global<Backend>().set_chosen_path(slint::SharedString(manager::fileUriPath(row[0].substr(4)))); return; }
                message(r.out.find("unavailable") != std::string::npos ? "No file chooser portal: type the path of the .apk" : "No file chosen");
            });
        });
        b.on_open_app([this](slint::SharedString pkg) {
            std::string p(pkg);
            message("Starting " + p);
            auto weak = slint::ComponentWeakHandle<AppWindow>(ui_);
            cli_.launch(p,
                [this, weak](const std::string& line) {
                    if (line.find("app jvm spawned") != std::string::npos) slint::invoke_from_event_loop([this, weak] { if (weak.lock()) refresh(); });
                },
                [this, weak, p](int status) {
                    slint::invoke_from_event_loop([this, weak, p, status] {
                        if (!weak.lock()) return;
                        if (status != 0 && status != 128 + 15) message(p + " ended with status " + std::to_string(status));
                        refresh();
                    });
                });
        });
        b.on_stop_app([this](slint::SharedString pkg) { action("Stopping", {"stop", std::string(pkg)}); });
        b.on_clear_data([this](slint::SharedString pkg) { action("Clearing data", {"clear-data", std::string(pkg)}); });
        b.on_uninstall([this](slint::SharedString pkg) { action("Uninstalling", {"uninstall", std::string(pkg)}); });
        b.on_set_permission([this](slint::SharedString pkg, slint::SharedString permission, bool granted) {
            action(granted ? "Granting" : "Revoking", {"permissions", granted ? "grant" : "revoke", std::string(pkg), std::string(permission)});
        });
        b.on_set_drawer([this](slint::SharedString pkg, bool shown) {
            action(shown ? "Showing in the app drawer" : "Hiding from the app drawer", {"launcher", shown ? "show" : "hide", std::string(pkg)});
        });
        b.on_select_app([this](slint::SharedString pkg) {
            current_ = std::string(pkg);
            auto& b = ui_->global<Backend>();
            for (size_t i = 0; i < b.get_apps()->row_count(); i++)
                if (auto e = b.get_apps()->row_data(i); e && e->package == pkg) b.set_current_app(*e);
            b.set_has_current_app(true);
            showDetail({});
            later([cli = cli_, p = current_] { return loadDetail(cli, p); }, [this, p = current_](const Detail& d) {
                if (p != current_) return;
                showDetail(d);
                if (!d.error.empty()) message(d.error);
            });
        });
        b.on_select_instance([this](slint::SharedString id) {
            cli_.instance = std::string(id);
            ui_->global<Backend>().set_instance(id);
            refresh();
        });
        b.on_create_instance([this](slint::SharedString id) {
            later([cli = cli_, id = std::string(id)] { return cli.run({"instance", "create", id}); }, [this, id = std::string(id)](const Result& r) {
                for (const auto& row : manager::parseRows(r.out))
                    if (row[0] == "binder" && field(row, 1) == "missing") {
                        auto& b = ui_->global<Backend>();
                        b.set_binder_command(slint::SharedString(field(row, 2)));
                        b.set_binder_instance(slint::SharedString(id));
                        b.set_binder_serial(b.get_binder_serial() + 1);
                    }
                message(r.status == 0 ? "Created instance " + id : summary(r));
                refresh();
            });
        });
        b.on_remove_instance([this](slint::SharedString id) {
            if (std::string(id) == cli_.instance) { cli_.instance = "default"; ui_->global<Backend>().set_instance("default"); }
            action("Removing instance", {"instance", "remove", std::string(id)});
        });
        b.on_setup_binder([this](slint::SharedString id) {
            if (root_.empty()) { message("The setup script is not found beside the CLI"); return; }
            // binderfs devices need root: polkit asks the desktop user for the password (pkexec), the command is in the dialog otherwise
            later([script = root_ + "/tools/host-setup/binder.sh", id = std::string(id)] { return manager::runProgram({"pkexec", "sh", script, id}); },
                  [this](const Result& r) {
                      message(r.status == 0 ? "Binder devices ready" : r.status == 126 || r.status == 127 ? "Not authorized: run the command shown" : summary(r));
                      refresh();
                  });
        });
        b.on_stop_instance([this](slint::SharedString id) { action("Stopping the instance", {"stop"}, std::string(id)); });
        b.on_set_gpu([this](int i) {
            if (i < 0 || i >= static_cast<int>(gpuIds_.size())) return;
            auto& b = ui_->global<Backend>();
            b.set_busy(true);
            b.set_busy_text("Changing the GPU");
            // the choice applies from the stack's next start: stop it, the next app launch starts it on the new GPU
            later([cli = cli_, id = gpuIds_[i]] { Result r = cli.run({"gpu", "set", id}); if (r.status == 0) cli.run({"stop"}); return r; },
                  [this](const Result& r) { ui_->global<Backend>().set_busy(false); message(summary(r)); refresh(); });
        });
        b.on_set_prop([this](slint::SharedString k, slint::SharedString v) { action("Saving", {"prop", "set", std::string(k), std::string(v)}); });
        b.on_unset_prop([this](slint::SharedString k) { action("Saving", {"prop", "unset", std::string(k)}); });
    }

    slint::ComponentHandle<AppWindow> ui_;
    std::vector<std::string> gpuIds_;  // the GPU menu's ids, in its order
    Cli cli_;
    std::string root_;     // the tree of the CLI (bin/apklayer): its portal helper and binder setup script
    std::string current_;  // the selected app (UI thread)
    std::unique_ptr<manager::Setup> setup_;  // the first run and the updates (src/setup.cpp)
};

bool writePng(const std::string& path, const slint::SharedPixelBuffer<slint::Rgba8Pixel>& pixels) {
    png_image image{};
    image.version = PNG_IMAGE_VERSION;
    image.width = pixels.width();
    image.height = pixels.height();
    image.format = PNG_FORMAT_RGBA;
    return png_image_write_to_file(&image, path.c_str(), 0, pixels.begin(), 0, nullptr) != 0;
}

}  // namespace

int main(int argc, char** argv) {
    Cli cli(Cli::locate());
    if (const char* id = getenv("APKLAYER_INSTANCE"); id && *id) cli.instance = id;
    std::string shot, app, dialog;
    std::vector<std::string> input, expect;
    int width = 0, height = 0, page = 0, dark = -1, waitRefreshes = 0, refreshes = 0;
    bool firstRun = false;
    for (int i = 1; i < argc; i++) {
        std::string a = argv[i];
        bool more = i + 1 < argc;
        if (a == "--instance" && more) cli.instance = argv[++i];
        else if (a == "--screenshot" && more) shot = argv[++i];
        else if (a == "--size" && more && sscanf(argv[++i], "%dx%d", &width, &height) == 2) {}
        else if (a == "--page" && more) page = atoi(argv[++i]);
        else if (a == "--app" && more) app = argv[++i];
        else if (a == "--dialog" && more) dialog = argv[++i];
        else if (a == "--dark" || a == "--light") dark = a == "--dark";
        else if ((a == "--click" || a == "--type") && more) input.push_back(argv[++i]);
        else if (a == "--wait-refresh" && more) waitRefreshes = atoi(argv[++i]);
        else if (a == "--expect" && more) expect.push_back(argv[++i]);
        else if (a == "--first-run") firstRun = true;
        else { fprintf(stderr, "usage: apklayer-manager [--instance ID] [--first-run] [--screenshot OUT.png [--size WxH] [--page N] [--app PKG] [--dialog NAME] [--dark|--light] [--click LABEL | --type LABEL=TEXT]... [--wait-refresh N] [--expect LABEL]...]\n"); return 2; }
    }
    auto ui = AppWindow::create();
    Manager m(ui, cli);
    if (width > 0 && height > 0) ui->window().set_size(slint::LogicalSize({static_cast<float>(width), static_cast<float>(height)}));
    ui->set_page(page);
    m.setup().firstRun(firstRun);
    if (shot.empty()) {
        m.refresh();
        m.setup().check(false);
        ui->run();
        return 0;
    }
    if (!app.empty()) { m.select(app); ui->set_app_detail_open(true); } else m.refreshNow();
    if (getenv("APKLAYER_RELEASES_URL")) m.setup().check(true);  // the screenshot mode reads releases.json only when a test names one
    ui->show();
    if (!dialog.empty()) ui->invoke_show_dialog(slint::SharedString(dialog));
    int status = 1;
    auto take = [&] {
        auto pixels = ui->window().take_snapshot();
        status = pixels && writePng(shot, *pixels) ? 0 : 1;
        if (status == 0) printf("apklayer-manager: %ux%u -> %s\n", pixels->width(), pixels->height(), shot.c_str());
        for (const std::string& label : expect)  // what the picture must show (a dialog's title...)
            if (slint::testing::ElementHandle::find_by_accessible_label(ui, label).empty()) { fprintf(stderr, "apklayer-manager: no %s on screen\n", label.c_str()); status = 1; }
        slint::quit_event_loop();
    };
    // the dialogs fade in over their first 50 ms (material Dialog): the picture is taken once they settled
    auto settled = [&] { slint::Timer::single_shot(std::chrono::milliseconds(600), take); };
    slint::Timer::single_shot(std::chrono::milliseconds(100), [&] {
        // the theme once the window has its own (the system's) scheme, which it would otherwise replace
        if (dark >= 0 && dark != ui->get_is_dark()) ui->invoke_toggle_theme();
        // scripted input: each label's control as a screen reader would press it (the last one: dialogs come after the pages)
        for (std::string label : input) {
            size_t eq = label.find('=');  // LABEL=TEXT types into a text field
            std::string text = eq == std::string::npos ? "" : label.substr(eq + 1);
            if (eq != std::string::npos) label.resize(eq);
            std::optional<slint::testing::ElementHandle> target;  // a button's own label text matches too: the control, not its text
            for (const auto& e : slint::testing::ElementHandle::find_by_accessible_label(ui, label))
                if (e.accessible_role() != slint::language::AccessibleRole::Text) target = e;
            if (!target) { fprintf(stderr, "apklayer-manager: nothing labelled %s\n", label.c_str()); slint::quit_event_loop(); return; }
            if (eq == std::string::npos) target->invoke_accessible_default_action();
            else target->set_accessible_value(slint::SharedString(text));
            printf("apklayer-manager: %s %s\n", eq == std::string::npos ? "pressed" : "typed into", label.c_str());
        }
        if (waitRefreshes == 0) { settled(); return; }
        // the picture after the actions' own events (an app's start, a finished uninstall) reached the window, not after a guessed delay
        m.applied = [&] { if (++refreshes == waitRefreshes) settled(); };
        slint::Timer::single_shot(std::chrono::minutes(3), [] { fputs("apklayer-manager: the refresh never came\n", stderr); slint::quit_event_loop(); });
    });
    slint::run_event_loop();
    return status;
}
