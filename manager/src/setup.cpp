#include "setup.h"

#include <algorithm>
#include <dirent.h>
#include <fstream>
#include <sstream>
#include <thread>
#include <unistd.h>

#include "fetch.h"

namespace manager {

struct Setup::State {
    Release release;
    std::string error, version, runtime;  // the check's failure, the CLI's version ("" when it does not say: older than --version), the runtime in use
    Rows stale;                           // list --tsv rows of the apps converted with an older converter ABI
    bool debNewer() const { return release.deb.found() && compareVersions(release.version, version.empty() ? "0" : version) > 0; }
    bool runtimeNewer() const { return release.runtime.found() && !release.runtimeVersion.empty() && release.runtimeVersion != runtime; }
};

namespace {
void post(std::function<void()> f) { slint::invoke_from_event_loop(std::move(f)); }
std::string field(const std::vector<std::string>& row, size_t i) { return i < row.size() ? row[i] : ""; }
std::string baseName(const std::string& url) { return url.substr(url.rfind('/') + 1); }
std::string readText(const std::string& path) { std::stringstream s; s << std::ifstream(path, std::ios::binary).rdbuf(); return s.str(); }
std::string envOr(const char* name, const char* fallback) { const char* v = getenv(name); return v && *v ? v : fallback; }
std::string marker() { std::string r = runtimeRoot(); return r.substr(0, r.rfind('/')) + "/setup-done"; }  // beside runtime/ in the data home

Setup::State probe(const Cli& cli) {
    Setup::State s;
    for (const auto& row : parseRows(cli.run({"--version"}).out)) if (field(row, 0) == "version") s.version = field(row, 1);
    s.runtime = installedRuntime(runtimeRoot());
    for (const auto& row : cli.rows({"list", "--tsv"})) if (field(row, 8) == "1") s.stale.push_back(row);
    std::string file = downloadsDir() + "/releases.json";
    s.error = download(releasesUrl(), file, nullptr);
    if (s.error.empty()) s.release = parseLatest(readText(file), osCodename(), cpuNames(), s.error);
    return s;
}

std::string getRuntime(const Release& r, const Progress& progress) {
    std::string file = downloadsDir() + "/" + baseName(r.runtime.url);
    std::string e = fetch(r.runtime.url, r.runtime.sha256, file, progress);
    if (e.empty()) e = unpackRuntime(file, runtimeRoot(), r.runtimeVersion);
    unlink(file.c_str());
    return e;
}

using Report = std::function<void(const std::string&, double)>;

// packages of an F-Droid repository: its index, each APK checked against the index's SHA256, then `apklayer install`
std::string installFromIndex(const Cli& cli, const std::string& indexUrl, const std::vector<std::string>& packages, const Report& report) {
    std::string index = downloadsDir() + "/index-" + std::to_string(std::hash<std::string>{}(indexUrl)) + ".json";
    report("Reading the repository's index", -1);
    if (std::string e = download(indexUrl, index, nullptr); !e.empty()) return e;
    std::string text = readText(index);
    unlink(index.c_str());
    for (const std::string& pkg : packages) {
        std::string error;
        IndexApk apk = pickApk(text, pkg, androidAbi(), error);
        if (!error.empty()) return error;
        std::string file = downloadsDir() + "/" + baseName(apk.url);
        if (std::string e = fetch(apk.url, apk.sha256, file, [&](double p) { report("Downloading " + pkg, p); }); !e.empty()) return e;
        report("Installing " + pkg, -1);
        Result r = cli.run({"install", file});
        unlink(file.c_str());
        if (r.status != 0) return summary(r);
    }
    return "";
}

std::vector<std::string> apks(const std::string& dir) {
    std::vector<std::string> found;
    if (DIR* d = opendir(dir.c_str())) {
        while (dirent* e = readdir(d)) { std::string n = e->d_name; if (n.size() > 4 && n.substr(n.size() - 4) == ".apk") found.push_back(n); }
        closedir(d);
    }
    std::sort(found.begin(), found.end());
    return found;
}
}  // namespace

Setup::Setup(slint::ComponentHandle<AppWindow> ui, const Cli& cli, std::string root, std::function<void()> refresh, std::function<void(const std::string&)> message)
    : ui_(ui), cli_(cli), root_(std::move(root)), refresh_(std::move(refresh)), message_(std::move(message)), steps_(std::make_shared<slint::VectorModel<SetupStep>>()) {
    wire();
}

void Setup::step(const std::string& id, const std::string& state, const std::string& detail, bool running) {
    SetupStep s{slint::SharedString(id), "", slint::SharedString(detail), slint::SharedString(state), running};
    for (size_t i = 0; i < steps_->row_count(); i++)
        if (auto row = steps_->row_data(i); row && row->id == s.id) { s.title = row->title; steps_->set_row_data(i, s); return; }
}

void Setup::firstRun(bool force) {
    if (!force && (access(marker().c_str(), F_OK) == 0 || !cli_.rows({"list", "--tsv"}).empty())) return;
    bool binder = false;
    for (const auto& row : cli_.rows({"instances"})) if (field(row, 0) == cli_.instance) binder = field(row, 2) == "1";
    std::string runtime = installedRuntime(runtimeRoot());
    auto add = [&](const char* id, const char* title, const std::string& detail, bool done) {
        steps_->push_back(SetupStep{id, title, slint::SharedString(detail), done ? "Done" : "", false});
    };
    add("binder", "Binder devices", binder ? "Ready for instance " + cli_.instance : "Missing for instance " + cli_.instance, binder);
    add("runtime", "Android runtime", runtime.empty() ? "Downloaded from the current release" : "Version " + runtime, !runtime.empty());
    add("instance", "Default instance", "Instance " + cli_.instance + ": its own Android data and storage", false);
    add("system-apps", "System apps", "Android's providers, the documents UI and the WebView, from the runtime", false);
    auto& b = ui_->global<Backend>();
    b.set_setup_steps(steps_);
    b.set_first_run(true);
}

void Setup::binder(bool ready) {
    if (!ui_->global<Backend>().get_first_run() || ui_->global<Backend>().get_setup_running()) return;
    step("binder", ready ? "Done" : "", ready ? "Ready for instance " + cli_.instance : "Missing for instance " + cli_.instance, false);
}

void Setup::check(bool now) {
    if (now) { show(probe(cli_)); return; }
    std::thread([this, cli = cli_] { State s = probe(cli); post([this, s] { show(s); }); }).detach();
}

void Setup::show(const State& s) {
    state_ = std::make_shared<State>(s);
    if (!s.error.empty()) fprintf(stderr, "apklayer-manager: update check: %s\n", s.error.c_str());
    std::string title, detail, action, notes;
    if (s.debNewer()) {
        title = "apklayer " + s.release.version + " is available";
        detail = (s.release.date.empty() ? "" : "Released " + s.release.date + ". ") + (s.version.empty() ? "" : "You have " + s.version + ".");
        action = "Update";
        notes = s.release.notesUrl;
    } else if (s.runtimeNewer() && !s.runtime.empty()) {  // no runtime at all: the first run's, not the banner's
        title = "Android runtime " + s.release.runtimeVersion + " is available";
        detail = "You have runtime " + s.runtime + ".";
        action = "Update";
        notes = s.release.notesUrl;
    } else if (!s.stale.empty()) {
        title = s.stale.size() == 1 ? "1 app needs converting again" : std::to_string(s.stale.size()) + " apps need converting again";
        detail = "apklayer's converter changed since they were installed; their data stays.";
        action = "Convert again";
    }
    auto& b = ui_->global<Backend>();
    b.set_update_title(slint::SharedString(title));
    b.set_update_detail(slint::SharedString(detail));
    b.set_update_action(slint::SharedString(action));
    b.set_update_notes_url(slint::SharedString(notes));
}

void Setup::runUpdate(Cli cli, std::shared_ptr<State> s) {
    auto report = [this](const std::string& text, double p) {
        post([this, text, p] { auto& b = ui_->global<Backend>(); b.set_update_detail(slint::SharedString(text)); b.set_update_progress(static_cast<float>(p)); });
    };
    std::string e;
    if (s->debNewer()) {
        std::string file = downloadsDir() + "/" + baseName(s->release.deb.url), what = "Downloading apklayer " + s->release.version;
        e = fetch(s->release.deb.url, s->release.deb.sha256, file, [&](double p) { report(what, p); });
        if (e.empty()) {
            report("Installing apklayer " + s->release.version + " (it asks for your password)", -1);
            Result r = installDeb(file);
            if (r.status != 0) e = "The package was not installed: " + summary(r);
        }
        unlink(file.c_str());
    }
    if (e.empty() && s->runtimeNewer()) {
        std::string what = "Downloading Android runtime " + s->release.runtimeVersion;
        e = getRuntime(s->release, [&](double p) { report(what, p); });
    }
    // then the apps the (new) CLI on disk calls stale: converted again from their kept APKs, stopped first as Android stops an updated app
    Rows stale;
    for (const auto& row : cli.rows({"list", "--tsv"})) if (e.empty() && field(row, 8) == "1") stale.push_back(row);
    for (size_t i = 0; i < stale.size(); i++) {
        std::string pkg = field(stale[i], 0), label = field(stale[i], 1).empty() ? pkg : field(stale[i], 1);
        if (field(stale[i], 5) == "1") cli.run({"stop", pkg});
        report("Converting " + label + " again (" + std::to_string(i + 1) + " of " + std::to_string(stale.size()) + ")", static_cast<double>(i) / stale.size());
        if (Result r = cli.run({"reconvert", pkg}); r.status != 0 && e.empty()) e = label + ": " + summary(r);
    }
    State next = probe(cli);
    post([this, e, next] {
        ui_->global<Backend>().set_updating(false);
        show(next);
        if (!e.empty()) ui_->global<Backend>().set_update_detail(slint::SharedString(e));  // the banner keeps what failed
        message_(e.empty() ? "Updated" : e);
        refresh_();
    });
}

void Setup::runSetup(Cli cli, bool microg, bool fdroid) {
    auto stepLater = [this](std::string id, std::string state, std::string detail, bool running) { post([=, this] { step(id, state, detail, running); }); };
    auto progress = [this](double p) { post([this, p] { ui_->global<Backend>().set_setup_progress(static_cast<float>(p)); }); };
    auto finish = [this](bool done) {
        post([this, done] { auto& b = ui_->global<Backend>(); b.set_setup_running(false); b.set_setup_done(done); refresh_(); });
    };
    bool binder = false;
    for (const auto& row : cli.rows({"instances"})) if (field(row, 0) == cli.instance) binder = field(row, 2) == "1";
    if (!binder) { stepLater("binder", "Failed", "Missing for instance " + cli.instance + ": set up binder first", false); finish(false); return; }
    stepLater("binder", "Done", "Ready for instance " + cli.instance, false);

    stepLater("runtime", "", "Looking for the current release", true);
    progress(-1);
    State s = probe(cli);
    if (s.runtimeNewer()) {
        std::string what = "Downloading runtime " + s.release.runtimeVersion;
        stepLater("runtime", "", what, true);
        if (std::string e = getRuntime(s.release, progress); !e.empty()) { stepLater("runtime", "Failed", e, false); finish(false); return; }
        s.runtime = s.release.runtimeVersion;
    } else if (s.runtime.empty()) {
        stepLater("runtime", "Failed", s.error.empty() ? "The release has no runtime for " + cpuNames()[0] : s.error, false);
        finish(false);
        return;
    }
    stepLater("runtime", "Done", "Version " + s.runtime, false);

    Result r = cli.run({"instance", "create", cli.instance});
    stepLater("instance", r.status == 0 ? "Done" : "Failed", r.status == 0 ? "Instance " + cli.instance : summary(r), false);
    if (r.status != 0) { finish(false); return; }

    std::string dir = runtimeRoot() + "/current/system-apps", failed;
    std::vector<std::string> list = apks(dir);
    for (size_t i = 0; i < list.size(); i++) {
        stepLater("system-apps", "", "Installing " + list[i] + " (" + std::to_string(i + 1) + " of " + std::to_string(list.size()) + ")", true);
        progress(static_cast<double>(i) / list.size());
        if (Result a = cli.run({"install", "--system", dir + "/" + list[i]}); a.status != 0) failed += (failed.empty() ? "" : "; ") + list[i] + ": " + summary(a);
    }
    // the WebView provider's renderer runs without Chromium's sandbox, a switch only root can write (tools/host-setup/webview.sh)
    if (std::find(list.begin(), list.end(), "webview.apk") != list.end() && access("/data/local/tmp/webview-command-line", F_OK) != 0) {
        std::string script = root_ + "/tools/host-setup/webview.sh";
        if (runProgram({"pkexec", "sh", script}).status != 0) failed += (failed.empty() ? "" : "; ") + std::string("the WebView needs its switch: sudo sh ") + script;
    }
    stepLater("system-apps", failed.empty() ? "Done" : "Failed", failed.empty() ? std::to_string(list.size()) + " installed" : failed, false);

    auto extra = [&](const char* id, const std::string& index, const std::vector<std::string>& packages) {
        std::string e = installFromIndex(cli, index, packages, [&](const std::string& text, double p) { stepLater(id, "", text, true); progress(p); });
        stepLater(id, e.empty() ? "Done" : "Failed", e.empty() ? "Installed" : e, false);
    };
    if (microg) extra("microg", envOr("APKLAYER_MICROG_INDEX", "https://microg.org/fdroid/repo/index-v1.json"), {"com.google.android.gms", "com.android.vending"});
    if (fdroid) extra("fdroid", envOr("APKLAYER_FDROID_INDEX", "https://f-droid.org/repo/index-v1.json"), {"org.fdroid.fdroid"});
    finish(true);
}

void Setup::wire() {
    auto& b = ui_->global<Backend>();
    b.on_setup_start([this] {
        auto& b = ui_->global<Backend>();
        bool microg = b.get_want_microg(), fdroid = b.get_want_fdroid();
        for (size_t i = steps_->row_count(); i-- > 0;)  // a retry: the extras as chosen now
            if (auto row = steps_->row_data(i); row && (row->id == "microg" || row->id == "fdroid")) steps_->erase(i);
        if (microg) steps_->push_back(SetupStep{"microg", "microG Services and Companion", "From microG's repository", "", false});
        if (fdroid) steps_->push_back(SetupStep{"fdroid", "F-Droid", "From f-droid.org", "", false});
        b.set_setup_running(true);
        std::thread([this, cli = cli_, microg, fdroid] { runSetup(cli, microg, fdroid); }).detach();
    });
    b.on_setup_finish([this] {
        std::string m = marker();
        runProgram({"mkdir", "-p", m.substr(0, m.rfind('/'))});
        std::ofstream(m) << "1\n";
        ui_->global<Backend>().set_first_run(false);
        refresh_();
    });
    b.on_update_now([this] {
        if (!state_) return;
        auto& b = ui_->global<Backend>();
        b.set_updating(true);
        b.set_update_progress(-1);
        std::thread([this, cli = cli_, s = state_] { runUpdate(cli, s); }).detach();
    });
    b.on_open_notes([this] {
        std::string url(ui_->global<Backend>().get_update_notes_url());
        std::thread([url] { runProgram({"xdg-open", url}); }).detach();
    });
    b.on_dismiss_update([this] { ui_->global<Backend>().set_update_title(""); });
}

}  // namespace manager
