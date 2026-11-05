#include "releases.h"

#include <cctype>
#include <cstdlib>
#include <fstream>
#include <sys/utsname.h>

#include "json.h"

namespace manager {

namespace {
Asset asset(std::string_view perCpu, const std::vector<std::string>& cpus) {
    for (const std::string& cpu : cpus)
        if (auto a = json::member(perCpu, cpu); !a.empty()) return {json::str(json::member(a, "url")), json::str(json::member(a, "sha256"))};
    return {};
}

// "0.2.0-1~noble1" -> numbers {0, 2, 0} and the suffix "-1~noble1"
std::vector<long long> numbers(const std::string& v, std::string& suffix) {
    std::vector<long long> n;
    size_t i = 0;
    while (i < v.size() && isdigit(static_cast<unsigned char>(v[i]))) {
        long long x = 0;
        while (i < v.size() && isdigit(static_cast<unsigned char>(v[i]))) x = x * 10 + (v[i++] - '0');
        n.push_back(x);
        if (i + 1 < v.size() && v[i] == '.' && isdigit(static_cast<unsigned char>(v[i + 1]))) i++;
        else break;
    }
    suffix = v.substr(i);
    return n;
}
}  // namespace

std::string releasesUrl() {
    const char* url = getenv("APKLAYER_RELEASES_URL");
    return url && *url ? url : "https://erfanoabdi.github.io/apklayer/releases.json";
}

std::string osCodename(const std::string& osRelease) {
    std::ifstream in(osRelease);
    for (std::string line; std::getline(in, line);) {
        if (line.rfind("VERSION_CODENAME=", 0) != 0) continue;
        std::string v = line.substr(17);
        if (v.size() >= 2 && (v.front() == '"' || v.front() == '\'')) v = v.substr(1, v.size() - 2);
        return v;
    }
    return "";
}

std::vector<std::string> cpuNames() {
    utsname u{};
    uname(&u);
    std::string m = u.machine;
    if (m == "aarch64") return {"arm64", "aarch64"};
    if (m == "x86_64") return {"amd64", "x86_64"};
    return {m};
}

std::string androidAbi() {
    std::string cpu = cpuNames()[0];
    return cpu == "arm64" ? "arm64-v8a" : cpu == "amd64" ? "x86_64" : cpu;
}

Release parseLatest(const std::string& text, const std::string& codename, const std::vector<std::string>& cpus, std::string& error) {
    Release r;
    std::string latest = json::str(json::member(text, "latest"));
    std::string_view chosen;
    for (std::string_view rel : json::elements(json::member(text, "releases"))) {
        std::string v = json::str(json::member(rel, "version"));
        if (v.empty()) continue;
        if (latest.empty() ? chosen.empty() || compareVersions(v, r.version) > 0 : v == latest) { chosen = rel; r.version = v; }
    }
    if (chosen.empty()) { error = latest.empty() ? "releases.json lists no release" : "releases.json lists no release " + latest; return {}; }
    r.date = json::str(json::member(chosen, "date"));
    r.notesUrl = json::str(json::member(chosen, "notes_url"));
    r.converterAbi = static_cast<int>(json::num(json::member(chosen, "converter_abi")));
    r.dataFormat = static_cast<int>(json::num(json::member(chosen, "data_format")));
    std::string_view runtime = json::member(chosen, "runtime");
    r.runtimeVersion = json::str(json::member(runtime, "version"));
    r.runtime = asset(json::member(runtime, "assets"), cpus);
    r.deb = asset(json::member(json::member(chosen, "debs"), codename), cpus);  // none: this OS or CPU has no package (the caller says so when it matters)
    return r;
}

int compareVersions(const std::string& a, const std::string& b) {
    std::string sa, sb;
    std::vector<long long> x = numbers(a, sa), y = numbers(b, sb);
    for (size_t i = 0; i < x.size() || i < y.size(); i++) {  // a missing number is 0: 0.1 == 0.1.0
        long long p = i < x.size() ? x[i] : 0, q = i < y.size() ? y[i] : 0;
        if (p != q) return p < q ? -1 : 1;
    }
    if (sa.empty() != sb.empty()) return sa.empty() ? 1 : -1;  // a pre-release before its release
    return sa == sb ? 0 : sa < sb ? -1 : 1;
}

IndexApk pickApk(const std::string& text, const std::string& package, const std::string& abi, std::string& error) {
    std::string repo = json::str(json::member(json::member(text, "repo"), "address"));
    long long suggested = 0;
    for (std::string_view app : json::elements(json::member(text, "apps")))
        if (json::str(json::member(app, "packageName")) == package) {
            std::string_view v = json::member(app, "suggestedVersionCode");  // a string in F-Droid's index, a number in some others
            suggested = !v.empty() && v[0] == '"' ? atoll(json::str(v).c_str()) : json::num(v);
        }
    IndexApk best;
    for (std::string_view apk : json::elements(json::member(json::member(text, "packages"), package))) {
        std::string_view native = json::member(apk, "nativecode");
        bool fits = native.empty();
        for (std::string_view n : json::elements(native)) fits = fits || json::str(n) == abi;
        long long code = json::num(json::member(apk, "versionCode"));
        if (!fits || json::str(json::member(apk, "hashType")) != "sha256") continue;
        // the repository's suggested version when it fits, else the newest that does
        bool better = best.url.empty() || (best.versionCode != suggested && (code == suggested || code > best.versionCode));
        if (better) best = {repo + "/" + json::str(json::member(apk, "apkName")), json::str(json::member(apk, "hash")), code};
    }
    if (repo.empty() || best.url.empty()) error = "the repository index has no " + package + " for " + abi;
    return best;
}

}  // namespace manager
