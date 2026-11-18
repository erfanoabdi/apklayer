// Entry points for google-switch.py. Package writes go through its fake CLI.
#include "gapps.h"
#include "unit.h"
#include "util.h"

#include <cstdlib>
#include <iostream>

namespace apklayer {
std::string appDir(const Layout& layout, const std::string& package) {
    return join(layout.appsDir, package);
}
void scopeSpawn(Spawn&, const std::string&) { std::abort(); }
std::string Layout::cliBinary() const { return getenvOr("APKLAYER_CLI", ""); }
}

int main(int argc, char** argv) {
    using namespace apklayer;
    if (argc != 3) return 2;
    Layout layout;
    layout.root = argv[1];
    layout.dataHome = join(layout.root, "data");
    layout.appsDir = join(layout.dataHome, "apklayer/apps");
    Instance instance;
    instance.id = "switch-fixture";
    auto report = [](const std::string& line) { std::cout << line << '\n'; };
    auto progress = [](double) {};
    std::string error = std::string(argv[2]) == "gapps"
        ? installGapps(layout, instance, report, progress)
        : installMicrog(layout, instance, "file://" + join(layout.root, "index.json"),
                        {"com.google.android.gms", "com.android.vending"}, report, progress);
    if (!error.empty()) std::cerr << error << '\n';
    return error.empty() ? 0 : 1;
}
