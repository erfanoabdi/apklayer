// dalvik.system.MacApkDexConverter -- internal (Mac.apk-only) helper that
// turns a DEX (a file on disk, or raw bytes from a ByteBuffer) into a .jar of
// JVM bytecode loadable into a live ClassLoader. This is the single shared
// primitive behind every dynamic class-loading entry point that AOSP backs
// with the on-device DEX interpreter:
//
//   * InMemoryDexClassLoader (ByteBuffer dex from packers / DRM / dynamic
//     feature modules; Google Mobile Ads does this on every launch)
//   * DexClassLoader / PathClassLoader / DelegateLastClassLoader /
//     BaseDexClassLoader.addDexPath (a .dex / .jar / .apk plugin path; the
//     Facebook Audience Network SDK and the Firebase C++ SDK do this on every
//     launch)
//   * DexFile.loadDex / new DexFile (legacy + MultiDex bootstrap)
//
// On a real device ART loads DEX directly. Mac.apk has no DEX interpreter, so
// we reuse the EXACT same DEX->JAR converter the runtime uses for the primary
// app dex: com.macapk.dex, called in this JVM. The result is fed to a plain
// URLClassLoader.
//
// WHERE THE JAR LIVES, AND FOR HOW LONG (2026-09-24, docs/dynamic-dex-cache_2026-09-24.md).
// This used to be File.createTempFile("macapk-dyn-", ".jar") in the shared
// per-user TMPDIR with File.deleteOnExit as the only cleanup. deleteOnExit is
// a Java shutdown hook, and no way a user or an app ends this runtime runs
// those: the JVM starts with -Xrs (jvm_loader.cpp), so HotSpot refuses to
// install its TERM/INT/HUP handlers; window close and Cmd-Q end in ::_exit
// (macapk_terminate_now); and an app's own System.exit / Runtime.exit is
// re-pointed by the converter (com.macapk.dex.GuestRepoint) to
// MacApkProcessExit, which ends in that same ::_exit. Only a System.exit made
// by our own framework code, or DestroyJavaVM, still runs hooks.
// Measured 2026-09-24: ~1,100 jars / 3.1 GB in three days, every one of them a
// byte-identical copy of a conversion done on a previous launch.
//
// So there are now two lifetimes, chosen by where the DEX came from:
//
//   FILE-BACKED -> PERSISTENT. A content-addressed, build-scoped cache in
//     <dataDir>/code_cache/macapk-dex/<javaId12>-<sha256>.jar. This is our
//     analogue of the odex/vdex ART persists for an on-disk secondary dex. ART
//     itself puts those in <dexdir>/oat/<isa>/ (oat_file_assistant.cc
//     DexLocationToOdexFilename); we deliberately use code_cache instead,
//     because Context.getCodeCacheDir() promises exactly the lifetime a
//     JAVA_ID-keyed artifact needs -- wiped when the app is upgraded AND when
//     the platform is upgraded -- and Mac.apk already wipes it on update
//     (apk_unpacker.cpp, InstallCore.swift). That placement is a RECONCILE with
//     ART, not a copy of it. A second launch of the same SDK is a hit: no
//     conversion, no write.
//
//   IN-MEMORY -> EPHEMERAL. InMemoryDexClassLoader output is written, opened
//     by the loader, then UNLINKED (the loader keeps reading through its open
//     fd). ART never persists the code of an in-memory dex: VdexFile::WriteToDisk
//     writes such a vdex with has_dex_section=false, i.e. checksums and
//     verifier deps only. Persisting packer-decrypted bytecode would be an
//     artifact a device never produces.
//
// Nothing here depends on deleteOnExit, atexit or any shutdown hook. A kill in
// the middle of a conversion leaves only pid-tagged names, which the next
// process removes exactly (dead pid) or by age.
//
// This class lives in dalvik.system so the loader classes can call it without a
// com.macapk dependency (com.macapk is NOT auto-shipped in the framework jar;
// everything else is). It is NOT part of the public Android API -- apps never
// reference it -- so the exact shape is ours to choose.
//
// NOTE: do not add a nested, anonymous, record or enum-switch class here (lambdas
// are fine): a new class file in the framework jar moves FRAMEWORK_ID, which
// re-converts every installed app's classes.jar.
package dalvik.system;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

final class MacApkDexConverter {

    private MacApkDexConverter() {}

    /** Thrown when the DEX cannot be converted. Callers map this to a faithful
     *  runtime failure (the on-device path would also throw if the DEX could
     *  not be opened). */
    static final class ConvertUnavailableException extends RuntimeException {
        ConvertUnavailableException(String msg) { super(msg); }
        ConvertUnavailableException(String msg, Throwable cause) { super(msg, cause); }
    }

    // ---- cache policy constants ----

    /** Bumped when the cache-key RECIPE changes shape, so an old entry can
     *  never be read under a new meaning. */
    private static final String KEY_TAG = "macapk-dexjar/1";

    /** Current-build entries kept by the LRU bound. ART bounds its anonymous
     *  vdex cache at 8 (oat_file_manager.h kAnonymousVdexCacheSize); ours holds
     *  whole converted jars for FILE-backed dex, and an app with several SDKs
     *  (FAN + Firebase C++ helpers + a plugin) needs more headroom than 8. */
    private static final int MAX_ENTRIES = 32;
    private static final long MAX_BYTES = 256L << 20;

    /** Nothing younger than this is ever evicted by the LRU bound, and a
     *  pid-tagged leftover older than it is reclaimed even when its pid looks
     *  alive (pids get reused, and a bundle can be copied to another Mac). A URL handed out by ANOTHER live
     *  process on the same data dir (the GUI stub plus a harness, say) may not
     *  have been opened yet -- DexFile opens lazily, by path -- and deleting it
     *  would surface as a silent ClassNotFoundException there. The floor is
     *  what makes cross-process pruning safe. */
    private static final long AGE_FLOOR_MS = 24L * 60 * 60 * 1000;

    /** Other-build entries can never hit again (the key embeds JAVA_ID), so
     *  they go as soon as no concurrent other-build process can still be about
     *  to open one it just handed out. */
    private static final long OTHER_BUILD_FLOOR_MS = 10L * 60 * 1000;

    private static final long PID = ProcessHandle.current().pid();

    /** <javaId12>-<sha256 hex>.jar -- a finished, current or other-build entry. */
    private static final Pattern ENTRY =
            Pattern.compile("^([0-9A-Za-z]{12})-([0-9a-f]{64})\\.jar$");
    /** <entry>.<pid>.<threadId>.part.jar, plus Main's own ".tmp" sibling. */
    private static final Pattern PART =
            Pattern.compile("^.+\\.(\\d+)\\.(\\d+)\\.part\\.jar(\\.tmp)?$");
    /** mem.<pid>.<seq>.jar, plus Main's own ".tmp" sibling. */
    private static final Pattern MEM =
            Pattern.compile("^mem\\.(\\d+)\\.(\\d+)\\.jar(\\.tmp)?$");
    /** ${java.io.tmpdir}/macapk-dyn.<pid> -- the no-data-dir fallback. */
    private static final Pattern TMP_RUN_DIR = Pattern.compile("^macapk-dyn\\.(\\d+)$");

    // ---- per-process state ----

    /** Single-flight per cache key. The Firebase C++ SDK builds a NEW
     *  DexClassLoader over the same helper jar for every class it cannot find
     *  (firebase-cpp-sdk util_android.cc FindClassInFiles), so the same key is
     *  asked for repeatedly, sometimes from several threads. A FAILED
     *  conversion is removed before its future completes, so it is never
     *  memoized: a transient failure must not disable an SDK for the process. */
    private static final ConcurrentHashMap<String, CompletableFuture<Path>> INFLIGHT =
            new ConcurrentHashMap<>();

    /** Ephemeral jars handed out and not yet released, as URL external forms.
     *  releaseEphemeral deletes only names in this set, so a caller's own file
     *  that happens to look like ours can never be touched. */
    private static final Set<String> EPHEMERAL = ConcurrentHashMap.newKeySet();
    private static final AtomicLong SEQ = new AtomicLong();

    private static final Set<String> PRUNED_DIRS = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean TMP_PRUNED = new AtomicBoolean();

    /** SHA-256 of this class's own bytes, "" once found to be unreadable. */
    private static volatile String sSelfDigest;
    private static final AtomicBoolean SELF_UNREADABLE_LOGGED = new AtomicBoolean();

    // ---- package-private conversion entry points ----

    /** Convert a DEX/JAR/APK file at {@code input} into a .jar of JVM bytecode
     *  and return its URL. The jar is a PERSISTENT cache entry when the data
     *  dir and build identity are known and every class translated (see the
     *  file header), so it exists by path for as long as a DexFile may need to
     *  re-open it. Otherwise it is ephemeral, exactly like in-memory output.
     *  Never returns null; throws on failure. */
    static URL convertFileToJarUrl(File input) {
        List<byte[]> images;
        try {
            // The same reader Main.run(Path, Path) uses, so the image set --
            // including the base.apk sibling-split merge -- is unchanged.
            images = com.macapk.dex.Main.readDexes(input.toPath());
        } catch (IOException | RuntimeException e) {
            throw new ConvertUnavailableException("cannot read DEX from " + input, e);
        }
        if (images.isEmpty()) {
            throw new ConvertUnavailableException("no classes*.dex in " + input);
        }
        return persistentJar(images, input.getPath());
    }

    /**
     * Convert an array of in-memory DEX buffers into ONE jar and return its URL
     * (as a single-element array, because the caller feeds a URLClassLoader).
     * The jar is EPHEMERAL: the caller must open it and then call
     * {@link #releaseEphemeral} (BaseDexClassLoader.macapkOpenNow does both).
     *
     * <p>ART's {@code InMemoryDexClassLoader(ByteBuffer[])} builds ONE class
     * table over every buffer, so they must be converted TOGETHER. One jar per
     * buffer resolved the same classes -- URLClassLoader is first-on-classpath
     * wins, which matches DEX first-definition wins -- but each buffer got its
     * own converter session, so the class-hierarchy oracle behind the emitted
     * StackMapTable could not see a supertype defined in a SIBLING buffer and
     * collapsed those merges to java/lang/Object. That is exactly the precision
     * loss the oracle was added to fix, reintroduced on the dynamic-loading
     * path. It also re-parsed and re-JITted the converter per buffer, and
     * packers commonly hand over several.
     */
    static URL[] convertBuffersToJarUrls(ByteBuffer[] dexes) {
        List<byte[]> images = new ArrayList<>();
        for (ByteBuffer b : dexes) {
            if (b == null) continue;
            images.add(bufferBytes(b));
        }
        if (images.isEmpty()) {
            throw new ConvertUnavailableException("no DEX buffers to convert");
        }
        return new URL[] { ephemeralJar(images, images.size() + " in-memory dex buffer(s)") };
    }

    /** Unlink every ephemeral jar among {@code urls}. Call only AFTER the
     *  loader holding them has opened them: URLClassPath's JarLoader then
     *  reads through its ZipFile.Source fd, which survives the unlink. Only
     *  names this class handed out are touched. */
    static void releaseEphemeral(URL[] urls) {
        if (urls == null || EPHEMERAL.isEmpty()) return;
        for (URL u : urls) {
            if (u == null || !EPHEMERAL.remove(u.toExternalForm())) continue;
            try {
                Files.deleteIfExists(Paths.get(u.toURI()));
            } catch (Exception ignored) {
                // Left for pruneOnce: the name carries our pid.
            }
        }
    }

    // ---- persistent (file-backed) path ----

    private static URL persistentJar(List<byte[]> images, String what) {
        String javaIdFull = System.getenv("MACAPK_BUILD_JAVA_ID");
        String javaId12 = javaId12(javaIdFull);
        String self = selfDigest();
        String dataDir = VMRuntime.processDataDirectory();
        if (javaId12 == null || self == null || dataDir == null || dataDir.isEmpty()) {
            // An unknown build identity must not persist: a dev binary without
            // build-id.txt is exactly when the converter is changing, and a
            // stale entry would load silently under -Xverify:none. No data dir
            // means an offline tool. Either way, convert for this process only.
            return ephemeralJar(images, what);
        }
        Path dir = cacheDir();
        String name = javaId12 + "-" + cacheKey(javaIdFull, self, images) + ".jar";

        int vanished = 0;
        while (true) {
            CompletableFuture<Path> mine = new CompletableFuture<>();
            CompletableFuture<Path> prev = INFLIGHT.putIfAbsent(name, mine);
            if (prev != null) {
                Path p;
                try {
                    p = prev.join();
                } catch (CompletionException e) {
                    Throwable c = e.getCause() != null ? e.getCause() : e;
                    throw new ConvertUnavailableException(
                            "DEX->JAR conversion failed for " + what, c);
                }
                // The owner's conversion had per-class failures, so it served
                // itself an EPHEMERAL jar that its own loader will unlink.
                // That jar is not ours to share; the owner has already dropped
                // the key, so the next pass converts for this caller.
                if (!p.getFileName().toString().equals(name)) continue;
                // Re-validate on every reuse: the app may have cleared its own
                // getCodeCacheDir(), or another process may have replaced it.
                if (isValidJar(p)) return toUrl(p);
                INFLIGHT.remove(name, prev);
                if (vanished++ == 0) continue;
                throw new ConvertUnavailableException(
                        "cached conversion for " + what + " vanished twice: " + p);
            }
            // We own the key. Convert OUTSIDE any map lock (computeIfAbsent
            // would hold a bin lock for a multi-second conversion).
            try {
                Path p = materialize(dir, name, images, what);
                // An ephemeral result is never memoized: drop the key BEFORE
                // completing, so no later caller can pick this jar up.
                if (!p.getFileName().toString().equals(name)) INFLIGHT.remove(name, mine);
                mine.complete(p);
                return toUrl(p);
            } catch (Throwable t) {
                INFLIGHT.remove(name, mine);
                mine.completeExceptionally(t);
                if (t instanceof Error) throw (Error) t;
                if (t instanceof ConvertUnavailableException) throw (ConvertUnavailableException) t;
                throw new ConvertUnavailableException("DEX->JAR conversion failed for " + what, t);
            }
        }
    }

    /** Produce (or find) the finished entry {@code dir/name}. When the
     *  conversion drops any class, return an EPHEMERAL jar instead (same
     *  directory, mem.<pid>.<seq>.jar, registered for releaseEphemeral) and
     *  leave {@code name} unwritten, so the next launch converts again. */
    private static Path materialize(Path dir, String name, List<byte[]> images, String what) {
        pruneOnce(dir);
        Path fin = dir.resolve(name);
        if (Files.exists(fin)) {
            if (isValidJar(fin)) {
                try {
                    // The LRU touch. Runs once per key per process: later asks
                    // are served from INFLIGHT.
                    Files.setLastModifiedTime(fin, FileTime.fromMillis(System.currentTimeMillis()));
                } catch (IOException ignored) {
                    // A read-only data dir still serves the hit.
                }
                System.err.println("[macapk] dyn dex: " + what + " -> "
                        + name.substring(0, 25) + "... hit");
                return fin;
            }
            // Renamed into place complete, so an unreadable entry was truncated
            // or damaged after the fact. Left alone it would be a PERMANENT
            // silent ClassNotFoundException: URLClassPath drops a jar it cannot
            // open ("Silently ignore for now", URLClassPath.getLoader).
            System.err.println("[macapk] dyn dex: corrupt cache entry " + fin
                    + ", reconverting");
            deleteQuietly(fin);
        }
        // Pid- and thread-tagged, and NOT the final name: Main.convert writes a
        // fixed "<out>.tmp" sibling, which two threads or two processes
        // converting the same key would otherwise share.
        Path part = dir.resolve(name + "." + PID + "." + Thread.currentThread().threadId()
                + ".part.jar");
        long t0 = System.nanoTime();
        int classes;
        Map<String, String> errors = new TreeMap<>();
        Path eph = null;
        try {
            classes = com.macapk.dex.Main.run(images, part, errors);
            if (classes <= 0) {
                throw new ConvertUnavailableException(
                        "DEX->JAR conversion produced no classes for " + what);
            }
            if (!errors.isEmpty()) {
                // A class the converter dropped is simply absent from the jar,
                // and a per-class failure can be transient: DexConverter's
                // parallel drain records ANY worker Throwable, OutOfMemoryError
                // included. Published under the content-addressed name, that
                // jar would pass isValidJar on every later launch and the
                // missing classes would be a silent ClassNotFoundException
                // until JAVA_ID moved. Serve it for this process only; a
                // deterministic failure then costs one conversion per loader construction (the pre-cache cost), and no disk,
                // which is what every launch cost before the cache existed.
                eph = dir.resolve("mem." + PID + "." + SEQ.incrementAndGet() + ".jar");
                Files.move(part, eph, StandardCopyOption.ATOMIC_MOVE);
            } else {
                // REPLACE_EXISTING is safe: the output is deterministic, and a
                // loader holding the replaced inode keeps reading it.
                Files.move(part, fin, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new ConvertUnavailableException("DEX->JAR conversion failed for " + what, e);
        } finally {
            deleteQuietly(part);
            deleteQuietly(Paths.get(part + ".tmp"));
        }
        if (eph != null) {
            EPHEMERAL.add(toUrl(eph).toExternalForm());
            System.err.println("[macapk] dyn dex: " + what + " -> " + eph.getFileName()
                    + ": " + errors.size() + " class(es) failed to translate, not caching"
                    + " (ephemeral " + classes + " classes; the next launch retries)");
            return eph;
        }
        System.err.println("[macapk] dyn dex: " + what + " -> "
                + name.substring(0, 25) + "... converted " + classes + " classes in "
                + ((System.nanoTime() - t0) / 1_000_000) + " ms");
        return fin;
    }

    /**
     * The cache key. The converted bytes are a function of exactly these:
     * the dex images; the converter and the type universe ClassHierarchyOracle
     * reasons in (DexConverter hands it DexConverter.class.getClassLoader(),
     * i.e. the runtime classpath), both of which JAVA_ID covers along with the
     * JDK; this class's own assembly of the image set, which JAVA_ID does NOT
     * cover (build_ids.py CONVERTER_PREFIXES is com/macapk/dex only), hence its
     * own digest; and the DEX2JVM_* converter toggles, several of which change the
     * emitted bytecode -- without them a bisect A/B would silently reuse the
     * other arm's jar. DEX2JVM_THREADS is excluded because the output is
     * byte-identical at any thread count. Content, never path or mtime: an
     * InMemoryDexClassLoader has no path, and the Firebase C++ SDK rewrites
     * its source jars on every launch.
     */
    private static String cacheKey(String javaIdFull, String self, List<byte[]> images) {
        MessageDigest md = sha256();
        md.update((KEY_TAG + "\0" + javaIdFull + "\0" + self + "\0").getBytes(StandardCharsets.UTF_8));
        TreeMap<String, String> env = new TreeMap<>();
        for (Map.Entry<String, String> e : System.getenv().entrySet()) {
            String k = e.getKey();
            if (k.startsWith("DEX2JVM_") && !k.equals("DEX2JVM_THREADS")) {
                env.put(k, e.getValue());
            }
        }
        for (Map.Entry<String, String> e : env.entrySet()) {
            md.update((e.getKey() + "=" + e.getValue() + "\n").getBytes(StandardCharsets.UTF_8));
        }
        md.update((byte) 0);
        byte[] len = new byte[8];
        for (byte[] img : images) {
            long n = img.length;
            for (int i = 7; i >= 0; i--) { len[i] = (byte) n; n >>>= 8; }
            md.update(len);
            md.update(img);
        }
        return hex(md.digest());
    }

    // ---- ephemeral path ----

    private static URL ephemeralJar(List<byte[]> images, String what) {
        Path dir = cacheDir();
        pruneOnce(dir);
        Path out = dir.resolve("mem." + PID + "." + SEQ.incrementAndGet() + ".jar");
        int classes;
        Map<String, String> errors = new TreeMap<>();
        try {
            classes = com.macapk.dex.Main.run(images, out, errors);
        } catch (IOException | RuntimeException e) {
            deleteQuietly(out);
            throw new ConvertUnavailableException("DEX->JAR conversion failed for " + what, e);
        } finally {
            deleteQuietly(Paths.get(out + ".tmp"));
        }
        if (classes <= 0) {
            deleteQuietly(out);
            throw new ConvertUnavailableException(
                    "DEX->JAR conversion produced no classes for " + what);
        }
        URL u = toUrl(out);
        EPHEMERAL.add(u.toExternalForm());
        System.err.println("[macapk] dyn dex: " + what + " -> " + out.getFileName()
                + " ephemeral " + classes + " classes"
                + (errors.isEmpty() ? "" : ", " + errors.size() + " failed to translate"));
        return u;
    }

    // ---- where ----

    /** <dataDir>/code_cache/macapk-dex once Boot has handed the data dir over
     *  (VMRuntime.setProcessDataDirectory, our handleBindApplication). Before
     *  that -- or in an offline tool with no app at all -- a private per-run
     *  dir under java.io.tmpdir, named by pid so a later process can remove it
     *  exactly. */
    private static Path cacheDir() {
        String d = VMRuntime.processDataDirectory();
        Path dir;
        try {
            if (d != null && !d.isEmpty()) {
                dir = Paths.get(d, "code_cache", "macapk-dex");
                Files.createDirectories(dir);
            } else {
                dir = Paths.get(System.getProperty("java.io.tmpdir", "/tmp"), "macapk-dyn." + PID);
                if (!Files.isDirectory(dir)) {
                    Files.createDirectories(dir,
                            PosixFilePermissions.asFileAttribute(
                                    PosixFilePermissions.fromString("rwx------")));
                }
            }
        } catch (IOException | RuntimeException e) {
            throw new ConvertUnavailableException(
                    "cannot create the dynamic-DEX directory for data dir " + d, e);
        }
        return dir;
    }

    /**
     * Once per process per directory, before this process hands out any URL
     * from it. Touches ONLY names this class produces (as ART's own anonymous
     * vdex cleanup touches only IsAnonymousVdexBasename names):
     *   - part / mem leftovers: dead pid, or older than the age floor (a pid
     *     can be reused, and a bundle can be copied to another Mac);
     *   - other-build entries: can never hit again, so they go once older than
     *     OTHER_BUILD_FLOOR_MS (the platform-upgrade wipe getCodeCacheDir
     *     promises);
     *   - current-build entries beyond MAX_ENTRIES / MAX_BYTES: oldest first,
     *     never anything younger than AGE_FLOOR_MS;
     *   - ${java.io.tmpdir}/macapk-dyn.<pid> run dirs whose pid is dead.
     * Best-effort: a failure here never fails a class load.
     */
    private static synchronized void pruneOnce(Path dir) {
        // synchronized: a second thread must not hand out a URL from this dir
        // while the first is still pruning it.
        if (!PRUNED_DIRS.add(dir.toString())) return;
        long now = System.currentTimeMillis();
        String cur = javaId12(System.getenv("MACAPK_BUILD_JAVA_ID"));
        List<Path> current = new ArrayList<>();
        int removed = 0;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                String n = p.getFileName().toString();
                long age = now - mtimeMillis(p);
                Matcher m;
                if ((m = PART.matcher(n)).matches() || (m = MEM.matcher(n)).matches()) {
                    if (!isAlive(Long.parseLong(m.group(1))) || age > AGE_FLOOR_MS) {
                        if (deleteQuietly(p)) removed++;
                    }
                } else if ((m = ENTRY.matcher(n)).matches()) {
                    if (cur != null && m.group(1).equals(cur)) {
                        current.add(p);
                    } else if (cur != null && age > OTHER_BUILD_FLOOR_MS) {
                        if (deleteQuietly(p)) removed++;
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            // Best-effort.
        }
        if (current.size() > MAX_ENTRIES || totalBytes(current) > MAX_BYTES) {
            current.sort((a, b) -> Long.compare(mtimeMillis(a), mtimeMillis(b)));
            int count = current.size();
            long bytes = totalBytes(current);
            for (Path p : current) {
                if (count <= MAX_ENTRIES && bytes <= MAX_BYTES) break;
                if (now - mtimeMillis(p) < AGE_FLOOR_MS) break;   // sorted: all the rest are younger
                long sz = sizeOf(p);
                if (deleteQuietly(p)) {
                    removed++;
                    count--;
                    bytes -= sz;
                }
            }
        }
        if (TMP_PRUNED.compareAndSet(false, true)) {
            Path tmp = Paths.get(System.getProperty("java.io.tmpdir", "/tmp"));
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(tmp, "macapk-dyn.*")) {
                for (Path p : ds) {
                    Matcher m = TMP_RUN_DIR.matcher(p.getFileName().toString());
                    if (!m.matches() || !Files.isDirectory(p)) continue;
                    if (isAlive(Long.parseLong(m.group(1)))) continue;
                    try (DirectoryStream<Path> inner = Files.newDirectoryStream(p)) {
                        for (Path f : inner) deleteQuietly(f);
                    } catch (IOException | RuntimeException ignored) {
                        // fall through to the dir delete, which then fails harmlessly
                    }
                    if (deleteQuietly(p)) removed++;
                }
            } catch (IOException | RuntimeException e) {
                // Best-effort.
            }
        }
        if (removed > 0) {
            System.err.println("[macapk] dyn dex: pruned " + removed + " stale item(s) in " + dir);
        }
    }

    // ---- helpers ----

    /** A finished jar we can actually open. Reads the END/CEN records (about a
     *  millisecond), which is what a truncated file fails. */
    private static boolean isValidJar(Path p) {
        try (ZipFile z = new ZipFile(p.toFile())) {
            return z.size() > 0;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** The first 12 characters of JAVA_ID, or null when it is absent or not a
     *  plain alphanumeric token (it is hex from build_ids.py; anything else is
     *  not an identity we can put in a file name or trust). */
    private static String javaId12(String javaId) {
        if (javaId == null || javaId.length() < 12) return null;
        for (int i = 0; i < javaId.length(); i++) {
            if (!Character.isLetterOrDigit(javaId.charAt(i)) || javaId.charAt(i) > 0x7f) return null;
        }
        return javaId.substring(0, 12);
    }

    private static String selfDigest() {
        String s = sSelfDigest;
        if (s == null) {
            try (InputStream in = MacApkDexConverter.class.getResourceAsStream(
                    "MacApkDexConverter.class")) {
                s = in == null ? "" : hex(sha256().digest(in.readAllBytes()));
            } catch (IOException | RuntimeException e) {
                s = "";
            }
            if (s.isEmpty() && SELF_UNREADABLE_LOGGED.compareAndSet(false, true)) {
                // Every conversion then goes ephemeral, i.e. reconverts on
                // every launch -- the cost this cache exists to remove, so it
                // must not happen silently.
                System.err.println("[macapk] dyn dex: cannot read own class bytes; caching disabled");
            }
            sSelfDigest = s;
        }
        return s.isEmpty() ? null : s;
    }

    private static boolean isAlive(long pid) {
        if (pid == PID) return true;
        try {
            return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
        } catch (RuntimeException e) {
            return true;   // cannot tell: keep the file, the safe direction
        }
    }

    private static long mtimeMillis(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException | RuntimeException e) {
            return System.currentTimeMillis();   // unknown age counts as young
        }
    }

    private static long sizeOf(Path p) {
        try {
            return Files.size(p);
        } catch (IOException | RuntimeException e) {
            return 0L;
        }
    }

    private static long totalBytes(List<Path> ps) {
        long t = 0;
        for (Path p : ps) t += sizeOf(p);
        return t;
    }

    private static boolean deleteQuietly(Path p) {
        try {
            return Files.deleteIfExists(p);
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private static URL toUrl(Path p) {
        try {
            return p.toUri().toURL();
        } catch (MalformedURLException e) {
            throw new ConvertUnavailableException("cannot form URL for converted jar " + p, e);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new ConvertUnavailableException("SHA-256 unavailable", e);
        }
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) {
            sb.append(Character.forDigit((x >> 4) & 0xf, 16)).append(Character.forDigit(x & 0xf, 16));
        }
        return sb.toString();
    }

    /** The bytes a ByteBuffer holds from its current position to its limit,
     *  without disturbing the caller's position. */
    private static byte[] bufferBytes(ByteBuffer src) {
        ByteBuffer dup = src.duplicate();   // do not disturb caller's position
        if (dup.hasArray() && dup.arrayOffset() == 0
                && dup.array().length == dup.remaining()) {
            return dup.array();
        }
        byte[] bytes = new byte[dup.remaining()];
        dup.get(bytes);
        return bytes;
    }
}
