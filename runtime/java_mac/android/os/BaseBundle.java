// android.os.BaseBundle -- the shared superclass of Bundle and
// PersistableBundle, introduced in API 21. AOSP's
// frameworks/base/core/java/android/os/BaseBundle.java holds the
// key/value map and the primitive accessors that don't involve
// Parcelable / Binder. Bundle adds those on top.
//
// LOAD-BEARING DETAIL. APK bytecode compiled against API 21+ emits
// `invokevirtual android/os/BaseBundle.<method>` for any method
// AOSP declares on this class (notably keySet, getString, getInt,
// size, etc.). If only Bundle declares the method, the JVM
// verifier refuses to link and the caller hits
// `NoSuchMethodError: 'X android.os.BaseBundle.<method>(...)'`
// before any code runs. So this class MUST carry the same surface
// AOSP's BaseBundle carries -- not Bundle's full surface, just the
// methods AOSP put here.
//
// Hit live by androidx.startup.AppInitializer.discoverAndInitialize
// calling metadata.keySet() on the ProviderInfo.metaData Bundle.
//
// v1.0.940: this class gained AOSP's LAZY UNPARCEL. A Bundle read out of a
// Parcel now holds the raw bytes and rebuilds the map on FIRST ACCESS, not at
// read time. That is not an optimisation, it changes what an app observes:
// apps routinely put a Bundle somewhere that is never read, and unparcelling
// eagerly would surface a BadParcelableException at a point real Android never
// does. The partial-commit in initializeFromParcelLocked's finally block is
// equally observable -- when the unparcel dies partway, the keys read BEFORE
// the failure stay readable and the rest are simply missing, with no retry and
// no second throw. Design + evidence: docs/parcel-real-marshalling_2026-08-10.md.
package android.os;

import android.util.ArrayMap;
import java.util.Map;
import java.util.Set;

// AOSP's BaseBundle is concrete (frameworks/base android11-release
// core/java/android/os/BaseBundle.java:40, "public class BaseBundle"). Ours
// carried a stray `abstract` with no abstract method to justify it -- every
// method below already has a body -- so it cost nothing but correctness: a
// class file compiled with ACC_ABSTRACT set where AOSP's binary shape does
// not have it. type-identity audit (docs/type-identity-audit_2026-08-12.md)
// flagged this as one of 3 "harmful" abstract divergences. Safe to drop:
// every constructor is protected/package-private already, so this does not
// newly expose direct construction to app code outside android.os.
public class BaseBundle {
    /** BaseBundle.java:45. 'B' 'N' 'D' 'L' little-endian. */
    static final int BUNDLE_MAGIC = 0x4C444E42;
    /** The C++ PersistableBundle writer's magic. Nothing in this tree writes
     *  it (we have no native Bundle writer), but readFromParcelInner still has
     *  to recognise it rather than report "bad magic" on a stream we produced. */
    static final int BUNDLE_MAGIC_NATIVE = 0x4C444E44;

    /** AOSP's NoImagePreloadHolder.EMPTY_PARCEL: the sentinel that says "this
     *  bundle came from a parcel and the parcel was empty". Shared, so it must
     *  never be recycled -- hence recycleParcel's identity check. */
    private static final Parcel EMPTY_PARCEL = Parcel.obtain();

    /** Backing map. `protected` so concrete subclasses (Bundle,
     *  PersistableBundle) can add their own typed put/get pairs
     *  without re-declaring storage.
     *
     *  Backed by ArrayMap, not HashMap, and that is observable: BaseBundle
     *  marshals with val.keyAt(i), i.e. ArrayMap's hash-sorted index order, and
     *  the reader appends sequentially and throws at the first value it cannot
     *  rebuild. WHICH keys survive a failed unparcel is therefore decided by
     *  String.hashCode ordering, and a HashMap answers a different set.
     *
     *  Declared as Map and NOT final because AOSP's Bundle.EMPTY swaps in an
     *  IMMUTABLE backing map after construction (Bundle.java:57-58,
     *  "EMPTY.mMap = ArrayMap.EMPTY"), which is what makes a put on the shared
     *  singleton throw instead of permanently corrupting it for every later
     *  reader. */
    protected Map<String, Object> map;

    /** AOSP BaseBundle.java:95. Exactly one of `map` / `mParcelledData` is the
     *  live one: while this is non-null the bundle's contents are still bytes. */
    private Parcel mParcelledData;
    private boolean mParcelledByNative;

    /** AOSP BaseBundle.mClassLoader (BaseBundle.java:105). The default is the
     *  bundle's own defining loader (BaseBundle.java:122), which is why
     *  getClassLoader() is never null on a freshly constructed Bundle -- and
     *  why a Bundle read with no loader cannot resolve an APP Parcelable, since
     *  the framework's loader is the PARENT of the app's. */
    private ClassLoader mClassLoader;

    protected BaseBundle() {
        this.map = new ArrayMap<>();
        this.mClassLoader = getClass().getClassLoader();
    }
    protected BaseBundle(int capacity) {
        this.map = new ArrayMap<>(capacity);
        this.mClassLoader = getClass().getClassLoader();
    }
    protected BaseBundle(BaseBundle src) {
        if (src != null) {
            // AOSP's copyInternal keeps the copy lazy by SHARING the source's
            // mParcelledData. We unparcel instead, deliberately: a shared Parcel
            // has two owners and initializeFromParcelLocked recycles the one it
            // read, so the second owner would unparcel a recycled buffer. The
            // resulting map is identical either way; the only difference is that
            // cloning a bundle holding an unresolvable Parcelable throws here
            // rather than at the clone's first read.
            src.unparcel();
            this.map = new ArrayMap<>(src.map);
        } else {
            this.map = new ArrayMap<>();
        }
        // AOSP BaseBundle.java:463 copies the source's loader across.
        this.mClassLoader = (src != null && src.mClassLoader != null)
                ? src.mClassLoader : getClass().getClassLoader();
    }
    protected BaseBundle(ClassLoader loader) {
        this.map = new ArrayMap<>();
        this.mClassLoader = loader == null ? getClass().getClassLoader() : loader;
    }
    /** AOSP BaseBundle(Parcel, int): slice `length` bytes out and defer. The
     *  loader is this class's own, matching AOSP where that is the boot loader. */
    BaseBundle(Parcel parcelledData, int length) {
        readFromParcelInner(parcelledData, length);
        this.mClassLoader = getClass().getClassLoader();
    }

    // ------------------------------------------------------------------
    // Lazy unparcel (AOSP BaseBundle.java:232-307)
    // ------------------------------------------------------------------

    /** Force the byte form back into a map. Every accessor calls this first;
     *  there is no per-key path into the stream, which is why getInt() after a
     *  round trip pays for the WHOLE map or throws trying. */
    void unparcel() {
        synchronized (this) {
            final Parcel source = mParcelledData;
            if (source != null) {
                initializeFromParcelLocked(source, /*recycleParcel=*/ true, mParcelledByNative);
            } else if (map == null) {
                map = new ArrayMap<>();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void initializeFromParcelLocked(Parcel parcelledData, boolean recycleParcel,
                                            boolean parcelledByNative) {
        final int count = parcelledData.readInt();
        if (count < 0) {
            // A truncated/empty parcel. AOSP leaves the map alone and, notably,
            // does NOT clear mParcelledData -- but our EMPTY_PARCEL sentinel is
            // shared, so leaving it attached would re-read it forever. Detach.
            mParcelledData = null;
            mParcelledByNative = false;
            if (map == null) map = new ArrayMap<>();
            return;
        }
        ArrayMap<String, Object> m =
            (map instanceof ArrayMap) ? (ArrayMap<String, Object>) map : null;
        if (m == null) {
            m = new ArrayMap<>(count);
        } else {
            m.erase();
        }
        // Required, not defensive: ArrayMap.append throws "Array is full"
        // rather than growing, because the caller is asserting it already knows
        // the final size. AOSP calls this for the same reason.
        m.ensureCapacity(count);
        try {
            parcelledData.readArrayMapInternal(m, count, mClassLoader);
        } finally {
            // The PARTIAL map is committed and mParcelledData cleared even when
            // readArrayMapInternal threw. That is what makes a failed unparcel
            // leave the pre-failure keys readable and everything after it simply
            // absent: later get() calls neither retry nor re-throw, they miss.
            // sShouldDefuse (which would erase instead) is false outside the
            // system server, so an app sees the throw.
            map = m;
            if (recycleParcel) recycleParcel(parcelledData);
            mParcelledData = null;
            mParcelledByNative = false;
        }
    }

    private static void recycleParcel(Parcel p) {
        if (p != null && p != EMPTY_PARCEL) p.recycle();
    }

    /** True while the contents are still bytes. AOSP exposes this @hide as
     *  isParcelled(); Bundle.toString() is the observable consumer. */
    final boolean isParcelled() { return mParcelledData != null; }
    final boolean isEmptyParcel() { return mParcelledData == EMPTY_PARCEL; }
    final int parcelledDataSize() {
        return mParcelledData == null ? 0 : mParcelledData.dataSize();
    }

    // ------------------------------------------------------------------
    // Parcel framing (AOSP BaseBundle.java:1585-1684)
    // ------------------------------------------------------------------

    void writeToParcelInner(Parcel parcel, int flags) {
        final Map<String, Object> m;
        synchronized (this) {
            if (mParcelledData != null) {
                if (mParcelledData == EMPTY_PARCEL) {
                    parcel.writeInt(0);
                } else {
                    // Still bytes: copy them straight through rather than
                    // paying for (and risking) an unparcel we were not asked for.
                    int length = mParcelledData.dataSize();
                    parcel.writeInt(length);
                    parcel.writeInt(mParcelledByNative ? BUNDLE_MAGIC_NATIVE : BUNDLE_MAGIC);
                    parcel.appendFrom(mParcelledData, 0, length);
                }
                return;
            }
            m = map;
        }
        // An empty bundle is a bare writeInt(0) with no magic, which is why
        // dataSize() for one is 4.
        if (m == null || m.size() <= 0) { parcel.writeInt(0); return; }

        // The length is back-patched, and it counts the ArrayMap payload ONLY,
        // not the magic. setDataPosition deliberately does not bounds-check, so
        // seeking back to the placeholder and forward again is legal.
        int lengthPos = parcel.dataPosition();
        parcel.writeInt(-1);
        parcel.writeInt(BUNDLE_MAGIC);
        int startPos = parcel.dataPosition();
        parcel.writeArrayMapInternal(m);
        int endPos = parcel.dataPosition();
        parcel.setDataPosition(lengthPos);
        parcel.writeInt(endPos - startPos);
        parcel.setDataPosition(endPos);
    }

    void readFromParcelInner(Parcel parcel) {
        int length = parcel.readInt();
        readFromParcelInner(parcel, length);
    }

    private void readFromParcelInner(Parcel parcel, int length) {
        if (length < 0) {
            throw new RuntimeException("Bad length in parcel: " + length);
        } else if (length == 0) {
            // Empty bundle, or the end of the data.
            map = new ArrayMap<>();
            mParcelledData = EMPTY_PARCEL;
            mParcelledByNative = false;
            return;
        } else if (length % 4 != 0) {
            throw new IllegalStateException("Bundle length is not aligned by 4: " + length);
        }
        final int magic = parcel.readInt();
        final boolean isJavaBundle = magic == BUNDLE_MAGIC;
        final boolean isNativeBundle = magic == BUNDLE_MAGIC_NATIVE;
        if (!isJavaBundle && !isNativeBundle) {
            throw new IllegalStateException("Bad magic number for Bundle: 0x"
                    + Integer.toHexString(magic));
        }

        // Advance the SOURCE cursor past the slice before copying it out, so a
        // caller reading more values after the bundle picks up where the bundle
        // ended rather than inside it.
        int offset = parcel.dataPosition();
        parcel.setDataPosition(offset + length);

        Parcel p = Parcel.obtain();
        p.setDataPosition(0);
        p.appendFrom(parcel, offset, length);
        p.adoptClassCookies(parcel);
        p.setDataPosition(0);

        map = null;
        mParcelledData = p;
        mParcelledByNative = isNativeBundle;
    }

    /** AOSP BaseBundle.java:216-225. Package-private here exactly as on AOSP;
     *  Bundle re-declares both as public. Storing the loader matters beyond the
     *  getter: an app that hands us its own loader so a Bundle can resolve its
     *  Parcelables is entitled to read the same loader back. Neither unparcels:
     *  installing a loader BEFORE the first read is the whole point. */
    void setClassLoader(ClassLoader loader) { mClassLoader = loader; }
    ClassLoader getClassLoader()            { return mClassLoader; }

    public int size()                       { unparcel(); return map.size(); }
    public boolean isEmpty()                { unparcel(); return map.isEmpty(); }
    public void clear()                     { unparcel(); map.clear(); }
    public boolean containsKey(String key)  { unparcel(); return map.containsKey(key); }
    public Set<String> keySet()             { unparcel(); return map.keySet(); }
    public Object get(String key)           { unparcel(); return map.get(key); }
    public void remove(String key)          { unparcel(); map.remove(key); }

    /** AOSP BaseBundle.putAll(PersistableBundle): merge another bundle's entries.
     *  Bytecode compiled against API 21+ that holds a BaseBundle/PersistableBundle
     *  reference and calls putAll emits invokevirtual android/os/BaseBundle.putAll
     *  -> NoSuchMethodError without this. PersistableBundle extends BaseBundle, so
     *  its inherited `map` is reachable (same package). v1.0.426. */
    public void putAll(PersistableBundle bundle) {
        unparcel();
        if (bundle != null) { bundle.unparcel(); map.putAll(bundle.map); }
    }

    // Primitive scalars. The accessors return defaults when the key
    // is missing rather than throw -- matches AOSP behavior.
    public void putBoolean(String k, boolean v)   { unparcel(); map.put(k, v); }
    public boolean getBoolean(String k)           { return getBoolean(k, false); }
    public boolean getBoolean(String k, boolean def) {
        unparcel();
        Object v = map.get(k); return (v instanceof Boolean) ? (Boolean) v : def;
    }
    public void putInt(String k, int v)           { unparcel(); map.put(k, v); }
    public int  getInt(String k)                  { return getInt(k, 0); }
    public int  getInt(String k, int def) {
        unparcel();
        Object v = map.get(k); return (v instanceof Integer) ? (Integer) v : def;
    }
    public void putLong(String k, long v)         { unparcel(); map.put(k, v); }
    public long getLong(String k)                 { return getLong(k, 0L); }
    public long getLong(String k, long def) {
        unparcel();
        Object v = map.get(k); return (v instanceof Long) ? (Long) v : def;
    }
    public void putDouble(String k, double v)     { unparcel(); map.put(k, v); }
    public double getDouble(String k)             { return getDouble(k, 0d); }
    public double getDouble(String k, double def) {
        unparcel();
        Object v = map.get(k); return (v instanceof Double) ? (Double) v : def;
    }
    // Diagnostic (MACAPK_BUNDLE_TRACE): trace put/get of variant/splash/welcome
    // keys to see exactly what value reaches a screen's ViewModel (e.g. Reddit's
    // "com.reddit.welcome_v2.arg_variant_name").
    private static final boolean BUNDLE_TRACE = System.getenv("MACAPK_BUNDLE_TRACE") != null;
    private static boolean traceKey(String k) {
        return BUNDLE_TRACE && k != null
            && (k.contains("variant") || k.contains("splash") || k.contains("welcome"));
    }
    public void putString(String k, String v) {
        unparcel();
        if (traceKey(k)) android.util.Log.i("Mac.apk.BundleTrace", "putString(" + k + ") = " + v);
        map.put(k, v);
    }
    public String getString(String k) {
        unparcel();
        Object v = map.get(k);
        String r = (v instanceof String) ? (String) v : null;
        if (traceKey(k)) android.util.Log.i("Mac.apk.BundleTrace",
            "getString(" + k + ") = " + r + " (present=" + map.containsKey(k) + ")");
        return r;
    }
    public String getString(String k, String def) {
        String v = getString(k); return v != null ? v : def;
    }

    // Primitive arrays. AOSP puts only these scalar-array forms on
    // BaseBundle; Bundle adds char[], short[], byte[], float[] etc.
    public void putBooleanArray(String k, boolean[] v) { unparcel(); map.put(k, v); }
    public boolean[] getBooleanArray(String k) {
        unparcel();
        Object v = map.get(k); return (v instanceof boolean[]) ? (boolean[]) v : null;
    }
    public void putIntArray(String k, int[] v)    { unparcel(); map.put(k, v); }
    public int[] getIntArray(String k) {
        unparcel();
        Object v = map.get(k); return (v instanceof int[]) ? (int[]) v : null;
    }
    public void putLongArray(String k, long[] v)  { unparcel(); map.put(k, v); }
    public long[] getLongArray(String k) {
        unparcel();
        Object v = map.get(k); return (v instanceof long[]) ? (long[]) v : null;
    }
    public void putDoubleArray(String k, double[] v) { unparcel(); map.put(k, v); }
    public double[] getDoubleArray(String k) {
        unparcel();
        Object v = map.get(k); return (v instanceof double[]) ? (double[]) v : null;
    }
    public void putStringArray(String k, String[] v) { unparcel(); map.put(k, v); }
    public String[] getStringArray(String k) {
        unparcel();
        Object v = map.get(k); return (v instanceof String[]) ? (String[]) v : null;
    }

    @Override public String toString() {
        // AOSP renders this with SQUARE brackets, not braces: Bundle.toString
        // returns "Bundle[" + mMap.toString() + "]" and PersistableBundle's
        // returns "PersistableBundle[" + ... + "]". Deriving the prefix from the
        // runtime class gives both subclasses their AOSP string from one place.
        //
        // toString does NOT unparcel: AOSP reports the byte count instead,
        // precisely so that logging a Bundle cannot trigger the work (or the
        // BadParcelableException) that reading it would.
        synchronized (this) {
            if (mParcelledData != null) {
                if (isEmptyParcel()) return getClass().getSimpleName() + "[EMPTY_PARCEL]";
                return getClass().getSimpleName() + "[mParcelledData.dataSize="
                    + mParcelledData.dataSize() + "]";
            }
        }
        return getClass().getSimpleName() + "[" + map + "]";
    }
}
