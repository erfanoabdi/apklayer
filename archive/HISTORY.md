# apklayer — history log (archived 2026-10-25)

Verbatim decisions log from memory.md covering M0 → the Robolectric pivot and the first
Wayland frames. Superseded approaches live alongside this file (see README.md here).

## Decisions log
- 2026-10-25: **Approved by user**: compiling the framework jar from los24 sources with
  javac is acceptable (it is not an AOSP/Soong build). Adopt Mac.apk's prebuilt strategy:
  JVM = prebuilt OpenJDK, Skia = prebuilt (Skiko ships linux-arm64), GPU = native Mesa
  (no ANGLE/MoltenVK needed on Linux), vendored small prebuilts where Mac.apk does
  (BouncyCastle etc.).
- 2026-10-25: Use git; commit at every step of work.
- 2026-10-25 (M0 recon): Mac.apk runs **HotSpot OpenJDK 21**, not ART. Framework = synced AOSP
  java compiled via javac --release 21 -> android-framework.jar (java_base_patch via
  --patch-module). Apps: pure-Java **dex2jvm** (runtime/java/com/macapk/dex, 36 files, AGPL)
  converts classes.dex -> JVM jar at install time, cached by FRAMEWORK_ID. Details:
  docs/M0-FINDINGS.md. Open decision for user: port com.macapk.dex (AGPL) vs alternative
  dex->class converter.
- 2026-10-25: Parallels VM prepared for M1: `openjdk-21-jdk-headless` (21.0.12 arm64) installed
  via apt (needed `apt-get update` first). HotSpot prints a harmless SVE warning on this VM —
  pass `-XX:UseSVE=0`. Mesa EGL/GL libs already present.
- 2026-10-25 (M1 smoke PASSED): genuine AOSP classes from los24 (android.util.Base64 unmodified;
  Log/SystemProperties patched thin; plus annotation/internal deps) compile with plain `javac
  --release 21` and run on stock OpenJDK 21 on the bare Ubuntu ARM64 VM:
  `apklayer M1 smoke: base64=aGVsbG8gYXBrbGF5ZXI= roundtrip_ok=true jvm=21.0.12.1`.
  Seed lives in runtime/java (24 files), smoke in tests/smoke. Thin patches marked
  "apklayer patch" inline. Notable stubs so far: minimal android/os/Build.java, minimal
  com.android.internal.util.Preconditions, DeadObjectException (upstream is aidl-generated),
  Log.java edits (no RuntimeInit/FastPrintWriter/LineBreakBufferedWriter), SystemProperties
  Binder no-ops. Compile/run on VM: javac -nowarn -d out $(find java tests/smoke -name "*.java");
  java -XX:UseSVE=0 -cp out apklayer.smoke.Smoke  (sources synced to ~/apklayer-m1 on VM).
- 2026-10-25 (M1b PASSED): the genuine AOSP message loop (Looper, Handler, HandlerThread,
  Message, LegacyMessageQueue/MessageQueue — copied from los24) compiles with javac 21 and
  runs on stock OpenJDK on the bare Ubuntu ARM64 VM: post + sendMessage(what=42,obj) +
  postDelayed all dispatched -> `looper_ok=true`. Key insight: LegacyMessageQueue is pure
  Java except the native epoll bridge, which we replaced with JVM wait/notify
  (mPollLock/mWoke; semantics preserved). Also stripped natives in SystemProperties (JVM
  -D properties) and Trace (disabled). Thin stubs added: Binder, Process, SystemClock,
  Bundle, Messenger, ThreadLocalWorkSource, StrictMode, VMRuntime, Flags, Perfetto*
  (all marked "apklayer patch"). Seed now ~50 files in runtime/java.
  Next: M2 — first window via Wayland (UIBench APK at /Users/erfanabdi/TestApps/uibench.apk
  on the Mac). Needs: dex2jvm decision (port com.macapk.dex AGPL vs alternative), ActivityThread
  bootstrap w/o system_server, and the surface bridge (HWUI->EGL->wl_surface or
  Bitmap->SHM path). Userspace binder = allowed substitute for the kernel driver (user OK).
- 2026-10-25 (M2a — first window): bridge/ (meson, C, libwayland + xdg-shell) opens a real
  xdg_toplevel on the VM's GNOME Wayland session and presents 2 SHM frames:
  `apklayer-window: mapped=1 frames=2`, exit 0. Build/run on VM: sources synced to
  ~/apklayer-m1/bridge; `meson setup -Dbuildtype=release b && ninja -C b`; run with
  `XDG_RUNTIME_DIR=/run/user/1000 WAYLAND_DISPLAY=wayland-0 ./b/apklayer-window`.
  Gotchas learned: meson 1.3.2 (no `/` path join; one-expr-per-line; use 'client-header'
  not 'header' with wayland-scanner); after wl_surface_commit must wl_display_flush()
  before a prepare_read/poll loop (frames never arrive otherwise). VM now has
  meson/ninja/gcc/libwayland-dev/wayland-protocols installed.
- 2026-10-25 (M2b — dex2jvm WORKS): com.macapk.dex (ported verbatim, 36 files) compiles
  clean with javac 21 on the ARM64 VM and converted tests/uibench/uibench.apk ->
  2,272 JVM .class files in 796 ms (`java -cp out com.macapk.dex.Main <apk> <out.jar>`).
  uibench.apk is pure-Java (no .so). Whole tree now compiles together (framework seed +
  dex2jvm + smoke) via: javac -nowarn -d out $(find runtime/java tests/smoke -name "*.java")
  on ~/apklayer-m1 (note: runtime/java path after the scp layout change).
  Next: class-load smoke (run a converted app class against the framework seed), then
  android.app bootstrap (ActivityThread-style driver, Mac.apk Boot.java as reference),
  then resource system (AssetManager/XmlBlock) + hwui->Skiko surface bridge.
- 2026-10-25 (M2c — framework base VALIDATED): Mac.apk runtime/java snapshot copied to
  runtime/java_mac (3049 files) compiles CLEAN with `javac --release 21` on the ARM64 VM
  (0 errors). LoadProbe vs converted uibench: **800/800 app classes link** (was 567/800 with
  our hand-grown seed). Decision locked: java_mac is the framework base; the hand seed
  (runtime/java) is archived for reference (M1 loop proven there). VM layout:
  ~/apklayer-mac/{java_mac,out_mac,probeout}; probe:
  `java -XX:UseSVE=0 -cp out_mac:probeout apklayer.probe.LoadProbe /tmp/uibench-classes.jar`.
  Next: runner (Boot-style in-process driver: parse manifest -> makeApplication ->
  instantiate Activity -> drive lifecycle) + surface bridge (ViewRootImpl->HWUI->Skiko
  linux-arm64 -> our Wayland window) + userspace binder where unavoidable.
- 2026-10-25 (M2d — REAL APP ACTIVITY INSTANTIATED): tests/runner/AppRunner.java loads
  converted uibench classes over the java_mac framework; `com.android.test.uibench.MainActivity`
  resolves the full chain (AppCompatActivity<-FragmentActivity<-...<-android.app.Activity<-
  ContextThemeWrapper<-ContextWrapper<-Context), static-init passes, and the Activity
  CONSTRUCTS successfully on the bare JVM (`INSTANTIATED: ...@67424e82`). First JNI gap
  (com.macapk.loader.NativeLog.print) implemented in bridge/src/jni/nativelog.c
  (meson target libapklayer-jni.so; loaded via -Dapklayer.jni=<path>). Framework's own
  logging/Looper init ran (I/Looper: MAIN thread pinned; PresentedSdk gate messages).
  Run recipe on VM:
    java -XX:UseSVE=0 -Dapklayer.jni=~/apklayer-m1/bridge/b/libapklayer-jni.so \
         -cp ~/apklayer-mac/out_mac:~/apklayer-mac/runnerout apklayer.runner.AppRunner \
         /tmp/uibench-classes.jar com.android.test.uibench.MainActivity
  Meson gotcha learned: JDK include via R=$(readlink -f $(command -v javac)); ${R%/bin/javac}/include.
  Next: attach/launch path — ContextImpl + Resources (AssetManager is native-backed in
  java_mac; check how Mac.apk bridges it), Instrumentation.execStartActivity-style driver
  (Boot.java reference), then ViewRootImpl -> our Wayland surface bridge.
- 2026-10-25 (M2e — JAVA FRAME ON WAYLAND): bridge/src/jni/nativecocoa.c implements the
  whole com.macapk.loader.NativeCocoa host seam (71 natives) for Linux: init/present* over
  our own wl_shm xdg_toplevel (480x800 phone canvas), runLoop=pump(16ms), rest stubbed.
  FrameSmoke (tests/smoke/FrameSmoke.java, reflective) -> `FRAME init=ok size=480x800
  presented=1` exit 0 on the VM with WAYLAND_DISPLAY=wayland-0. A Java-drawn gradient now
  reaches a real Wayland window through the framework's own host seam.
  Build: ninja -C ~/apklayer-m1/bridge/b -> libapklayer-jni.so (jni include via
  R=$(readlink -f $(command -v javac)); ${R%/bin/javac}/include).
  Next: run Boot.run(apkPath, appJar, appDir, manifestPath) against uibench — inspect its
  required inputs (manifest file format from apk_unpacker; com.macapk.loader has
  ArscReader + BinaryXmlParser pure-Java; AppClassLoader) and drive a real Activity to
  present via ViewCanvasDriver -> NativeCocoa.presentBitmap.
- 2026-10-25 (M3 attempt #1): tests/launcher/Launch.java (unpack APK -> BinaryXmlParser
  decodes binary AXML to text (pure Java!) -> dex2jvm -> Boot.run) gets Mac.apk's Boot deep
  into startup on the VM: manifest parsed (32 activities, launcher MainActivity), class
  loader built, display 960x1600px/480x800dp config built, AWT initialised (framework
  Canvas is AWT-backed), monet/BC/translator warnings non-fatal. Hard blockers from logs:
  (a) MACAPK_FW_RES_APK unset -> platform resources empty -> every @android: ref fails;
      need framework-res.apk staged next to jars (tools/framework-res.apk) or env var set.
  (b) "resources.arsc NOT ingested ... installed apk set is the only resource source" and
      "MACAPK_APK_PATH is unset" -> set MACAPK_APK_PATH to the apk (also picks SDK 27).
  Boot.run returned (exit 0) after theme warning. Next: source framework-res.apk
  (Mac.apk release bundle / their staged runtime), re-run with MACAPK_FW_RES_APK +
  MACAPK_APK_PATH env set; watch tail of /tmp/launch1.log for the next gate.
- 2026-10-25 (M3 attempt#3 — APP RUNNING): with framework-res.apk + MACAPK_APK_PATH +
  blocking runLoop + NativeOs natives, launch3.log shows the FULL path: resource index,
  theme, starting window, Application init, LAUNCH_ACTIVITY posted, "starting activity
  com.android.test.uibench.MainActivity", fragments resumed
  (TestListFragment view=true resumed=true), "main loop dispatched #1 on thread=main".
  Process stayed alive until `timeout 45` SIGTERM (exit 124) — the app was RUNNING.
  Remaining: verify frames actually present (added PRESENT frame logging to jni), missing
  native setTopActivityConfigChanges (added), NativeSqlite.nativeOpen (media provider,
  non-fatal), input injection (registerTouchDispatcher currently stubbed).

## 2026-10-25 (evening) — ROBOLECTRIC PIVOT (user directive)
- User rejected the java_mac approach: "you copied whole android framework from mac.apk,
  that's manually patched files that won't be good to replicate by the android version
  updates, so you should use robolectric's method — keep working until full UI running
  uibench.apk." java_mac = reference only from now on. Hand-written stubs discouraged;
  prefer copying Robolectric/AOSP artifacts.
- Robolectric repo studied at /Users/erfanabdi/GitHub/robolectric (MIT): sandbox
  instrumentation, shadows/framework, resources (CppAssetManager2/LoadedArsc),
  nativeruntime. We consume PUBLISHED artifacts: robolectric 4.17 (maven -> ~/rob/libs,
  plus manual androidx.test monitor/annotation jars) and
  android-all-instrumented-8.1.0-robolectric-4611349-i7.jar (SDK 27 -> ~/rob/android-all).
  API-35 android.jar stubs -> ~/rob/android-stub.jar (satisfies shadow signatures newer
  than 27; outer classpath only, like AGP's mockable jar).
- Driver: tests/rob — RunUiBench (JUnit4 + RobolectricTestRunner, @Config sdk=27,
  manifest uibench/AndroidManifest.xml via -Dandroid_resource_apk, qualifiers w480dp-h800dp,
  LEGACY graphics), HostWindow (plain-loader JNI bridge to libapklayer-wl.so so JNI
  bindings survive the sandbox), DenyInstrument (ASM stamps RUNTIME-visible
  @DoNotInstrument on dex2jvm output — required: its <init>->__constructor__ lowering
  VerifyErrors under Robolectric's own <init> renaming; AnnotationCollector only honors
  VISIBLE annotations).
- bridge: libapklayer-wl.so (present.c) = our Wayland presenter (init/present/pump/
  shouldClose C ABI). libapklayer-jni.so (NativeCocoa/NativeOs/NativeLog) = retired path.
- scripts/apklayer-rob.sh = one-shot sync+build+run on VM.
- Gates passed: deps resolve; sandbox boots (needs outer stubout Build SDK_INT=27 for
  conscrypt, androidx.test monitor classes.jar, android-stub.jar on outer cp); app
  classes load (DoNotInstrument); manifest+resources load via android_resource_apk;
  MainActivity CREATED. Current gate: IllegalStateException in postCreate — AppCompat
  "You need to use a Theme.AppCompat theme"; driver-side setTheme fallback added
  (resolve Theme.AppCompat(.Light)/AppTheme from app package). Next: fix resource
  package resolution (pkg printed org.robolectric.default -> getIdentifier returned 0;
  must make Robolectric use com.android.test.uibench package) then frames -> Wayland.

## 2026-10-25 (night) — FULL UIBENCH UI ON WAYLAND (M3+M4 render)
- Theme gate root cause: Robolectric reads android_resource_apk/android_merged_manifest ONLY from
  classpath /com/android/tools/test_config.properties (AGP contract), not -D props; and @Config
  manifest pointing at the binary AXML fails to parse -> org.robolectric.default. Script now writes
  that file (manifest.txt = text-decoded manifest, base.apk). The real PackageParser parses the APK.
- LEGACY graphics = recording fake (no text, translate not composed) -> never full UI.
  NATIVE graphics renders the complete UI (verified first on the Mac with mac-arm64 dylib).
- Robolectric native runtime ships linux-x86_64 / mac / windows only — NO linux-arm64 in any
  android-all (checked 27, 36, 37) nor nativeruntime-dist-compat. In AOSP it is a prebuilt too
  (built from a custom AOSP hostruntime branch with Soong) -> building it = AOSP build (rule!).
- Solution on the arm64 VM: Rosetta for Linux is active (binfmt RosettaLinux, amd64 foreign arch,
  libc6:amd64). x86_64 JDK = LOCAL los24 prebuilts/jdk/jdk21/linux-x86 -> VM ~/jdk21-x86.
  HotSpot C2 bus-errors under Rosetta -> -XX:TieredStopAtLevel=1 (C1 only) works.
  No x86_64 libwayland locally -> frames go through a shared file /dev/shm/apklayer-fb
  (pure-Java MappedByteBuffer in HostWindow) to native arm64 bridge/b/apklayer-presentd.
- Run: APKLAYER_GFX=NATIVE APKLY_VM_PASS=1234 ./scripts/apklayer-rob.sh /tmp/uibench-classes.jar
  com.android.test.uibench.MainActivity '$HOME/apklayer-apps/uibench'  -> ~43 fps, snapshot
  /tmp/apklayer-frame.png on VM.
- User (2026-10-25): "stop downloads, try to use local stuff" — prefer local artifacts (los24
  prebuilts, OrbStack VM, existing ~/rob) over network downloads.
- Next: M5 input (presentd forwards wl_pointer into the fb; Java injects MotionEvents) and
  following startActivity into sub-activities.
