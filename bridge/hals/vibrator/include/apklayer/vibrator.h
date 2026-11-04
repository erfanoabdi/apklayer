/* apklayer vibrator HAL, host side: what the Android-side front (aosp/hals/vibrator, AOSP's IVibrator example with a backend) calls.
 * A backend is a library libapklayer-vibrator-<backend>.so exporting these (one backend per front process: the stack puts the configured one
 * on the front's APKLAYER_HOST_LIBS, providers.toml). Plain C, no Android types. */
#ifndef APKLAYER_VIBRATOR_H
#define APKLAYER_VIBRATOR_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* <0: not usable on this host; 0: usable but never chosen by default (fake); >0: quality, the highest is the default */
int apklayer_vibrator_probe(void);
/* the domain's options from providers.toml, "key=value" each; 0 or -errno */
int apklayer_vibrator_init(int count, const char *const *options);
/* runs the motor for duration_ms at the last amplitude (0..1, 1 at start); off stops it early. 0 or -errno */
int apklayer_vibrator_on(int32_t duration_ms);
int apklayer_vibrator_off(void);
int apklayer_vibrator_amplitude(float amplitude);

#ifdef __cplusplus
}
#endif
#endif
