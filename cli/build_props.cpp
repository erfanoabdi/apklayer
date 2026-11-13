// The build.prop files of the device the instance presents, for apps that read them as files (AIDA64 and Device Info HW read /system/build.prop and
// /default.prop next to their SystemProperties calls). Written into the instance's own android-root (the path layer's APKLAYER_INSTANCE_ROOT, made
// fresh with each stack) from the live property store, so file and store agree, derived properties (the fingerprint, the ABI lists) included.
// Partitions as Android has them: ro.product.<partition>.* and ro.<partition>.build.* go to that partition's file, everything else to /system.
#include "build_props.h"

#include "env.h"
#include "util.h"

namespace apklayer {

std::vector<std::pair<std::string, std::string>> dumpStore(const Layout& l, const Instance& inst) {
    std::vector<std::pair<std::string, std::string>> rows;
    Spawn dump;
    dump.argv = {join(l.native, "bin/aprop"), "dump"};
    dump.env = nativeEnv(l, inst).env;
    for (const std::string& line : split(runCapture(dump), '\n')) {  // "[name]: [value]"
        size_t sep = line.find("]: [");
        if (line.size() > 4 && line[0] == '[' && sep != std::string::npos && line.back() == ']')
            rows.emplace_back(line.substr(1, sep - 1), line.substr(sep + 4, line.size() - sep - 5));
    }
    return rows;
}

namespace {
bool startsWith(const std::string& s, const std::string& p) { return s.compare(0, p.size(), p) == 0; }
}  // namespace

void writeBuildProps(const Layout&, const Instance& inst, const std::vector<std::pair<std::string, std::string>>& store) {
    const std::string root = join(inst.runtimeDir, "android-root");
    static const char* const names[] = {"system", "vendor", "odm", "product", "system_ext"};
    std::string text[5];
    for (const auto& [key, value] : store) {
        bool build = startsWith(key, "ro.") || startsWith(key, "dalvik.") || startsWith(key, "persist.");  // what a build.prop holds, not runtime state (sys.*, init.svc.*, gsm.*)
        if (!build || startsWith(key, "ro.boot.") || startsWith(key, "ro.hardware.")) continue;  // the bootloader's, the HAL module choice
        size_t into = 0;
        for (size_t i = 1; i < 5; i++)
            if (startsWith(key, std::string("ro.product.") + names[i] + ".") || startsWith(key, std::string("ro.") + names[i] + ".build.")) into = i;
        text[into] += key + "=" + value + "\n";
    }
    for (size_t i = 0; i < 5; i++) {
        makeDirs(join(root, names[i]));
        writeFile(join(join(root, names[i]), "build.prop"), "# apklayer: the properties of the device this instance presents (cli/build_props.cpp)\n" + text[i]);
    }
    writeFile(join(root, "default.prop"), "# apklayer: see /system/build.prop\n" + text[0]);
}

}  // namespace apklayer
