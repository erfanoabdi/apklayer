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
                                 │ Unix socket + shm buffers (frames ⇄ input)
                                 ▼
  apklayer-shell (one per session) ──> Wayland windows  (bridge/shell/)
```

## Build (Linux)

```sh
scripts/fetch-deps.sh          # pinned deps (deps/deps.lock) -> ~/.cache/apklayer/deps, local-first
meson setup b && ninja -C b    # b/bridge/shell/apklayer-shell, b/runtime/{dex2jvm,host}/*.jar
```

## Use (Linux)

```sh
bin/apklayer install app.apk        # convert once (cached), menu entry + icon, .apk file handler
bin/apklayer run <package|app.apk>  # starts the session shell if needed; app data persists
bin/apklayer list
bin/apklayer uninstall <package>
```

Apps live in `~/.local/share/apklayer/apps/<package>/` (`base.apk`, `classes.jar`, `icon.png`,
`info.properties`, `data/`). Settings for menu launches go in `~/.config/apklayer/env`
(e.g. `APKLAYER_JAVA`).

## Run (on the Parallels VM, from the Mac)

```sh
./scripts/vm-run.sh            # uibench on the VM's Wayland desktop
./scripts/vm-run.sh my.apk     # any APK: installed via bin/apklayer, then its launcher activity runs
./scripts/check.sh             # headless check on this machine (snapshots in build/check/)
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
| `runtime/host/java/` | host (`apklayer.host`: Main, AppHost, ShellFrameListener, ShellClient), DenyInstrument |
| `runtime/host/jvm.args` | JDK flags Robolectric needs |
| `deps/deps.lock`, `scripts/fetch-deps.sh` | pinned dependencies (sha256), local-first fetch into `~/.cache/apklayer/deps` |
| `meson.build`, `meson_options.txt` | one build for C and Java (`scripts/javac-jar.sh` compiles each jar in one javac call) |
| `bridge/shell/` | `apklayer-shell` (session Wayland shell: windows, zero-copy frames, input) |
| `bin/apklayer` | the CLI: install / run / list / uninstall |
| `scripts/vm-run.sh` | sync → Meson build → `apklayer run` on the VM |
| `tests/uibench/` | target APK |
| `archive/` | superseded approaches (M1 seed, Mac.apk java_mac era, M0 plan), reference only |

## License

apklayer is **AGPL-3.0-or-later** (`LICENSE`, `NOTICE`). Third-party parts keep their own
licenses (dex2jvm AGPL-3.0, AOSP Apache-2.0, Robolectric MIT); see `docs/THIRD_PARTY.md`.
