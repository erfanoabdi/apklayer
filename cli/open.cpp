#include "open.h"

#include <ctime>
#include <sys/stat.h>
#include <unistd.h>

#include "apps.h"
#include "run.h"
#include "sandbox.h"

namespace apklayer {

namespace {
int sh(std::vector<std::string> argv) {
    Spawn s;
    s.argv = std::move(argv);
    s.env = currentEnv();
    return runAndWait(s);
}

std::string encode(const std::string& name) {
    std::string out;
    for (unsigned char c : name) {
        if (isalnum(c) || c == '.' || c == '_' || c == '-' || c == '~') out += (char)c;
        else { char hex[4]; snprintf(hex, sizeof hex, "%%%02X", c); out += hex; }
    }
    return out;
}

// the desktop's own type for the file (shared-mime-info: the app's extensions are registered there, cli/filetypes.cpp)
std::string mimeOf(const std::string& path) {
    Spawn s;
    s.argv = {"xdg-mime", "query", "filetype", path};
    s.env = currentEnv();
    std::string type = trim(runCapture(s));
    return type.find('/') != std::string::npos && type.find(' ') == std::string::npos ? type : "application/octet-stream";
}
}  // namespace

// The file is copied into the app's own cache (opened/<token>/data, its type beside it) and the app started, or told if it runs, with an ACTION_VIEW of
// content://apklayer.opened/<token>/<name> and the read grant: the app's provider (HostOpenedFiles) serves that one file, never the host path.
int openCommand(const Layout& l, const Instance& inst, const std::vector<std::string>& args) {
    const std::string& package = args[0];
    if (!exists(join(appDir(l, package), "info.properties"))) die("not installed: " + package + " (apklayer install <app.apk>)");
    if (args.size() == 1) return runApp(l, inst, package, RunMode::App);
    std::string path = realPath(args[1]);
    if (path.empty() || !exists(path) || isDir(path)) die("no such file: " + args[1]);
    std::string token = sha256Text(path + std::to_string(getpid()) + std::to_string(time(nullptr))).substr(0, 16), root = join(appDataDir(l, inst, package), "app/cache/opened");  // the app's getCacheDir() (its data link "app", AppEnvironment.persistDataDirs)
    // shortcut: under the sandbox the app's data is its own uid's and out of the user's reach, so the copy lies in the instance's tmp (the app's java.io.tmpdir,
    // HostOpenedFiles reads both), world readable behind its unguessable token with the folder not listable; chown through the namespace would keep it private
    if (sandboxOn()) root = join(inst.tmpDir(), "opened");
    makeDirs(root);
    if (sandboxOn()) chmod(root.c_str(), 0711);
    // a day of them stays (an app may keep the Uri for a while); the cache is the app's own and clear-data removes it
    sh({"find", root, "-mindepth", "1", "-maxdepth", "1", "-mtime", "+1", "-exec", "rm", "-rf", "{}", "+"});
    makeDirs(join(root, token));
    if (sh({"cp", "--reflink=auto", path, join(root, token + "/data")}) != 0) die("cannot copy " + path);
    std::string type = mimeOf(path);
    writeFile(join(root, token + "/type"), type + "\n");
    if (sandboxOn()) for (const char* f : {"", "/data", "/type"}) chmod(join(root, token + f).c_str(), *f ? 0644 : 0755);
    std::string intent = join(inst.tmpDir(), "open-" + std::to_string(getpid()) + ".properties");
    makeDirs(inst.tmpDir());
    // Properties of apklayer.host.HostApps: action, type, data, flags (FLAG_GRANT_READ_URI_PERMISSION)
    if (!writeFile(intent, "action=android.intent.action.VIEW\ntype=" + type + "\ndata=content://apklayer.opened/" + token + "/" + encode(baseName(path)) + "\nflags=1\n"))
        die("cannot write " + intent);
    setenv("APKLAYER_INTENT_FILE", intent.c_str(), 1);
    return runApp(l, inst, package, RunMode::App);
}

}  // namespace apklayer
