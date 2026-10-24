package android.renderscript;

/**
 * An Element represents one item within an {@link Allocation}: roughly the C
 * type of a cell in a RenderScript kernel. Elements are either BASIC (a
 * DataType plus a vector size, e.g. uchar4) or COMPLEX (a struct of named
 * sub-Elements, built with {@link Element.Builder}).
 *
 * This is a full implementation, not a shim, because Allocation's byte layout
 * is derived entirely from it and every intrinsic reads that layout. Two rules
 * are easy to get wrong and are load bearing:
 *
 *  - A vector size of THREE occupies FOUR components' worth of bytes. float3 is
 *    16 bytes, not 12. RenderScript inherits that from OpenCL, and Allocation's
 *    auto-padding mode exists purely to hide it from Java arrays. Getting this
 *    wrong shifts every cell after the first.
 *  - The packed pixel types (5_6_5, 5_5_5_1, 4_4_4_4) are ONE unit wide
 *    regardless of how many channels they describe, so their size is the
 *    DataType's size and not size * vectorSize.
 *
 * The opaque RS_* handle types size to the driver's pointer width. Mac.apk is
 * 64-bit-only, so they are 32 bytes (see RenderScript.sPointerSize).
 *
 * Source: frameworks/rs/java/android/renderscript/Element.java
 */
public class Element extends BaseObj {

    int mSize;
    Element[] mElements;
    String[] mElementNames;
    int[] mArraySizes;
    int[] mOffsetInBytes;
    int[] mVisibleElementMap;

    DataType mType;
    DataKind mKind;
    boolean mNormalized;
    int mVectorSize;

    /**
     * The basic type information for a basic Element. For numeric types the
     * name is FLOAT / SIGNED / UNSIGNED followed by the bit width. BOOLEAN is
     * true/false in an 8 bit container. The UNSIGNED variants with several bit
     * widths are packed graphics formats and count as a single unit for packing
     * and alignment. The three MATRIX types hold FLOAT_32 elements and are
     * treated as 32 bits for alignment. The RS_* entries are opaque driver
     * handles whose size follows the pointer width.
     */
    public enum DataType {
        NONE(0, 0),
        FLOAT_16(1, 2),
        FLOAT_32(2, 4),
        FLOAT_64(3, 8),
        SIGNED_8(4, 1),
        SIGNED_16(5, 2),
        SIGNED_32(6, 4),
        SIGNED_64(7, 8),
        UNSIGNED_8(8, 1),
        UNSIGNED_16(9, 2),
        UNSIGNED_32(10, 4),
        UNSIGNED_64(11, 8),

        BOOLEAN(12, 1),

        UNSIGNED_5_6_5(13, 2),
        UNSIGNED_5_5_5_1(14, 2),
        UNSIGNED_4_4_4_4(15, 2),

        MATRIX_4X4(16, 64),
        MATRIX_3X3(17, 36),
        MATRIX_2X2(18, 16),

        RS_ELEMENT(1000),
        RS_TYPE(1001),
        RS_ALLOCATION(1002),
        RS_SAMPLER(1003),
        RS_SCRIPT(1004),
        RS_MESH(1005),
        RS_PROGRAM_FRAGMENT(1006),
        RS_PROGRAM_VERTEX(1007),
        RS_PROGRAM_RASTER(1008),
        RS_PROGRAM_STORE(1009),
        RS_FONT(1010);

        int mID;
        int mSize;

        DataType(int id, int size) {
            mID = id;
            mSize = size;
        }

        DataType(int id) {
            mID = id;
            mSize = 4;
            if (RenderScript.sPointerSize == 8) {
                mSize = 32;
            }
        }
    }

    /**
     * How a {@link Sampler} should interpret the data. USER means no special
     * interpretation. The PIXEL_* kinds pair with the unsigned DataTypes to
     * describe texture formats.
     */
    public enum DataKind {
        USER(0),

        PIXEL_L(7),
        PIXEL_A(8),
        PIXEL_LA(9),
        PIXEL_RGB(10),
        PIXEL_RGBA(11),
        PIXEL_DEPTH(12),
        PIXEL_YUV(13);

        int mID;

        DataKind(int id) {
            mID = id;
        }
    }

    /** Basic Element: a DataType repeated vectorSize times. */
    Element(RenderScript rs, DataType dt, DataKind dk, boolean norm, int size) {
        super(rs);
        if ((dt != DataType.UNSIGNED_5_6_5)
                && (dt != DataType.UNSIGNED_4_4_4_4)
                && (dt != DataType.UNSIGNED_5_5_5_1)) {
            // A vec3 is stored in vec4's worth of space. See the class comment.
            if (size == 3) {
                mSize = dt.mSize * 4;
            } else {
                mSize = dt.mSize * size;
            }
        } else {
            mSize = dt.mSize;
        }
        mType = dt;
        mKind = dk;
        mNormalized = norm;
        mVectorSize = size;
    }

    /** Complex (struct) Element built by {@link Element.Builder}. */
    Element(RenderScript rs, Element[] e, String[] n, int[] as) {
        super(rs);
        mSize = 0;
        mVectorSize = 1;
        mElements = e;
        mElementNames = n;
        mArraySizes = as;
        mType = DataType.NONE;
        mKind = DataKind.USER;
        mOffsetInBytes = new int[mElements.length];
        for (int ct = 0; ct < mElements.length; ct++) {
            mOffsetInBytes[ct] = mSize;
            mSize += mElements[ct].mSize * mArraySizes[ct];
        }
        updateVisibleSubElements();
    }

    /**
     * Sub-element names beginning with '#' are compiler-inserted padding and
     * are hidden from getSubElement*(). The map translates a visible index into
     * a storage index.
     */
    private void updateVisibleSubElements() {
        if (mElements == null) {
            return;
        }

        int noPaddingFieldCount = 0;
        int fieldCount = mElementNames.length;
        for (int ct = 0; ct < fieldCount; ct++) {
            if (mElementNames[ct].charAt(0) != '#') {
                noPaddingFieldCount++;
            }
        }
        mVisibleElementMap = new int[noPaddingFieldCount];

        for (int ct = 0, ctNoPadding = 0; ct < fieldCount; ct++) {
            if (mElementNames[ct].charAt(0) != '#') {
                mVisibleElementMap[ctNoPadding++] = ct;
            }
        }
    }

    /** @return element size in bytes. */
    public int getBytesSize() {
        return mSize;
    }

    /** @return number of vector components: 2 for float2, 4 for float4, etc. */
    public int getVectorSize() {
        return mVectorSize;
    }

    /** @return true if this Element is too complex to feed a Mesh or Program. */
    public boolean isComplex() {
        if (mElements == null) {
            return false;
        }
        for (int ct = 0; ct < mElements.length; ct++) {
            if (mElements[ct].mElements != null) {
                return true;
            }
        }
        return false;
    }

    /** @return 0 for a basic Element, else the number of visible sub-elements. */
    public int getSubElementCount() {
        if (mVisibleElementMap == null) {
            return 0;
        }
        return mVisibleElementMap.length;
    }

    public Element getSubElement(int index) {
        if (mVisibleElementMap == null) {
            throw new RSIllegalArgumentException("Element contains no sub-elements");
        }
        if (index < 0 || index >= mVisibleElementMap.length) {
            throw new RSIllegalArgumentException("Illegal sub-element index");
        }
        return mElements[mVisibleElementMap[index]];
    }

    public String getSubElementName(int index) {
        if (mVisibleElementMap == null) {
            throw new RSIllegalArgumentException("Element contains no sub-elements");
        }
        if (index < 0 || index >= mVisibleElementMap.length) {
            throw new RSIllegalArgumentException("Illegal sub-element index");
        }
        return mElementNames[mVisibleElementMap[index]];
    }

    public int getSubElementArraySize(int index) {
        if (mVisibleElementMap == null) {
            throw new RSIllegalArgumentException("Element contains no sub-elements");
        }
        if (index < 0 || index >= mVisibleElementMap.length) {
            throw new RSIllegalArgumentException("Illegal sub-element index");
        }
        return mArraySizes[mVisibleElementMap[index]];
    }

    public int getSubElementOffsetBytes(int index) {
        if (mVisibleElementMap == null) {
            throw new RSIllegalArgumentException("Element contains no sub-elements");
        }
        if (index < 0 || index >= mVisibleElementMap.length) {
            throw new RSIllegalArgumentException("Illegal sub-element index");
        }
        return mOffsetInBytes[mVisibleElementMap[index]];
    }

    public DataType getDataType() {
        return mType;
    }

    public DataKind getDataKind() {
        return mKind;
    }

    /**
     * Primitive Elements are compatible when they share a size, DataType and
     * vector size, so U8 is compatible with A_8 even though their DataKinds
     * differ. User-defined (struct) Elements must be the SAME object, since
     * compatibility there requires name equivalence all the way down.
     */
    public boolean isCompatible(Element e) {
        if (this.equals(e)) {
            return true;
        }
        if (e == null) {
            return false;
        }
        return ((mSize == e.mSize)
                && (mType != DataType.NONE)
                && (mType == e.mType)
                && (mVectorSize == e.mVectorSize));
    }

    // ---- Builtin Element factories ----------------------------------------    // AOSP caches one instance per builtin per context; the cache is what makes    // Element.U8_4(rs) == Element.U8_4(rs) and therefore what makes    // isCompatible() short-circuit on equals(). The map lives on RenderScript.    /** @return the context's cached builtin under key, or null. */
    private static Element cached(RenderScript rs, String key) {
        if (rs == null) {
            return null;
        }
        synchronized (rs.mElementCache) {
            return rs.mElementCache.get(key);
        }
    }

    /**
     * Publishes a freshly built builtin, returning whichever instance wins the
     * race. Returning the winner rather than the caller's own object is what
     * keeps Element.U8_4(rs) == Element.U8_4(rs) under concurrency, and
     * isCompatible()'s equals() short-circuit depends on that identity.
     */
    private static Element cache(RenderScript rs, String key, Element fresh) {
        if (rs == null) {
            return fresh;
        }
        synchronized (rs.mElementCache) {
            Element e = rs.mElementCache.get(key);
            if (e != null) {
                return e;
            }
            rs.mElementCache.put(key, fresh);
            return fresh;
        }
    }
    public static Element BOOLEAN(RenderScript rs) {
        Element e = cached(rs, "BOOLEAN");
        return e != null ? e : cache(rs, "BOOLEAN", createUser(rs, DataType.BOOLEAN));
    }
    public static Element U8(RenderScript rs) {
        Element e = cached(rs, "U8");
        return e != null ? e : cache(rs, "U8", createUser(rs, DataType.UNSIGNED_8));
    }
    public static Element I8(RenderScript rs) {
        Element e = cached(rs, "I8");
        return e != null ? e : cache(rs, "I8", createUser(rs, DataType.SIGNED_8));
    }
    public static Element U16(RenderScript rs) {
        Element e = cached(rs, "U16");
        return e != null ? e : cache(rs, "U16", createUser(rs, DataType.UNSIGNED_16));
    }
    public static Element I16(RenderScript rs) {
        Element e = cached(rs, "I16");
        return e != null ? e : cache(rs, "I16", createUser(rs, DataType.SIGNED_16));
    }
    public static Element U32(RenderScript rs) {
        Element e = cached(rs, "U32");
        return e != null ? e : cache(rs, "U32", createUser(rs, DataType.UNSIGNED_32));
    }
    public static Element I32(RenderScript rs) {
        Element e = cached(rs, "I32");
        return e != null ? e : cache(rs, "I32", createUser(rs, DataType.SIGNED_32));
    }
    public static Element U64(RenderScript rs) {
        Element e = cached(rs, "U64");
        return e != null ? e : cache(rs, "U64", createUser(rs, DataType.UNSIGNED_64));
    }
    public static Element I64(RenderScript rs) {
        Element e = cached(rs, "I64");
        return e != null ? e : cache(rs, "I64", createUser(rs, DataType.SIGNED_64));
    }
    public static Element F16(RenderScript rs) {
        Element e = cached(rs, "F16");
        return e != null ? e : cache(rs, "F16", createUser(rs, DataType.FLOAT_16));
    }
    public static Element F32(RenderScript rs) {
        Element e = cached(rs, "F32");
        return e != null ? e : cache(rs, "F32", createUser(rs, DataType.FLOAT_32));
    }
    public static Element F64(RenderScript rs) {
        Element e = cached(rs, "F64");
        return e != null ? e : cache(rs, "F64", createUser(rs, DataType.FLOAT_64));
    }
    public static Element ELEMENT(RenderScript rs) {
        Element e = cached(rs, "ELEMENT");
        return e != null ? e : cache(rs, "ELEMENT", createUser(rs, DataType.RS_ELEMENT));
    }
    public static Element TYPE(RenderScript rs) {
        Element e = cached(rs, "TYPE");
        return e != null ? e : cache(rs, "TYPE", createUser(rs, DataType.RS_TYPE));
    }
    public static Element ALLOCATION(RenderScript rs) {
        Element e = cached(rs, "ALLOCATION");
        return e != null ? e : cache(rs, "ALLOCATION", createUser(rs, DataType.RS_ALLOCATION));
    }
    public static Element SAMPLER(RenderScript rs) {
        Element e = cached(rs, "SAMPLER");
        return e != null ? e : cache(rs, "SAMPLER", createUser(rs, DataType.RS_SAMPLER));
    }
    public static Element SCRIPT(RenderScript rs) {
        Element e = cached(rs, "SCRIPT");
        return e != null ? e : cache(rs, "SCRIPT", createUser(rs, DataType.RS_SCRIPT));
    }
    public static Element MESH(RenderScript rs) {
        Element e = cached(rs, "MESH");
        return e != null ? e : cache(rs, "MESH", createUser(rs, DataType.RS_MESH));
    }
    public static Element PROGRAM_FRAGMENT(RenderScript rs) {
        Element e = cached(rs, "PROGRAM_FRAGMENT");
        return e != null ? e : cache(rs, "PROGRAM_FRAGMENT", createUser(rs, DataType.RS_PROGRAM_FRAGMENT));
    }
    public static Element PROGRAM_VERTEX(RenderScript rs) {
        Element e = cached(rs, "PROGRAM_VERTEX");
        return e != null ? e : cache(rs, "PROGRAM_VERTEX", createUser(rs, DataType.RS_PROGRAM_VERTEX));
    }
    public static Element PROGRAM_RASTER(RenderScript rs) {
        Element e = cached(rs, "PROGRAM_RASTER");
        return e != null ? e : cache(rs, "PROGRAM_RASTER", createUser(rs, DataType.RS_PROGRAM_RASTER));
    }
    public static Element PROGRAM_STORE(RenderScript rs) {
        Element e = cached(rs, "PROGRAM_STORE");
        return e != null ? e : cache(rs, "PROGRAM_STORE", createUser(rs, DataType.RS_PROGRAM_STORE));
    }
    public static Element FONT(RenderScript rs) {
        Element e = cached(rs, "FONT");
        return e != null ? e : cache(rs, "FONT", createUser(rs, DataType.RS_FONT));
    }
    public static Element MATRIX_4X4(RenderScript rs) {
        Element e = cached(rs, "MATRIX_4X4");
        return e != null ? e : cache(rs, "MATRIX_4X4", createUser(rs, DataType.MATRIX_4X4));
    }
    public static Element MATRIX_3X3(RenderScript rs) {
        Element e = cached(rs, "MATRIX_3X3");
        return e != null ? e : cache(rs, "MATRIX_3X3", createUser(rs, DataType.MATRIX_3X3));
    }
    public static Element MATRIX_2X2(RenderScript rs) {
        Element e = cached(rs, "MATRIX_2X2");
        return e != null ? e : cache(rs, "MATRIX_2X2", createUser(rs, DataType.MATRIX_2X2));
    }
    /**
     * Misspelling of MATRIX_4X4 that shipped in the public API and can never be
     * removed. AOSP forwards it verbatim.
     */
    public static Element MATRIX4X4(RenderScript rs) {
        return MATRIX_4X4(rs);
    }
    public static Element A_8(RenderScript rs) {
        Element e = cached(rs, "A_8");
        return e != null ? e : cache(rs, "A_8", createPixel(rs, DataType.UNSIGNED_8, DataKind.PIXEL_A));
    }
    public static Element RGB_565(RenderScript rs) {
        Element e = cached(rs, "RGB_565");
        return e != null ? e : cache(rs, "RGB_565", createPixel(rs, DataType.UNSIGNED_5_6_5, DataKind.PIXEL_RGB));
    }
    public static Element RGB_888(RenderScript rs) {
        Element e = cached(rs, "RGB_888");
        return e != null ? e : cache(rs, "RGB_888", createPixel(rs, DataType.UNSIGNED_8, DataKind.PIXEL_RGB));
    }
    public static Element RGBA_5551(RenderScript rs) {
        Element e = cached(rs, "RGBA_5551");
        return e != null ? e : cache(rs, "RGBA_5551", createPixel(rs, DataType.UNSIGNED_5_5_5_1, DataKind.PIXEL_RGBA));
    }
    public static Element RGBA_4444(RenderScript rs) {
        Element e = cached(rs, "RGBA_4444");
        return e != null ? e : cache(rs, "RGBA_4444", createPixel(rs, DataType.UNSIGNED_4_4_4_4, DataKind.PIXEL_RGBA));
    }
    public static Element RGBA_8888(RenderScript rs) {
        Element e = cached(rs, "RGBA_8888");
        return e != null ? e : cache(rs, "RGBA_8888", createPixel(rs, DataType.UNSIGNED_8, DataKind.PIXEL_RGBA));
    }
    public static Element YUV(RenderScript rs) {
        Element e = cached(rs, "YUV");
        return e != null ? e : cache(rs, "YUV", createPixel(rs, DataType.UNSIGNED_8, DataKind.PIXEL_YUV));
    }
    public static Element F16_2(RenderScript rs) {
        Element e = cached(rs, "F16_2");
        return e != null ? e : cache(rs, "F16_2", createVector(rs, DataType.FLOAT_16, 2));
    }
    public static Element F16_3(RenderScript rs) {
        Element e = cached(rs, "F16_3");
        return e != null ? e : cache(rs, "F16_3", createVector(rs, DataType.FLOAT_16, 3));
    }
    public static Element F16_4(RenderScript rs) {
        Element e = cached(rs, "F16_4");
        return e != null ? e : cache(rs, "F16_4", createVector(rs, DataType.FLOAT_16, 4));
    }
    public static Element F32_2(RenderScript rs) {
        Element e = cached(rs, "F32_2");
        return e != null ? e : cache(rs, "F32_2", createVector(rs, DataType.FLOAT_32, 2));
    }
    public static Element F32_3(RenderScript rs) {
        Element e = cached(rs, "F32_3");
        return e != null ? e : cache(rs, "F32_3", createVector(rs, DataType.FLOAT_32, 3));
    }
    public static Element F32_4(RenderScript rs) {
        Element e = cached(rs, "F32_4");
        return e != null ? e : cache(rs, "F32_4", createVector(rs, DataType.FLOAT_32, 4));
    }
    public static Element F64_2(RenderScript rs) {
        Element e = cached(rs, "F64_2");
        return e != null ? e : cache(rs, "F64_2", createVector(rs, DataType.FLOAT_64, 2));
    }
    public static Element F64_3(RenderScript rs) {
        Element e = cached(rs, "F64_3");
        return e != null ? e : cache(rs, "F64_3", createVector(rs, DataType.FLOAT_64, 3));
    }
    public static Element F64_4(RenderScript rs) {
        Element e = cached(rs, "F64_4");
        return e != null ? e : cache(rs, "F64_4", createVector(rs, DataType.FLOAT_64, 4));
    }
    public static Element U8_2(RenderScript rs) {
        Element e = cached(rs, "U8_2");
        return e != null ? e : cache(rs, "U8_2", createVector(rs, DataType.UNSIGNED_8, 2));
    }
    public static Element U8_3(RenderScript rs) {
        Element e = cached(rs, "U8_3");
        return e != null ? e : cache(rs, "U8_3", createVector(rs, DataType.UNSIGNED_8, 3));
    }
    public static Element U8_4(RenderScript rs) {
        Element e = cached(rs, "U8_4");
        return e != null ? e : cache(rs, "U8_4", createVector(rs, DataType.UNSIGNED_8, 4));
    }
    public static Element I8_2(RenderScript rs) {
        Element e = cached(rs, "I8_2");
        return e != null ? e : cache(rs, "I8_2", createVector(rs, DataType.SIGNED_8, 2));
    }
    public static Element I8_3(RenderScript rs) {
        Element e = cached(rs, "I8_3");
        return e != null ? e : cache(rs, "I8_3", createVector(rs, DataType.SIGNED_8, 3));
    }
    public static Element I8_4(RenderScript rs) {
        Element e = cached(rs, "I8_4");
        return e != null ? e : cache(rs, "I8_4", createVector(rs, DataType.SIGNED_8, 4));
    }
    public static Element U16_2(RenderScript rs) {
        Element e = cached(rs, "U16_2");
        return e != null ? e : cache(rs, "U16_2", createVector(rs, DataType.UNSIGNED_16, 2));
    }
    public static Element U16_3(RenderScript rs) {
        Element e = cached(rs, "U16_3");
        return e != null ? e : cache(rs, "U16_3", createVector(rs, DataType.UNSIGNED_16, 3));
    }
    public static Element U16_4(RenderScript rs) {
        Element e = cached(rs, "U16_4");
        return e != null ? e : cache(rs, "U16_4", createVector(rs, DataType.UNSIGNED_16, 4));
    }
    public static Element I16_2(RenderScript rs) {
        Element e = cached(rs, "I16_2");
        return e != null ? e : cache(rs, "I16_2", createVector(rs, DataType.SIGNED_16, 2));
    }
    public static Element I16_3(RenderScript rs) {
        Element e = cached(rs, "I16_3");
        return e != null ? e : cache(rs, "I16_3", createVector(rs, DataType.SIGNED_16, 3));
    }
    public static Element I16_4(RenderScript rs) {
        Element e = cached(rs, "I16_4");
        return e != null ? e : cache(rs, "I16_4", createVector(rs, DataType.SIGNED_16, 4));
    }
    public static Element U32_2(RenderScript rs) {
        Element e = cached(rs, "U32_2");
        return e != null ? e : cache(rs, "U32_2", createVector(rs, DataType.UNSIGNED_32, 2));
    }
    public static Element U32_3(RenderScript rs) {
        Element e = cached(rs, "U32_3");
        return e != null ? e : cache(rs, "U32_3", createVector(rs, DataType.UNSIGNED_32, 3));
    }
    public static Element U32_4(RenderScript rs) {
        Element e = cached(rs, "U32_4");
        return e != null ? e : cache(rs, "U32_4", createVector(rs, DataType.UNSIGNED_32, 4));
    }
    public static Element I32_2(RenderScript rs) {
        Element e = cached(rs, "I32_2");
        return e != null ? e : cache(rs, "I32_2", createVector(rs, DataType.SIGNED_32, 2));
    }
    public static Element I32_3(RenderScript rs) {
        Element e = cached(rs, "I32_3");
        return e != null ? e : cache(rs, "I32_3", createVector(rs, DataType.SIGNED_32, 3));
    }
    public static Element I32_4(RenderScript rs) {
        Element e = cached(rs, "I32_4");
        return e != null ? e : cache(rs, "I32_4", createVector(rs, DataType.SIGNED_32, 4));
    }
    public static Element U64_2(RenderScript rs) {
        Element e = cached(rs, "U64_2");
        return e != null ? e : cache(rs, "U64_2", createVector(rs, DataType.UNSIGNED_64, 2));
    }
    public static Element U64_3(RenderScript rs) {
        Element e = cached(rs, "U64_3");
        return e != null ? e : cache(rs, "U64_3", createVector(rs, DataType.UNSIGNED_64, 3));
    }
    public static Element U64_4(RenderScript rs) {
        Element e = cached(rs, "U64_4");
        return e != null ? e : cache(rs, "U64_4", createVector(rs, DataType.UNSIGNED_64, 4));
    }
    public static Element I64_2(RenderScript rs) {
        Element e = cached(rs, "I64_2");
        return e != null ? e : cache(rs, "I64_2", createVector(rs, DataType.SIGNED_64, 2));
    }
    public static Element I64_3(RenderScript rs) {
        Element e = cached(rs, "I64_3");
        return e != null ? e : cache(rs, "I64_3", createVector(rs, DataType.SIGNED_64, 3));
    }
    public static Element I64_4(RenderScript rs) {
        Element e = cached(rs, "I64_4");
        return e != null ? e : cache(rs, "I64_4", createVector(rs, DataType.SIGNED_64, 4));
    }

    /**
     * A basic Element with DataKind USER and vector size 1.
     *
     * AOSP keeps this out of the public API but the support library and a lot
     * of copied sample code call it, so it stays public here as it is in the
     * platform's own hidden surface.
     */
    public static Element createUser(RenderScript rs, DataType dt) {
        return new Element(rs, dt, DataKind.USER, false, 1);
    }

    /**
     * A vector Element (float2, uchar4, ...). Only primitive numeric and
     * boolean DataTypes may be vectorised; matrices and driver handles may not.
     */
    public static Element createVector(RenderScript rs, DataType dt, int size) {
        if (size < 2 || size > 4) {
            throw new RSIllegalArgumentException("Vector size out of range 2-4.");
        }

        switch (dt) {
            case FLOAT_16:
            case FLOAT_32:
            case FLOAT_64:
            case SIGNED_8:
            case SIGNED_16:
            case SIGNED_32:
            case SIGNED_64:
            case UNSIGNED_8:
            case UNSIGNED_16:
            case UNSIGNED_32:
            case UNSIGNED_64:
            case BOOLEAN:
                return new Element(rs, dt, DataKind.USER, false, size);
            default:
                throw new RSIllegalArgumentException(
                        "Cannot create vector of non-primitive type.");
        }
    }

    /**
     * A pixel Element. The DataType and DataKind must agree on the channel
     * count, and the packed formats are locked to one DataKind each. Vector
     * size is set from the KIND, not passed in, and normalized is true.
     */
    public static Element createPixel(RenderScript rs, DataType dt, DataKind dk) {
        if (!(dk == DataKind.PIXEL_L
                || dk == DataKind.PIXEL_A
                || dk == DataKind.PIXEL_LA
                || dk == DataKind.PIXEL_RGB
                || dk == DataKind.PIXEL_RGBA
                || dk == DataKind.PIXEL_DEPTH
                || dk == DataKind.PIXEL_YUV)) {
            throw new RSIllegalArgumentException("Unsupported DataKind");
        }
        if (!(dt == DataType.UNSIGNED_8
                || dt == DataType.UNSIGNED_16
                || dt == DataType.UNSIGNED_5_6_5
                || dt == DataType.UNSIGNED_4_4_4_4
                || dt == DataType.UNSIGNED_5_5_5_1)) {
            throw new RSIllegalArgumentException("Unsupported DataType");
        }
        if (dt == DataType.UNSIGNED_5_6_5 && dk != DataKind.PIXEL_RGB) {
            throw new RSIllegalArgumentException("Bad kind and type combo");
        }
        if (dt == DataType.UNSIGNED_5_5_5_1 && dk != DataKind.PIXEL_RGBA) {
            throw new RSIllegalArgumentException("Bad kind and type combo");
        }
        if (dt == DataType.UNSIGNED_4_4_4_4 && dk != DataKind.PIXEL_RGBA) {
            throw new RSIllegalArgumentException("Bad kind and type combo");
        }
        if (dt == DataType.UNSIGNED_16 && dk != DataKind.PIXEL_DEPTH) {
            throw new RSIllegalArgumentException("Bad kind and type combo");
        }

        int size = 1;
        switch (dk) {
            case PIXEL_LA:
                size = 2;
                break;
            case PIXEL_RGB:
                size = 3;
                break;
            case PIXEL_RGBA:
                size = 4;
                break;
            case PIXEL_DEPTH:
                size = 2;
                break;
            default:
                break;
        }

        return new Element(rs, dt, dk, true, size);
    }

    /**
     * Builds a complex (struct) Element from field/name pairs. Order is
     * retained and determines the memory layout.
     *
     * The "#rs_padding" skip logic is AOSP's: a script's reflected struct
     * inserts explicit padding fields, and adding a real field after one means
     * the padding was wrong and must be dropped.
     */
    public static class Builder {

        RenderScript mRS;
        Element[] mElements;
        String[] mElementNames;
        int[] mArraySizes;
        int mCount;
        int mSkipPadding;

        public Builder(RenderScript rs) {
            if (rs != null) {
                rs.validate();
            }
            mRS = rs;
            mCount = 0;
            mElements = new Element[8];
            mElementNames = new String[8];
            mArraySizes = new int[8];
        }

        /**
         * Adds an array of an Element to this Element.
         *
         * @param element the sub-element to add
         * @param name    the field name, must be unique within this Element
         * @param arraySize the number of copies, must be >= 1
         */
        public Builder add(Element element, String name, int arraySize) {
            if (arraySize < 1) {
                throw new RSIllegalArgumentException("Array size cannot be less than 1.");
            }

            // Skip padding fields after a padding field has been seen, matching
            // the reflected-struct layout a script would produce.
            if (name.startsWith("#padding_")) {
                if (mSkipPadding == 1) {
                    mSkipPadding = 0;
                    return this;
                }
            }

            if (element.mVectorSize == 3) {
                mSkipPadding = 1;
            } else {
                mSkipPadding = 0;
            }

            if (mCount == mElements.length) {
                Element[] e = new Element[mCount + 8];
                String[] s = new String[mCount + 8];
                int[] as = new int[mCount + 8];
                System.arraycopy(mElements, 0, e, 0, mCount);
                System.arraycopy(mElementNames, 0, s, 0, mCount);
                System.arraycopy(mArraySizes, 0, as, 0, mCount);
                mElements = e;
                mElementNames = s;
                mArraySizes = as;
            }
            mElements[mCount] = element;
            mElementNames[mCount] = name;
            mArraySizes[mCount] = arraySize;
            mCount++;
            return this;
        }

        public Builder add(Element element, String name) {
            return add(element, name, 1);
        }

        public Element create() {
            if (mRS != null) {
                mRS.validate();
            }
            Element[] ein = new Element[mCount];
            String[] sin = new String[mCount];
            int[] asin = new int[mCount];
            System.arraycopy(mElements, 0, ein, 0, mCount);
            System.arraycopy(mElementNames, 0, sin, 0, mCount);
            System.arraycopy(mArraySizes, 0, asin, 0, mCount);
            return new Element(mRS, ein, sin, asin);
        }
    }
}
