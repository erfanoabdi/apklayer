# Binary releases

What a release is made of, where each piece is installed, the `releases.json` format the manager's updater reads, and how to cut a release.
Open work: `docs/PLAN.md` "Click, Flatpak, packaging, releases"; the release plan as decided: `docs/history/PLAN-2026-11-10.md` "Next phase: binary releases".

## The pieces

| Piece | Made by | For |
|---|---|---|
| `apklayer_X.Y.Z-1~<codename>1_<arch>.deb` (uploaded with `.` for `~`) | `debian/` through `tools/release/build-deb.sh` | one OS release (noble, trixie) and CPU (arm64; amd64 experimental) |
| `apklayer.erfanoabdi_X.Y.Z_arm64.click` | `tools/release/click.sh` (the noble arm64 deb's tree and a JDK image) | Ubuntu Touch 24.04 (arm64), section "The click" |
| `apklayer-runtime-<cpu>-<R>.tar.zst` + `.sha256` | `tools/release/runtime-bundle.sh <prebuilts> <cpu> <out>` | every Linux of that CPU (`<cpu>`: `arm64`, `x86_64`, the prebuilts' names) |
| `SHA256SUMS` | the release job | every file of the release |
| `releases.json` on the `pages` branch | `tools/release/releases-json.py` | the manager's updater (no GitHub API rate limit) |

`tools/release/versions` names the runtime version `R` the package needs (`runtime=`); the deb installs it as `/usr/share/apklayer/versions`
(the CLI's sign that it runs installed). Bump it whenever the prebuilts branch changes; a release that keeps it ships the same bundle again (the manager
downloads it only when the version changes). The workflow's first job (`tools/release/check-runtime.py`) enforces this: it fails before any build when the
runtime version was already released from another prebuilts commit (`runtime.prebuilts` in releases.json; older entries take their
`prebuilts-v<version>` tag), naming both commits. `converter_abi` and `data_format` are `kConverterAbi` and `kDataFormat` of `cli/version.h`;
`apklayer --version` prints them with the package version.

## The deb

Everything glibc-linked, built from the tag with the prebuilts as build inputs (`debian/rules`: the Meson build and install of `docs/INSTALL.md` with `-Dandroid_runtime=false`):

| Path | What |
|---|---|
| `/usr/bin/apklayer` -> `/usr/lib/apklayer/cli/apklayer`, `/usr/bin/apklayer-manager` | the CLI, the manager |
| `/usr/lib/apklayer/` | a mirror of a meson build dir (`cli/`, `bridge/shell/apklayer-shell`, `runtime/dex2jvm/dex2jvm.jar`), `bionic_translation/{b,cfg.d}` (the subproject at `subprojects/bionic_translation.wrap`'s revision with `patches/apply`), `native/` (the package's half of the native dir: `bin/` btload, aprop, getprop; the preload, NDK and HAL libraries; `native-runtime.jar` with the JNI shim; `jdk-patch/`; `aosp/host-classes.jar`; `share/bionic_translation/cfg.d` the library mapping; `libs/`, the Maven jars an install runs), `apklayer-manager/libslint_cpp.so` |
| `/usr/share/apklayer/` | the CLI's root: `versions`, `tools/{build,sysapps.list,host-setup}`, `runtime/host/jvm.args`, `bridge/native/{apklayer.prop,bionic_translation.cfg,android-root}`, `bridge/portal`, `aosp/hals/**.xml` |
| `apklayer-binder.service` | kernel binder only (`apklayer binder kernel`): binderfs and the instances' devices at boot (`tools/host-setup/binder.sh`); shipped disabled (`sudo systemctl enable --now apklayer-binder`): the userspace binder, the default since 0.4.9, needs no root step |

Ubuntu keeps `binder_linux` (only the kernel binder option needs it) in `linux-modules-extra-<kernel>`, which only the kernel metapackages pull in: the noble deb suggests
`linux-modules-extra-generic | linux-image-generic | linux-image-generic-hwe-24.04` (`debian/<codename>.substvars`, written by `build-deb.sh`). Other kernels must bring the module.
Debian builds it into its kernel package. Per-codename source patches, if one is ever needed: `debian/patches/<codename>/series` (copied into place by `build-deb.sh`).

## The click

Ubuntu Touch 24.04 (arm64) installs apklayer as a click (docs/history/PLAN-2026-11-10.md "Plan: Ubuntu Touch click package", docs/HALIUM.md "Ubuntu Touch: the click"):
no root, nothing on the read-only rootfs, and it survives OTA updates. `tools/release/click.sh <version> <prebuilts> <out> [<deb>]` (noble arm64, the
deb's Build-Depends and `click`; the release job passes the noble arm64 deb it built):

| Piece | What |
|---|---|
| `usr/...` | the noble arm64 deb unpacked as it is (`dpkg-deb -x`), without its root-side files (units, udev rule, the sandbox's AppArmor profile) |
| `usr/lib/apklayer/native/jdk/` | the runtime's JDK image (`jdk-image.sh` at build time: every module of the build's JDK 21, java.base with the jdk-patch built in, the CDS archive; ~160 MB unpacked). The CLI takes it before any system Java (cli/env.cpp `findJava`; `APKLAYER_NATIVE_JAVA` still wins) and runs it as the image it is (no `--patch-module`, class data sharing on); `setup-runtime.sh` makes no per-user image from it and puts the JNI shim into the framework jars with its `jar` when the phone has no `zip` |
| `apklayer.desktop`, `manifest.json`, `apklayer.apparmor` | the manager as the click's app (`Exec=usr/bin/apklayer-manager`), app id `apklayer.erfanoabdi_apklayer`; framework `ubuntu-touch-24.04-1.x`, AppArmor template `unconfined` (policy version 2404.1: aa-clickhook refuses another for this framework, and Lomiri's launch then fails with "profile does not exist") |

The click lives in `/opt/click.ubuntu.com/apklayer.erfanoabdi/<version>/` (the user data partition), which an update replaces and removes at once:
everything that outlives a process names it through the click's own `current` link (`cli/layout.cpp` selfPath: the CLI's root, the launchers' Exec,
the always-on unit, `-Dapklayer.cli`, the runtime's links into the package half, which setup-runtime.sh now makes from the unresolved path).
A running always-on hub keeps its old, deleted files until its next start; launches after an update run the new version.
The Android runtime bundle comes from `apklayer setup` (the manager's first run) into `~/.local/share/apklayer`, as with the deb. The manager's own
update check reads `debs` in releases.json and does not know the click: the OpenStore (or a newer click) updates it.

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
`<share>/tools/setup-runtime.sh` (installed from `tools/release/setup-runtime.sh`; run under a lock) which links the package's half in (with its library mapping), puts the JNI shim into the framework jars
and instruments the framework once (a few minutes). Without the bundle, commands that start Android code say where it is missing.
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
      "runtime": {"version": "0.1.0", "prebuilts": "<prebuilts commit>", "assets": {"arm64": {"url": "https://github.com/.../apklayer-runtime-arm64-0.1.0.tar.zst", "sha256": "...", "size": 520000000, "unpacked_size": 1640000000}}},
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
- `converter_abi` and `data_format` are `cli/version.h`'s numbers at the tag (`docs/history/PLAN-2026-11-10.md` "Next phase: binary releases" item 8).
- `runtime.prebuilts` is the prebuilts commit the bundle was built from (what `PREBUILTS_REF` resolved to; the build jobs check out that commit).
- The manager downloads `runtime.assets.<arch>` into `runtime/<runtime.version>/` when that version differs from what it has, checking `sha256`.
- `size` and `unpacked_size` (bytes, since 0.4.10) let setup check the free space before it downloads; the bundle's zstd frame also records its
  unpacked size (runtime-bundle.sh: `--stream-size`), checked again before unpacking (a bundle without one is counted by decompressing it).

## Cutting a release

1. On `main`: `tools/release/versions` (`runtime`, bumped if the prebuilts changed), `cli/version.h` and the code are what should ship; set
   `meson.build`'s project version to X.Y.Z for source builds (the deb's `apklayer --version` prints the package version either way).
2. Tag the prebuilts commit the release builds from, then main, and push both tags:
   ```sh
   git tag prebuilts-vX.Y.Z <prebuilts commit>
   git tag vX.Y.Z <main commit>
   git push origin prebuilts-vX.Y.Z vX.Y.Z
   ```
3. The `release` workflow first checks `runtime=` against releases.json (a runtime version released from other prebuilts fails: bump it), then builds the four debs and the bundle, makes the GitHub Release `vX.Y.Z` and commits the entry to `releases.json` on `pages`.
   A dry run without releasing: Actions -> release -> Run workflow (version, prebuilts ref); it prints the `releases.json` entry.
4. Once: the `pages` branch exists on GitHub and Pages serves it (Settings -> Pages -> Deploy from branch `pages`, `/`).

Local build of one deb, the same steps (Ubuntu 24.04 container or machine, in the source tree):

```sh
sudo apt-get build-dep ./
tools/release/build-deb.sh noble 0.1.0 <joined prebuilts checkout> out/
tools/release/runtime-bundle.sh <prebuilts> arm64 out/
```

## First run: `apklayer setup`

One command does the per-user first run and the runtime updates; apklayer-manager runs it and shows its lines (`manager/src/setup.cpp`), the deb's postinst
downloads nothing. The code that looks releases.json up, downloads with a SHA256 check and unpacks the bundle is `common/` (fetch, releases, json, proc),
compiled into both the CLI and the manager.

```
apklayer [--instance ID] setup [--check] [--update] [--bundle <file|file://|URL>] [--microg | --gapps] [--fdroid]
apklayer setup --move-data <dir>
```

Output, tab separated: `step <id> <state> <detail>` (ids: binder, java, runtime, providers, system-apps, microg, gapps, fdroid; states: todo, running, done,
failed, skipped), `progress <0..1|-1>`, and with `--check` (which changes nothing) `latest <version> <runtime version> <date>`. Idempotent: the runtime
is fetched when none is installed (`--update`: when the release has a newer one), the system apps the runtime changed since the last setup (each APK's sha256 in `~/.local/share/apklayer/system-apps.sha256`; replaced with their data kept; `setup-done` marks the first setup).
Space: the bundle downloads into `runtime/.downloads` (the runtime's file system, one place to check), and before a download or an unpack
setup compares the file system's free space (statvfs) with the bundle plus its unpacked size and a tenth (common/fetch.cpp `runtimeRoom`). It
fails early (`Not enough space for the Android runtime: it needs X, Y free in <dir>`, with `apklayer setup --move-data <dir>`), or removes the
runtimes other than the current one first when only that makes room (an update would remove them after switching anyway; it says so); the
current one is never removed. `apklayer setup --move-data <dir>` moves `~/.local/share/apklayer` to `<dir>` and leaves a symlink;
the first setup does it by itself on a small home with a writable `/userdata/apklayer` (docs/HALIUM.md, Sailfish). Checks: manager/tests/updater_test.cpp
(fake statvfs), tests/vm/setup-space.sh `<cli>` (a 100 MB loop-mounted data home, root).
Offline: `--bundle apklayer-runtime-<cpu>-<R>.tar.zst` (a `.sha256` beside it is checked) or `APKLAYER_RUNTIME_BUNDLE`; `APKLAYER_RELEASES_URL` names another releases.json.
On a Halium device the step `providers` seeds a new instance with graphics and camera = device (docs/HALIUM.md).
Nothing in it runs as root (no pkexec, no sudo): binder is only checked (the userspace binder, the default, needs nothing; an instance on the kernel binder needs its nodes, which the
optional `apklayer-binder.service` makes at boot; a failed step names the command), java checks for Java 21 (`apklayer config set java <path>` names another), and the WebView's switch file is the instance's own. A runtime directory
missing `android-root/system`, `android-libs` or `aosp/android-all` (a bundle made from a prebuilts checkout of links, before runtime-bundle.sh copied
through them) counts as not installed: setup unpacks it again, and such a bundle is refused. `apklayer --version` also prints `defaultInstance`
(`ut` on a Halium device), which the manager uses when no instance is named.
