# HIDL libraries for old system apps

Old system apps (targetSdk below Q) call `android.hardware.*` HAL proxies through the framework's `HwBinder`/`HwParcel` and
`android.hidl.manager@1.0` `IServiceManager`. Four pieces make that work; the first three are done.

| Piece | Where | State |
|---|---|---|
| `android.hidl.base`/`manager` Java classes | the framework (`android-all` carries `android/hidl/{base,manager}/V1_0..V1_2`); `HostSharedLibraries.used` drops `android.hidl.base-V1.0-java` / `android.hidl.manager-V1.0-java` from `<uses-library>` (the real names; the old names never matched), as AOSP's AndroidHidlUpdater does for non-system apps | done, no jar to stage |
| HwBinder JNI in app processes | `bridge/native/binder_runtime.c` `apklayer_register_binder`: the framework's own `register_android_os_{HidlMemory,HidlSupport,HwBinder,HwBlob,HwParcel,HwRemoteBinder,NativeHandle}` from `libandroid_runtime`, inside the same renaming window as `android.os.Binder`; a failure is logged and not fatal | done |
| an instance hwservicemanager | `libapklayer_hwservicemanager` (patches/aosp/system/hwservicemanager 0001, allow-all `aosp/hals/hwservicemanager/AccessControl.cpp`), started by `cli/stack.cpp` | done, every instance (with host HALs beside the stand-in, below) |
| `hidltool manager`, group `hidl` | `aosp/tools/hidltool.cpp`, `tests/apps/hello-hidl`, `tests/vm/hidl.sh` | native client and app |

## The gate

libhwbinder (`Utils.cpp`) picks the real hwservicemanager only when `/system/bin/hwservicemanager` (or the system_ext one) exists, and then every
libhidl client waits for the property `hwservicemanager.ready`. Without the file, libhidl answers with its `NoHwServiceManager` stand-in, which
patches/aosp/system/libhidl 0001-0003 teach to forward the names in `host_hals.conf` to the host (Halium: `camera`, `media`, `graphics = device`).
So the stack starts the manager in every instance, stages its VINTF entry (`aosp/hals/hwservicemanager/manifest.xml`, the tree's `hwservicemanager.xml`:
the manager refuses to run without its own `android.hidl.manager` entry), and writes an empty `system/bin/hwservicemanager` into the instance's own root
(never the shared android-root) only when `host_hals.conf` lists nothing.

## Findings while making it work

- Robolectric's instrumented `android.os.Hw*` classes return defaults for their natives unless a shadow says `callNativeMethodsByDefault`
  (`HostPlainShadows.HwBinder` and the others, listed in `HostShadowProvider`): registering the JNI alone left `getService` returning null.
- The tree's hwservicemanager aborted at its first transaction (`CHECK_EQ` on the serving stack pointer: no security context here); patches/aosp/system/hwservicemanager 0002.
- `libandroid_runtime`'s `LOG(INFO) << ...` in `JHwBinder_native_getService` crashed in an app JVM (SEGV_ACCERR, pc in libc++'s rodata, from its inline
  `std::__put_character_sequence`). Root cause (2026-11-15, from the hs_err registers): not libc++ against libstdc++ but two libc++ copies. `libpdfium.so`
  links libc++ statically and exported 699 `std::__1` symbols (`locale::use_facet`, `ctype<char>::id`, `basic_ostream::sentry`...); libandroid_runtime
  lists libpdfium.so (NEEDED 77) before libc++.so (101), so the bionic linker's lookup bound its `ctype<char>::id` and `use_facet` to pdfium's copy while
  the ostringstream's locale (made in libbase) holds libc++.so's facets numbered by libc++.so's own ids: `use_facet` returned a `collate<char>` for
  `ctype<char>`, and `widen()` (vtable slot 7) jumped into rodata. libbase and the stack's processes never crashed because libbase lists libc++ first (NEEDED 2) and the stack loads no pdfium; libhwui lists pdfium (23)
  before libc++ (46) and was bound to pdfium's copy too (73 undefined std::__1 symbols), it only never reached the unset-fill path, so the fix matters beyond HIDL. Fix at the source: patches/aosp/external/pdfium 0001 (`-Wl,--exclude-libs` for libc++_static.a, libc++abi.a, libunwind.a in
  libpdfium's ldflags); `m libpdfium` (x86_64 and arm64). The earlier workaround (android-base minimum severity WARNING in `apklayer_register_binder`)
  is gone.

## Own services and Halium instances (2026-11-15)

- An app's own HIDL service: libhidl's `registerAsService` and `getService` both ask `getTransport`, which the manager answered from the VINTF manifests only.
  patches/aosp/system/hwservicemanager 0003 answers hwbinder for packages outside `android.*` and for instance names starting with `local` (a platform
  interface in a test); platform HALs keep needing their entry. `hello.hidl` registers an `IBase.Stub` as `local-java`, and sees it in `list()` (not fetched back: in the app's own process the Java proxy's check calls into itself and `getService` finds nothing)
  (a native `IFoo::getService()` of such a name nobody registered now waits for it instead of failing at once; Java's does not retry)
  (the SDK hides the class: `tests/apps/hello-hidl/compile-only/` is compiled against and left out of the APK, see `tests/apps/build.sh`);
  `hidltool local` does the same natively (an `IBase` as `local-native`, in its own process).
- Instances with host HALs (Halium): the manager now runs there too, beside libhidl's stand-in (the gate file `system/bin/hwservicemanager` stays absent, so
  the stand-in keeps forwarding the listed names to the host). The stack writes `/data/apklayer/hwservicemanager.local`; with it, patches/aosp/system/libhidl 0004
  sends the stand-in's other calls (get, add, addWithChain, getTransport, list, listByInterface, listManifestByInterface, notifications, client callbacks)
  to the manager of the instance's own binder (a Halium instance's unlisted names also meet the manager's VINTF: a name the instance manifests declare as
  hwbinder that nobody serves now makes a native `getService` wait, where the stand-in said "nothing"). Checked on the UTM VM by `tests/vm/host-binder.py`
  (group `host-binder`): a hand-written `host_hals.conf` listing atrace, the marker, the manager started with its VINTF entry staged by the test, and a second
  instance playing the device: `hidltool local` (register, find, list) passes through the stand-in's local branches and `hidltool atrace` still reaches the device.
  The stack's part (starting the manager and writing the marker where `writeHostHals` chose device HALs) is not run, it needs `probeDeviceHal` to see a Halium
  device. Checked on the X23 (2026-11-16, `tools/halium/phone-test.sh ut hidl-host hidl`): the stack in an instance with `camera`/`media`/`graphics = device` starts the manager
  and writes the marker, the host-forwarded names (camera provider, Codec2 stores) are listed with the manager running, `hidltool manager` and `local` pass, and
  `hello.hidl` (Java) gets the manager and registers its own service since libhidl 0006 (before it, `HwBinder.getService(IServiceManager)` returned the local stand-in
  and the Java side saw NoSuchElementException).

## What remains

- Android ships `android.hidl.*-java` as boot jars; an app that is not a system app gets the same classes here (the framework carries them).
