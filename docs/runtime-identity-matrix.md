# Runtime identity gap matrix (handoff items 5-7), measured 2026-11-13/14

This file compares what an app sees on real Android with what it sees under apklayer. It covers Java and class identity, native inspection, and ART internals. Each probe is one `key=value` line from `tests/apps/hello-identity`, the same APK in all three places:

- **Android**: Waydroid, Android 13 (LineageOS 20 `waydroid_x86_64`, userdebug/test-keys, x86_64) on the x86 PC. The probe ran **as a real installed app** (`pm install`, `am start`, target SDK 34, `untrusted_app` sandbox, zygote child). It did not run as standalone dalvikvm. Waydroid has no ARM translation (`ro.dalvik.vm.native.bridge=0`), so it ran the x86_64 build of the same sources.
- **ARM64**: apklayer on UTM (native arm64), userspace binder. Runtime: the `~/wt/realapps` tree (built 2026-11-13 19:36 from current main plus the Termux/Fennec agent's work). The VM had no room for a tree of its own; the `~/apklayer` tree did not start an app at the time.
- **x86**: apklayer on the x86 PC, worktree at 0f42092b (main 811b44c1), userspace binder. The arm64 library is translated by berberis.

Raw results are in `tests/identity/results/{waydroid,apklayer-arm64,apklayer-x86}.txt` (first run) and `apklayer-{arm64,x86}-main.txt` (rerun on main fecb75f5). The full joined table comes from `python3 tests/identity/compare.py tests/identity/results/waydroid.txt tests/identity/results/apklayer-{arm64,x86}.txt`. To collect again: `tests/run identity-matrix` (an optional group) or `tests/identity/collect.py apklayer|waydroid OUT` (docstring there).

**Reading the verdicts.** Some differences are expected and are not gaps:
- the ABI (x86_64 on Waydroid, arm64 for apklayer);
- the device profile (apklayer presents a Pixel 5 running Android 17);
- uid numbers;
- Waydroid's container artifacts: AppArmor label, `/proc/1` visible, host `uname`, x86 cpuinfo.

The verdict judges whether apklayer gives an Android-shaped answer. Where Waydroid itself deviates from a phone, the AOSP value is named.

- **Severity:**
  - H: real apps or SDKs in the corpus read it, or it breaks code (crash, wrong result).
  - M: a clear identity clue that detection SDKs or hooking frameworks use.
  - L: rarely inspected.
- **Fix layer:**
  - *Java shadow*: libcore/ShadowXxx/converter.
  - *bionic patch*: bionic_translation, the preload path layer, `bridge/native`.
  - *proc view*: the preload/translator `/proc` filters.
  - *translator*: berberis patches and the nativehelper/JNI trampolines.
  - *ART emulation*: a fake ART structure layer.
  - *launcher*: the `cli/` process setup.

## Summary

Raw key-by-key equality against Waydroid (464 probe keys; ABI and device-profile differences are included, so 100% is not the goal). First run, rerun on main fecb75f5, and the second rerun on main 6dc2e161 (2026-11-14, `tests/identity/results/apklayer-{arm64,x86}-main2.txt`, one `tests/run identity-matrix` per host):

| area (key prefix) | keys | ARM64 first / fecb75f5 / 6dc2e161 | x86 first / fecb75f5 / 6dc2e161 |
|---|---|---|---|
| 5 Java/class identity (`java.`) | 161 | 50 / 79 / 80 | 51 / 80 / 81 |
| 6 native inspection (`native.`) | 218 | 69 / 99 / 101 | 97 / 102 / 103 |
| 7 ART internals (`art.`, `native.art.`, `native.jnithread`) | 83 | 21 / 29 / 32 | 22 / 30 / 32 |
| total (with 2 meta keys) | 464 | 142 (of 446) / 207 / **213** | 169 (of 463) / 212 / **216** |

The judged matrix below groups those keys into 117 probes. A row counts as "match" only if both apklayer columns match. Re-judged key by key on 2026-11-14 against the new raw files (the verdict cells say "rerun" where a row changed):

| area | rows | match | partial | mismatch | n/a |
|---|---|---|---|---|---|
| 5 Java/class identity | 37 | 15 | 17 | 5 | 0 |
| 6 native inspection | 49 | 23 | 17 | 7 | 2 |
| 7 ART internals | 31 | 16 | 4 | 10 | 1 |
| total | 117 | 54 | 38 | 22 | 3 |

History of the totals: first measurement 29 match, 13 partial, 73 mismatch, 1 n/a (116 rows); after the fixes of 2026-11-14 (verdicts as of main fecb75f5, judged generously): 57 / 29 / 28 / 3; this rerun, judged key by key: 54 / 38 / 22 / 3. Match fell by 3 and partial rose by 9 because the stricter reading counts a row as partial when any of its keys still differs (host paths in loader and map_files, `SunEC` in the providers list, `cldr.version`, libdl/x86 dladdr paths, the maps markers); mismatch fell by 6. New since fecb75f5 and visible in the raw files: ArtMethod adjacent delta 32 (`art.artMethod.delta`, `native.art.artMethod.delta`), BOOTCLASSPATH set, `ro.vndk.version`, `JNI_GetCreatedJavaVMs` from RTLD_DEFAULT gone on both, `dladdr` of `JNI_CreateJavaVM`/`JNI_GetCreatedJavaVMs` equal, default SSLContext equal. New differences: `SunEC` is appended to `Security.getProviders()` (c07d523e), `dalvik.vm.isa.arm64.variant` set where Waydroid has none (device profile, harmless). The x86 run used the arm64 build of the probe app under the native bridge (`meta.abi=arm64-v8a`; the earlier x86 files ran the x86_64 build), so x86 values now show the translated guest (`/system/bin/arm64/app_process64`, `/system/lib64/arm64/libc.so`, AT_PLATFORM null).

### Top mismatches by severity
*Update 2026-11-14 (native process views, tests/native/proc-views.c, hello-identity `child.*`): item 2 and the process-view parts of item 4 (thread names, comm, exe, fd targets, cgroup, mountinfo, rwx, host paths in maps) are fixed on ARM64 and x86; the rows of "6. Native inspection" say what changed and what is left. Items 1, 3 and 5-10 are as measured.*

1. **FIXED 2026-11-14: `JNI_GetCreatedJavaVMs` (H, crash).** (Before the fix:) On x86 the call aborts the process: berberis' nativehelper trampoline hits `CHECK(*output_size == 1u)` because the host libnativehelper returns 0 VMs. On ARM64 it returns `count=0`. Android returns 1, the app's VM. FCL (`libterracotta`), many SDKs and Unity/Unreal-style engines call it.
2. **`/proc/self/status` read through libc fails on ARM64 (H).** `open`+`read` returns EBADF (`native.proc.status=ERR 9`). The raw syscall works but shows the **host uid 1000**. TracerPid readers (libunity, libjingle, WebView, DisplayLink in the corpus) get an error.
3. **Raw syscalls on native ARM64 bypass every view (H for anti-tamper). Fixed 2026-11-14 (0074, bridge/native/raw_syscall.c).** Was: `svc #0` getuid returned 1000, `openat(/proc/self/maps)` showed `libjvm.so` and no `libart.so`. Now: 10012 = libc and libart without libjvm, on native ARM64 as under x86 translation.
4. **Host JVM inside the process (H).**
   - Maps: 258 `rwx` mappings (Android: 0); paths under `/home`, `/usr/lib`, `/tmp`.
   - Thread names: `VM Thread`, `C2 CompilerThre`, `Signal Dispatch`, `apklayer-*`, `java`; ART has `Signal Catcher`, `HeapTaskDaemon`, `Jit thread pool` and others.
   - `comm=java`; `exe`/`AT_EXECFN` show the JDK `java` on ARM64.
   - fd targets: `Robolectric_all.jar`, the JDK `modules`, apklayer jars. Android's are `/apex/**/javalib/*.jar` and `/dev/binderfs`.
   - cgroup is the desktop's `user.slice/...apklayer-mx1-app...scope`.
5. **JNI functions do not live in libart (H for anti-hook, M otherwise).**
   - `dladdr` of the `JNIEnv` table and functions: Android names `libart.so` (`gJniNativeInterface`; no symbol name for the functions). ARM64 names `libandroid_runtime.so` in a Robolectric temp dir. x86 is `unresolved`. **FIXED on native ARM64 2026-11-14 (bridge/native/libart_jni.c):** all 229 JNIEnv and 5 JavaVM entries are jumps inside the real libart.so, `dladdr` names `/apex/com.android.art/lib64/libart.so` without a symbol name (hello-identity `dladdr.jni.inLibart=234/234`; `.symtab` carries ART's `art::JNI<false>::...` names). The table itself (`*env`) is still the JVM's (names `libopenjdkjvm.so`, Android: `_ZN3art27JniNativeInterfaceFunctionsILb0EE19gJniNativeInterfaceE` in libart); x86 translation: libart.so is loaded in the guest, but the guest's JNI table is Berberis' own (host-side thunks cannot rewrite it; the constructor finds no table hand-over), so `dladdr` of JNI functions stays unresolved there (inLibart=0/234).
   - `GetVersion` and FindClass from an attached thread: FIXED 2026-11-14 (guest callers now get 0x10006 and ClassNotFoundException). Only the `dladdr` naming above remains (item 14: needs a real libart ELF).
6. **Host libraries are dlopen-able (M/H). Fixed 2026-11-14 (0073) for app-namespace callers.** Was: `dlopen("libjvm.so")`, `libjava.so`, `libc.so.6`, `libjavacore.so`, `libbinder.so` and `libandroid_runtime.so` succeeded on ARM64 and `dladdr(malloc)` named glibc. Now they fail with `dlopen failed: library "x" not found`, `dladdr` names bionic libc, `dl_iterate_phdr` lists Android modules only.
7. **libart.so is a name, not a file (H for ElfImg-style resolvers, M otherwise).** The maps line exists, but opening the path fails (ENOENT). There are no `.dynsym`/`.symtab` ART symbols. Waydroid opens it and finds 12/12 symbols whose code matches memory. **FIXED on native ARM64 2026-11-14:** a real aarch64 ELF (bridge/native/libart_symbols.c, libart_jni.c; installed in the Android root's `apex/com.android.art/lib64`, loaded by the runtime) is the maps line and the file: `.dynsym`+`.symtab`, the 12 probed symbols (`JNI_*`, `_ZN3art7Runtime9instance_E`, `art_quick_*`, ArtMethod/Thread/ClassLinker/Heap/Jit functions as stubs: nothing/null/false, or an abort naming the function), code matches memory (18/18), raw `openat` of the path reads it. The JVM's own mapping is now `libopenjdkjvm.so` (an ART apex file name).
8. **ArtMethod/ArtField layout (M; H for hook frameworks and HiddenApiBypass).**
   - Android: `jmethodID == Executable.artMethod`, adjacent delta 32, access flags at +4, native method `data_ == JNI function`, `entry_point` in libart, `jfieldID` delta 16.
   - apklayer (2026-11-14, JdkArt): `Executable.artMethod` exists (java.base patch, `ApklayerArt`), points into a synthetic 32-byte-entry table per class (LengthPrefixedArray: u32 length, element 0 at +8, direct methods then virtual, sorted), adjacent delta 32 (probe `art.artMethod.walk`: size, access flags at +4 = modifiers | 0x10000000, index at +8, length prefix, read through Unsafe addresses). Not done: jmethodID is still HotSpot's (delta 8, `!= artMethod`: needs every JNI call that takes a jmethodID translated, ~100 entries of bridge/native/jni_guest_surface.c), `data_`/`entry_point` are 0 (no libart code yet), declaring_class_ is an identity hash not a heap reference, no hidden-API flag bits, tables never freed. jfieldID unchanged. Unsafe reads of a `Class` or `MethodHandle` (HiddenApiBypass's stub offsets, `Class.methods`) are still answered empty by ApklayerUnsafe: the stubs' offsets are HotSpot's, a real walk would then reach `invoke` through a handle whose artFieldOrMethod HotSpot ignores, so the library's present fallback to reflection stays.
   - `Class.accessFlags/objectSize/dexCache`, `Thread.nativePeer`, `Field.accessFlags` are all absent (all readable on Android 13 for apps).
9. **Hidden-API enforcement missing (M).**
   - Android 13 blocks these for a target-34 app: `VMRuntime.setHiddenApiExemptions` (and meta-reflection to it), `vmVersion`, `isJavaDebuggable`, `getTargetSdkVersion`, `VMDebug.getVmFeatureList`. apklayer allows them all.
   - `VMRuntime.getTargetSdkVersion` returns 10000 instead of 34.
   - HiddenApiBypass (Termux, Aurora) is the user found in the corpus.
10. **Java messages and classes are HotSpot's (M).**
    - Messages: NPE "Cannot invoke ... because" (ART: "Attempt to invoke virtual method ... on a null object reference"); AIOOBE "Index 5 out of bounds" (ART: "length=3; index=5"); CCE; ArrayStoreException; "/ by zero".
    - Security providers `SUN SunRsaSign ... ApklayerTrust` (Android: `AndroidNSSP AndroidOpenSSL ...`); default SSLContext `SunJSSE`. (Fixed 2026-11-14, see the providers row.)
    - `Runtime.version()` exists and leaks `21.0.13...-Ubuntu`.
    - `java.lang.Module`, `StackWalker`, `ManagementFactory` and `HotSpotDiagnosticMXBean` are present.
    - The proxy class is `jdk.proxy4.$Proxy45`.
    - `Object.hashCode` is `native`.

## Matrix

### 5. Java and class identity

| probe | Android 13 (Waydroid app) | apklayer ARM64 | apklayer x86 | verdict | sev | fix layer |
|---|---|---|---|---|---|---|
| java.vm.name/vendor/version, java.version, java.specification.*, java.runtime.* | Dalvik / The Android Project / 2.1.0, 0, 0.9 | same | same | match | H (OkHttp, Gson, Guava, ~30 dex) | - |
| os.name, os.version, file.*, path/line separators, java.home, java.io.tmpdir, user.home | Linux, kernel, /system, app cache, "" | same | same | match | M | - |
| os.arch | x86_64 (ABI) | aarch64 | aarch64 | match (ABI) | M | - |
| java.boot.class.path | /apex/com.android.art/javalib/core-oj.jar:... | **fixed 2026-11-14**: the device list (core-oj ... framework ... conscrypt) | same | match (shorter list than Android 13: no per-apex modules) | M | - |
| java.library.path | /system/lib64:/system/system_ext/lib64:/system/product/lib64 | **fixed**: same | same | match | L | - |
| user.name | root (Waydroid) / AOSP: "" | u0_a0 | u0_a0 | partial | L | Java shadow |
| user.language/region/locale/variant, http.agent, android.icu.*, android.openssl.version, android.zlib.version | set (http.agent `Dalvik/2.1.0 (Linux; U; Android 13; ...)`) | **fixed**: user.language/region from the locale; http.agent `Dalvik/2.1.0 (Linux; U; Android 17; Pixel 5 Build/CP2A...)` from Build; icu 78.3/17.0, openssl, zlib 1.3.1 (the bundled libraries' versions, not Android 13's) | same | match (values are this device's) | M | - |
| System.getProperties keys (host-only sun./jdk./apklayer keys) | none | none | none | partial (2026-11-14 rerun: `android.icu.cldr.version` missing, 2 keys of Android's 3; no host keys) | H | - |
| dalvik.system.* classes present | yes, BootClassLoader | yes, SdkSandboxClassLoader | same | partial (wrong loader) | M | Java shadow (boot-parent identity) |
| java.lang.Daemons, VMClassLoader, BootClassLoader, AndroidHardcodedSystemProperties, invoke.Transformers, sun.misc.Cleaner, sun.reflect.Reflection, jdk.internal.misc.SharedSecrets | present | absent | absent | mismatch | M | Java shadow (libcore overlay classes) |
| HotSpot-only classes: Module, StackWalker, ProcessHandle, Record, LambdaForm, DirectMethodHandle, StringConcatFactory, sun.misc.Signal, jdk.internal.loader.ClassLoaders, java.lang.management.*, com.sun.management.*, sun.management.* | absent | **fixed for Class.forName** (AppClassLoader.jdkOnly; the JVM's own linking still reaches them); java.lang.Module etc. exist as classes | same | match (by Class.forName) | M | - |
| org.robolectric.*, apklayer.host.* | absent | **fixed**: absent for Class.forName and loadClass through the app loader (the converter's own targets stay loadable, but not by Class.forName) | same | match | H | - |
| org.apache.http.HttpResponse (no uses-library) | absent | present | present | mismatch | L | Java shadow |
| de.robv.android.xposed.*, com.saurik.substrate.* | absent | absent | absent | match | M | - |
| ClassNotFoundException message | `no.such.Klass`, cause CNFE | cause null | cause null | partial | L | Java shadow |
| Loader chain | PathClassLoader -> BootClassLoader -> null | **fixed**: PathClassLoader -> java.lang.BootClassLoader -> null (a java.base class from the jdk-patch that asks the sandbox) | same | match | H | - |
| ClassLoader.getSystemClassLoader | PathClassLoader[DexPathList[[directory "."],...]] -> BootClassLoader | **fixed**: the app's PathClassLoader (converter route GuestLoaders, converter ABI 9); its toString is the app's, not `directory "."` | same | partial | M | - |
| String.class / Activity.class getClassLoader | BootClassLoader instance (sic: Android 13 returns the object for framework classes) | null / SdkSandboxClassLoader (open: the loader of a JVM class is null, a framework class's is the sandbox) | same | mismatch | M | known limit: native Class.getClassLoader cannot be redirected |
| VMStack.getCallingClassLoader | PathClassLoader | null | null | mismatch | L | Java shadow |
| getResource("java/lang/Object.class") on parent | null | **fixed** on the BootClassLoader parent (null); `Object.class.getResource` still gives jrt: (known limit) | same | partial | M | Java shadow (Class.getResource) |
| app loader toString / DexPathList | `[zip file "/data/app/<APP>/base.apk"], nativeLibraryDirectories=[/data/app/<APP>/lib/x86_64, .../base.apk!/lib/x86_64, /system/lib64, ...]` | **fixed in shape**: `[zip file "<host apk path>"], nativeLibraryDirectories=[<host lib dir>, <apk>!/lib/<abi>, /system/lib64, /system/system_ext/lib64, /system/product/lib64]` | same | partial (host paths: no /data/app mapping) | H | path layer (present /data/app/..., open) |
| DexPathList.dexElements | 1 element, DexFile with long[2] mCookie | **fixed**: 1 element, a DexFile (never opened) with a long[2] mCookie | same | partial (rerun: shape equal, the zip path is the host file) | H | - |
| findLibrary / nativeLibraryDirectories path | /data/app/<APP>/lib/x86_64/libhello.so | **fixed 2026-11-14**: the app class loader's `findLibrary` returns `/data/app/~~tag/<pkg>-tag/lib/arm64/libhello.so` (the path layer maps it back; the loading path `System.loadLibrary` still opens the host file); maps, fd links, dladdr and dl_iterate_phdr show the same spelling (0076) | same | partial (ARM64 checked on Parallels 2026-11-14: findLibrary, ApplicationInfo.sourceDir/publicSourceDir/nativeLibraryDir (Context and PackageManager routes), dataDir, getFilesDir, maps; loadLibrary through the Android path works; the PathClassLoader toString/nativeLibraryDirectories still list host dirs) | M | path layer + AppClassLoader |
| BaseDexClassLoader / ClassLoader declared fields | [pathList] / [parent] (hidden-API filtered) | **fixed for BaseDexClassLoader**: [pathList] (Class.getDeclaredFields hook for app callers; `__robo_data__` removed everywhere); ClassLoader gives [] (the JDK filters its fields) | same | partial | M | - |
| Exception stack depth/frames in onCreate | 22 frames, ActivityThread...ZygoteInit.main | **fixed**: 22 frames, 0 host frames, ZygoteInit.main at the bottom (GuestTraces; `-Dapklayer.rawtraces=true` shows the JVM's raw trace; a trace that starts in Robolectric/apklayer code stays raw) | same | match (line numbers are the framework's) | H | - |
| Thread.getStackTrace first frame | dalvik.system.VMStack.getThreadStackTrace, java.lang.Thread.getStackTrace | **fixed**: same two frames | same | match | M | - |
| Method.invoke of a package-private method | works (frame `java.lang.reflect.Method.invoke(Native Method)`) | **fixed**: works (Reflect.invokeAs checks access for the app's class, calls through a private accessible copy); frame `Method.invoke(Native Method)` | same | match | H | - |
| Thread.getAllStackTraces names | main, FinalizerDaemon, FinalizerWatchdogDaemon, HeapTaskDaemon, ReferenceQueueDaemon, Signal Catcher, Jit thread pool, Profile Saver, RenderThread, binder:* | **partly fixed**: main, FinalizerDaemon, ReferenceQueueDaemon, Signal Catcher, FinalizerWatchdogDaemon/HeapTaskDaemon/Jit/Profile Saver (unstarted stand-ins), binder:*; host threads hidden from the list (the real JVM threads keep their names in /proc: native views) | same | partial | M | - |
| Main thread | name=main id=2 group=main | **fixed**: name=main group=main; id=16 (not 2) | id=15 | partial (thread id) | H | - |
| VMRuntime vmVersion/isJavaDebuggable/getTargetSdkVersion | blocked (NoSuchMethodException: hidden API) | **fixed**: blocked for target SDK >= 28 apps (GuestHiddenApi); getTargetSdkVersion is the app's manifest target (VMRuntime shadow, set at start); `setHiddenApiExemptions` stays visible (HiddenApiBypass needs it; see PLAN) | same | partial | M | - |
| VMDebug.getVmFeatureList, Debug.getRuntimeStats | blocked / art.gc.* keys | **fixed**: getVmFeatureList blocked; getRuntimeStats still [] | same | partial | L | Java shadow |
| Runtime.version() | NoSuchMethodException (API 33) | **fixed**: NoSuchMethodException from Class.getMethod for app callers (libcore removed it) | same | match | M | - |
| Runtime.availableProcessors / maxMemory | 4 / 192 MB (heapgrowthlimit) | **fixed**: maxMemory 256 MB (dalvik.vm.heapgrowthlimit of the Pixel 5 profile; the converter routes Runtime.maxMemory to GuestLoaders.maxMemory, the real -Xmx is not capped); cpus 10 | 4 / 256 MB | partial | M | - |
| Security providers / SSLContext default | AndroidNSSP AndroidOpenSSL CertPathProvider AndroidKeyStoreBCWorkaround BC HarmonyJSSE AndroidKeyStore / AndroidOpenSSL | **fixed (2026-11-14)**: the same list, order and default SSLContext (AndroidOpenSSL): the framework's Conscrypt over its native `libjavacrypto.so` (loaded by the guest-library loader; `HostProviders`, `NativeCryptoJni` overlay), AndroidNSSP from the app's network_security_config, HarmonyJSSE (default key/trust manager algorithm PKIX, `AndroidCAStore` over `/system/etc/security/cacerts`). CertPathProvider delegates to the JDK's own certpath classes (libcore's are the same code). The JDK's SUN/SunJSSE/... leave the list. A failed Conscrypt load keeps the old list with one warning | same | partial (rerun: `SunEC` is appended to the list since c07d523e; the rest equal) | H | tests: group `tls`, identity-matrix probes `java.ssl.*`, `java.keystore.cacerts`, `java.securerandom` |
| MethodHandle class, Proxy class name, Object.hashCode/getClass toString | MethodHandleImpl, `$Proxy2`, not native | DirectMethodHandle, `jdk.proxy4.$Proxy45`, `native` | same | mismatch | L | Java shadow |
| Lambda class name, mapLibraryName, charset | `$$ExternalSyntheticLambda`, libx.so, UTF-8 | same | same | match | L | - |
| System.getenv keys | ANDROID_*, ASEC_MOUNTPOINT, BOOTCLASSPATH, DEX2OATBOOTCLASSPATH, SYSTEMSERVERCLASSPATH, ANDROID_SOCKET_usap_pool_primary, PATH, ... | ANDROID_* subset, no *CLASSPATH | same | partial | L | launcher (initial environment) |

### 6. Native inspection

| probe | Android 13 (Waydroid app) | apklayer ARM64 | apklayer x86 | verdict | sev | fix layer |
|---|---|---|---|---|---|---|
| maps: libart.so line | present | present: the real libart.so ELF (2026-11-14); the JDK mapping is `libopenjdkjvm.so` | present (JDK mapping renamed); x86 translation: the guest loads the aarch64 libart.so at start (2026-11-14, native_bridge.c) | match | H (13 apps, ACE) | - |
| maps: other runtime markers (bionic libc/linker64 apex paths, app_process64, boot.art/oat, boot-framework, .vdex/.odex, framework.jar, core-oj.jar, [anon:dalvik-*], jit-code-cache, scudo, /dev/__properties__) | all present | only libart, libandroid_runtime, base.apk | + apex linker64, [anon:.bss] | partial (rerun: libart, framework.jar, [anon:dalvik-*], jit cache, base.apk present; still absent: bionic libc/linker64/app_process64 paths, boot.art/oat, vdex/odex, core-oj.jar, dalvik-main space, scudo, libc_malloc, stack_and_tls, /dev/__properties__) | H (Unity/il2cpp, Cronet, Sentry, ACE read maps) | proc view (synthesize ART/zygote names; dalvik anon names via prctl PR_SET_VMA) |
| maps: host markers (/home, /usr/lib, /tmp, ld-linux, libglib/wayland) | none | **fixed 2026-11-14**: desktop libraries (/usr, /lib, /opt, /snap) and apklayer host libs unnamed; Android root files at /system, /apex; staged libs at /system/lib64; Robolectric font assets at /system/fonts. Left: the app's own files (/home/.../apps/<pkg>/lib, base.apk), /tmp, `memfd:` | /home (app files) | partial (rerun: `/home/`, `/tmp/`, libapklayer still in maps on ARM64 and x86) | M | proc view (bridge/native/proc_task.c `apklayer_proc_map_name`) |
| maps: rwx mappings | 0 | **fixed**: 0 (was 258, 62 MB: bionic_translation 0072 re-protects every library's text; the JVM code cache reads `r-x [anon:dalvik-jit-code-cache]`) | 0 | match | M | linker patch 0072 + proc view |
| maps: first exec mapping, bionic libc path | `[anon_shmem:dalvik-zygote-jit-code-cache]`, /apex/com.android.runtime/lib64/bionic/libc.so | libhello / /usr/lib/aarch64-linux-gnu/libc.so.6 (glibc file) | android-root/system/lib64/arm64/libc.so (host path) | mismatch | H | proc view (bionic libc at its apex path) |
| smaps field names, smaps_rollup | kernel's | same | same | match | L | - |
| map_files | readable, no JDK entries | readable, no JDK | readable | partial (rerun: readable, no JDK, but `first=` is a host path, /home/<user>/.../prebuilts/.../libjavacrypto.so) | M (ACE) | - |
| /proc/self/status via libc | readable: Name hello.identity, Uid app, TracerPid 0, Seccomp 2, NoNewPrivs 1, CapBnd 0 | **fixed**: readable, Name hello.identity, Uid 10000, TracerPid 0, NoNewPrivs 1, Seccomp 2, Seccomp_filters 1, CapBnd 0 (was EBADF: bionic's open returned an fmemopen "fd", patch 0071; a toybox child's `cat /proc/self/status` too) | same (was Name java, CapBnd 1ffffffffff, Seccomp 0) | match | H | patch 0071 + proc view |
| /proc/self/status via raw syscall | Uid app | **Uid 1000** (host) | Uid 10000 | match (rerun: raw Uid 10000 = libc, 0074) | H for anti-tamper | proc view needs a seccomp/ptrace or syscall-user-dispatch layer on native ARM64 |
| cmdline | `hello.identity` | `hello.identity` (padded) | same | match | H | - |
| comm / prctl(PR_GET_NAME) / stat field 2 | hello.identity | **fixed**: hello.identity in comm, stat, status Name (the process leader's comm projects to the cmdline process name, last 15 bytes as AndroidRuntime::setArgv0) | same | match | M | proc view (proc_task.c); the real thread keeps `java` |
| cgroup | `0::/` (Waydroid; phone: `0::/uid_10xxx/pid_N`) | **fixed**: `0::/uid_10000/pid_<pid>` (cgroups.json v2 shape; the filter's own hidden-process check reads the real line) | same | match | M | proc view |
| attr/current (SELinux) | AppArmor (Waydroid); phone: `u:r:untrusted_app:s0:c...` | `unconfined` | `u:r:untrusted_app:s0:c0,c256,c512,c768` | match: **fixed 2026-11-14** on ARM64 (proc_view.c reads `u:r:untrusted_app:s0:c<id>,c256,c512,c768`, as x86); untested on the phones | M (Unity reads it) | proc view (done) |
| loginuid / wchan | 4294967295 / 0 | 1000 / futex_do_wait | same | mismatch | L | proc view |
| /proc/1/cmdline | /system/bin/init second_stage (container); phone: EACCES/ENOENT | ENOENT | ENOENT | match (phone) | M | - |
| /proc/version, uname | host kernel (Waydroid) | host kernel, nodename `UTM-Virtual-Machine` | nodename `Erfan-Hi10-Max` | partial (rerun: nodename localhost; kernel version string and sysname/release are the host's) | M (uname in dex of Telegram, Unciv, Device Info) | bionic patch (uname nodename/version) + translator |
| /proc/net/unix | readable (Waydroid) ; phone: EACCES since Android 10 | readable | EACCES | partial | L | proc view |
| environ, auxv (own) | EACCES (Waydroid AppArmor); phone: readable | readable, 11 keys | readable | n/a (reference differs) | M (Flutter/Go/Cronet read auxv) | - |
| auxv HWCAP/HWCAP2/PLATFORM vs getauxval | consistent | consistent, platform aarch64 | consistent, AT_PLATFORM **null**, HWCAP2 0 | partial (x86) | M (Go runtime, OpenCV, BoringSSL CPU dispatch) | translator (AT_PLATFORM string, HWCAP2): not tiny (`runtime/arm64/init_kernel_args.cc` needs a string copied onto the guest stack and a HWCAP2 that matches the emulated features); left open |
| exe link / AT_EXECFN | /system/bin/app_process64 | **fixed**: /system/bin/app_process64 for both (readlink view; the AT_EXECFN string on the stack is rewritten in place). Opening /proc/self/exe (an own-process open of the JDK `java`) now reads an ELF64 `app_process64` stand-in of the host machine (bridge/native/proc_view.c; no symbols, only exits; hello-identity `exe.file=ELF machine=183`). x86 translated guests: not checked | /system/bin/arm64/app_process64 (translator's name; unchanged) | match (ARM), near (x86) | H | proc view; translator for x86 |
| AT_SECURE / prctl dumpable / no_new_privs / seccomp | 1 / 0 / 1 / 2 | **fixed except AT_SECURE**: prctl dumpable 0, no_new_privs 1, seccomp 2 (a view: the real process stays dumpable and unfiltered so the host can read its environ and the JVM keeps its syscalls); status NoNewPrivs 1, Seccomp 2, Seccomp_filters 1, CapBnd 0. AT_SECURE stays 0 (auxv is the kernel's, set at exec); Waydroid's 1 is its AppArmor profile's exec transition: zygote's app_process64 is exec'd by init without one, so AT_SECURE is 0 on a device and the 0 here is right, no change | same | partial | M | proc view (prctl/syscall wrapper) |
| ppid / parent cmdline / pid==tid | zygote64 / main thread is pid | **partial**: the parent's /proc/<ppid>/cmdline and comm read `zygote64`, its exe app_process64 (the CLI is hidden from the app by the pid filter: on a phone the app cannot read zygote either); getppid() is the real pid, the main Looper thread is not the process leader | same | partial | M | known limit: the JVM's `java` launcher runs the main Looper in a thread it creates, so the app's main thread is not the process leader; not rewritten (the launcher is the JDK's) |
| fd targets | /apex/**/javalib/*.jar, /system/framework/*.jar, /dev/binderfs/*, /dev/ashmem, sockets | **fixed**: Robolectric/android-all/host jars read as /apex/com.android.art/javalib/*.jar and /system/framework/*.jar, the JDK `modules` as core-oj.jar, cfg.d as /linkerconfig, the instance log as /dev/null (the descriptors are unchanged). Left: the app's own base.apk/classes.jar host paths | same | partial | H | proc view (readlink of /proc/<pid>/fd/N) |
| mountinfo | Android mounts (/system, /vendor, /apex/*, /data) | **fixed**: desktop kernel mounts (/dev/shm, /sys/kernel/security, binfmt_misc, /sys/firmware...) left out on top of the earlier desktop-mount hiding; Android partitions added (/system, /vendor, /product, /system_ext, /odm, /apex + art/runtime/i18n, /data, /linkerconfig, /storage/emulated) | same | match | M | proc view (proc_view.c mounts_filter) |
| task/*/comm | ART daemons, binder:N_N, RenderThread, perfetto_hprof_ | **fixed**: HeapTaskDaemon, Signal Catcher, ReferenceQueueD, FinalizerDaemon, FinalizerWatchd, Profile Saver, Jit thread pool, Runtime worker, binder:<pid>_<n>; no VM Thread, C2 CompilerThre, apklayer-* or java (the task directory is unchanged: tids and tgkill work, only the names are views in comm, stat and status) | same | match | H | proc view |
| ns links | readable | same | same | match | L | - |
| limits | kernel table | same shape | same | match | L | - |
| dl_iterate_phdr modules | 320 Android modules, all EM_X86_64, sonames | **fixed 2026-11-14 (0073)** from an app-namespace callback: 261 modules, 8 without soname (330 / 77 before): program `/system/bin/app_process64`, one bionic libc at `/apex/com.android.runtime/lib64/bionic/libc.so`, the Android libraries at `/system/lib64`, no JVM/glibc-helper/desktop module; callers outside the app namespace (unwinders of the platform libraries) still see all | 8 guest modules only (app_process64, linker64, vdso, libc++, libc, libm, libdl, libhello) | partial: ARM64 (the app libraries keep host paths, a few host-loaded modules without soname remain); x86 near | H | bionic patch (done), host paths of app libs: path layer |
| foreign-machine modules in phdr list | 0 | 0 | 0 | match | M | - |
| dladdr(open/pthread_create/dl_iterate_phdr) | libc.so/libdl.so at /apex/com.android.runtime/lib64/bionic | **fixed 2026-11-14 (0073)**: `open`, `pthread_create` and the others are Android names in `/apex/com.android.runtime/lib64/bionic/libc.so`; libdl entries `/system/lib64/libdl.so` | /system/lib64/arm64/libc.so | partial (rerun: libdl entries at /system/lib64/libdl.so; `open` not `open64`) | H | bionic patch (done) |
| dladdr(malloc/__cxa_finalize) | bionic libc | **fixed 2026-11-14 (0073)**: `/apex/com.android.runtime/lib64/bionic/libc.so` | bionic libc (guest) | partial (rerun: ARM64 equal; x86 `__cxa_finalize` names /system/lib64/arm64/libc.so) | H | bionic patch (done) |
| dlsym(RTLD_DEFAULT, JNI_GetCreatedJavaVMs/JNI_CreateJavaVM/__libc_start_main/gnu_get_libc_version) | null | **partly fixed 2026-11-14 (0073)**: `gnu_get_libc_version`, `__libc_start_main`, `JVM_*`, `JNI_CreateJavaVM` are not found from the app namespace (`dladdr` null); **0076:** `dlsym(RTLD_DEFAULT, "JNI_GetCreatedJavaVMs")` is `no symbol` and a dlsym on the `libc.so` handle no longer finds `gnu_*`/`JVM_*` (hello-identity ARM64); the `libc.so` handle still answers `JNI_GetCreatedJavaVMs` (libnativehelper's own handle must keep it) | null (JNI_*), but JNI_GetCreatedJavaVMs via libnativehelper | partial (rerun: RTLD_DEFAULT answer equal on both; the `libc.so` handle still answers JNI_GetCreatedJavaVMs) | M | bionic patch (JNI_GetCreatedJavaVMs: item 1; non-global handles) |
| dlopen libjvm.so/libjava.so/libc.so.6/libjavacore.so/libbinder.so/libandroid_runtime.so | not found (namespace) | **fixed 2026-11-14 (0073)**: all six `dlopen failed: library "x" not found` (Android's wording); public libraries (libandroid.so, libnativehelper.so, libdl.so, libc.so) open | not found | match | M/H | bionic patch (done) |
| dlopen libart.so | not found (namespace) | not found | not found | match | M | - |
| libart.so file from maps path (ElfImg) | readable ELF EM_X86_64, .dynsym+.symtab, 12/12 ART symbols, code == memory | **fixed 2026-11-14 (ARM64 native)**: ELF EM_AARCH64, .dynsym+.symtab, 12/12 probed symbols (stubs), code == memory 18/18 | **fixed 2026-11-14 (x86 translation, PC)**: aarch64 build staged for the guest (tools/lib/build-libart-arm64.sh), in maps, symbols found, code == memory 18/18 | match (symbol code is stubs, not ART's) | H for hookers/anti-cheat (SandHook, LSPlant, Pine, ACE) | libart_symbols.c / libart_jni.c |
| libc.so file from maps path | bionic libc, symbols match memory | glibc libc.so.6 file (gnu_get_libc_version) | guest bionic libc at host path, 8/8 match | mismatch ARM64, partial x86 | M | proc view (path) / bionic patch |
| libjavacore.so file | /apex/com.android.art/lib64, JNI_OnLoad | prebuilts path, JNI_OnLoad, match | not in maps | mismatch (rerun: ARM64 and x86 "not in maps", file not readable) | L | proc view (path) |
| uname (libc) | Linux / localhost on a phone / kernel / x86_64 | aarch64, nodename `localhost` (**fixed 2026-11-14, 0077**) | aarch64, desktop nodename (x86 libc uname is bionic's too: 0077 covers it once rebuilt) | partial (rerun: nodename localhost; sysname/release/version/machine are host/ABI) | M | bionic patch (done) |
| raw syscall uname / getuid / openat maps | consistent with libc | **fixed 2026-11-14 (0074)**: `getuid` 10012 = libc, raw openat of maps shows libart and no libjvm, raw status `Uid` 10012, `/system/...` paths mapped; raw uname nodename is `localhost`, statx/newfstatat owner ids the app's, rt_sigprocmask (works on the saved mask, never blocks SIGSYS; test cases added) (libc uname still the host's: bionic item) | consistent (translator filters) | match | H (anti-tamper deliberately uses svc) | seccomp-bpf SIGSYS trap on the guest window (docs/PLAN.md "Raw syscalls of guest code") |
| getauxval(AT_BASE/AT_ENTRY) | linker64 / app_process64 | libdl.so / unnamed | linker64 / app_process64 | mismatch ARM64 | L | launcher |
| ids / groups | app uid, 9997 20xxx 50xxx | 10000, same shape | same | match | H | - |
| cpus / sysconf / affinity | consistent | consistent | consistent | match | L | - |
| cpuinfo | x86 (Waydroid) | real host arm64 (implementer 0x61 Apple) | Pixel 5 profile (0x51/0x805) | partial (ARM64 host CPU) | M (Device Info, CPU-Z) | proc view (device profile cpuinfo on ARM64 too) |
| system properties | 635 props; ro.debuggable/secure, init.svc.*, dalvik.vm.heapsize/heapgrowthlimit, persist.sys.dalvik.vm.lib.2=libart.so, ro.vndk.version, ro.zygote | 180 props; dalvik.vm.* and persist.sys.dalvik.vm.lib.2 set; **dalvik.vm.isa.*.variant, ro.vndk.version, init.svc.* added (pixel5-extra.prop, untested)** | 181, ro.dalvik.vm.native.bridge=libndk_translation.so (AOSP's name; was libberberis_arm64.so) | partial | H (ASTRO reads dalvik.vm.isa/native.bridge; Device Info reads heapsize/heapgrowthlimit) | bionic patch / property defaults (bridge/native/apklayer.prop) |
| ro.build.fingerprint via __system_property_get | "Must use __system_property_read_callback() to read" (value > 92 bytes on Waydroid) | value | value | n/a (Waydroid value unreadable) | L | - |
| file existence: su paths, magisk, qemu_pipe, houdini/ndk_translation | absent | absent | absent | match | H (root/emulator SDKs) | - |
| file existence: Android paths (**FIXED 2026-11-14 for /system/bin/app_process64 and /data/local/tmp; others open**: /system/bin/app_process64, /apex/.../libart.so, core-oj.jar, framework.jar, /data/local/tmp, /dev/binder, /dev/ashmem, /dev/__properties__, /sys/fs/selinux/enforce, /vendor/build.prop, /odm, /product, /system_ext) | present | **absent** | absent (/dev/binderfs present) | mismatch | M (emulator/integrity checks, Unity, Flutter engine paths) | bionic patch / path layer (stub files for the Android tree) |
| file existence: host paths (**FIXED 2026-11-14 for the exact directories /usr, /usr/lib, /lib64, /run/user, /lib/<arch>-linux-gnu; /home stays (the app data dirs are host paths below it and createDirectories stalls on an ENOENT parent) and /tmp (JVM)**: /etc/os-release, /usr/lib, /home, /run/user, /tmp, /lib/<arch>-linux-gnu) | absent | **present** | present | mismatch | M | path layer (hide host roots from the app view) |
| getenv TMPDIR, BOOTCLASSPATH, ANDROID_SOCKET_zygote | /data/user/0/<pkg>/cache, set, set | BOOTCLASSPATH **set** (0077, with DEX2OATBOOTCLASSPATH); TMPDIR and ANDROID_SOCKET_zygote unset | same | partial (rerun: BOOTCLASSPATH equal; TMPDIR and ANDROID_SOCKET_zygote unset) | L | TMPDIR: Android sets it in ActivityThread (a Java shadow, open); the zygote socket has no counterpart |

### 7. ART internals

| probe | Android 13 (Waydroid app) | apklayer ARM64 | apklayer x86 | verdict | sev | fix layer |
|---|---|---|---|---|---|---|
| Unsafe class, addressSize 8, pageSize 4096 | sun.misc.Unsafe | same | same | match | H (Gson, protobuf, Kotlin) | - |
| Unsafe.arrayBaseOffset byte/char/int/Object | 12 | 16 | 16 | mismatch | M (only code that hardcodes 12; offsets are normally queried) | ART emulation (HotSpot's array header is 16 bytes; 12 needs a different object model, or Unsafe answers that only some readers trust) |
| Unsafe.arrayBaseOffset long[] / index scales | 16 / 1,2,4,8,4 | same | same | match | M | - |
| objectFieldOffset of user fields | i=12 j=16 o=8 b=24 (refs first) | i=12 j=16 o=28 b=24 | same | mismatch | L (query-based code adapts) | ART emulation |
| String fields / layout | `count` (len<<1 \| uncompressed), inline chars; `count` 6 for "abc" | value/coder/hash/hashIsZero, no `count` | same | mismatch | M (fast string readers, hookers) | ART emulation / Java shadow |
| Declared fields of Object/Method | [] / [] | same | same | match | L | - |
| Declared fields of String, Class, Thread, Throwable, Executable, Field, ClassLoader, Reference, MethodHandle, Buffer | ART set (e.g. Executable.artMethod, Class.accessFlags/dexCache/objectSize, Field.accessFlags, Buffer.address/_elementSizeShift, Throwable.backtrace/stackState) | OpenJDK 21 set | same | partial (rerun: Executable has artMethod; the rest is OpenJDK 21's) | H for HiddenApiBypass/LSPlant/Pine (Executable.artMethod, Class.methods); M otherwise | ART emulation (synthetic fields backed by an ArtMethod/ArtField table) |
| Executable.artMethod value, ArtMethod size (adjacent delta) | readable, == jmethodID, 32 | absent | absent | match (rerun: `artMethod` readable, adjacent delta 32) | H (HiddenApiBypass in Termux/Aurora; Pine/SandHook/Epic) | ART emulation |
| Class.accessFlags/objectSize/dexCache, Thread.nativePeer, Field.accessFlags | readable (16, 25, set, nonzero, 0x10000008) | absent | absent | mismatch | M | ART emulation |
| Object.shadow$_klass_, Class.status | hidden (NoSuchFieldException with ART text) | NoSuchFieldException (plain) | same | match (message differs) | L | - |
| Hidden API enforcement | setHiddenApiExemptions and meta-reflection blocked; ActivityThread.currentActivityThread allowed | **partly fixed**: the runtime classes' members Android 13 blocks (VMRuntime vmVersion ... classPath, VMDebug.getVmFeatureList) are NoSuchMethodException for app callers of target SDK >= 28; the framework's non-SDK members are not (no hiddenapi-flags database); `setHiddenApiExemptions` stays visible on purpose and records the exemptions (HiddenApiBypass cannot walk ART's ArtMethod array here, so it needs the direct route), meta-reflection is allowed | same | partial | M | Java shadow: GuestHiddenApi; the flags database is open |
| SystemProperties.get("ro.build.version.sdk") | 33 | 37 | 37 | n/a (profile) | L | - |
| identityHashCode | 28 bits (ART lock word) | 31 bits | 31 bits | mismatch | L (hash-range assumptions) | Java shadow (mask to 28 bits; affects HashMap only cosmetically) |
| Exception messages NPE/field NPE/AIOOBE/SIOOBE/CCE/ASE/arith/NSME/UnsatisfiedLinkError | ART texts (see summary) | **mostly fixed**: NPE (invoke/field without the return/field type: HotSpot does not know it), array/null-array/synchronized forms, AIOOBE/SIOOBE `length=3; index=5`, ArrayList `Index: 5, Size: 3` (only IOOBEs built inside java.util.ArrayList; other collections, strings and app-made exceptions keep their text), CCE `A cannot be cast to B`, `divide by zero`, NSME `a.B.m [int, class x.Y]`, ULE `dlopen failed: library "libx.so" not found`; ArrayStoreException keeps HotSpot's class-name-only text (the VM builds it in the aastore stub from the stored object's class only; the array type is not available to a hook: known limit) | same | partial | M | java.lang.ApklayerHooks (jdk-patch) + GuestMessages |
| NegativeArraySize message, Object.toString format, String.intern | "-1", `Class@hex` | same | same | match | L | - |
| StackOverflowError depth | ~65k frames | ~46k | ~21k | partial | L | known limit: depth depends on frame size and the translator, not a single -Xss value (no launcher arg set it to 65k) |
| jmethodID shape | pointer, 8-aligned, low bit 0 | same | same | match | M | - |
| jmethodID adjacent delta / == artMethod | 32 / yes | 8 / n.a. | 8 / n.a. | mismatch | H for hookers | ART emulation |
| ArtMethod at jmethodID: access_flags @4, dex_method_index @8, data_ @16 (native: == JNI fn), entry @24 (in libart) | all as ART | garbage, no match, unresolved | same | mismatch | H for hookers (Pine/SandHook/LSPlant/Frida-java) | ART emulation |
| jfieldID delta | 16 (ArtField) | -736 | -110208 | mismatch | M | ART emulation |
| JNI GetVersion | 0x10006 | 0x10006 | 0x10006 | **match (fixed 2026-11-14)** | M | bridge/native/jni_guest_surface.c (guest callers only; the JDK's libraries keep 0x150000) |
| Reference low bits (local/global/weak) | 1 / 2 / 7 (ART IndirectRef kinds) | 0 / 2 / 1 (HotSpot handles) | same | mismatch | L (GetObjectRefType agrees: 1/2/3) | ART emulation |
| isCopy: string critical/chars/utf, int[] critical/elements | 1,1,1 / 0,1 | same | same | match | M | - |
| isCopy large (1 MB) int[] elements | 0 (large-object space, non-moving) | 1 | 1 | mismatch | M (code relying on direct pointers into big arrays) | ART emulation (non-moving large arrays, PLAN item) |
| JavaVM from GetJavaVM == JNI_OnLoad's | yes | yes | yes | match | H | - |
| JNI_GetCreatedJavaVMs (libnativehelper) | ret 0, count 1, same VM | ret 0, count 1, same VM | ret 0, count 1, same VM | **match (fixed 2026-11-14)** | H | jni_guest_surface.c binds libnativehelper's JniInvocation table to the JVM; Berberis' CHECK holds unchanged, no translator patch |
| JNI_GetDefaultJavaVMInitArgs / JNI_CreateJavaVM (libnativehelper) | -1 / -1 (JNI_ERR) | -1 / -1 | -1 / -1 | **match (fixed 2026-11-14; both aborted or were unbound before)** | M | same |
| JNI_GetCreatedJavaVMs (RTLD_DEFAULT) | no symbol | resolves to libandroid_runtime stub (now count 1, the VM) | no symbol | match (rerun, ARM64 and x86: no symbol) | L | bionic patch 0076 |
| GetEnv on unattached thread | JNI_EDETACHED (-2) | -2 | -2 | match | H | - |
| FindClass(app class) from an attached native thread | **ClassNotFoundException** (system loader) | ClassNotFoundException | ClassNotFoundException | **match (fixed 2026-11-14)** | M | jni_guest_surface.c: guest callers on a thread with no Java frame get the Android answer for app-loader classes; framework/JDK classes and calls from a native method or JNI_OnLoad still resolve through the app loader |
| Attached thread name / group / comm | Thread-3 / main / Thread-3 | Thread-4 / main / comm Thread-4 | same | **match (fixed 2026-11-14; numbering differs)** | L | jni_guest_surface.c (AttachCurrentThread names the native thread, ART's SetThreadName rule) |

### HTTPS in a real app (2026-11-14, UTM ARM64, main fecb75f5 + prebuilts 7304dd9c)

`tests/run tls` passes (29 s). NewPipe (corpus, first launch, Trending over HTTPS through its okhttp) draws no data: the RxCachedThreadScheduler thread dies with `ExceptionInInitializerError` in `sun.security.ssl.NamedGroup.<clinit>` (SunJSSE's `SSLSocketFactoryImpl.createSocket`, called by okhttp's `ConnectPlan.connectTlsEtc`). Cause: `InvalidParameterException: unhandled field class java.security.spec.ECFieldF2m` from Conscrypt's `OpenSSLECGroupContext.getInstance` (via `ECParameters.engineInit`): SunJSSE enumerates the sect* curves through `AlgorithmParameters.getInstance("EC")`, which resolves to Conscrypt's EC parameters. So the app's TLS path runs SunJSSE (an explicit SunJSSE socket factory, not the default AndroidOpenSSL SSLContext) over Conscrypt's EC. Not a one-line fix; in PLAN item 7.

## Corpus evidence (which apps probe what)

The scan was static: strings in the dex and .so files of 44 APKs (Mac Downloads, UTM, x86 PC); the script is listed in the PLAN entry.
- Direct ART probing appears in no APK in the corpus: no `libart.so`, `ArtMethod`, `_ZN3art*` or `art_quick_*` strings. `JNI_GetCreatedJavaVMs` appears only in FCL's `libterracotta`. The ACE/libanogs anti-cheat (PGR) is not on any machine. Its runtime trace (docs/PLAN.md) shows reads of maps, map_files, auxv/cpuinfo, emulator properties, TracerPid, and foreign `/proc/<pid>` status and cmdline.
- `/proc/self/maps` is read in 13 apps: Unity/il2cpp (Crossy Road), Cronet (Meet, GMS, microG), WebView, Gecko (Fennec), Sentry/unwindstack crash handlers, Rust FFI libs, and FCL.
- `dl_iterate_phdr`/`dladdr`/`getauxval` appear in about 25 apps: Unity, Flutter, Hermes, Cronet, WebRTC, Rust std, Crashlytics/Sentry/Breakpad, libc++.
- Other `/proc/self` files:
  - `exe`: Flutter, Rust, Cronet, Gecko, SDL, Crashlytics;
  - `auxv`: Flutter, Go, OpenCV, MediaPipe, Cronet;
  - `mountinfo`/`cgroup`: Rust std (every Rust lib);
  - `attr/current`: Unity;
  - `smaps_rollup`/`pagemap`: WebView;
  - `task/`: Gecko, Meet.
- `java.vm.version`/`java.vm.name` appear in about 30 dex files (OkHttp, Gson, Guava, Kotlin).
- `dalvik.vm.*` properties: ASTRO reads `dalvik.vm.isa.arm*` and `dalvik.vm.native.bridge`; Device Info HW reads `heapsize` and `heapgrowthlimit`.
- Hidden-API bypass through `setHiddenApiExemptions` + `Executable.artMethod` + Unsafe: Termux and Aurora (LSPosed HiddenApiBypass).
- TracerPid: libunity, libjingle (WebRTC), WebView, DisplayLink. Root/emulator strings appear mostly in dex: Crossy Road, WhatsApp, Telegram, GMS/microG, ASTRO, Element.

## Limits
- Waydroid is a container on a desktop kernel. Its kernel, uname, cpuinfo, `/proc/1`, AppArmor attr, environ/auxv EACCES, cgroup and mountinfo differ from a phone. The AOSP value is given where it matters.
- Waydroid is x86_64, so the ARM64-specific Android values were not measured directly: the arm64 apex paths, HWCAP, and `ro.dalvik.vm.native.bridge` on a translated device. For those, AOSP sources and the ARM64 columns are the reference.
- The ARM64 column ran the `~/wt/realapps` runtime tree (main of 2026-11-13 evening plus another agent's uncommitted work in system/popen, not in these probes). A rerun from the `~/apklayer` tree after main 9c9a1c03 started the stack, but the app did not start in that run.
- The probes read memory only through `process_vm_readv`. The JNI_GetCreatedJavaVMs probe runs in a forked child, because the x86 call aborts.

## Java layer after the 2026-11-14 round (items 6-13 of PLAN "Runtime identity")

Raw results: `tests/identity/results/apklayer-{arm64,x86}-java.txt` (the "after"; the files without `-java` are the "before"). Equal to Waydroid's value after normalisation, over the 176 Java/class/message probes: ARM64 52 -> 87, x86 53 -> 88. What is still different is the ABI/device profile (os.arch, http.agent, icu/zlib versions of the bundled libraries, user.name), host paths in the loader (no `/data/app` mapping), `String.class.getClassLoader()` (null; Android: BootClassLoader), the ClassLoader's declared fields (the JDK filters them), thread ids (main is not id 2), the lambda/proxy/MethodHandle class names, the security providers (needs Conscrypt's native library), and ART's own internals (area 7).
