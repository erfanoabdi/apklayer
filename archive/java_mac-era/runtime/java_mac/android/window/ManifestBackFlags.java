// android.window.ManifestBackFlags -- Mac.apk-internal (package-private, NOT
// AOSP surface): the two manifest facts WindowOnBackInvokedDispatcher.Checker
// needs, read from the app's BINARY AndroidManifest.xml.
//
// WHAT AOSP DOES. The package parser records them at install time:
//   <application android:enableOnBackInvokedCallback> ->
//     ApplicationInfo.privateFlagsExt PRIVATE_FLAG_EXT_ENABLE_ON_BACK_INVOKED_CALLBACK
//     (android14-release ParsingPackageUtils :2256
//      `bool(false, R.styleable.AndroidManifestApplication_enableOnBackInvokedCallback, sa)`;
//      android16-release :2436-2439 changed the DEFAULT to
//      `predictiveBackDefaultEnableSdk36() && targetSdk > VANILLA_ICE_CREAM`)
//   <activity android:enableOnBackInvokedCallback> (API 34) ->
//     ActivityInfo.privateFlags PRIVATE_FLAG_ENABLE_/DISABLE_ON_BACK_INVOKED_CALLBACK,
//     set ONLY when the attribute is present (android14-release
//     services/.../ParsedActivityUtils `if (sa.hasValue(...))`), so "absent" and
//     "false" stay distinguishable (ActivityInfo.hasOnBackInvokedCallbackEnabled).
//
// WHY A READER HERE instead of those fields. Mac.apk's package parser
// (com.macapk.loader.Manifest -> MacApkPackageManager / Application) does not
// read either attribute, so both bits are 0 for every app, which would make the
// Checker answer "legacy" for all of them. The parser files belong to other
// lanes; the patch that populates the real fields is recorded in
// docs/predictive-back-onbackinvokeddispatcher_2026-09-26.md. RETIRE this class
// when that lands: the Checker then reads ApplicationInfo / ActivityInfo exactly
// as AOSP does and this file is deleted.
//
// The attribute is read raw (present? true/false?) and the DEFAULT is applied by
// the Checker, where the presented platform level is known.
package android.window;

final class ManifestBackFlags {
    private static final String NS = "http://schemas.android.com/apk/res/android";

    /** <application android:enableOnBackInvokedCallback>, or null when absent. */
    final Boolean application;
    /** Fully-qualified <activity> class name -> its explicit attribute value.
     *  Only activities that SET the attribute appear (AOSP hasValue rule). */
    final java.util.Map<String, Boolean> activities;
    /** <uses-sdk android:targetSdkVersion> (AOSP default chain: min, then 1);
     *  0 when unreadable. Only a fallback -- ApplicationInfo.targetSdkVersion
     *  is preferred because it is what the rest of the platform reads. */
    final int targetSdk;

    private ManifestBackFlags(Boolean app, java.util.Map<String, Boolean> acts, int tsdk) {
        application = app; activities = acts; targetSdk = tsdk;
    }

    static final ManifestBackFlags NONE =
            new ManifestBackFlags(null, java.util.Collections.emptyMap(), 0);

    private static final java.util.concurrent.ConcurrentHashMap<String, ManifestBackFlags> CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** The flags of the package whose ApplicationInfo is `ai`. Tries the
     *  installed base apk (`sourceDir`, the AOSP-shaped answer) first, then the
     *  path the launcher exported (MACAPK_APK_PATH, the same source
     *  com.macapk.loader.PresentedSdk reads). Never throws; NONE when neither
     *  is readable. Cached per path: a manifest cannot change under a running
     *  process. */
    static ManifestBackFlags of(android.content.pm.ApplicationInfo ai) {
        String a = ai != null ? ai.sourceDir : null;
        ManifestBackFlags f = forPath(a);
        if (f != NONE) return f;
        return forPath(System.getenv("MACAPK_APK_PATH"));
    }

    static ManifestBackFlags forPath(String apk) {
        if (apk == null || apk.isEmpty()) return NONE;
        ManifestBackFlags f = CACHE.get(apk);
        if (f != null) return f;
        f = read(apk);
        CACHE.put(apk, f);
        return f;
    }

    private static ManifestBackFlags read(String apk) {
        java.io.File file = new java.io.File(apk);
        if (!file.isFile()) return NONE;
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(file)) {
            byte[] axml = entry(zf, "AndroidManifest.xml");
            if (axml == null) {
                // A multi-part bundle (.apkm / .xapk / .apks) at MACAPK_APK_PATH
                // keeps its manifest inside base.apk. STREAMED to that one entry:
                // a base.apk can be hundreds of MB and must not be buffered.
                java.util.zip.ZipEntry base = zf.getEntry("base.apk");
                if (base != null) {
                    try (java.io.InputStream in = zf.getInputStream(base)) {
                        axml = nested(in, "AndroidManifest.xml");
                    }
                }
            }
            if (axml == null) return NONE;
            return parse(com.macapk.loader.BinaryXmlParser.parse(axml));
        } catch (Throwable t) {
            return NONE;
        }
    }

    private static byte[] entry(java.util.zip.ZipFile zf, String name) throws java.io.IOException {
        java.util.zip.ZipEntry e = zf.getEntry(name);
        if (e == null) return null;
        try (java.io.InputStream in = zf.getInputStream(e)) { return in.readAllBytes(); }
    }

    private static byte[] nested(java.io.InputStream zip, String name) throws java.io.IOException {
        try (java.util.zip.ZipInputStream zi = new java.util.zip.ZipInputStream(zip)) {
            for (java.util.zip.ZipEntry e; (e = zi.getNextEntry()) != null; ) {
                if (name.equals(e.getName())) return zi.readAllBytes();
            }
        }
        return null;
    }

    static ManifestBackFlags parse(org.w3c.dom.Document doc) {
        org.w3c.dom.Element root = doc.getDocumentElement();
        String pkg = root.getAttribute("package");
        int target = 0, min = 0;
        org.w3c.dom.NodeList sdks = root.getElementsByTagName("uses-sdk");
        if (sdks.getLength() > 0) {
            org.w3c.dom.Element s = (org.w3c.dom.Element) sdks.item(0);
            target = intAttr(s, "targetSdkVersion");
            min = intAttr(s, "minSdkVersion");
        }
        if (target == 0) target = min > 0 ? min : 1;
        Boolean app = null;
        org.w3c.dom.NodeList apps = root.getElementsByTagName("application");
        if (apps.getLength() > 0) app = boolAttr((org.w3c.dom.Element) apps.item(0));
        java.util.HashMap<String, Boolean> acts = new java.util.HashMap<>();
        org.w3c.dom.NodeList as = root.getElementsByTagName("activity");
        for (int i = 0; i < as.getLength(); i++) {
            org.w3c.dom.Element a = (org.w3c.dom.Element) as.item(i);
            Boolean v = boolAttr(a);
            if (v == null) continue;
            String n = a.getAttributeNS(NS, "name");
            if (n == null || n.isEmpty()) continue;
            acts.put(className(pkg, n), v);
        }
        return new ManifestBackFlags(app, java.util.Collections.unmodifiableMap(acts), target);
    }

    /** AOSP PackageParser.buildClassName: ".Foo" and "Foo" are relative to the
     *  package; anything else with a dot is already fully qualified. */
    static String className(String pkg, String name) {
        if (name.startsWith(".")) return (pkg == null ? "" : pkg) + name;
        if (name.indexOf('.') < 0) return (pkg == null ? "" : pkg + ".") + name;
        return name;
    }

    /** The attribute as a boolean, null when ABSENT. BinaryXmlParser renders an
     *  inline boolean as "true"/"false"; the raw data forms are accepted too.
     *  A @bool/ resource reference is not resolved here (none in the 62-app
     *  corpus) and reads as absent. */
    private static Boolean boolAttr(org.w3c.dom.Element e) {
        if (!e.hasAttributeNS(NS, "enableOnBackInvokedCallback")) return null;
        String v = e.getAttributeNS(NS, "enableOnBackInvokedCallback").trim();
        if (v.equalsIgnoreCase("true") || v.equals("-1") || v.equalsIgnoreCase("0xffffffff")) return Boolean.TRUE;
        if (v.equalsIgnoreCase("false") || v.equals("0") || v.equalsIgnoreCase("0x0")) return Boolean.FALSE;
        return null;
    }

    private static int intAttr(org.w3c.dom.Element e, String name) {
        String v = e.getAttributeNS(NS, name);
        if (v == null || v.isEmpty()) return 0;
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException nfe) { return 0; }
    }
}
