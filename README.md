# apklayer

Run Android APKs natively on Linux. No emulator, no VM, no Linux-inside-Linux.

## Status (2026-10-25)

**uibench runs with its full UI and is interactive in a Wayland window.** It uses the real
AOSP View system, HWUI/Skia rendering, activities, dialogs, and mouse/keyboard input.

apklayer follows the **Robolectric method**: the unmodified AOSP framework compiled for the
JVM (`android-all`) plus MIT-licensed shadows, so Android version updates stay replicable.
There are zero hand patches to AOSP or Robolectric.

```
app.apk ──dex2jvm──> JVM classes ┐
        └─ manifest/resources ───┤ (AOSP PackageParser)
                                 ▼
  android-all (unmodified AOSP) + Robolectric shadows + native HWUI/Skia
  driver: lifecycle, back stack, windows, input        (tests/rob)
                                 │ /dev/shm/apklayer-fb (pixels ⇄ input)
                                 ▼
  apklayer-presentd ──> Wayland window                  (bridge/)
```

## Run (on the Parallels VM, from the Mac)

```sh
APKLAYER_GFX=NATIVE ./scripts/apklayer-rob.sh            # uibench
APKLAYER_GFX=NATIVE ./scripts/apklayer-rob.sh my.apk     # another APK (driver is still uibench-specific: see roadmap M6)
```

Left-click taps; drag scrolls; Esc/Backspace is Back; closing the window exits.
VM access: `ssh parallels@10.211.55.4` (password `1234`). Setup details are in memory.md.

## Layout

| Path | What |
|---|---|
| `goal.md` / `memory.md` | mission, rules, milestones / facts and decisions (read first) |
| `docs/ARCHITECTURE.md` | how the pipeline works, framebuffer layout |
| `docs/ROADMAP.md` | path to a full APK opener (M6+) |
| `docs/PATCHES.md` / `docs/THIRD_PARTY.md` | patch log (none) / licenses |
| `runtime/dex2jvm/` | DEX → JVM converter (from Mac.apk, AGPL) |
| `tests/rob/` | driver (RunUiBench, HostWindow, WlWindow, DenyInstrument) |
| `tests/stubs/` | outer-classpath `android.os.Build` |
| `bridge/` | meson: `apklayer-presentd`, `libapklayer-wl.so` (Wayland + input) |
| `scripts/apklayer-rob.sh` | sync → build → convert → run on the VM |
| `tests/uibench/` | target APK |
| `archive/` | superseded approaches (M1 seed, Mac.apk java_mac era, M0 plan), reference only |

## License note

While `runtime/dex2jvm` (from Mac.apk) is present, the tree carries **AGPL-3.0** obligations.
AOSP-derived parts are Apache-2.0 and Robolectric parts are MIT. See `docs/THIRD_PARTY.md`.
