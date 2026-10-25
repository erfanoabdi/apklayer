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
  android-all 16 (unmodified AOSP) + Robolectric shadows + native HWUI/Skia
  driver: lifecycle, back stack, windows, input        (runtime/host/java)
                                 │ /dev/shm/apklayer-fb (pixels ⇄ input)
                                 ▼
  apklayer-presentd ──> Wayland window                  (bridge/shell/)
```

## Run (on the Parallels VM, from the Mac)

```sh
APKLAYER_GFX=NATIVE ./scripts/vm-run.sh            # uibench
APKLAYER_GFX=NATIVE ./scripts/vm-run.sh my.apk     # another APK (driver is still uibench-specific: see docs/PLAN.md P1)
```

Left-click taps; drag scrolls; Esc/Backspace is Back; closing the window exits.
VM access: `ssh parallels@10.211.55.4` (password `1234`). Setup details are in memory.md.

## Layout

| Path | What |
|---|---|
| `goal.md` / `memory.md` | mission, rules, milestones / facts and decisions (read first) |
| `docs/ARCHITECTURE.md` | how the pipeline works, framebuffer layout |
| `docs/PLAN.md` | **plan of record**: decisions D1–D23, phases P1–P7, exit tests, next tasks |
| `docs/PATCHES.md` / `docs/THIRD_PARTY.md` | patch log (none) / licenses |
| `runtime/dex2jvm/` | DEX → JVM converter (from Mac.apk, AGPL) |
| `runtime/host/java/` | driver (RunUiBench, HostWindow, WlWindow, DenyInstrument) |
| `deps/deps.lock`, `scripts/fetch-deps.sh` | pinned dependencies (sha256), local-first fetch into `~/.cache/apklayer/deps` |
| `bridge/` | meson: `apklayer-presentd`, `libapklayer-wl.so` (Wayland + input) |
| `scripts/vm-run.sh` | sync → build → convert → run on the VM |
| `tests/uibench/` | target APK |
| `archive/` | superseded approaches (M1 seed, Mac.apk java_mac era, M0 plan), reference only |

## License

apklayer is **AGPL-3.0-or-later** (`LICENSE`, `NOTICE`). Third-party parts keep their own
licenses (dex2jvm AGPL-3.0, AOSP Apache-2.0, Robolectric MIT); see `docs/THIRD_PARTY.md`.
