#include "portal.h"
#include "sandbox.h"

#include <cerrno>
#include <cstring>
#include <unistd.h>

namespace apklayer {

namespace {
std::string uriPath(const std::string& uri) {  // file:///a%20b -> /a b
    std::string p = uri.rfind("file://", 0) == 0 ? uri.substr(7) : uri, out;
    for (size_t i = 0; i < p.size(); i++)
        if (p[i] == '%' && i + 2 < p.size()) { out.push_back((char)strtol(p.substr(i + 1, 2).c_str(), nullptr, 16)); i += 2; }
        else out.push_back(p[i]);
    return out;
}

// The desktop's dialog, as the app JVM's own picker runs it (HostPortals.pick): APKLAYER_PICKER (tests: a command printing paths), else the file
// chooser portal (bridge/portal/file-chooser.py), else zenity. One chosen path per entry; none: cancelled.
std::vector<std::string> pick(const Layout& l, const std::string& mode, bool multiple, const std::string& name) {
    Spawn s;
    s.env = currentEnv();
    std::string override = getenvOr("APKLAYER_PICKER", "");
    std::vector<std::string> paths;
    if (override.empty()) {
        s.argv = {"python3", join(l.root, "bridge/portal/file-chooser.py"), mode};
        if (multiple) s.argv.push_back("--multiple");
        if (!name.empty()) s.argv.insert(s.argv.end(), {"--name", name});
        bool available = true;
        for (const std::string& line : split(runCapture(s), '\n')) {
            if (line.rfind("uri ", 0) == 0) paths.push_back(uriPath(line.substr(4)));
            if (line.rfind("unavailable ", 0) == 0) available = false;
        }
        if (available) return paths;
        s.argv = {"zenity", "--file-selection", "--separator=\n"};
        if (mode == "save") { s.argv.push_back("--save"); if (!name.empty()) s.argv.push_back("--filename=" + name); }
        if (mode == "directory") s.argv.push_back("--directory");
        if (multiple) s.argv.push_back("--multiple");
    } else {
        s.argv = {"sh", "-c", override};
    }
    int status = 0;
    std::string out = runCapture(s, &status);
    if (status == 0) for (const std::string& line : split(out, '\n')) if (!trim(line).empty()) paths.push_back(trim(line));
    return paths;
}
}  // namespace

int portalCommand(const Layout& l, const Instance& inst, const std::vector<std::string>& args) {
    int peer = atoi(getenvOr("APKLAYER_PEER_PID", "0").c_str());  // set by apklayerd for a sandboxed app's request
    if (args.size() == 2 && args[0] == "open") {
        std::string scheme = args[1].substr(0, args[1].find(':'));
        for (const char* allowed : {"http", "https", "mailto", "tel", "geo"})  // as HostPortals.open: links, never files or programs
            if (scheme == allowed) { Spawn s; s.argv = {"xdg-open", args[1]}; s.env = currentEnv(); return runAndWait(s); }
        fprintf(stderr, "apklayer: portal open takes http(s), mailto, tel and geo links\n");
        return 2;
    }
    if (args.size() >= 2 && args[0] == "helper") {  // a D-Bus helper of bridge/portal, as the user: sandboxed processes reach no bus themselves
        if (args[1] != "geoclue.py" && args[1] != "bluez.py" && args[1] != "settings.py") { fprintf(stderr, "apklayer: no portal helper %s\n", args[1].c_str()); return 2; }
        std::vector<std::string> argv = {"python3", join(l.root, "bridge/portal/" + args[1])};
        argv.insert(argv.end(), args.begin() + 2, args.end());
        std::vector<char*> pointers;
        for (std::string& a : argv) pointers.push_back(a.data());
        pointers.push_back(nullptr);
        execvp(pointers[0], pointers.data());  // its stdin and stdout are the caller's (apklayerd passes them): a stream of lines, BlueZ's RFCOMM pipe
        die("cannot run python3: " + std::string(strerror(errno)));
    }
    if (args.size() < 2 || args[0] != "pick" || (args[1] != "open" && args[1] != "save" && args[1] != "directory")) {
        fprintf(stderr, "usage: apklayer portal pick open|save|directory [--multiple] [--name NAME] | open <uri>\n");
        return 2;
    }
    bool multiple = false;
    std::string name;
    for (size_t i = 2; i < args.size(); i++) {
        if (args[i] == "--multiple") multiple = true;
        else if (args[i] == "--name" && i + 1 < args.size()) name = args[++i];
    }
    for (const std::string& path : pick(l, args[1], multiple, name)) {
        if (args[1] == "save" && !exists(path)) writeFile(path, "");  // the app writes into the file it was handed
        // the one file or folder chosen is the app's to see, nothing beside it (an app outside the sandbox sees everything anyway)
        if (peer > 0 && !grantPath(inst, peer, path)) continue;
        printf("%s\n", path.c_str());
    }
    return 0;
}

}  // namespace apklayer
