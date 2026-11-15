# HIDL libraries for old system apps

Old system apps (targetSdk below Q) call `android.hardware.*` HAL proxies through the framework's `HwBinder`/`HwParcel` and
`android.hidl.manager@1.0` `IServiceManager`. Four pieces make that work; the first three are done.

| Piece | Where | State |
|---|---|---|
| `android.hidl.base`/`manager` Java classes | the framework (`android-all` carries `android/hidl/{base,manager}/V1_0..V1_2`); `HostSharedLibraries.used` drops `android.hidl.base-V1.0-java` / `android.hidl.manager-V1.0-java` from `<uses-library>` (the real names; the old names never matched), as AOSP's AndroidHidlUpdater does for non-system apps | done, no jar to stage |
| HwBinder JNI in app processes | `bridge/native/binder_runtime.c` `apklayer_register_binder`: the framework's own `register_android_os_{HidlMemory,HidlSupport,HwBinder,HwBlob,HwParcel,HwRemoteBinder,NativeHandle}` from `libandroid_runtime`, inside the same renaming window as `android.os.Binder`; a failure is logged and not fatal | done |
| an instance hwservicemanager | `libapklayer_hwservicemanager` (patches/aosp/system/hwservicemanager 0001, allow-all `aosp/hals/hwservicemanager/AccessControl.cpp`), started by `cli/stack.cpp` | done for instances with no host HALs (below) |
| `hidltool manager`, group `hidl` | `aosp/tools/hidltool.cpp`, `tests/apps/hello-hidl`, `tests/vm/hidl.sh` | native client and app |

## The gate

libhwbinder (`Utils.cpp`) picks the real hwservicemanager only when `/system/bin/hwservicemanager` (or the system_ext one) exists, and then every
libhidl client waits for the property `hwservicemanager.ready`. Without the file, libhidl answers with its `NoHwServiceManager` stand-in, which
patches/aosp/system/libhidl 0001-0003 teach to forward the names in `host_hals.conf` to the host (Halium: `camera`, `media`, `graphics = device`).
So the stack writes an empty `system/bin/hwservicemanager` into the instance's own root (never the shared android-root) and starts the manager
only when `host_hals.conf` lists nothing, and stages its VINTF entry (`aosp/hals/hwservicemanager/manifest.xml`, the tree's `hwservicemanager.xml`:
the manager refuses to run without its own `android.hidl.manager` entry). An instance that has host HALs keeps the stand-in and still has no
local manager.

## What remains

- Instances with host HALs (Halium `graphics`/`camera`/`media = device`): a local manager next to the host forwarding needs `NoHwServiceManager`'s
  non-host branches (get, add, list, registerForNotifications) to go to the local manager instead of "nothing" (libhidl patch 0004). Not started.
- HALs of the app's own: an app cannot register a HIDL service yet (`ServiceManager::add` needs a VINTF entry for the name; the stack declares none).
- x86_64: `libapklayer_hwservicemanager` is built for arm64 only (`m` of `gsi_x86_64`, then `deps/android-tree-files-x86_64.lock`).
- Android ships `android.hidl.*-java` as boot jars; an app that is not a system app gets the same classes here (the framework carries them).
