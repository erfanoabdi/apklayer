// android.graphics.PointF -- pair of floats. Used by Material widgets,
// touch handling, animation. AOSP shape, no behavior beyond storage.
package android.graphics;

// AOSP declares `public class PointF implements Parcelable` (PointF.java:29):
// see Point.java's note for why the `implements` is a class-load-time issue,
// not merely a missing parcel path.
public class PointF implements android.os.Parcelable {
    public float x, y;
    public PointF()                 {}
    public PointF(float x, float y) { this.x = x; this.y = y; }
    public PointF(Point p)          { this.x = p.x; this.y = p.y; }
    /** AOSP PointF(PointF) copy constructor (PointF.java:44-47). Absent here,
     *  so `new PointF(other)` was a NoSuchMethodError. */
    public PointF(PointF p)         { this.x = p.x; this.y = p.y; }
    public final void set(float x, float y) { this.x = x; this.y = y; }
    public final void set(PointF p) { this.x = p.x; this.y = p.y; }
    public final void offset(float dx, float dy) { x += dx; y += dy; }
    public final void negate() { x = -x; y = -y; }
    public final boolean equals(float x, float y) { return this.x == x && this.y == y; }
    public final float length() { return (float) Math.hypot(x, y); }
    public static float length(float x, float y) { return (float) Math.hypot(x, y); }
    @Override public String toString() { return "PointF(" + x + ", " + y + ")"; }

    // AOSP defines PointF as a VALUE type with equals/hashCode. Without them the
    // inherited identity comparison makes two identical PointFs unequal, so any
    // `if (bounds.equals(newBounds)) return;` early-out silently never fires
    // and a PointF used as a map key or in a Set behaves wrongly.
    // Source: frameworks/base/graphics/java/android/graphics/PointF.java
    @Override public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof PointF)) return false;
        PointF o = (PointF) obj;
        return Float.compare(x, o.x) == 0 && Float.compare(y, o.y) == 0;
    }

    @Override public int hashCode() {
        int r = x != 0.0f ? Float.floatToIntBits(x) : 0;
        r = 31 * r + (y != 0.0f ? Float.floatToIntBits(y) : 0);
        return r;
    }

    // Parcelable. Mirrors frameworks/base/.../PointF.java exactly: 2 floats,
    // x then y, describeContents() -> 0.
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel out, int flags) {
        out.writeFloat(x); out.writeFloat(y);
    }
    public void readFromParcel(android.os.Parcel in) {
        x = in.readFloat(); y = in.readFloat();
    }
    public static final android.os.Parcelable.Creator<PointF> CREATOR =
        new android.os.Parcelable.Creator<PointF>() {
            @Override public PointF createFromParcel(android.os.Parcel in) {
                PointF p = new PointF();
                p.readFromParcel(in);
                return p;
            }
            @Override public PointF[] newArray(int size) { return new PointF[size]; }
        };
}
