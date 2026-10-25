# Corpus results

APKs come from the user's local `~/TestApps` (no downloads). Headless run on the Mac:
`scripts/check.sh <apk> "" 5500 6000` (launch, 6 s, snapshot). Pass = the launcher activity
starts and draws a non-blank frame with no uncaught exception.

## 2026-10-25 (SDK 36, Robolectric 4.17, P1 host)

| App | APK | dex / native | Result | Cause / next step |
|---|---|---|---|---|
| UiBench | tests/uibench/uibench.apk | 1 / none | ✅ pass (navigation, dialogs, back) | `InflatingEmojiListActivity` is declared but not in the dex (the app's own defect; the host logs it) |
| Vibrations Test | Vibrations Test_v1.1.0 | 1 / none | ✅ pass (full UI rendered; also on the VM via `apklayer run`, window "Vibrations-Test") | — |
| Open Camera | opencamera.apk | 1 / none | ✅ pass (UI + welcome dialog; no camera preview) | camera backend comes in P5 |
| Device Info HW | Device Info HW_v4.26.5 | 1 / none | ❌ main screen draws, then the app exits | runs `getprop` via `Runtime.exec`: needs a host `getprop` stand-in (system properties) |
| GPSTest | gpstest.apk | 1 / none | ❌ crash at start | Firebase not initialized: Firebase's `ContentProvider` init (`FirebaseInitProvider`) does not run; manifest providers must be installed before `Application.onCreate` |
| DRM Info | drminfo.apk | 1 / none | ❌ crash at start | `Display.getHdrCapabilities()` returns null in Robolectric |
| Barcode Scanner | Barcode Scanner_v4.7.8 | 1 / none | ❌ crash at start | `MediaPlayer` data source unknown to Robolectric's media shadow (P5 media backend) |
| NewPipe | org.schabi.newpipe_955 | 1 / none | ❌ crash at start | NPE inside the app's startup; not yet analysed |
| CPU-Z | cpu_z.apk | 1 / libcpuid.so (4 ABIs) | ❌ installs, then `UnsatisfiedLinkError: no cpuid` (VM) | loading APK native libraries is PLAN P4 |

Not yet tried: multi-dex apps (ASTRO, Sensors Multitool, Spic) and apps with native libraries (P4).
