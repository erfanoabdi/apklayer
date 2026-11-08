# Installing apklayer

Released versions come as a deb (Ubuntu 24.04, Debian 13; arm64): `sudo apt install ./apklayer_*.deb` from the latest GitHub Release, then open apklayer-manager (`docs/RELEASES.md`), or as a Flatpak for any distribution (section "Flatpak" at the end). This page builds from source.

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
pull it, `sh "$AL/prebuilts/join-parts.sh"`, then the same two commands. An edited bionic_translation patch: the
reconfigure resets the subproject to its revision and applies the series again (`meson subprojects update --reset bionic_translation` by hand does the same). OpenGL ES 1 for hosts whose Mesa lacks it: add `-Dgfxstream=<gfxstream source>`
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

WebView (apps that show web pages, sign-in screens): the Chromium provider `com.android.webview`. Its sandbox cannot work inside a JVM and is switched off
(the CLI writes Chromium's switch file into the instance's own `/data` at each launch; no root). Install the provider as a system app:

```sh
tools/host-setup/webview.sh "$AL/prebuilts/arm64/system-apps/webview.apk"
```

## 7. The manager

`apklayer-manager` is a window over the CLI: apps (install, open, stop, clear data, uninstall; the add-ons microG and F-Droid), instances, Android
property overrides.
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

### The per-app sandbox (on where the host is set up)

With the sandbox every app process runs as its own uid (Android's app uid, taken from your `/etc/subuid` range) in a user namespace per
instance, with Android's groups. It sees its own data and what Android's `/data` shows an app, never the other apps' data, the system's state
(keystore2's database) or your home; a file reaches it through the file picker (only the one chosen), and `apklayer home-access <package> on`
gives an app your home (as far as the files' permissions for others allow: the app's uid is not yours). Location (GeoClue) and Bluetooth (BlueZ)
go through `apklayerd`, which runs as you.

- The deb sets the host up at install: the AppArmor profile that lets apklayer make user namespaces (Ubuntu 23.10+ restricts them), GPU render
  nodes usable by app uids (`/usr/lib/udev/rules.d/70-apklayer-render.rules`), `uidmap`. A source tree: `sudo sh tools/host-setup/sandbox.sh` once.
- Your user needs 65536 subordinate ids: `grep $USER /etc/subuid /etc/subgid`; without a line,
  `sudo usermod --add-subuids 100000-165535 --add-subgids 100000-165535 $USER`.
- It is on by default wherever the host is set up for it (the three points above); otherwise apps run without it and `apklayer run` says why
  (`apklayer setup` shows it as its `sandbox` step). Inside Flatpak and on Halium phones it stays off for now. `APKLAYER_SANDBOX=0` turns it off,
  `APKLAYER_SANDBOX=1` insists on it (a host that is not set up then fails with the reason): for your session in
  `~/.config/environment.d/60-apklayer.conf`. `systemctl --user enable apklayerd` starts the daemon at login (else it starts on demand; a source
  tree: copy `tools/host-setup/apklayerd.service` to `~/.config/systemd/user/` first). `apklayer stop` before switching.
- An app's data becomes its uid's at its first sandboxed start and yours again when it runs without the sandbox.

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
sudo rm -f /data/local/tmp/webview-command-line   # only if an older version's step 6 wrote it (no longer read)
```

The binder devices go away at the next reboot. The apt packages from step 1 stay; remove them with `apt` if nothing else needs them.

## What still needs the LineageOS tree

Only rebuilding the Android side: apklayer's modules in `aosp/` (`libapklayer_*`), the tree libraries apklayer patches, the Vulkan loader, and regenerating
the Android root (`tools/stage root|media|sysapps`); see `docs/AOSP-BUILDS.md`. `-Dgfxstream` (OpenGL ES 1 for hosts whose Mesa lacks it) needs
gfxstream's sources (`hardware/google/gfxstream`, also on android.googlesource.com).

## Installed package: first run

After `sudo apt install ./apklayer_*.deb` run `apklayer setup` (or open apklayer-manager, which runs it): it downloads the Android runtime bundle for your CPU
from the release (`--bundle <file>` for an offline copy), installs the system apps and sets the instance up; `apklayer setup --check` shows what is missing.
microG (Services and Companion, for apps that need Google's services) and the F-Droid client are optional: `apklayer setup --microg --fdroid`, the
first-run screen's checkboxes, or at any time the "Add-ons" section at the end of the Apps page's list, which shows whether each is
installed and offers Install, Update (nothing is downloaded beyond the repository index when the installed version is current) and Remove.
Setup needs no root: what does (binderfs and the instances' binder devices) the package does at install and at every boot (`apklayer-binder.service`).
Ubuntu Touch (Halium phones): docs/HALIUM.md "The apt install on the phone"; "Choose file…" and Share to apklayer go through Lomiri Content Hub
(docs/HALIUM.md "Choosing and sharing files on Ubuntu Touch").

## Flatpak

**Not yet built or run end to end (2026-11-08): see docs/PLAN.md "Flatpak".** The release's `apklayer-<version>-<cpu>.flatpak` (aarch64, x86_64; app id `io.github.erfanoabdi.Apklayer`, runtime `org.freedesktop.Platform` 26.08 from Flathub):

```sh
flatpak install --user apklayer-*-aarch64.flatpak
flatpak run io.github.erfanoabdi.Apklayer                               # the manager (also "Apklayer" in the app menu)
alias apklayer='flatpak run --command=apklayer io.github.erfanoabdi.Apklayer'   # the CLI
```

**Binder is a host step.** A Flatpak cannot load kernel modules or mount file systems, so binderfs must exist on the host before the app starts.
Run the app's own script on the host as root, once per boot (it loads binder_linux, mounts `/dev/binderfs`, makes the default instance's devices and those named):

```sh
sudo sh "$(flatpak info --show-location io.github.erfanoabdi.Apklayer)/files/share/apklayer/tools/host-setup/binder.sh" [instance...]
```

`apklayer setup` (the manager's first-run screen) and the manager's binder button print this command with the path filled in when `/dev/binderfs` is
missing. For every boot, install the deb's unit (`debian/apklayer.apklayer-binder.service`) with `ExecStart` pointing at a copy of that script.
The Android runtime bundle is downloaded per user by `apklayer setup`, as with the deb (not a Flatpak extension: the bundle has its own version and
release cycle, `tools/release/versions`, and updates without a new Flatpak).

Permissions (`finish-args` in `flatpak/io.github.erfanoabdi.Apklayer.yml`) and why:

| Permission | Why |
|---|---|
| `--device=all` | the host's `/dev/binderfs` (Flatpak has no grant for single binder nodes), GPU render nodes, cameras, input and USB devices; `--device=dri` would cover only the GPU |
| `--socket=wayland`, `--share=ipc` | apps' windows and the manager (Wayland only, no X11) |
| `--socket=pulseaudio`, `--filesystem=xdg-run/pipewire-0` | the audio backends (`apklayer providers`) and PipeWire cameras |
| `--share=network` | apps' internet, `apklayer setup`'s downloads |
| `--allow=bluetooth`, `--system-talk-name=org.bluez`, `--system-talk-name=org.freedesktop.GeoClue2`, `--talk-name=org.freedesktop.Notifications` | Bluetooth, location, notifications |
| `--filesystem=xdg-data/applications:create`, `xdg-data/desktop-directories:create`, `xdg-config/menus:create` | launchers of installed apps and the Apklayer submenu, written at the host's paths (as Bottles does; the dynamic launcher portal asks the user once per app and is a later option) |

Different from the deb: data, logs and the runtime live under `~/.var/app/io.github.erfanoabdi.Apklayer/`; the per-app sandbox (`APKLAYER_SANDBOX=1`)
cannot run (Flatpak's seccomp filter refuses nested user namespaces and `newuidmap` is not setuid inside); an app's full home access needs
`flatpak override --user --filesystem=home io.github.erfanoabdi.Apklayer`; `apklayer instance autostart` (systemd user units) and the `.apk` file
association are not available.

Build it: `sh flatpak/build.sh <prebuilts checkout> <out dir> [version]` (needs flatpak and flatpak-builder; it joins split prebuilts and installs the
SDK and openjdk21 extension from Flathub). The build fetches what the deb's build fetches (the bionic_translation wrap, `deps.lock`, Slint) with
network on; Flathub itself would need those as pinned sources.
