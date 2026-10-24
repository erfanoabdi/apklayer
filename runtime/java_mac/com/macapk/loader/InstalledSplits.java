// The installed split set of an app: which zips ARE the app.
//
// Since v1.0.1585 ("no copy of the APKs, ever", Phase 1) an installed app is
// laid out exactly the way `pm install-multiple` leaves it on a device:
//
//     <root>/data/app/~~<nonce>/<pkg>-<nonce>/base.apk
//                                           /split_config.arm64_v8a.apk
//                                           /split_config.en.apk
//                                           /split_<module>.apk
//                                           /split_<module>.config.<q>.apk
//
// and the runtime hands Boot.run the base.apk of that set. Every Java reader
// that used to treat "the APK" as ONE zip (AssetManager, the class loader's
// resource fallback, META-INF/services, the arsc and decoded-XML readers, the
// PackageManager's split fields) derives the whole set from that path through
// THIS class, so the rule lives in exactly one place. The rule is the one
// com.macapk.dex.Main.readDexes already applies to the DEX side (its
// "INSTALLED split set" block): base first, then every regular-file sibling
// named split_*.apk, skipping the installer's temp names, sorted by name.
// Keep the two identical: the classes.jar was converted in that order, so
// resources and code must agree on which member is "first".
//
// Two states, both correct here, and the C++ side never passes a list:
//  - the legacy synthetic `<codeDir>/_merged.apk` (base + every split raw-merged,
//    apk_unpacker.cpp): it is not named base.apk, so the set is just [it];
//  - the installed base.apk: the set is [base.apk, split_*...].
//
// AOSP CONTRACT (android11-release, fetched 2026-09-19 from
// raw.githubusercontent.com/aosp-mirror/platform_frameworks_base):
//  - the installer names the files: services/core/java/com/android/server/pm/
//    PackageInstallerSession.java:2095-2101
//        if (apk.splitName == null) targetName = "base" + APK_FILE_EXTENSION;
//        else targetName = "split_" + apk.splitName + APK_FILE_EXTENSION;
//    so `split_<name>.apk` <-> the split's manifest `split="<name>"` is a
//    device invariant, and reading the name back off the file is the inverse
//    of what the installer did.
//  - the order is by split NAME: core/java/android/content/pm/parsing/
//    ApkLiteParseUtils.java:166 "Always apply deterministic ordering based on
//    splitName", :184 `Arrays.sort(splitNames, PackageParser.sSplitNameComparator)`,
//    and PackageParser.java:898-909 SplitNameComparator = String.compareTo.
//    ApplicationInfo.java:863 documents the result: "The names of all installed
//    split APKs, ordered lexicographically."
//  - a package with NO splits has null arrays, not empty ones:
//    ParsingPackageUtils.java:285-286 calls pkg.asSplit(...) only when
//    !ArrayUtils.isEmpty(lite.splitNames); ParsingPackageImpl.java:299-308
//    leaves splitNames/splitCodePaths/splitRevisionCodes at their null defaults
//    otherwise, and toAppInfoWithoutStateWithoutFlags (:917, :933-934) copies
//    them through as-is. That null-vs-empty distinction is load-bearing for us
//    too: an EMPTY splitSourceDirs regressed Crossy's older libunity (v1.0.471
//    note), while null (no splits) and a real list (a split install) are both
//    device shapes.
//
// ⚠ SORT ORDER, precisely. We sort by FILE name, which is "split_" + name +
// ".apk"; AOSP sorts by NAME. For every valid split name (PackageParser.
// validateName:1490-1518 allows letters, then digits/underscore, with '.'
// separators that must be followed by a letter) the two orders agree, with one
// theoretical exception: a split `P` next to a split `P.Xyz` whose first
// segment letter after the dot is UPPERCASE ('A'..'Z' < 'a' of ".apk") sorts
// P.Xyz first here and P first on a device. bundletool module names and config
// splits are lowercase, so no real set hits it; it is written down so nobody
// has to re-derive it. The file-name order is what Main.readDexes and the C++
// merge use, and agreeing with THEM is the property that matters.
package com.macapk.loader;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class InstalledSplits {
    private InstalledSplits() {}

    /** The device file name of a base APK. */
    public static final String BASE_NAME = "base.apk";

    private static final String SPLIT_PREFIX = "split_";
    private static final String APK_SUFFIX   = ".apk";

    /** The whole set, base first: `[apkPath]` when the path is not an
     *  installed base.apk (a plain APK anywhere, the legacy `_merged.apk`, a
     *  bundle container), else `[base.apk, split_*.apk...]` sorted by name.
     *  The first element is exactly `new File(apkPath)`, so a caller that keys
     *  on the base's own spelling keeps it. Never null; empty only for a
     *  null/empty path. Splits are as the directory listing names them
     *  (absolute when apkPath is), and only regular files count. */
    public static List<File> of(String apkPath) {
        if (apkPath == null || apkPath.isEmpty()) return Collections.emptyList();
        File base = new File(apkPath);
        List<File> out = new ArrayList<>();
        out.add(base);
        out.addAll(splitsOf(base));
        return out;
    }

    /** Only the splits of {@code base} (never the base itself), sorted by
     *  file name; empty when {@code base} is not named base.apk, has no
     *  parent, or the parent cannot be listed. The rule is the one in
     *  com.macapk.dex.Main.readDexes: `split_*.apk`, not `.installing.`
     *  (the C++ installer's temp-name marker, see PlaceInstalledFile),
     *  regular files only -- plus a zero-length skip that readDexes gets for
     *  free from its ZipFile failure path (see below). */
    public static List<File> splitsOf(File base) {
        if (base == null || !BASE_NAME.equals(base.getName())) return Collections.emptyList();
        // getAbsoluteFile so a bare relative "base.apk" resolves its siblings
        // against the cwd instead of having no parent at all.
        File dir = base.getAbsoluteFile().getParentFile();
        if (dir == null) return Collections.emptyList();
        File[] kids;
        try {
            kids = dir.listFiles();
        } catch (Throwable t) {
            kids = null;
        }
        if (kids == null || kids.length == 0) return Collections.emptyList();
        List<File> splits = new ArrayList<>();
        for (File k : kids) {
            if (!isSplitFileName(k.getName())) continue;
            if (!k.isFile()) continue;
            // A ZERO-LENGTH split_*.apk is not an APK (an empty zip is 22
            // bytes) and no device install has one; skip it rather than
            // report it and then fail to open it. This is not theoretical:
            // bionic_shim.cpp macapk_ensure_install_dir (MACAPK_PROCFS_STAT=1,
            // default OFF) lays EMPTY placeholder files under Minecraft's four
            // split names into the install dir of WHATEVER app is running,
            // and this machine still carries two pairip-era MC install dirs
            // full of them. Main.readDexes gets the same outcome by catching
            // the ZipFile failure; here the file must not even be listed,
            // because splitSourceDirs is reported before anything opens it.
            if (k.length() == 0L) continue;
            splits.add(k);
        }
        // String.compareTo on the file name: the same order Files.list().sorted()
        // gives Main.readDexes for these ASCII device names (UnixPath compares
        // the path bytes, and UTF-8 byte order is code-point order).
        splits.sort((a, b) -> a.getName().compareTo(b.getName()));
        return splits;
    }

    /** True for `split_<name>.apk` that is not an in-progress temp name. */
    /** True when {@code apkPath} is an installed base.apk: named base.apk,
     *  inside `<pkg>-<nonce>/` inside `~~<nonce>/` -- the /data/app shape the
     *  runtime installs into (apk_unpacker.cpp EnsureInstalledPackage). A
     *  dropped file that happens to be called base.apk elsewhere is NOT one. */
    public static boolean isInstalledBase(String apkPath) {
        if (apkPath == null) return false;
        File f = new File(apkPath);
        if (!BASE_NAME.equals(f.getName())) return false;
        File pkgDir = f.getParentFile();
        File nonceDir = pkgDir != null ? pkgDir.getParentFile() : null;
        File appDir = nonceDir != null ? nonceDir.getParentFile() : null;
        return nonceDir != null && nonceDir.getName().startsWith("~~")
            && appDir != null && "app".equals(appDir.getName()) && f.isFile();
    }

    /** The install directory (`<pkg>-<nonce>`) of an installed base.apk, or
     *  null when {@code apkPath} is not one. Everything the installer put
     *  down for this package lives here: the apk set, and since v1.0.1588
     *  the native libraries under {@code lib/<isa>/}. */
    public static File installDirOf(String apkPath) {
        if (!isInstalledBase(apkPath)) return null;
        return new File(apkPath).getAbsoluteFile().getParentFile();
    }

    /** The instruction-set name an ABI's libraries are installed under, as
     *  libcore's VMRuntime.getInstructionSet spells it: arm64-v8a -> "arm64",
     *  armeabi-v7a / armeabi -> "arm". Null for anything else. Mirrors
     *  apk_unpacker.cpp InstructionSetOf, which chose the directory. */
    public static String isaOf(String abi) {
        if (abi == null) return null;
        try { return dalvik.system.VMRuntime.getInstructionSet(abi); }
        catch (Throwable notAnAbi) { return null; }
    }

    /** {@code ApplicationInfo.nativeLibraryDir} for an installed app: the
     *  install dir's {@code lib/<isa>/}, exactly AOSP's
     *  {@code new File(new File(codeFile, "lib"), primaryInstructionSet)}
     *  (PackageAbiHelperImpl.java:189-193, android11-release). The runtime
     *  installs ONE isa dir (main.cpp, v1.0.1588); this returns it when it
     *  exists -- honouring MACAPK_FORCE_ABI, which selects the isa the
     *  runtime extracted for that run -- and otherwise the arm64 path, which
     *  is the device's own answer for a package with no native code: a
     *  reported directory that does not exist. Null only when
     *  {@code apkPath} is not an installed base.apk. */
    public static File nativeLibDir(String apkPath) {
        File inst = installDirOf(apkPath);
        if (inst == null) return null;
        File libRoot = new File(inst, "lib");
        String forced = System.getenv("MACAPK_FORCE_ABI");
        if (forced != null && !forced.isEmpty()) {
            String isa = isaOf(forced);
            if (isa != null) return new File(libRoot, isa);
        }
        // The runtime installs ONE isa dir; prefer one that holds files, so
        // an empty sibling (the shim's install-dir materialiser mkdirs
        // lib/arm64 for a guest that opens /data/app, even on an arm app)
        // cannot shadow the populated one.
        File firstDir = null;
        for (String isa : new String[] { "arm64", "arm" }) {
            File d = new File(libRoot, isa);
            if (!d.isDirectory()) continue;
            if (firstDir == null) firstDir = d;
            String[] kids = d.list();
            if (kids != null && kids.length > 0) return d;
        }
        if (firstDir != null) return firstDir;
        return new File(libRoot, "arm64");
    }

    public static boolean isSplitFileName(String name) {
        return name != null
            && name.startsWith(SPLIT_PREFIX) && name.endsWith(APK_SUFFIX)
            && name.length() > SPLIT_PREFIX.length() + APK_SUFFIX.length()
            && !name.contains(".installing.");
    }

    /** True when {@code apkPath} is an installed base.apk with at least one
     *  split beside it, i.e. when the app is more than one zip. */
    public static boolean isSplitSet(String apkPath) {
        if (apkPath == null || apkPath.isEmpty()) return false;
        return !splitsOf(new File(apkPath)).isEmpty();
    }

    /** The AOSP split name a device file name encodes, the inverse of
     *  PackageInstallerSession's `"split_" + splitName + ".apk"`:
     *  `split_config.arm64_v8a.apk` -> `config.arm64_v8a`,
     *  `split_feature.apk` -> `feature`. Null when the name is not a split's
     *  (the base, or anything else). Pure: no I/O, so it is also what the
     *  PackageManager reports without opening the split's manifest. */
    public static String splitName(File split) {
        if (split == null) return null;
        String n = split.getName();
        if (!isSplitFileName(n)) return null;
        return n.substring(SPLIT_PREFIX.length(), n.length() - APK_SUFFIX.length());
    }

    /** The split names of a set, in set order, skipping the base; null when
     *  the set carries no split -- the AOSP value for a package with none. */
    public static String[] splitNames(List<File> set) {
        if (set == null) return null;
        List<String> names = new ArrayList<>();
        for (File f : set) {
            String n = splitName(f);
            if (n != null) names.add(n);
        }
        return names.isEmpty() ? null : names.toArray(new String[0]);
    }

    /** One line for a boot log: "base.apk + 3 split(s): config.arm64_v8a, ..."
     *  or "base.apk (no splits)". */
    public static String describe(List<File> set) {
        if (set == null || set.isEmpty()) return "(no apk)";
        StringBuilder sb = new StringBuilder(set.get(0).getName());
        String[] names = splitNames(set);
        if (names == null) return sb.append(" (no splits)").toString();
        sb.append(" + ").append(names.length).append(" split(s): ");
        for (int i = 0; i < names.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(names[i]);
        }
        return sb.toString();
    }
}
