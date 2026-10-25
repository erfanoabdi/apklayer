package android.util;

import android.os.Parcel;
import android.os.Parcelable;

/** An immutable pair of float dimensions (width, height). Real value class.
 *  Source: frameworks/base/core/java/android/util/SizeF.java
 *
 *  Parcelable since API 21. The wire form is two floats, width first -- AOSP
 *  writes them with writeFloat/writeFloat and reads them back through the
 *  PUBLIC constructor, so a parcelled SizeF is re-validated (a NaN that
 *  somehow reached the parcel throws on read rather than propagating). */
public final class SizeF implements Parcelable {
    private final float mWidth;
    private final float mHeight;
    public SizeF(float width, float height) {
        mWidth = checkArgumentFinite(width, "width");
        mHeight = checkArgumentFinite(height, "height");
    }
    /** AOSP's ctor calls Preconditions.checkArgumentFinite, which rejects
     *  INFINITY as well as NaN. Rejecting only NaN let an infinite dimension
     *  through, and it then propagates silently through every layout
     *  computation instead of failing where it was created. */
    private static float checkArgumentFinite(float v, String name) {
        if (Float.isNaN(v)) throw new IllegalArgumentException(name + " must not be NaN");
        if (Float.isInfinite(v)) throw new IllegalArgumentException(name + " must be finite");
        return v;
    }
    public float getWidth()  { return mWidth; }
    public float getHeight() { return mHeight; }
    @Override public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj instanceof SizeF) {
            SizeF other = (SizeF) obj;
            return mWidth == other.mWidth && mHeight == other.mHeight;
        }
        return false;
    }
    @Override public String toString() { return mWidth + "x" + mHeight; }
    @Override public int hashCode() {
        return Float.floatToIntBits(mWidth) ^ Float.floatToIntBits(mHeight);
    }
    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeFloat(mWidth);
        out.writeFloat(mHeight);
    }

    public static final Parcelable.Creator<SizeF> CREATOR = new Parcelable.Creator<SizeF>() {
        @Override public SizeF createFromParcel(Parcel in) {
            float width = in.readFloat();
            float height = in.readFloat();
            return new SizeF(width, height);
        }
        @Override public SizeF[] newArray(int size) { return new SizeF[size]; }
    };

    public static SizeF parseSizeF(String string) throws NumberFormatException {
        // AOSP opens with Preconditions.checkNotNull, so a null string is a
        // NullPointerException, NOT the NumberFormatException the method
        // declares. Callers distinguish the two: a NumberFormatException means
        // "the user typed something odd", an NPE means "you passed me nothing".
        if (string == null) throw new NullPointerException("string must not be null");
        int sep = string.indexOf('*');
        if (sep < 0) sep = string.indexOf('x');
        if (sep < 0) throw invalidSizeF(string);
        try {
            return new SizeF(Float.parseFloat(string.substring(0, sep)),
                             Float.parseFloat(string.substring(sep + 1)));
        } catch (NumberFormatException e) {
            throw invalidSizeF(string);
        } catch (IllegalArgumentException e) {
            // A NaN or infinite component: AOSP funnels the ctor's
            // IllegalArgumentException into the same NumberFormatException.
            throw invalidSizeF(string);
        }
    }

    private static NumberFormatException invalidSizeF(String s) {
        throw new NumberFormatException("Invalid SizeF: \"" + s + "\"");
    }
}
