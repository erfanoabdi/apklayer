/* apklayer vibrator plugin API: what the Android-side front (aosp/hals/vibrator, AOSP's IVibrator example with a plugin) calls. A plugin is
 * a library libapklayer-vibrator-<backend>.so exporting these (one per front process, chosen in providers.toml; common rules, probe, options,
 * init and the ABI version: apklayer/plugin.h; docs/PLUGINS.md). Plain C, no Android types.
 * The example HAL keeps the timing and the completion callbacks: the plugin only moves the motor. Threads: on, off and amplitude come from
 * the front's binder threads (up to 5), possibly at the same time: guard shared state. They should return quickly (no waiting for the
 * vibration to end); the front ignores their result (the app's call succeeds anyway). */
#ifndef APKLAYER_VIBRATOR_H
#define APKLAYER_VIBRATOR_H

#include <stdint.h>

#include <apklayer/plugin.h>

#ifdef __cplusplus
extern "C" {
#endif
#pragma GCC visibility push(default)

int apklayer_vibrator_probe(void);
extern const char apklayer_vibrator_options[];  /* optional */
int apklayer_vibrator_init(int count, const char *const *options);
/* runs the motor for duration_ms (> 0) at the last amplitude; a call while it runs restarts it with the new duration. 0 or -errno */
int apklayer_vibrator_on(int32_t duration_ms);
/* stops the motor now (also when it is not running). 0 or -errno */
int apklayer_vibrator_off(void);
/* the strength of this and later vibrations, 0 < amplitude <= 1 (1 until the first call); a running vibration may take it at once. 0 or -errno */
int apklayer_vibrator_amplitude(float amplitude);

#pragma GCC visibility pop
#ifdef __cplusplus
}
#endif
#endif
