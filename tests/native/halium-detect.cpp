#include <cstdio>
#include "../../cli/halium.h"
// cli/halium.h haliumLayout: a Halium device has its Android's trees and a binder of its own, binderfs or plain /dev/binder (a kernel without
// binderfs). Build: g++ -std=c++20 -I common tests/native/halium-detect.cpp
int main() {
    using apklayer::haliumLayout;
    bool ok = haliumLayout(true, true, false, true) && haliumLayout(true, false, true, true) && haliumLayout(true, true, true, true) &&
              !haliumLayout(true, false, false, true) && !haliumLayout(false, true, true, true) && !haliumLayout(true, true, true, false);
    printf("%s\n", ok ? "PASS" : "FAIL");
    return ok ? 0 : 1;
}
