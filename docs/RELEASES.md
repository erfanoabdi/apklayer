# Binary releases

What a release is made of, where each piece is installed, the `releases.json` format the manager's updater reads, and how to cut a release.
Plan and open work: `docs/PLAN.md` "Next phase: binary releases".

## The pieces

| Piece | Made by | For |
|---|---|---|
| `apklayer_X.Y.Z-1~<codename>1_<arch>.deb` (uploaded with `.` for `~`) | `debian/` through `tools/release/build-deb.sh` | one OS release (noble, trixie) and CPU (arm64; amd64 experimental) |
| `apklayer-runtime-<cpu>-<R>.tar.zst` + `.sha256` | `tools/release/runtime-bundle.sh <prebuilts> <cpu> <out>` | every Linux of that CPU (`<cpu>`: `arm64`, `x86_64`, the prebuilts' names) |
| `SHA256SUMS` | the release job | every file of the release |
| `releases.json` on the `pages` branch | `tools/release/releases-json.py` | the manager's updater (no GitHub API rate limit) |

`tools/release/versions` names the runtime version `R` the package needs (`runtime=`); the deb installs it as `/usr/share/apklayer/versions`
(the CLI's sign that it runs installed). Bump it whenever the prebuilts branch changes; a release that keeps it ships the same bundle again (the manager
downloads it only when the version changes). `converter_abi` and `data_format` are `kConverterAbi` and `kDataFormat` of `cli/version.h`;
`apklayer --version` prints them with the package version.

## The deb

Everything glibc-linked, built from the tag with the prebuilts as build inputs (`tools/release/package.sh`, the steps of `docs/INSTALL.md`):

| Path | What |
|---|---|
| `/usr/bin/apklayer` -> `/usr/lib/apklayer/cli/apklayer`, `/usr/bin/apklayer-manager` | the CLI, the manager |
| `/usr/lib/apklayer/` | a mirror of a meson build dir (`cli/`, `bridge/shell/apklayer-shell`, `runtime/dex2jvm/dex2jvm.jar`), `bionic_translation/{b,cfg.d}` (built at `patches/bionic_translation/REVISION` with `patches/apply`), `native/` (the package's half of the native dir: `bin/` btload, aprop, getprop; the preload, NDK and HAL libraries; `native-runtime.jar` with the JNI shim; `jdk-patch/`; `aosp/host-classes`; `libs/`, the Maven jars an install runs), `apklayer-manager/libslint_cpp.so` |
| `/usr/share/apklayer/` | the CLI's root: `versions`, `tools/{build,sysapps.list,host-setup}`, `runtime/host/jvm.args`, `bridge/native/{apklayer.prop,bionic_translation.cfg,android-root}`, `bridge/portal`, `aosp/hals/**.xml` |
| `/usr/lib/modules-load.d/apklayer.conf`, `apklayer-binder.service` | `binder_linux` at boot; binderfs and the default instance's devices (`tools/host-setup/binder.sh`), enabled and started by the package |

Ubuntu keeps `binder_linux` in `linux-modules-extra-<kernel>`, which only the kernel metapackages pull in: the noble deb depends on
`linux-image-generic | linux-image-generic-hwe-24.04` (`debian/<codename>.substvars`, written by `build-deb.sh`). Other kernels must bring the module.
Debian builds it into its kernel package. Per-codename source patches, if one is ever needed: `debian/patches/<codename>/series` (copied into place by `build-deb.sh`).

## The runtime bundle

The Android half (Apache-2.0 content of the prebuilts branch for one CPU). Files at the top of the tarball:

```
runtime.json        {"version": R, "cpu": "arm64", "main_commit": "<main commit the prebuilts match>", "contents": [...]}
android-libs/       Android's libraries (prebuilts <cpu>/android-libs/lib64)
android-root/       the Android root (prebuilts <cpu>/android-root)
aosp/               Robolectric_all.jar, sim-classes/, android-all/ (the framework, under both names Robolectric asks for)
android-stub.jar    the SDK's android.jar
system-apps/        the providers, DocumentsUI and webview.apk for the first run
```

Install (what apklayer-manager does): unpack into `~/.local/share/apklayer/runtime/R/` (`$XDG_DATA_HOME` is honoured) and point the symlink
`runtime/current` at `R`; by hand: `mkdir -p ~/.local/share/apklayer/runtime/R && tar --zstd -xf apklayer-runtime-<cpu>-R.tar.zst -C ~/.local/share/apklayer/runtime/R && ln -sfn R ~/.local/share/apklayer/runtime/current`.
The installed CLI takes `runtime/current` (else `runtime/R` of its `versions`) as its native dir: on first use, and again after each package or runtime update, it runs
`tools/build runtime` (under a lock) which links the package's half in, puts the JNI shim into the framework jars, writes bionic_translation's
library mapping and instruments the framework once (a few minutes). Without the bundle, commands that start Android code say where it is missing.
Environment variables (`APKLAYER_NATIVE`, `APKLAYER_ANDROID_LIBS`, ...) still override every path; a source tree keeps working as before (no `versions` at its root).

## releases.json

```json
{
  "latest": "0.1.0",
  "releases": [
    {
      "version": "0.1.0",
      "date": "2026-11-05",
      "notes_url": "https://github.com/erfanoabdi/apklayer/releases/tag/v0.1.0",
      "converter_abi": 1,
      "data_format": 1,
      "runtime": {"version": "0.1.0", "assets": {"arm64": {"url": "https://github.com/.../apklayer-runtime-arm64-0.1.0.tar.zst", "sha256": "..."}}},
      "debs": {
        "noble": {"arm64": {"url": ".../apklayer_0.1.0-1.noble1_arm64.deb", "sha256": "..."}, "amd64": {"url": "...", "sha256": "..."}},
        "trixie": {"arm64": {"url": "...", "sha256": "..."}}
      }
    }
  ]
}
```

- `releases` is newest first; `latest` is its first version. A missing OS or CPU means no deb was built for it (amd64 is experimental).
- Keys of `debs` are codenames (`VERSION_CODENAME` of `/etc/os-release`); CPU keys everywhere are Debian architectures (`dpkg --print-architecture`:
  `arm64`, `amd64`), also for the runtime assets (the bundle file of `amd64` is named `x86_64`).
- `converter_abi` and `data_format` are `cli/version.h`'s numbers at the tag (`docs/PLAN.md` item 8).
- The manager downloads `runtime.assets.<arch>` into `runtime/<runtime.version>/` when that version differs from what it has, checking `sha256`.

## Cutting a release

1. On `main`: `tools/release/versions` (`runtime`, bumped if the prebuilts changed), `cli/version.h` and the code are what should ship; set
   `meson.build`'s project version to X.Y.Z for source builds (the deb's `apklayer --version` prints the package version either way).
2. Tag the prebuilts commit the release builds from, then main, and push both tags:
   ```sh
   git tag prebuilts-vX.Y.Z <prebuilts commit>
   git tag vX.Y.Z <main commit>
   git push origin prebuilts-vX.Y.Z vX.Y.Z
   ```
3. The `release` workflow builds the four debs and the bundle, makes the GitHub Release `vX.Y.Z` and commits the entry to `releases.json` on `pages`.
   A dry run without releasing: Actions -> release -> Run workflow (version, prebuilts ref); it prints the `releases.json` entry.
4. Once: the `pages` branch exists on GitHub and Pages serves it (Settings -> Pages -> Deploy from branch `pages`, `/`).

Local build of one deb, the same steps (Ubuntu 24.04 container or machine, in the source tree):

```sh
sudo apt-get build-dep ./
tools/release/build-deb.sh noble 0.1.0 <joined prebuilts checkout> out/
tools/release/runtime-bundle.sh <prebuilts> arm64 out/
```
