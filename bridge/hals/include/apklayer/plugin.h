/* apklayer hardware plugins: what every plugin shares, whatever its domain (docs/PLUGINS.md).
 *
 * A plugin is a glibc shared library libapklayer-<domain>-<backend>.so implementing one domain's header (apklayer/sensors.h, gnss.h,
 * vibrator.h, camera.h). apklayer loads one per HAL front process (the front is Android code running through bionic_translation; the plugin
 * is ordinary host code: link any host library, never an Android one). Its stderr is the front's log, <instance state>/<domain>.log.
 *
 * ABI: every plugin defines, once, in one of its sources,
 *     const int apklayer_plugin_abi = APKLAYER_PLUGIN_ABI;
 * apklayer reads it before it probes or starts the plugin and refuses a plugin whose number is not its own (or that has none): listed as
 * "refused" by `apklayer providers`, never loaded into a front. The number grows whenever a declaration in these headers changes
 * incompatibly (a struct's layout, a call's arguments or meaning); new optional symbols do not change it.
 *
 * Calls every domain has (<d> is the domain: sensors, gnss, vibrator, camera):
 *   int apklayer_<d>_probe(void)
 *       Is the hardware there? <0: not usable on this host; 0: usable, but never picked by default (fakes, test plugins); >0: quality, the
 *       usable plugin with the highest is the domain's default when providers.toml names none. Called by the apklayer CLI in a forked child
 *       process that exits right after (so it may start threads or connect to a bus, and need not clean up), without init() first. Should
 *       answer within about a second: `apklayer providers` and every stack start wait for it. Values above 253 count as 253.
 *   extern const char apklayer_<d>_options[]   (optional)
 *       The options init() takes, for `apklayer providers` and the manager's Hardware page: one line "key\tdefault\tkind" per option, kind
 *       "text" or "file" (a file chooser), lines separated by '\n'. Read as a constant string (the CLI dlopens the plugin in its own
 *       process for this and for the ABI check: no constructors with side effects, no threads started at load).
 *   int apklayer_<d>_init(int count, const char *const *options)
 *       First call in the front, on its main thread, before any other: the domain's options from providers.toml, `count` strings
 *       "key=value" (valid during the call only). Returns 0, or -errno (the front logs strerror and exits; the stack restarts it a few times, then leaves
 *       the domain off).
 *       Unknown keys should be ignored or refused with -EINVAL, never crash.
 * Every other call comes after a successful init(), from the threads its header names. Buffers passed in are the caller's and valid only
 * during the call unless the header says otherwise. There is no deinit: the front ends by SIGTERM (`apklayer stop`, a changed configuration),
 * so anything the plugin holds must be released by the process ending (file descriptors, bus connections). */
#ifndef APKLAYER_PLUGIN_H
#define APKLAYER_PLUGIN_H

#define APKLAYER_PLUGIN_ABI 1

#ifdef __cplusplus
extern "C" {
#endif

#pragma GCC visibility push(default)  /* the API stays exported from a plugin built with -fvisibility=hidden */
extern const int apklayer_plugin_abi;
#pragma GCC visibility pop

#ifdef __cplusplus
}
#endif
#endif
