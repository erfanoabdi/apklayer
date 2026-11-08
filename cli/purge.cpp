#include "purge.h"
#include "flatpak.h"
#include "sandbox.h"
#include "stack.h"

#include <cstdio>
#include <unistd.h>

namespace apklayer {
namespace {
void quiet(const std::vector<std::string>& argv) {
    Spawn s;
    s.argv = argv;
    s.env = currentEnv();
    s.logFile = "/dev/null";  // a unit that was never there, a tree already gone: nothing to report
    runAndWait(s);
}
}  // namespace

int removeAllCommand(const Layout& l, const std::vector<std::string>& args) {
    std::string data = hostXdg("XDG_DATA_HOME", ".local/share"), config = hostXdg("XDG_CONFIG_HOME", ".config");
    std::string cache = getenvOr("XDG_CACHE_HOME", join(homeDir(), ".cache")), units = join(config, "systemd/user");
    std::vector<std::string> trees = {join(l.dataHome, "apklayer"), join(l.stateHome, "apklayer"), join(cache, "apklayer"), join(config, "apklayer")};
    std::vector<std::string> files = {join(config, "menus/applications-merged/apklayer.menu"), join(data, "desktop-directories/apklayer.directory"),
                                      join(units, "apklayer-instance@.service"), join(units, "apklayerd.service")};
    for (const std::string& name : listDir(join(data, "applications")))
        if (name.rfind("apklayer-", 0) == 0 && name.size() > 8 && name.compare(name.size() - 8, 8, ".desktop") == 0) files.push_back(join(data, "applications/" + name));
    if (args.size() != 1 || args[0] != "--yes") {
        puts("apklayer remove-all --yes deletes, for this user (every Android app and its data, which cannot be undone):");
        for (const std::string& t : trees) if (exists(t)) printf("  %s\n", t.c_str());
        for (const std::string& f : files) if (exists(f)) printf("  %s\n", f.c_str());
        puts("then remove the package itself: sudo apt purge apklayer apklayer-dev");
        return args.empty() ? 0 : 2;
    }
    // every instance stopped, its always-on unit off, the folders the sandbox's app uids own taken back before the trees go
    for (const std::string& name : listDir(join(l.dataHome, "apklayer"))) {
        std::string root = join(l.dataHome, "apklayer/" + name);
        if (!isDir(join(root, "storage")) && !isDir(join(root, "data"))) continue;
        Instance inst = Instance::open(l, name == "0" ? "default" : name);
        stopStack(l, inst);  // a stopped one: nothing to end
        quiet({"systemctl", "--user", "disable", "--now", "apklayer-instance@" + inst.id + ".service"});
        for (const std::string& app : listDir(join(inst.root, "app-data"))) reclaim(join(inst.root, "app-data/" + app));
        for (const std::string& tmp : listDir(inst.tmpDir())) reclaim(join(inst.tmpDir(), tmp));
        removeTree(inst.runtimeDir);
    }
    quiet({"systemctl", "--user", "disable", "--now", "apklayerd.service"});
    for (const std::string& name : listDir(units))
        if (name.rfind("apklayer-instance@", 0) == 0 && isDir(join(units, name))) removeTree(join(units, name));  // drop-ins
    for (const std::string& f : files) unlink(f.c_str());
    for (const std::string& t : trees) {
        if (exists(t)) quiet({"chmod", "-R", "u+w", t});  // apps leave read-only folders behind
        removeTree(t);
    }
    quiet({"systemctl", "--user", "daemon-reload"});
    quiet({"update-desktop-database", join(data, "applications")});
    puts("apklayer: removed this user's apps, data, runtime, logs, desktop entries and units; the package: sudo apt purge apklayer apklayer-dev");
    return 0;
}
}  // namespace apklayer
