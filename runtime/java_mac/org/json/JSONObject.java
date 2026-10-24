// org.json.JSONObject. Backs onto a LinkedHashMap; covers the
// get/put/has/optX/toString surface plus real string parsing via
// JSONTokener (JSONObject(String) / JSONObject(JSONTokener)), mirroring
// AOSP libcore's org.json. The parser was added after Vector Pinball
// black-screened: it loads its table layouts with
// `new JSONObject(jsonString)`, which the old stub turned into an empty
// object (no elements -> black GL field, no error). See JSONTokener.java.
package org.json;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class JSONObject {
    public static final Object NULL = new Object() {
        @Override public boolean equals(Object o) { return o == null || o == this; }
        @Override public int hashCode() { return 0; }
        @Override public String toString() { return "null"; }
    };
    /** AOSP org.json.JSON.NEGATIVE_ZERO (inlined -- we have no JSON helper
     *  class): numberToString() special-cases -0.0 so it prints as "-0"
     *  rather than "0" or "-0.0". */
    private static final Double NEGATIVE_ZERO = -0d;

    private final LinkedHashMap<String, Object> map = new LinkedHashMap<>();

    public JSONObject() {}
    /** AOSP: a null value is dropped rather than stored (matches
     *  put(String,Object)'s null-removes-key contract below), inlined here
     *  rather than routed through put() so this constructor's signature
     *  does not have to start declaring throws JSONException. */
    // AOSP libcore json/src/main/java/org/json/JSONObject.java:
    //   public JSONObject(@NonNull Map copyFrom) {
    //       this();
    //       Map<?, ?> contentsTyped = (Map<?, ?>) copyFrom;
    //       for (Map.Entry<?, ?> entry : contentsTyped.entrySet()) {
    //           String key = (String) entry.getKey();
    //           if (key == null) { throw new NullPointerException("key == null"); }
    //           nameValuePairs.put(key, wrap(entry.getValue()));
    //       }
    //   }
    // Every VALUE goes through wrap(): a nested Map becomes a JSONObject, a
    // Collection / array a JSONArray, null becomes JSONObject.NULL. The old copy
    // stored values raw, so optJSONObject("CognitoUserPool") on a config built
    // as `new JSONObject(mapOf("CognitoUserPool" to mapOf(...)))` answered null
    // (the value was a java.util.Map) -- Efteling's AWSMobileClient then threw
    // "Neither Cognito Identity or Cognito UserPool was used" out of
    // Application.onCreate, which skipped the rest of the app's init.
    // MACAPK_JSON_MAP_WRAP=0 restores the old raw copy.
    static final boolean MAP_WRAP = !"0".equals(System.getenv("MACAPK_JSON_MAP_WRAP"));

    public JSONObject(Map<?, ?> copy) {
        if (!MAP_WRAP) {
            if (copy != null) for (Map.Entry<?, ?> e : copy.entrySet()) {
                Object v = e.getValue();
                if (v != null) map.put(String.valueOf(e.getKey()), v);
            }
            return;
        }
        Map<?, ?> contentsTyped = (Map<?, ?>) copy;
        for (Map.Entry<?, ?> entry : contentsTyped.entrySet()) {
            String key = (String) entry.getKey();
            if (key == null) {
                throw new NullPointerException("key == null");
            }
            map.put(key, wrap(entry.getValue()));
        }
    }
    public JSONObject(JSONTokener tok) throws JSONException {
        // MACAPK_JSON_NOPARSE=1: emergency rollback / A-B isolation knob.
        // Reverts to the pre-v1.0.288 stub behavior (empty object, no
        // parse) so a regression suspected to come from real JSON parsing
        // can be confirmed without a rebuild. Default-unset = real parse.
        if ("1".equals(System.getenv("MACAPK_JSON_NOPARSE"))) return;
        // JSONTokener does the real parse; the next value must be an object.
        Object parsed = tok.nextValue();
        if (parsed instanceof JSONObject o) {
            this.map.putAll(o.map);
        } else {
            throw new JSONException("JSON text must start with '{', got "
                + (parsed == null ? "null" : parsed.getClass().getName()));
        }
    }
    public JSONObject(String json) throws JSONException {
        this(new JSONTokener(json));
    }
    /** AOSP: selective copy -- only the requested names, read via opt() so
     *  a name absent from copyFrom is silently skipped (not defaulted or
     *  thrown). Mirrors libcore json/src/main/java/org/json/JSONObject.java. */
    public JSONObject(JSONObject copyFrom, String[] names) throws JSONException {
        this();
        if (copyFrom != null && names != null) {
            for (String name : names) {
                Object value = copyFrom.opt(name);
                if (value != null) map.put(name, value);
            }
        }
    }

    public int    length() { return map.size(); }
    public Iterator<String> keys() { return map.keySet().iterator(); }
    /** AOSP: throws whenever the stored value is null. put(String,Object)
     *  never stores a raw null (a null value REMOVES the key, matching
     *  AOSP), so in practice this fires exactly when the key is absent. */
    public Object get(String key) throws JSONException {
        Object v = map.get(key);
        if (v == null) throw new JSONException("No value for " + key);
        return v;
    }
    public Object opt(String key)     { return map.get(key); }

    /** Strict getters -- throw JSONException if the key is absent or the
     *  value can't coerce to the requested type. Coercion mirrors AOSP
     *  libcore's org.json.JSON helper exactly (see JSON.java): e.g.
     *  getInt on the String "3" or "3.5" succeeds (matching AOSP's
     *  (int) Double.parseDouble truncation), where a naive
     *  Integer.parseInt-based coercion would throw on "3.5". */
    public String  getString(String key) throws JSONException {
        Object v = get(key);
        String result = JSON.toString(v);
        if (result == null) throw JSON.typeMismatch(key, v, "String");
        return result;
    }
    public int     getInt(String key) throws JSONException {
        Object v = get(key);
        Integer result = JSON.toInteger(v);
        if (result == null) throw JSON.typeMismatch(key, v, "int");
        return result;
    }
    public long    getLong(String key) throws JSONException {
        Object v = get(key);
        Long result = JSON.toLong(v);
        if (result == null) throw JSON.typeMismatch(key, v, "long");
        return result;
    }
    public double  getDouble(String key) throws JSONException {
        Object v = get(key);
        Double result = JSON.toDouble(v);
        if (result == null) throw JSON.typeMismatch(key, v, "double");
        return result;
    }
    public boolean getBoolean(String key) throws JSONException {
        Object v = get(key);
        Boolean result = JSON.toBoolean(v);
        if (result == null) throw JSON.typeMismatch(key, v, "boolean");
        return result;
    }
    public JSONObject getJSONObject(String key) throws JSONException {
        Object v = get(key);
        if (v instanceof JSONObject o) return o;
        throw JSON.typeMismatch(key, v, "JSONObject");
    }
    public JSONArray  getJSONArray(String key) throws JSONException {
        Object v = get(key);
        if (v instanceof JSONArray a) return a;
        throw JSON.typeMismatch(key, v, "JSONArray");
    }

    // opt*: like the getters, but return a fallback instead of throwing.
    // Coercion goes through the same JSON helper as the strict getters (see
    // JSON.java), so e.g. a value stored as the STRING "42" is returned as
    // 42 by optInt -- previously only a boxed Number satisfied optInt/
    // optLong/optDouble/optBoolean at all; a numeric-looking String always
    // fell through to the fallback, which AOSP does not do.
    public String optString(String key) { return optString(key, ""); }
    public String optString(String key, String fallback) {
        String result = JSON.toString(map.get(key));
        return result != null ? result : fallback;
    }
    public int    optInt(String key)     { return optInt(key, 0); }
    public int    optInt(String key, int f) {
        Integer result = JSON.toInteger(map.get(key));
        return result != null ? result : f;
    }
    public long   optLong(String key)    { return optLong(key, 0L); }
    public long   optLong(String key, long f) {
        Long result = JSON.toLong(map.get(key));
        return result != null ? result : f;
    }
    /** AOSP default is NaN (unlike JSONArray.optDouble(int), which Mac.apk
     *  deliberately keeps at a pre-existing 0.0 default -- see that file). */
    public double optDouble(String key)  { return optDouble(key, Double.NaN); }
    public double optDouble(String key, double f) {
        Double result = JSON.toDouble(map.get(key));
        return result != null ? result : f;
    }
    public boolean optBoolean(String key){ return optBoolean(key, false); }
    public boolean optBoolean(String key, boolean f) {
        Boolean result = JSON.toBoolean(map.get(key));
        return result != null ? result : f;
    }
    public boolean has(String key)       { return map.containsKey(key); }
    public JSONArray  optJSONArray(String key)  { Object v = map.get(key); return v instanceof JSONArray a ? a : null; }
    public JSONObject optJSONObject(String key) { Object v = map.get(key); return v instanceof JSONObject o ? o : null; }
    public boolean isNull(String key)    { Object v = map.get(key); return v == null || v == NULL; }
    public Object remove(String key)     { return map.remove(key); }

    /** AOSP: a null value REMOVES the key (JSON has no way to represent a
     *  Java null distinct from absence; use JSONObject.NULL for a JSON
     *  null). A Number is validated eagerly (NaN/Infinite are not valid
     *  JSON numbers) so a bad value is caught at the put() call site
     *  rather than surfacing later as a null/garbled toString(). */
    public JSONObject put(String key, Object value) throws JSONException {
        if (value == null) { map.remove(key); return this; }
        if (value instanceof Number n) JSON.checkDouble(n.doubleValue());
        map.put(key, value);
        return this;
    }
    public JSONObject put(String key, int value)    { map.put(key, value); return this; }
    public JSONObject put(String key, long value)   { map.put(key, value); return this; }
    /** AOSP: boxes and delegates to put(String,Object) so the NaN/Infinite
     *  check above applies uniformly (a primitive double can't be null, but
     *  it can still be non-finite). */
    public JSONObject put(String key, double value) throws JSONException { return put(key, (Double) value); }
    public JSONObject put(String key, boolean value){ map.put(key, value); return this; }
    /** AOSP putOpt: a true no-op (not a throw) when name or value is
     *  null -- lets callers build up an object from optional fields
     *  without null-checking every one themselves. */
    public JSONObject putOpt(String name, Object value) throws JSONException {
        if (name == null || value == null) return this;
        return put(name, value);
    }
    /** AOSP accumulate: like put(), but a repeated key promotes the value
     *  to a JSONArray instead of overwriting -- the first repeat wraps the
     *  existing single value, later repeats just append. */
    public JSONObject accumulate(String name, Object value) throws JSONException {
        if (name == null) throw new JSONException("Names must be non-null");
        Object current = map.get(name);
        if (current == null) return put(name, value);
        if (current instanceof JSONArray array) {
            array.put(value);
        } else {
            JSONArray array = new JSONArray();
            array.put(current);
            array.put(value);
            map.put(name, array);
        }
        return this;
    }

    /** AOSP JSONObject.wrap: coerces an arbitrary Java object into a value
     *  safe to store in a JSONObject/JSONArray, the way put(String,Object)
     *  expects. Mirrors libcore json/src/main/java/org/json/JSONObject.java
     *  wrap(Object) exactly, with the array branch (AOSP's reflective
     *  JSONArray(Object) constructor, which our JSONArray does not carry)
     *  inlined via java.lang.reflect.Array against our existing put(Object). */
    public static Object wrap(Object o) {
        if (o == null) return NULL;
        if (o instanceof JSONArray || o instanceof JSONObject) return o;
        if (o.equals(NULL)) return o;
        try {
            if (o instanceof Collection) return new JSONArray((Collection<?>) o);
            if (o.getClass().isArray()) {
                JSONArray array = new JSONArray();
                int length = java.lang.reflect.Array.getLength(o);
                for (int i = 0; i < length; i++) {
                    array.put(wrap(java.lang.reflect.Array.get(o, i)));
                }
                return array;
            }
            if (o instanceof Map) return new JSONObject((Map<?, ?>) o);
            if (o instanceof Boolean || o instanceof Byte || o instanceof Character
                    || o instanceof Double || o instanceof Float || o instanceof Integer
                    || o instanceof Long || o instanceof Short || o instanceof String) {
                return o;
            }
            if (o.getClass().getPackage().getName().startsWith("java.")) {
                return o.toString();
            }
        } catch (Exception ignored) {
            // Falls through to null, matching AOSP.
        }
        return null;
    }

    /** AOSP JSONObject.numberToString: NaN/Infinite are forbidden JSON
     *  values (throws); -0.0 prints as "-0"; a double that round-trips
     *  exactly through longValue() prints as a plain integer (no trailing
     *  ".0"); anything else falls back to Number.toString(). Mirrors
     *  libcore json/src/main/java/org/json/JSONObject.java +
     *  org/json/JSON.java checkDouble(). */
    public static String numberToString(Number number) throws JSONException {
        if (number == null) throw new JSONException("Number must be non-null");
        double doubleValue = number.doubleValue();
        if (Double.isInfinite(doubleValue) || Double.isNaN(doubleValue)) {
            throw new JSONException("Forbidden numeric value: " + doubleValue);
        }
        if (number.equals(NEGATIVE_ZERO)) return "-0";
        long longValue = number.longValue();
        if (doubleValue == (double) longValue) return Long.toString(longValue);
        return number.toString();
    }

    public JSONArray names() {
        JSONArray a = new JSONArray();
        for (String k : map.keySet()) a.put(k);
        return a;
    }
    /** AOSP: projects just the given names into a JSONArray of their
     *  values (opt()-based, so a missing name contributes a null slot),
     *  or null if names is null/empty. */
    public JSONArray toJSONArray(JSONArray names) throws JSONException {
        if (names == null || names.length() == 0) return null;
        JSONArray result = new JSONArray();
        int length = names.length();
        for (int i = 0; i < length; i++) {
            Object rawName = names.opt(i);
            String name = rawName instanceof String s ? s
                    : (rawName != null ? String.valueOf(rawName) : null);
            result.put(opt(name));
        }
        return result;
    }

    /** AOSP: toString() SWALLOWS JSONException (e.g. a NaN/Infinite Number
     *  that slipped in through a path put() doesn't guard) and returns Java
     *  null, not "{}" and not a thrown exception. Mirrors
     *  libcore json/src/main/java/org/json/JSONObject.java toString(). */
    @Override public String toString() {
        try {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> e : map.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append('"').append(e.getKey()).append("\":");
                sb.append(formatValue(e.getValue()));
            }
            return sb.append('}').toString();
        } catch (JSONException e) {
            return null;
        }
    }
    /** AOSP: real indent-aware pretty-printing via JSONStringer (previously
     *  this discarded the indent argument and just called toString(),
     *  producing compact output regardless of what the caller asked for --
     *  JSONStringer already implements the indent-aware writer correctly,
     *  it just was not wired up here). */
    public String toString(int indentSpaces) throws JSONException {
        JSONStringer stringer = new JSONStringer(indentSpaces);
        writeTo(stringer);
        return stringer.toString();
    }
    /** AOSP: a Number formats via numberToString (integral doubles print
     *  without a trailing ".0", -0.0 prints as "-0", NaN/Infinite throw) --
     *  this is the same path AOSP's JSONStringer.value(Object) uses for the
     *  no-arg toString(), so a stored 3.0 renders as "3" here too, matching
     *  what real Android's org.json emits (previously this called Number's
     *  own toString() directly, e.g. "3.0"). */
    static String formatValue(Object v) throws JSONException {
        if (v == null || v == NULL) return "null";
        // Nested containers serialize as objects/arrays, not quoted strings,
        // so toString() round-trips back through the parser (apps that re-
        // encode parsed JSON to send to a server depend on this).
        if (v instanceof JSONObject || v instanceof JSONArray) return v.toString();
        if (v instanceof Number n) return numberToString(n);
        if (v instanceof Boolean) return v.toString();
        return quote(v.toString());
    }
    /** JSON-escape a string and wrap it in double quotes (AOSP-equivalent).
     *  public static per the real org.json API: apps (e.g. AppLovin mediation)
     *  call JSONObject.quote() directly. It MUST be public because the app's
     *  classes load under AppClassLoader while this shim loads under the host
     *  'app' loader, so the two org.json packages are DISTINCT runtime packages
     *  (same name, different loaders); a package-private quote() would deny the
     *  cross-loader call with IllegalAccessError. */
    public static String quote(String s) {
        // AOSP takes a @Nullable and answers "" (two quote characters) for it,
        // verbatim from platform/libcore json/src/main/java/org/json/JSONObject.java:773
        //
        //     @NonNull public static String quote(@Nullable String data) {
        //         if (data == null) {
        //             return "\"\"";
        //         }
        //
        // We went straight to s.length() and threw NPE instead. That is a FATAL
        // EXCEPTION on whatever thread called it, and ad-mediation SDKs call
        // quote() with a null field as a matter of course.
        //
        // Measured on Plague Inc (com.miniclip.plagueinc), which the 2026-08-27
        // full-fleet census recorded as one of only two DEAD apps:
        //     [E] Mac.apk.Boot: FATAL EXCEPTION: main
        //     java.lang.NullPointerException: Cannot invoke "String.length()"
        //         because "<parameter1>" is null
        //         at org.json.JSONObject.quote(JSONObject.java:339)
        //         at com.applovin.impl.mediation.f.a(SourceFile:170)
        //         ... ChartboostMediationAdapter$1.onStartCompleted ...
        // i.e. it killed the MAIN thread out of an ad-adapter completion
        // callback. The javadoc above already noted AppLovin calls this method
        // directly; it just did not cover the null it passes.
        if (s == null) return "\"\"";
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                // AOSP's org.json.JSONStringer#string also escapes '/' (both
                // "/" and "\/" are valid JSON; this matches Android's exact
                // output byte-for-byte for apps that compare/hash the text).
                case '/':  sb.append("\\/");  break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                case '\b': sb.append("\\b");  break;
                case '\f': sb.append("\\f");  break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }

    /** AOSP package-private serialization used by JSONStringer. */
    void writeTo(JSONStringer stringer) throws JSONException {
        stringer.object();
        for (Map.Entry<String, Object> e : map.entrySet()) {
            stringer.key(e.getKey()).value(e.getValue());
        }
        stringer.endObject();
    }
}

