package android.renderscript;

/**
 * A Type is the dimensions plus the {@link Element} layout of an
 * {@link Allocation}: how many cells there are, and what one cell looks like.
 *
 * A Type is immutable once created; that is not an optimisation but a contract
 * apps rely on when they cache a Type and hand it to createTyped repeatedly.
 *
 * The dimension convention is the platform's and reads oddly at first: an unset
 * dimension is ZERO, not one. A 1D Type has y == 0 and z == 0. Code that treats
 * getY() as "at least one" has to say Math.max(1, getY()) itself, and Allocation
 * does exactly that.
 *
 * Source: frameworks/rs/java/android/renderscript/Type.java
 */
public class Type extends BaseObj {

    int mDimX;
    int mDimY;
    int mDimZ;
    boolean mDimMipmaps;
    boolean mDimFaces;
    int mDimYuv;
    int mElementCount;
    Element mElement;

    /**
     * The six faces of a cubemap Allocation.
     *
     * POSITVE_X / POSITVE_Y / POSITVE_Z are misspellings that shipped in the
     * public API and can never be removed; AOSP keeps both spellings, and an
     * app that names either must find it.
     */
    public enum CubemapFace {
        POSITVE_X(0),
        NEGATIVE_X(1),
        POSITVE_Y(2),
        NEGATIVE_Y(3),
        POSITVE_Z(4),
        NEGATIVE_Z(5),
        POSITIVE_X(0),
        POSITIVE_Y(2),
        POSITIVE_Z(4);

        int mID;

        CubemapFace(int id) {
            mID = id;
        }
    }

    Type(RenderScript rs) {
        super(rs);
    }

    /** @return the X dimension, always >= 1 for a valid Type. */
    public int getX() {
        return mDimX;
    }

    /** @return the Y dimension, or 0 for a 1D Type. */
    public int getY() {
        return mDimY;
    }

    /** @return the Z dimension, or 0 for a Type with fewer than 3 dimensions. */
    public int getZ() {
        return mDimZ;
    }

    /**
     * @return the ImageFormat constant this Type describes, or 0 for a
     *         non-YUV Type.
     */
    public int getYuv() {
        return mDimYuv;
    }

    public boolean hasMipmaps() {
        return mDimMipmaps;
    }

    public boolean hasFaces() {
        return mDimFaces;
    }

    /** @return total cell count, INCLUDING every mipmap level and every face. */
    public int getCount() {
        return mElementCount;
    }

    public Element getElement() {
        return mElement;
    }

    /**
     * Total cells over all faces and all mipmap levels. Halving each dimension
     * per level (floor, minimum 1) is the platform's mipmap chain rule; the
     * loop stops when every dimension has reached 1.
     */
    void calcElementCount() {
        boolean hasLod = hasMipmaps();
        int x = getX();
        int y = getY();
        int z = getZ();
        int faces = 1;
        if (hasFaces()) {
            faces = 6;
        }
        if (x == 0) {
            x = 1;
        }
        if (y == 0) {
            y = 1;
        }
        if (z == 0) {
            z = 1;
        }

        int count = x * y * z * faces;

        while (hasLod && ((x > 1) || (y > 1) || (z > 1))) {
            if (x > 1) {
                x >>= 1;
            }
            if (y > 1) {
                y >>= 1;
            }
            if (z > 1) {
                z >>= 1;
            }
            count += x * y * z * faces;
        }
        mElementCount = count;
    }

    /** Basic 1D Type, no mipmaps and no cubemap. */
    public static Type createX(RenderScript rs, Element e, int dimX) {
        if (dimX < 1) {
            throw new RSInvalidStateException("Dimension must be >= 1.");
        }
        Type t = new Type(rs);
        t.mElement = e;
        t.mDimX = dimX;
        t.calcElementCount();
        return t;
    }

    /** Basic 2D Type, no mipmaps and no cubemap. */
    public static Type createXY(RenderScript rs, Element e, int dimX, int dimY) {
        if ((dimX < 1) || (dimY < 1)) {
            throw new RSInvalidStateException("Dimension must be >= 1.");
        }
        Type t = new Type(rs);
        t.mElement = e;
        t.mDimX = dimX;
        t.mDimY = dimY;
        t.calcElementCount();
        return t;
    }

    /** Basic 3D Type, no mipmaps. */
    public static Type createXYZ(RenderScript rs, Element e, int dimX, int dimY, int dimZ) {
        if ((dimX < 1) || (dimY < 1) || (dimZ < 1)) {
            throw new RSInvalidStateException("Dimension must be >= 1.");
        }
        Type t = new Type(rs);
        t.mElement = e;
        t.mDimX = dimX;
        t.mDimY = dimY;
        t.mDimZ = dimZ;
        t.calcElementCount();
        return t;
    }

    /**
     * Builder for Types with mipmaps, cubemap faces or a YUV format.
     *
     * The create() checks are AOSP's and are all reachable from app code: a Z
     * dimension without X and Y, a cubemap that is not 2D, a cubemap combined
     * with 3D, and YUV combined with anything but plain 2D.
     */
    public static class Builder {

        RenderScript mRS;
        int mDimX = 1;
        int mDimY;
        int mDimZ;
        boolean mDimMipmaps;
        boolean mDimFaces;
        int mYuv;
        Element mElement;

        public Builder(RenderScript rs, Element e) {
            if (e != null) {
                e.checkValid();
            }
            if (rs != null) {
                rs.validate();
            }
            mRS = rs;
            mElement = e;
        }

        public Builder setX(int value) {
            if (value < 1) {
                throw new RSIllegalArgumentException(
                        "Values of less than 1 for Dimension X are not valid.");
            }
            mDimX = value;
            return this;
        }

        public Builder setY(int value) {
            if (value < 1) {
                throw new RSIllegalArgumentException(
                        "Values of less than 1 for Dimension Y are not valid.");
            }
            mDimY = value;
            return this;
        }

        public Builder setZ(int value) {
            if (value < 1) {
                throw new RSIllegalArgumentException(
                        "Values of less than 1 for Dimension Z are not valid.");
            }
            mDimZ = value;
            return this;
        }

        public Builder setMipmaps(boolean value) {
            mDimMipmaps = value;
            return this;
        }

        public Builder setFaces(boolean value) {
            mDimFaces = value;
            return this;
        }

        /**
         * @param yuvFormat must be one of ImageFormat.NV21, .YV12 or
         *                  .YUV_420_888.
         */
        public Builder setYuvFormat(int yuvFormat) {
            switch (yuvFormat) {
                case android.graphics.ImageFormat.NV21:
                case android.graphics.ImageFormat.YV12:
                case android.graphics.ImageFormat.YUV_420_888:
                    break;
                default:
                    throw new RSIllegalArgumentException(
                            "Only ImageFormat.NV21, .YV12, and .YUV_420_888 are supported..");
            }

            mYuv = yuvFormat;
            return this;
        }

        public Type create() {
            if (mDimZ > 0) {
                if ((mDimX < 1) || (mDimY < 1)) {
                    throw new RSInvalidStateException(
                            "Both X and Y dimension required when Z is present.");
                }
                if (mDimFaces) {
                    throw new RSInvalidStateException("Cube maps not supported with 3D types.");
                }
            }
            if (mDimY > 0) {
                if (mDimX < 1) {
                    throw new RSInvalidStateException("X dimension required when Y is present.");
                }
            }
            if (mDimFaces) {
                if (mDimY < 1) {
                    throw new RSInvalidStateException("Cube maps require 2D Types.");
                }
            }
            if (mYuv != 0) {
                if ((mDimZ != 0) || mDimFaces || mDimMipmaps) {
                    throw new RSInvalidStateException("YUV only supports basic 2D.");
                }
            }

            Type t = new Type(mRS);
            t.mElement = mElement;
            t.mDimX = mDimX;
            t.mDimY = mDimY;
            t.mDimZ = mDimZ;
            t.mDimMipmaps = mDimMipmaps;
            t.mDimFaces = mDimFaces;
            t.mDimYuv = mYuv;

            t.calcElementCount();
            return t;
        }
    }
}
