#include "desktop.h"

#include <unistd.h>

#include "apps.h"
#include "flatpak.h"

namespace apklayer {

namespace {
int sh(std::vector<std::string> argv) {
    Spawn s;
    s.argv = std::move(argv);
    s.env = currentEnv();
    return runAndWait(s);
}

std::string infoFile(const Layout& l, const std::string& package) { return join(appDir(l, package), "info.properties"); }

// a system app without a launcher activity (a provider) gets no entry: there is nothing to open
bool hasEntry(const std::string& info) { return !property(info, "launcher").empty() || !isSystemApp(info); }
}  // namespace

// the "Apklayer" submenu (freedesktop menu spec, as Waydroid's X-WayDroid-App): a merge file selects the X-Apklayer-App entries
// (the deb also installs data/apklayer.menu in /etc/xdg: the same submenu, merged by name)
void writeMenu(const Layout& l) {
    std::string config = hostXdg("XDG_CONFIG_HOME", ".config"), menu = join(config, "menus/applications-merged/apklayer.menu");
    static bool written;  // writeDesktop below calls this again: never loop when the file cannot be written
    std::string data = hostXdg("XDG_DATA_HOME", ".local/share");
    if (written || (exists(menu) && exists(join(data, "desktop-directories/apklayer.directory")))) return;  // another data home beside the same config: its own entry
    written = true;
    makeDirs(join(data, "desktop-directories"));
    writeFile(join(data, "desktop-directories/apklayer.directory"),
              "[Desktop Entry]\nType=Directory\nName=Apklayer\nIcon=" + getenvOr("FLATPAK_ID", "apklayer") + "\n");  // the Flatpak's icon is named by its id
    makeDirs(join(config, "menus/applications-merged"));
    writeFile(menu, "<!DOCTYPE Menu PUBLIC \"-//freedesktop//DTD Menu 1.0//EN\"\n \"http://www.freedesktop.org/standards/menu-spec/menu-1.0.dtd\">\n"
                    "<Menu>\n <Name>Applications</Name>\n <Menu>\n  <Name>Apklayer</Name>\n  <Directory>apklayer.directory</Directory>\n"
                    "  <Include><Category>X-Apklayer-App</Category></Include>\n </Menu>\n</Menu>\n");
    // the first menu: the entries written before it get their category
    for (const std::string& pkg : listDir(l.appsDir))
        if (exists(infoFile(l, pkg))) writeDesktop(l, pkg);
}

void setProperty(const std::string& file, const std::string& key, const std::string& value) {
    std::string out;
    bool done = false;
    for (const std::string& line : split(readFile(file).value_or(""), '\n')) {
        if (line.empty()) continue;
        if (line.rfind(key + "=", 0) == 0) { if (!done) out += key + "=" + value + "\n"; done = true; continue; }
        out += line + "\n";
    }
    if (!done) out += key + "=" + value + "\n";
    // a temporary file renamed over it: the hub and app JVMs never read half an info.properties
    if (!writeFile(file + ".tmp", out) || rename((file + ".tmp").c_str(), file.c_str()) != 0) die("cannot write " + file);
}

bool isSystemApp(const std::string& info) { return property(info, "system") == "true"; }

bool inDrawer(const std::string& info) {
    std::string drawer = property(info, "drawer");
    return hasEntry(info) && (drawer == "show" || (drawer != "hide" && !isSystemApp(info)));
}

void writeDesktop(const Layout& l, const std::string& pkg) {
    writeMenu(l);
    std::string apps = join(hostXdg("XDG_DATA_HOME", ".local/share"), "applications"), info = infoFile(l, pkg), entry = join(apps, "apklayer-" + pkg + ".desktop");
    makeDirs(apps);
    std::string cli = hostCli(l.cliBinary());
    if (hasEntry(info))
        writeFile(entry, "[Desktop Entry]\nType=Application\nName=" + property(info, "label") + "\nComment=Android app (" + pkg + ") via apklayer\nExec=" + cli + " run " + pkg +
                             "\nIcon=" + appDir(l, pkg) + "/icon.png\nStartupWMClass=" + pkg + "\nCategories=X-Apklayer-App;\nX-Lomiri-Touch=true\n" + (inDrawer(info) ? "" : "NoDisplay=true\n"));
    else unlink(entry.c_str());
    writeFile(join(apps, "apklayer-open-apk.desktop"),
              "[Desktop Entry]\nType=Application\nName=Android app (apklayer)\nExec=" + cli + " run %f\nMimeType=application/vnd.android.package-archive;\nNoDisplay=true\n");
    sh({"update-desktop-database", apps});
    sh({"xdg-mime", "default", "apklayer-open-apk.desktop", "application/vnd.android.package-archive"});
}

void refreshDesktops(const Layout& l) {
    // an entry of an earlier install whose CLI is gone (a tarball in /opt replaced by the RPM: the launcher's icon opened nothing) is written again
    for (const std::string& pkg : listDir(l.appsDir)) {
        std::string exec = property(join(l.dataHome, "applications/apklayer-" + pkg + ".desktop"), "Exec");
        if (!exec.empty() && !exists(exec.substr(0, exec.find(' '))) && exists(infoFile(l, pkg))) writeDesktop(l, pkg);
    }
}

int launcherCommand(const Layout& l, const std::string& action, const std::string& pkg) {
    if (action != "show" && action != "hide") die("launcher show|hide <package>");
    std::string info = infoFile(l, pkg);
    if (!exists(info)) die("not installed: " + pkg);
    if (action == "show" && !hasEntry(info)) die(pkg + " has no launcher activity: nothing to show in the app drawer");
    setProperty(info, "drawer", action);
    writeDesktop(l, pkg);
    printf("apklayer: %s %s in the app drawer\n", action == "show" ? "showing" : "hiding", pkg.c_str());
    return 0;
}

void markSystemApps(const Layout& l) {
    // the staging's own list (tools/stage sysapps installs these with --system): package<TAB>where the APK comes from
    for (const std::string& line : split(readFile(join(l.root, "tools/sysapps.list")).value_or(""), '\n')) {
        std::string pkg = trim(line.substr(0, line.find('\t')));
        if (pkg.empty() || pkg[0] == '#') continue;
        std::string info = infoFile(l, pkg);
        if (!exists(info) || !property(info, "system").empty()) continue;  // only once: the manager lists on every refresh
        setProperty(info, "system", "true");
        writeDesktop(l, pkg);
        fprintf(stderr, "apklayer: %s marked as a system app\n", pkg.c_str());
    }
}

}  // namespace apklayer
