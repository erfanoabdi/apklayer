# Hardware plugins

A hardware plugin gives Android apps in apklayer a piece of the host's hardware: sensors, a location source, a vibration motor or cameras.
This guide covers writing one, building it outside the apklayer tree, installing it and testing it.

## What a plugin is

Android apps reach hardware through the standard Android HALs. apklayer runs AOSP's own HAL implementations as "fronts", one process per
domain (`aosp/hals/<domain>`). Each front calls a small C API, and a plugin implements that API:

```
app ── SensorManager / LocationManager / Vibrator / CameraManager
      └─ Android services (SensorService, the hub, cameraserver)
          └─ HAL front (Android code, aosp/hals/<domain>)
              └─ plugin: libapklayer-<domain>-<backend>.so  (plain glibc C, this guide)
                  └─ the host: iio-sensor-proxy, GeoClue, a force-feedback device, PipeWire, a file, ...
```

- A plugin is an ordinary Linux shared library, named `libapklayer-<domain>-<backend>.so`. `<domain>` is one of `sensors`, `gnss`,
  `vibrator` or `camera`. `<backend>` is your plugin's name (letters, digits and `-`), the one users pick.
- It is host code. Link any host library (GLib, PipeWire, libudev); never an Android one. The front is Android code running through
  bionic_translation, and the plugin is loaded beside it. C or C++ (the headers are `extern "C"`).
- One plugin per domain runs per apklayer instance, inside that domain's front process. Its stderr is the front's log,
  `~/.local/state/apklayer/<instance>/<domain>.log` (`apklayer logs` lists the instance's logs).
- The in-tree plugins are written the same way and are good references: `bridge/hals/<domain>/*.c` (`fake` for each domain, `iio` and
  `sensorfw` sensors, `geoclue`, `geoclue1` and `lomiri` GNSS, `ff`, `feedbackd` and `hfd` vibrators, `pipewire` camera).

The API is five headers. apklayer-dev installs them in `<includedir>/apklayer/`. In the sources they are `bridge/hals/include/apklayer/plugin.h`
and `bridge/hals/<domain>/include/apklayer/<domain>.h`. The headers are the reference: every call's threads, buffer ownership, return
values and units are documented there. This page gives the overview.

## The calls

Every domain has the same three entry points, with `<d>` the domain (`apklayer/plugin.h`):

| Symbol | Required | What it is |
|---|---|---|
| `const int apklayer_plugin_abi` | yes | Set it to `APKLAYER_PLUGIN_ABI` (see [ABI version](#abi-version)). |
| `int apklayer_<d>_probe(void)` | yes | Is the hardware there? `<0` not usable on this host; `0` usable but never picked by default (fakes, test plugins); `>0` a quality score. When the user picked nothing, the domain's default is the usable plugin with the highest score. |
| `const char apklayer_<d>_options[]` | no | The options `init` accepts, one line per option: `"key\tdefault\tkind\n"`, where kind is `text` or `file`. `apklayer providers` and the manager's Hardware page show them. |
| `int apklayer_<d>_init(int count, const char *const *options)` | yes | The first call in the front: the user's options as `"key=value"` strings. Returns 0 or `-errno`. |

The domain's own calls:

| Domain | Calls | Threads |
|---|---|---|
| `vibrator` | `on(duration_ms)`, `off()`, `amplitude(0..1)` | Binder threads (up to 5), possibly at the same time. Return at once. AOSP's example HAL keeps the timing and the completion callbacks. |
| `sensors` | `list(out, max)` once; `read(index, values[3])` → 3 (x, y, z) or 1 value, in Android's units (m/s², rad/s, µT, lux, hPa, cm) | `list` on the main thread. `read` runs on one sampling thread per active sensor, so calls for different sensors run in parallel. Never block: return the latest value. |
| `gnss` | `next_fix(&fix)` → 0, or `-EAGAIN` while there is no fix yet | One reporting thread, about once a second. Never block for long. |
| `camera` | `list`, `open(id, callbacks)`, `start(w, h, fps, fourccs)`, `format`, `stop`, `close`; frames arrive through `callbacks->frame` with I420, NV12, YUYV or XRGB planes and a monotonic timestamp | Control calls come from binder threads, one at a time per camera. Callbacks run on your capture thread. No callback may run after `stop` or `close` returns. |

Rules for all domains:

- **The CLI loads your plugin too.** The apklayer CLI dlopens the plugin to read `apklayer_plugin_abi` and the options string. Constructors
  must therefore have no side effects and must not start threads.
- **probe() runs in a throwaway process.** It runs in a forked child of the CLI, without `init()`. It may connect to a bus or start threads,
  and it need not clean up. Answer within about a second: `apklayer providers` and every stack start wait for it.
- **init() comes before everything else**, on the front's main thread. Option strings are valid only during the call, so copy what you
  keep. Reject an unknown or malformed option with `-EINVAL` and a line on stderr. A failed `init` ends the front. The stack restarts it a
  few times, then leaves the domain off.
- **There is no deinit.** The front ends by SIGTERM, on `apklayer stop` or when the configuration changes. Hold nothing that the end of
  the process does not release.
- **Buffers are the caller's.** Anything passed in (arrays, `struct` pointers, frames in callbacks) is valid only during the call.
- **Keep your symbols private.** Every plugin of the process is loaded with `RTLD_GLOBAL`, so build with `-fvisibility=hidden`. The headers
  keep the API exported.

## A complete minimal plugin

`examples/plugin-vibrator-hello/` is a working vibrator plugin. It has no motor: it logs each command, prefixed with its one option. The
whole of `hello.c`:

```c
#include <apklayer/vibrator.h>
#include <errno.h>
#include <pthread.h>
#include <stdio.h>
#include <string.h>

const int apklayer_plugin_abi = APKLAYER_PLUGIN_ABI;         /* 1. the ABI it was built for */
const char apklayer_vibrator_options[] = "greeting\thello\ttext\n";  /* 2. one text option, default "hello" */

static char greeting[64] = "hello";
static float amplitude = 1.0f;
static pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;     /* on/off/amplitude come from several binder threads */

int apklayer_vibrator_probe(void) { return 0; }              /* 3. usable, never the default */

int apklayer_vibrator_init(int count, const char *const *options)   /* 4. options from providers.toml */
{
    for (int i = 0; i < count; i++) {
        if (strncmp(options[i], "greeting=", 9)) { fprintf(stderr, "vibrator hello: unknown option %s\n", options[i]); return -EINVAL; }
        snprintf(greeting, sizeof greeting, "%s", options[i] + 9);
    }
    fprintf(stderr, "vibrator %s: ready\n", greeting);
    return 0;
}

int apklayer_vibrator_on(int32_t duration_ms)                /* 5. the motor commands */
{
    pthread_mutex_lock(&lock);
    fprintf(stderr, "vibrator %s: on %d ms amplitude %.2f\n", greeting, duration_ms, amplitude);
    pthread_mutex_unlock(&lock);
    return 0;
}
int apklayer_vibrator_off(void) { fprintf(stderr, "vibrator %s: off\n", greeting); return 0; }
int apklayer_vibrator_amplitude(float value)
{
    pthread_mutex_lock(&lock);
    amplitude = value;
    pthread_mutex_unlock(&lock);
    return 0;
}
```

1. `apklayer_plugin_abi` records which API version the plugin was compiled against. apklayer refuses a plugin whose number is not its own.
2. The options string turns into a text field in the manager and an `option` row in `apklayer providers`.
3. A real plugin checks its hardware here. `ff.c` looks for a force-feedback input device and `feedbackd.c` for the daemon on the session
   bus; each returns a positive quality when it finds one. Returning 0 makes this plugin a choice the user has to make.
4. `init` gets the configured options. The values come from the user, so validate them.
5. The motor calls must return immediately; a real motor driver starts the effect and returns. They can run at the same time from
   different binder threads, so shared state is locked.

## Building out of tree

Install the API first: `sudo apt install apklayer-dev` (the deb from the release page), or `meson install` of an apklayer source build.
Either one provides the headers and `apklayer-plugin.pc`:

```
$ pkg-config --modversion apklayer-plugin
0.3.0-1~noble1
$ pkg-config --variable=plugindir apklayer-plugin
/usr/lib/aarch64-linux-gnu/apklayer/plugins
```

With Meson (the example's `meson.build`, nothing else of apklayer's needed):

```meson
project('apklayer-vibrator-hello', 'c', default_options: ['warning_level=2', 'c_std=c11'])
apklayer = dependency('apklayer-plugin')
shared_library('apklayer-vibrator-hello', 'hello.c',
  dependencies: [apklayer, dependency('threads')],
  gnu_symbol_visibility: 'hidden',
  install: true, install_dir: apklayer.get_variable(pkgconfig: 'plugindir'))
```

```
meson setup build && ninja -C build && sudo meson install -C build
```

With plain cc:

```
cc -shared -fPIC -fvisibility=hidden $(pkg-config --cflags apklayer-plugin) hello.c -o libapklayer-vibrator-hello.so -pthread
```

When apklayer is installed under its own prefix (a source build in `~/apklayer-inst`, for example), point pkg-config at it:
`PKG_CONFIG_PATH=~/apklayer-inst/lib/<multiarch>/pkgconfig`. A plugin installed from a Debian package should declare `Depends: apklayer`
and build-depend on `apklayer-dev`.

## Installing: where apklayer looks

apklayer searches these directories, in this order. If two directories hold the same `<domain>-<backend>` name, the first one wins (so a
plugin cannot replace a built-in one by reusing its name):

1. apklayer's own native dir (the built-in plugins);
2. `$APKLAYER_PLUGIN_PATH`, a colon-separated list, for a plugin under development (set it in the environment of the `apklayer` command that
   starts the instance);
3. `~/.local/lib/apklayer/plugins`, for one user;
4. `<libdir>/apklayer/plugins` of the apklayer install, the `plugindir` of `apklayer-plugin.pc`. For the deb that is
   `/usr/lib/<multiarch>/apklayer/plugins`, where packaged plugins go.

## Testing it

```
$ apklayer providers | grep vibrator
backend   vibrator  fake    quality 0     /usr/lib/apklayer/native/libapklayer-vibrator-fake.so
backend   vibrator  hello   quality 0     /usr/lib/aarch64-linux-gnu/apklayer/plugins/libapklayer-vibrator-hello.so
option    vibrator  hello   greeting  hello  text
default   vibrator  (none: off)
$ apklayer providers set vibrator hello greeting=hi
$ apklayer stack libapklayer_vibrator.so --probe     # the HAL's own check over binder: amplitude 0.5, on 120 ms, off
$ grep vibrator ~/.local/state/apklayer/default/vibrator.log
vibrator hi: ready
vibrator hi: on 120 ms amplitude 0.50
vibrator hi: off
```

- `apklayer providers` lists every plugin it found, with its `probe()` answer (`quality N`, `unavailable`, `unloadable` or
  `refused: ...`), its library and its options. The `default` row shows what a domain the user has not set would run.
- `apklayer providers set <domain> <backend> [key=value...]` writes the instance's `providers.toml`. A running instance restarts only that
  domain's front. `apklayer providers unset <domain>` goes back to the default.
- The manager's **Hardware** page shows the same thing: one card per domain, the plugin dropdown with each probe() answer (plugins that
  cannot be used are greyed out), your options as fields, Apply, and the live tests (Vibrate, Read sensors).
- Each front has a `--probe` mode for `apklayer stack <front> --probe` (sensors, vibrator) and a GNSS client
  (`apklayer stack libapklayer_gnss.so --client 3`). The probe's output goes to `tool.log`, the plugin's to `<domain>.log`.
- From an app: `tests/apps/hello-hardware` vibrates, locates and reads the accelerometer through Android's APIs.
- Without Android: `tests/hals/backend-probe.c` (built as `b/bridge/hals/vibrator/backend-probe` in a source build) loads a sensors or
  vibrator plugin on its own: `backend-probe vibrator ./libapklayer-vibrator-hello.so 300 greeting=hi`.

apklayer's own test of this page is the `plugins` group (`tests/run plugins`, `tests/vm/plugins.sh`). It builds the example against the
installed API, installs it into the plugin dir, plays hello.hardware's vibrations through it, and checks that a plugin with another ABI
version is refused.

## ABI version

`APKLAYER_PLUGIN_ABI` (in `apklayer/plugin.h`) is one number covering all five headers. Before apklayer probes, lists or starts a plugin,
it reads the plugin's `apklayer_plugin_abi`. A plugin whose number differs, or that has none, is refused:

- `apklayer providers` lists it as `refused: plugin ABI 2, this apklayer takes 1`, and the manager shows the same;
- it is never the default, and `apklayer providers set` fails with that message;
- an instance whose `providers.toml` names it starts no front for that domain and says why in the stack's output
  (`providers: vibrator: <library> refused: ...`). Nothing crashes.

The number increases whenever a header changes incompatibly: a struct's layout, a call's arguments or its meaning. Adding a new optional
symbol (as `apklayer_<d>_options` was) does not change it. After an apklayer update that raises the number, rebuild the plugin against the
new apklayer-dev. Packaged plugins should depend on the apklayer version range they were built for.
