// downloads.json: the installed file, the user's file over it (per entry), a missing key, a missing or broken file. Usage: downloads-test
#include <cstdio>
#include <cstdlib>
#include <fstream>
#include <unistd.h>

#include "../../common/downloads.h"

using namespace manager;

static int failures = 0;
#define CHECK(cond, what) do { if (cond) printf("ok   %s\n", what); else { fprintf(stderr, "FAIL %s\n", what); failures++; } } while (0)
static void write(const std::string& path, const std::string& text) { std::ofstream(path, std::ios::binary) << text; }

int main() {
    char tmpl[] = "/tmp/downloads-test-XXXXXX";
    const std::string dir = mkdtemp(tmpl), base = dir + "/base.json", user = dir + "/user.json";
    write(base, R"({"gapps": {"arm64": {"url": "https://a/1.zip", "sha256": "aa"}, "amd64": {"url": "https://a/2.zip", "sha256": "bb"}},
                    "fdroid": {"index": "https://f/index.json", "packages": ["p.one", "p.two"]}})");
    Downloads d;
    CHECK(d.load(base, user).empty(), "base alone loads, a missing user file is fine");
    CHECK(d.str({"gapps", "arm64", "url"}) == "https://a/1.zip", "base value");
    CHECK(d.strings({"fdroid", "packages"}) == std::vector<std::string>({"p.one", "p.two"}), "string array");
    CHECK(d.str({"microg", "index"}).empty(), "a missing key is empty");

    write(user, R"({"gapps": {"arm64": {"url": "https://u/3.zip", "sha256": "cc"}}, "microg": {"index": "https://m/i.json"}})");
    CHECK(d.load(base, user).empty(), "base and user load");
    CHECK(d.str({"gapps", "arm64", "url"}) == "https://u/3.zip" && d.str({"gapps", "arm64", "sha256"}) == "cc", "user overrides an entry (url with its sha256)");
    CHECK(d.str({"gapps", "amd64", "url"}) == "https://a/2.zip", "an entry the user lacks stays the base's");
    CHECK(d.str({"microg", "index"}) == "https://m/i.json" && d.str({"fdroid", "index"}) == "https://f/index.json", "user adds a key, others stay");

    write(user, "not json");
    std::string e = d.load(base, user);
    CHECK(e.find(user) != std::string::npos, "a broken user file: the error names it");
    e = d.load(dir + "/nope.json", "");
    CHECK(e.find("nope.json") != std::string::npos, "a missing base file: the error names it");
    write(base, "[]");
    e = d.load(base, "");
    CHECK(e.find(base) != std::string::npos, "an invalid base file: the error names it");

    unlink(base.c_str()); unlink(user.c_str()); rmdir(dir.c_str());
    return failures ? 1 : 0;
}
