/* apklayer GNSS plugin API: what the Android-side front (aosp/hals/gnss, AOSP's IGnss example with a plugin's fixes) calls. A plugin is a
 * library libapklayer-gnss-<backend>.so exporting these (one per front process, chosen in providers.toml; common rules, probe, options, init
 * and the ABI version: apklayer/plugin.h; docs/PLUGINS.md). Plain C.
 * Threads: next_fix comes from the example's one reporting thread while a client has the receiver started, once per reporting interval
 * (1 s by default); never concurrently with itself. It must not block for long: a plugin with an asynchronous source (a D-Bus service) keeps
 * the latest fix from its own thread and returns that. */
#ifndef APKLAYER_GNSS_H
#define APKLAYER_GNSS_H

#include <apklayer/plugin.h>

#ifdef __cplusplus
extern "C" {
#endif
#pragma GCC visibility push(default)

struct apklayer_gnss_fix {
    double latitude, longitude, altitude;  /* degrees (WGS84), meters above the WGS84 ellipsoid */
    float speed, bearing, accuracy;        /* m/s, degrees clockwise from true north (0..360), horizontal accuracy in meters */
};

int apklayer_gnss_probe(void);
extern const char apklayer_gnss_options[];  /* optional */
int apklayer_gnss_init(int count, const char *const *options);
/* fills *fix (the caller's) with the current position: 0, or -EAGAIN while there is none yet (nothing is reported for that interval). The
 * front stamps the time itself. */
int apklayer_gnss_next_fix(struct apklayer_gnss_fix *fix);

#pragma GCC visibility pop
#ifdef __cplusplus
}
#endif
#endif
