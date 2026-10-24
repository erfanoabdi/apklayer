package android.renderscript;

/**
 * A movable WINDOW onto another Allocation's memory.
 *
 * The adapter does not own bytes. It shares the adapted Allocation's backing
 * store and adds an origin (an LOD, a cubemap face, and an x/y/z offset) plus a
 * smaller shape. Reading or writing through the adapter reads and writes the
 * parent, which is the whole point: a reflected ScriptField_* uses it to hand a
 * kernel one row, one slice or one mipmap level of a larger allocation.
 *
 * Because the window can be NARROWER than the parent, its rows are not
 * contiguous in the backing store. Allocation.rowStride() / planeStride() exist
 * for exactly this case, and every row-walking loop in this package goes
 * through them; using the window's own X as a stride instead would walk
 * diagonally through the parent and corrupt it silently.
 *
 * The setters follow AOSP's rule that a dimension is either IN the window or
 * SELECTABLE, never both. If the window covers the parent's full X, setX
 * throws, because there is nothing left to slide along.
 *
 * Source: frameworks/rs/java/android/renderscript/AllocationAdapter.java
 */
public class AllocationAdapter extends Allocation {

    Type mWindow;

    AllocationAdapter(RenderScript rs, Allocation alloc, Type t) {
        // allocate == false: the window borrows the parent's bytes.
        super(rs, alloc.mType, alloc.mUsage, false);
        mAdaptedAllocation = alloc;
        mWindow = t;
        mCurrentDimX = t.getX();
        mCurrentDimY = t.getY();
        mCurrentDimZ = t.getZ();
        mCurrentCount = mCurrentDimX;
        if (mCurrentDimY > 1) {
            mCurrentCount *= mCurrentDimY;
        }
        if (mCurrentDimZ > 1) {
            mCurrentCount *= mCurrentDimZ;
        }
    }

    /**
     * Recomputes the window shape for a mipmap level: every dimension halves
     * (floor, minimum 1) per level, and a level past the end of the chain is an
     * argument error rather than a clamp.
     */
    void initLOD(int lod) {
        if (lod < 0) {
            throw new RSIllegalArgumentException("Attempting to set negative lod (" + lod + ").");
        }

        int tx = mAdaptedAllocation.mType.getX();
        int ty = mAdaptedAllocation.mType.getY();
        int tz = mAdaptedAllocation.mType.getZ();

        for (int ct = 0; ct < lod; ct++) {
            if ((tx == 1) && (ty == 1) && (tz == 1)) {
                throw new RSIllegalArgumentException(
                        "Attempting to set lod (" + lod + ") out of range.");
            }
            if (tx > 1) {
                tx >>= 1;
            }
            if (ty > 1) {
                ty >>= 1;
            }
            if (tz > 1) {
                tz >>= 1;
            }
        }

        mCurrentDimX = tx;
        mCurrentDimY = ty;
        mCurrentDimZ = tz;
        mCurrentCount = mCurrentDimX;
        if (mCurrentDimY > 1) {
            mCurrentCount *= mCurrentDimY;
        }
        if (mCurrentDimZ > 1) {
            mCurrentCount *= mCurrentDimZ;
        }
        mSelectedY = 0;
        mSelectedZ = 0;
        mSelectedLOD = lod;
    }

    /**
     * On a device this pushes the new origin down to the driver. Here
     * Allocation.baseOffset() derives it from the selectors on every access, so
     * there is nothing to push; the method is kept because every setter calls
     * it and a future storage change would need it back.
     */
    private void updateOffsets() {
    }

    public void setLOD(int lod) {
        if (!mAdaptedAllocation.getType().hasMipmaps()) {
            throw new RSInvalidStateException(
                    "Cannot set LOD when the allocation type does not include mipmaps.");
        }
        if (mWindow.hasMipmaps()) {
            throw new RSInvalidStateException(
                    "Cannot set LOD when the adapter includes mipmaps.");
        }
        initLOD(lod);
    }

    public void setFace(Type.CubemapFace cf) {
        if (!mAdaptedAllocation.getType().hasFaces()) {
            throw new RSInvalidStateException(
                    "Cannot set Face when the allocation type does not include faces.");
        }
        if (mWindow.hasFaces()) {
            throw new RSInvalidStateException(
                    "Cannot set Face when the adapter includes faces.");
        }
        if (cf == null) {
            throw new RSIllegalArgumentException("Cannot set null face.");
        }
        mSelectedFace = cf;
        updateOffsets();
    }

    public void setX(int x) {
        if (mAdaptedAllocation.getType().getX() <= x) {
            throw new RSInvalidStateException(
                    "Cannot set X greater than dimension of allocation.");
        }
        if (mWindow.getX() == mAdaptedAllocation.getType().getX()) {
            throw new RSInvalidStateException("Cannot set X when the adapter includes X.");
        }
        if ((mWindow.getX() + x) >= mAdaptedAllocation.getType().getX()) {
            throw new RSInvalidStateException("Cannot set (X + window) which would be larger"
                    + " than dimension of allocation.");
        }
        mSelectedX = x;
        updateOffsets();
    }

    public void setY(int y) {
        if (mAdaptedAllocation.getType().getY() == 0) {
            throw new RSInvalidStateException(
                    "Cannot set Y when the allocation type does not include Y dim.");
        }
        if (mAdaptedAllocation.getType().getY() <= y) {
            throw new RSInvalidStateException(
                    "Cannot set Y greater than dimension of allocation.");
        }
        if (mWindow.getY() == mAdaptedAllocation.getType().getY()) {
            throw new RSInvalidStateException("Cannot set Y when the adapter includes Y.");
        }
        if ((mWindow.getY() + y) >= mAdaptedAllocation.getType().getY()) {
            throw new RSInvalidStateException("Cannot set (Y + window) which would be larger"
                    + " than dimension of allocation.");
        }
        mSelectedY = y;
        updateOffsets();
    }

    public void setZ(int z) {
        if (mAdaptedAllocation.getType().getZ() == 0) {
            throw new RSInvalidStateException(
                    "Cannot set Z when the allocation type does not include Z dim.");
        }
        if (mAdaptedAllocation.getType().getZ() <= z) {
            throw new RSInvalidStateException(
                    "Cannot set Z greater than dimension of allocation.");
        }
        if (mWindow.getZ() == mAdaptedAllocation.getType().getZ()) {
            throw new RSInvalidStateException("Cannot set Z when the adapter includes Z.");
        }
        if ((mWindow.getZ() + z) >= mAdaptedAllocation.getType().getZ()) {
            throw new RSInvalidStateException("Cannot set (Z + window) which would be larger"
                    + " than dimension of allocation.");
        }
        mSelectedZ = z;
        updateOffsets();
    }

    /** A full-width 1D window; Y, Z, LOD and face all become selectable. */
    public static AllocationAdapter create1D(RenderScript rs, Allocation a) {
        rs.validate();
        Type t = Type.createX(rs, a.getElement(), a.getType().getX());
        return createTyped(rs, a, t);
    }

    /** A full-size 2D window; Z, LOD and face become selectable. */
    public static AllocationAdapter create2D(RenderScript rs, Allocation a) {
        rs.validate();
        Type t = Type.createXY(rs, a.getElement(), a.getType().getX(), a.getType().getY());
        return createTyped(rs, a, t);
    }

    /**
     * An arbitrary window, whose Type gives its shape.
     *
     * A dimension the Type omits is constrained away by the matching selector.
     * A dimension the Type matches in full cannot be selected. A dimension the
     * Type covers only partly becomes a sliding window whose selector sets the
     * base.
     */
    public static AllocationAdapter createTyped(RenderScript rs, Allocation a, Type t) {
        rs.validate();

        if (a.mAdaptedAllocation != null) {
            throw new RSInvalidStateException("Adapters cannot be nested.");
        }
        if (!a.getType().getElement().equals(t.getElement())) {
            throw new RSInvalidStateException("Element must match Allocation type.");
        }
        if (t.hasFaces() || t.hasMipmaps()) {
            throw new RSInvalidStateException(
                    "Adapters do not support window types with Mipmaps or Faces.");
        }

        Type at = a.getType();
        if ((t.getX() > at.getX()) || (t.getY() > at.getY()) || (t.getZ() > at.getZ())) {
            throw new RSInvalidStateException(
                    "Type cannot have dimension larger than the source allocation.");
        }

        return new AllocationAdapter(rs, a, t);
    }

    /**
     * Adapters cannot be resized, so this THROWS rather than silently doing
     * nothing. Overriding a state-assigning superclass method with an empty
     * body would leave the caller believing a resize happened.
     */
    @Override
    public synchronized void resize(int dimX) {
        throw new RSInvalidStateException("Resize not allowed for Adapters.");
    }
}
