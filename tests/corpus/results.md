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
| ASTRO_File_Manager_v8.10.1_Softgozar.com | 2 none | FAIL | java.lang.NoSuchMethodError: 'com.google.common.collect.ImmutableMap com.google.common.collect.ImmutableMap$Builder.buildOrThrow()' |
| Barcode Scanner_v4.7.8_apkpure.com | 1 none | FAIL | java.lang.IllegalArgumentException: Don't know what to do with dataSource org.robolectric.shadows.util.DataSource@4a703c8c - either add an exception with addExc |
| Device Info HW_v4.26.5_apkpure.com | 1 none | FAIL | Exception in thread "Thread-5" java.lang.NullPointerException: Cannot read the array length because "<local3>" is null |
| DisplayLink Presenter_4.2.0.6 (43a748f131d)_APKPure | 1 x86,armeabi-v7a,x86_64,arm64-v8a | FAIL | blank frame |
| MotCamera2_signed | 1 arm64-v8a | FAIL | java.lang.RuntimeException: java.lang.NullPointerException: Cannot read field "mShouldHideWhenLocked" because "<local3>" is null |
| cfa-2.5.9-foss-armeabi-v7a-release | 1 armeabi-v7a | FAIL | Exception in thread "SDK 36 Main Thread" java.lang.NullPointerException: Cannot invoke "android.os.IBinder.transact(int, android.os.Parcel, android.os.Parcel, i |
| com.google.zxing.client.android_4.7.8-108_minAPI19(nodpi)_apkmirror.com | 1 none | FAIL | java.lang.IllegalArgumentException: Don't know what to do with dataSource org.robolectric.shadows.util.DataSource@8e5466d8 - either add an exception with addExc |
| com.wireguard.android-1.0.20260102 | 2 x86,armeabi-v7a,x86_64,arm64-v8a | FAIL | blank frame |
| com.wireguard.android_492 | 2 x86,armeabi-v7a,x86_64,arm64-v8a | FAIL | blank frame |
| cpu_z | 1 x86,armeabi-v7a,x86_64,arm64-v8a | FAIL | java.lang.UnsatisfiedLinkError: no cpuid in java.library.path: /Users/erfanabdi/Library/Java/Extensions:/Library/Java/Extensions:/Network/Library/Java/Extension |
| de.blinkt.openvpn_196 | 2 x86,armeabi-v7a,x86_64,arm64-v8a | FAIL | blank frame |
| drminfo | 1 none | FAIL | java.lang.NullPointerException: it.hdrCapabilities must not be null |
| gpstest | 1 none | FAIL | java.lang.IllegalStateException: Default FirebaseApp is not initialized in this process null. Make sure to call FirebaseApp.initializeApp(Context) first. |
| microg | 2 x86,armeabi-v7a,x86_64,arm64-v8a | FAIL | activity not started |
| net.typeblog.shelter_20 | 1 none | FAIL | activity not started |
| nextcloud-release-30270000-signed | 4 arm64-v8a | FAIL | java.lang.ArrayIndexOutOfBoundsException: Index 0 out of bounds for length 0 |
| opencamera | 1 none | FAIL | blank frame |
| org.schabi.newpipe_955 | 1 none | FAIL | java.lang.NullPointerException: Cannot load from object array because "<local1>" is null |
| org.telegram.messenger_22275 | 3 arm64-v8a | FAIL | java.lang.UnsatisfiedLinkError: 'void org.telegram.tgnet.ConnectionsManager.native_setJava(boolean)' |
| sensorsmultitoo | 2 none | FAIL | java.lang.RuntimeException: MultiDex installation failed (Field pathList not found in class org.robolectric.simulator.SimulatorClassLoader). |
| shadowsocksr-release | 1 armeabi-v7a,x86 | FAIL | java.lang.IllegalAccessError: Update to static final field scala.Predef$.MODULE$ attempted from a different method (<init>) than the initializer method <clinit> |
| spic-v1.4.0 | 2 none | FAIL | java.lang.NoSuchMethodError: 'com.google.common.collect.ImmutableMap com.google.common.collect.ImmutableMap$Builder.buildOrThrow()' |
| uibench | 1 none | PASS | com.android.test.uibench.MainActivity (tap diff 0%) |
| whatsapp | 3 arm64-v8a | FAIL | java.lang.NoClassDefFoundError: java/lang/RunnableEBaseShape3S0100000_I0_3 |
