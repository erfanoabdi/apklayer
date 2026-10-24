package android.view;

import android.graphics.Point;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Describes one rounded corner of the display so an app can inset content that
 * would otherwise be clipped by the physical corner radius.
 *
 * A Mac.apk window is a rectangular NSWindow with square content corners, so
 * the honest answer for every position is radius 0 with a center at the
 * corresponding window corner -- which is exactly what AOSP reports on a device
 * with no rounded corners. Apps use getRadius() == 0 to mean "nothing to inset",
 * so this degrades correctly rather than needing a fake radius.
 *
 * Source: frameworks/base/core/java/android/view/RoundedCorner.java
 * https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/core/java/android/view/RoundedCorner.java
 */
public final class RoundedCorner implements Parcelable {

    public static final int POSITION_TOP_LEFT = 0;
    public static final int POSITION_TOP_RIGHT = 1;
    public static final int POSITION_BOTTOM_RIGHT = 2;
    public static final int POSITION_BOTTOM_LEFT = 3;

    private final int mPosition;
    private final int mRadius;
    private final Point mCenter;

    /** AOSP: an empty corner at this position (radius 0, center at origin). */
    public RoundedCorner(int position) {
        mPosition = position;
        mRadius = 0;
        mCenter = new Point(0, 0);
    }

    public RoundedCorner(int position, int radius, int centerX, int centerY) {
        mPosition = position;
        mRadius = radius;
        mCenter = new Point(centerX, centerY);
    }

    public RoundedCorner(RoundedCorner other) {
        mPosition = other.mPosition;
        mRadius = other.mRadius;
        mCenter = new Point(other.mCenter);
    }

    public int getPosition() { return mPosition; }

    public int getRadius() { return mRadius; }

    /** AOSP returns a copy so a caller cannot mutate the corner in place. */
    public Point getCenter() { return new Point(mCenter); }

    public boolean isEmpty() {
        return mRadius == 0 || mCenter.x <= 0 || mCenter.y <= 0;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RoundedCorner)) return false;
        RoundedCorner c = (RoundedCorner) o;
        return mPosition == c.mPosition && mRadius == c.mRadius
                && mCenter.x == c.mCenter.x && mCenter.y == c.mCenter.y;
    }

    @Override public int hashCode() {
        int r = mPosition;
        r = 31 * r + mRadius;
        r = 31 * r + mCenter.x;
        r = 31 * r + mCenter.y;
        return r;
    }

    @Override public String toString() {
        return "RoundedCorner{position=" + positionToString(mPosition)
                + ", radius=" + mRadius
                + ", center=" + mCenter.x + "," + mCenter.y + "}";
    }

    private static String positionToString(int position) {
        switch (position) {
            case POSITION_TOP_LEFT:     return "TopLeft";
            case POSITION_TOP_RIGHT:    return "TopRight";
            case POSITION_BOTTOM_RIGHT: return "BottomRight";
            case POSITION_BOTTOM_LEFT:  return "BottomLeft";
            default:                    return "Unknown(" + position + ")";
        }
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mPosition);
        out.writeInt(mRadius);
        out.writeInt(mCenter.x);
        out.writeInt(mCenter.y);
    }

    public static final Creator<RoundedCorner> CREATOR = new Creator<RoundedCorner>() {
        @Override public RoundedCorner createFromParcel(Parcel in) {
            return new RoundedCorner(in.readInt(), in.readInt(), in.readInt(), in.readInt());
        }
        @Override public RoundedCorner[] newArray(int size) {
            return new RoundedCorner[size];
        }
    };
}
