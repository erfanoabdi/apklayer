// The manager's first run (binder, runtime, instance, system apps, microG or Google Play, F-Droid) and its updates (the banner: a new apklayer deb, a new
// runtime bundle, apps to convert again). The work runs on threads; the UI hears of it through Backend's setup-* and update-* properties.
#pragma once

#include <functional>
#include <memory>

#include "app.h"
#include "backend.h"
#include "releases.h"

namespace manager {

// `apklayer setup <flags>` (the first run's and the add-ons' one way in): onStep for each `step <id> <state> <detail>` line, onProgress for
// `progress <p>`, onOther for the rest (a failure's message); the exit status
int runCliSetup(const Cli& cli, std::vector<std::string> flags, const std::function<void(const std::string&, const std::string&, const std::string&)>& onStep,
                const std::function<void(double)>& onProgress, const std::function<void(const std::string&)>& onOther = nullptr);

class Setup {
public:
    // cli: the manager's (its instance follows the selection); root: the tree of the CLI (tools/host-setup); refresh and message: the manager's
    Setup(slint::ComponentHandle<AppWindow> ui, const Cli& cli, std::string root, std::function<void()> refresh, std::function<void(const std::string&)> message);
    // shows the first-run screen when forced, or when this data home was never set up and has no apps
    void firstRun(bool force);
    // releases.json against what is installed, then the banner; now: before the first frame (the screenshot mode), else on a thread
    void check(bool now);
    // the selected instance's binder devices as the last refresh saw them (the first run's binder step follows a setup)
    void binder(bool ready);

    struct State;  // what a check found (setup.cpp)

private:
    void wire();
    void show(const State& s);
    void step(const std::string& id, const std::string& state, const std::string& detail, bool running);
    void runSetup(Cli cli, std::string google, bool fdroid);  // google: "microg", "gapps" or ""
    void runUpdate(Cli cli, std::shared_ptr<State> s);

    slint::ComponentHandle<AppWindow> ui_;
    const Cli& cli_;
    std::string root_;
    std::function<void()> refresh_;
    std::function<void(const std::string&)> message_;
    std::shared_ptr<State> state_;  // the last check (UI thread)
    std::shared_ptr<slint::VectorModel<SetupStep>> steps_;
};

}  // namespace manager
