# apklayer prebuilts

The binary inputs apklayer needs on a new machine, so it can be built and run without the LineageOS tree. Taken from the working arm64 setup
(the UTM test VM's staged runtime; LineageOS 24 `gsi_arm64 userdebug`, Android 17 tree, Robolectric `android-all` for SDK 36). `MANIFEST` names the
commit of `main` they match. How to use them: `docs/INSTALL.md` in `main`.

After cloning: `sh join-parts.sh` (files over 50 MB are split into 45 MB parts for GitHub's 100 MB file limit; it joins them and checks `SHA256SUMS-split`).

| Path | What | Where it goes (docs/INSTALL.md) |
|---|---|---|
| `arm64/android-libs/lib64/` | Android's own arm64 libraries: exactly the 439 files of `main`'s `deps/android-tree-files.lock` (sha256 per file there), the closure of `deps/android-libs.list`, the patched tree libraries and apklayer's modules (`libapklayer_*.so`, keymint/keystore2, the Vulkan loader copy) | used in place: `APKLAYER_ANDROID_LIBS` |
| `arm64/android-root/` | the files Android reads at fixed paths (`tools/stage root`): `system/etc` (permissions, sysconfig, compat config, vintf, CA certificates `security/cacerts`), fonts, hyphenation data, `apex/` i18n and tzdata, `props/system_build.prop`, the audio policy tables, apklayer's vendor files | `<native>/android-root` |
| `arm64/system-apps/` | the tree's providers and system apps as apklayer installs them (`tools/stage sysapps`): SettingsProvider, MediaProvider, ContactsProvider, CalendarProvider, DownloadProvider, UserDictionaryProvider, ExternalStorageProvider, TelephonyProvider, DocumentsUI; `webview.apk` is the Chromium WebView provider `com.android.webview` 156.0.8078.47 (AOSP `external/chromium-webview`) | `apklayer install`, `tools/host-setup/webview.sh` |
| `common/Robolectric_all.jar` | the tree's Robolectric (`out/host/linux-x86/framework/Robolectric_all.jar`) | `<native>/aosp/` |
| `common/android-all-16-robolectric-13921718.jar` | the tree's framework as Robolectric's `android-all` (Java; `tools/build native` puts the JNI shim into it) | `<native>/aosp/android-all/`, also as `android-all-current-robolectric-r0.jar` |
| `common/sim-classes/` | the tree's Robolectric simulator (`external/robolectric/simulator`) compiled | `<native>/aosp/sim-classes` |
| `common/robolectric-src/` | the tree's `external/robolectric` sources that `patches/robolectric` changes, unpatched, at `patches/robolectric/REVISION` (`tools/build overlay` patches a copy) | `<native>/aosp/robolectric-src` |
| `common/javac-shards/` | `framework.jar0..60`, the framework's javac outputs: `tools/build native` lists the `@CriticalNative` methods from them (unchanged since 2026-10-29: the tree's current shards are identical) | `tools/build native <native> <shards>` (first build only) |
| `common/android-stub.jar` | Android SDK platform 36.1 `android.jar` (the `sdk:` entry of `main`'s `deps/deps.lock`) | `APKLAYER_DEPS_LOCAL` for `tools/fetch-deps.sh` |
| `x86_64/android-libs/lib64/` | Android's own x86_64 libraries from the tree's `gsi_x86_64 userdebug` build (2026-11-05): the 509 files of `main`'s `deps/android-tree-files-x86_64.lock` (sha256 per file there; the same set as arm64: the closure of `deps/android-libs.list` and of every library of the arm64 lock, apklayer's modules incl. the hardware providers (camera, cameraserver, GNSS, sensors, sensorservice, vibrator) and tools, the Vulkan loader copy, the patched tree libraries, and the native bridge below) | `APKLAYER_ANDROID_LIBS` on x86_64 |
| `x86_64/android-root/` | as `arm64/android-root/`, from the x86_64 product (`tools/stage x86_64`) | `<native>/android-root` on x86_64 |
| `x86_64/android-libs/lib64/libberberis_*.so`, `x86_64/android-root/system/{lib64,bin}/arm64/`, `system/etc/{ld.config,cpuinfo}.arm64.txt` | the native bridge for arm64-only apps (2026-11-05): Digitalis's Berberis arm64 guest (`frameworks/libs/binary_translation` at Digitalis android17 abefbe2b plus `patches/aosp/frameworks/libs/binary_translation`), its 22 proxies and the arm64 guest libraries, from the tree's `gsi_x86_64` build with `APKLAYER_NATIVE_BRIDGE=arm64`; libberberis_arm64.so sha256 aa543ebe77dcb2c1... | install picks `nativeRoute=bridge-arm64` when it is there (docs/X86_64.md "arm64-only apps") |
| `x86_64/system-apps/webview.apk` | the Chromium WebView provider for x86_64 (`external/chromium-webview/prebuilt/x86_64`, sha256 in `SHA256SUMS-split`); no other system apps for x86_64 yet | `tools/host-setup/webview.sh` |

`android-root/system/lib64/extractors/*` are relative symlinks to `../../../../android-libs/<lib>`: they resolve once `<native>/android-libs` links to the
libraries directory (`tools/build native` makes that link).

Not here, because a new machine builds them from source: bionic_translation (public, `tools/build bt`), the CLI, shell, JNI shim, `btload` and host HALs (meson),
the host classes (`tools/build host`), `native-runtime.jar` (`tools/build native`), the pinned Maven jars (`tools/fetch-deps.sh`), the Slint manager library
(`manager/fetch-slint.sh`). Not here at all: gfxstream's sources for `tools/build gles1` (only for hosts without OpenGL ES 1).

Licenses: the Android libraries, framework, system apps and SDK stub are Apache-2.0 (AOSP); the WebView provider is Chromium's (BSD-3-Clause and the
licenses its `about:credits` lists); Robolectric is MIT; the `libapklayer_*` modules are AGPL-3.0-or-later like the main repository.
`props/system_build.prop` carries the build host's user name (`eng.erfana`, `ro.build.user`) as the tree recorded it.
