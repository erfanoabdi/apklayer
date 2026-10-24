// android.util.Size -- immutable width x height. AOSP: a final value type with
// getWidth/getHeight + equals/hashCode/toString + parseSize. Was a no-arg stub
// -> Compose (layout size math, ImageBitmap/Painter intrinsic sizing,
// LocalDensity helpers) and CameraX/Media constructing Size(w,h) NoSuchMethod-
// Error'd, aborting onCreate. v1.0.422.
package android.util;

public final class Size {
    private final int mWidth;
    private final int mHeight;

    public Size(int width, int height) {
        mWidth = width;
        mHeight = height;
    }

    public int getWidth()  { return mWidth; }
    public int getHeight() { return mHeight; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Size)) return false;
        Size s = (Size) o;
        return mWidth == s.mWidth && mHeight == s.mHeight;
    }

    @Override public int hashCode() {
        // AOSP: assuming most sizes are < 2^16, hashCode is roughly unique.
        return mHeight ^ ((mWidth << (Integer.SIZE / 2)) | (mWidth >>> (Integer.SIZE / 2)));
    }

    @Override public String toString() { return mWidth + "x" + mHeight; }

    public static Size parseSize(String string) {
        if (string == null) throw new NullPointerException("string must not be null");
        int sep = string.indexOf('*');
        if (sep < 0) sep = string.indexOf('x');
        if (sep < 0) throw new NumberFormatException("Invalid Size: \"" + string + "\"");
        return new Size(Integer.parseInt(string.substring(0, sep)),
                        Integer.parseInt(string.substring(sep + 1)));
    }
}
