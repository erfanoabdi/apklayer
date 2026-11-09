// The updater's backend without the CLI or the network: releases.json, version order, F-Droid index picks, the SHA256-checked download (file://
// and a local HTTP server: python3's http.server), the runtime bundle's atomic unpack with one previous version kept. Usage: updater-test
#include <csignal>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <sys/wait.h>
#include <unistd.h>

#include "../../common/fetch.h"
#include "../../common/json.h"
#include "../../common/releases.h"

using namespace manager;

static int failures = 0;
#define CHECK(cond, what) do { if (cond) printf("ok   %s\n", what); else { fprintf(stderr, "FAIL %s\n", what); failures++; } } while (0)

static void write(const std::string& path, const std::string& text) { std::ofstream(path, std::ios::binary) << text; }
static bool exists(const std::string& path) { return access(path.c_str(), F_OK) == 0; }
static std::string sh(const std::string& cmd) { return runProgram({"sh", "-c", cmd}).out; }

static const char* kReleases = R"({
  "latest": "0.2.0",
  "releases": [
    {"version": "0.2.0", "date": "2026-11-20", "notes_url": "https://example.org/v0.2.0", "converter_abi": 2, "data_format": 1,
     "runtime": {"version": "2", "assets": {"arm64": {"url": "https://example.org/rt-arm64-2.tar.zst", "sha256": "aa", "size": 500, "unpacked_size": 1600}}},
     "debs": {"noble": {"arm64": {"url": "https://example.org/apklayer_0.2.0-1~noble1_arm64.deb", "sha256": "bb"},
                        "amd64": {"url": "https://example.org/apklayer_0.2.0-1~noble1_amd64.deb", "sha256": "cc"}},
              "trixie": {"arm64": {"url": "https://example.org/apklayer_0.2.0-1~trixie1_arm64.deb", "sha256": "dd"}}}},
    {"version": "0.1.0", "date": "2026-11-05", "notes_url": "https://example.org/v0.1.0", "converter_abi": 1, "data_format": 1,
     "runtime": {"version": "1", "assets": {}}, "debs": {}}
  ]
})";

int main() {
    // releases.json
    std::string error;
    Release r = parseLatest(kReleases, "trixie", {"arm64", "aarch64"}, error);
    CHECK(error.empty() && r.version == "0.2.0" && r.converterAbi == 2 && r.runtimeVersion == "2" && r.notesUrl == "https://example.org/v0.2.0", "releases.json: the latest release");
    CHECK(r.deb.url == "https://example.org/apklayer_0.2.0-1~trixie1_arm64.deb" && r.deb.sha256 == "dd" && r.runtime.sha256 == "aa", "the deb of this codename and CPU, the runtime of this CPU");
    CHECK(r.runtime.size == 500 && r.runtime.unpacked == 1600 && r.deb.size == 0, "the runtime's sizes (none listed: 0)");
    r = parseLatest(kReleases, "noble", {"amd64", "x86_64"}, error);
    CHECK(r.deb.sha256 == "cc" && !r.runtime.found(), "another CPU: its deb, no runtime asset");
    error.clear();
    r = parseLatest(kReleases, "bookworm", {"arm64"}, error);
    CHECK(!r.deb.found() && r.version == "0.2.0" && error.empty(), "an unknown codename: the release without a deb");
    error.clear();
    r = parseLatest(R"({"releases": [{"version": "0.9.0"}, {"version": "0.10.0"}, {"version": "0.10.0-rc1"}]})", "noble", {"arm64"}, error);
    CHECK(r.version == "0.10.0", "no latest: the newest of the list");
    error.clear();
    r = parseLatest("{\"latest\": \"0.3\", \"releases\": [", "noble", {"arm64"}, error);
    CHECK(r.version.empty() && !error.empty(), "malformed releases.json: an error, no release");

    // versions
    CHECK(compareVersions("0.1", "0.1.0") == 0 && compareVersions("0.10.0", "0.9.9") == 1 && compareVersions("0.1.0", "0.1.1") == -1, "versions: dotted numbers");
    CHECK(compareVersions("0.2.0-rc1", "0.2.0") == -1 && compareVersions("0.2.0", "0.1.9-1~noble1") == 1 && compareVersions("1.0", "1.0") == 0, "versions: a pre-release before its release");

    // json
    CHECK(json::str(R"("a\"b\\cé😀\n")") == "a\"b\\c\xc3\xa9\xf0\x9f\x98\x80\n", "json strings: escapes, BMP and surrogate pairs");
    CHECK(json::member(R"({"a": [1, {"b": "}"}], "c": 7})", "c") == "7" && json::elements("[1, \"x\", {}]").size() == 3, "json: nested values skipped");

    // an F-Droid index
    const char* index = R"({"repo": {"address": "https://repo.example.org/fdroid/repo"},
      "apps": [{"packageName": "org.example", "suggestedVersionCode": "20"}],
      "packages": {"org.example": [
        {"versionCode": 30, "apkName": "org.example-30.apk", "hash": "h30", "hashType": "sha256"},
        {"versionCode": 20, "apkName": "org.example-20.apk", "hash": "h20", "hashType": "sha256", "nativecode": ["arm64-v8a", "x86_64"]},
        {"versionCode": 25, "apkName": "org.example-25.apk", "hash": "h25", "hashType": "sha256", "nativecode": ["armeabi-v7a"]}],
       "org.other": [{"versionCode": 3, "apkName": "o-3.apk", "hash": "o3", "hashType": "sha256", "nativecode": ["x86"]},
                     {"versionCode": 2, "apkName": "o-2.apk", "hash": "o2", "hashType": "sha256"}]}})";
    error.clear();
    IndexApk apk = pickApk(index, "org.example", "arm64-v8a", error);
    CHECK(error.empty() && apk.versionCode == 20 && apk.url == "https://repo.example.org/fdroid/repo/org.example-20.apk" && apk.sha256 == "h20", "F-Droid index: the suggested version");
    apk = pickApk(index, "org.other", "arm64-v8a", error);
    CHECK(apk.versionCode == 2, "F-Droid index: no suggestion, the newest for this ABI");
    apk = pickApk(index, "org.missing", "arm64-v8a", error);
    CHECK(apk.url.empty() && !error.empty(), "F-Droid index: a missing package is an error");

    // downloads
    char pattern[] = "/tmp/updater-test.XXXXXX";
    std::string t = mkdtemp(pattern);
    std::string osRelease = t + "/os-release";
    write(osRelease, "NAME=\"Ubuntu\"\nVERSION_CODENAME=noble\nUBUNTU_CODENAME=noble\n");
    CHECK(osCodename(osRelease) == "noble", "os-release: VERSION_CODENAME");
    write(t + "/payload.bin", std::string(300000, 'x'));
    std::string sum = sha256File(t + "/payload.bin");
    double last = -2;
    std::string e = fetch("file://" + t + "/payload.bin", sum, t + "/dl/a.bin", [&](double p) { last = p; });
    CHECK(e.empty() && exists(t + "/dl/a.bin") && last == 1.0, "file:// download with the right SHA256");
    std::string bad = sum;
    bad[0] = bad[0] == '0' ? '1' : '0';
    e = fetch("file://" + t + "/payload.bin", bad, t + "/dl/b.bin", nullptr);
    CHECK(e.find("checksum mismatch") != std::string::npos && !exists(t + "/dl/b.bin") && !exists(t + "/dl/b.bin.part"), ("a wrong SHA256 is refused and nothing is left: " + e).c_str());
    CHECK(!fetch("file://" + t + "/payload.bin", "", t + "/dl/c.bin", nullptr).empty() && !exists(t + "/dl/c.bin"), "no SHA256: nothing is downloaded");

    // the same over HTTP (python3's server prints its port first: that line is the readiness event)
    int out[2];
    if (pipe(out) == 0) {
        pid_t server = fork();
        if (server == 0) {
            dup2(out[1], 1);
            close(out[0]);
            execlp("python3", "python3", "-u", "-m", "http.server", "0", "--bind", "127.0.0.1", "--directory", t.c_str(), nullptr);
            _exit(127);
        }
        close(out[1]);
        FILE* f = fdopen(out[0], "r");
        char line[256] = "";
        int port = 0;
        if (fgets(line, sizeof line, f)) { const char* p = strstr(line, "port "); if (p) port = atoi(p + 5); }
        if (port > 0) {
            std::string url = "http://127.0.0.1:" + std::to_string(port) + "/payload.bin";
            CHECK(fetch(url, sum, t + "/dl/h.bin", nullptr).empty() && exists(t + "/dl/h.bin"), "HTTP download with the right SHA256");
            e = fetch(url, bad, t + "/dl/i.bin", nullptr);
            CHECK(e.find("checksum mismatch") != std::string::npos && !exists(t + "/dl/i.bin"), "HTTP download with a wrong SHA256 is refused");
            CHECK(!fetch("http://127.0.0.1:" + std::to_string(port) + "/missing", sum, t + "/dl/j.bin", nullptr).empty(), "HTTP 404 is an error");
        } else { fputs("FAIL no python3 http.server\n", stderr); failures++; }
        kill(server, SIGTERM);
        waitpid(server, nullptr, 0);
        fclose(f);
    }

    // the runtime bundle
    std::string root = t + "/runtime";
    auto bundle = [&](const std::string& version, bool withJson) {
        std::string dir = t + "/src-" + version + "/apklayer-runtime-arm64-" + version;
        // the parts a complete runtime has (fetch.cpp runtimeProblem)
        sh("mkdir -p '" + dir + "/system-apps' '" + dir + "/android-root/system' '" + dir + "/android-libs' '" + dir + "/aosp/android-all' && echo " + version + " > '" + dir + "/system-apps/marker'");
        if (withJson) write(dir + "/runtime.json", "{\"version\": \"" + version + "\"}\n");
        std::string file = t + "/rt-" + version + ".tar.zst";
        sh("tar --zstd -cf '" + file + "' -C '" + t + "/src-" + version + "' .");
        return file;
    };
    CHECK(installedRuntime(root).empty(), "no runtime yet");
    CHECK(unpackRuntime(bundle("1", true), root, "1").empty() && installedRuntime(root) == "1" && exists(root + "/1/system-apps/marker"), "runtime 1 unpacked, current -> 1");
    CHECK(unpackRuntime(bundle("2", true), root, "2").empty() && installedRuntime(root) == "2" && exists(root + "/1/runtime.json"), "runtime 2: current -> 2, 1 kept");
    CHECK(unpackRuntime(bundle("3", true), root, "3").empty() && installedRuntime(root) == "3" && exists(root + "/2") && !exists(root + "/1"), "runtime 3: 2 kept, 1 removed");
    write(t + "/broken.tar.zst", "not a tarball");
    e = unpackRuntime(t + "/broken.tar.zst", root, "4");
    CHECK(!e.empty() && installedRuntime(root) == "3" && !exists(root + "/4") && sh("ls -A '" + root + "' | grep -c unpack").find('0') == 0, "a broken bundle: an error, current unchanged, nothing half-unpacked");
    e = unpackRuntime(bundle("5", false), root, "5");
    CHECK(e.find("runtime.json") != std::string::npos && installedRuntime(root) == "3" && !exists(root + "/5"), "a bundle without runtime.json is refused");
    CHECK(!unpackRuntime(bundle("3", true), root, "../x").empty() && !unpackRuntime(bundle("3", true), root, "current").empty(), "a version that is no plain name is refused");
    CHECK(unpackRuntime(bundle("3", true), root, "3").empty() && installedRuntime(root) == "3" && exists(root + "/2"), "the same version again replaces it in place");


    // free space (a fake statvfs): the bundle's unpacked size, the runtimes an update may remove first, never current
    long long tarSize = atoll(sh("tar -cf - -C '" + t + "/src-3' . | wc -c").c_str());
    CHECK(unpackedSize(bundle("3", true)) == tarSize, "unpacked size of a bundle without a recorded one: counted");
    sh("printf '\\050\\265\\057\\375\\244\\000\\360\\055\\000garbage' > '" + t + "/recorded.tar.zst'");  // single segment, 4-byte size 3010560
    CHECK(unpackedSize(t + "/recorded.tar.zst") == 3010560, "unpacked size recorded in the zstd frame header");
    sh("printf 'xxxx' > '" + t + "/notzstd'");
    CHECK(unpackedSize(t + "/notzstd") <= 0, "no zstd frame: no size");
    static long long fakeFree = 0;
    freeBytes = [](const std::string&) { return fakeFree; };
    std::string note;
    fakeFree = 1LL << 40;
    CHECK(runtimeRoom(root, 1LL << 30, &note).empty() && note.empty() && exists(root + "/2"), "room enough: nothing removed");
    long long old = atoll(sh("du -sk '" + root + "/2'").c_str()) * 1024;
    fakeFree = 1000;
    e = runtimeRoom(root, 1000 + old + 1, &note);
    CHECK(e.rfind("Not enough space", 0) == 0 && e.find(root) != std::string::npos && exists(root + "/2") && exists(root + "/3"), ("does not fit even without runtime 2: nothing removed, " + e).c_str());
    CHECK(runtimeRoom(root, 1000 + old, &note).empty() && !exists(root + "/2") && installedRuntime(root) == "3" && note.find("runtime 2") != std::string::npos,
          ("fits with the previous runtime removed: removed, current kept: " + note).c_str());
    note.clear();
    CHECK(!runtimeRoom(root, 1LL << 40, &note).empty() && installedRuntime(root) == "3", "only current left: never removed");
    e = unpackRuntime(bundle("4", true), root, "4", &note);
    CHECK(e.rfind("Not enough space", 0) == 0 && installedRuntime(root) == "3" && !exists(root + "/4"), ("unpack: checked before a byte is written: " + e).c_str());
    freeBytes = [](const std::string&) { return -1LL; };
    CHECK(runtimeRoom(root, 1LL << 40, &note).empty(), "free space unknown: no refusal");

    sh("rm -rf '" + t + "'");
    if (failures) { fprintf(stderr, "FAIL updater backend: %d\n", failures); return 1; }
    puts("PASS updater backend");
    return 0;
}
