// android.os.PersistableBundle -- like Bundle but restricted to primitives +
// String + nested PersistableBundle (so it can be persisted to disk). Extends
// BaseBundle on AOSP since API 21 and implements Parcelable + Cloneable. Most
// accessors are inherited from BaseBundle; this file adds EMPTY, the nested-
// PersistableBundle accessors, clone, and the (in-process no-op) Parcelable
// surface so JobInfo / app-restrictions / Bundle.putAll(PersistableBundle)
// round-trip.
//
// AOSP ref: frameworks/base/core/java/android/os/PersistableBundle.java
package android.os;

public final class PersistableBundle extends BaseBundle implements Parcelable, Cloneable {
    public static final PersistableBundle EMPTY = new PersistableBundle();

    public PersistableBundle() { super(); }
    public PersistableBundle(int capacity) { super(capacity); }

    public PersistableBundle(PersistableBundle b) {
        super();
        if (b != null) { b.unparcel(); unparcel(); map.putAll(b.map); }
    }

    // AOSP copy ctor from a Bundle keeps only persistable-typed entries; in our
    // single-VM model we copy everything (the map is type-agnostic and apps that
    // store non-persistable values into a PersistableBundle simply read them back
    // unchanged here, which is harmless without real disk serialization).
    public PersistableBundle(Bundle b) {
        super();
        if (b != null) { b.unparcel(); unparcel(); map.putAll(b.map); }
    }

    /** AOSP's package-private PersistableBundle(Parcel, int): take the slice and
     *  defer the unparcel, exactly as Bundle does. Parcel.readPersistableBundle
     *  is the only caller. */
    PersistableBundle(Parcel parcelledData, int length) { super(parcelledData, length); }

    public void putPersistableBundle(String key, PersistableBundle value) {
        unparcel();
        map.put(key, value);
    }
    public PersistableBundle getPersistableBundle(String key) {
        unparcel();
        Object o = map.get(key);
        return (o instanceof PersistableBundle) ? (PersistableBundle) o : null;
    }

    @Override public Object clone() { return new PersistableBundle(this); }

    /** AOSP core/java/android/os/PersistableBundle.java:212 --
     *  `new PersistableBundle(this, /* deep *\/ true)`. The distinction from
     *  clone()/the copy constructor is that nested PersistableBundles and
     *  arrays are COPIED rather than shared, so mutating the copy cannot be
     *  observed through the original. */
    public PersistableBundle deepCopy() {
        unparcel();
        PersistableBundle out = new PersistableBundle();
        out.unparcel();
        for (java.util.Map.Entry<String, Object> e : map.entrySet()) {
            out.map.put(e.getKey(), deepCopyValue(e.getValue()));
        }
        return out;
    }

    private static Object deepCopyValue(Object v) {
        if (v instanceof PersistableBundle) return ((PersistableBundle) v).deepCopy();
        if (v instanceof int[])     return ((int[]) v).clone();
        if (v instanceof long[])    return ((long[]) v).clone();
        if (v instanceof double[])  return ((double[]) v).clone();
        if (v instanceof boolean[]) return ((boolean[]) v).clone();
        if (v instanceof String[])  return ((String[]) v).clone();
        // Everything else a PersistableBundle may legally hold is immutable
        // (String and the boxed primitives), so sharing the reference IS a
        // deep copy for those.
        return v;
    }

    // ---- XML persistence (AOSP :450 / :471) --------------------------------
    // AOSP writes <bundle> wrapping XmlUtils.writeMapXml(), with a nested
    // PersistableBundle written under the tag "pbundle_as_map"
    // (PersistableBundle.java:57 TAG_PERSISTABLEMAP). The element shapes below
    // are XmlUtils.writeValueXml()'s, verbatim:
    //   <null    name="k" />
    //   <string  name="k">text</string>
    //   <int     name="k" value="1" />      (also long / float / double / boolean)
    //   <int-array name="k" num="2"><item value="1"/>...</int-array>
    //             (also long-array / double-array / boolean-array / string-array)
    //   <pbundle_as_map name="k"> ...nested... </pbundle_as_map>
    // Only the PERSISTABLE value types are handled, matching AOSP's own
    // saveToXml, which drops anything else before writing ("Dropping bad data
    // before persisting").
    // One deliberate divergence, noted rather than hidden: AOSP writes a null
    // String[] ELEMENT as out.attribute(null, "value", null), which its own
    // serializer rejects; we omit the attribute and read a missing one back as
    // null, so a String[] containing nulls round-trips here instead of
    // throwing.
    private static final String TAG_PERSISTABLEMAP = "pbundle_as_map";

    public void writeToStream(java.io.OutputStream outputStream) throws java.io.IOException {
        org.xmlpull.v1.XmlSerializer out = android.util.Xml.newSerializer();
        out.setOutput(outputStream, "UTF-8");
        out.startTag(null, "bundle");
        saveToXml(out);
        out.endTag(null, "bundle");
        out.flush();
    }

    private void saveToXml(org.xmlpull.v1.XmlSerializer out) throws java.io.IOException {
        unparcel();
        for (java.util.Map.Entry<String, Object> e : map.entrySet()) {
            writeValueXml(e.getValue(), e.getKey(), out);
        }
    }

    private static void writeValueXml(Object v, String name, org.xmlpull.v1.XmlSerializer out)
            throws java.io.IOException {
        if (v == null) {
            startNamed(out, "null", name); out.endTag(null, "null");
        } else if (v instanceof String) {
            startNamed(out, "string", name); out.text((String) v); out.endTag(null, "string");
        } else if (v instanceof Integer) {
            valueTag(out, "int", name, v.toString());
        } else if (v instanceof Long) {
            valueTag(out, "long", name, v.toString());
        } else if (v instanceof Float) {
            valueTag(out, "float", name, v.toString());
        } else if (v instanceof Double) {
            valueTag(out, "double", name, v.toString());
        } else if (v instanceof Boolean) {
            valueTag(out, "boolean", name, v.toString());
        } else if (v instanceof int[]) {
            int[] a = (int[]) v;
            startArray(out, "int-array", name, a.length);
            for (int x : a) itemValue(out, Integer.toString(x));
            out.endTag(null, "int-array");
        } else if (v instanceof long[]) {
            long[] a = (long[]) v;
            startArray(out, "long-array", name, a.length);
            for (long x : a) itemValue(out, Long.toString(x));
            out.endTag(null, "long-array");
        } else if (v instanceof double[]) {
            double[] a = (double[]) v;
            startArray(out, "double-array", name, a.length);
            for (double x : a) itemValue(out, Double.toString(x));
            out.endTag(null, "double-array");
        } else if (v instanceof boolean[]) {
            boolean[] a = (boolean[]) v;
            startArray(out, "boolean-array", name, a.length);
            for (boolean x : a) itemValue(out, Boolean.toString(x));
            out.endTag(null, "boolean-array");
        } else if (v instanceof String[]) {
            String[] a = (String[]) v;
            startArray(out, "string-array", name, a.length);
            for (String x : a) itemValue(out, x);
            out.endTag(null, "string-array");
        } else if (v instanceof PersistableBundle) {
            startNamed(out, TAG_PERSISTABLEMAP, name);
            ((PersistableBundle) v).saveToXml(out);
            out.endTag(null, TAG_PERSISTABLEMAP);
        }
        // else: AOSP drops non-persistable values before writing. Same here.
    }

    private static void startNamed(org.xmlpull.v1.XmlSerializer out, String tag, String name)
            throws java.io.IOException {
        out.startTag(null, tag);
        if (name != null) out.attribute(null, "name", name);
    }
    private static void valueTag(org.xmlpull.v1.XmlSerializer out, String tag, String name,
                                 String value) throws java.io.IOException {
        startNamed(out, tag, name);
        out.attribute(null, "value", value);
        out.endTag(null, tag);
    }
    private static void startArray(org.xmlpull.v1.XmlSerializer out, String tag, String name,
                                   int num) throws java.io.IOException {
        startNamed(out, tag, name);
        out.attribute(null, "num", Integer.toString(num));
    }
    private static void itemValue(org.xmlpull.v1.XmlSerializer out, String value)
            throws java.io.IOException {
        out.startTag(null, "item");
        if (value != null) out.attribute(null, "value", value);
        out.endTag(null, "item");
    }

    public static PersistableBundle readFromStream(java.io.InputStream inputStream)
            throws java.io.IOException {
        try {
            org.xmlpull.v1.XmlPullParser parser = android.util.Xml.newPullParser();
            parser.setInput(inputStream, "UTF-8");
            int ev;
            while ((ev = parser.next()) != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (ev == org.xmlpull.v1.XmlPullParser.START_TAG) {
                    // The outer <bundle> wrapper written by writeToStream.
                    return restoreFromXml(parser);
                }
            }
            return new PersistableBundle();
        } catch (org.xmlpull.v1.XmlPullParserException e) {
            throw new java.io.IOException(e);
        }
    }

    // Reads the CHILDREN of the currently-open container tag into a bundle and
    // consumes that container's END_TAG.
    private static PersistableBundle restoreFromXml(org.xmlpull.v1.XmlPullParser in)
            throws java.io.IOException, org.xmlpull.v1.XmlPullParserException {
        PersistableBundle b = new PersistableBundle();
        b.unparcel();
        final int outerDepth = in.getDepth();
        int ev;
        while ((ev = in.next()) != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (ev == org.xmlpull.v1.XmlPullParser.END_TAG && in.getDepth() <= outerDepth) {
                break;
            }
            if (ev != org.xmlpull.v1.XmlPullParser.START_TAG) continue;
            String tag = in.getName();
            String name = in.getAttributeValue(null, "name");
            Object v = readValueXml(in, tag);
            b.map.put(name, v);
        }
        return b;
    }

    private static Object readValueXml(org.xmlpull.v1.XmlPullParser in, String tag)
            throws java.io.IOException, org.xmlpull.v1.XmlPullParserException {
        String value = in.getAttributeValue(null, "value");
        if ("null".equals(tag))    { skipToEnd(in, tag); return null; }
        if ("string".equals(tag))  { return readTextTo(in, tag); }
        if ("int".equals(tag))     { skipToEnd(in, tag); return Integer.valueOf(value); }
        if ("long".equals(tag))    { skipToEnd(in, tag); return Long.valueOf(value); }
        if ("float".equals(tag))   { skipToEnd(in, tag); return Float.valueOf(value); }
        if ("double".equals(tag))  { skipToEnd(in, tag); return Double.valueOf(value); }
        if ("boolean".equals(tag)) { skipToEnd(in, tag); return Boolean.valueOf(value); }
        if (TAG_PERSISTABLEMAP.equals(tag)) { return restoreFromXml(in); }
        if (tag.endsWith("-array")) {
            int num = Integer.parseInt(in.getAttributeValue(null, "num"));
            java.util.ArrayList<String> items = new java.util.ArrayList<>(num);
            final int outerDepth = in.getDepth();
            int ev;
            while ((ev = in.next()) != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (ev == org.xmlpull.v1.XmlPullParser.END_TAG && in.getDepth() <= outerDepth) break;
                if (ev == org.xmlpull.v1.XmlPullParser.START_TAG && "item".equals(in.getName())) {
                    items.add(in.getAttributeValue(null, "value"));
                }
            }
            int n = items.size();
            if ("int-array".equals(tag)) {
                int[] a = new int[n];
                for (int i = 0; i < n; i++) a[i] = Integer.parseInt(items.get(i));
                return a;
            }
            if ("long-array".equals(tag)) {
                long[] a = new long[n];
                for (int i = 0; i < n; i++) a[i] = Long.parseLong(items.get(i));
                return a;
            }
            if ("double-array".equals(tag)) {
                double[] a = new double[n];
                for (int i = 0; i < n; i++) a[i] = Double.parseDouble(items.get(i));
                return a;
            }
            if ("boolean-array".equals(tag)) {
                boolean[] a = new boolean[n];
                for (int i = 0; i < n; i++) a[i] = Boolean.parseBoolean(items.get(i));
                return a;
            }
            return items.toArray(new String[0]);   // string-array
        }
        skipToEnd(in, tag);
        return null;
    }

    private static String readTextTo(org.xmlpull.v1.XmlPullParser in, String tag)
            throws java.io.IOException, org.xmlpull.v1.XmlPullParserException {
        StringBuilder sb = new StringBuilder();
        final int outerDepth = in.getDepth();
        int ev;
        while ((ev = in.next()) != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (ev == org.xmlpull.v1.XmlPullParser.END_TAG && in.getDepth() <= outerDepth) break;
            if (ev == org.xmlpull.v1.XmlPullParser.TEXT) sb.append(in.getText());
        }
        return sb.toString();
    }

    private static void skipToEnd(org.xmlpull.v1.XmlPullParser in, String tag)
            throws java.io.IOException, org.xmlpull.v1.XmlPullParserException {
        final int outerDepth = in.getDepth();
        int ev;
        while ((ev = in.next()) != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (ev == org.xmlpull.v1.XmlPullParser.END_TAG && in.getDepth() <= outerDepth) break;
        }
    }

    // toString comes from BaseBundle, which derives the "PersistableBundle[...]"
    // prefix from the runtime class AND reports the byte count while the bundle
    // is still parcelled. Overriding it here would lose the second half.

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {
        // The same length + magic + ArrayMap framing Bundle uses. The old body
        // was dest.writeValue(this), which recursed straight back here through
        // VAL_PERSISTABLEBUNDLE once writeValue learned to marshal.
        writeToParcelInner(dest, flags);
    }

    public static final Parcelable.Creator<PersistableBundle> CREATOR =
        new Parcelable.Creator<PersistableBundle>() {
            @Override public PersistableBundle createFromParcel(Parcel in) {
                return in.readPersistableBundle();
            }
            @Override public PersistableBundle[] newArray(int size) {
                return new PersistableBundle[size];
            }
        };
}
