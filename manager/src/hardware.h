// The Hardware page (ui/hardware.slint): `apklayer providers` rows as one HwDomain per domain, and the page's actions (Apply, a file for an
// option, a domain's live test). The CLI runs on threads; results come back through the manager's action and message.
#pragma once

#include <functional>

#include "app.h"
#include "backend.h"

namespace manager {

class Hardware {
public:
    using Action = std::function<void(const std::string& what, std::vector<std::string> args)>;
    // cli: the manager's (its instance follows the selection); root: the tree of the CLI (the portal's file chooser)
    Hardware(slint::ComponentHandle<AppWindow> ui, const Cli& cli, std::string root, Action action, std::function<void(const std::string&)> message);
    void show(const Rows& providers);

private:
    slint::ComponentHandle<AppWindow> ui_;
    const Cli& cli_;
    std::string root_;
    Action action_;
    std::function<void(const std::string&)> message_;
};

}  // namespace manager
