package org.json;
import java.util.ArrayList;
import java.util.List;
public class JSONArray {
    private final ArrayList<Object> items = new ArrayList<>();
    public JSONArray() {}
    public JSONArray(JSONTokener t) throws JSONException {
        if ("1".equals(System.getenv("MACAPK_JSON_NOPARSE"))) return;  // see JSONObject
        Object parsed = t.nextValue();
        if (parsed instanceof JSONArray a) {
            this.items.addAll(a.items);
        } else {
            throw new JSONException("JSON text must start with '[', got "
                + (parsed == null ? "null" : parsed.getClass().getName()));
        }
    }
    public JSONArray(String s) throws JSONException {
        this(new JSONTokener(s));
    }
    /** AOSP: each element is coerced via JSONObject.wrap(), not stored raw --
     *  e.g. a plain java.util.Map element becomes a nested JSONObject
     *  (recursively wrapped) instead of an opaque Object whose toString()
     *  would later serialize as a non-JSON blob. Mirrors libcore
     *  json/src/main/java/org/json/JSONArray.java JSONArray(Collection). */
    public JSONArray(java.util.Collection<?> c) {
        if (c != null) for (Object o : c) items.add(JSONObject.wrap(o));
    }
    // AOSP JSONArray(Object array) wraps ANY Java array (primitive int[]/
    // String[]/etc, not just a Collection) via reflection, calling
    // JSONObject.wrap() on each element. Verified verbatim against AOSP:
    //   if (!array.getClass().isArray()) throw new JSONException(...);
    //   final int length = Array.getLength(array);
    //   values = new ArrayList<Object>(length);
    //   for (int i = 0; i < length; ++i) put(JSONObject.wrap(Array.get(array, i)));
    // (values.add == our items.add via put(Object), so we reuse put() below
    // rather than touching the private field directly.) Deliberately NOT
    // null-guarded: AOSP calls array.getClass() unconditionally, so a null
    // argument NPEs there too -- matching that exactly rather than softening
    // it to a JSONException, per this file's array==null callers already
    // being expected to not pass null (same as real Android).
    public JSONArray(Object array) throws JSONException {
        if (!array.getClass().isArray()) {
            throw new JSONException("Not a primitive array: " + array.getClass());
        }
        int length = java.lang.reflect.Array.getLength(array);
        for (int i = 0; i < length; ++i) {
            put(JSONObject.wrap(java.lang.reflect.Array.get(array, i)));
        }
    }
    public int    length()        { return items.size(); }
    /** AOSP: an out-of-range index still throws (unchecked
     *  IndexOutOfBoundsException on real Android, since get() there is a
     *  bare List.get with no bounds guard of its own); an IN-RANGE slot
     *  holding a raw Java null throws JSONException("Value at i is null.")
     *  -- reachable via put(i,(Object)null) / put((Object)null), since our
     *  index-growth padding uses the JSONObject.NULL sentinel, not raw
     *  null, so a parsed JSON `null` element (stored as the sentinel) is
     *  NOT affected by this check and still reads back successfully. */
    public Object get(int i) throws JSONException {
        if (i < 0 || i >= items.size()) throw new JSONException("oob");
        Object v = items.get(i);
        if (v == null) throw new JSONException("Value at " + i + " is null.");
        return v;
    }
    public Object opt(int i)      { return i >= 0 && i < items.size() ? items.get(i) : null; }
    // Strict getters: coerce via the shared JSON helper (see JSON.java),
    // matching AOSP libcore's org.json.JSONArray exactly -- e.g. getInt
    // truncates a decimal String ("3.5" -> 3) rather than throwing, and
    // getString on a JSON-null element (JSONObject.NULL) returns the
    // 4-character STRING "null" rather than throwing (get() already
    // rejects a raw-null slot above; NULL the sentinel is not raw null).
    public String  getString(int i)  throws JSONException {
        Object v = get(i);
        String result = JSON.toString(v);
        if (result == null) throw JSON.typeMismatch(i, v, "String");
        return result;
    }
    public int     getInt(int i)     throws JSONException {
        Object v = get(i);
        Integer result = JSON.toInteger(v);
        if (result == null) throw JSON.typeMismatch(i, v, "int");
        return result;
    }
    public long    getLong(int i)    throws JSONException {
        Object v = get(i);
        Long result = JSON.toLong(v);
        if (result == null) throw JSON.typeMismatch(i, v, "long");
        return result;
    }
    public double  getDouble(int i)  throws JSONException {
        Object v = get(i);
        Double result = JSON.toDouble(v);
        if (result == null) throw JSON.typeMismatch(i, v, "double");
        return result;
    }
    public boolean getBoolean(int i) throws JSONException {
        Object v = get(i);
        Boolean result = JSON.toBoolean(v);
        if (result == null) throw JSON.typeMismatch(i, v, "boolean");
        return result;
    }
    public String optString(int i){ return optString(i, ""); }
    public String optString(int i, String fallback){
        String result = JSON.toString(opt(i));
        return result != null ? result : fallback;
    }
    public int    optInt(int i)   { return optInt(i, 0); }
    public int    optInt(int i, int fallback){
        Integer result = JSON.toInteger(opt(i));
        return result != null ? result : fallback;
    }
    public long   optLong(int i)  { return optLong(i, 0L); }
    public long   optLong(int i, long fallback){
        Long result = JSON.toLong(opt(i));
        return result != null ? result : fallback;
    }
    // NOTE: default is 0.0, NOT AOSP's NaN. Mac.apk shipped 0.0 here
    // long before this turn; keeping it is the conservative no-regression
    // choice (switching to NaN had no proven benefit and risks poisoning
    // any caller that does arithmetic on a defaulted element, since NaN
    // propagates through every sum/transform). Callers that genuinely
    // want NaN/another sentinel use the explicit-fallback overload below.
    // The STRING-COERCION gap is still fixed (a value stored as the String
    // "3.5" now yields 3.5 instead of always falling through to 0.0).
    public double optDouble(int i){ return optDouble(i, 0.0); }
    public double optDouble(int i, double fallback){
        Double result = JSON.toDouble(opt(i));
        return result != null ? result : fallback;
    }
    public boolean optBoolean(int i){ return optBoolean(i, false); }
    public boolean optBoolean(int i, boolean fallback){
        Boolean result = JSON.toBoolean(opt(i));
        return result != null ? result : fallback;
    }
    public boolean isNull(int i)  { Object v = opt(i); return v == null || v == JSONObject.NULL; }
    public JSONObject getJSONObject(int i) throws JSONException { Object v = get(i); if (v instanceof JSONObject o) return o; throw JSON.typeMismatch(i, v, "JSONObject"); }
    public JSONArray  getJSONArray(int i)  throws JSONException { Object v = get(i); if (v instanceof JSONArray a)  return a;  throw JSON.typeMismatch(i, v, "JSONArray"); }
    public JSONObject optJSONObject(int i) { Object v = opt(i); return v instanceof JSONObject ? (JSONObject) v : null; }
    public JSONArray  optJSONArray(int i)  { Object v = opt(i); return v instanceof JSONArray  ? (JSONArray)  v : null; }
    // AOSP: only the primitive-double overloads validate (a primitive
    // double can be NaN/Infinite; a plain Object might already be a
    // pre-validated Number, or NULL, or a String, so the generic put(Object)
    // does not re-check it -- mirrors libcore json/src/main/java/org/json/
    // JSONArray.java exactly: put(double) calls JSON.checkDouble(), the
    // other no-index put() overloads and put(int,Object) do not... except
    // put(int,Object), which separately validates a Number argument (see
    // below) because that is the one AOSP method every put(index,primitive)
    // overload delegates through.
    public JSONArray put(Object v) { items.add(v); return this; }
    public JSONArray put(int v)    { items.add(v); return this; }
    public JSONArray put(long v)   { items.add(v); return this; }
    public JSONArray put(double v) throws JSONException { items.add(JSON.checkDouble(v)); return this; }
    public JSONArray put(boolean v){ items.add(v); return this; }
    /** AOSP: validates a Number argument (NaN/Infinite rejected) before
     *  padding/inserting; padding uses the JSONObject.NULL sentinel (see
     *  get(int)'s doc above for why that -- not raw null -- is what a
     *  grown gap contains). */
    public JSONArray put(int idx, Object v) throws JSONException {
        if (v instanceof Number n) JSON.checkDouble(n.doubleValue());
        while (items.size() <= idx) items.add(JSONObject.NULL);
        items.set(idx, v);
        return this;
    }
    // AOSP: these are one-line boxing delegates to put(int,Object), e.g.
    // `public JSONArray put(int index, boolean value) { return put(index, (Boolean) value); }`.
    // Were missing entirely (only the no-index put(primitive) overloads existed).
    public JSONArray put(int index, boolean value) throws JSONException { return put(index, (Boolean) value); }
    public JSONArray put(int index, double value)  throws JSONException { return put(index, (Double) value); }
    public JSONArray put(int index, int value)     throws JSONException { return put(index, (Integer) value); }
    public JSONArray put(int index, long value)    throws JSONException { return put(index, (Long) value); }
    public Object remove(int i)    { return i >= 0 && i < items.size() ? items.remove(i) : null; }
    /** AOSP: swallows JSONException and returns Java null, matching
     *  JSONObject.toString() -- see that file's toString() for why. */
    @Override public String toString() {
        try {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < items.size(); ++i) { if (i > 0) sb.append(','); sb.append(JSONObject.formatValue(items.get(i))); }
            return sb.append(']').toString();
        } catch (JSONException e) {
            return null;
        }
    }
    /** AOSP: real indent-aware pretty-printing via JSONStringer (previously
     *  discarded the indent argument, see JSONObject.toString(int) for the
     *  identical fix). */
    public String toString(int indentSpaces) throws JSONException {
        JSONStringer stringer = new JSONStringer(indentSpaces);
        writeTo(stringer);
        return stringer.toString();
    }

    /** AOSP package-private serialization used by JSONStringer. */
    void writeTo(JSONStringer stringer) throws JSONException {
        stringer.array();
        for (Object value : items) stringer.value(value);
        stringer.endArray();
    }

    /** AOSP JSONArray.toJSONObject: pair each name with this array's value at
     *  the same index. Returns null if names/values are empty. */
    public JSONObject toJSONObject(JSONArray names) throws JSONException {
        if (names == null || names.length() == 0 || length() == 0) return null;
        JSONObject result = new JSONObject();
        for (int i = 0; i < names.length(); i++) {
            result.put(names.getString(i), opt(i));
        }
        return result;
    }
}


