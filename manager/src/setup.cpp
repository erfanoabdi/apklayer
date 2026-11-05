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
    bool runtimeNewer() const { return release.runtime.found() && !release.runtimeVersion.empty() && compareVersions(release.runtimeVersion, runtime.empty() ? "0" : runtime) > 0; }  // never a downgrade (a release index behind the installed runtime)
};

namespace {
void post(std::function<void()> f) { slint::invoke_from_event_loop(std::move(f)); }
std::string field(const std::vector<std::string>& row, size_t i) { return i < row.size() ? row[i] : ""; }
std::string baseName(const std::string& url) { return url.substr(url.rfind('/') + 1); }
std::string readText(const std::string& path) { std::stringstream s; s << std::ifstream(path, std::ios::binary).rdbuf(); return s.str(); }
std::string sentence(std::string s) { if (!s.empty()) s[0] = static_cast<char>(toupper(static_cast<unsigned char>(s[0]))); return s; }
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


// `apklayer setup <flags>`: onStep for each `step <id> <state> <detail>` line, onProgress for `progress <p>`; the exit status
int runCliSetup(const Cli& cli, std::vector<std::string> flags, const std::function<void(const std::string&, const std::string&, const std::string&)>& onStep,
                const std::function<void(double)>& onProgress) {
    flags.insert(flags.begin(), "setup");
    return cli.runLines(flags, [&](const std::string& line) {
        Rows rows = parseRows(line);
        if (rows.empty()) return;
        if (rows[0][0] == "step" && rows[0].size() >= 3) onStep(rows[0][1], rows[0][2], rows[0].size() > 3 ? rows[0][3] : "");
        else if (rows[0][0] == "progress" && rows[0].size() >= 2) onProgress(atof(rows[0][1].c_str()));
    });
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
    add("java", "Java", "Java 21 runs the apps", false);
    add("runtime", "Android runtime", runtime.empty() ? "From the current release" : "Version " + runtime, !runtime.empty());
    add("instance", "Default instance", "Instance " + cli_.instance, false);
    add("system-apps", "System apps", "Providers, documents and the WebView", false);
    auto& b = ui_->global<Backend>();
    b.set_setup_steps(steps_);
    b.set_first_run(true);
}

void Setup::binder(bool ready) {
    // only a setup's success shows: a run that just failed on missing devices keeps its "Failed"
    if (!ready || !ui_->global<Backend>().get_first_run() || ui_->global<Backend>().get_setup_running()) return;
    step("binder", "Done", "Ready for instance " + cli_.instance, false);
}

void Setup::check(bool now) {
    if (now) { show(probe(cli_)); return; }
    std::thread([this, cli = cli_] { State s = probe(cli); post([this, s] { show(s); }); }).detach();
}

void Setup::show(const State& s) {
    state_ = std::make_shared<State>(s);
    if (!s.error.empty()) fprintf(stderr, "apklayer-manager: update check: %s\n", s.error.c_str());
    else if (!s.release.deb.found() && compareVersions(s.release.version, s.version.empty() ? "0" : s.version) > 0)
        fprintf(stderr, "apklayer-manager: release %s has no package for %s on %s\n", s.release.version.c_str(), osCodename().c_str(), cpuNames()[0].c_str());
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
        detail = s.stale.size() == 1 ? "apklayer's converter changed since it was installed; its data stays." : "apklayer's converter changed since they were installed; their data stays.";
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
    if (e.empty() && s->runtimeNewer()) {  // the CLI's `setup --update` is the one place that downloads, checks and unpacks a runtime
        std::string what = "Downloading Android runtime " + s->release.runtimeVersion;
        int status = runCliSetup(cli, {"--update"}, [&](const std::string& id, const std::string& state, const std::string& detail) {
            if (id == "runtime") report(state == "failed" ? detail : detail.empty() ? what : detail, -1);
            if (id == "runtime" && state == "failed") e = detail;
        }, [&](double p) { report(what, p); });
        if (status != 0 && e.empty()) e = "The runtime was not installed";
    }
    // then the apps the (new) CLI on disk calls stale: converted again from their kept APKs, stopped first as Android stops an updated app
    Rows stale;
    std::string failed;
    for (const auto& row : cli.rows({"list", "--tsv"})) if (e.empty() && field(row, 8) == "1") stale.push_back(row);
    for (size_t i = 0; i < stale.size(); i++) {
        std::string pkg = field(stale[i], 0), label = field(stale[i], 1).empty() ? pkg : field(stale[i], 1);
        if (field(stale[i], 5) == "1") cli.run({"stop", pkg});
        report("Converting " + label + " again (" + std::to_string(i + 1) + " of " + std::to_string(stale.size()) + ")", static_cast<double>(i) / stale.size());
        // a failing app keeps its old install (the CLI leaves it): every one is named, the others still convert
        if (Result r = cli.run({"reconvert", pkg}); r.status != 0) failed += (failed.empty() ? "" : "; ") + label + ": " + summary(r);
    }
    if (!failed.empty()) e = "Not converted again, the old version stays: " + failed;
    State next = probe(cli);
    post([this, e, next] {
        ui_->global<Backend>().set_updating(false);
        show(next);
        if (!e.empty()) ui_->global<Backend>().set_update_detail(slint::SharedString(sentence(e)));  // the banner keeps what failed
        message_(e.empty() ? "Updated" : sentence(e));
        refresh_();
    });
}

void Setup::runSetup(Cli cli, bool microg, bool fdroid) {
    auto stepLater = [this](std::string id, std::string state, std::string detail, bool running) { post([=, this] { step(id, state, detail, running); }); };
    auto progress = [this](double p) { post([this, p] { ui_->global<Backend>().set_setup_progress(static_cast<float>(p)); }); };
    // the CLI's `setup` does the work; its `step <id> <state> <detail>` lines become the UI's steps
    std::vector<std::string> flags;
    if (microg) flags.push_back("--microg");
    if (fdroid) flags.push_back("--fdroid");
    int status = runCliSetup(cli, flags, [&](const std::string& id, const std::string& state, const std::string& detail) {
        if (id == "webview") return;  // no row of its own
        stepLater(id == "providers" ? "instance" : id, state == "done" || state == "skipped" ? "Done" : state == "failed" ? "Failed" : "", state == "failed" ? sentence(detail) : detail, state == "running");
    }, progress);
    post([this, ok = status == 0] { auto& b = ui_->global<Backend>(); b.set_setup_running(false); b.set_setup_done(ok); refresh_(); });
}

void Setup::wire() {
    auto& b = ui_->global<Backend>();
    b.on_setup_start([this](bool microg, bool fdroid) {
        auto& b = ui_->global<Backend>();
        for (size_t i = steps_->row_count(); i-- > 0;)  // a retry: the extras as chosen now
            if (auto row = steps_->row_data(i); row && (row->id == "microg" || row->id == "fdroid")) steps_->erase(i);
        if (microg) steps_->push_back(SetupStep{"microg", "microG", "Services and Companion", "", false});
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
