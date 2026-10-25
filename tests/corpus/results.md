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
| Barcode Scanner_v4.7.8_apkpure.com | 1 none | PASS | com.google.zxing.client.android.CaptureActivity (tap diff 66%) |
| com.amaze.filemanager_126 | 1 x86,armeabi-v7a,x86_64,arm64-v8a | FAIL | java.lang.IllegalAccessError: failed to access class org.bouncycastle.util.Strings$1 from class ch.qos.logback.core.util.Loader (org.bouncycastle.util.Strings$1 |
| com.beemdevelopment.aegis_82 | 2 x86,armeabi-v7a,x86_64,arm64-v8a | FAIL | java.lang.IllegalAccessError: class com.bumptech.glide.load.engine.bitmap_recycle.LruArrayPool$KeyPool cannot access its abstract superclass org.bouncycastle.as |
| com.ichi2.anki_22503300 | 3 x86,armeabi-v7a,x86_64,arm64-v8a | FAIL | no frame (rc=0, likely hang/timeout) |
| de.dennisguse.opentracks_6766 | 5 none | FAIL | blank frame |
| me.hackerchick.catima_1006 | 2 x86,armeabi-v7a,x86_64,arm64-v8a | FAIL | activity not started |
| me.zhanghai.android.files_40 | 2 x86,armeabi-v7a,x86_64,arm64-v8a | FAIL | java.lang.ExceptionInInitializerError |
| net.gsantner.markor_163 | 2 none | PASS | net.gsantner.markor.activity.MainActivity (tap diff 0%) |
| net.osmand.plus_541003 | 5 arm64-v8a | FAIL | java.lang.UnsupportedOperationException: Tried to obtain display from a Context not associated with one. Only visual Contexts (such as Activity or one created w |
| org.fdroid.fdroid_1023052 | 2 x86,armeabi-v7a,x86_64,arm64-v8a | FAIL | blank frame |
| ws.xsoh.etar_57 | 4 none | PASS | com.android.calendar.AllInOneActivity (tap diff 26%) |
| org.fossify.notes_13 | 1 x86,armeabi-v7a,x86_64,arm64-v8a | FAIL | java.lang.RuntimeException: java.lang.NullPointerException: null cannot be cast to non-null type org.fossify.notes.adapters.NotesPagerAdapter |
| org.fossify.clock_10 | 1 x86,armeabi-v7a,x86_64,arm64-v8a | PASS | org.fossify.clock.activities.SplashActivity (tap diff 0%) |
| org.fossify.contacts_13 | 2 x86,armeabi-v7a,x86_64,arm64-v8a | FAIL | blank frame |
| org.fossify.messages_25 | 1 x86,armeabi-v7a,x86_64,arm64-v8a | FAIL | no frame (rc=0, likely hang/timeout) |
| org.fossify.calendar_23 | 1 x86,armeabi-v7a,x86_64,arm64-v8a | FAIL | java.lang.IllegalStateException: WorkManager is not initialized properly.  You have explicitly disabled WorkManagerInitializer in your manifest, have not manual |
| org.fossify.gallery_29 | 1 x86,armeabi-v7a,x86_64,arm64-v8a | FAIL | blank frame |
| org.fossify.filemanager_14 | 1 x86,armeabi-v7a,x86_64,arm64-v8a | FAIL | blank frame |
