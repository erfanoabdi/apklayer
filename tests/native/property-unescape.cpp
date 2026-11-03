#include <cstdio>
#include <string>
#include <fstream>
namespace apklayer { std::string property(const std::string& file, const std::string& key); }
// apklayer::property decodes java.util.Properties escapes, a surrogate pair (emoji) as one 4-byte UTF-8 sequence. Build: g++ -std=c++20 tests/native/property-unescape.cpp cli/util.cpp
int main() {
    std::ofstream("/tmp/utf-test.properties") << "label=a\\uD83D\\uDE00b\\u00e9\\!\n";
    std::string v = apklayer::property("/tmp/utf-test.properties", "label");
    bool ok = v == "a\xF0\x9F\x98\x80" "b\xC3\xA9!";
    printf("%s %zu\n", ok ? "PASS" : "FAIL", v.size());
    return ok ? 0 : 1;
}
