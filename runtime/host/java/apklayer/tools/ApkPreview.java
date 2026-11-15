package apklayer.tools;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * What the manager's install dialog shows before Install: tab-separated lines package, version, label and one perm per uses-permission, read from the
 * base APK's binary manifest (an .apks/.xapk/.apkm: its base APK) with no conversion; the label and the icon are resolved in resources.arsc.
 * The icon (a PNG; an adaptive or vector icon has none here) is written to the second argument.
 *
 * <p>Usage: ApkPreview <apk or archive> <icon-out>
 */
public final class ApkPreview {
    public static void main(String[] args) throws IOException {
        Path in = Path.of(args[0]), tmp = null, apk = in;
        try (ZipFile zip = new ZipFile(in.toFile())) {
            if (zip.getEntry("AndroidManifest.xml") == null) {
                for (ZipEntry e : ApkSet.bundleApks(zip)) {
                    Path copy = Files.createTempFile("apkpreview", ".apk");
                    try (InputStream is = zip.getInputStream(e)) { Files.copy(is, copy, StandardCopyOption.REPLACE_EXISTING); }
                    if (ApkSet.manifest(copy)[2].isEmpty()) { tmp = apk = copy; break; }
                    Files.delete(copy);
                }
                if (tmp == null) ApkSet.fail("no base APK in " + in);
            }
        }
        try (ZipFile zip = new ZipFile(apk.toFile())) {
            byte[] xml = bytes(zip, "AndroidManifest.xml"), arsc = zip.getEntry("resources.arsc") == null ? null : bytes(zip, "resources.arsc");
            String pkg = "", version = "", name = "";
            int label = 0, icon = 0;
            List<String> perms = new ArrayList<>();
            ByteBuffer b = ByteBuffer.wrap(xml).order(ByteOrder.LITTLE_ENDIAN);
            List<String> strings = new ArrayList<>();
            int[] ids = new int[0];
            for (int at = b.getShort(2) & 0xffff; at + 8 <= xml.length; ) {
                int type = b.getShort(at) & 0xffff, header = b.getShort(at + 2) & 0xffff, size = b.getInt(at + 4);
                if (size <= 0) break;
                if (type == 0x0001) strings = ApkSet.strings(b, at);
                else if (type == 0x0180) { ids = new int[(size - header) / 4]; for (int i = 0; i < ids.length; i++) ids[i] = b.getInt(at + header + 4 * i); }
                else if (type == 0x0102) {
                    String element = str(strings, b.getInt(at + 20));
                    int attrs = at + header + (b.getShort(at + 24) & 0xffff), count = b.getShort(at + 28) & 0xffff;
                    for (int i = 0; i < count; i++) {
                        int a = attrs + 20 * i, key = b.getInt(a + 4), raw = b.getInt(a + 8), dataType = b.get(a + 15) & 0xff, data = b.getInt(a + 16);
                        String k = key >= 0 && key < ids.length ? androidName(ids[key], str(strings, key)) : str(strings, key), v = raw >= 0 ? str(strings, raw) : "";
                        if (v.isEmpty() && dataType == 0x03) v = str(strings, data);
                        if (element.equals("manifest") && k.equals("package")) pkg = v;
                        else if (element.equals("manifest") && k.equals("versionName")) version = v;
                        else if (element.equals("uses-permission") && k.equals("name") && !v.isEmpty()) perms.add(v);
                        else if (element.equals("application") && dataType == 0x01 && k.equals("label")) label = data;
                        else if (element.equals("application") && dataType == 0x01 && k.equals("icon")) icon = data;
                        else if (element.equals("application") && k.equals("label")) name = v;
                    }
                }
                at += size;
            }
            // a resource it cannot read (an unusual table) leaves the package name and no icon, not an empty preview
            try { if (arsc != null && label != 0) { String r = resolve(arsc, label, false); if (r != null) name = r; } } catch (RuntimeException unreadable) { }
            System.out.println("package\t" + pkg + "\nversion\t" + version + "\nlabel\t" + (name.isEmpty() ? pkg : name));
            for (String p : perms) System.out.println("perm\t" + p);
            try {
                String r = arsc != null && icon != 0 ? resolve(arsc, icon, true) : null;
                if (r != null && zip.getEntry(r) != null) Files.write(Path.of(args[1]), bytes(zip, r));
            } catch (RuntimeException unreadable) { }
        } finally {
            if (tmp != null) Files.deleteIfExists(tmp);
        }
    }

    /**
     * The string of resource id (0xPPTTEEEE) in resources.arsc: the default locale for a label, the densest .png for a file path (icon).
     * shortcut: only plain string values and references to one, no plurals or styles; follows a reference up to four times.
     */
    static String resolve(byte[] arsc, int id, boolean file) {
        ByteBuffer b = ByteBuffer.wrap(arsc).order(ByteOrder.LITTLE_ENDIAN);
        List<String> global = new ArrayList<>();
        for (int hop = 0; hop < 4; hop++) {
            String best = null;
            int bestScore = -1, ref = 0;
            for (int at = b.getShort(2) & 0xffff; at + 8 <= arsc.length; ) {
                int type = b.getShort(at) & 0xffff, size = b.getInt(at + 4);
                if (size <= 0) break;
                if (type == 0x0001 && global.isEmpty()) global = ApkSet.strings(b, at);
                else if (type == 0x0200 && b.getInt(at + 8) == (id >>> 24)) {
                    for (int t = at + (b.getShort(at + 2) & 0xffff); t < at + size; ) {
                        int ct = b.getShort(t) & 0xffff, ch = b.getShort(t + 2) & 0xffff, cs = b.getInt(t + 4);
                        if (cs <= 0) break;
                        // shortcut: dense 32-bit offsets only; a sparse (flag 1) or 16-bit (flag 2) type chunk is skipped and its resource stays unresolved
                        if (ct == 0x0201 && (b.get(t + 9) & 3) == 0 && (b.get(t + 8) & 0xff) == ((id >>> 16) & 0xff)) {
                            int entry = id & 0xffff, count = b.getInt(t + 12);
                            if (entry < count && b.getInt(t + ch + 4 * entry) != -1) {
                                int e = t + b.getInt(t + 16) + b.getInt(t + ch + 4 * entry), dataType = b.get(e + 11) & 0xff, data = b.getInt(e + 12);
                                int cfg = t + 20, density = b.getShort(cfg + 14) & 0xffff, locale = b.getShort(cfg + 8) & 0xffff;
                                if ((b.getShort(e + 2) & 1) == 0) {  // not a complex (bag) entry
                                    int score = file ? density : locale == 0 ? 1 : 0;
                                    if (dataType == 0x03 && data < global.size() && score > bestScore && (!file || global.get(data).endsWith(".png"))) { best = global.get(data); bestScore = score; ref = 0; }
                                    else if (dataType == 0x01 && !file && bestScore < 0) ref = data;
                                }
                            }
                        }
                        t += cs;
                    }
                }
                at += size;
            }
            if (best != null) return best;
            if (ref == 0) return null;
            id = ref;
        }
        return null;
    }

    /** The attribute's name from its android: resource id (an obfuscated manifest drops the names), else the name the manifest gives. */
    private static String androidName(int id, String name) {
        return switch (id) { case 0x01010001 -> "label"; case 0x01010002 -> "icon"; case 0x01010003 -> "name"; case 0x0101021c -> "versionName"; default -> name; };
    }

    private static byte[] bytes(ZipFile zip, String name) throws IOException {
        try (InputStream is = zip.getInputStream(zip.getEntry(name))) { return is.readAllBytes(); }
    }

    private static String str(List<String> strings, int i) { return i >= 0 && i < strings.size() ? strings.get(i) : ""; }

    private ApkPreview() {}
}
