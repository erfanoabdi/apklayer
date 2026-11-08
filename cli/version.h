// apklayer's version (meson's project version, cli/meson.build) and the two format numbers an update is checked against (`apklayer --version`).
#pragma once

namespace apklayer {

inline constexpr const char* kVersion = APKLAYER_VERSION;
// Bump when dex2jvm, the instrumentation (DenyInstrument, VerifyInstall) or the converted-class format changes: each install records the number in
// info.properties (converterAbi=), installs with an older one are stale (`apklayer reconvert --all-stale`, the manager after an update).
// next bump: 5, not 4 (4 was the reverted verify-at-install of 2026-11-08; apps converted then say 4 and must count as stale)
inline constexpr int kConverterAbi = 3;  // 3: exits routed to ApklayerExit; 2: direct ByteBuffers with an array (ApklayerBuffers)
// Bump when the layout of the data home (apps, instances, app data) changes, with a migration step in cli/migrate.cpp.
inline constexpr int kDataFormat = 1;

}  // namespace apklayer
