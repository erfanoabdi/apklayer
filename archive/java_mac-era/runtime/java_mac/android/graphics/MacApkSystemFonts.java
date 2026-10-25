// MacApkSystemFonts -- a real /system/fonts + /system/etc/fonts.xml for NATIVE
// text stacks.
//
// WHY THIS EXISTS. Java text never needed these files: MacApkFontFamilies reads
// the AOSP faces straight out of android-framework.jar. A guest that draws text
// in native code finds its fonts the way the platform does instead. Skia's
// SkFontMgr_Android (Flutter's libflutter.so, any app that ships its own Skia)
// parses /system/etc/fonts.xml and opens each <font> under /system/fonts/. With
// neither path present Flutter's font manager came up with no families, its
// first default-typeface read was a NULL load, and every Flutter app drew its
// widgets with no text at all (Feeel 2.4.1, measured 2026-10-16).
//
// WHAT IT DOES. Once per distinct font set, writes the bundled faces plus a
// fonts.xml generated from MacApkFontFamilies' table (itself transcribed from
// the API 30 device's fonts.xml) into
//     ~/Library/Caches/MacApk/android-system-fonts/<key>/
// and Boot hands that directory to the bionic shim (NativeCocoa
// .setSystemFontsDir), whose path remap serves /system/etc/fonts.xml and
// /system/fonts/<file> from it (bionic_shim.cpp macapk_system_fonts_remap).
// The device file's CJK families (zh-Hans, zh-Hant, ja, ko) are backed by the
// Mac's own faces, linked in by MacApkHostFonts; without them Flutter drew every
// Chinese character as a box (GitHub issue #27).
//
// THE KEY is a digest of the generated XML and each face's jar entry (name,
// size, CRC-32 from the central directory, no font bytes read), so a warm launch
// costs one stat, a new runtime with the same fonts reuses the directory, and a
// changed face gets a new directory instead of changing files a running process
// may have open. Same "MacApk" (never ".apk") root as MacApkFontStore, for the
// same reason.
//
// PUBLISHING is crash-safe: every file is written and forced to disk in a
// <key>.part-<pid> staging directory, fonts.xml last, and the directory is then
// renamed into place. A complete directory is never modified again.
//
// Gate: MACAPK_SYSTEM_FONTS=0 skips it (the shim's remap reads the same gate).
package android.graphics;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.stream.Stream;

public final class MacApkSystemFonts {
    private MacApkSystemFonts() {}

    public static final boolean ENABLED = !"0".equals(System.getenv("MACAPK_SYSTEM_FONTS"));

    /** A staging directory older than this cannot belong to a live writer. */
    private static final long STALE_PART_MS = 60L * 60L * 1000L;

    /** The published directory (fonts.xml plus every face it names), writing
     *  it on first use; null when disabled or when it cannot be built, in which
     *  case /system/fonts stays absent exactly as before. Never throws. */
    public static String materialize() {
        return materialize(null);
    }

    /** As materialize(), plus the host's CJK faces (MacApkHostFonts) found
     *  through hostFontFile (PostScript name -> file path, CoreText's answer;
     *  null for none). They are LINKED into the directory, never copied: the
     *  bytes stay the Mac's, and the key carries each file's path, size and
     *  mtime so a macOS update that moves one publishes a new directory. */
    public static String materialize(java.util.function.Function<String, String> hostFontFile) {
        if (!ENABLED) return null;
        try {
            List<MacApkHostFonts.Family> host = MacApkHostFonts.resolve(hostFontFile);
            String baseXml = MacApkFontFamilies.systemFontsXml();
            List<String> files = MacApkFontFamilies.systemFontFiles();
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(baseXml.getBytes(StandardCharsets.UTF_8));
            md.update(MacApkHostFonts.identity(host).getBytes(StandardCharsets.UTF_8));
            for (String f : files) {
                URL u = MacApkSystemFonts.class.getResource("/fonts/" + f);
                if (u == null) continue;   // not bundled: fonts.xml names it, Skia skips it
                md.update((f + '\0' + entryStamp(u) + '\0').getBytes(StandardCharsets.UTF_8));
            }
            String key = hex(md.digest()).substring(0, 16);

            File root = root();
            File dir = new File(root, key);
            if (new File(dir, "fonts.xml").isFile()) return dir.getPath();

            purgeStaleParts(root);
            File part = new File(root, key + ".part-" + ProcessHandle.current().pid());
            deleteTree(part.toPath());
            Files.createDirectories(part.toPath());
            for (String f : files) {
                try (InputStream in = MacApkSystemFonts.class.getResourceAsStream("/fonts/" + f)) {
                    if (in == null) continue;
                    writeForced(new File(part, f).toPath(), in.readAllBytes());
                }
            }
            // Host faces: index each in its collection (reads only headers and
            // name tables), then one symlink per host file under an ASCII name.
            MacApkHostFonts.indexAll(host);
            java.util.Set<String> linked = new java.util.HashSet<>();
            for (MacApkHostFonts.Family fam : host) {
                for (MacApkHostFonts.Face f : fam.faces) {
                    if (linked.add(f.linkName)) {
                        Files.createSymbolicLink(new File(part, f.linkName).toPath(),
                                                 f.file.toPath());
                    }
                }
            }
            String xml = host.isEmpty() ? baseXml : MacApkFontFamilies.systemFontsXml(host);
            writeForced(new File(part, "fonts.xml").toPath(), xml.getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(part.toPath(), dir.toPath(), StandardCopyOption.ATOMIC_MOVE);
            } catch (FileAlreadyExistsException | java.nio.file.DirectoryNotEmptyException raced) {
                // Another runtime published the same key first; its copy is identical.
                deleteTree(part.toPath());
            }
            return new File(dir, "fonts.xml").isFile() ? dir.getPath() : null;
        } catch (Throwable t) {
            System.err.println("[W] MacApkSystemFonts: /system/fonts not published: " + t);
            return null;
        }
    }

    /** "size:crc" of the jar entry behind a resource URL, read from the jar's
     *  central directory; the URL itself when it is not a jar entry (a dev
     *  classpath directory), which still changes when the file moves. */
    private static String entryStamp(URL u) {
        try {
            URLConnection c = u.openConnection();
            if (c instanceof JarURLConnection jc) {
                JarEntry e = jc.getJarEntry();
                if (e != null) return e.getSize() + ":" + Long.toHexString(e.getCrc());
            }
            return u.toString() + ":" + c.getContentLengthLong() + ":" + c.getLastModified();
        } catch (IOException e) {
            return u.toString();
        }
    }

    private static File root() throws IOException {
        String home = System.getProperty("user.home");
        if (home == null || home.isEmpty()) home = System.getenv("HOME");
        if (home == null || home.isEmpty()) throw new IOException("no home directory");
        File d = new File(home + "/Library/Caches/MacApk/android-system-fonts");
        Files.createDirectories(d.toPath());
        return d;
    }

    private static void writeForced(Path p, byte[] bytes) throws IOException {
        try (FileChannel ch = FileChannel.open(p, StandardOpenOption.CREATE_NEW,
                                               StandardOpenOption.WRITE)) {
            ByteBuffer b = ByteBuffer.wrap(bytes);
            while (b.hasRemaining()) ch.write(b);
            ch.force(true);
        }
    }

    private static void purgeStaleParts(File root) {
        long cutoff = System.currentTimeMillis() - STALE_PART_MS;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(root.toPath(), "*.part-*")) {
            for (Path p : ds) {
                try {
                    if (Files.getLastModifiedTime(p).toMillis() < cutoff) deleteTree(p);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    private static void deleteTree(Path p) throws IOException {
        if (!Files.exists(p)) return;
        try (Stream<Path> s = Files.walk(p)) {
            for (Path q : s.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(q);
        }
    }

    private static String hex(byte[] d) {
        StringBuilder b = new StringBuilder(d.length * 2);
        for (byte x : d) b.append(Character.forDigit((x >> 4) & 0xf, 16))
                          .append(Character.forDigit(x & 0xf, 16));
        return b.toString();
    }
}
