// com.macapk.loader.PreferenceDefaultsScanner -- scan every res/xml/*.xml
// in the app, find <*Preference android:key=K android:defaultValue=V>
// nodes, and write K=V into the default SharedPreferences.
//
// Why: AOSP's androidx.preference.PreferenceManager.setDefaultValues
// uses reflection-driven PreferenceInflater to construct each
// Preference, then calls onSetInitialValue which persists the
// default value to SharedPreferences. Mac.apk's
// Context.obtainStyledAttributes returns an empty TypedArray, so
// the Preference's defaultValue is never extracted, never persisted.
// Apps that read SharedPreferences keys with a null fallback then
// crash on the first lookup (Enum.valueOf(null) NPE etc.).
//
// This scanner is a GENERIC framework-level workaround that runs at
// Application init time, BEFORE Application.onCreate, so any default
// SharedPreferences read in onCreate sees the populated keys. It
// walks all XML resources via the same DocumentBuilder pattern we
// use elsewhere; for each Preference-type element with both
// android:key and android:defaultValue, it writes the resolved
// value to the default SharedPreferences (apply mode).
//
// Idempotent: contains() check means already-set keys (from prior
// runs persisted to disk) are not overwritten.
package com.macapk.loader;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class PreferenceDefaultsScanner {
    private static final String TAG = "Mac.apk.PrefDefaults";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    // Persisted-value shape a Preference XML tag expects, resolved by
    // CLASS HIERARCHY (see classifyTag / classifyByHierarchy below).
    private static final int KIND_BOOLEAN    = 1;
    private static final int KIND_INTEGER    = 2;
    private static final int KIND_STRING_SET = 3;
    private static final int KIND_STRING     = 4;

    // androidx.preference's PreferenceInflater (GenericInflater) prefixes
    // any dot-free XML tag with this package before Class.forName, the
    // same way View inflation prefixes "TextView" with "android.widget.".
    // A tag that already contains a '.' (a custom app subclass, e.g.
    // F-Droid's org.fdroid.fdroid.panic.DestructiveSwitchPreference) is
    // used as-is.
    private static final String PREFERENCE_TAG_PREFIX = "androidx.preference.";

    private PreferenceDefaultsScanner() {}

    /** Walk every `xml` resource of the installed apk set (the table's
     *  file rows, served by the ResourceIndex -- v1.0.1590) looking for
     *  Preference default-value declarations and write them to the default
     *  SharedPreferences. Best-effort: per-XML failures are logged
     *  and skipped, NOT propagated. */
    public static void populate(Application app) {
        if (app == null) return;
        SharedPreferences sp =
            android.preference.PreferenceManager.getDefaultSharedPreferences(app);
        if (sp == null) {
            NativeLog.i(TAG, "no default SharedPreferences -- skipping");
            return;
        }
        // Locate the app's apkt res/xml directory via Boot's known
        // app dir. Boot exposes the dir via Application.macapk_get().
        // We dig it out of the app's class-loader-cached path.
        // The XML resources are the installed apk set's `xml` file rows,
        // served by the ResourceIndex (v1.0.1590; no decoded tree exists).
        java.util.List<Object[]> docs = new java.util.ArrayList<>();   // {label, InputStream}
        try {
            Object ri = invokeStatic("android.content.res.Resources", "macapkGetResourceIndex");
            if (ri instanceof ResourceIndex rix) {
                for (int id : rix.macapkFileIdsOfType("xml")) {
                    java.io.InputStream in = rix.openRaw(id);
                    if (in != null) docs.add(new Object[] { "0x" + Integer.toHexString(id), in });
                }
            }
        } catch (Throwable ignored) {}
        if (docs.isEmpty()) {
            NativeLog.i(TAG, "no xml resources found -- skipping");
            return;
        }
        SharedPreferences.Editor ed = sp.edit();
        // Snapshot the store ONCE so the self-heal check in walk() can see
        // each key's actual runtime type without a getAll() copy per XML
        // node. See isWrongType() for why this matters: a key can be
        // contains()==true yet hold the WRONG type from an earlier Mac.apk
        // bug (unresolved @array/ tokens persisted as String -- see
        // resolveReference), and a plain contains() skip would never
        // repair that on a long-lived data dir.
        Map<String, ?> existing = sp.getAll();
        int written = 0;
        for (Object[] d : docs) {
            try (InputStream is = (InputStream) d[1]) {
                written += scanStream(is, app, sp, ed, existing);
            } catch (Throwable t) {
                NativeLog.w(TAG, "scan " + d[0] + " threw: " + t);
            }
        }
        String from = docs.size() + " xml resource(s)";
        if (written > 0) {
            ed.apply();
            NativeLog.i(TAG, "wrote " + written + " preference default(s) from " + from);
        } else {
            NativeLog.i(TAG, "no new preference defaults found in " + from);
        }
    }

    /** Parse one XML and write any matching defaults. Returns the
     *  number of keys written. */
    private static int scanStream(InputStream is, Application app,
                                   SharedPreferences sp,
                                   SharedPreferences.Editor ed,
                                   Map<String, ?> existing)
        throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(is);
        return walk(doc.getDocumentElement(), app, sp, ed, existing);
    }

    private static int walk(Node node, Application app,
                              SharedPreferences sp,
                              SharedPreferences.Editor ed,
                              Map<String, ?> existing) {
        if (!(node instanceof Element)) return 0;
        Element e = (Element) node;
        int written = 0;
        // Only consider tags that look like Preference subclasses.
        // The full tag list is large but predictable: anything ending
        // in "Preference" or specific known tags.
        String tag = e.getTagName();
        if (tag != null && tag.endsWith("Preference")) {
            String key = e.getAttributeNS(ANDROID_NS, "key");
            String defaultValue = e.getAttributeNS(ANDROID_NS, "defaultValue");
            // Empty-string attributes mean "not set" in DOM-land. The
            // AttributeNS API returns "" rather than null when the
            // attribute is missing.
            if (key != null && !key.isEmpty()
                && defaultValue != null && !defaultValue.isEmpty()) {
                boolean already = sp.contains(key);
                // v1.0.73 fix: write TYPED based on the Preference
                // SUBCLASS (not the value literal). AOSP's
                // setDefaultValues calls Preference.onGetDefaultValue
                // + onSetInitialValue, and each subclass's
                // onSetInitialValue persists via the correct typed
                // putXxx. We mirror that -- see classifyTag() for the
                // full dispatch (Boolean / Integer / Set<String> /
                // String). The value-literal type heuristic (try
                // parseInt, try parseBool) was wrong: F-Droid's
                // keepCacheFor defaultValue is "86400000" (looks like
                // long), but the app reads via
                // getString("keepCacheFor", null) → NPE on
                // String.hashCode if we'd written as int.
                //
                // Only skip when the key is ALREADY correctly typed.
                // isWrongType() catches a key poisoned by an older
                // Mac.apk bug (see resolveReference below) so it gets
                // repaired instead of skipped forever.
                if (!already || isWrongType(existing, key, tag, app)) {
                    if (writeForTag(ed, key, defaultValue, tag, app)) {
                        written++;
                        if (already) {
                            NativeLog.w(TAG, "self-healed wrong-typed key '"
                                + key + "' (tag=" + tag + ")");
                        }
                    }
                }
            }
        }
        // Recurse over children (PreferenceCategory / PreferenceScreen
        // wrap their kids).
        NodeList kids = e.getChildNodes();
        for (int i = 0; i < kids.getLength(); ++i) {
            written += walk(kids.item(i), app, sp, ed, existing);
        }
        return written;
    }

    /** Detect a PREVIOUSLY-POISONED key: contains() is true (so the
     *  ordinary idempotent skip would apply forever) but the stored
     *  value's runtime type doesn't match what this Preference tag
     *  needs. A long-lived data dir can carry exactly this state from
     *  an older Mac.apk bug (an unresolved "@array/…" token persisted
     *  as a String -- the F-Droid blank-screen regression: androidx
     *  MultiSelectListPreference.setValues() casts the stored value to
     *  Set<String> and NPEs on mValues.addAll(null) when it isn't one).
     *  Real Android can never reach this state (its own Preference
     *  machinery only ever writes the type it reads back), so this is
     *  a Mac.apk-specific repair -- but it is keyed purely off the XML
     *  tag's class, never a package/app name, so it stays generic. */
    private static boolean isWrongType(Map<String, ?> existing, String key,
                                         String tag, Application app) {
        if (existing == null || !existing.containsKey(key)) return false;
        Object v = existing.get(key);
        switch (classifyTag(tag, app)) {
            case KIND_BOOLEAN:    return !(v instanceof Boolean);
            case KIND_INTEGER:    return !(v instanceof Integer);
            case KIND_STRING_SET: return !(v instanceof Set);
            default:              return false; // KIND_STRING: nothing to heal
        }
    }

    /** Classify the persisted-value shape a Preference XML tag expects,
     *  by CLASS HIERARCHY against the real Preference classes
     *  (reflection, through the app's own class loader so custom
     *  subclasses -- F-Droid's org.fdroid.fdroid.panic.
     *  DestructiveSwitchPreference, org.fdroid.fdroid.views.
     *  LiveSeekBarPreference -- resolve correctly). This is what AOSP's
     *  own dispatch does (virtual onSetInitialValue/persistXxx on the
     *  REAL object), so it is more faithful than matching the tag
     *  STRING. Falls back to a substring heuristic on the simple tag
     *  name if reflection can't load the class for any reason (keeps
     *  the narrower pre-existing behavior as a floor). */
    private static int classifyTag(String tag, Application app) {
        Integer byClass = classifyByHierarchy(tag, app);
        if (byClass != null) return byClass;
        String simpleTag = simpleName(tag);
        if (simpleTag.contains("MultiSelect")) return KIND_STRING_SET;
        if (simpleTag.equals("CheckBoxPreference")
            || simpleTag.equals("SwitchPreference")
            || simpleTag.equals("SwitchPreferenceCompat")
            || simpleTag.equals("TwoStatePreference")
            || simpleTag.contains("Switch")
            || simpleTag.contains("CheckBox")) {
            return KIND_BOOLEAN;
        }
        if (simpleTag.contains("SeekBar")) return KIND_INTEGER;
        return KIND_STRING;
    }

    private static String simpleName(String tag) {
        int dot = tag.lastIndexOf('.');
        return dot >= 0 ? tag.substring(dot + 1) : tag;
    }

    /** Resolve `tag` to a real Class (prefixing well-known short
     *  androidx.preference tags the same way PreferenceInflater does)
     *  and classify it by which well-known Preference base type it is
     *  assignable to. Returns null (never guesses) when the class can't
     *  be loaded this way -- classifyTag() falls back to the tag-name
     *  heuristic in that case. */
    private static Integer classifyByHierarchy(String tag, Application app) {
        if (app == null || tag == null) return null;
        try {
            ClassLoader cl = app.getClassLoader();
            if (cl == null) return null;
            String binaryName = tag.indexOf('.') >= 0 ? tag : PREFERENCE_TAG_PREFIX + tag;
            Class<?> cls = Class.forName(binaryName, false, cl);
            if (isAssignable(cl, "androidx.preference.MultiSelectListPreference", cls)) return KIND_STRING_SET;
            if (isAssignable(cl, "androidx.preference.TwoStatePreference", cls)) return KIND_BOOLEAN;
            if (isAssignable(cl, "androidx.preference.SeekBarPreference", cls)) return KIND_INTEGER;
            return KIND_STRING;
        } catch (Throwable t) {
            return null;
        }
    }
    private static boolean isAssignable(ClassLoader cl, String targetName, Class<?> cls) {
        try {
            Class<?> target = Class.forName(targetName, false, cl);
            return target.isAssignableFrom(cls);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Write the resolved default value with the correct type as
     *  determined by the Preference XML tag (matches AOSP per-class
     *  onSetInitialValue dispatch). Returns true if a write was made. */
    private static boolean writeForTag(SharedPreferences.Editor ed, String key,
                                          String rawValue, String tag, Application app) {
        switch (classifyTag(tag, app)) {
            case KIND_BOOLEAN: {
                Boolean b = resolveBoolean(rawValue, app);
                if (b == null) return false;
                ed.putBoolean(key, b);
                return true;
            }
            case KIND_INTEGER: {
                Integer i = resolveInteger(rawValue, app);
                if (i == null) return false;
                ed.putInt(key, i);
                return true;
            }
            case KIND_STRING_SET: {
                // MultiSelectListPreference. AOSP's onGetDefaultValue
                // resolves the XML defaultValue's "@array/…" reference
                // via TypedArray.getTextArray, converts it to a
                // Set<String>, and onSetInitialValue persists it via
                // persistStringSet. Mirror that instead of falling
                // through to the generic String branch (which used to
                // persist the LITERAL unresolved token -- see
                // resolveReference's default case for why that
                // poisoned the key).
                Set<String> set = resolveStringSet(rawValue, app);
                if (set == null) return false;
                ed.putStringSet(key, set);
                return true;
            }
            default: {
                // Everything else -> STRING (ListPreference,
                // EditTextPreference, DialogPreference, ...). String is
                // the safest universal type because the underlying XML
                // attribute IS a string.
                String resolved = resolveReference(rawValue, app);
                if (resolved == null) return false;
                ed.putString(key, resolved);
                return true;
            }
        }
    }

    /** Resolve a MultiSelectListPreference `defaultValue="@array/name"`
     *  reference to the real Set<String>, mirroring AOSP's
     *  MultiSelectListPreference.onGetDefaultValue (TypedArray.
     *  getTextArray(index) -> new HashSet<>(Arrays.asList(...))). A
     *  bare (non-"@") literal isn't a documented shape for this
     *  attribute's Set<String> semantics (the Preference styleable's
     *  defaultValue format is "string|boolean|integer|reference|float"
     *  -- there is no flat-string encoding of a string SET), so there
     *  is nothing sane to persist for one; return null (caller then
     *  behaves as if no default were set, matching AOSP's own
     *  unresolvable-reference contract). */
    private static Set<String> resolveStringSet(String v, Application app) {
        if (v == null || !v.startsWith("@")) return null;
        int slash = v.indexOf('/');
        if (slash < 0) return null;
        String type = v.substring(1, slash);
        String name = v.substring(slash + 1);
        if (!"array".equals(type)) return null;
        try {
            android.content.res.Resources r = app.getResources();
            if (r == null) return null;
            int id = r.getIdentifier(name, type, app.getPackageName());
            if (id == 0) return null;
            String[] arr = r.getStringArray(id);
            if (arr == null) return null;
            return new LinkedHashSet<>(Arrays.asList(arr));
        } catch (Throwable t) {
            return null;
        }
    }

    private static Boolean resolveBoolean(String v, Application app) {
        if (v == null) return null;
        if ("true".equals(v))  return true;
        if ("false".equals(v)) return false;
        if (v.startsWith("@bool/")) {
            android.content.res.Resources r = app.getResources();
            if (r == null) return null;
            int id = r.getIdentifier(v.substring("@bool/".length()),
                                       "bool", app.getPackageName());
            if (id == 0) return null;
            try { return r.getBoolean(id); } catch (Throwable t) { return null; }
        }
        return null;
    }

    private static Integer resolveInteger(String v, Application app) {
        if (v == null) return null;
        if (v.startsWith("@integer/")) {
            android.content.res.Resources r = app.getResources();
            if (r == null) return null;
            int id = r.getIdentifier(v.substring("@integer/".length()),
                                       "integer", app.getPackageName());
            if (id == 0) return null;
            try { return r.getInteger(id); } catch (Throwable t) { return null; }
        }
        try { return Integer.parseInt(v); }
        catch (NumberFormatException e) { return null; }
    }

    /** Resolve @string/foo / @bool/foo / @integer/foo references to
     *  their actual values. Bare strings (no @) are returned as-is. */
    private static String resolveReference(String v, Application app) {
        if (v == null || v.isEmpty()) return v;
        if (!v.startsWith("@")) return v;
        // @<type>/<name> form
        int slash = v.indexOf('/');
        if (slash < 0) return v;
        String type = v.substring(1, slash);
        String name = v.substring(slash + 1);
        try {
            android.content.res.Resources r = app.getResources();
            if (r == null) return null;
            int id = r.getIdentifier(name, type, app.getPackageName());
            if (id == 0) return null;
            switch (type) {
                case "string": return r.getString(id);
                case "bool":   return String.valueOf(r.getBoolean(id));
                case "integer":return String.valueOf(r.getInteger(id));
                // Was `return v` (the raw unresolved token, e.g.
                // "@array/antifeaturesValuesDefault"): that persisted a
                // literal, meaningless placeholder string for ANY
                // reference type this switch doesn't model, poisoning
                // the key with contains()==true and the WRONG runtime
                // type forever (this is what corrupted F-Droid's
                // "showAntiFeatures" MultiSelectListPreference key --
                // see resolveStringSet, which now handles the array
                // case properly before falling here at all). An
                // unresolvable reference must behave like "no default
                // value", i.e. null -- never write a placeholder.
                default:       return null;
            }
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object invokeStatic(String clsName, String method) throws Exception {
        Class<?> cls = Class.forName(clsName);
        java.lang.reflect.Method m = cls.getDeclaredMethod(method);
        m.setAccessible(true);
        return m.invoke(null);
    }
    private static Object invoke(Object target, String method) throws Exception {
        java.lang.reflect.Method m = target.getClass().getDeclaredMethod(method);
        m.setAccessible(true);
        return m.invoke(target);
    }
}
