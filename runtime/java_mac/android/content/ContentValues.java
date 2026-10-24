package android.content;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
// AOSP: `public final class ContentValues implements Parcelable`. The interface
// is load-bearing, not decorative: app bytecode compiled against the real
// android.jar does `bundle.putParcelable(k, contentValues)` /
// `intent.putExtra(k, (Parcelable) contentValues)`, and a class that does not
// actually implement the interface fails at the invoke/checkcast site rather
// than degrading -- so the whole containing method dies.
public final class ContentValues implements Parcelable {
    /** AOSP core/java/android/content/ContentValues.java -- the log tag,
     *  public API since API 1. */
    public static final String TAG = "ContentValues";

    private final HashMap<String, Object> map = new HashMap<>();
    public ContentValues() {}
    /** AOSP: `Preconditions.checkArgumentNonnegative(size)` before allocating the
     *  ArrayMap (ContentValues.java:63-66), so a negative hint is an
     *  IllegalArgumentException rather than a silently-ignored argument. */
    public ContentValues(int size) {
        if (size < 0) throw new IllegalArgumentException("size " + size + " must be >= 0");
    }
    /** AOSP: `Objects.requireNonNull(from)` (ContentValues.java:73-76). Copying
     *  from null is a caller bug and apps rely on the NPE to find it; a
     *  forgiving no-op would hand back an empty ContentValues that then writes
     *  an empty row. */
    public ContentValues(ContentValues from) {
        if (from == null) throw new NullPointerException();
        map.putAll(from.map);
    }
    /** AOSP has this private (kept "for lame people doing reflection") and it
     *  takes ownership of the map; we copy instead because our Parcel hands
     *  back the live object rather than a decoded copy, and unparcelling must
     *  yield an independent ContentValues the way a real Parcel round-trip does. */
    private ContentValues(HashMap<String, Object> from) { if (from != null) map.putAll(from); }
    /** AOSP's Parcel constructor: size hint first, then the key/value map. */
    private ContentValues(Parcel in) {
        in.readInt();
        @SuppressWarnings("unchecked")
        HashMap<String, Object> from = (HashMap<String, Object>) in.readHashMap(null);
        if (from != null) map.putAll(from);
    }
    /** AOSP dereferences `other.mMap` unconditionally (ContentValues.java:128-130),
     *  so putAll(null) is an NPE. Same reasoning as the copy constructor. */
    public void putAll(ContentValues other)  { map.putAll(other.map); }
    public void put(String k, String v)  { map.put(k, v); }
    public void put(String k, Integer v) { map.put(k, v); }
    public void put(String k, Long v)    { map.put(k, v); }
    public void put(String k, Short v)   { map.put(k, v); }
    public void put(String k, Byte v)    { map.put(k, v); }
    public void put(String k, Boolean v) { map.put(k, v); }
    public void put(String k, Float v)   { map.put(k, v); }
    public void put(String k, Double v)  { map.put(k, v); }
    public void put(String k, byte[] v)  { map.put(k, v); }
    public void putNull(String k)        { map.put(k, null); }
    public Object get(String k)          { return map.get(k); }
    public String getAsString(String k)  { Object v = map.get(k); return v != null ? v.toString() : null; }
    // ---- the getAs* coercion family -------------------------------------
    // AOSP is FAR more forgiving here than the obvious `instanceof Number`
    // reimplementation, and the difference is load-bearing rather than
    // cosmetic. Each getter casts to Number inside a try and, on the resulting
    // ClassCastException, RETRIES by PARSING the value when it is a
    // CharSequence (ContentValues.java:321-463; getAsInteger at :346-363,
    // getAsLong at :321-338). That retry is what makes the round trip through
    // DatabaseUtils.cursorRowToContentValues work at all: that helper stores
    // every non-blob column as a String, so a ContentValues freshly read off a
    // cursor holds "42" where the caller expects 42, and `instanceof Number`
    // answers null to a third of the Room / ContentProvider / OpenHelper
    // idiom. We express the same rules with instanceof tests instead of an
    // actual thrown CCE (identical answers, no exception construction on the
    // hot path): Number wins, else a CharSequence is parsed, else null. A
    // parse failure is null, exactly like AOSP's inner NumberFormatException
    // catch -- so "5000000000" is still null for getAsInteger.
    public Integer getAsInteger(String k) {
        Object v = map.get(k);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof CharSequence) {
            try { return Integer.valueOf(v.toString()); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }
    public Long getAsLong(String k) {
        Object v = map.get(k);
        if (v instanceof Number n) return n.longValue();
        if (v instanceof CharSequence) {
            try { return Long.valueOf(v.toString()); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }
    public Float getAsFloat(String k) {
        Object v = map.get(k);
        if (v instanceof Number n) return n.floatValue();
        if (v instanceof CharSequence) {
            try { return Float.valueOf(v.toString()); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }
    public Double getAsDouble(String k) {
        Object v = map.get(k);
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof CharSequence) {
            try { return Double.valueOf(v.toString()); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }
    public Short getAsShort(String k) {
        Object v = map.get(k);
        if (v instanceof Number n) return n.shortValue();
        if (v instanceof CharSequence) {
            try { return Short.valueOf(v.toString()); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }
    public Byte getAsByte(String k) {
        Object v = map.get(k);
        if (v instanceof Number n) return n.byteValue();
        if (v instanceof CharSequence) {
            try { return Byte.valueOf(v.toString()); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }
    /** AOSP does NOT coerce anything into a byte[] (ContentValues.java:466-469). */
    public byte[]  getAsByteArray(String k) { Object v = map.get(k); return v instanceof byte[] b ? b : null; }
    /** AOSP getAsBoolean (ContentValues.java:471-489) is the odd one out in TWO
     *  ways, both mirrored here. (1) The CharSequence branch is tested BEFORE
     *  the Number branch. (2) It accepts the STRING "1" as true, with a source
     *  comment naming the reason: "SQLite's internal representation for
     *  booleans is an integer with a value of 0 or 1. Without this check,
     *  boolean values obtained via DatabaseUtils#cursorRowToContentValues will
     *  always return false." The `"1".equals(v)` compares against the raw
     *  object, not v.toString(), so a StringBuilder("1") is NOT true -- that
     *  asymmetry is AOSP's, kept deliberately. */
    public Boolean getAsBoolean(String k) {
        Object v = map.get(k);
        if (v instanceof Boolean b) return b;
        if (v instanceof CharSequence) return Boolean.valueOf(v.toString()) || "1".equals(v);
        if (v instanceof Number n) return n.intValue() != 0;
        return null;
    }
    public Set<String> keySet()          { return map.keySet(); }
    public Set<Map.Entry<String, Object>> valueSet() { return map.entrySet(); }
    public boolean containsKey(String k) { return map.containsKey(k); }
    public int size()                    { return map.size(); }
    public boolean isEmpty()             { return map.isEmpty(); }
    public void clear()                  { map.clear(); }
    public void remove(String k)         { map.remove(k); }

    // AOSP defines value equality on the backing map; some persistence code
    // compares ContentValues instances and uses them as keys.
    @Override public boolean equals(Object object) {
        if (!(object instanceof ContentValues)) return false;
        return map.equals(((ContentValues) object).map);
    }
    @Override public int hashCode() { return map.hashCode(); }

    // --- Parcelable ------------------------------------------------------
    // AOSP writes the entry count first (an ArrayMap capacity hint) and then
    // the key/value map itself; we mirror that ordering with the primitives
    // this tree's Parcel provides (writeMap/readHashMap), so the round-trip
    // pairs up read-for-write at the same data positions.
    public static final Parcelable.Creator<ContentValues> CREATOR =
            new Parcelable.Creator<ContentValues>() {
        @Override public ContentValues createFromParcel(Parcel in) { return new ContentValues(in); }
        @Override public ContentValues[] newArray(int size) { return new ContentValues[size]; }
    };

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(map.size());
        parcel.writeMap(map);
    }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder();
        for (String name : map.keySet()) {
            if (sb.length() > 0) sb.append(' ');
            Object value = map.get(name);
            sb.append(name).append('=').append(value);
        }
        return sb.toString();
    }
}
