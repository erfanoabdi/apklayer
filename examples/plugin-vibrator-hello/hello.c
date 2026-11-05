/* A complete apklayer vibrator plugin (docs/PLUGINS.md): it has no motor and logs each command on stderr (the front's log,
 * <instance state>/vibrator.log), prefixed with its one option, `greeting`. Built out of tree against apklayer-dev:
 *     meson setup build && meson install -C build        (or: cc -shared -fPIC $(pkg-config --cflags apklayer-plugin) hello.c -o ...)
 * then `apklayer providers set vibrator hello greeting=hi`. */
#include <apklayer/vibrator.h>

#include <errno.h>
#include <pthread.h>
#include <stdio.h>
#include <string.h>

const int apklayer_plugin_abi = APKLAYER_PLUGIN_ABI;  /* checked by apklayer before it loads the plugin */

/* the options init() takes: "key\tdefault\tkind" per line (kind text or file), for `apklayer providers` and the manager */
const char apklayer_vibrator_options[] = "greeting\thello\ttext\n";

static char greeting[64] = "hello";
static float amplitude = 1.0f;
static pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;  /* on, off and amplitude come from several binder threads */

/* usable everywhere, but never the default (0): a user picks it with `apklayer providers set` */
int apklayer_vibrator_probe(void) { return 0; }

int apklayer_vibrator_init(int count, const char *const *options)
{
    for (int i = 0; i < count; i++) {
        if (strncmp(options[i], "greeting=", 9)) { fprintf(stderr, "vibrator hello: unknown option %s\n", options[i]); return -EINVAL; }
        snprintf(greeting, sizeof greeting, "%s", options[i] + 9);
    }
    fprintf(stderr, "vibrator %s: ready\n", greeting);
    return 0;
}

int apklayer_vibrator_on(int32_t duration_ms)
{
    pthread_mutex_lock(&lock);
    fprintf(stderr, "vibrator %s: on %d ms amplitude %.2f\n", greeting, duration_ms, amplitude);
    pthread_mutex_unlock(&lock);
    return 0;
}

int apklayer_vibrator_off(void)
{
    fprintf(stderr, "vibrator %s: off\n", greeting);
    return 0;
}

int apklayer_vibrator_amplitude(float value)
{
    pthread_mutex_lock(&lock);
    amplitude = value;
    pthread_mutex_unlock(&lock);
    return 0;
}
