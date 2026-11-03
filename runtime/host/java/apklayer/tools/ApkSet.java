package apklayer.tools;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Stages what `apklayer install` was given (one APK, a base APK and its splits, or an .apks/.xapk/.apkm archive) as Android installs a package:
 * base.apk and split_<split name>.apk in one directory (cli/apps.cpp; dex2jvm and VerifyInstall then read the splits' code beside base.apk).
 * Every APK's manifest names its package, versionCode and split; they must agree and there must be exactly one base, as PackageParser
 * requires of a cluster. Config splits for another ABI are left out (the device installs only its own ABI's split); density and language
 * splits all stay: the AssetManager picks the best configuration among them at run time.
 *
 * <p>Usage: ApkSet <out-dir> <abi> <apk or archive>... Exit 1 with a message on a bad set.
 */
public final class ApkSet {
    private static final Set<String> ABIS = Set.of("armeabi", "armeabi_v7a", "arm64_v8a", "x86", "x86_64", "mips", "mips64", "riscv64");

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args[0]);
        String abi = args[1].replace('-', '_');
        List<Path> apks = new ArrayList<>();
        Path unpacked = Files.createDirectories(out.resolve("unpacked"));
        for (int i = 2; i < args.length; i++) {
            Path in = Path.of(args[i]);
            try (ZipFile zip = new ZipFile(in.toFile())) {
                if (zip.getEntry("AndroidManifest.xml") != null) { apks.add(in); continue; }
                int n = 0;
                for (ZipEntry e : bundleApks(zip)) {
                    Path copy = unpacked.resolve(i + "-" + n++ + ".apk");
                    try (InputStream is = zip.getInputStream(e)) { Files.copy(is, copy, StandardCopyOption.REPLACE_EXISTING); }
                    apks.add(copy);
                }
            }
        }
        String pkg = null, base = null;
        long version = 0;
        TreeMap<String, Path> splits = new TreeMap<>();
        for (Path apk : apks) {
            String[] m = manifest(apk);
            if (pkg == null) { pkg = m[0]; version = Long.parseLong(m[1]); }
            else if (!pkg.equals(m[0])) fail(apk + " is " + m[0] + ", not " + pkg);
            else if (version != Long.parseLong(m[1])) fail(apk + " has versionCode " + m[1] + ", the others " + version);
            if (m[2].isEmpty()) {
                if (base != null) fail("two base APKs: " + base + " and " + apk);
                base = apk.toString();
                Files.copy(apk, out.resolve("base.apk"), StandardCopyOption.REPLACE_EXISTING);
            } else if (!m[2].matches("[A-Za-z0-9_.]+")) fail("bad split name " + m[2]);
            else if (otherAbi(m[2], abi)) System.err.println("apklayer: leaving out " + m[2] + " (not " + abi + ")");
            else if (splits.put(m[2], apk) != null) fail("split " + m[2] + " given twice");
        }
        if (base == null) fail("no base APK among " + apks);
        for (var split : splits.entrySet()) Files.copy(split.getValue(), out.resolve("split_" + split.getKey() + ".apk"), StandardCopyOption.REPLACE_EXISTING);
        try (var stream = Files.walk(unpacked)) { for (Path p : stream.sorted(Collections.reverseOrder()).toList()) Files.delete(p); }
        if (!splits.isEmpty()) System.out.println("apklayer: " + pkg + " with splits " + String.join(", ", splits.keySet()));
    }

    /** "config.x86" for a machine that is arm64_v8a; the base and feature splits are for every ABI. */
    static boolean otherAbi(String split, String abi) {
        String config = split.startsWith("config.") ? split.substring(7) : split.contains(".config.") ? split.substring(split.indexOf(".config.") + 8) : null;
        return config != null && ABIS.contains(config) && !config.equals(abi);
    }

    /** The APKs of an archive a device would install: a bundletool set's splits (not its standalone alternatives), else every APK at the root. */
    private static List<ZipEntry> bundleApks(ZipFile zip) {
        List<ZipEntry> all = Collections.list(zip.entries()).stream().filter(e -> !e.isDirectory() && e.getName().endsWith(".apk")).map(e -> (ZipEntry) e).toList();
        List<ZipEntry> splits = all.stream().filter(e -> e.getName().startsWith("splits/") || e.getName().startsWith("asset-slices/")).toList();
        if (!splits.isEmpty()) return splits;
        List<ZipEntry> universal = all.stream().filter(e -> e.getName().equals("universal.apk")).toList();
        if (!universal.isEmpty()) return universal;
        List<ZipEntry> root = all.stream().filter(e -> !e.getName().contains("/")).toList();
        if (root.isEmpty()) fail(zip.getName() + " holds no APK");
        return root;
    }

    private static void fail(String message) {
        System.err.println("apklayer: " + message);
        System.exit(1);
    }

    /**
     * {package, versionCode, split} from the APK's binary AndroidManifest.xml: the attributes of its root element (ResXMLTree: a string pool,
     * then a resource map that names android: attributes by id, then the elements). versionCode is read by its id (obfuscated manifests drop the name).
     */
    static String[] manifest(Path apk) throws IOException {
        byte[] xml;
        try (ZipFile zip = new ZipFile(apk.toFile())) {
            ZipEntry e = zip.getEntry("AndroidManifest.xml");
            if (e == null) { fail(apk + " has no AndroidManifest.xml"); return null; }
            try (InputStream is = zip.getInputStream(e)) { xml = is.readAllBytes(); }
        }
        ByteBuffer b = ByteBuffer.wrap(xml).order(ByteOrder.LITTLE_ENDIAN);
        List<String> strings = new ArrayList<>();
        int[] ids = new int[0];
        for (int at = b.getShort(2) & 0xffff; at + 8 <= xml.length; ) {
            int type = b.getShort(at) & 0xffff, header = b.getShort(at + 2) & 0xffff, size = b.getInt(at + 4);
            if (size <= 0) break;
            if (type == 0x0001) strings = strings(b, at);
            else if (type == 0x0180) { ids = new int[(size - header) / 4]; for (int i = 0; i < ids.length; i++) ids[i] = b.getInt(at + header + 4 * i); }
            else if (type == 0x0102) {  // the first start element: <manifest>
                int attrs = at + header + (b.getShort(at + 24) & 0xffff), count = b.getShort(at + 28) & 0xffff;
                String pkg = "", split = "", version = "0";
                for (int i = 0; i < count; i++) {
                    int a = attrs + 20 * i, name = b.getInt(a + 4), raw = b.getInt(a + 8), dataType = b.get(a + 15) & 0xff, data = b.getInt(a + 16);
                    String key = name >= 0 && name < strings.size() ? strings.get(name) : "";
                    String value = raw >= 0 && raw < strings.size() ? strings.get(raw) : dataType == 0x03 && data >= 0 && data < strings.size() ? strings.get(data) : Integer.toUnsignedString(data);
                    if (name >= 0 && name < ids.length && ids[name] == 0x0101021b) version = Integer.toUnsignedString(data);  // android:versionCode
                    else if (key.equals("package")) pkg = value;
                    else if (key.equals("split")) split = value;
                }
                return new String[] {pkg, version, split};
            }
            at += size;
        }
        fail(apk + ": unreadable AndroidManifest.xml");
        return null;
    }

    private static List<String> strings(ByteBuffer b, int at) {
        int count = b.getInt(at + 8), flags = b.getInt(at + 16), start = at + b.getInt(at + 20);
        boolean utf8 = (flags & 0x100) != 0;
        List<String> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int p = start + b.getInt(at + (b.getShort(at + 2) & 0xffff) + 4 * i);
            if (utf8) {
                p += (b.get(p) & 0x80) != 0 ? 2 : 1;  // the UTF-16 length
                int len = b.get(p) & 0xff;
                if ((len & 0x80) != 0) { len = ((len & 0x7f) << 8) | (b.get(p + 1) & 0xff); p += 2; } else p += 1;
                out.add(new String(b.array(), p, len, StandardCharsets.UTF_8));
            } else {
                int len = b.getShort(p) & 0xffff;
                if ((len & 0x8000) != 0) { len = ((len & 0x7fff) << 16) | (b.getShort(p + 2) & 0xffff); p += 4; } else p += 2;
                out.add(new String(b.array(), p, len * 2, StandardCharsets.UTF_16LE));
            }
        }
        return out;
    }

    private ApkSet() {}
}
