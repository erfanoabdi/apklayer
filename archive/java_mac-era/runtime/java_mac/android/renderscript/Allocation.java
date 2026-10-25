package android.renderscript;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A block of memory the RenderScript kernels read and write, described by a
 * {@link Type} (dimensions) and an {@link Element} (cell layout).
 *
 * MAC.APK STORAGE MODEL. On a device an Allocation is driver-owned memory. Here
 * it is a plain byte[] laid out exactly the way the driver would lay it out,
 * because the intrinsics in this package are a Java port of AOSP's CPU
 * reference implementation and they read that layout directly. The rules:
 *
 *  - Cells are contiguous, x fastest, then y, then z.
 *  - Cell size is Element.getBytesSize(), which already accounts for a vec3
 *    occupying vec4's worth of space.
 *  - Multi-byte components are LITTLE ENDIAN. Every Android ABI is
 *    little-endian and so is Apple Silicon, so this matches both the guest's
 *    expectation and the host.
 *  - Mipmap levels follow level 0 in order; each level holds all faces.
 *
 * AUTO PADDING. When an Element has vector size 3, the cell is 4 components
 * wide but a Java float[]/int[] the app hands us usually holds only 3 per cell.
 * {@link #setAutoPadding} tells the copy methods to insert or skip the 4th
 * component. It is OFF by default, which is the platform default, and it
 * changes the required array length by a factor of 3/4.
 *
 * WHAT IS NOT REAL HERE. USAGE_IO_INPUT / USAGE_IO_OUTPUT connect an Allocation
 * to a BufferQueue so a camera or display can produce or consume frames.
 * Mac.apk has no RenderScript driver to own that queue, so getSurface(),
 * setSurface(), ioSend() and ioReceive() throw rather than silently dropping
 * frames the app believes were delivered.
 *
 * Source: frameworks/rs/java/android/renderscript/Allocation.java
 */
public class Allocation extends BaseObj {

    /** The Allocation will be bound to and accessed by scripts. */
    public static final int USAGE_SCRIPT = 0x0001;
    /** The Allocation will be used as a texture source by one or more programs. */
    public static final int USAGE_GRAPHICS_TEXTURE = 0x0002;
    /** The Allocation will be used as a graphics mesh. */
    public static final int USAGE_GRAPHICS_VERTEX = 0x0004;
    /** The Allocation will be used as the source of shader constants by one or more programs. */
    public static final int USAGE_GRAPHICS_CONSTANTS = 0x0008;
    /** The Allocation will be used as a target for offscreen rendering. */
    public static final int USAGE_GRAPHICS_RENDER_TARGET = 0x0010;
    /** The Allocation will be used as a {@link android.view.Surface} consumer. */
    public static final int USAGE_IO_INPUT = 0x0020;
    /** The Allocation will be used as a {@link android.view.Surface} producer. */
    public static final int USAGE_IO_OUTPUT = 0x0040;
    /** The Allocation's backing store will be inherited from another object. */
    public static final int USAGE_SHARED = 0x0080;

    private static final int USAGE_ALL = USAGE_SCRIPT | USAGE_GRAPHICS_TEXTURE
            | USAGE_GRAPHICS_VERTEX | USAGE_GRAPHICS_CONSTANTS
            | USAGE_GRAPHICS_RENDER_TARGET | USAGE_IO_INPUT | USAGE_IO_OUTPUT
            | USAGE_SHARED;

    /**
     * Controls mipmap behaviour at creation time.
     */
    public enum MipmapControl {
        /** No mipmaps are generated and the Type has no mipmap chain. */
        MIPMAP_NONE(0),
        /** The Type has a full mipmap chain and it is filled from the source. */
        MIPMAP_FULL(1),
        /** The Type has no mipmap chain but a texture upload builds one. */
        MIPMAP_ON_SYNC_TO_TEXTURE(2);

        int mID;

        MipmapControl(int id) {
            mID = id;
        }
    }

    /**
     * Notification that a new buffer is available on a USAGE_IO_INPUT
     * Allocation. Mac.apk never has a producer attached (see the class
     * comment), so an installed listener is retained but never invoked.
     */
    public interface OnBufferAvailableListener {
        void onBufferAvailable(Allocation a);
    }

    Type mType;
    int mUsage;
    MipmapControl mMipmapControl = MipmapControl.MIPMAP_NONE;
    Bitmap mBitmap;
    boolean mAutoPadding = false;
    long mTimeStamp = -1;
    OnBufferAvailableListener mBufferNotifier;

    /** The bytes. Null only for an AllocationAdapter, which borrows its parent's. */
    byte[] mData;

    /** Non-null for an AllocationAdapter; the Allocation whose bytes we index into. */
    Allocation mAdaptedAllocation;

    int mSelectedX;
    int mSelectedY;
    int mSelectedZ;
    int mSelectedLOD;
    Type.CubemapFace mSelectedFace = Type.CubemapFace.POSITVE_X;

    int mCurrentDimX;
    int mCurrentDimY;
    int mCurrentDimZ;
    int mCurrentCount;

    private ByteBuffer mByteBuffer;
    private long mByteBufferStride = -1;

    Allocation(RenderScript rs, Type t, int usage) {
        this(rs, t, usage, true);
    }

    /**
     * @param allocate false only for {@link AllocationAdapter}, which is a
     *                 WINDOW onto another Allocation's bytes and must not own
     *                 a second copy of them.
     */
    Allocation(RenderScript rs, Type t, int usage, boolean allocate) {
        super(rs);
        if ((usage & ~USAGE_ALL) != 0) {
            throw new RSIllegalArgumentException("Unknown usage specified.");
        }
        mType = t;
        mUsage = usage;
        if (t != null) {
            updateCacheInfo(t);
            if (allocate) {
                mData = new byte[byteSizeOf(t)];
            }
        }
    }

    /**
     * A YUV Type stores a full-resolution luma plane plus half-resolution
     * chroma, so its byte size is 1.5x the cell count rather than 1x. AOSP
     * rounds up, which matters for odd dimensions.
     */
    private static int byteSizeOf(Type t) {
        if (t.mDimYuv != 0) {
            return (int) Math.ceil(t.getCount() * t.getElement().getBytesSize() * 1.5);
        }
        return t.getCount() * t.getElement().getBytesSize();
    }

    private void updateCacheInfo(Type t) {
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

    // ---- storage plumbing the intrinsics and the adapter share ---------------

    /** The backing bytes, following the adapter chain to the real owner. */
    byte[] data() {
        return mAdaptedAllocation != null ? mAdaptedAllocation.data() : mData;
    }

    /** Byte offset of this view's origin within {@link #data()}. */
    int baseOffset() {
        if (mAdaptedAllocation == null) {
            return 0;
        }
        int elem = mType.getElement().getBytesSize();
        int off = mAdaptedAllocation.lodFaceOffset(mSelectedLOD, mSelectedFace);
        int lodX = rowStride();
        int lodY = Math.max(1,
                mAdaptedAllocation.lodDim(mAdaptedAllocation.mType.getY(), mSelectedLOD));
        off += ((long) mSelectedZ * lodY * lodX + (long) mSelectedY * lodX + mSelectedX) * elem;
        return off;
    }

    /**
     * Cells between the start of one row and the start of the next IN THE
     * BACKING STORE.
     *
     * For an ordinary Allocation this equals the X dimension, so the two are
     * interchangeable and most code says mCurrentDimX. For an
     * {@link AllocationAdapter} whose window is NARROWER than the allocation it
     * views, they differ: the window's rows are separated by the parent's full
     * width. Every place that steps from one row to the next must use this, not
     * mCurrentDimX, or a narrow window walks diagonally through the parent.
     */
    int rowStride() {
        if (mAdaptedAllocation == null) {
            return mCurrentDimX;
        }
        return Math.max(1,
                mAdaptedAllocation.lodDim(mAdaptedAllocation.mType.getX(), mSelectedLOD));
    }

    /** Cells between the start of one z-plane and the next, in the backing store. */
    int planeStride() {
        if (mAdaptedAllocation == null) {
            return mCurrentDimX * Math.max(1, mCurrentDimY);
        }
        int lodY = Math.max(1,
                mAdaptedAllocation.lodDim(mAdaptedAllocation.mType.getY(), mSelectedLOD));
        return rowStride() * lodY;
    }

    int lodDim(int dim, int lod) {
        int d = dim;
        for (int i = 0; i < lod && d > 1; i++) {
            d >>= 1;
        }
        return d;
    }

    /**
     * Byte offset of the start of one (level, face) sub-image. Levels follow
     * level 0 in order and each level holds every face, which is the layout the
     * driver uses and the only one Type.calcElementCount's total agrees with.
     */
    int lodFaceOffset(int lod, Type.CubemapFace face) {
        final int elem = mType.getElement().getBytesSize();
        final int faces = mType.hasFaces() ? 6 : 1;
        int off = 0;
        for (int l = 0; l < lod; l++) {
            off += cellsInLevel(l) * faces * elem;
        }
        off += cellsInLevel(lod) * (faces == 6 ? face.mID : 0) * elem;
        return off;
    }

    private int cellsInLevel(int lod) {
        int x = Math.max(1, lodDim(mType.getX(), lod));
        int y = Math.max(1, lodDim(mType.getY(), lod));
        int z = Math.max(1, lodDim(mType.getZ(), lod));
        return x * y * z;
    }

    /** Little-endian view over the whole backing store, positioned at 0. */
    ByteBuffer buffer() {
        return ByteBuffer.wrap(data()).order(ByteOrder.LITTLE_ENDIAN);
    }

    // ---- accessors ----------------------------------------------------------

    public Type getType() {
        return mType;
    }

    public Element getElement() {
        return mType.getElement();
    }

    public int getUsage() {
        return mUsage;
    }

    /** @return size of the Allocation in bytes. */
    public int getBytesSize() {
        return byteSizeOf(mType);
    }

    /**
     * Enables or disables the 4th-component padding that hides a vec3's true
     * 16-byte footprint from a 3-per-cell Java array. Off by default, as on a
     * device.
     */
    public void setAutoPadding(boolean useAutoPadding) {
        mAutoPadding = useAutoPadding;
    }

    /**
     * Timestamp of the most recent buffer received via ioReceive(). Stays at
     * the platform default of -1 here, because Mac.apk never receives one.
     */
    public long getTimeStamp() {
        return mTimeStamp;
    }

    // ---- validation, mirrored from AOSP -------------------------------------

    private void validateIsInt64() {
        if ((mType.mElement.mType == Element.DataType.SIGNED_64)
                || (mType.mElement.mType == Element.DataType.UNSIGNED_64)) {
            return;
        }
        throw new RSIllegalArgumentException(
                "64 bit integer source does not match allocation type " + mType.mElement.mType);
    }

    private void validateIsInt32() {
        if ((mType.mElement.mType == Element.DataType.SIGNED_32)
                || (mType.mElement.mType == Element.DataType.UNSIGNED_32)) {
            return;
        }
        throw new RSIllegalArgumentException(
                "32 bit integer source does not match allocation type " + mType.mElement.mType);
    }

    private void validateIsInt16OrFloat16() {
        if ((mType.mElement.mType == Element.DataType.SIGNED_16)
                || (mType.mElement.mType == Element.DataType.UNSIGNED_16)
                || (mType.mElement.mType == Element.DataType.FLOAT_16)) {
            return;
        }
        throw new RSIllegalArgumentException(
                "16 bit integer source does not match allocation type " + mType.mElement.mType);
    }

    private void validateIsInt8() {
        if ((mType.mElement.mType == Element.DataType.SIGNED_8)
                || (mType.mElement.mType == Element.DataType.UNSIGNED_8)) {
            return;
        }
        throw new RSIllegalArgumentException(
                "8 bit integer source does not match allocation type " + mType.mElement.mType);
    }

    private void validateIsFloat32() {
        if (mType.mElement.mType == Element.DataType.FLOAT_32) {
            return;
        }
        throw new RSIllegalArgumentException(
                "32 bit float source does not match allocation type " + mType.mElement.mType);
    }

    private void validateIsFloat64() {
        if (mType.mElement.mType == Element.DataType.FLOAT_64) {
            return;
        }
        throw new RSIllegalArgumentException(
                "64 bit float source does not match allocation type " + mType.mElement.mType);
    }

    private void validateIsObject() {
        switch (mType.mElement.mType) {
            case RS_ELEMENT:
            case RS_TYPE:
            case RS_ALLOCATION:
            case RS_SAMPLER:
            case RS_SCRIPT:
            case RS_MESH:
            case RS_PROGRAM_FRAGMENT:
            case RS_PROGRAM_VERTEX:
            case RS_PROGRAM_RASTER:
            case RS_PROGRAM_STORE:
                return;
            default:
                throw new RSIllegalArgumentException(
                        "Object source does not match allocation type " + mType.mElement.mType);
        }
    }

    /**
     * @param checkType when true the array's component type must match the
     *                  Element's DataType; when false (the *Unchecked entry
     *                  points) the bytes are reinterpreted as-is.
     */
    private Element.DataType validateObjectIsPrimitiveArray(Object d, boolean checkType) {
        final Class<?> c = d.getClass();
        if (!c.isArray()) {
            throw new RSIllegalArgumentException("Object passed is not an array of primitives.");
        }
        final Class<?> cmp = c.getComponentType();
        if (!cmp.isPrimitive()) {
            throw new RSIllegalArgumentException("Object passed is not an Array of primitives.");
        }

        if (cmp == Long.TYPE) {
            if (checkType) {
                validateIsInt64();
                return mType.mElement.mType;
            }
            return Element.DataType.SIGNED_64;
        }
        if (cmp == Integer.TYPE) {
            if (checkType) {
                validateIsInt32();
                return mType.mElement.mType;
            }
            return Element.DataType.SIGNED_32;
        }
        if (cmp == Short.TYPE) {
            if (checkType) {
                validateIsInt16OrFloat16();
                return mType.mElement.mType;
            }
            return Element.DataType.SIGNED_16;
        }
        if (cmp == Byte.TYPE) {
            if (checkType) {
                validateIsInt8();
                return mType.mElement.mType;
            }
            return Element.DataType.SIGNED_8;
        }
        if (cmp == Float.TYPE) {
            if (checkType) {
                validateIsFloat32();
            }
            return Element.DataType.FLOAT_32;
        }
        if (cmp == Double.TYPE) {
            if (checkType) {
                validateIsFloat64();
            }
            return Element.DataType.FLOAT_64;
        }
        throw new RSIllegalArgumentException("Unsupported array type " + cmp);
    }

    private void data1DChecks(int off, int count, int len, int dataSize, boolean usePadding) {
        mRS.validate();
        if (off < 0) {
            throw new RSIllegalArgumentException("Offset must be >= 0.");
        }
        if (count < 1) {
            throw new RSIllegalArgumentException("Count must be >= 1.");
        }
        if ((off + count) > mCurrentCount) {
            throw new RSIllegalArgumentException("Overflow, Available count " + mCurrentCount
                    + ", got " + count + " at offset " + off + ".");
        }
        if (usePadding) {
            if (len < dataSize / 4 * 3) {
                throw new RSIllegalArgumentException("Array too small for allocation type.");
            }
        } else {
            if (len < dataSize) {
                throw new RSIllegalArgumentException("Array too small for allocation type.");
            }
        }
    }

    // ---- the byte mover -----------------------------------------------------

    /**
     * Cells whose components are not a whole number of array slots (the packed
     * 5_6_5 / 5_5_5_1 / 4_4_4_4 pixels, and the matrix types read through a
     * float[]) fall back to a straight byte move, which is what the driver
     * does for them as well.
     */
    private boolean cellIsDivisible(Element.DataType dt) {
        return dt.mSize > 0 && (mType.getElement().getBytesSize() % dt.mSize) == 0;
    }

    /**
     * Moves count cells between a Java primitive array and the backing bytes.
     *
     * dt describes the ARRAY, not the Allocation: the *Unchecked entry points
     * deliberately reinterpret, so a byte[] can be poured into a float
     * Allocation. Cells whose size is not a whole number of dt slots (the
     * packed 16-bit pixel formats read through an int[], say) fall back to a
     * straight byte move, which is what the driver does for them too.
     */
    private void copyCells(int cellOffset, int count, Object array, Element.DataType dt,
                           boolean toAlloc) {
        final int cellBytes = mType.getElement().getBytesSize();
        final int byteOffset = baseOffset() + cellOffset * cellBytes;
        if (cellIsDivisible(dt)) {
            moveRow(byteOffset, count, array, dt, 0, toAlloc);
        } else {
            rawRowMove(byteOffset, count * cellBytes, array, dt, 0, toAlloc);
        }
    }

    // ---- creation -----------------------------------------------------------

    static Element elementFromBitmap(RenderScript rs, Bitmap b) {
        final Bitmap.Config bc = b.getConfig();
        if (bc == Bitmap.Config.ALPHA_8) {
            return Element.A_8(rs);
        }
        if (bc == Bitmap.Config.ARGB_4444) {
            return Element.RGBA_4444(rs);
        }
        if (bc == Bitmap.Config.ARGB_8888) {
            return Element.RGBA_8888(rs);
        }
        if (bc == Bitmap.Config.RGB_565) {
            return Element.RGB_565(rs);
        }
        throw new RSIllegalArgumentException("Bitmap type " + bc + " is not supported.");
    }

    static Type typeFromBitmap(RenderScript rs, Bitmap b, MipmapControl mip) {
        Element e = elementFromBitmap(rs, b);
        Type.Builder tb = new Type.Builder(rs, e);
        tb.setX(b.getWidth());
        tb.setY(b.getHeight());
        tb.setMipmaps(mip == MipmapControl.MIPMAP_FULL);
        return tb.create();
    }

    public static Allocation createTyped(RenderScript rs, Type type, MipmapControl mips, int usage) {
        rs.validate();
        if (type.getID(rs) == 0) {
            throw new RSInvalidStateException("Bad Type");
        }
        Allocation a = new Allocation(rs, type, usage);
        a.mMipmapControl = mips;
        return a;
    }

    public static Allocation createTyped(RenderScript rs, Type type, int usage) {
        return createTyped(rs, type, MipmapControl.MIPMAP_NONE, usage);
    }

    public static Allocation createTyped(RenderScript rs, Type type) {
        return createTyped(rs, type, MipmapControl.MIPMAP_NONE, USAGE_SCRIPT);
    }

    public static Allocation createSized(RenderScript rs, Element e, int count, int usage) {
        rs.validate();
        Type.Builder b = new Type.Builder(rs, e);
        b.setX(count);
        Type t = b.create();
        return new Allocation(rs, t, usage);
    }

    public static Allocation createSized(RenderScript rs, Element e, int count) {
        return createSized(rs, e, count, USAGE_SCRIPT);
    }

    public static Allocation createFromBitmap(RenderScript rs, Bitmap b,
                                              MipmapControl mips, int usage) {
        rs.validate();
        if (b == null) {
            throw new RSIllegalArgumentException("Bitmap is null");
        }
        Type t = typeFromBitmap(rs, b, mips);
        Allocation a = new Allocation(rs, t, usage);
        a.mMipmapControl = mips;
        a.mBitmap = b;
        a.writeBitmap(b, 0, 0, b.getWidth(), b.getHeight());
        if (mips == MipmapControl.MIPMAP_FULL) {
            a.generateMipmaps();
        }
        return a;
    }

    public static Allocation createFromBitmap(RenderScript rs, Bitmap b) {
        return createFromBitmap(rs, b, MipmapControl.MIPMAP_NONE, USAGE_SCRIPT);
    }

    public static Allocation createFromBitmapResource(RenderScript rs, Resources res, int id,
                                                      MipmapControl mips, int usage) {
        rs.validate();
        if ((usage & (USAGE_SHARED | USAGE_IO_INPUT | USAGE_IO_OUTPUT)) != 0) {
            throw new RSIllegalArgumentException("Unsupported usage specified.");
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inScaled = false;
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap b = BitmapFactory.decodeResource(res, id, opts);
        if (b == null) {
            throw new RSRuntimeException("Could not decode resource 0x" + Integer.toHexString(id));
        }
        return createFromBitmap(rs, b, mips, usage);
    }

    public static Allocation createFromBitmapResource(RenderScript rs, Resources res, int id) {
        return createFromBitmapResource(rs, res, id,
                MipmapControl.MIPMAP_NONE, USAGE_SCRIPT | USAGE_GRAPHICS_TEXTURE);
    }

    /** A U8 Allocation holding str's UTF-8 bytes. */
    public static Allocation createFromString(RenderScript rs, String str, int usage) {
        rs.validate();
        byte[] allocArray;
        try {
            allocArray = str.getBytes("UTF-8");
            Allocation alloc = Allocation.createSized(rs, Element.U8(rs), allocArray.length, usage);
            alloc.copyFrom(allocArray);
            return alloc;
        } catch (Exception e) {
            throw new RSRuntimeException("Could not convert string to utf-8.");
        }
    }

    /**
     * numAlloc Allocations of the same Type. AOSP shares one BufferQueue across
     * the set when USAGE_IO_INPUT is requested; here they are independent,
     * which is indistinguishable to an app that never attaches a producer.
     */
    public static Allocation[] createAllocations(RenderScript rs, Type t, int usage, int numAlloc) {
        rs.validate();
        if (t.getID(rs) == 0) {
            throw new RSInvalidStateException("Bad Type");
        }
        Allocation[] mAllocationArray = new Allocation[numAlloc];
        for (int i = 0; i < numAlloc; i++) {
            mAllocationArray[i] = createTyped(rs, t, usage);
        }
        return mAllocationArray;
    }

    public static Allocation createCubemapFromBitmap(RenderScript rs, Bitmap b,
                                                     MipmapControl mips, int usage) {
        rs.validate();

        int height = b.getHeight();
        int width = b.getWidth();

        if (width % 6 != 0) {
            throw new RSIllegalArgumentException("Cubemap height must be multiple of 6");
        }
        if (width / 6 != height) {
            throw new RSIllegalArgumentException("Only square cube map faces supported");
        }
        boolean isPow2 = (height & (height - 1)) == 0;
        if (!isPow2) {
            throw new RSIllegalArgumentException("Only power of 2 cube faces supported");
        }

        Element e = elementFromBitmap(rs, b);
        Type.Builder tb = new Type.Builder(rs, e);
        tb.setX(height);
        tb.setY(height);
        tb.setFaces(true);
        tb.setMipmaps(mips == MipmapControl.MIPMAP_FULL);
        Type t = tb.create();

        Allocation a = new Allocation(rs, t, usage);
        a.mMipmapControl = mips;
        // The source strip is right, left, top, bottom, front, back, which is
        // the CubemapFace ordinal order, so face n starts at x = n * height.
        int[] px = new int[height * height];
        for (int face = 0; face < 6; face++) {
            b.getPixels(px, 0, height, face * height, 0, height, height);
            a.writeFacePixels(px, face, height, height);
        }
        return a;
    }

    public static Allocation createCubemapFromBitmap(RenderScript rs, Bitmap b) {
        return createCubemapFromBitmap(rs, b, MipmapControl.MIPMAP_NONE, USAGE_GRAPHICS_TEXTURE);
    }

    public static Allocation createCubemapFromCubeFaces(RenderScript rs,
                                                        Bitmap xpos, Bitmap xneg,
                                                        Bitmap ypos, Bitmap yneg,
                                                        Bitmap zpos, Bitmap zneg,
                                                        MipmapControl mips, int usage) {
        int height = xpos.getHeight();
        boolean isPow2 = (height & (height - 1)) == 0;
        if (!isPow2) {
            throw new RSIllegalArgumentException("Only power of 2 cube faces supported");
        }
        if (xpos.getWidth() != height || xneg.getWidth() != height || xneg.getHeight() != height
                || ypos.getWidth() != height || ypos.getHeight() != height
                || yneg.getWidth() != height || yneg.getHeight() != height
                || zpos.getWidth() != height || zpos.getHeight() != height
                || zneg.getWidth() != height || zneg.getHeight() != height) {
            throw new RSIllegalArgumentException("Only square cube map faces supported");
        }

        Element e = elementFromBitmap(rs, xpos);
        Type.Builder tb = new Type.Builder(rs, e);
        tb.setX(height);
        tb.setY(height);
        tb.setFaces(true);
        tb.setMipmaps(mips == MipmapControl.MIPMAP_FULL);
        Type t = tb.create();

        Allocation a = new Allocation(rs, t, usage);
        a.mMipmapControl = mips;
        Bitmap[] faces = {xpos, xneg, ypos, yneg, zpos, zneg};
        int[] px = new int[height * height];
        for (int face = 0; face < 6; face++) {
            faces[face].getPixels(px, 0, height, 0, 0, height, height);
            a.writeFacePixels(px, face, height, height);
        }
        return a;
    }

    public static Allocation createCubemapFromCubeFaces(RenderScript rs,
                                                        Bitmap xpos, Bitmap xneg,
                                                        Bitmap ypos, Bitmap yneg,
                                                        Bitmap zpos, Bitmap zneg) {
        return createCubemapFromCubeFaces(rs, xpos, xneg, ypos, yneg, zpos, zneg,
                MipmapControl.MIPMAP_NONE, USAGE_GRAPHICS_TEXTURE);
    }

    // ---- Bitmap conversion --------------------------------------------------

    private void validateBitmapFormat(Bitmap b) {
        Bitmap.Config bc = b.getConfig();
        if (bc == null) {
            throw new RSIllegalArgumentException(
                    "Bitmap has an unsupported format for this operation");
        }
        switch (bc) {
            case ALPHA_8:
                if (mType.getElement().mKind != Element.DataKind.PIXEL_A) {
                    throw new RSIllegalArgumentException(bitmapMismatch(bc));
                }
                break;
            case ARGB_8888:
                if ((mType.getElement().mKind != Element.DataKind.PIXEL_RGBA)
                        || (mType.getElement().getBytesSize() != 4)) {
                    throw new RSIllegalArgumentException(bitmapMismatch(bc));
                }
                break;
            case RGB_565:
                if ((mType.getElement().mKind != Element.DataKind.PIXEL_RGB)
                        || (mType.getElement().getBytesSize() != 2)) {
                    throw new RSIllegalArgumentException(bitmapMismatch(bc));
                }
                break;
            case ARGB_4444:
                if ((mType.getElement().mKind != Element.DataKind.PIXEL_RGBA)
                        || (mType.getElement().getBytesSize() != 2)) {
                    throw new RSIllegalArgumentException(bitmapMismatch(bc));
                }
                break;
            default:
                throw new RSIllegalArgumentException(
                        "Bitmap has an unsupported format for this operation");
        }
    }

    private String bitmapMismatch(Bitmap.Config bc) {
        return "Allocation kind is " + mType.getElement().mKind
                + ", type " + mType.getElement().mType
                + " of " + mType.getElement().getBytesSize()
                + " bytes, passed bitmap was " + bc;
    }

    private void validateBitmapSize(Bitmap b) {
        if ((mCurrentDimX != b.getWidth()) || (mCurrentDimY != b.getHeight())) {
            throw new RSIllegalArgumentException(
                    "Cannot update allocation from bitmap, sizes mismatch");
        }
    }

    /**
     * Packs one ARGB_8888 pixel into this Allocation's Element layout.
     *
     * Bitmap always hands Java an ARGB int regardless of its own Config, but
     * the Allocation stores what the ELEMENT says: RGBA_8888 is four bytes in
     * R,G,B,A order (not the int's A,R,G,B), A_8 is the alpha byte alone, and
     * the packed 16-bit formats are one little-endian short.
     */
    private void putPixel(ByteBuffer bb, int pos, int argb) {
        final Element e = mType.getElement();
        final int a = (argb >>> 24) & 0xff;
        final int r = (argb >>> 16) & 0xff;
        final int g = (argb >>> 8) & 0xff;
        final int b = argb & 0xff;
        switch (e.mType) {
            case UNSIGNED_5_6_5:
                bb.putShort(pos, (short) (((r & 0xf8) << 8) | ((g & 0xfc) << 3) | (b >> 3)));
                break;
            case UNSIGNED_4_4_4_4:
                bb.putShort(pos, (short) (((r & 0xf0) << 8) | ((g & 0xf0) << 4)
                        | (b & 0xf0) | (a >> 4)));
                break;
            case UNSIGNED_5_5_5_1:
                bb.putShort(pos, (short) (((r & 0xf8) << 8) | ((g & 0xf8) << 3)
                        | ((b & 0xf8) >> 2) | (a >= 128 ? 1 : 0)));
                break;
            default:
                if (e.mKind == Element.DataKind.PIXEL_A) {
                    bb.put(pos, (byte) a);
                    break;
                }
                // Branch on the COMPONENT count, not the byte size: RGB_888 is
                // three components in four bytes (the vec3 padding rule), so a
                // size test would write alpha into the pad slot.
                final int nw = IntrinsicSupport.componentCount(e);
                if (nw >= 3) {
                    bb.put(pos, (byte) r);
                    bb.put(pos + 1, (byte) g);
                    bb.put(pos + 2, (byte) b);
                    if (nw >= 4) {
                        bb.put(pos + 3, (byte) a);
                    }
                } else if (nw == 2) {
                    bb.put(pos, (byte) r);
                    bb.put(pos + 1, (byte) a);
                } else {
                    bb.put(pos, (byte) r);
                }
                break;
        }
    }

    /** The inverse of {@link #putPixel}: one cell back to an ARGB_8888 int. */
    private int getPixel(ByteBuffer bb, int pos) {
        final Element e = mType.getElement();
        switch (e.mType) {
            case UNSIGNED_5_6_5: {
                int v = bb.getShort(pos) & 0xffff;
                int r = (v >> 11) & 0x1f;
                int g = (v >> 5) & 0x3f;
                int b = v & 0x1f;
                // Replicate the high bits into the low ones so 0x1f maps to 0xff.
                r = (r << 3) | (r >> 2);
                g = (g << 2) | (g >> 4);
                b = (b << 3) | (b >> 2);
                return 0xff000000 | (r << 16) | (g << 8) | b;
            }
            case UNSIGNED_4_4_4_4: {
                int v = bb.getShort(pos) & 0xffff;
                int r = (v >> 12) & 0xf;
                int g = (v >> 8) & 0xf;
                int b = (v >> 4) & 0xf;
                int a = v & 0xf;
                return ((a << 4 | a) << 24) | ((r << 4 | r) << 16)
                        | ((g << 4 | g) << 8) | (b << 4 | b);
            }
            case UNSIGNED_5_5_5_1: {
                int v = bb.getShort(pos) & 0xffff;
                int r = (v >> 11) & 0x1f;
                int g = (v >> 6) & 0x1f;
                int b = (v >> 1) & 0x1f;
                int a = (v & 1) != 0 ? 0xff : 0;
                r = (r << 3) | (r >> 2);
                g = (g << 3) | (g >> 2);
                b = (b << 3) | (b >> 2);
                return (a << 24) | (r << 16) | (g << 8) | b;
            }
            default:
                if (e.mKind == Element.DataKind.PIXEL_A) {
                    return (bb.get(pos) & 0xff) << 24;
                }
                final int nr = IntrinsicSupport.componentCount(e);
                if (nr >= 4) {
                    return ((bb.get(pos + 3) & 0xff) << 24) | ((bb.get(pos) & 0xff) << 16)
                            | ((bb.get(pos + 1) & 0xff) << 8) | (bb.get(pos + 2) & 0xff);
                }
                if (nr == 3) {
                    // Opaque: the 4th byte of an RGB_888 cell is padding, not alpha.
                    return 0xff000000 | ((bb.get(pos) & 0xff) << 16)
                            | ((bb.get(pos + 1) & 0xff) << 8) | (bb.get(pos + 2) & 0xff);
                }
                if (nr == 2) {
                    int lum = bb.get(pos) & 0xff;
                    return ((bb.get(pos + 1) & 0xff) << 24) | (lum << 16) | (lum << 8) | lum;
                }
                int l = bb.get(pos) & 0xff;
                return 0xff000000 | (l << 16) | (l << 8) | l;
        }
    }

    private void writeBitmap(Bitmap b, int xoff, int yoff, int w, int h) {
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        writePixels(px, xoff, yoff, w, h);
    }

    private void writePixels(int[] px, int xoff, int yoff, int w, int h) {
        final ByteBuffer bb = ByteBuffer.wrap(data()).order(ByteOrder.LITTLE_ENDIAN);
        final int cell = mType.getElement().getBytesSize();
        final int base = baseOffset();
        final int stride = rowStride();
        for (int y = 0; y < h; y++) {
            int dst = base + ((yoff + y) * stride + xoff) * cell;
            int src = y * w;
            for (int x = 0; x < w; x++) {
                putPixel(bb, dst, px[src + x]);
                dst += cell;
            }
        }
    }

    private void readPixels(int[] px, int xoff, int yoff, int w, int h) {
        final ByteBuffer bb = ByteBuffer.wrap(data()).order(ByteOrder.LITTLE_ENDIAN);
        final int cell = mType.getElement().getBytesSize();
        final int base = baseOffset();
        final int stride = rowStride();
        for (int y = 0; y < h; y++) {
            int s = base + ((yoff + y) * stride + xoff) * cell;
            int d = y * w;
            for (int x = 0; x < w; x++) {
                px[d + x] = getPixel(bb, s);
                s += cell;
            }
        }
    }

    private void writeFacePixels(int[] px, int face, int w, int h) {
        final ByteBuffer bb = ByteBuffer.wrap(data()).order(ByteOrder.LITTLE_ENDIAN);
        final int cell = mType.getElement().getBytesSize();
        int dst = face * w * h * cell;
        for (int i = 0; i < w * h; i++) {
            putPixel(bb, dst, px[i]);
            dst += cell;
        }
    }

    // ---- copyFrom / copyTo --------------------------------------------------

    private void copyFromUnchecked(Object array, Element.DataType dt, int arrayLen) {
        mRS.validate();
        if (mCurrentDimZ > 0) {
            copy3DRangeFromUnchecked(0, 0, 0, mCurrentDimX, mCurrentDimY, mCurrentDimZ,
                    array, dt, arrayLen);
        } else if (mCurrentDimY > 0) {
            copy2DRangeFromUnchecked(0, 0, mCurrentDimX, mCurrentDimY, array, dt, arrayLen);
        } else {
            copy1DRangeFromUnchecked(0, mCurrentCount, array, dt, arrayLen);
        }
    }

    public void copyFromUnchecked(Object array) {
        copyFromUnchecked(array, validateObjectIsPrimitiveArray(array, false),
                java.lang.reflect.Array.getLength(array));
    }

    public void copyFromUnchecked(int[] d) {
        copyFromUnchecked(d, Element.DataType.SIGNED_32, d.length);
    }

    public void copyFromUnchecked(short[] d) {
        copyFromUnchecked(d, Element.DataType.SIGNED_16, d.length);
    }

    public void copyFromUnchecked(byte[] d) {
        copyFromUnchecked(d, Element.DataType.SIGNED_8, d.length);
    }

    public void copyFromUnchecked(float[] d) {
        copyFromUnchecked(d, Element.DataType.FLOAT_32, d.length);
    }

    public void copyFrom(Object array) {
        copyFromUnchecked(array, validateObjectIsPrimitiveArray(array, true),
                java.lang.reflect.Array.getLength(array));
    }

    public void copyFrom(int[] d) {
        validateIsInt32();
        copyFromUnchecked(d, Element.DataType.SIGNED_32, d.length);
    }

    public void copyFrom(short[] d) {
        validateIsInt16OrFloat16();
        copyFromUnchecked(d, Element.DataType.SIGNED_16, d.length);
    }

    public void copyFrom(byte[] d) {
        validateIsInt8();
        copyFromUnchecked(d, Element.DataType.SIGNED_8, d.length);
    }

    public void copyFrom(float[] d) {
        validateIsFloat32();
        copyFromUnchecked(d, Element.DataType.FLOAT_32, d.length);
    }

    public void copyFrom(Bitmap b) {
        mRS.validate();
        if (b.getConfig() == null) {
            // A HARDWARE or otherwise opaque Bitmap has no readable Config;
            // AOSP copies it into an ARGB_8888 one first and so do we.
            Bitmap newBitmap = Bitmap.createBitmap(b.getWidth(), b.getHeight(),
                    Bitmap.Config.ARGB_8888);
            new android.graphics.Canvas(newBitmap).drawBitmap(b, 0, 0, null);
            copyFrom(newBitmap);
            return;
        }
        validateBitmapSize(b);
        validateBitmapFormat(b);
        writeBitmap(b, 0, 0, mCurrentDimX, Math.max(1, mCurrentDimY));
        mBitmap = b;
    }

    /** Copies from another Allocation; the Types must be identical. */
    public void copyFrom(Allocation a) {
        mRS.validate();
        if (!mType.equals(a.getType())) {
            // Type identity is per-object on a device too, so compare shape.
            if (mType.getX() != a.mType.getX() || mType.getY() != a.mType.getY()
                    || mType.getZ() != a.mType.getZ()
                    || !mType.getElement().isCompatible(a.mType.getElement())) {
                throw new RSIllegalArgumentException("Types of allocations must match.");
            }
        }
        int n = Math.min(getBytesSize(), a.getBytesSize());
        System.arraycopy(a.data(), a.baseOffset(), data(), baseOffset(), n);
    }

    /**
     * Copies an array of RS object handles. Mac.apk has no driver-side object
     * table, so there is nothing meaningful to store; the validation still runs
     * so an app pouring the wrong array type in fails the same way.
     */
    public void copyFrom(BaseObj[] d) {
        mRS.validate();
        validateIsObject();
        if (d.length != mCurrentCount) {
            throw new RSIllegalArgumentException("Array size mismatch, allocation sizeX = "
                    + mCurrentCount + ", array length = " + d.length);
        }
        throw new RSDriverException(
                "Allocations of RS object handles need a RenderScript driver, which Mac.apk"
                + " does not have. Only data Elements are supported.");
    }

    public void copyTo(Bitmap b) {
        mRS.validate();
        validateBitmapFormat(b);
        validateBitmapSize(b);
        int w = mCurrentDimX;
        int h = Math.max(1, mCurrentDimY);
        int[] px = new int[w * h];
        readPixels(px, 0, 0, w, h);
        b.setPixels(px, 0, w, 0, 0, w, h);
    }

    private void copyTo(Object array, Element.DataType dt, int arrayLen) {
        mRS.validate();
        boolean usePadding = mAutoPadding && (mType.getElement().getVectorSize() == 3);
        int dataSize = mType.getElement().getBytesSize() * mCurrentCount;
        if (usePadding) {
            if (arrayLen * dt.mSize < dataSize / 4 * 3) {
                throw new RSIllegalArgumentException("Array too small for allocation type.");
            }
        } else if (arrayLen * dt.mSize < dataSize) {
            throw new RSIllegalArgumentException("Array too small for allocation type.");
        }
        copyCells(0, mCurrentCount, array, dt, false);
        // A device reads the driver's WHOLE buffer here, which for a YUV
        // Allocation includes the chroma planes past the luma; the size check
        // above deliberately only demands the luma bytes, exactly as AOSP's
        // does, so a caller who asked for luma only still gets luma only.
        moveYuvChroma(0, 0, mCurrentDimX, Math.max(1, mCurrentDimY), array, dt, arrayLen, false);
    }

    public void copyTo(Object array) {
        copyTo(array, validateObjectIsPrimitiveArray(array, true),
                java.lang.reflect.Array.getLength(array));
    }

    public void copyTo(byte[] d) {
        validateIsInt8();
        copyTo(d, Element.DataType.SIGNED_8, d.length);
    }

    public void copyTo(short[] d) {
        validateIsInt16OrFloat16();
        copyTo(d, Element.DataType.SIGNED_16, d.length);
    }

    public void copyTo(int[] d) {
        validateIsInt32();
        copyTo(d, Element.DataType.SIGNED_32, d.length);
    }

    public void copyTo(float[] d) {
        validateIsFloat32();
        copyTo(d, Element.DataType.FLOAT_32, d.length);
    }

    // ---- 1D ranges ----------------------------------------------------------

    private void copy1DRangeFromUnchecked(int off, int count, Object array,
                                          Element.DataType dt, int arrayLen) {
        final int dataSize = mType.mElement.getBytesSize() * count;
        boolean usePadding = mAutoPadding && (mType.getElement().getVectorSize() == 3);
        data1DChecks(off, count, arrayLen * dt.mSize, dataSize, usePadding);
        copyCells(off, count, array, dt, true);
    }

    public void copy1DRangeFromUnchecked(int off, int count, Object array) {
        copy1DRangeFromUnchecked(off, count, array,
                validateObjectIsPrimitiveArray(array, false),
                java.lang.reflect.Array.getLength(array));
    }

    public void copy1DRangeFromUnchecked(int off, int count, int[] d) {
        copy1DRangeFromUnchecked(off, count, d, Element.DataType.SIGNED_32, d.length);
    }

    public void copy1DRangeFromUnchecked(int off, int count, short[] d) {
        copy1DRangeFromUnchecked(off, count, d, Element.DataType.SIGNED_16, d.length);
    }

    public void copy1DRangeFromUnchecked(int off, int count, byte[] d) {
        copy1DRangeFromUnchecked(off, count, d, Element.DataType.SIGNED_8, d.length);
    }

    public void copy1DRangeFromUnchecked(int off, int count, float[] d) {
        copy1DRangeFromUnchecked(off, count, d, Element.DataType.FLOAT_32, d.length);
    }

    public void copy1DRangeFrom(int off, int count, Object array) {
        copy1DRangeFromUnchecked(off, count, array,
                validateObjectIsPrimitiveArray(array, true),
                java.lang.reflect.Array.getLength(array));
    }

    public void copy1DRangeFrom(int off, int count, int[] d) {
        validateIsInt32();
        copy1DRangeFromUnchecked(off, count, d, Element.DataType.SIGNED_32, d.length);
    }

    public void copy1DRangeFrom(int off, int count, short[] d) {
        validateIsInt16OrFloat16();
        copy1DRangeFromUnchecked(off, count, d, Element.DataType.SIGNED_16, d.length);
    }

    public void copy1DRangeFrom(int off, int count, byte[] d) {
        validateIsInt8();
        copy1DRangeFromUnchecked(off, count, d, Element.DataType.SIGNED_8, d.length);
    }

    public void copy1DRangeFrom(int off, int count, float[] d) {
        validateIsFloat32();
        copy1DRangeFromUnchecked(off, count, d, Element.DataType.FLOAT_32, d.length);
    }

    /** Copies a 1D range out of another Allocation. */
    public void copy1DRangeFrom(int off, int count, Allocation data, int dataOff) {
        mRS.validate();
        if (count < 1) {
            throw new RSIllegalArgumentException("Count must be >= 1.");
        }
        if (off < 0 || (off + count) > mCurrentCount) {
            throw new RSIllegalArgumentException("Overflow, Available count " + mCurrentCount
                    + ", got " + count + " at offset " + off + ".");
        }
        final int cell = mType.getElement().getBytesSize();
        final int srcCell = data.mType.getElement().getBytesSize();
        if (cell != srcCell) {
            throw new RSIllegalArgumentException("Element sizes of allocations must match.");
        }
        System.arraycopy(data.data(), data.baseOffset() + dataOff * srcCell,
                data(), baseOffset() + off * cell, count * cell);
    }

    private void copy1DRangeToUnchecked(int off, int count, Object array,
                                        Element.DataType dt, int arrayLen) {
        final int dataSize = mType.mElement.getBytesSize() * count;
        boolean usePadding = mAutoPadding && (mType.getElement().getVectorSize() == 3);
        data1DChecks(off, count, arrayLen * dt.mSize, dataSize, usePadding);
        copyCells(off, count, array, dt, false);
    }

    public void copy1DRangeToUnchecked(int off, int count, Object array) {
        copy1DRangeToUnchecked(off, count, array,
                validateObjectIsPrimitiveArray(array, false),
                java.lang.reflect.Array.getLength(array));
    }

    public void copy1DRangeToUnchecked(int off, int count, int[] d) {
        copy1DRangeToUnchecked(off, count, d, Element.DataType.SIGNED_32, d.length);
    }

    public void copy1DRangeToUnchecked(int off, int count, short[] d) {
        copy1DRangeToUnchecked(off, count, d, Element.DataType.SIGNED_16, d.length);
    }

    public void copy1DRangeToUnchecked(int off, int count, byte[] d) {
        copy1DRangeToUnchecked(off, count, d, Element.DataType.SIGNED_8, d.length);
    }

    public void copy1DRangeToUnchecked(int off, int count, float[] d) {
        copy1DRangeToUnchecked(off, count, d, Element.DataType.FLOAT_32, d.length);
    }

    public void copy1DRangeTo(int off, int count, Object array) {
        copy1DRangeToUnchecked(off, count, array,
                validateObjectIsPrimitiveArray(array, true),
                java.lang.reflect.Array.getLength(array));
    }

    public void copy1DRangeTo(int off, int count, int[] d) {
        validateIsInt32();
        copy1DRangeToUnchecked(off, count, d, Element.DataType.SIGNED_32, d.length);
    }

    public void copy1DRangeTo(int off, int count, short[] d) {
        validateIsInt16OrFloat16();
        copy1DRangeToUnchecked(off, count, d, Element.DataType.SIGNED_16, d.length);
    }

    public void copy1DRangeTo(int off, int count, byte[] d) {
        validateIsInt8();
        copy1DRangeToUnchecked(off, count, d, Element.DataType.SIGNED_8, d.length);
    }

    public void copy1DRangeTo(int off, int count, float[] d) {
        validateIsFloat32();
        copy1DRangeToUnchecked(off, count, d, Element.DataType.FLOAT_32, d.length);
    }

    // ---- 2D ranges ----------------------------------------------------------

    private void validate2DRange(int xoff, int yoff, int w, int h) {
        if (xoff < 0 || yoff < 0) {
            throw new RSIllegalArgumentException("Offset cannot be negative.");
        }
        if (h < 0 || w < 0) {
            throw new RSIllegalArgumentException("Height or width cannot be negative.");
        }
        if (((xoff + w) > mCurrentDimX) || ((yoff + h) > mCurrentDimY)) {
            throw new RSIllegalArgumentException("Updated region larger than allocation.");
        }
    }

    /**
     * Moves a rectangle a row at a time. A 2D range is NOT contiguous in the
     * backing store (each row is followed by the rest of the Allocation's
     * width), so the row loop is required, not an optimisation.
     */
    private void move2D(int xoff, int yoff, int w, int h, Object array,
                        Element.DataType dt, boolean toAlloc) {
        final int cell = mType.getElement().getBytesSize();
        final boolean pad = mAutoPadding && mType.getElement().getVectorSize() == 3;
        final int compsPerCell = pad ? (cell / dt.mSize) / 4 * 3 : cell / dt.mSize;
        final int rowArrayLen = w * compsPerCell;
        final int base = baseOffset();

        for (int y = 0; y < h; y++) {
            int cellOff = (yoff + y) * rowStride() + xoff;
            if (cellIsDivisible(dt)) {
                moveRow(base + cellOff * cell, w, array, dt, y * rowArrayLen, toAlloc);
            } else {
                rawRowMove(base + cellOff * cell, w * cell, array, dt,
                        y * (w * cell / dt.mSize), toAlloc);
            }
        }
    }

    private void moveRow(int byteOffset, int count, Object array, Element.DataType dt,
                         int arrayStart, boolean toAlloc) {
        final Element e = mType.getElement();
        final int cellBytes = e.getBytesSize();
        final boolean pad = mAutoPadding && e.getVectorSize() == 3;
        final int compBytes = dt.mSize;
        final int compsPerCell = pad ? (cellBytes / compBytes) / 4 * 3 : cellBytes / compBytes;
        final int skipBytes = cellBytes - compsPerCell * compBytes;

        final ByteBuffer bb = ByteBuffer.wrap(data()).order(ByteOrder.LITTLE_ENDIAN);
        int pos = byteOffset;
        int ai = arrayStart;
        for (int c = 0; c < count; c++) {
            for (int k = 0; k < compsPerCell; k++, ai++) {
                moveOne(bb, pos, array, ai, dt, toAlloc);
                pos += compBytes;
            }
            pos += skipBytes;
        }
    }

    private void rawRowMove(int byteOffset, int byteCount, Object array, Element.DataType dt,
                            int arrayStart, boolean toAlloc) {
        final ByteBuffer bb = ByteBuffer.wrap(data()).order(ByteOrder.LITTLE_ENDIAN);
        int pos = byteOffset;
        int ai = arrayStart;
        for (int moved = 0; moved < byteCount; moved += dt.mSize, ai++) {
            moveOne(bb, pos, array, ai, dt, toAlloc);
            pos += dt.mSize;
        }
    }

    private static void moveOne(ByteBuffer bb, int pos, Object array, int ai,
                                Element.DataType dt, boolean toAlloc) {
        switch (dt.mSize) {
            case 1:
                if (toAlloc) {
                    bb.put(pos, ((byte[]) array)[ai]);
                } else {
                    ((byte[]) array)[ai] = bb.get(pos);
                }
                break;
            case 2:
                if (toAlloc) {
                    bb.putShort(pos, ((short[]) array)[ai]);
                } else {
                    ((short[]) array)[ai] = bb.getShort(pos);
                }
                break;
            case 4:
                if (dt == Element.DataType.FLOAT_32) {
                    if (toAlloc) {
                        bb.putFloat(pos, ((float[]) array)[ai]);
                    } else {
                        ((float[]) array)[ai] = bb.getFloat(pos);
                    }
                } else {
                    if (toAlloc) {
                        bb.putInt(pos, ((int[]) array)[ai]);
                    } else {
                        ((int[]) array)[ai] = bb.getInt(pos);
                    }
                }
                break;
            case 8:
                if (dt == Element.DataType.FLOAT_64) {
                    if (toAlloc) {
                        bb.putDouble(pos, ((double[]) array)[ai]);
                    } else {
                        ((double[]) array)[ai] = bb.getDouble(pos);
                    }
                } else {
                    if (toAlloc) {
                        bb.putLong(pos, ((long[]) array)[ai]);
                    } else {
                        ((long[]) array)[ai] = bb.getLong(pos);
                    }
                }
                break;
            default:
                throw new RSIllegalArgumentException("Unsupported component size " + dt.mSize);
        }
    }

    private void copy2DRangeFromUnchecked(int xoff, int yoff, int w, int h, Object array,
                                          Element.DataType dt, int arrayLen) {
        mRS.validate();
        validate2DRange(xoff, yoff, w, h);
        final int dataSize = mType.mElement.getBytesSize() * w * h;
        boolean usePadding = mAutoPadding && (mType.getElement().getVectorSize() == 3);
        int needed = usePadding ? dataSize / 4 * 3 : dataSize;
        if (arrayLen * dt.mSize < needed) {
            throw new RSIllegalArgumentException("Array too small for allocation type.");
        }
        move2D(xoff, yoff, w, h, array, dt, true);
        moveYuvChroma(xoff, yoff, w, h, array, dt, arrayLen, true);
    }

    /**
     * Copies the CHROMA planes that follow the luma plane of a YUV Allocation.
     *
     * This is the canonical camera-preview path: an app builds a Type with
     * setYuvFormat(NV21), then hands the whole 1.5x NV21 buffer to
     * copyFrom(byte[]). Java-side that lands in copy2DRangeFrom, which by
     * itself would move only w*h bytes and leave the chroma at zero, so every
     * frame would come out grey. The driver avoids that by continuing past the
     * luma plane inside rsdAllocationData2D, and so do we.
     *
     * The layouts are the driver's (DeriveYUVLayout in rsdAllocation.cpp):
     *   NV21 / YUV_420_888: one interleaved chroma block of yStride bytes per
     *                       row for h/2 rows, immediately after luma.
     *   YV12: two half-width planes. The driver stores lod 2 first and lod 1
     *         second but FILLS lod 1 first, so the first chroma block in the
     *         caller's array lands in the second stored plane.
     *
     * Skipped unless the range covers the whole image and the array is long
     * enough, so a caller that passes only the luma plane gets the luma
     * behaviour rather than an out-of-bounds read.
     */
    private void moveYuvChroma(int xoff, int yoff, int w, int h, Object array,
                               Element.DataType dt, int arrayLen, boolean toAlloc) {
        final int fmt = mType.mDimYuv;
        if (fmt == 0) {
            return;
        }
        final int dimY = Math.max(1, mCurrentDimY);
        if (xoff != 0 || yoff != 0 || w != mCurrentDimX || h != dimY) {
            return;
        }
        final int cell = mType.getElement().getBytesSize();
        final int yStride = rowStride() * cell;
        final int ySize = yStride * dimY;
        final int chRows = dimY / 2;
        if (chRows < 1) {
            return;
        }
        final int base = baseOffset() + ySize;
        final int consumed = (w * h * cell) / dt.mSize;

        if (fmt == android.graphics.ImageFormat.YV12) {
            final int cw = mCurrentDimX / 2;
            final int planeBytes = cw * chRows * cell;
            if (planeBytes < 1) {
                return;
            }
            if (arrayLen * dt.mSize < ySize + 2 * planeBytes
                    || base + 2 * planeBytes > data().length) {
                return;
            }
            rawRowMove(base + planeBytes, planeBytes, array, dt, consumed, toAlloc);
            rawRowMove(base, planeBytes, array, dt, consumed + planeBytes / dt.mSize, toAlloc);
        } else {
            final int bytes = yStride * chRows;
            if (arrayLen * dt.mSize < ySize + bytes || base + bytes > data().length) {
                return;
            }
            rawRowMove(base, bytes, array, dt, consumed, toAlloc);
        }
    }

    public void copy2DRangeFrom(int xoff, int yoff, int w, int h, Object array) {
        copy2DRangeFromUnchecked(xoff, yoff, w, h, array,
                validateObjectIsPrimitiveArray(array, true),
                java.lang.reflect.Array.getLength(array));
    }

    public void copy2DRangeFrom(int xoff, int yoff, int w, int h, byte[] data) {
        validateIsInt8();
        copy2DRangeFromUnchecked(xoff, yoff, w, h, data, Element.DataType.SIGNED_8, data.length);
    }

    public void copy2DRangeFrom(int xoff, int yoff, int w, int h, short[] data) {
        validateIsInt16OrFloat16();
        copy2DRangeFromUnchecked(xoff, yoff, w, h, data, Element.DataType.SIGNED_16, data.length);
    }

    public void copy2DRangeFrom(int xoff, int yoff, int w, int h, int[] data) {
        validateIsInt32();
        copy2DRangeFromUnchecked(xoff, yoff, w, h, data, Element.DataType.SIGNED_32, data.length);
    }

    public void copy2DRangeFrom(int xoff, int yoff, int w, int h, float[] data) {
        validateIsFloat32();
        copy2DRangeFromUnchecked(xoff, yoff, w, h, data, Element.DataType.FLOAT_32, data.length);
    }

    public void copy2DRangeFrom(int xoff, int yoff, int w, int h,
                                Allocation data, int dataXoff, int dataYoff) {
        mRS.validate();
        validate2DRange(xoff, yoff, w, h);
        final int cell = mType.getElement().getBytesSize();
        if (cell != data.mType.getElement().getBytesSize()) {
            throw new RSIllegalArgumentException("Element sizes of allocations must match.");
        }
        for (int y = 0; y < h; y++) {
            int src = data.baseOffset()
                    + ((dataYoff + y) * data.rowStride() + dataXoff) * cell;
            int dst = baseOffset() + ((yoff + y) * rowStride() + xoff) * cell;
            System.arraycopy(data.data(), src, data(), dst, w * cell);
        }
    }

    public void copy2DRangeFrom(int xoff, int yoff, Bitmap data) {
        mRS.validate();
        if (data.getConfig() == null) {
            Bitmap newBitmap = Bitmap.createBitmap(data.getWidth(), data.getHeight(),
                    Bitmap.Config.ARGB_8888);
            new android.graphics.Canvas(newBitmap).drawBitmap(data, 0, 0, null);
            copy2DRangeFrom(xoff, yoff, newBitmap);
            return;
        }
        validateBitmapFormat(data);
        validate2DRange(xoff, yoff, data.getWidth(), data.getHeight());
        writeBitmap(data, xoff, yoff, data.getWidth(), data.getHeight());
    }

    private void copy2DRangeToUnchecked(int xoff, int yoff, int w, int h, Object array,
                                        Element.DataType dt, int arrayLen) {
        mRS.validate();
        validate2DRange(xoff, yoff, w, h);
        final int dataSize = mType.mElement.getBytesSize() * w * h;
        boolean usePadding = mAutoPadding && (mType.getElement().getVectorSize() == 3);
        int needed = usePadding ? dataSize / 4 * 3 : dataSize;
        if (arrayLen * dt.mSize < needed) {
            throw new RSIllegalArgumentException("Array too small for allocation type.");
        }
        move2D(xoff, yoff, w, h, array, dt, false);
        moveYuvChroma(xoff, yoff, w, h, array, dt, arrayLen, false);
    }

    public void copy2DRangeTo(int xoff, int yoff, int w, int h, Object array) {
        copy2DRangeToUnchecked(xoff, yoff, w, h, array,
                validateObjectIsPrimitiveArray(array, true),
                java.lang.reflect.Array.getLength(array));
    }

    public void copy2DRangeTo(int xoff, int yoff, int w, int h, byte[] data) {
        validateIsInt8();
        copy2DRangeToUnchecked(xoff, yoff, w, h, data, Element.DataType.SIGNED_8, data.length);
    }

    public void copy2DRangeTo(int xoff, int yoff, int w, int h, short[] data) {
        validateIsInt16OrFloat16();
        copy2DRangeToUnchecked(xoff, yoff, w, h, data, Element.DataType.SIGNED_16, data.length);
    }

    public void copy2DRangeTo(int xoff, int yoff, int w, int h, int[] data) {
        validateIsInt32();
        copy2DRangeToUnchecked(xoff, yoff, w, h, data, Element.DataType.SIGNED_32, data.length);
    }

    public void copy2DRangeTo(int xoff, int yoff, int w, int h, float[] data) {
        validateIsFloat32();
        copy2DRangeToUnchecked(xoff, yoff, w, h, data, Element.DataType.FLOAT_32, data.length);
    }

    // ---- 3D ranges ----------------------------------------------------------

    private void validate3DRange(int xoff, int yoff, int zoff, int w, int h, int d) {
        if (xoff < 0 || yoff < 0 || zoff < 0) {
            throw new RSIllegalArgumentException("Offset cannot be negative.");
        }
        if (h < 0 || w < 0 || d < 0) {
            throw new RSIllegalArgumentException("Height or width cannot be negative.");
        }
        if (((xoff + w) > mCurrentDimX) || ((yoff + h) > mCurrentDimY)
                || ((zoff + d) > mCurrentDimZ)) {
            throw new RSIllegalArgumentException("Updated region larger than allocation.");
        }
    }

    private void move3D(int xoff, int yoff, int zoff, int w, int h, int d, Object array,
                        Element.DataType dt, boolean toAlloc) {
        final int cell = mType.getElement().getBytesSize();
        final boolean pad = mAutoPadding && mType.getElement().getVectorSize() == 3;
        final int compsPerCell = pad ? (cell / dt.mSize) / 4 * 3 : cell / dt.mSize;
        final int rowArrayLen = w * compsPerCell;
        final int base = baseOffset();
        final int planeCells = planeStride();

        int row = 0;
        for (int z = 0; z < d; z++) {
            for (int y = 0; y < h; y++, row++) {
                int cellOff = (zoff + z) * planeCells + (yoff + y) * rowStride() + xoff;
                if (cellIsDivisible(dt)) {
                    moveRow(base + cellOff * cell, w, array, dt, row * rowArrayLen, toAlloc);
                } else {
                    rawRowMove(base + cellOff * cell, w * cell, array, dt,
                            row * (w * cell / dt.mSize), toAlloc);
                }
            }
        }
    }

    private void copy3DRangeFromUnchecked(int xoff, int yoff, int zoff, int w, int h, int d,
                                          Object array, Element.DataType dt, int arrayLen) {
        mRS.validate();
        validate3DRange(xoff, yoff, zoff, w, h, d);
        final int dataSize = mType.mElement.getBytesSize() * w * h * d;
        boolean usePadding = mAutoPadding && (mType.getElement().getVectorSize() == 3);
        int needed = usePadding ? dataSize / 4 * 3 : dataSize;
        if (arrayLen * dt.mSize < needed) {
            throw new RSIllegalArgumentException("Array too small for allocation type.");
        }
        move3D(xoff, yoff, zoff, w, h, d, array, dt, true);
    }

    public void copy3DRangeFrom(int xoff, int yoff, int zoff, int w, int h, int d, Object array) {
        copy3DRangeFromUnchecked(xoff, yoff, zoff, w, h, d, array,
                validateObjectIsPrimitiveArray(array, true),
                java.lang.reflect.Array.getLength(array));
    }

    public void copy3DRangeFrom(int xoff, int yoff, int zoff, int w, int h, int d,
                                Allocation data, int dataXoff, int dataYoff, int dataZoff) {
        mRS.validate();
        validate3DRange(xoff, yoff, zoff, w, h, d);
        final int cell = mType.getElement().getBytesSize();
        if (cell != data.mType.getElement().getBytesSize()) {
            throw new RSIllegalArgumentException("Element sizes of allocations must match.");
        }
        final int dstPlane = planeStride();
        final int srcPlane = data.planeStride();
        for (int z = 0; z < d; z++) {
            for (int y = 0; y < h; y++) {
                int src = data.baseOffset() + ((dataZoff + z) * srcPlane
                        + (dataYoff + y) * data.rowStride() + dataXoff) * cell;
                int dst = baseOffset() + ((zoff + z) * dstPlane
                        + (yoff + y) * rowStride() + xoff) * cell;
                System.arraycopy(data.data(), src, data(), dst, w * cell);
            }
        }
    }

    public void copy3DRangeTo(int xoff, int yoff, int zoff, int w, int h, int d, Object array) {
        Element.DataType dt = validateObjectIsPrimitiveArray(array, true);
        int arrayLen = java.lang.reflect.Array.getLength(array);
        mRS.validate();
        validate3DRange(xoff, yoff, zoff, w, h, d);
        final int dataSize = mType.mElement.getBytesSize() * w * h * d;
        boolean usePadding = mAutoPadding && (mType.getElement().getVectorSize() == 3);
        int needed = usePadding ? dataSize / 4 * 3 : dataSize;
        if (arrayLen * dt.mSize < needed) {
            throw new RSIllegalArgumentException("Array too small for allocation type.");
        }
        move3D(xoff, yoff, zoff, w, h, d, array, dt, false);
    }

    // ---- FieldPacker writes -------------------------------------------------

    /**
     * Overwrites one cell from a {@link FieldPacker}'s bytes. This is how a
     * reflected ScriptField_* class pushes a struct into an Allocation, so the
     * bytes have to land exactly where the Element's layout says.
     */
    public void setFromFieldPacker(int xoff, FieldPacker fp) {
        mRS.validate();
        int eSize = mType.mElement.getBytesSize();
        final byte[] data = fp.getData();
        int data_length = fp.getPos();

        int count = data_length / eSize;
        if ((eSize * count) != data_length) {
            throw new RSIllegalArgumentException("Field packer length " + data_length
                    + " not divisible by element size " + eSize + ".");
        }
        data1DChecks(xoff, count, data_length, data_length, false);
        System.arraycopy(data, 0, data(), baseOffset() + xoff * eSize, data_length);
    }

    /** Overwrites ONE sub-element of one cell (component_number selects it). */
    public void setFromFieldPacker(int xoff, int component_number, FieldPacker fp) {
        setFromFieldPacker(xoff, 0, 0, component_number, fp);
    }

    public void setFromFieldPacker(int xoff, int yoff, int zoff, int component_number,
                                   FieldPacker fp) {
        mRS.validate();
        if (component_number >= mType.mElement.mElements.length) {
            throw new RSIllegalArgumentException("Component_number " + component_number
                    + " out of range.");
        }
        if (xoff < 0 || yoff < 0 || zoff < 0) {
            throw new RSIllegalArgumentException("Offset must be >= 0.");
        }

        final byte[] data = fp.getData();
        int data_length = fp.getPos();
        int eSize = mType.mElement.mElements[component_number].getBytesSize();
        eSize *= mType.mElement.mArraySizes[component_number];

        if (data_length != eSize) {
            throw new RSIllegalArgumentException("Field packer sizelength " + data_length
                    + " does not match component size " + eSize + ".");
        }

        final int cell = mType.mElement.getBytesSize();
        final int plane = planeStride();
        final int cellOff = zoff * plane + yoff * rowStride() + xoff;
        final int off = baseOffset() + cellOff * cell
                + mType.mElement.mOffsetInBytes[component_number];
        System.arraycopy(data, 0, data(), off, data_length);
    }

    // ---- mipmaps ------------------------------------------------------------

    /**
     * Fills every mipmap level below 0 by 2x2 box filtering the level above it.
     *
     * This is real work rather than a no-op on purpose. Nothing in Mac.apk
     * samples a mipmap chain today, but an app that calls this and gets an
     * exception fails where a device would not, and an app that calls it and
     * gets silence has an Allocation whose upper levels are zeros. Box
     * filtering is what the driver's own mipmap generator does.
     */
    public void generateMipmaps() {
        mRS.validate();
        if (!mType.hasMipmaps()) {
            throw new RSInvalidStateException(
                    "Attempted to generate mipmaps on an Allocation without mipmaps.");
        }
        final Element e = mType.getElement();
        final int faces = mType.hasFaces() ? 6 : 1;
        final byte[] buf = data();

        for (int lod = 1; ; lod++) {
            int px = Math.max(1, lodDim(mType.getX(), lod - 1));
            int py = Math.max(1, lodDim(mType.getY(), lod - 1));
            int pz = Math.max(1, lodDim(mType.getZ(), lod - 1));
            int cx = Math.max(1, lodDim(mType.getX(), lod));
            int cy = Math.max(1, lodDim(mType.getY(), lod));
            int cz = Math.max(1, lodDim(mType.getZ(), lod));
            if (px == cx && py == cy && pz == cz) {
                break;
            }
            for (int f = 0; f < faces; f++) {
                Type.CubemapFace face = faces == 6
                        ? Type.CubemapFace.values()[f] : Type.CubemapFace.POSITVE_X;
                int srcBase = lodFaceOffset(lod - 1, face);
                int dstBase = lodFaceOffset(lod, face);
                boxFilter(buf, e, srcBase, px, py, pz, dstBase, cx, cy, cz);
            }
        }
    }

    /**
     * Averages each 2x2x2 (or 2x2, or 2) neighbourhood of the source level into
     * one destination cell, component by component. Components are decoded to
     * double and re-encoded so the same code covers u8, i16, f32 and friends.
     */
    private static void boxFilter(byte[] buf, Element e, int srcBase, int sx, int sy, int sz,
                                  int dstBase, int dx, int dy, int dz) {
        final ByteBuffer bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
        final int cell = e.getBytesSize();
        final Element.DataType dt = e.mType;
        final int comps = dt.mSize > 0 ? cell / dt.mSize : 0;
        if (comps == 0) {
            return;
        }
        final int stepX = sx > dx ? 2 : 1;
        final int stepY = sy > dy ? 2 : 1;
        final int stepZ = sz > dz ? 2 : 1;

        for (int z = 0; z < dz; z++) {
            for (int y = 0; y < dy; y++) {
                for (int x = 0; x < dx; x++) {
                    int dst = dstBase + ((z * dy + y) * dx + x) * cell;
                    for (int c = 0; c < comps; c++) {
                        double sum = 0;
                        int n = 0;
                        for (int oz = 0; oz < stepZ; oz++) {
                            for (int oy = 0; oy < stepY; oy++) {
                                for (int ox = 0; ox < stepX; ox++) {
                                    int szz = Math.min(sz - 1, z * stepZ + oz);
                                    int syy = Math.min(sy - 1, y * stepY + oy);
                                    int sxx = Math.min(sx - 1, x * stepX + ox);
                                    int src = srcBase + ((szz * sy + syy) * sx + sxx) * cell;
                                    sum += readComponent(bb, src + c * dt.mSize, dt);
                                    n++;
                                }
                            }
                        }
                        writeComponent(bb, dst + c * dt.mSize, dt, sum / n);
                    }
                }
            }
        }
    }

    /** Component decode shared by the mipmap generator and the intrinsics. */
    static double readComponent(ByteBuffer bb, int pos, Element.DataType dt) {
        switch (dt) {
            case UNSIGNED_8:
            case BOOLEAN:
                return bb.get(pos) & 0xff;
            case SIGNED_8:
                return bb.get(pos);
            case UNSIGNED_16:
                return bb.getShort(pos) & 0xffff;
            case SIGNED_16:
                return bb.getShort(pos);
            case FLOAT_16:
                return halfToFloat(bb.getShort(pos) & 0xffff);
            case UNSIGNED_32:
                return bb.getInt(pos) & 0xffffffffL;
            case SIGNED_32:
                return bb.getInt(pos);
            case UNSIGNED_64:
            case SIGNED_64:
                return bb.getLong(pos);
            case FLOAT_32:
            case MATRIX_2X2:
            case MATRIX_3X3:
            case MATRIX_4X4:
                return bb.getFloat(pos);
            case FLOAT_64:
                return bb.getDouble(pos);
            default:
                return bb.get(pos) & 0xff;
        }
    }

    static void writeComponent(ByteBuffer bb, int pos, Element.DataType dt, double v) {
        switch (dt) {
            case UNSIGNED_8:
            case BOOLEAN:
                bb.put(pos, (byte) clampToLong(v + 0.5, 0, 255));
                break;
            case SIGNED_8:
                bb.put(pos, (byte) clampToLong(Math.rint(v), -128, 127));
                break;
            case UNSIGNED_16:
                bb.putShort(pos, (short) clampToLong(v + 0.5, 0, 65535));
                break;
            case SIGNED_16:
                bb.putShort(pos, (short) clampToLong(Math.rint(v), -32768, 32767));
                break;
            case FLOAT_16:
                bb.putShort(pos, floatToHalf((float) v));
                break;
            case UNSIGNED_32:
                bb.putInt(pos, (int) clampToLong(v + 0.5, 0, 4294967295L));
                break;
            case SIGNED_32:
                bb.putInt(pos, (int) clampToLong(Math.rint(v), Integer.MIN_VALUE,
                        Integer.MAX_VALUE));
                break;
            case UNSIGNED_64:
            case SIGNED_64:
                bb.putLong(pos, (long) v);
                break;
            case FLOAT_32:
            case MATRIX_2X2:
            case MATRIX_3X3:
            case MATRIX_4X4:
                bb.putFloat(pos, (float) v);
                break;
            case FLOAT_64:
                bb.putDouble(pos, v);
                break;
            default:
                bb.put(pos, (byte) clampToLong(v + 0.5, 0, 255));
                break;
        }
    }

    private static long clampToLong(double v, long lo, long hi) {
        if (Double.isNaN(v)) {
            return 0;
        }
        if (v <= lo) {
            return lo;
        }
        if (v >= hi) {
            return hi;
        }
        return (long) v;
    }

    /** IEEE 754 binary16 decode; RenderScript's FLOAT_16 is plain half. */
    static float halfToFloat(int h) {
        int s = (h >> 15) & 0x1;
        int exp = (h >> 10) & 0x1f;
        int man = h & 0x3ff;
        if (exp == 0) {
            if (man == 0) {
                return s == 0 ? 0.0f : -0.0f;
            }
            // Subnormal: no implicit leading 1, fixed exponent of -14.
            float v = man * (float) Math.pow(2, -24);
            return s == 0 ? v : -v;
        }
        if (exp == 0x1f) {
            if (man == 0) {
                return s == 0 ? Float.POSITIVE_INFINITY : Float.NEGATIVE_INFINITY;
            }
            return Float.NaN;
        }
        int bits = (s << 31) | ((exp - 15 + 127) << 23) | (man << 13);
        return Float.intBitsToFloat(bits);
    }

    static short floatToHalf(float f) {
        int bits = Float.floatToIntBits(f);
        int s = (bits >>> 16) & 0x8000;
        int val = (bits & 0x7fffffff);
        if (val >= 0x47800000) {
            // Overflow or NaN/Inf.
            if (val > 0x7f800000) {
                return (short) (s | 0x7e00);
            }
            return (short) (s | 0x7c00);
        }
        if (val < 0x38800000) {
            // Subnormal or zero in half precision.
            if (val < 0x33000000) {
                return (short) s;
            }
            int e = val >>> 23;
            int m = (val & 0x7fffff) | 0x800000;
            int shift = 126 - e;
            return (short) (s | (m >> (shift + 1)));
        }
        return (short) (s | ((val - 0x38000000) >>> 13));
    }

    // ---- everything the missing driver owns ---------------------------------

    /**
     * Only ever meaningful for USAGE_GRAPHICS_TEXTURE, which needs a GL/driver
     * texture object. Mac.apk has no RenderScript texture to sync to, and there
     * is nothing to lose by returning: the script-visible bytes are already the
     * single copy.
     */
    public void syncAll(int srcLocation) {
        mRS.validate();
        switch (srcLocation) {
            case USAGE_GRAPHICS_TEXTURE:
            case USAGE_SCRIPT:
            case USAGE_GRAPHICS_CONSTANTS:
            case USAGE_GRAPHICS_VERTEX:
            case USAGE_SHARED:
                break;
            default:
                throw new RSIllegalArgumentException("Source must be exactly one usage type.");
        }
    }

    public void setOnBufferAvailableListener(OnBufferAvailableListener callback) {
        mBufferNotifier = callback;
    }

    /**
     * A USAGE_IO_* Allocation is one end of a BufferQueue owned by the
     * RenderScript driver. Mac.apk has no such driver and cannot fabricate the
     * Surface, so this reports the missing capability instead of handing back
     * something that silently drops every frame.
     */
    public Surface getSurface() {
        if ((mUsage & USAGE_IO_INPUT) == 0) {
            throw new RSInvalidStateException("Allocation is not a surface texture.");
        }
        throw new RSDriverException(
                "USAGE_IO_INPUT needs a RenderScript driver BufferQueue, which Mac.apk does not"
                + " have. Read the Allocation with copyTo() instead.");
    }

    public void setSurface(Surface sur) {
        mRS.validate();
        if ((mUsage & USAGE_IO_OUTPUT) == 0) {
            throw new RSInvalidStateException("Allocation is not USAGE_IO_OUTPUT.");
        }
        throw new RSDriverException(
                "USAGE_IO_OUTPUT needs a RenderScript driver BufferQueue, which Mac.apk does not"
                + " have. Write the result to a Bitmap with copyTo() instead.");
    }

    public void ioSend() {
        mRS.validate();
        if ((mUsage & USAGE_IO_OUTPUT) == 0) {
            throw new RSIllegalArgumentException(
                    "Can only send buffer if IO_OUTPUT usage specified.");
        }
        throw new RSDriverException(
                "ioSend needs a RenderScript driver BufferQueue, which Mac.apk does not have.");
    }

    public void ioReceive() {
        mRS.validate();
        if ((mUsage & USAGE_IO_INPUT) == 0) {
            throw new RSIllegalArgumentException(
                    "Can only receive if IO_INPUT usage specified.");
        }
        throw new RSDriverException(
                "ioReceive needs a RenderScript driver BufferQueue, which Mac.apk does not have.");
    }

    /**
     * Resizing was removed from the platform at API 21 and throws there. AOSP
     * gates on the app's targetSdkVersion; we cannot read that without a
     * Context, so the gate is "we have a Context and it targets 21+".
     */
    public synchronized void resize(int dimX) {
        android.content.Context ctx = mRS != null ? mRS.getApplicationContext() : null;
        if (ctx == null || ctx.getApplicationInfo() == null
                || ctx.getApplicationInfo().targetSdkVersion >= 21) {
            throw new RSRuntimeException("Resize is not allowed in API 21+.");
        }
        if ((mType.getY() > 0) || (mType.getZ() > 0) || mType.hasFaces() || mType.hasMipmaps()) {
            throw new RSInvalidStateException(
                    "Resize only support for 1D allocations at this time.");
        }
        Type t = Type.createX(mRS, mType.getElement(), dimX);
        byte[] grown = new byte[byteSizeOf(t)];
        System.arraycopy(mData, 0, grown, 0, Math.min(mData.length, grown.length));
        mType = t;
        mData = grown;
        mByteBuffer = null;
        mByteBufferStride = -1;
        updateCacheInfo(mType);
    }

    /**
     * A direct view of the bytes. AOSP returns driver memory here; we hand back
     * a wrapper over the same array the copy methods use, so a write through the
     * buffer really is visible to the next forEach. Rows are tightly packed, so
     * the stride is exactly x * elementBytes.
     */
    public ByteBuffer getByteBuffer() {
        if (mType.hasFaces()) {
            throw new RSInvalidStateException("Cubemap is not supported for getByteBuffer().");
        }
        if (mType.getYuv() == android.graphics.ImageFormat.NV21
                || mType.getYuv() == android.graphics.ImageFormat.YV12
                || mType.getYuv() == android.graphics.ImageFormat.YUV_420_888) {
            throw new RSInvalidStateException("YUV format is not supported for getByteBuffer().");
        }
        if (mByteBuffer == null) {
            mByteBuffer = ByteBuffer.wrap(data()).order(ByteOrder.nativeOrder());
            mByteBufferStride = (long) mType.getX() * mType.getElement().getBytesSize();
        }
        if ((mUsage & USAGE_IO_INPUT) != 0) {
            return mByteBuffer.asReadOnlyBuffer();
        }
        return mByteBuffer;
    }

    /** @return bytes per row of {@link #getByteBuffer}. */
    public long getStride() {
        if (mByteBufferStride == -1) {
            getByteBuffer();
        }
        return mByteBufferStride;
    }
}
