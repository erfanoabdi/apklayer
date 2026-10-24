package android.os;

// apklayer patch: minimal Bundle stand-in (upstream needs BaseBundle/Parcel).
// HashMap-backed; covers the Message data-bag surface used by the seed.
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Bundle implements Cloneable {
    private final Map<String, Object> mMap = new HashMap<>();

    public Bundle() {}
    public Bundle(Bundle from) { if (from != null) mMap.putAll(from.mMap); }

    public int size() { return mMap.size(); }
    public boolean isEmpty() { return mMap.isEmpty(); }
    public void clear() { mMap.clear(); }
    public Set<String> keySet() { return mMap.keySet(); }
    public Object get(String key) { return mMap.get(key); }
    public boolean containsKey(String key) { return mMap.containsKey(key); }

    public void putString(String k, String v) { mMap.put(k, v); }
    public String getString(String k) { return (String) mMap.get(k); }
    public void putInt(String k, int v) { mMap.put(k, (Integer) v); }
    public int getInt(String k) { return (Integer) mMap.getOrDefault(k, 0); }
    public void putLong(String k, long v) { mMap.put(k, (Long) v); }
    public long getLong(String k) { return (Long) mMap.getOrDefault(k, 0L); }
    public void putBoolean(String k, boolean v) { mMap.put(k, (Boolean) v); }
    public boolean getBoolean(String k) { return (Boolean) mMap.getOrDefault(k, false); }

    @Override public Object clone() { return new Bundle(this); }
    @Override public String toString() { return "Bundle{" + mMap + "}"; }
}
