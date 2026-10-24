package android.graphics;

// android.graphics.Point -- pair of ints.
//
// AOSP declares `public class Point implements Parcelable` (Point.java:29).
// That is load-bearing beyond the parcel itself: an app class compiled against
// the real android.jar that stores a Point through a Parcelable-typed formal
// param (Bundle.putParcelable, Intent.putExtra, a custom Parcelable with a
// Point field) verifies the stack type against android.os.Parcelable at CLASS
// LOAD, so a Point that is not a Parcelable fails the WHOLE containing class
// with VerifyError, not just the call site. Same reasoning as Rect.java's
// existing note on the identical gap.
public class Point implements android.os.Parcelable {
    public int x, y;
    public Point() {}
    public Point(int x, int y) { this.x = x; this.y = y; }
    public Point(Point o) { if (o != null) { x = o.x; y = o.y; } }
    public void set(int x, int y) { this.x = x; this.y = y; }
    public final boolean equals(int x, int y) { return this.x == x && this.y == y; }
    @Override public String toString() { return "Point(" + x + ", " + y + ")"; }

    // AOSP defines Point as a VALUE type with equals/hashCode. Without them the
    // inherited identity comparison makes two identical Points unequal, so any
    // `if (bounds.equals(newBounds)) return;` early-out silently never fires
    // and a Point used as a map key or in a Set behaves wrongly.
    // Source: frameworks/base/graphics/java/android/graphics/Point.java
    @Override public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Point)) return false;
        Point o = (Point) obj;
        return x == o.x && y == o.y;
    }

    @Override public int hashCode() {
        int r = x;
        r = 31 * r + y;
        return r;
    }

    /** AOSP Point.offset (API 1): translate this point by (dx, dy). */
    public void offset(int dx, int dy) { x += dx; y += dy; }
    /** AOSP Point.negate (API 1). */
    public final void negate() { x = -x; y = -y; }

    // Parcelable. Mirrors frameworks/base/.../Point.java exactly: 2 ints, x then
    // y, describeContents() -> 0.
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel out, int flags) {
        out.writeInt(x); out.writeInt(y);
    }
    public void readFromParcel(android.os.Parcel in) {
        x = in.readInt(); y = in.readInt();
    }
    public static final android.os.Parcelable.Creator<Point> CREATOR =
        new android.os.Parcelable.Creator<Point>() {
            @Override public Point createFromParcel(android.os.Parcel in) {
                Point p = new Point();
                p.readFromParcel(in);
                return p;
            }
            @Override public Point[] newArray(int size) { return new Point[size]; }
        };
}
