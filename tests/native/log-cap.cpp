// cli/logcap.h: an app log at its size cap takes whole lines and counts the rest. Build: g++ -std=c++17 -I cli tests/native/log-cap.cpp
#include <cstdio>
#include <cstring>
#include "logcap.h"
using namespace apklayer;
static int bad;
#define CHECK(x) do { if (!(x)) { printf("FAIL line %d: %s\n", __LINE__, #x); bad = 1; } } while (0)
int main() {
    const char* t = "one\ntwo\nthree\nfour";  // 18 bytes
    CHECK(capKeep(t, 18, 100) == 18);          // fits
    CHECK(capKeep(t, 18, 18) == 18);
    CHECK(capKeep(t, 18, 17) == 14);           // "four" does not fit: up to the end of "three\n"
    CHECK(capKeep(t, 18, 9) == 8);             // "one\ntwo\n"
    CHECK(capKeep(t, 18, 2) == 0);             // not even the first line
    CHECK(capKeep(t, 18, 0) == 0);
    CHECK(capLines(t + 14, 4) == 1);           // "four", no end
    CHECK(capLines(t, 18) == 4);
    CHECK(capLines("a\nb\n", 4) == 2);
    CHECK(capLines(t, 0) == 0);
    printf("%s\n", bad ? "FAIL" : "PASS");
    return bad;
}
