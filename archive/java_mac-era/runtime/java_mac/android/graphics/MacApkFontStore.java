// MacApkFontStore -- the stable, immutable FILE behind every font we load.
//
// WHY THIS EXISTS. AWT can build a java.awt.Font from bytes two ways, and the
// obvious one leaks. Font.createFont(int, InputStream) copies the bytes to a
// fresh $TMPDIR/+~JF<random>.tmp (JDK 21 java.desktop/java/awt/Font.java:1113,
// in createFont0) and registers that copy for deletion by exactly two things
// (sun/font/SunFontManager.java:2244-2290, isCopy=true): a GC Disposer on the
// Font2D, which our static font caches mostly pin, and the JDK "FileCloser"
// SHUTDOWN HOOK. The hook never runs in Mac.apk: the runtime boots HotSpot with
// -Xrs (runtime/src/jvm_loader.cpp), so HotSpot refuses HUP/INT/TERM
// (jvm_posix.cpp JVM_RegisterSignal) and Java never owns SIGTERM, and every
// real exit is _exit (cocoa_bridge.mm macapk_terminate_now), Runtime.halt
// (MacApkProcessExit) or SIGKILL (the harnesses). Measured 2026-09-24: 220
// orphaned copies, 207 MB, in ~90 minutes of sweep -- 1-8 per launch plus one
// per uncached app-font lookup (4.4 MB each for Google Calculator's res font).
//
// Font.createFont(int, File) passes isCopy=false (Font.java:1243-1248 ->
// :636-644): no copy, no Disposer, no hook. But AWT then reads that file BY PATH
// for as long as the Font2D is reachable -- TrueTypeFont.open() re-opens
// platName whenever its channel is closed (TrueTypeFont.java:250-256), which
// happens after channel-pool eviction (SunFontManager CHANNELPOOLSIZE = 20) and
// after a ClosedByInterruptException on a glyph read. So the backing file must
// stay at its path, with the same bytes, for the life of the process. That
// rules out unlink-after-open, a per-launch temp file, AND an app's own font
// file (AOSP mmaps it, so an app may delete it right after
// Typeface.createFromFile).
//
// THE AOSP SHAPE this mirrors: one stable, read-only backing per distinct font.
// SystemFonts.mmap maps /system/fonts READ_ONLY (frameworks/base/graphics/java/
// android/graphics/fonts/SystemFonts.java:93-97) and fonts.Font.Builder mmaps a
// File or an APK entry (fonts/Font.java:137-146, :264-297), falling back to
// anonymous allocateDirect memory -- never a temp file. Here the backing is a
// CONTENT-ADDRESSED file, <sha256>.ttf, so:
//   - its bytes can never change under a reader (a new font is a new name), in
//     this process or in any concurrent one (GUI, staged runtime, dev binary);
//   - a warm launch writes nothing;
//   - disk use is bounded by DISTINCT font contents, not by launches x calls;
//   - a SIGKILL at any instant leaves nothing, a complete file, or a *.part
//     that the next process purges.
//
// WHERE. ~/Library/Caches/MacApk/fonts -- "MacApk", NOT "Mac.apk": no path of
// ours may contain the substring ".apk", because guests that locate their APK
// by splitting a path at the first ".apk" split OURS instead (Among Us,
// v1.0.1634; see runtime/src/apk_unpacker.cpp "'.apk' MUST NOT APPEAR"). The
// same rule and resolution order as ResourceIndex.macapkFwViewCacheDir:
// user.home first (so a gate can isolate it with -Duser.home), then $HOME. It is
// deliberately OUTSIDE every app's getCacheDir(), so no app can wipe it while
// another is reading, and no cleaner may touch it while a runtime is live.
package android.graphics;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

final class MacApkFontStore {
    private MacApkFontStore() {}

    /** A staging file older than this cannot belong to a live writer: a write
     *  of the largest font we have seen (4.4 MB) takes milliseconds. */
    private static final long STALE_PART_MS = 60L * 60L * 1000L;

    private static volatile File sDir;
    private static final AtomicBoolean sPurged = new AtomicBoolean();

    /** sha256 hex -> store file already byte-verified by THIS process. Once a
     *  file is verified its name guarantees its content for every later reader,
     *  so a second load of the same face costs one hash and no disk read. */
    private static final ConcurrentHashMap<String, File> sVerified = new ConcurrentHashMap<>();

    /** The store directory, created on first use. Throws when no home can be
     *  resolved or the directory cannot be created; the caller then falls back
     *  to the legacy copying path so text still renders. */
    static File dir() throws IOException {
        File d = sDir;
        if (d != null) return d;
        String home = System.getProperty("user.home");
        if (home == null || home.isEmpty()) home = System.getenv("HOME");
        if (home == null || home.isEmpty()) throw new IOException("no home directory for the font store");
        d = new File(home + "/Library/Caches/MacApk/fonts");
        Files.createDirectories(d.toPath());
        if (sPurged.compareAndSet(false, true)) purgeStaleParts(d);
        sDir = d;
        return d;
    }

    /** Once per process: delete *.part staging files a killed writer left
     *  behind. Complete <sha256>.ttf files are meant to persist and are never
     *  purged here -- they are what a warm launch reuses. */
    private static void purgeStaleParts(File d) {
        long cutoff = System.currentTimeMillis() - STALE_PART_MS;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(d.toPath(), "*.part")) {
            for (Path p : ds) {
                try {
                    if (Files.getLastModifiedTime(p).toMillis() < cutoff) Files.deleteIfExists(p);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    /** The store file holding exactly these bytes, writing it if needed. */
    static File fileFor(byte[] b) throws IOException {
        String hex = sha256Hex(b);
        File f = new File(dir(), hex + ".ttf");
        if (sVerified.containsKey(hex)) return f;

        // VERIFY ON HIT, do not trust the name. The publish below is a rename
        // with no fsync, so after a panic or power loss APFS can surface a
        // correct-LENGTH file whose data never reached disk (zeros). A length
        // check would accept it, and because the name is content-addressed
        // nothing would ever rewrite it: every app on the machine would get
        // blank glyphs or a FontFormatException (-> the SANS_SERIF fallback
        // with wrong metrics) until someone deleted the store by hand. A byte
        // compare against bytes already in hand costs ~1-3 ms from the page
        // cache and turns that into a self-heal.
        //
        // Read and write through FileInputStream / FileOutputStream, NOT
        // Files.readAllBytes / Files.write: those use an interruptible
        // FileChannel, so a thread whose interrupt flag is set (executor
        // shutdownNow, Future.cancel(true)) would get ClosedByInterruptException
        // here, send the load down the LEAKING legacy path and burn the
        // one-shot warning. The pre-fix path wrote with a FileOutputStream
        // (JDK 21 Font.createFont0), which ignores interrupts, and so do these.
        if (f.isFile() && f.length() == b.length) {
            try (FileInputStream in = new FileInputStream(f)) {
                if (Arrays.equals(in.readAllBytes(), b)) {
                    sVerified.put(hex, f);
                    return f;
                }
            } catch (IOException unreadable) {
                // fall through and rewrite it
            }
        }

        // Publish atomically: write a private staging file (createTempFile is
        // mode 0600), then rename(2) it over the final name. ATOMIC_MOVE on the
        // Unix provider is a plain rename, which replaces an existing target in
        // one step, so a racing process that publishes the same hash writes
        // identical bytes and a reader keeps its old inode or re-opens
        // identical content. Never write the final name in place: a reader in
        // another process could open it half-written.
        Path part;
        try {
            part = Files.createTempFile(dir().toPath(), hex + ".", ".part");
        } catch (NoSuchFileException dirGone) {
            // The store directory was removed under this live process (a cache
            // cleaner, a user). sDir is cached forever, so without this every
            // later load would fail here and leak through the legacy path for
            // the rest of the process. Drop the cached state that described
            // the old directory, recreate it, and try once more.
            sDir = null;
            sVerified.clear();
            f = new File(dir(), hex + ".ttf");
            part = Files.createTempFile(dir().toPath(), hex + ".", ".part");
        }
        try {
            try (FileOutputStream o = new FileOutputStream(part.toFile())) {
                o.write(b);
            }
            Files.move(part, f.toPath(), StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(part);
        }
        sVerified.put(hex, f);
        return f;
    }

    /** Forget that THIS process verified the store file for {@code hex}, so
     *  the next fileFor re-checks it on disk and rewrites it if it is gone or
     *  wrong. Used when AWT fails to open a file we believed good: something
     *  outside this process (a cache cleaner, a user) may have removed it. */
    static void invalidate(String hex) {
        if (hex != null) sVerified.remove(hex);
    }

    /** Drop this process's verification memo for a store file whose bytes AWT
     *  rejected. It deliberately does NOT unlink the file. AWT reports
     *  ENVIRONMENTAL failures as FontFormatException too: TrueTypeFont.open
     *  wraps the FileNotFoundException from new RandomAccessFile (EMFILE in an
     *  fd-exhausted app, ENFILE, EIO) and its IOException arm into FFE. So
     *  "both paths threw FFE" does not prove the bytes are not a font, and the
     *  file is SHARED: other live runtimes re-open it by path after channel-pool
     *  eviction, and unlinking a good Roboto here would blank their text. A
     *  genuinely junk font costs one small file per distinct junk content. */
    static void forget(File f) {
        if (f == null) return;
        String name = f.getName();
        if (name.endsWith(".ttf")) invalidate(name.substring(0, name.length() - 4));
    }

    /** SHA-256 from the JDK's own SUN provider, pinned. An unpinned
     *  getInstance("SHA-256") takes the FIRST provider, and Android apps
     *  routinely Security.insertProviderAt(Conscrypt / BouncyCastle, 1) --
     *  that would run app-supplied digest code on our framework font path. */
    static String sha256Hex(byte[] b) throws IOException {
        byte[] d;
        try {
            d = MessageDigest.getInstance("SHA-256", "SUN").digest(b);
        } catch (java.security.GeneralSecurityException e) {
            throw new IOException("SHA-256 unavailable from the SUN provider", e);
        }
        char[] out = new char[d.length * 2];
        final char[] hexd = "0123456789abcdef".toCharArray();
        for (int i = 0; i < d.length; i++) {
            out[2 * i]     = hexd[(d[i] >> 4) & 0xf];
            out[2 * i + 1] = hexd[d[i] & 0xf];
        }
        return new String(out);
    }
}
