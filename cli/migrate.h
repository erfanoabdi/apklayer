// The data home's format number (<dataHome>/apklayer/data-format, cli/version.h kDataFormat) and the steps that bring older data up to it.
#pragma once

#include "layout.h"

namespace apklayer {

// Runs the missing migration steps in order, recording each one; refuses data written by a newer apklayer (a downgrade would misread it).
void migrateData(const Layout& layout);

}  // namespace apklayer
