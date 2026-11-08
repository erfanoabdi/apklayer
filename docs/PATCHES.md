# Patches over upstream code

Every edit to upstream source is a patch file whose header says what it changes and why: `patches/<project>/NNNN-*.patch`, against the
commit pinned in the set's `REVISION`, applied idempotently (already-applied patches are skipped) by `patches/apply <project> <tree>`.
bionic_translation: the Meson subproject (`subprojects/bionic_translation.wrap`); meson.build runs `patches/apply` at configure time and applies `compat/` as conditions on the toolchain; Robolectric: `runtime/meson.build` applies `patches/robolectric`
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
| compat/gcc-before-11-cxx20 | `-std=c++20` for gcc before 11 (Sailfish OS 5.0: gcc 10), on top of the previous one |
| compat/glibc-before-2.36-relr | RELR types in the linker for glibc before 2.36 (Ubuntu 22.04, Sailfish OS 5.0: 2.30) |
| compat/glibc-before-2.38-libc | libc functions Android's libraries use that glibc before 2.38 lacks: `__strlcpy_chk`, `__strlcat_chk`, `fstat64`, `pidfd_open`, `res_mkquery` (Sailfish OS 5.0: 2.30) |
| compat/libunwind-before-1.8-meson | accept libunwind before 1.8 |
| compat/libunwind-before-1.8-weak-phdr | weak `unw_set_iterate_phdr_function` |

`compat/include/stdatomic.h` (C11 atomics from C++ for gcc before 12) is a header meson.build adds (a global include directory: Meson
before 1.8 ignores a subproject's default_options cpp_args), not a patch.

## Robolectric (`patches/robolectric/`, external/robolectric)
| Patch | Purpose |
|---|---|
| 0001-native-runtime-loader-arm64 | DefaultNativeRuntimeLoader: arm64 Linux (and arm: the 32-bit app process), real window and media natives |
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
| 0019-navigation-bar-visibility-listener | ShadowWindowManagerGlobal.applyInsets tells `NavigationBarInset` when an app hides or shows the navigation bar (immersive mode) |
| 0020-alarm-manager-pending-intent-hook | ShadowAlarmManager: PendingIntent alarms go to a hook (the hub holds them, HubScheduler) |
| 0021-native-assets-content-key | the shared ICU/font copy keyed by the jar's entries' content (name, CRC, size), not its path and mtime (a copy per install) |

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
| frameworks/native/0007-libbinder-own-uid-is-system | a caller with the stack's own uid counts as the system uid 1000 (Ubuntu Touch's phablet is uid 32011, an app uid to Android; Waydroid 0009 by the process uid) |
| hardware/interfaces/0001-audio-effect-factory-filegroup | audio: the effect factory as a public filegroup |
| libnativehelper/0001-file-descriptor-hotspot | file descriptor helpers on HotSpot |

## Deviations that are not patches
Behaviour that differs from Android or from stock Robolectric without editing upstream, where the reason is not obvious from the code:
- Per-app class loader: app classes child-first, boot and host packages parent-first, as through `LoadedApk` (`runtime/host/java/apklayer/host/AppClassLoader.java`, `runtime/patches/org/robolectric/internal/bytecode/ApklayerAppHook.java`).
- `DenyInstrument` (`runtime/host/java/apklayer/tools/DenyInstrument.java`) rewrites converted apps: `@DoNotInstrument` on every class, app java.* classes to a private package, start/stop/bind/unbindService -> `Services`, `Unsafe.getUnsafe()` (also via `Method.invoke`) -> `UnsafeAccess`, `MultiDex.install` a no-op.
- java.base additions (`--patch-module`): libcore-only members in `JdkMembers.java` (found by `apklayer.tools.JdkScan`), FileDescriptor `getOwnerId$/getInt$/setInt$` and Android's `ArrayList$Itr` limit in `JdkPatch.java`.
- dex2jvm: `HostDefaults.REDIRECT_EXIT = apklayer.loader.ApklayerExit` (an app's `System.exit`/`Runtime.exit` right after an activity start is a restart: ProcessPhoenix, Aurora Store; any other exit really exits) and the Unsafe redirect target `apklayer.loader.ApklayerUnsafe` (`tools/gen/unsafe-redirect.py`).
- bionic_translation library mappings: `bridge/native/bionic_translation.cfg`, written before upstream's cfg.d by `bridge/native/meson.build` (`tools/gen/bt-cfg.py`) (first line naming a library wins).
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
- Navigation bar of the shell as the display's navigation bar inset (mobile window style): `runtime/patches/org/robolectric/shadows/NavigationBarInset.java` (sets SystemUi's NavigationBarBehavior; AppHost.hostResize); robolectric 0019 makes the app's hide/show of the navigation bar (WindowInsetsController, SYSTEM_UI_FLAG_HIDE_NAVIGATION) reach it, AppHost passes it to the shell (MSG_NAVBAR).
- apklayer patch in `bridge/hals/graphics/display.c`: the window style (`APKLAYER_WINDOW_MODE`) is sent to the shell before the window is created.
- Window container a window manager would make for each window: `runtime/patches/org/robolectric/shadows/WindowSurface.java`.
- Codec2: `aosp/hals/media/codec2/SoftCodecs.cpp` loads whichever tree `libcodec2_soft_*` are installed (the platform list aborts on a missing one); codec list `bridge/native/android-root/vendor/etc/media_codecs.xml`.
- dex2jvm (`runtime/dex2jvm`, marked "apklayer patch"): `TypeInference.needsNarrowingCast` skips the checkcast when the required type is an interface (JVMS 4.10.1.2: any class is assignable to an interface type for the verifier).
- dex2jvm `InitRepointPlan` (marked "apklayer patch"): the constructor scan keeps a tracked object across writes on other branches; constructors R8 deleted but still names get forwarders up to the class resolution reaches (ART resolves through the superclass).
- Signature spoofing (`ShadowHostPackageManager.fakeSignature`) mirrors LineageOS `ComputerEngine.isMicrogSigned`/`generateFakeSignature`: gms/vending only, signed exactly with MICROG_REAL_SIGNATURE, `fake-signature` equal to MICROG_FAKE_SIGNATURE (compared by SHA-256); the debuggable gate is LineageOS's compile-time ANDROID_DEBUGGABLE, true for our userdebug tree.
- Robolectric 0016: the constructor split skips `<init>` calls of objects the constructor itself creates with NEW (`this(new SecurityException(..), ..)` in AuthenticationRequiredException cut the constructor inside its argument list; DAVx5). Upstream 872a2e0d4 still has it.
- bionic_translation 0032: symbol lookup also searches the dlopen root's dependency tree, last (Android's local group: a library using a sibling's symbols it does not list; SimpleX).
- Robolectric 0017: ShadowUiAutomation skips windows whose root view is not VISIBLE for input and screenshots (a stopped activity's dialogs and popups, hidden by AppHost, take no taps).
- bionic_translation 0033 linker-dt-runpath: DT_NEEDED searched in DT_RUNPATH (else DT_RPATH) first, absolute directories only. 0034 linker-quiet-wrap-hint: "symbol may need to be wrapped" only with linker debugging. 0035 pthread-ready-tag: a tag beside the glibc pointer replaces mincore() on every mutex lock/unlock and cond wait (~6% of a cold start's CPU).
- dex2jvm `GuestRepoint` (marked apklayer patch): an app's `ByteBuffer.allocateDirect/hasArray/array/arrayOffset` go to `HostDefaults.REDIRECT_BUFFER` = `apklayer.loader.ApklayerBuffers` (Android's direct buffers have a backing array; on HotSpot `array()` is a refreshed copy, writes through it are lost).
- dex2jvm `NestPlan` (apklayer patch): NestHost/NestMembers only within one run-time package (JVMS 5.4.4); R8-repackaged classes that keep EnclosingClass are left out of the nest.
- Robolectric 0004: `place(layer, info, windows)`, a sub-window's container nests in its parent's. 0017: input sorted by WindowOrder (a sub-window with its parent), not by type. runtime/patches WindowOrder.java (new) and WindowSurface (sub-window reparenting); application windows stack with their activity (its base window's add order), system windows above (WindowOrder.keys, WindowOrder.screenLayer).
- `patches/aosp/frameworks/libs/binary_translation/` (REVISION Digitalis `abefbe2b`): 0001 each guest thread gets its own zeroed `pthread_internal_t` (tid set) and `bionic_tls` with glibc-host-thread-id-handoff (the host TLS slots are glibc's under bionic_translation); 0002 the function-wrapper code in an anonymous code region on a glibc host (no ANDROID_DLEXT_RESERVED_ADDRESS). Tree hook files `vendor/extra/{product,BoardConfigExtra}.mk` (only with APKLAYER_NATIVE_BRIDGE=arm64).
- bionic_translation 0036: `dlsym` on libc_bio's handle falls back to the process's global symbols (the bridge's proxy libc looks up `__system_property_*` there).
- `patches/aosp/hardware/interfaces/0002-sensors-vibrator-gnss-example-sources.patch`: the example HALs' sources as public filegroups; Sensors members protected; `Gnss::getLocationFromHW` protected and virtual. `0003-sensors-constructor-without-example-sensors.patch`: a protected `Sensors(bool)` that skips the example sensors (destroying them deadlocks: `~Sensor` holds its run mutex while joining).
- frameworks/native 0006-libbinder-host-context: a host binder context (`/dev/host_binder`, mapped by bridge/native/paths.c to $APKLAYER_HOST_BINDER): per-context process and thread state, BpBinder and Parcel carry their context, buffers freed by address, servicemanager fallback (getService, transaction 1) for names in `/data/apklayer/host_hals.conf`, the older stability format for hosts up to Android 13. From Waydroid frameworks/native 0015. Rebuild: `m libbinder`.
- bionic_translation 0037-linker-no-host-system-lib: no `/vendor/lib`, `/system/lib` default search paths (on Halium they are the device's 32-bit Android).
- frameworks/av 0003-camera-trust-instance-uid: cameraserver trusts its own Linux uid to name a client (Camera1's native connect carries the kernel uid beside an attribution with the app's uid); AID_SYSTEM too (libbinder 0007 reports the stack's own uid as system on Ubuntu Touch).
- Vendored Goldfish `aosp/hals/camera/goldfish/HwCamera.cpp` (marked "apklayer patch"): the JPEG blob header is written before unlock (our mapper unmaps on unlock; the provider crashed on every still).
- system/libhwbinder 0001-host-hwbinder-context: a host hwbinder context (`/dev/host_hwbinder`, mapped by paths.c), no TXN_SECURITY_CTX (Waydroid libhwbinder 0001/0002). A released host proxy is expunged from the host context (IPCThreadState::expungeHandle is static and local). Rebuild: `m libhidlbase`.
- system/libhidl 0001-host-hals: `getService` asks the host's hwservicemanager first for names in `host_hals.conf` (Waydroid libhidl 0001-0005, 0007, 0008). Rebuild: `m libhidlbase`.
- system/libhidl 0003-host-listing-notifications: the no-hwservicemanager stand-in forwards listManifestByInterface (falling back to listByInterface), getTransport, registerForNotifications and the others to the host for listed HALs (cameraserver's provider listing, the Codec2 client's store names). Rebuild: `m libhidlbase`.
- system/libhidl 0002-host-hals-passthrough: a listed HAL the host does not serve (the device's gralloc mapper) is loaded as passthrough from /vendor; the local hwservicemanager is never asked. Rebuild: `m libhidlbase`.
- bionic_translation 0038-pthread-attr-bionic-fields: pthread attrs keep bionic's stack_size and guard_size filled (the native bridge's proxy pthread_create sizes the guest stack from them). binary_translation 0001 (amended): the guest linker's host pthread is the thread's own bionic record on a glibc host.
- bionic_translation 0042-fault-signals-chained-with-libjsig: with HotSpot's libjsig preloaded, a bionic fault handler is chained behind the JVM's instead of dropped (0018). binary_translation 0003-glibc-host-guest-errno: on a glibc host, every trampoline into host code carries errno both ways.
- bionic_translation 0039-linker-vendor-namespace: with BIONIC_VENDOR_LD_LIBRARY_PATH, a Halium device's vendor libraries (GPU driver, passthrough mapper) get their own copies of what they need from /vendor and the device's VNDK apex; the LLNDK and libc/libm/libdl stay shared. Unset: unchanged.
- bionic_translation 0040-pthread-attr-copy-owner: the glibc attr's page records the bionic attr that made it; a byte copy (native_bridge_support's proxy pthread_create memcpy's the guest attr, edits its stack fields, destroys it) no longer unmaps the original's glibc attr on destroy, and pthread_create takes a copy's stack from bionic's fields. Fixed OTR2 through Berberis crashing in pthread_attr_destroy at about every second start. Test: tests/native/attr-copy.c (suite bionic).
- bionic_translation 0041-linker-vendor-shared-libs: BIONIC_VENDOR_SHARED_LIBS (basenames, ':'-separated) names libraries the vendor namespace never loads its own copy of; the CLI sets libhidlbase.so for graphics = device (the vendor's Android 12 libhidlbase waited forever for hwservicemanager.ready inside the Mali driver's IMapper::getService). Unset: unchanged.
- bionic_translation 0043-sigset-empty-fill: sigemptyset and sigfillset on bionic's one-long sigset_t; glibc's wrote its whole set (128 bytes in glibc 2.30, 16 in newer ones) past a set on the caller's stack (libandroidfw died in __stack_chk_fail on Sailfish OS 5.0).
- bionic_translation 0044-mmap2-32-bit: `__mmap2` for 32-bit bionic (its mmap and mmap64 call it with the offset in pages; glibc exports none): libprotobuf-cpp-lite and everything above it (libhwui, libandroid_runtime) failed to link in the 32-bit app process.
- bionic_translation 0045-rel-relative-addend: R_ARM_RELATIVE adds the load base to the word's implicit addend (REL, 32-bit ARM); it wrote the bare base, so an NDK library's pointer tables (JNINativeMethod for RegisterNatives) pointed at its ELF header.
- frameworks/native 0003-vulkan-host-dispatch (2026-11-08): the host-dispatch hooks are `VKAPI_ATTR` (their casts broke the arm32 build: `-Wcast-calling-convention`).
- hardware/interfaces 0004-gnss-no-mock-fix-from-a-subclass: `Gnss::reportsMockLocation()` (virtual, true); a subclass without a fix reports none.
- robolectric 0018-location-manager-requests-listener: ProviderEntry tells `LocationRequests` (runtime/patches) when a provider's first request comes and its last goes.
