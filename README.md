# apklayer prebuilts

Binary inputs apklayer needs on a new machine, taken from the working arm64 setup (LineageOS 24 `gsi_arm64 userdebug`, Android 17 / SDK 37 tree with Robolectric `android-all` for SDK 36). Nothing here is built by this repository's CI; `scripts/` of the main repository say how each piece was made.

| Path | What | Used by |
|---|---|---|
| `arm64/android-libs/lib64/` | Android's own arm64 libraries (the closure of `deps/android-libs.list`) and apklayer's HAL modules (`libapklayer_*.so`, incl. keymint/keystore2, patched libbinder with no kernel security context) | `~/android-arm64/lib64` (`APKLAYER_ANDROID_LIBS`) |
| `arm64/android-root/` | the files Android reads at fixed paths: `system/etc` (permissions, sysconfig, compat config, vintf manifest), fonts, `apex/` i18n and tzdata, `props/system_build.prop`, apklayer's vendor manifests | `<native>/android-root` |
| `x86_64/android-libs/lib64/` | the same set built for x86_64 from the user's `gsi_x86_64` tree (438 libraries, all x86-64 ELF; `libs-copied.txt` lists them) with the apklayer modules, patched libbinder/libnativehelper/libmedia_jni/..., keymint and keystore2 | `APKLAYER_ANDROID_LIBS` on an x86_64 machine |
| `x86_64/android-root/` | the same Android root for the x86_64 product (the x86 build's `props/system_build.prop`) | `<native>/android-root` |
| `common/Robolectric_all.jar` | Robolectric with the apklayer-tree framework natives (Java, same for every CPU) | `<native>/aosp/Robolectric_all.jar` |
| `common/android-all-16-robolectric-13921718.jar` | Robolectric's instrumented Android framework | `<native>/aosp/android-all/` (also as `android-all-current-robolectric-r0.jar`) |
| `common/javac-shards/` | `framework.jar0..N` javac outputs of the framework: `scripts/build-native.sh` lists the `@CriticalNative` methods from them | `build-native.sh <native> <shards> ...` |

`system/lib64/extractors/*` are relative symlinks to `../../../../android-libs/<lib>`: they resolve when `<native>/android-libs` links to the libraries directory (`build-native.sh` makes that link).

Files over 50 MB are split into 45 MB parts: run `sh join-parts.sh` once after cloning (it verifies `SHA256SUMS-split`).

Not here, because the new machine builds them from source: bionic_translation (clone + `scripts/build-bionic-translation.sh`), the JNI shim and `btload` (`scripts/build-native.sh`), the host classes (`scripts/build-tree-host.sh`), the CLI (meson).

Licenses: the Android libraries and framework are Apache-2.0 (AOSP); Robolectric is MIT; the `libapklayer_*` modules are AGPL-3.0-or-later like the main repository. `props/system_build.prop` carries the build host's user name (`eng.erfana`, `ro.build.user`) as the tree recorded it.
