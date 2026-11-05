// The Apps page's add-ons (microG, F-Droid): the optional apps of the first run, offered at any time. Installs and updates go through the
// first run's own path (`apklayer setup --microg|--fdroid`, src/setup.h); a removal uninstalls their packages.
#pragma once

#include <functional>
#include <string>

#include "app.h"
#include "backend.h"

namespace manager {

class Addons {
public:
    Addons(slint::ComponentHandle<AppWindow> ui, const Cli& cli, std::function<void()> refresh, std::function<void(const std::string&)> message);
    // what is installed, from the last refresh's `list --tsv` rows
    void show(const Rows& apps);

private:
    void run(const std::string& id, bool remove);

    slint::ComponentHandle<AppWindow> ui_;
    const Cli& cli_;
    std::function<void()> refresh_;
    std::function<void(const std::string&)> message_;
    Rows apps_;
};

}  // namespace manager
