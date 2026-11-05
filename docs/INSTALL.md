# Installing apklayer

Released versions come as a deb (Ubuntu 24.04, Debian 13; arm64): `sudo apt install ./apklayer_*.deb` from the latest GitHub Release, then open apklayer-manager (`docs/RELEASES.md`). This page builds from source.

Step by step, from a fresh Linux machine to an Android app in a window. Tested on Ubuntu 24.04 arm64 (a Parallels VM, GNOME Wayland, Mesa virgl), 2026-11-04, from a fresh clone.
x86_64 has its own prebuilts but was never run on a real PC (`docs/X86_64.md`); the steps below are written for arm64.

What you need: arm64 Linux with **Ubuntu 24.04 or newer** (tested; Debian 13 should take the same packages, untested), a **Wayland** desktop session (GNOME, KDE), a GPU with Mesa (GBM, EGL, GLES), PipeWire for audio,
about 5 GB of disk for the sources, prebuilts, build and install, and `sudo` once per boot (kernel binder devices).
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
    libdrm-dev libgbm-dev libegl-dev libgles-dev mesa-utils \
    libpipewire-0.3-dev libgstreamer1.0-dev libgstreamer-plugins-base1.0-dev \
    gstreamer1.0-plugins-good gstreamer1.0-plugins-bad gstreamer1.0-libav \
    libelf-dev libunwind-dev libbsd-dev libinput-dev   # libinput: the manager's Slint library
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
```

`$AL/prebuilts/MANIFEST` names the commit of `main` the prebuilts match; `README.md` there lists every piece.

## 4. Build and install

One Meson build makes and stages everything: bionic_translation (fetched at its pinned revision, `subprojects/bionic_translation.wrap`, and
patched by `patches/apply`), the pinned Java libraries (`deps/deps.lock`, sha256-checked), the CLI, shell, dex2jvm, host HALs, JNI shim and btload,
the host classes and Robolectric overlays, and the framework jar with the shim, instrumented once. `meson install` puts the runtime into the prefix
(`<prefix>/lib/apklayer`, its native dir `native/`; `<prefix>/share/apklayer`), the layout the deb installs into `/usr`:

```sh
cd "$AL/repo"
meson setup b --prefix "$AL/install" -Dprebuilts="$AL/prebuilts"
ninja -C b
meson install -C b --skip-subprojects     # --skip-subprojects: bionic_translation's own install would add a system-wide copy
```

`bin/apklayer` (the tree's CLI, `b/cli/apklayer`) and `$AL/install/bin/apklayer` both use that install; no environment variables are needed.
After a pull or an edit: `ninja -C b && meson install -C b --skip-subprojects --only-changed` (ninja rebuilds exactly what changed:
a host Java file rebuilds the host classes and what depends on them, an edit of the JNI shim the jars that carry it). A new prebuilts commit:
pull it, `sh "$AL/prebuilts/join-parts.sh"`, then the same two commands. An edited bionic_translation patch:
`meson subprojects update --reset bionic_translation` first. OpenGL ES 1 for hosts whose Mesa lacks it: add `-Dgfxstream=<gfxstream source>`
(the tree's `hardware/google/gfxstream`).

## 5. Install and run an app

```sh
bin/apklayer install app.apk        # converts the dex to JVM classes once (a minute for a big app)
bin/apklayer list
bin/apklayer run com.example.app    # starts the instance's Android services on first use, opens a Wayland window; returns when the app ends
bin/apklayer stop                   # stops every app of the instance and its services
```

`bin/apklayer run app.apk` installs and runs in one go. `bin/apklayer screenshot <package> out.png` saves what the app's window shows
(SurfaceFlinger's own picture); `bin/apklayer info`, `uninstall <package>`, `clear-data <package>`, `permissions <package>`, `status` do what they say.
Symlink `bin/apklayer` into your `PATH` to drop the `bin/`.

## 6. System providers and the WebView

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

## 7. The manager

`apklayer-manager` is a window over the CLI: apps (install, open, stop, clear data, uninstall), instances, Android property overrides.
It uses a prebuilt Slint library (no Rust toolchain):

```sh
manager/fetch-slint.sh && meson setup manager/b manager && ninja -C manager/b
manager/b/apklayer-manager
```

## 8. Instances

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

### USB devices

Apps reach a USB device the way they do on Android (UsbManager: the device list, the attach intent, a permission question, `openDevice`); apklayer
then opens the device's node `/dev/bus/usb/BBB/DDD` read-write as your user. By default those nodes are root's: give your user the device with a udev
rule (`uaccess`: the logged-in desktop user gets it while their session is active), e.g. for a USB serial adapter:

```sh
echo 'SUBSYSTEM=="usb", ENV{DEVTYPE}=="usb_device", ATTR{idVendor}=="0403", ATTR{idProduct}=="6001", TAG+="uaccess"' | sudo tee /etc/udev/rules.d/70-my-usb-device.rules
sudo udevadm control --reload && sudo udevadm trigger    # or unplug and plug in again
```

An interface a Linux driver holds (ftdi_sio, cdc_acm, usb-storage...) cannot be claimed while it does, unless the app forces it (`claimInterface(..., true)`,
which detaches the driver until the device is plugged in again); blacklisting the driver (`/etc/modprobe.d`) keeps it off for good.

## 9. Troubleshooting

- Logs: `~/.local/state/apklayer/<instance>/` (`default` for the default instance): `stack.log` (the services' start), `shell.log` (windows, frames, input),
  one log per Android service (`surfaceflinger.log`, `audioserver.log`...) and per app process. `bin/apklayer status` shows what runs.
- "the Android services did not come up": the binder devices are missing (`ls /dev/binderfs`; run step 2 again after a reboot) or another instance uses the same id.
- "the shell did not listen": no Wayland session (`echo $WAYLAND_DISPLAY`); apklayer does not run on X11 or a text console.
- An app that closes at once: its log names the cause; `bin/apklayer info <package>` shows what was installed. Converted classes are cached: after an
  apklayer update that changed dex2jvm, install the app again.
- Wrong paths: the CLI reads `~/.config/apklayer/env` for every variable not set in the environment; `bin/apklayer env` prints what it uses.

## 10. Uninstall

```sh
bin/apklayer stop                         # and `--instance <id> stop` for every other instance
rm -rf "$AL" ~/.config/apklayer ~/.local/share/apklayer ~/.local/state/apklayer ~/.cache/apklayer
sudo rm -f /data/local/tmp/webview-command-line   # if step 6 ran
```

The binder devices go away at the next reboot. The apt packages from step 1 stay; remove them with `apt` if nothing else needs them.

## What still needs the LineageOS tree

Only rebuilding the Android side: apklayer's modules in `aosp/` (`libapklayer_*`), the tree libraries apklayer patches, the Vulkan loader, and regenerating
the Android root (`tools/stage root|media|sysapps`); see `docs/AOSP-BUILDS.md`. `-Dgfxstream` (OpenGL ES 1 for hosts whose Mesa lacks it) needs
gfxstream's sources (`hardware/google/gfxstream`, also on android.googlesource.com).

## Installed package: first run

After `sudo apt install ./apklayer_*.deb` run `apklayer setup` (or open apklayer-manager, which runs it): it downloads the Android runtime bundle for your CPU
from the release (`--bundle <file>` for an offline copy), installs the system apps and sets the instance up; `apklayer setup --check` shows what is missing.
Ubuntu Touch (Halium phones): docs/HALIUM.md "The apt install on the phone".
