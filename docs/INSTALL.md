# Installing apklayer

Released versions come as a deb (Ubuntu 24.04, Debian 13; arm64): `sudo apt install ./apklayer_*.deb` from the latest GitHub Release, then open apklayer-manager (`docs/RELEASES.md`), or as a Flatpak for any distribution (section "Flatpak" at the end), or on Ubuntu Touch 24.04 as a click (`docs/HALIUM.md` "Ubuntu Touch: the click"). This page builds from source.

Step by step, from a fresh Linux machine to an Android app in a window. Tested on Ubuntu 24.04 arm64 (a Parallels VM, GNOME Wayland, Mesa virgl), 2026-11-04, from a fresh clone.
x86_64 has its own prebuilts but was never run on a real PC (`docs/X86_64.md`); the steps below are written for arm64.

What you need: arm64 Linux with **Ubuntu 24.04 or newer** (tested; Debian 13 should take the same packages, untested), a **Wayland** desktop session (GNOME, KDE), a GPU with Mesa (GBM, EGL, GLES), PipeWire for audio,
about 5 GB of disk for the sources, prebuilts, build and install. No root step: binder is apklayer's own userspace one (docs/BINDER.md); the kernel's
binder driver is an option (step 2).
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
    libpipewire-0.3-dev libpulse-dev libglib2.0-dev libgstreamer1.0-dev libgstreamer-plugins-base1.0-dev \
    gstreamer1.0-plugins-good gstreamer1.0-plugins-bad gstreamer1.0-libav \
    libelf-dev libunwind-dev libbsd-dev libinput-dev   # libinput: the manager's Slint library
```

JDK 21 is the tested one (the host patches `java.base`; other versions are untested).

## 2. Kernel binder (optional)

Android's IPC (binder) runs on apklayer's userspace binder by default (docs/BINDER.md): no kernel module, no devices, nothing as root. Skip this
step unless you want the kernel's binder driver (binderfs) for an instance: `bin/apklayer [--instance <id>] binder kernel` (back with
`binder userspace`; `APKLAYER_BINDER=kernel|userspace` overrides it for one command; the manager's Hardware page has the same switch). Ubuntu ships
the driver in `linux-modules-extra`, Debian in its standard kernel:

```sh
modinfo binder_linux >/dev/null || sudo apt install linux-modules-extra-$(uname -r)   # Ubuntu
```

`tools/host-setup/binder.sh` (step 3 clones it) loads the module, mounts binderfs at `/dev/binderfs` and creates the devices: `binder`, `hwbinder`,
`vndbinder` for the default instance, and `binder-<id>`... for every instance named on its command line. The devices do not survive a reboot: run it again after each boot
(an installed package: `sudo systemctl enable --now apklayer-binder` does that at every boot).

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
reconfigure resets the subproject to its revision and applies the series again (`meson subprojects update --reset bionic_translation` by hand does the same). OpenGL ES 1 for hosts whose Mesa lacks it (libapklayer-gles1.so) is built from the prebuilts' `common/gfxstream`;
`-Dgfxstream=<gfxstream source>` (the tree's `hardware/google/gfxstream`) picks another copy.

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
bin/apklayer instance create work
bin/apklayer --instance work run com.example.app
bin/apklayer instances                    # every instance, whether it runs, whether its binder is ready (kernel binder: its devices exist)
bin/apklayer --instance work stop
bin/apklayer instance remove work         # deletes its data
```

### Notifications while apps are closed

Messengers (Telegram, WhatsApp, Signal with Play services...) get their messages through Google's cloud messaging, which on apklayer is microG
(`apklayer setup --microg`: Services and Companion) or Google's own Play services (`apklayer setup --gapps`). Either keeps one connection to Google for every app, so the instance has to run while the apps
are closed: installing Play services (microG's or Google's) turns the instance's always-on mode on (`apklayer instance autostart <id> on|off`, the switch on the manager's
Instances page): a systemd user unit starts its services at login and keeps them up, and a message wakes the closed app, whose notification appears on
the desktop. In microG's settings (the microG Settings app in the instance) "Google device registration" and "Cloud Messaging" are on by default
(apklayer's `/system/etc/microg.xml`); a Google account is not needed for push, an app that signs in with Google needs one.

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

### The per-app sandbox (opt-in)

With the sandbox every app process runs as its own uid (Android's app uid, taken from your `/etc/subuid` range) in a user namespace per
instance, with Android's groups. It sees its own data and what Android's `/data` shows an app, never the other apps' data, the system's state
(keystore2's database) or your home; a file reaches it through the file picker (only the one chosen), and `apklayer home-access <package> on`
gives an app your home. Location (GeoClue), Bluetooth (BlueZ) and the hub's desktop notifications go through `apklayerd`, which runs as you. So
does what the app's uid may not open but you may (its file broker): GPU device nodes that only your seat's ACL or a group opens (`/dev/dri/*`,
NVIDIA's `/dev/nvidia*`, `/dev/kfd`), the app's own log, and with home access the files, folders, renames and deletions in your home that their
permissions close to others.

- The deb sets the host up at install: the AppArmor profile that lets apklayer make user namespaces (Ubuntu 23.10+ restricts them), GPU render
  nodes usable by app uids (`/usr/lib/udev/rules.d/70-apklayer-render.rules`), `uidmap`. A source tree: `sudo sh tools/host-setup/sandbox.sh` once.
- Your user needs 65536 subordinate ids: `grep $USER /etc/subuid /etc/subgid`; without a line,
  `sudo usermod --add-subuids 100000-165535 --add-subgids 100000-165535 $USER`.
- Opt-in: `APKLAYER_SANDBOX=1` for your session in `~/.config/environment.d/60-apklayer.conf` (0.4.5 turned it on by default where the host was
  set up; 0.4.6 made it opt-in again: on NVIDIA's driver and on deb installs it broke apps; both are addressed since 2026-11-08, NVIDIA not yet
  confirmed on real hardware: Troubleshooting below). `systemctl --user enable apklayerd` starts the
  daemon at login (else it starts on demand). `apklayer stop` before switching.
- An app's data becomes its uid's at its first sandboxed start and yours again when it runs without the sandbox.
- A source tree on a machine where you cannot use sudo yourself: the owner runs
  `sudo sh tools/host-setup/sandbox.sh <tree>/b/cli/apklayer` once (the profile names that exact binary).

## 9. Troubleshooting

- Logs: `~/.local/state/apklayer/<instance>/` (`default` for the default instance): `stack.log` (the services' start), `shell.log` (windows, frames, input),
  one log per Android service (`surfaceflinger.log`, `audioserver.log`...) and per app process. `bin/apklayer status` shows what runs.
- "the Android services did not come up": `binderd.log` in the instance's logs (the userspace binder); with the kernel binder its devices may be missing (`ls /dev/binderfs`; run step 2 again after a reboot); or another instance uses the same id.
- "the shell did not listen": no Wayland session (`echo $WAYLAND_DISPLAY`); apklayer does not run on X11 or a text console.
- An app that closes at once: its log names the cause; `bin/apklayer info <package>` shows what was installed. Converted classes are cached: after an
  apklayer update that changed dex2jvm, install the app again.
- A host without a GPU (a cloud VM, a container host): `sudo modprobe vkms` (the kernel's virtual KMS device; `echo vkms | sudo tee /etc/modules-load.d/vkms.conf` keeps it) and be in the `video` group. `apklayer gpus` then lists vkms by its card node (`/dev/dri/card<n>`, it has no render node) and an instance with no other GPU runs on it: Mesa renders on the CPU (kms_swrast/llvmpipe), buffers are dumb buffers. Slow (a game at a few frames per second at best), for tests. vgem (render node `renderD128` on such hosts) allocates nothing and is never the default (`apklayer-gfx: ... allocates no buffers`).
- Apps draw with software (llvmpipe) only with the sandbox on (`APKLAYER_SANDBOX=1`), on NVIDIA or any GPU: send these to the developers.
  1. `ls -l /dev/dri /dev/nvidia*` and `getfacl /dev/nvidia* /dev/dri/renderD*`; `grep -r NVreg_DeviceFile /etc/modprobe.d /lib/modprobe.d`
     (Debian makes NVIDIA's nodes 0660 group video; app uids reach them only through apklayerd's broker).
  2. `grep broker $XDG_RUNTIME_DIR/apklayer/apklayerd.log`: every GPU node an app asked for, opened or refused (and why).
  3. The app's renderer: `APKLAYER_LOGCAT=1 apklayer run <package> 2>&1 | grep -m1 "GL version"`, once with `APKLAYER_SANDBOX=1` and once with 0.
     `llvmpipe` only sandboxed means NVIDIA's EGL did not start in the app's view and glvnd fell back to Mesa.
  4. `ls /dev/nvidia-modeset /dev/nvidia-uvm`: a node that does not exist yet is made by the setuid `nvidia-modprobe`, which the sandbox cannot run
     (no new privileges); opening any GL app outside the sandbox once makes it.
  5. `env | grep -E "__EGL|VK_|LIBGL|GBM"` and `ls ~/.local/share/glvnd ~/.local/share/vulkan 2>/dev/null`: driver files in your home are bound into
     the app's view from these; a path elsewhere that is hidden (/tmp) is not.
- Wrong paths: the CLI reads `~/.config/apklayer/env` for every variable not set in the environment; `bin/apklayer env` prints what it uses.

## 10. Uninstall

Installed from the deb (apt): first, as each user who ran apklayer, remove what it keeps in that user's home (every Android app and its
data, the runtime bundles, logs, caches, desktop entries, the Apklayer submenu, systemd user units; apt never touches home folders), then
the package once:

```sh
apklayer remove-all          # lists what it would delete
apklayer remove-all --yes    # deletes it (the apps' data cannot be brought back)
sudo apt purge apklayer apklayer-dev
```

From a source tree: `bin/apklayer remove-all --yes`, then delete the tree (`rm -rf "$AL"`) and, if an older version's step 6 wrote it,
`sudo rm -f /data/local/tmp/webview-command-line`. Kernel binder devices (if you made any) go away at the next reboot. The apt packages from step 1 stay; remove
them with `apt` if nothing else needs them.

## What still needs the LineageOS tree

Only rebuilding the Android side: apklayer's modules in `aosp/` (`libapklayer_*`), the tree libraries apklayer patches, the Vulkan loader, and regenerating
the Android root (`tools/stage root|media|sysapps`); see `docs/AOSP-BUILDS.md`. `tools/stage gfxstream <prebuilts>` copies gfxstream's sources
(`hardware/google/gfxstream`, OpenGL ES 1 for hosts whose Mesa lacks it) into the prebuilts' `common/gfxstream`.

## Installed package: first run

After `sudo apt install ./apklayer_*.deb` run `apklayer setup` (or open apklayer-manager, which runs it): it downloads the Android runtime bundle for your CPU
from the release (`--bundle <file>` for an offline copy), installs the system apps and sets the instance up; `apklayer setup --check` shows what is missing.
microG (Services and Companion, for apps that need Google's services) and the F-Droid client are optional: `apklayer setup --microg --fdroid`, the
first-run screen's checkboxes, or at any time the "Add-ons" section at the end of the Apps page's list, which shows whether each is
installed and offers Install, Update (nothing is downloaded beyond the repository index when the installed version is current) and Remove.
In place of microG, `apklayer setup --gapps` (the "Google Play" tile) installs Google's own apps from the LiteGapps "lite" zip for the runtime's SDK
(~250 MB from SourceForge, sha256-pinned in cli/gapps.cpp; `APKLAYER_GAPPS_URL`/`APKLAYER_GAPPS_SHA256` for another copy, `file://` works): the Services
Framework, Play services, Play Store and the contacts/calendar sync adapters as system apps, with Google's privileged-permission and sysconfig XMLs
(kept in `~/.local/share/apklayer/google-apps/etc`, copied into the runtime's android-root again after a runtime update). microG is removed first;
the two cannot be installed together.
Space: the runtime takes ~1.6 GB unpacked per version and an update unpacks the new one beside the current one (the one before stays; older
ones go), with the bundle (~0.5 GB) downloaded beside them. Setup checks the free space of `~/.local/share/apklayer` before it downloads or unpacks
and fails early with what is needed, what is free and where; when only removing the runtimes other than the current one makes room it removes
them first and says so. A home on a small file system: `apklayer setup --move-data <dir>` moves `~/.local/share/apklayer` (runtimes, apps,
instances; no instance may run) to `<dir>` on a bigger one and leaves a symlink in its place (docs/HALIUM.md for phones).
Setup needs no root: binder is the userspace one. The kernel's binder is an option (`apklayer binder kernel`, or the manager's Hardware page); its
devices need root once per boot: `sudo systemctl enable --now apklayer-binder` (the package ships the unit disabled).
Ubuntu Touch 24.04 (Halium phones): the click, not the deb (docs/HALIUM.md "Ubuntu Touch: the click": no root, survives OTA updates, its own
Java); "Choose file…" and Share to apklayer go through Lomiri Content Hub (docs/HALIUM.md "Choosing and sharing files on Ubuntu Touch"; Share to
apklayer is the deb's only so far).

## Flatpak

**New (2026-11-08): run end to end on aarch64 and x86_64; see docs/history/PLAN-2026-11-10.md "Flatpak".** The release's `apklayer-<version>-<cpu>.flatpak` (aarch64, x86_64; app id `io.github.erfanoabdi.Apklayer`, runtime `org.freedesktop.Platform` 26.08 from Flathub):

```sh
flatpak install --user apklayer-*-aarch64.flatpak
flatpak run io.github.erfanoabdi.Apklayer                               # the manager (also "Apklayer" in the app menu)
alias apklayer='flatpak run --command=apklayer io.github.erfanoabdi.Apklayer'   # the CLI
```

**No host step.** A Flatpak cannot load kernel modules or mount binderfs, and it sees only the GPU's devices (`--device=dri`); apklayer uses the
userspace binder (docs/BINDER.md), the default everywhere: `apklayer setup`'s binder step passes and nothing runs as root. The kernel's binder is
an option as with the deb (`apklayer binder kernel`): with `flatpak override --user --device=all io.github.erfanoabdi.Apklayer` and binderfs
mounted on the host (`sudo sh "$(flatpak info --show-location io.github.erfanoabdi.Apklayer)/files/share/apklayer/tools/host-setup/binder.sh"`);
`APKLAYER_BINDER=userspace|kernel` (`flatpak run --env=...`) forces either.
The Android runtime bundle is downloaded per user by `apklayer setup`, as with the deb (not a Flatpak extension: the bundle has its own version and
release cycle, `tools/release/versions`, and updates without a new Flatpak).

Permissions (`finish-args` in `flatpak/io.github.erfanoabdi.Apklayer.yml`) and why:

| Permission | Why |
|---|---|
| `--device=dri` | the GPU's render nodes. Input comes through Wayland and cameras through PipeWire; USB devices (`apklayer usb`) and the force-feedback vibrator's `/dev/input` need `flatpak override --user --device=all io.github.erfanoabdi.Apklayer` (Flatpak's `--device=usb`/`input` need Flatpak 1.16, Ubuntu 24.04 has 1.14) |
| `--socket=wayland`, `--share=ipc` | apps' windows and the manager (Wayland only, no X11) |
| `--socket=pulseaudio`, `--filesystem=xdg-run/pipewire-0` | the audio backends (`apklayer providers`) and PipeWire cameras |
| `--share=network` | apps' internet, `apklayer setup`'s downloads |
| `--allow=bluetooth`, `--system-talk-name=org.bluez`, `--system-talk-name=org.freedesktop.GeoClue2`, `--talk-name=org.freedesktop.Notifications` | Bluetooth, location, notifications |

Different from the deb: data, logs and the runtime live under `~/.var/app/io.github.erfanoabdi.Apklayer/`; the per-app sandbox (`APKLAYER_SANDBOX=1`)
cannot run (Flatpak's seccomp filter refuses nested user namespaces and `newuidmap` is not setuid inside); an app's full home access needs
`flatpak override --user --filesystem=home io.github.erfanoabdi.Apklayer`; `apklayer instance autostart` (systemd user units) is not available; an
installed app's launcher comes from the dynamic launcher portal (`org.freedesktop.portal.DynamicLauncher`, bridge/portal/launcher.py): the desktop
asks once per app ("Create App"), `launcher hide` and uninstall remove it, an app update keeps the launcher as it was, and there is no Apklayer
submenu (the deb's `apklayer-*` entries are untouched). On Ubuntu 24.04 the portal refuses every launcher ("Dynamic launcher icon failed
validation": its icon check runs in bwrap, which the AppArmor user-namespace restriction breaks, LP#2069526); there
`flatpak override --user --filesystem=xdg-data/applications:create io.github.erfanoabdi.Apklayer` makes apklayer write the launchers itself at the
host's paths, as the deb does (`io.github.erfanoabdi.Apklayer.<package>.desktop`; then `apklayer stop`: a running apklayerd keeps the permissions it started with). `.apk` files open with the manager's own entry (`MimeType` there, `apklayer run <file>`);
every command runs in apklayerd (started on demand, as with the per-app sandbox),
because each `flatpak run` is a pid namespace of its own; the GLES version probe (`eglinfo`, mesa-utils) is not in the Platform, so a host driver under GLES 3.2 gets no 3.2
claim. NVIDIA: Flatpak needs the `org.freedesktop.Platform.GL.nvidia-<version>` extension matching the host driver (`flatpak update` installs it
when Flathub has that version); other GPUs use Mesa from `org.freedesktop.Platform.GL.default`.

Build it: `sh flatpak/build.sh <prebuilts checkout> <out dir> [version]` (needs flatpak and flatpak-builder; it joins split prebuilts and installs the
SDK and openjdk21 extension from Flathub). The build has no network: the bionic_translation wrap, `deps.lock`'s artifacts and Slint are pinned
sources in the manifest; after a change to `deps/deps.lock` run `tools/gen/flatpak-sources.py` (it writes `flatpak/deps-sources.json`), after a new
bionic_translation revision or Slint version change the manifest beside the wrap or `manager/fetch-slint.sh`.
