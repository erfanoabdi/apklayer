# Environment variables and JVM properties

A user sets **settings** (below), in the manager's Settings page (global, and per app in the app's page) or with
`apklayer config list | get KEY | set KEY VALUE | unset KEY [--app <package>]`. They live in `~/.config/apklayer/config` (`key=value`, or
`<package>:key=value`), one table in `cli/config.cpp` feeds the CLI and the manager. Every apklayer command reads the file, and the launcher hands a
setting on to the runtime as the variable of the table's second column; a variable already set in the environment wins over the file (that is how
the debug switches and the tests work).

Nothing else is a user option. The rest of this page is for people who work on apklayer.

## Settings (the manager, `apklayer config`)

| key | variable | default | what it does | per app |
|---|---|---|---|---|
| `gl-hide` | `APKLAYER_GL_HIDE` | empty | GL extensions hidden from Android code (a driver that advertises a format it decodes wrongly) | yes |
| `gles-claim` | `APKLAYER_GLES_OVERRIDE` | auto | auto: apps are told OpenGL ES 3.2 where the host driver is older but copes; off: the driver's version | |
| `vulkan` | `APKLAYER_VULKAN_PRESENT` | auto | auto: Vulkan where the host can present; off: none; on: also where the probe finds no device | yes |
| `aot` | `APKLAYER_AOT` | on | JDK 25 AOT cache for the app JVMs; off: the class data cache only | |
| `jvm-mem` | `APKLAYER_JVM_MEM` | tuned | tuned: apklayer's heap and compiler sizing; default: the JVM's own | yes |
| `jvm-options` | `APKLAYER_JOPTS` | empty | extra JVM options, space separated | yes |
| `java` | `APKLAYER_NATIVE_JAVA` | the system's Java 21 | the java the apps run on (a phone's small root file system) | |
| `permissions` | `APKLAYER_PERMISSIONS` | ask | grant: every runtime permission an app requests is granted without the prompt | yes |
| `proc-view` | `APKLAYER_PROC_VIEW` | app | host: /proc as the host has it, not only the app's own processes | yes |
| `etc-view` | `APKLAYER_ETC_VIEW` | app | host: the host's /etc | yes |
| `pts-view` | `APKLAYER_PTS_VIEW` | app | host: all of the host's terminals | yes |
| `raw-syscall-view` | `APKLAYER_RAW_SYSCALL_VIEW` | on | off: an app's own system calls see the host's /proc, /etc and identity | yes |
| `webview-provider` | `APKLAYER_WEBVIEW_PROVIDER` | Chromium when installed | none, or the package of a provider | yes |
| `audio-output` | `APKLAYER_AUDIO_TARGET` | the default sink | the PipeWire/PulseAudio sink apps play to | |
| `audio-input` | `APKLAYER_AUDIO_TARGET_IN` | the default source | the source apps record from | |

Not here on purpose: the window style, colour scheme and the other host settings are `apklayer settings` (live in a running instance, also in the
manager); the instance's GPU, binder, hardware providers and Android properties have commands of their own (`gpu`, `binder`, `providers`, `prop`).

## Debug (stay environment variables, never documented to users)

Read where they are needed; set them for one command (`APKLAYER_RUNTIME_DEBUG=1 apklayer run <package>`).

| variable | effect |
|---|---|
| `APKLAYER_RUNTIME_DEBUG` | start-up timeline on stderr, the shell's and HAL's frame lines, every log line the runtime would drop |
| `APKLAYER_LOGCAT` | the app's android.util.Log on its output (also `-Dapklayer.logcat=true`) |
| `APKLAYER_CRASH_TRACE` | frame-pointer chain on SIGSEGV/SIGABRT (the launcher sets it to 1) |
| `APKLAYER_DUMP_ON`, `APKLAYER_DUMP_ABORT` | the first log line containing the text prints the JNI/native state; with ABORT the process aborts there |
| `APKLAYER_JNI_EXC`, `APKLAYER_JNI_ROUTE_DEBUG` | pending Java exception after a JNI call from native code; the JNI system route |
| `APKLAYER_PATHS_DEBUG`, `APKLAYER_RAW_SYSCALL_DEBUG`, `APKLAYER_LINKER_DEBUG` | the path layer's, the raw-syscall filter's, the linker's trace |
| `APKLAYER_BINDER_DEBUG`, `APKLAYER_BINDERD_DEBUG` | the userspace binder route's and its daemon's trace |
| `APKLAYER_DEBUG_INPUT` | the shell logs focus changes and key codes |
| `APKLAYER_PTRACER` | allow ptrace by any process (gdb, strace) |
| `APKLAYER_PRESENT_FENCE` | `gpu`: no sync object for presents (HAL tests) |
| `APKLAYER_CDS` | `off`: no class data sharing archive (a corrupt archive) |
| `APKLAYER_GST_AMC` | keep the Android-MediaCodec GStreamer elements on Halium |
| `APKLAYER_GPU` | the instance's GPU for one command (the persistent choice is `apklayer gpu set`) |
| `APKLAYER_BOOT_BROADCASTS`, `APKLAYER_MEDIA_WATCHES` | `0`/a package: BOOT_COMPLETED receivers; the hub's inotify budget |
| `APKLAYER_DISPLAY_WIDTH`, `_HEIGHT`, `_DPI` | the virtual display (720x1280, 320 dpi) |
| `APKLAYER_UNSTRIPPED` | a directory of unstripped libraries for `tools/debug/symbolize-trace.py` |
| `-Dapklayer.perf`, `.runms`, `.debugkeys`, `.debugwheel`, `.runtimetrace`, `.rawtraces`, `.traceroots`, `.tracebind`, `.tracestart`, `.threads`, `.tracewindows` | JVM-side traces and the run time of a test launch (through the `jvm-options` setting) |

## Test only

`APKLAYER_TEST_INPUT` (the shell takes injected input), `APKLAYER_OUTPUT_MM`, `APKLAYER_OUTPUT_PX` (a simulated phone output), `APKLAYER_SUBSURFACES`
(a compositor without subsurfaces), `APKLAYER_PICKER`, `APKLAYER_CHOOSER` (stand-ins for the file dialog and the app chooser),
`APKLAYER_GEOCLUE_PROBE`, `APKLAYER_PLUGIN_PATH` (a plugin under development), `APKLAYER_RELEASES_URL`, `APKLAYER_RUNTIME_BUNDLE`,
`APKLAYER_MICROG_INDEX`, `APKLAYER_FDROID_INDEX` (offline and local setup sources). Set by `tests/` and `tools/`; `tests/` use the debug
switches above the same way.

## Plumbing (set by apklayer for its own processes; not options)

Paths and ids the launcher computes and the native and Java sides read: `APKLAYER_INSTANCE`, `_INSTANCE_ROOT`, `_NATIVE`, `_ANDROID_LIBS`, `_ANDROID_ROOT`,
`_BT`, `_DEPS`, `_ROOT`, `_BUILD`, `_JAVA`, `_CLI`, `_LOGS`, `_STORAGE`, `_DATA`, `_OWN_DATA`, `_PROPERTIES`, `_APK_PATHS`, `_HOST_LIBS`,
`_HOST_BINDER`, `_HOST_HWBINDER`, `_BINDER`, `_BINDER_DIR`, `_RENDER_NODE`, `_COMPOSITOR_NODE`, `_GLES_VERSION`, `_VULKAN_HOST`, `_VULKAN_VERSION`,
`_NO_SYSTEM_SERVER`, `_WINDOW_MODE`, `_SHELL`, `_APP_SHELL`, `_APP_ID_PREFIX`; per launch: `_INTENT_FILE`, `_INTENT_TEXT`, `_RESULT_FILE`,
`_COMPONENT_HOST`, `_ISOLATED`, `_PROCESS`; pipes between our processes: `_DAEMON_READY_FD`, `_HUB_READY_FD`, `_SHELL_READY_FD`, `_STARTED_FD`,
`_SPARE_FD`, `_GUEST_ENV_FD`, `_STACK_PREPARED`; services started on first use: `_LAZY_NAMES`, `_LAZY_FIFO`, `_LAZY_SERVICE`; the sandbox and
apklayerd: `_SANDBOX` (opt-in, docs/INSTALL.md), `_SANDBOX_PRELOAD`, `_SANDBOX_STORAGE`, `_PEER_PID`, `_BROKER`, `_BROKER_HOME`, `_BROKER_LOG`,
`_UNIT`; the 32-bit runtime: `_ARM32`, `_ARM32_PRELOAD`. The JVM properties `-Dapklayer.package|data|uid|libdir|apps|cli|native|androidlibs|storage|
nativejar|platformres|applog|processname|isolated|componenthost|layerstack|displaywindow|shell|nativebridge|system|hostidlems|...` are the same kind:
`cli/run.cpp` and `cli/env.cpp` pass them on the command line. Moving this set into command-line arguments or one internal file is open (docs/PLAN.md).

## Vendored: not apklayer's

`runtime/dex2jvm/` (upstream, unchanged, docs/THIRD_PARTY.md) keeps only the `DEX2JVM_*` switches of its `Options` class (SYNTHETIC_PREFIX, REDIRECT_UNSAFE/EXIT, THREADS, LOG_TAG, PARSED_MARKER, LIBRARY, TEST_FAIL_CLASS: set by tests/vm/dex2jvm-stub.sh). Its ~25 other experiment/debug/force knobs (FRAME_*, *_DEBUG, *_FORCE, SPLIT_*, MONITOR_COVER*, NARROW_TRY, SUPER_IFACE*, INIT_REPOINT, ...) were read but set nowhere: removed 2026-11-14, their code is hard-coded to the default.
Build-time only: `APKLAYER_PREBUILTS`, `_SLINT`, `_ARCH`, `_VARIANT`, `_PRODUCT`, `_MAVEN`, `_DEPS_LOCAL`, `_ORB`, `_SAILFISH_CACHE`, `_CLICK_JAVA`.

## Removed (2026-11-14)

`APKLAYER_START_SHELL`, `APKLAYER_LAZY_SERVICES`, `APKLAYER_SETTINGS_IN_HUB`, `APKLAYER_PRELOAD_EXTRA` (their default is the only behaviour now) and the
never-set `-Dapklayer.autotap`, `.autotap.ms`, `.debugclickables`, `.debugdata`, `.notaskroot`, `.startservice`, `.tracesvc`, `.sdk`, `.qualifiers`.

Counts: variables read by the code 114 -> 110 (15 settings, 24 debug, 12 test only, 58 plumbing, 1 build); `System.getProperty`/`getBoolean` names
of the runtime 48 -> 39.
