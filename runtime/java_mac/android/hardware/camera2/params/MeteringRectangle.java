package android.hardware.camera2.params;

import android.graphics.Rect;
import android.util.Size;

/**
 * A weighted 3A (auto-exposure/focus/white-balance) metering region.
 * Source: frameworks/base/core/java/android/hardware/camera2/params/MeteringRectangle.java
 */
public final class MeteringRectangle {

    public static final int METERING_WEIGHT_MIN = 0;
    public static final int METERING_WEIGHT_MAX = 1000;
    public static final int METERING_WEIGHT_DONT_CARE = 0;

    private final int mX, mY, mWidth, mHeight, mWeight;

    public MeteringRectangle(int x, int y, int width, int height, int meteringWeight) {
        mX = x; mY = y; mWidth = width; mHeight = height; mWeight = meteringWeight;
    }
    public MeteringRectangle(android.graphics.Point xy, Size dimensions, int meteringWeight) {
        this(xy.x, xy.y, dimensions.getWidth(), dimensions.getHeight(), meteringWeight);
    }
    public MeteringRectangle(Rect rect, int meteringWeight) {
        this(rect.left, rect.top, rect.width(), rect.height(), meteringWeight);
    }

    public int getX() { return mX; }
    public int getY() { return mY; }
    public int getWidth() { return mWidth; }
    public int getHeight() { return mHeight; }
    public int getMeteringWeight() { return mWeight; }
    public Rect getRect() { return new Rect(mX, mY, mX + mWidth, mY + mHeight); }

    /** AOSP MeteringRectangle.java getUpperLeftPoint(): "new Point(mX, mY)" --
     *  the rectangle's top-left corner, in the same coordinate system as
     *  SENSOR_INFO_ACTIVE_ARRAY_SIZE. */
    public android.graphics.Point getUpperLeftPoint() {
        return new android.graphics.Point(mX, mY);
    }

    /** AOSP getSize(): "new Size(mWidth, mHeight)". */
    public Size getSize() { return new Size(mWidth, mHeight); }

    /** AOSP declares a type-specific overload alongside equals(Object), so an
     *  app calling it on a statically-typed MeteringRectangle binds to this one.
     *  Same comparison, including the metering weight. */
    public boolean equals(MeteringRectangle other) {
        return other != null && mX == other.mX && mY == other.mY && mWidth == other.mWidth
                && mHeight == other.mHeight && mWeight == other.mWeight;
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof MeteringRectangle)) return false;
        MeteringRectangle m = (MeteringRectangle) o;
        return mX == m.mX && mY == m.mY && mWidth == m.mWidth
                && mHeight == m.mHeight && mWeight == m.mWeight;
    }
    @Override public int hashCode() {
        int h = mX; h = 31*h+mY; h = 31*h+mWidth; h = 31*h+mHeight; h = 31*h+mWeight; return h;
    }
}
