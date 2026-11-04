# Installing apklayer

Step by step, from a fresh Linux machine to an Android app in a window. Tested on Ubuntu 24.04 arm64 (a Parallels VM, GNOME Wayland, Mesa virgl), 2026-11-04, from a fresh clone.
x86_64 has its own prebuilts but was never run on a real PC (`docs/X86_64.md`); the steps below are written for arm64.

What you need: arm64 Linux with **Ubuntu 24.04 or newer, or Debian 13**, a **Wayland** desktop session (GNOME, KDE), a GPU with Mesa (GBM, EGL, GLES), PipeWire for audio,
about 3 GB of disk for the sources, prebuilts and build, and `sudo` once per boot (kernel binder devices).
Nothing here needs the LineageOS tree: the Android parts come from the `prebuilts` branch of this repository.

Every command below uses one directory for everything, `$AL`. Set it once per shell:

```sh
export AL=~/apklayer-home
mkdir -p "$AL"
```

## 1. Packages

```sh
sudo apt install build-essential meson ninja-build pkg-config git python3 curl zip unzip binutils \
    openjdk-21-jdk \
    libwayland-dev wayland-protocols libxkbcommon-dev libcairo2-dev libpng-dev \
    libdrm-dev libgbm-dev libegl-dev libgles-dev \
    libpipewire-0.3-dev libgstreamer1.0-dev libgstreamer-plugins-base1.0-dev \
    gstreamer1.0-plugins-good gstreamer1.0-plugins-bad gstreamer1.0-libav \
    libelf-dev libunwind-dev libbsd-dev
```

JDK 21 is the tested one (the host patches `java.base`; other versions are untested).

## 2. Kernel binder

Android's IPC runs on the kernel's binder driver (binderfs). Ubuntu ships it in `linux-modules-extra`, Debian in its standard kernel:

```sh
modinfo binder_linux >/dev/null || sudo apt install linux-modules-extra-$(uname -r)   # Ubuntu
```

`tools/host-setup/binder.sh` (step 3 clones it) loads the module, mounts binderfs at `/dev/binderfs` and creates the devices: `binder`, `hwbinder`,
`vndbinder` for the default instance, and `binder-<id>`... for every instance named on its command line. The devices do not survive a reboot: run it again after each boot.

```sh
sudo sh "$AL/repo/tools/host-setup/binder.sh"            # the default instance
sudo sh "$AL/repo/tools/host-setup/binder.sh" work test  # and instances "work" and "test"
```

## 3. Sources and prebuilts

```sh
git clone https://github.com/erfanoabdi/apklayer "$AL/repo"
git clone --depth 1 --single-branch -b prebuilts https://github.com/erfanoabdi/apklayer "$AL/prebuilts"
sh "$AL/prebuilts/join-parts.sh"     # files over 50 MB are split for GitHub; this joins them and checks their sha256
git clone https://gitlab.com/android_translation_layer/bionic_translation "$AL/bionic_translation"
git -C "$AL/bionic_translation" checkout "$(cat "$AL/repo/patches/bionic_translation/REVISION")"
```

`$AL/prebuilts/MANIFEST` names the commit of `main` the prebuilts match; `README.md` there lists every piece.

## 4. Where things live

The CLI and the build read these paths from the environment; `~/.config/apklayer/env` keeps them for later shells and for apps started from the desktop menu.

```sh
C="${XDG_CONFIG_HOME:-$HOME/.config}/apklayer"
mkdir -p "$C"
cat > "$C/env" <<EOF
export APKLAYER_NATIVE=$AL/native
export APKLAYER_ANDROID_LIBS=$AL/prebuilts/arm64/android-libs/lib64
export APKLAYER_BT=$AL/bionic_translation/b
export APKLAYER_DEPS=$AL/deps
EOF
. "$C/env"
```

`APKLAYER_NATIVE` is the staged runtime (built in step 6), `APKLAYER_ANDROID_LIBS` Android's own libraries (used in place from the prebuilts),
`APKLAYER_BT` bionic_translation's build, `APKLAYER_DEPS` the pinned Java libraries. In a new shell that builds, set `AL` again and source the file (`. ~/.config/apklayer/env`); the CLI reads it by itself.

## 5. Build

```sh
cd "$AL/repo"
APKLAYER_DEPS_LOCAL="$AL/prebuilts/common" tools/fetch-deps.sh "$APKLAYER_DEPS"   # Maven jars (about 400 MB, sha256-checked); android-stub.jar from the prebuilts
tools/build bt "$AL/bionic_translation"                                           # bionic_translation, patched (patches/apply)
meson setup b -Dbionic_translation="$APKLAYER_BT" -Dandroid_libs="$APKLAYER_ANDROID_LIBS"
ninja -C b                                                                        # the CLI, shell, dex2jvm, host HALs, JNI shim, btload
```

## 6. Stage the runtime

The runtime directory gets the Android root and the framework jars from the prebuilts, then apklayer's own parts from the build:

```sh
N="$APKLAYER_NATIVE" P="$AL/prebuilts/common"
mkdir -p "$N/aosp/android-all"
cp -a "$AL/prebuilts/arm64/android-root" "$N/"
cp "$P/Robolectric_all.jar" "$N/aosp/"
cp -r "$P/sim-classes" "$P/robolectric-src" "$N/aosp/"
cp "$P/android-all-16-robolectric-13921718.jar" "$N/aosp/android-all/"
cp "$P/android-all-16-robolectric-13921718.jar" "$N/aosp/android-all/android-all-current-robolectric-r0.jar"
tools/build host "$N"                                     # the host classes
tools/build native "$N" "$P/javac-shards"                 # native runtime, native-runtime.jar, library mapping, the framework instrumented once
```

After a `git pull`: `ninja -C b`, then `tools/build host "$N"` when `runtime/host` changed and `tools/build native "$N"` (no shards needed any more) when
`bridge/` or `runtime/patches` changed. A new prebuilts commit: `git -C "$AL/prebuilts" pull`, `sh "$AL/prebuilts/join-parts.sh"`, and repeat step 6.

## 7. Install and run an app

```sh
bin/apklayer install app.apk        # converts the dex to JVM classes once (a minute for a big app)
bin/apklayer list
bin/apklayer run com.example.app    # starts the instance's Android services on first use, opens a Wayland window; returns when the app ends
bin/apklayer stop                   # stops every app of the instance and its services
```

`bin/apklayer run app.apk` installs and runs in one go. `bin/apklayer screenshot <package> out.png` saves what the app's window shows
(SurfaceFlinger's own picture); `bin/apklayer info`, `uninstall <package>`, `clear-data <package>`, `permissions <package>`, `status` do what they say.
Symlink `bin/apklayer` into your `PATH` to drop the `bin/`.

## 8. System providers and the WebView

Apps expect Android's own content providers (settings, media, contacts, calendar, downloads, documents). They are ordinary APKs in the prebuilts; install them once
(the installed apps are shared by all instances):

```sh
for a in SettingsProvider MediaProvider ContactsProvider CalendarProvider DownloadProvider UserDictionaryProvider ExternalStorageProvider TelephonyProvider DocumentsUI; do
    bin/apklayer install "$AL/prebuilts/arm64/system-apps/$a.apk"
done
```

WebView (apps that show web pages, sign-in screens): the Chromium provider `com.android.webview`. Its sandbox cannot work inside a JVM, so a one-time setup
switches it off (needs sudo once; it writes `/data/local/tmp/webview-command-line`) and installs the provider:

```sh
tools/host-setup/webview.sh "$AL/prebuilts/arm64/system-apps/webview.apk"
```

## 9. The manager

`apklayer-manager` is a window over the CLI: apps (install, open, stop, clear data, uninstall), instances, Android property overrides.
It uses a prebuilt Slint library (no Rust toolchain):

```sh
manager/fetch-slint.sh && meson setup manager/b manager && ninja -C manager/b
manager/b/apklayer-manager
```

## 10. Instances

An instance is a separate Android "device": its own services, properties, app data and storage, side by side with the others. Every command takes
`--instance <id>` (or `APKLAYER_INSTANCE=<id>`); without it the instance is `default`.

```sh
sudo sh tools/host-setup/binder.sh work   # its binder devices (once per boot)
bin/apklayer instance create work
bin/apklayer --instance work run com.example.app
bin/apklayer instances                    # every instance, whether it runs, whether its binder devices exist
bin/apklayer --instance work stop
bin/apklayer instance remove work         # deletes its data
```

## 11. Troubleshooting

- Logs: `~/.local/state/apklayer/<instance>/` (`default` for the default instance): `stack.log` (the services' start), `shell.log` (windows, frames, input),
  one log per Android service (`surfaceflinger.log`, `audioserver.log`...) and per app process. `bin/apklayer status` shows what runs.
- "the Android services did not come up": the binder devices are missing (`ls /dev/binderfs`; run step 2 again after a reboot) or another instance uses the same id.
- "the shell did not listen": no Wayland session (`echo $WAYLAND_DISPLAY`); apklayer does not run on X11 or a text console.
- An app that closes at once: its log names the cause; `bin/apklayer info <package>` shows what was installed. Converted classes are cached: after an
  apklayer update that changed dex2jvm, install the app again.
- Wrong paths: the CLI reads `~/.config/apklayer/env` for every variable not set in the environment; `bin/apklayer env` prints what it uses.

## 12. Uninstall

```sh
bin/apklayer stop                         # and `--instance <id> stop` for every other instance
rm -rf "$AL" ~/.config/apklayer ~/.local/share/apklayer ~/.local/state/apklayer ~/.cache/apklayer
sudo rm -f /data/local/tmp/webview-command-line   # if step 8 ran
```

The binder devices go away at the next reboot. The apt packages from step 1 stay; remove them with `apt` if nothing else needs them.

## What still needs the LineageOS tree

Only rebuilding the Android side: apklayer's modules in `aosp/` (`libapklayer_*`), the tree libraries apklayer patches, the Vulkan loader, and regenerating
the Android root (`tools/stage root|media|sysapps`); see `docs/AOSP-BUILDS.md`. `tools/build gles1` (OpenGL ES 1 for hosts whose Mesa lacks it) needs
gfxstream's sources (`hardware/google/gfxstream`, also on android.googlesource.com).
