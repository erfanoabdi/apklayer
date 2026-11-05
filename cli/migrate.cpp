#include "migrate.h"

#include <array>
#include <cstdlib>

#include "version.h"

namespace apklayer {

namespace {
using Step = void (*)(const Layout&);
// kSteps[i] turns format i+1 into i+2; bumping kDataFormat without a step here does not compile. None yet.
const std::array<Step, kDataFormat - 1> kSteps{};
}  // namespace

void migrateData(const Layout& l) {
    const std::string dir = join(l.dataHome, "apklayer"), file = join(dir, "data-format");
    auto text = readFile(file);
    // no record: data from before the record is format 1, a fresh data home starts at the current one
    // an empty or unreadable record (an interrupted write) counts as no record: atoi gives 0, and kSteps[-1] is out of bounds
    if (text && atoi(text->c_str()) < 1) text.reset();
    int have = text ? atoi(text->c_str()) : isDir(dir) ? 1 : kDataFormat;
    if (have > kDataFormat) die("the data in " + dir + " is format " + std::to_string(have) + ", from a newer apklayer (this one reads " + std::to_string(kDataFormat) + ")");
    if (text && have == kDataFormat) return;
    makeDirs(dir);
    // shortcut: no lock against a second CLI migrating at the same time, add one (flock on the file) with the first real step
    for (; have < kDataFormat; have++) {
        kSteps[have - 1](l);
        writeFile(file, std::to_string(have + 1) + "\n");
    }
    if (!text && !writeFile(file, std::to_string(kDataFormat) + "\n")) die("cannot write " + file);
}

}  // namespace apklayer
