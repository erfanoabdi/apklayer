# Runtime identity gap matrix (handoff items 5-7), measured 2026-11-13/14

This file compares what an app sees on real Android with what it sees under apklayer. It covers Java and class identity, native inspection, and ART internals. Each probe is one `key=value` line from `tests/apps/hello-identity`, the same APK in all three places:

- **Android**: Waydroid, Android 13 (LineageOS 20 `waydroid_x86_64`, userdebug/test-keys, x86_64) on the x86 PC. The probe ran **as a real installed app** (`pm install`, `am start`, target SDK 34, `untrusted_app` sandbox, zygote child). It did not run as standalone dalvikvm. Waydroid has no ARM translation (`ro.dalvik.vm.native.bridge=0`), so it ran the x86_64 build of the same sources.
- **ARM64**: apklayer on UTM (native arm64), userspace binder. Runtime: the `~/wt/realapps` tree (built 2026-11-13 19:36 from current main plus the Termux/Fennec agent's work). The VM had no room for a tree of its own; the `~/apklayer` tree did not start an app at the time.
- **x86**: apklayer on the x86 PC, worktree at 0f42092b (main 811b44c1), userspace binder. The arm64 library is translated by berberis.

Raw results are in `tests/identity/results/{waydroid,apklayer-arm64,apklayer-x86}.txt`. The full joined table comes from `python3 tests/identity/compare.py tests/identity/results/waydroid.txt tests/identity/results/apklayer-{arm64,x86}.txt`. To collect again: `tests/run identity-matrix` (an optional group) or `tests/identity/collect.py apklayer|waydroid OUT` (docstring there).

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

Raw key-by-key equality against Waydroid. Every probe line counts, so ABI and device-profile differences are included.

| area | probes | ARM64 equal | x86 equal |
|---|---|---|---|
| 5 Java/class identity | 161 | 50 | 51 |
| 6 native inspection | 227 | 69 | 97 |
| 7 ART internals | 83 | 21 | 22 |

The judged matrix below groups those keys into 116 probes. A row counts as "match" only if both apklayer columns match.

| area | probes | match | partial (one arch, or cosmetic) | mismatch | n/a |
|---|---|---|---|---|---|
| 5 Java/class identity | 37 | 6 | 4 | 27 | 0 |
| 6 native inspection | 49 | 13 | 7 | 28 | 1 |
| 7 ART internals | 30 | 10 | 2 | 18 | 0 |
| total | 116 | 29 | 13 | 73 | 1 |

### Top mismatches by severity
1. **`JNI_GetCreatedJavaVMs` (H, crash).** On x86 the call aborts the process: berberis' nativehelper trampoline hits `CHECK(*output_size == 1u)` because the host libnativehelper returns 0 VMs. On ARM64 it returns `count=0`. Android returns 1, the app's VM. FCL (`libterracotta`), many SDKs and Unity/Unreal-style engines call it.
2. **`/proc/self/status` read through libc fails on ARM64 (H).** `open`+`read` returns EBADF (`native.proc.status=ERR 9`). The raw syscall works but shows the **host uid 1000**. TracerPid readers (libunity, libjingle, WebView, DisplayLink in the corpus) get an error.
3. **Raw syscalls on native ARM64 bypass every view (H for anti-tamper).** `svc #0` getuid returns 1000 (libc says 10000). `openat(/proc/self/maps)` shows `libjvm.so` and no `libart.so`. x86 translation gets these right (libart, uid 10000), because the translator filters syscalls.
4. **Host JVM inside the process (H).**
   - Maps: 258 `rwx` mappings (Android: 0); paths under `/home`, `/usr/lib`, `/tmp`.
   - Thread names: `VM Thread`, `C2 CompilerThre`, `Signal Dispatch`, `apklayer-*`, `java`; ART has `Signal Catcher`, `HeapTaskDaemon`, `Jit thread pool` and others.
   - `comm=java`; `exe`/`AT_EXECFN` show the JDK `java` on ARM64.
   - fd targets: `Robolectric_all.jar`, the JDK `modules`, apklayer jars. Android's are `/apex/**/javalib/*.jar` and `/dev/binderfs`.
   - cgroup is the desktop's `user.slice/...apklayer-mx1-app...scope`.
5. **JNI functions do not live in libart (H for anti-hook, M otherwise).**
   - `dladdr` of the `JNIEnv` table and functions: Android names `libart.so` (`gJniNativeInterface`). ARM64 names `libandroid_runtime.so` in a Robolectric temp dir. x86 is `unresolved`.
   - `GetVersion` is `0x150000` (Android: `0x10006`).
   - FindClass of an app class from an attached native thread **succeeds**. On Android it fails, because that thread gets the system loader.
6. **Host libraries are dlopen-able (M/H).** On ARM64, `dlopen("libjvm.so")`, `libjava.so`, `libc.so.6`, `libjavacore.so`, `libbinder.so` and `libandroid_runtime.so` all succeed (Android: "not found"). `dladdr(malloc)` reports `/usr/lib/aarch64-linux-gnu/libc.so.6`. x86 translation hides these, and its dladdr shows `/system/lib64/arm64/libc.so`.
7. **libart.so is a name, not a file (H for ElfImg-style resolvers, M otherwise).** The maps line exists, but opening the path fails (ENOENT). There are no `.dynsym`/`.symtab` ART symbols. Waydroid opens it and finds 12/12 symbols whose code matches memory.
8. **ArtMethod/ArtField layout (M; H for hook frameworks and HiddenApiBypass).**
   - Android: `jmethodID == Executable.artMethod`, adjacent delta 32, access flags at +4, native method `data_ == JNI function`, `entry_point` in libart, `jfieldID` delta 16.
   - apklayer: no `artMethod` field, jmethodID delta 8, nothing at the ART offsets.
   - `Class.accessFlags/objectSize/dexCache`, `Thread.nativePeer`, `Field.accessFlags` are all absent (all readable on Android 13 for apps).
9. **Hidden-API enforcement missing (M).**
   - Android 13 blocks these for a target-34 app: `VMRuntime.setHiddenApiExemptions` (and meta-reflection to it), `vmVersion`, `isJavaDebuggable`, `getTargetSdkVersion`, `VMDebug.getVmFeatureList`. apklayer allows them all.
   - `VMRuntime.getTargetSdkVersion` returns 10000 instead of 34.
   - HiddenApiBypass (Termux, Aurora) is the user found in the corpus.
10. **Java messages and classes are HotSpot's (M).**
    - Messages: NPE "Cannot invoke ... because" (ART: "Attempt to invoke virtual method ... on a null object reference"); AIOOBE "Index 5 out of bounds" (ART: "length=3; index=5"); CCE; ArrayStoreException; "/ by zero".
    - Security providers `SUN SunRsaSign ... ApklayerTrust` (Android: `AndroidNSSP AndroidOpenSSL ...`); default SSLContext `SunJSSE`.
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
| System.getProperties keys (host-only sun./jdk./apklayer keys) | none | none | none | match | H | - |
| dalvik.system.* classes present | yes, BootClassLoader | yes, SdkSandboxClassLoader | same | partial (wrong loader) | M | Java shadow (boot-parent identity) |
| java.lang.Daemons, VMClassLoader, BootClassLoader, AndroidHardcodedSystemProperties, invoke.Transformers, sun.misc.Cleaner, sun.reflect.Reflection, jdk.internal.misc.SharedSecrets | present | absent | absent | mismatch | M | Java shadow (libcore overlay classes) |
| HotSpot-only classes: Module, StackWalker, ProcessHandle, Record, LambdaForm, DirectMethodHandle, StringConcatFactory, sun.misc.Signal, jdk.internal.loader.ClassLoaders, java.lang.management.*, com.sun.management.*, sun.management.* | absent | **fixed for Class.forName** (AppClassLoader.jdkOnly; the JVM's own linking still reaches them); java.lang.Module etc. exist as classes | same | match (by Class.forName) | M | - |
| org.robolectric.*, apklayer.host.* | absent | **fixed**: absent for Class.forName and loadClass through the app loader (the converter's own targets stay loadable, but not by Class.forName) | same | match | H | - |
| org.apache.http.HttpResponse (no uses-library) | absent | present | present | mismatch | L | Java shadow |
| de.robv.android.xposed.*, com.saurik.substrate.* | absent | absent | absent | match | M | - |
| ClassNotFoundException message | `no.such.Klass`, cause CNFE | cause null | cause null | partial | L | Java shadow |
| Loader chain | PathClassLoader -> BootClassLoader -> null | **fixed**: PathClassLoader -> java.lang.BootClassLoader -> null (a java.base class from the jdk-patch that asks the sandbox) | same | match | H | - |
| ClassLoader.getSystemClassLoader | PathClassLoader[DexPathList[[directory "."],...]] -> BootClassLoader | **fixed**: the app's PathClassLoader (converter route GuestLoaders, converter ABI 9); its toString is the app's, not `directory "."` | same | partial | M | - |
| String.class / Activity.class getClassLoader | BootClassLoader instance (sic: Android 13 returns the object for framework classes) | null / SdkSandboxClassLoader (open: the loader of a JVM class is null, a framework class's is the sandbox) | same | mismatch | M | native Class.getClassLoader cannot be redirected |
| VMStack.getCallingClassLoader | PathClassLoader | null | null | mismatch | L | Java shadow |
| getResource("java/lang/Object.class") on parent | null | **fixed** on the BootClassLoader parent (null); `Object.class.getResource` still gives jrt: | same | partial | M | Java shadow (Class.getResource) |
| app loader toString / DexPathList | `[zip file "/data/app/<APP>/base.apk"], nativeLibraryDirectories=[/data/app/<APP>/lib/x86_64, .../base.apk!/lib/x86_64, /system/lib64, ...]` | **fixed in shape**: `[zip file "<host apk path>"], nativeLibraryDirectories=[<host lib dir>, <apk>!/lib/<abi>, /system/lib64, /system/system_ext/lib64, /system/product/lib64]` | same | partial (host paths: no /data/app mapping) | H | path layer (present /data/app/..., open) |
| DexPathList.dexElements | 1 element, DexFile with long[2] mCookie | **fixed**: 1 element, a DexFile (never opened) with a long[2] mCookie | same | match | H | - |
| findLibrary / nativeLibraryDirectories path | /data/app/<APP>/lib/x86_64/libhello.so | host path (unchanged) | same | mismatch (host path) | M | bionic patch / path layer (present `/data/app/...`) |
| BaseDexClassLoader / ClassLoader declared fields | [pathList] / [parent] (hidden-API filtered) | **fixed for BaseDexClassLoader**: [pathList] (Class.getDeclaredFields hook for app callers; `__robo_data__` removed everywhere); ClassLoader gives [] (the JDK filters its fields) | same | partial | M | - |
| Exception stack depth/frames in onCreate | 22 frames, ActivityThread...ZygoteInit.main | **fixed**: 22 frames, 0 host frames, ZygoteInit.main at the bottom (GuestTraces; `-Dapklayer.rawtraces=true` shows the JVM's raw trace; a trace that starts in Robolectric/apklayer code stays raw) | same | match (line numbers are the framework's) | H | - |
| Thread.getStackTrace first frame | dalvik.system.VMStack.getThreadStackTrace, java.lang.Thread.getStackTrace | **fixed**: same two frames | same | match | M | - |
| Method.invoke of a package-private method | works (frame `java.lang.reflect.Method.invoke(Native Method)`) | **fixed**: works (Reflect.invokeAs checks access for the app's class, calls through a private accessible copy); frame `Method.invoke(Native Method)` | same | match | H | - |
| Thread.getAllStackTraces names | main, FinalizerDaemon, FinalizerWatchdogDaemon, HeapTaskDaemon, ReferenceQueueDaemon, Signal Catcher, Jit thread pool, Profile Saver, RenderThread, binder:* | **partly fixed**: main, FinalizerDaemon, ReferenceQueueDaemon, Signal Catcher, binder:*; host threads hidden from the list (the real JVM threads keep their names in /proc: native views) | same | partial | M | - |
| Main thread | name=main id=2 group=main | **fixed**: name=main group=main; id=16 (not 2) | id=15 | partial (thread id) | H | - |
| VMRuntime vmVersion/isJavaDebuggable/getTargetSdkVersion | blocked (NoSuchMethodException: hidden API) | **fixed**: blocked for target SDK >= 28 apps (GuestHiddenApi); getTargetSdkVersion is the app's manifest target (VMRuntime shadow, set at start); `setHiddenApiExemptions` stays visible (HiddenApiBypass needs it; see PLAN) | same | partial | M | - |
| VMDebug.getVmFeatureList, Debug.getRuntimeStats | blocked / art.gc.* keys | **fixed**: getVmFeatureList blocked; getRuntimeStats still [] | same | partial | L | Java shadow |
| Runtime.version() | NoSuchMethodException (API 33) | **fixed**: NoSuchMethodException from Class.getMethod for app callers (libcore removed it) | same | match | M | - |
| Runtime.availableProcessors / maxMemory | 4 / 192 MB (heapgrowthlimit) | **fixed**: maxMemory 256 MB (dalvik.vm.heapgrowthlimit of the Pixel 5 profile; the converter routes Runtime.maxMemory to GuestLoaders.maxMemory, the real -Xmx is not capped); cpus 10 | 4 / 256 MB | partial | M | - |
| Security providers / SSLContext default | AndroidNSSP AndroidOpenSSL CertPathProvider AndroidKeyStoreBCWorkaround BC HarmonyJSSE AndroidKeyStore / AndroidOpenSSL | unchanged: ApklayerTrust SUN SunRsaSign SunEC SunJSSE ... BC ... / SunJSSE. **Not fixed**: Android's AndroidOpenSSL is the framework's Conscrypt, whose native libjavacrypto.so is not staged here (not in the prebuilts, not in `inst`); presenting its names over the JDK providers would fake crypto | same | mismatch | H | needs libjavacrypto + Conscrypt natives staged, then a provider swap with TLS tests |
| MethodHandle class, Proxy class name, Object.hashCode/getClass toString | MethodHandleImpl, `$Proxy2`, not native | DirectMethodHandle, `jdk.proxy4.$Proxy45`, `native` | same | mismatch | L | Java shadow |
| Lambda class name, mapLibraryName, charset | `$$ExternalSyntheticLambda`, libx.so, UTF-8 | same | same | match | L | - |
| System.getenv keys | ANDROID_*, ASEC_MOUNTPOINT, BOOTCLASSPATH, DEX2OATBOOTCLASSPATH, SYSTEMSERVERCLASSPATH, ANDROID_SOCKET_usap_pool_primary, PATH, ... | ANDROID_* subset, no *CLASSPATH | same | partial | L | launcher (initial environment) |

### 6. Native inspection

| probe | Android 13 (Waydroid app) | apklayer ARM64 | apklayer x86 | verdict | sev | fix layer |
|---|---|---|---|---|---|---|
| maps: libart.so line | present | present (JDK mapping renamed) | present | match | H (13 apps, ACE) | - |
| maps: other runtime markers (bionic libc/linker64 apex paths, app_process64, boot.art/oat, boot-framework, .vdex/.odex, framework.jar, core-oj.jar, [anon:dalvik-*], jit-code-cache, scudo, /dev/__properties__) | all present | only libart, libandroid_runtime, base.apk | + apex linker64, [anon:.bss] | mismatch | H (Unity/il2cpp, Cronet, Sentry, ACE read maps) | proc view (synthesize ART/zygote names; dalvik anon names via prctl PR_SET_VMA) |
| maps: host markers (/home, /usr/lib, /tmp, ld-linux, libglib/wayland) | none | /usr/lib /home /tmp ld-linux libglib libwayland | /home /tmp | mismatch | H | proc view (path remap of runtime files to /system, /apex, /data/app) |
| maps: rwx mappings | 0 | 258 | 258 | mismatch | M (anti-tamper flags W+X as injected code) | proc view (report r-x for the JVM code cache) or launcher (a HotSpot W^X code cache, to be investigated) |
| maps: first exec mapping, bionic libc path | `[anon_shmem:dalvik-zygote-jit-code-cache]`, /apex/com.android.runtime/lib64/bionic/libc.so | libhello / /usr/lib/aarch64-linux-gnu/libc.so.6 (glibc file) | android-root/system/lib64/arm64/libc.so (host path) | mismatch | H | proc view (bionic libc at its apex path) |
| smaps field names, smaps_rollup | kernel's | same | same | match | L | - |
| map_files | readable, no JDK entries | readable, no JDK | readable | match (filtered) | M (ACE) | - |
| /proc/self/status via libc | readable: Name hello.identity, Uid app, TracerPid 0, Seccomp 2, NoNewPrivs 1, CapBnd 0 | **ERR EBADF** | readable; Name **java**, CapBnd 000001ffffffffff, Seccomp 0, NoNewPrivs 0 | mismatch (ARM64 bug) | H (TracerPid: libunity, libjingle, WebView) | proc view (status snapshot read fails; Name from cmdline) |
| /proc/self/status via raw syscall | Uid app | **Uid 1000** (host) | Uid 10000 | mismatch on ARM64 | H for anti-tamper | proc view needs a seccomp/ptrace or syscall-user-dispatch layer on native ARM64 |
| cmdline | `hello.identity` | `hello.identity` (padded) | same | match | H | - |
| comm / prctl(PR_GET_NAME) / stat field 2 | hello.identity | java | java | mismatch | M (Crashlytics, Breakpad use comm) | launcher (prctl(PR_SET_NAME) on the app main thread) |
| cgroup | `0::/` (Waydroid; phone: `0::/uid_10xxx/pid_N`) | desktop `user.slice/.../apklayer-mx1-app-hello.identity-N.scope` | same | mismatch | M (Rust std reads it, every Rust lib) | proc view (Android-shaped cgroup line) |
| attr/current (SELinux) | AppArmor (Waydroid); phone: `u:r:untrusted_app:s0:c...` | `unconfined` | `u:r:untrusted_app:s0:c0,c256,c512,c768` | partial (x86 OK) | M (Unity reads it) | proc view (ARM64 preload like the translator's 0018) |
| loginuid / wchan | 4294967295 / 0 | 1000 / futex_do_wait | same | mismatch | L | proc view |
| /proc/1/cmdline | /system/bin/init second_stage (container); phone: EACCES/ENOENT | ENOENT | ENOENT | match (phone) | M | - |
| /proc/version, uname | host kernel (Waydroid) | host kernel, nodename `UTM-Virtual-Machine` | nodename `Erfan-Hi10-Max` | partial (nodename leaks desktop name; phone: `localhost`) | M (uname in dex of Telegram, Unciv, Device Info) | bionic patch (uname nodename/version) + translator |
| /proc/net/unix | readable (Waydroid) ; phone: EACCES since Android 10 | readable | EACCES | partial | L | proc view |
| environ, auxv (own) | EACCES (Waydroid AppArmor); phone: readable | readable, 11 keys | readable | n/a (reference differs) | M (Flutter/Go/Cronet read auxv) | - |
| auxv HWCAP/HWCAP2/PLATFORM vs getauxval | consistent | consistent, platform aarch64 | consistent, AT_PLATFORM **null**, HWCAP2 0 | partial (x86) | M (Go runtime, OpenCV, BoringSSL CPU dispatch) | translator (AT_PLATFORM string, HWCAP2) |
| exe link / AT_EXECFN | /system/bin/app_process64 | **…/native/jdk/bin/java** | /system/bin/arm64/app_process64 | mismatch ARM64; x86 near (arm64 subdir) | H (Flutter, Rust, Cronet, Crashlytics read /proc/self/exe) | proc view (exe readlink) + launcher (AT_EXECFN) |
| AT_SECURE / prctl dumpable / no_new_privs / seccomp | 1 / 0 / 1 / 2 | 0 / 1 / 0 / 0 | same | mismatch | M (anti-debug: dumpable 1 permits ptrace attach) | launcher (PR_SET_DUMPABLE 0, NO_NEW_PRIVS, a seccomp filter like Android's app policy) |
| ppid / parent cmdline / pid==tid | zygote64 / main thread is pid | apklayer CLI `.../b/cli/apklayer run` ; main thread != pid | same | mismatch | M (zygote-child checks; Android main thread tid == pid) | launcher (run the app's main Looper on the process main thread; parent named zygote64) |
| fd targets | /apex/**/javalib/*.jar, /system/framework/*.jar, /dev/binderfs/*, /dev/ashmem, sockets | Robolectric_all.jar, jdk/lib/modules, apklayer jars, host paths | same | mismatch | H (fd listing in dex of most apps via ParcelFileDescriptor; Fennec/xul enumerates fds) | proc view (fd readlink remap/hide) |
| mountinfo | Android mounts (/system, /vendor, /apex/*, /data) | desktop mounts (/run, /sys/fs/cgroup, /dev/shm ...) | same | mismatch | M (Rust std, Gecko read mountinfo) | proc view (synthetic Android mountinfo) |
| task/*/comm | ART daemons, binder:N_N, RenderThread, perfetto_hprof_ | C2 CompilerThre, VM Thread, Signal Dispatch, java, apklayer-*, SDK NN Main Thr | same | mismatch | H (libmozglue, WebView, anti-cheat enumerate threads) | launcher/JVM (rename HotSpot threads via prctl) + proc view |
| ns links | readable | same | same | match | L | - |
| limits | kernel table | same shape | same | match | L | - |
| dl_iterate_phdr modules | 320 Android modules, all EM_X86_64, sonames | 330 incl. host glibc/JVM modules, **77 without soname**, host paths | 8 guest modules only (app_process64, linker64, vdso, libc++, libc, libm, libdl, libhello) | mismatch | H (Unity, Flutter, Cronet, Rust, Crashlytics unwinders) | bionic patch (ARM64: list only Android modules at Android paths) / translator (x86: expose the guest-side ART/framework modules) |
| foreign-machine modules in phdr list | 0 | 0 | 0 | match | M | - |
| dladdr(open/pthread_create/dl_iterate_phdr) | libc.so/libdl.so at /apex/com.android.runtime/lib64/bionic | `bionic_open` in libc_bio.so.0, libpthread_bio.so.0 (host names, `bionic_` prefixes) | /system/lib64/arm64/libc.so | mismatch ARM64; x86 near | H (crash handlers symbolize; hook frameworks verify libc) | bionic patch (dladdr names) |
| dladdr(malloc/__cxa_finalize) | bionic libc | glibc `/usr/lib/aarch64-linux-gnu/libc.so.6` | bionic libc (guest) | mismatch ARM64 | H | bionic patch |
| dlsym(RTLD_DEFAULT, JNI_GetCreatedJavaVMs/JNI_CreateJavaVM/__libc_start_main/gnu_get_libc_version) | null | resolved (libandroid_runtime, glibc) | null (JNI_*), but JNI_GetCreatedJavaVMs via libnativehelper | mismatch ARM64 | M (`gnu_get_libc_version` = glibc detected) | bionic patch (global symbol scope) |
| dlopen libjvm.so/libjava.so/libc.so.6/libjavacore.so/libbinder.so/libandroid_runtime.so | not found (namespace) | **loaded** | not found | mismatch ARM64 | M/H | bionic patch (linker namespace for app) |
| dlopen libart.so | not found (namespace) | not found | not found | match | M | - |
| libart.so file from maps path (ElfImg) | readable ELF EM_X86_64, .dynsym+.symtab, 12/12 ART symbols, code == memory | **ENOENT** | ENOENT | mismatch | H for hookers/anti-cheat (SandHook, LSPlant, Pine, ACE) | ART emulation (a real libart.so ELF at the path whose exported symbols map onto shims) |
| libc.so file from maps path | bionic libc, symbols match memory | glibc libc.so.6 file (gnu_get_libc_version) | guest bionic libc at host path, 8/8 match | mismatch ARM64, partial x86 | M | proc view (path) / bionic patch |
| libjavacore.so file | /apex/com.android.art/lib64, JNI_OnLoad | prebuilts path, JNI_OnLoad, match | not in maps | partial | L | proc view (path) |
| uname (libc) | Linux / localhost on a phone / kernel / x86_64 | aarch64, desktop nodename | aarch64, desktop nodename | partial | M | bionic patch / translator |
| raw syscall uname / getuid / openat maps | consistent with libc | uname ok; **getuid 1000**; maps shows **libjvm, no libart** | consistent (translator filters) | mismatch ARM64 | H (anti-tamper deliberately uses svc) | native ARM64 needs syscall interception (seccomp-bpf SIGSYS trap or SUD) |
| getauxval(AT_BASE/AT_ENTRY) | linker64 / app_process64 | libdl.so / unnamed | linker64 / app_process64 | mismatch ARM64 | L | launcher |
| ids / groups | app uid, 9997 20xxx 50xxx | 10000, same shape | same | match | H | - |
| cpus / sysconf / affinity | consistent | consistent | consistent | match | L | - |
| cpuinfo | x86 (Waydroid) | real host arm64 (implementer 0x61 Apple) | Pixel 5 profile (0x51/0x805) | partial (ARM64 host CPU) | M (Device Info, CPU-Z) | proc view (device profile cpuinfo on ARM64 too) |
| system properties | 635 props; ro.debuggable/secure, init.svc.*, dalvik.vm.heapsize/heapgrowthlimit, persist.sys.dalvik.vm.lib.2=libart.so, ro.vndk.version, ro.zygote | 180 props; **no dalvik.vm.*, persist.sys.dalvik.vm.lib.2, ro.vndk.version, init.svc.adbd** | 181, ro.dalvik.vm.native.bridge=libberberis_arm64.so (leaks translator name; AOSP: 0 or libndk_translation.so/libhoudini.so) | mismatch | H (ASTRO reads dalvik.vm.isa/native.bridge; Device Info reads heapsize/heapgrowthlimit) | bionic patch / property defaults (bridge/native/apklayer.prop) |
| ro.build.fingerprint via __system_property_get | "Must use __system_property_read_callback() to read" (value > 92 bytes on Waydroid) | value | value | match (device-dependent) | L | - |
| file existence: su paths, magisk, qemu_pipe, houdini/ndk_translation | absent | absent | absent | match | H (root/emulator SDKs) | - |
| file existence: Android paths (/system/bin/app_process64, /apex/.../libart.so, core-oj.jar, framework.jar, /data/local/tmp, /dev/binder, /dev/ashmem, /dev/__properties__, /sys/fs/selinux/enforce, /vendor/build.prop, /odm, /product, /system_ext) | present | **absent** | absent (/dev/binderfs present) | mismatch | M (emulator/integrity checks, Unity, Flutter engine paths) | bionic patch / path layer (stub files for the Android tree) |
| file existence: host paths (/etc/os-release, /usr/lib, /home, /run/user, /tmp, /lib/<arch>-linux-gnu) | absent | **present** | present | mismatch | M | path layer (hide host roots from the app view) |
| getenv TMPDIR, BOOTCLASSPATH, ANDROID_SOCKET_zygote | /data/user/0/<pkg>/cache, set, set | unset | unset | mismatch | L | launcher / Java shadow (Android sets TMPDIR in ActivityThread) |

### 7. ART internals

| probe | Android 13 (Waydroid app) | apklayer ARM64 | apklayer x86 | verdict | sev | fix layer |
|---|---|---|---|---|---|---|
| Unsafe class, addressSize 8, pageSize 4096 | sun.misc.Unsafe | same | same | match | H (Gson, protobuf, Kotlin) | - |
| Unsafe.arrayBaseOffset byte/char/int/Object | 12 | 16 | 16 | mismatch | M (only code that hardcodes 12; offsets are normally queried) | ART emulation (HotSpot's array header is 16 bytes; 12 needs a different object model, or Unsafe answers that only some readers trust) |
| Unsafe.arrayBaseOffset long[] / index scales | 16 / 1,2,4,8,4 | same | same | match | M | - |
| objectFieldOffset of user fields | i=12 j=16 o=8 b=24 (refs first) | i=12 j=16 o=28 b=24 | same | mismatch | L (query-based code adapts) | ART emulation |
| String fields / layout | `count` (len<<1 \| uncompressed), inline chars; `count` 6 for "abc" | value/coder/hash/hashIsZero, no `count` | same | mismatch | M (fast string readers, hookers) | ART emulation / Java shadow |
| Declared fields of Object/Method | [] / [] | same | same | match | L | - |
| Declared fields of String, Class, Thread, Throwable, Executable, Field, ClassLoader, Reference, MethodHandle, Buffer | ART set (e.g. Executable.artMethod, Class.accessFlags/dexCache/objectSize, Field.accessFlags, Buffer.address/_elementSizeShift, Throwable.backtrace/stackState) | OpenJDK 21 set | same | mismatch | H for HiddenApiBypass/LSPlant/Pine (Executable.artMethod, Class.methods); M otherwise | ART emulation (synthetic fields backed by an ArtMethod/ArtField table) |
| Executable.artMethod value, ArtMethod size (adjacent delta) | readable, == jmethodID, 32 | absent | absent | mismatch | H (HiddenApiBypass in Termux/Aurora; Pine/SandHook/Epic) | ART emulation |
| Class.accessFlags/objectSize/dexCache, Thread.nativePeer, Field.accessFlags | readable (16, 25, set, nonzero, 0x10000008) | absent | absent | mismatch | M | ART emulation |
| Object.shadow$_klass_, Class.status | hidden (NoSuchFieldException with ART text) | NoSuchFieldException (plain) | same | match (message differs) | L | - |
| Hidden API enforcement | setHiddenApiExemptions and meta-reflection blocked; ActivityThread.currentActivityThread allowed | **partly fixed**: the runtime classes' members Android 13 blocks (VMRuntime vmVersion ... classPath, VMDebug.getVmFeatureList) are NoSuchMethodException for app callers of target SDK >= 28; the framework's non-SDK members are not (no hiddenapi-flags database); `setHiddenApiExemptions` stays visible on purpose and records the exemptions (HiddenApiBypass cannot walk ART's ArtMethod array here, so it needs the direct route), meta-reflection is allowed | same | partial | M | Java shadow: GuestHiddenApi; the flags database is open |
| SystemProperties.get("ro.build.version.sdk") | 33 | 37 | 37 | match (profile) | L | - |
| identityHashCode | 28 bits (ART lock word) | 31 bits | 31 bits | mismatch | L (hash-range assumptions) | Java shadow (mask to 28 bits; affects HashMap only cosmetically) |
| Exception messages NPE/field NPE/AIOOBE/SIOOBE/CCE/ASE/arith/NSME/UnsatisfiedLinkError | ART texts (see summary) | **mostly fixed**: NPE (invoke/field without the return/field type: HotSpot does not know it), array/null-array/synchronized forms, AIOOBE/SIOOBE `length=3; index=5`, CCE `A cannot be cast to B`, `divide by zero`, NSME `a.B.m [int, class x.Y]`, ULE `dlopen failed: library "libx.so" not found`; ArrayStoreException keeps HotSpot's class-name-only text | same | partial | M | java.lang.ApklayerHooks (jdk-patch) + GuestMessages |
| NegativeArraySize message, Object.toString format, String.intern | "-1", `Class@hex` | same | same | match | L | - |
| StackOverflowError depth | ~65k frames | ~46k | ~21k | partial | L | launcher (-Xss) |
| jmethodID shape | pointer, 8-aligned, low bit 0 | same | same | match | M | - |
| jmethodID adjacent delta / == artMethod | 32 / yes | 8 / n.a. | 8 / n.a. | mismatch | H for hookers | ART emulation |
| ArtMethod at jmethodID: access_flags @4, dex_method_index @8, data_ @16 (native: == JNI fn), entry @24 (in libart) | all as ART | garbage, no match, unresolved | same | mismatch | H for hookers (Pine/SandHook/LSPlant/Frida-java) | ART emulation |
| jfieldID delta | 16 (ArtField) | -736 | -110208 | mismatch | M | ART emulation |
| JNI GetVersion | 0x10006 | 0x150000 | 0x150000 | mismatch | M (some libs refuse > 1.6, or detect JDK) | translator/bionic patch (JNIEnv wrapper GetVersion) |
| Reference low bits (local/global/weak) | 1 / 2 / 7 (ART IndirectRef kinds) | 0 / 2 / 1 (HotSpot handles) | same | mismatch | L (GetObjectRefType agrees: 1/2/3) | ART emulation |
| isCopy: string critical/chars/utf, int[] critical/elements | 1,1,1 / 0,1 | same | same | match | M | - |
| isCopy large (1 MB) int[] elements | 0 (large-object space, non-moving) | 1 | 1 | mismatch | M (code relying on direct pointers into big arrays) | ART emulation (non-moving large arrays, PLAN item) |
| JavaVM from GetJavaVM == JNI_OnLoad's | yes | yes | yes | match | H | - |
| JNI_GetCreatedJavaVMs (libnativehelper) | ret 0, count 1, same VM | **count 0** | **process abort** (berberis CHECK) | mismatch | **H** | translator (nativehelper trampoline) + host libnativehelper stub (return the JVM) |
| JNI_GetCreatedJavaVMs (RTLD_DEFAULT) | no symbol | resolves to libandroid_runtime stub, count 0 | no symbol | mismatch ARM64 | M | bionic patch |
| GetEnv on unattached thread | JNI_EDETACHED (-2) | -2 | -2 | match | H | - |
| FindClass(app class) from an attached native thread | **ClassNotFoundException** (system loader) | found | found | mismatch | M (apps that work on apklayer would fail on Android: harmless; detectors use it) | translator/Java shadow (use the system loader without a Java frame) |
| Attached thread name / group / comm | Thread-3 / main / Thread-3 | Thread-4 / main / comm `hello.identity` | same | partial (comm not set) | L | bionic patch (AttachCurrentThread sets the native thread name) |

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
