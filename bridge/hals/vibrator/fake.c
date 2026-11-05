/* The fake vibrator: no motor, every command is logged (one line on stderr, the front's log), so a test reads the pattern an app played. */
#include "apklayer/vibrator.h"

#include <stdio.h>

static float amplitude = 1.0f;

const int apklayer_plugin_abi = APKLAYER_PLUGIN_ABI;
int apklayer_vibrator_probe(void) { return 0; }
int apklayer_vibrator_init(int count, const char *const *options) { (void)count; (void)options; return 0; }

int apklayer_vibrator_on(int32_t duration_ms)
{
    fprintf(stderr, "vibrator: on %d ms amplitude %.2f\n", duration_ms, amplitude);
    return 0;
}

int apklayer_vibrator_off(void)
{
    fprintf(stderr, "vibrator: off\n");
    return 0;
}

int apklayer_vibrator_amplitude(float value)
{
    amplitude = value;
    fprintf(stderr, "vibrator: amplitude %.2f\n", value);
    return 0;
}
