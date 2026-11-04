# Patches over upstream code

Every edit to upstream source is a patch file whose header says what it changes and why: `patches/<project>/NNNN-*.patch`, against the
commit pinned in the set's `REVISION`, applied idempotently (already-applied patches are skipped) by `patches/apply <project> <tree>`.
bionic_translation: `tools/build bt` (plus `compat/` for old toolchains); Robolectric: `tools/build overlay` applies `patches/robolectric`
to the tree's external/robolectric sources and compiles them into `native-runtime.jar` ahead of `Robolectric_all.jar`; LineageOS tree:
`patches/apply aosp ~/los24` (each `patches/aosp/<project path>/` goes onto that project). apklayer's own code is not a patch and is not
listed per class: the shadows in `runtime/host` (registered by `HostShadowProvider`), the own classes in `runtime/patches`, the java.base
additions (`JdkPatch`/`JdkMembers`), the post-processing of converted apps (`DenyInstrument`) and generated stubs (`tools/gen`).
`android-all` is never patched; dex2jvm is vendored (docs/THIRD_PARTY.md).

Rule: keep a patch thin, mark it inline "apklayer patch" (in its header, and in the code where the change is not in a patch file),
and list it here. The M1 seed's patch log is in `archive/seed-m1/docs/PATCHES.md` at git tag `archive-last`.

## bionic_translation (`patches/bionic_translation/`)
| Patch | Purpose |
|---|---|
| 0001-so-max-512 | linker `SO_MAX` 128 -> 512: Android's libandroid_runtime closure is 237 libraries |
| 0002-properties-from-apklayer | drop its system-property stubs; `__system_property_*` resolve to apklayer's store (bridge/native/properties.c) |
| 0003-path-layer-stat | the path hook also serves stat, lstat, fstatat (bridge/native/paths.c) |
| 0004-path-layer-open-access-opendir-readlink-openat | `__open_2`, openat, access, opendir, readlink through the path layer |
| 0005-mutex-timedlock | timed locking unwraps the bionic mutex like normal locking (glibc overwrote its native pointer) |
| 0006-packed-reloc-trace | packed-relocation prints go through the linker's TRACE facility |
| 0007-cond-destroy-nonblocking | `pthread_cond_destroy` never waits for sleeping waiters, as in bionic (exit-time deadlock) |
| 0008-fortified-pwrite | fortified `pwrite64` through the existing checked write |
| 0009-absolute-plugin-paths | absolute /system, /apex library paths resolve through the instance-root overlay |
| 0010-pi-mutex-attr | mutexattr set/getprotocol translate the mapped attribute (priority-inheritance mutex) |
| 0011-fortified-fread | Android's fortified fread argument order, with its overflow checks |
| 0012-cond-monotonic-deadline | monotonic condition waits take an absolute deadline |
| 0013-atomic-lazy-init-static-objects | statically initialised pthread objects get their native object by compare-and-swap (lost wakeups) |
| 0014-aarch64-irelative | `R_AARCH64_IRELATIVE` (ifunc) relocations, as bionic's linker (libwebviewchromium.so) |
| 0015-cond-signal-broadcast | `pthread_cond_signal` wakes every waiter (HWUI CommonPool hang) |
| 0016-android-get-device-api-level | `android_get_device_api_level` in libc.so (Go cgo selects the Android Q TLS path) |
| 0017-dlsym-rtld-next-host-fallback | `dlsym(RTLD_NEXT)` falls back to the host's symbol (Gecko mozglue interposition) |
| 0018-jvm-owns-fault-signals | bionic libraries do not take over SIGSEGV/SIGBUS/SIGILL/SIGFPE: the JVM owns them |
| 0019-component-host-kill | a service host process cannot signal other processes (EPERM, as with Android's isolated uid) |
| 0020-sigmask-fault-signals | a bionic thread cannot block the JVM's fault signals |
| 0021-readdir-per-thread | readdir's entry buffer per thread; "." and ".." not returned |
| 0022-fortified-stdio-file-mapping | fortified stdio maps bionic's FILE* to glibc's |
| 0023-regex-abi | regcomp/regexec/regfree wrapped for bionic's regex_t ABI |
| 0024-path-layer-unlink-rename-mkdir | unlink, unlinkat, rename, mkdir, rmdir through the path layer |
| 0025-tls-dtpoff | `R_*_DTPOFF` without the load address (x86_64 `__tls_get_addr`) |
| 0026-app-namespace | two linker namespaces (app and system), as Android's classloader namespace |
| 0027-stdio-file-mapping-on-glibc | the FILE-taking stdio functions map bionic's standard streams on glibc too |
| 0028-getenv-home-set-by-app | `getenv("HOME")` returns a HOME the app set itself |
| compat/gcc-before-13-cxx2b | `-std=c++2b` for gcc before 13 |
| compat/libunwind-before-1.8-meson | accept libunwind before 1.8 |
| compat/libunwind-before-1.8-weak-phdr | weak `unw_set_iterate_phdr_function` |

`compat/include/relr-compat.h` (RELR types for glibc before 2.36) and `compat/include/stdatomic.h` (C11 atomics from C++ for gcc before 12)
are headers `tools/build bt` adds for old toolchains, not patches.

## Robolectric (`patches/robolectric/`, external/robolectric)
| Patch | Purpose |
|---|---|
| 0001-native-runtime-loader-arm64 | DefaultNativeRuntimeLoader: arm64 Linux, real window and media natives |
| 0002-surface-control-real-layers | ShadowSurfaceControl: real SurfaceFlinger layers |
| 0003-blast-buffer-queue-real | ShadowBlastBufferQueue: the real libgui BLASTBufferQueue |
| 0004-window-manager-global-layers | ShadowWindowManagerGlobal: relayout2, window layers placed and stacked, frames clamped |
| 0005-display-event-receiver-signature | ShadowDisplayEventReceiver: nativeInit chosen by signature |
| 0006-key-character-map-modifiers | ShadowKeyCharacterMap: no character under Ctrl/Alt/Meta |
| 0007-broadcast-pending-result-finish | ShadowBroadcastPendingResult: Android's own finish() |
| 0008-broadcast-receiver-abort | ShadowBroadcastReceiver: Android's own abortBroadcast() |
| 0009-alarm-manager-rtc-delay | ShadowAlarmManager: an RTC alarm's delay from the wall clock |
| 0010-activity-thread-defaults | ShadowActivityThread: default answers, currentProcessName |
| 0011-user-manager-alive-users-properties | ShadowUserManager: getAliveUsers, getUserProperties |
| 0012-sandbox-loader-art-names | SandboxClassLoader: array and slash names, app classes |
| 0013-instrumentation-acquire-khronos | InstrumentationConfiguration: acquire javax.microedition.khronos |
| 0014-native-surface-parcel | Surface parcel natives are the real ones (a Surface sent through binder) |
| 0015-display-change-without-idle | changeDisplay(qualifiers) leaves the display-change messages to the app's run loop |

## LineageOS tree (`patches/aosp/<project>/`)
| Patch | Purpose |
|---|---|
| frameworks/av/0001-codec2-linear-virtual-map | C2Buffer: non-DMA-heap linear allocations use their own map |
| frameworks/av/0002-mediametrics-vendor-visibility | libmediametrics visible to vendor/apklayer |
| frameworks/base/0001-media-player-native-parcels | MediaPlayer JNI: callback Parcels from apklayer NativeParcel |
| frameworks/native/0001-libbinder-no-security-context | libbinder without kernel security contexts |
| frameworks/native/0002-no-system-server-clients | ActivityManager and SensorPrivacyManager clients without system_server |
| frameworks/native/0003-vulkan-host-dispatch | libvulkan keeps the host loader dispatch pointers |
| frameworks/native/0004-servicemanager-surfaceflinger-as-libraries | servicemanager and SurfaceFlinger as shared libraries |
| frameworks/native/0005-libbinder-work-source-calling-uid | an app JVM's caller uid is the work source of its request header |
| hardware/interfaces/0001-audio-effect-factory-filegroup | audio: the effect factory as a public filegroup |
| libnativehelper/0001-file-descriptor-hotspot | file descriptor helpers on HotSpot |

## Deviations that are not patches
Behaviour that differs from Android or from stock Robolectric without editing upstream, where the reason is not obvious from the code:
- Per-app class loader: app classes child-first, boot and host packages parent-first, as through `LoadedApk` (`runtime/host/java/apklayer/host/AppClassLoader.java`, `runtime/patches/org/robolectric/internal/bytecode/ApklayerAppHook.java`).
- `DenyInstrument` (`runtime/host/java/apklayer/tools/DenyInstrument.java`) rewrites converted apps: `@DoNotInstrument` on every class, app java.* classes to a private package, start/stop/bind/unbindService -> `Services`, `Unsafe.getUnsafe()` (also via `Method.invoke`) -> `UnsafeAccess`, `MultiDex.install` a no-op.
- java.base additions (`--patch-module`): libcore-only members in `JdkMembers.java` (found by `apklayer.tools.JdkScan`), FileDescriptor `getOwnerId$/getInt$/setInt$` and Android's `ArrayList$Itr` limit in `JdkPatch.java`.
- dex2jvm: `HostDefaults.REDIRECT_EXIT = null` (an app's `System.exit` really exits) and the Unsafe redirect target `apklayer.loader.ApklayerUnsafe` (`tools/gen/unsafe-redirect.py`).
- bionic_translation library mappings: `bridge/native/bionic_translation.cfg`, written before upstream's cfg.d by `tools/build cfg` (first line naming a library wins).
- libc symbols Android's libraries import that bionic_translation lacks: `bridge/native/android_platform.c`; generated do-nothing stubs: `bridge/native/android_stubs.c` (`tools/gen/android-stubs.py`).
- SELinux: `bridge/ndk/libselinux.c` allows every check (the kernel has no SELinux).
- JNI shim `bridge/native/runtime_shim.c`: binder/callback threads attach to the JVM, `@CriticalNative` argument thunk, `GetFieldID(FileDescriptor, "descriptor")` answers OpenJDK's `fd`.
- Robolectric setup as its simulator's `AppLoader`: the APK as the manifest, `FixedConfiguration` (NATIVE graphics/SQLite, PAUSED looper), `LazyApplication` with persistent data dirs (`runtime/host/java/apklayer/host/AppEnvironment.java`); JDK opens in `runtime/host/jvm.args`.
- Activity stack and task rules emulating ActivityThread/ActivityTaskManager: `AppHost.java`, `LaunchRules.java`.
- Manifest ContentProviders created before `Application.onCreate`, the `android` package registered: `AppEnvironment.installProviders`, `installSystemPackage`.
- Permissions: normal and listed default permissions granted at start (`HostSystemConfig.java`), runtime requests granted at once (`HostPortals.requestPermissions`).
- Android app uids (10000+): `-Dapklayer.uid`, `ShadowHostProcess.java`; native services get it as the binder work source (aosp patch 0005 above).
- Keystore: `HostKeyStoreProvider` is a software "AndroidKeyStore" in the app's data directory, not hardware-backed.
- Media store: `HostMediaStub` answers the "media" provider with nothing indexed until the tree's MediaProvider is installed.
- Cross-app intents: `HostApps`, `ManifestFilters` match other installed apps' filters when the instance hub is off (with the hub, `HubActivityClient` asks it).
- MessageQueue: `ShadowHostMessageQueue` turns off DeliQueue (Robolectric's scheduler reads the legacy queue).
- Window container a window manager would make for each window: `runtime/patches/org/robolectric/shadows/WindowSurface.java`.
- Codec2: `aosp/hals/media/codec2/SoftCodecs.cpp` loads whichever tree `libcodec2_soft_*` are installed (the platform list aborts on a missing one); codec list `bridge/native/android-root/vendor/etc/media_codecs.xml`.
- dex2jvm (`runtime/dex2jvm`, marked "apklayer patch"): `TypeInference.needsNarrowingCast` skips the checkcast when the required type is an interface (JVMS 4.10.1.2: any class is assignable to an interface type for the verifier).
- dex2jvm `InitRepointPlan` (marked "apklayer patch"): the constructor scan keeps a tracked object across writes on other branches; constructors R8 deleted but still names get forwarders up to the class resolution reaches (ART resolves through the superclass).
- Signature spoofing (`ShadowHostPackageManager.fakeSignature`) mirrors LineageOS `ComputerEngine.isMicrogSigned`/`generateFakeSignature`: gms/vending only, signed exactly with MICROG_REAL_SIGNATURE, `fake-signature` equal to MICROG_FAKE_SIGNATURE (compared by SHA-256); the debuggable gate is LineageOS's compile-time ANDROID_DEBUGGABLE, true for our userdebug tree.
