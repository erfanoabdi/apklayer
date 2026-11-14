#include "config.h"

#include <algorithm>
#include <cstdio>
#include <cstdlib>

#include "util.h"

namespace apklayer {

namespace {
// kind: choice (the values of `choices`, comma separated) or text. env: the variable the runtime reads; a value equal to the default is not
// exported (the runtime's own default applies); map: "setting=exported" pairs where the runtime spells a choice differently. app: also per app.
struct Setting { const char *key, *env, *kind, *def, *choices, *map, *title, *help; bool app; };
const Setting kSettings[] = {
    {"gl-hide", "APKLAYER_GL_HIDE", "text", "", "", "", "Hidden GL extensions",
     "GL extensions hidden from Android code, separated by spaces (GL_KHR_texture_compression_astc_ldr): for a driver that advertises a format it decodes wrongly", true},
    {"gles-claim", "APKLAYER_GLES_OVERRIDE", "choice", "auto", "auto,off", "", "OpenGL ES 3.2 claim",
     "auto: apps are told OpenGL ES 3.2 where the host driver is older but copes; off: the driver's own version", false},
    {"vulkan", "APKLAYER_VULKAN_PRESENT", "choice", "auto", "auto,off,on", "off=0,on=1", "Vulkan",
     "auto: Vulkan for apps where the host can present; off: none; on: also where the probe finds no device", true},
    {"aot", "APKLAYER_AOT", "choice", "on", "on,off", "", "Ahead-of-time cache",
     "on: the app JVMs start from an AOT cache (JDK 25); off: the class data cache only", false},
    {"jvm-mem", "APKLAYER_JVM_MEM", "choice", "tuned", "tuned,default", "", "JVM memory",
     "tuned: apklayer's heap and compiler sizing; default: the JVM's own", true},
    {"jvm-options", "APKLAYER_JOPTS", "text", "", "", "", "Extra JVM options", "Options for the app's JVM, separated by spaces", true},
    {"java", "APKLAYER_NATIVE_JAVA", "text", "", "", "", "Java 21 program", "The java the apps run on, when it is not the system's (a phone's small root file system)", false},
    {"permissions", "APKLAYER_PERMISSIONS", "choice", "ask", "ask,grant", "", "Runtime permissions",
     "ask: the permission prompt; grant: every runtime permission a started app requests is granted without asking", true},
    {"proc-view", "APKLAYER_PROC_VIEW", "choice", "app", "app,host", "", "/proc seen by apps",
     "app: only the app's own processes; host: the whole host's, as it is", true},
    {"etc-view", "APKLAYER_ETC_VIEW", "choice", "app", "app,host", "", "/etc seen by apps", "app: an Android-like /etc; host: the host's", true},
    {"pts-view", "APKLAYER_PTS_VIEW", "choice", "app", "app,host", "", "/dev/pts seen by apps", "app: only the terminals the app opened; host: all", true},
    {"raw-syscall-view", "APKLAYER_RAW_SYSCALL_VIEW", "choice", "on", "on,off", "", "Raw system calls",
     "on: system calls an app makes itself see the same /proc, /etc and identity as its library calls; off: the host's", true},
    {"webview-provider", "APKLAYER_WEBVIEW_PROVIDER", "text", "", "", "", "WebView provider",
     "Empty: the Chromium WebView once installed; none: no WebView provider; or the package of one", true},
    {"audio-output", "APKLAYER_AUDIO_TARGET", "text", "", "", "", "Audio output", "The PipeWire or PulseAudio sink apps play to (empty: the default one)", false},
    {"audio-input", "APKLAYER_AUDIO_TARGET_IN", "text", "", "", "", "Audio input", "The source apps record from (empty: the default one)", false},
};

const Setting* find(const std::string& key) {
    for (const Setting& s : kSettings) if (key == s.key) return &s;
    return nullptr;
}

std::string configFile() { return join(getenvOr("XDG_CONFIG_HOME", join(homeDir(), ".config")), "apklayer/config"); }

using Lines = std::vector<std::pair<std::string, std::string>>;  // "key" or "package:key", value
Lines readLines() {
    Lines lines;
    for (const std::string& raw : split(readFile(configFile()).value_or(""), '\n')) {
        std::string line = trim(raw);
        size_t eq = line.find('=');
        if (!line.empty() && line[0] != '#' && eq != std::string::npos) lines.push_back({trim(line.substr(0, eq)), trim(line.substr(eq + 1))});
    }
    return lines;
}

// the value as the runtime reads it ("": leave the variable unset)
std::string exported(const Setting& s, const std::string& value) {
    if (value == s.def || value.empty()) return "";
    for (const std::string& pair : split(s.map, ','))
        if (pair.compare(0, value.size() + 1, value + "=") == 0) return pair.substr(value.size() + 1);
    return value;
}

std::string field(std::string text) {  // one TSV field
    std::replace_if(text.begin(), text.end(), [](char c) { return c == '\t' || c == '\n'; }, ' ');
    return text;
}
}  // namespace

void applySettings(const std::string& package) {
    for (const auto& [name, value] : readLines()) {
        size_t colon = name.find(':');
        bool own = colon != std::string::npos;
        if (own ? name.compare(0, colon, package) != 0 : !package.empty()) continue;  // the global ones were exported when the command started
        const Setting* s = find(own ? name.substr(colon + 1) : name);
        if (s && !exported(*s, value).empty()) setenv(s->env, exported(*s, value).c_str(), own);
    }
}

int configCommand(const std::vector<std::string>& args) {
    std::vector<std::string> a = args;
    std::string package;
    if (a.size() >= 2 && a[a.size() - 2] == "--app") { package = a.back(); a.resize(a.size() - 2); }
    const std::string usage = "config list | get KEY | set KEY VALUE | unset KEY  [--app <package>]  (keys: apklayer config list)";
    if (a.empty()) die(usage);
    const std::string& action = a[0];
    const std::string prefix = package.empty() ? "" : package + ":";
    Lines lines = readLines();
    auto own = [&](const std::string& key) -> const std::string* {
        for (const auto& [name, value] : lines) if (name == prefix + key) return &value;
        return nullptr;
    };
    auto effective = [&](const Setting& s) {  // the value in this scope: its own, else (an app's) the global one, else the default
        if (const std::string* v = own(s.key)) return *v;
        if (!package.empty())
            for (const auto& [name, value] : lines) if (name == s.key) return value;
        return std::string(s.def);
    };
    if (action == "list" && a.size() == 1) {  // key, kind, default, value, choices, title, help, per-app, own (set in this scope)
        for (const Setting& s : kSettings)
            if (package.empty() || s.app)
                printf("%s\t%s\t%s\t%s\t%s\t%s\t%s\t%d\t%d\n", s.key, s.kind, field(s.def).c_str(), field(effective(s)).c_str(), s.choices, s.title, s.help, s.app, own(s.key) != nullptr);
        return 0;
    }
    if (a.size() < 2 || !find(a[1])) die(usage);
    const Setting& s = *find(a[1]);
    if (!package.empty() && !s.app) die(a[1] + " is not set per app");
    if (action == "get" && a.size() == 2) { printf("%s\n", effective(s).c_str()); return 0; }
    if (!((action == "set" && a.size() == 3) || (action == "unset" && a.size() == 2))) die(usage);
    if (action == "set") {
        std::vector<std::string> choices = split(s.choices, ',');
        if (a[2].find_first_of("\n\r") != std::string::npos) die("a setting is one line");
        if (std::string(s.kind) == "choice" && std::find(choices.begin(), choices.end(), a[2]) == choices.end()) die(a[1] + ": one of " + s.choices);
    }
    std::string text;
    for (const auto& [name, value] : lines) if (name != prefix + s.key) text += name + "=" + value + "\n";
    if (action == "set") text += prefix + s.key + "=" + a[2] + "\n";
    makeDirs(dirName(configFile()));
    if (!writeFile(configFile(), text)) die("cannot write " + configFile());
    printf("apklayer: %s %s%s (from the next start of what it affects)\n", s.key, action == "set" ? "set" : "unset", package.empty() ? "" : (" for " + package).c_str());
    return 0;
}

}  // namespace apklayer
