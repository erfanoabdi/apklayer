package android.renderscript;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Base class for all RenderScript objects owned by a {@link RenderScript}
 * context.
 *
 * On a device every BaseObj wraps a native handle, and equals()/hashCode() are
 * defined over that handle. Mac.apk has no native RenderScript driver, so each
 * object gets a synthetic monotonically-increasing id instead. That reproduces
 * the observable contract exactly, because native handles are unique per live
 * object too: two separately created Elements with identical layout are NOT
 * equal on a device either (which is precisely why Element.isCompatible exists
 * and does a structural comparison instead).
 *
 * destroy() is the one piece of real behaviour here. A use-after-destroy throws
 * on a device, and apps do rely on the second destroy() throwing, so the flag
 * is tracked and checked.
 *
 * Source: frameworks/rs/java/android/renderscript/BaseObj.java
 */
public class BaseObj {

    /** Stands in for the native object handle. Never 0 for a live object. */
    private static final AtomicLong sNextID = new AtomicLong(1);

    RenderScript mRS;
    long mID;
    boolean mDestroyed;
    String mName;

    BaseObj(RenderScript rs) {
        mRS = rs;
        mID = sNextID.getAndIncrement();
        mDestroyed = false;
    }

    void setID(long id) {
        mID = id;
    }

    /**
     * AOSP validates the owning context on every internal use, and the three
     * failure shapes below are all reachable from ordinary app code.
     */
    long getID(RenderScript rs) {
        if (mDestroyed) {
            throw new RSInvalidStateException("using a destroyed object.");
        }
        if (mID == 0) {
            throw new RSRuntimeException("Internal error: Object id 0.");
        }
        if ((rs != null) && (rs != mRS)) {
            throw new RSInvalidStateException("using object with mismatched context.");
        }
        return mID;
    }

    void checkValid() {
        if (mID == 0) {
            throw new RSIllegalArgumentException("Invalid object.");
        }
    }

    /**
     * Set a name for the object so it shows up in the RenderScript debug
     * context dump. AOSP refuses null, empty, and a second name; those are
     * argument errors an app can trip, so they are reproduced.
     */
    public void setName(String name) {
        if (name == null) {
            throw new RSIllegalArgumentException("setName requires a string of non-zero length.");
        }
        if (name.length() < 1) {
            throw new RSIllegalArgumentException("setName does not accept a zero length string.");
        }
        if (mName != null) {
            throw new RSIllegalArgumentException("setName object already has a name.");
        }
        mName = name;
    }

    public String getName() {
        return mName;
    }

    /**
     * Frees the object. A second call throws, which is the AOSP contract and
     * is load bearing: apps that pool Allocations use the throw to detect a
     * double free.
     */
    public void destroy() {
        if (mDestroyed) {
            throw new RSInvalidStateException("Object already destroyed.");
        }
        mDestroyed = true;
        mRS = null;
        mID = 0;
    }

    protected boolean isDestroyed() {
        return mDestroyed;
    }

    /** Compares the object handles, exactly as AOSP does. */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        BaseObj b = (BaseObj) obj;
        return mID == b.mID;
    }

    @Override
    public int hashCode() {
        return (int) ((mID & 0xfffffff) ^ (mID >> 32));
    }
}
