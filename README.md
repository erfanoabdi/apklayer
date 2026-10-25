# apklayer

Run Android APKs natively on Linux. No emulator, no VM, no Linux-inside-Linux.

## Status (2026-10-25)

**Method pivot in effect** (see `goal.md`): apklayer follows the **Robolectric
method** — unmodified AOSP compiled for the JVM (`android-all-instrumented`) plus
MIT-licensed shadows — so Android version updates stay replicable. Mac.apk's
hand-patched framework snapshot is **reference only**.

Current pipeline (all running on the Parallels Ubuntu ARM64 VM):

```
uibench.apk ──dex2jvm──> app classes (stamped @DoNotInstrument)
              +
android-all-instrumented 8.1.0 (unmodified AOSP on JVM) + Robolectric shadows
              │  tests/rob: RunUiBench driver (real Activity lifecycle)
              ▼
real View tree -> Canvas -> Bitmap -> int[] -> libapklayer-wl.so -> Wayland window
```

Gate reached: sandbox boots, binary manifest + resources load, MainActivity is
created. Next gate: AppCompat theme resolution in `postCreate`.

## Layout

| Path | What |
|---|---|
| `goal.md` / `memory.md` | mission, rules, running log (read these first) |
| `runtime/dex2jvm/` | DEX -> JVM class converter (from Mac.apk, AGPL, in active use) |
| `runtime/java_mac/` | Mac.apk framework snapshot — **reference only, not the runtime base** |
| `runtime/prebuilt/framework-res.apk` | AOSP platform resources (for the retired java_mac path) |
| `bridge/` | meson build: `libapklayer-wl.so` (Wayland presenter), `libapklayer-jni.so` (retired java_mac bridge), `apklayer-window` PoC |
| `tests/rob/` | Robolectric-method driver (RunUiBench, HostWindow, WlWindow, DenyInstrument) |
| `tests/uibench/` | target APK |
| `archive/seed-m1/` | archived hand-grown M1 seed (do not extend) |
| `scripts/apklayer-rob.sh` | one-shot: sync -> build -> run the driver on the VM |
| `docs/` | findings, patch log, third-party inventory |

## Testing

- VM: `ssh parallels@10.211.55.4` (password `1234`), GNOME Wayland, OpenJDK 21 ARM64.
- VM layout: `~/rob/libs` (Robolectric deps), `~/rob/android-all/` (framework jars),
  `~/rob/android-stub.jar` (API-35 stubs), `~/apklayer-apps/uibench` (unpacked APK),
  `~/apklayer-m1/bridge/b/` (libapklayer-wl.so).
- Run: `APKLY_VM_PASS=1234 ./scripts/apklayer-rob.sh /tmp/uibench-classes.jar
  com.android.test.uibench.MainActivity '$HOME/apklayer-apps/uibench'`

## License note

While `runtime/dex2jvm` (from Mac.apk) is present the tree carries **AGPL-3.0**
obligations; AOSP-derived parts are Apache-2.0; Robolectric parts MIT.
See `docs/THIRD_PARTY.md`.
