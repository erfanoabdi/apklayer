package android.view;
import android.graphics.Path;
/** The physical outline of the display (rounded corners / notches) as a Path.
 *  Mac.apk targets rectangular windows, so the shape is an empty (rectangular)
 *  path -- getPath() is unchanged from the pre-Parcelable stub. The 7 scalar
 *  fields below exist only so instances round-trip through a Parcel the way
 *  AOSP's do (an app that stores a DisplayShape in saved state and reads it
 *  back should get the same NONE-shaped values, not null). AOSP additionally
 *  parses mDisplayShapeSpec (an SVG-path-like string) into the real Path via
 *  PathParser + a static cache (DisplayShape.java's inner Cache class); we do
 *  not carry that parser, so getPath() honestly returns empty rather than
 *  faking a parsed shape.
 *  Source: frameworks/base/core/java/android/view/DisplayShape.java */
public final class DisplayShape implements android.os.Parcelable {
    public static final DisplayShape NONE =
            new DisplayShape("", 0, 0, 0f, 0, 0, 0, 1f);

    public final String mDisplayShapeSpec;
    private final float mPhysicalPixelDisplaySizeRatio;
    private final int mDisplayWidth;
    private final int mDisplayHeight;
    private final int mRotation;
    private final int mOffsetX;
    private final int mOffsetY;
    private final float mScale;

    private DisplayShape(String spec, int displayWidth, int displayHeight,
            float physicalPixelDisplaySizeRatio, int rotation, int offsetX, int offsetY,
            float scale) {
        mDisplayShapeSpec = spec;
        mDisplayWidth = displayWidth;
        mDisplayHeight = displayHeight;
        mPhysicalPixelDisplaySizeRatio = physicalPixelDisplaySizeRatio;
        mRotation = rotation;
        mOffsetX = offsetX;
        mOffsetY = offsetY;
        mScale = scale;
    }

    public Path getPath() { return new Path(); }

    @Override public int describeContents() { return 0; }

    // AOSP field order (DisplayShape.java writeToParcel): spec, width, height,
    // ratio, rotation, offsetX, offsetY, scale. Uses writeString8/readString8;
    // our Parcel has one string encoding, so writeString/readString is the
    // same operation.
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeString(mDisplayShapeSpec);
        dest.writeInt(mDisplayWidth);
        dest.writeInt(mDisplayHeight);
        dest.writeFloat(mPhysicalPixelDisplaySizeRatio);
        dest.writeInt(mRotation);
        dest.writeInt(mOffsetX);
        dest.writeInt(mOffsetY);
        dest.writeFloat(mScale);
    }

    public static final android.os.Parcelable.Creator<DisplayShape> CREATOR =
            new android.os.Parcelable.Creator<DisplayShape>() {
        @Override public DisplayShape createFromParcel(android.os.Parcel in) {
            String spec = in.readString();
            int displayWidth = in.readInt();
            int displayHeight = in.readInt();
            float ratio = in.readFloat();
            int rotation = in.readInt();
            int offsetX = in.readInt();
            int offsetY = in.readInt();
            float scale = in.readFloat();
            return new DisplayShape(spec, displayWidth, displayHeight, ratio, rotation,
                    offsetX, offsetY, scale);
        }
        @Override public DisplayShape[] newArray(int size) { return new DisplayShape[size]; }
    };
}
