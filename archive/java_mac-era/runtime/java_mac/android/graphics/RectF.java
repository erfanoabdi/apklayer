// android.graphics.RectF -- public-field float-precision rectangle.
// Mirrors AOSP frameworks/base/graphics/java/android/graphics/RectF.java
// surface: Skia / Compose / SwipeRefreshLayout's CircleImageView use
// the float-precision form for path/shadow geometry where int-rounding
// (Rect) would jitter.
//
// v1.0.834: AOSP declares `public class RectF implements Parcelable`
// (frameworks/base/graphics/java/android/graphics/RectF.java line 36,
// verified against aosp-mirror main) but this class was missing the
// `implements` and the describeContents/writeToParcel methods entirely,
// even though a CREATOR field already existed below -- Rect.java's own
// comment on the identical gap explains why that matters: an app class
// compiled against real android.jar that stores a RectF through a
// Parcelable-typed reference (Bundle.putParcelable, Intent.putExtra, a
// custom Parcelable with a RectF field) verifies the stack type against
// android.os.Parcelable at class-load time, so the whole containing
// class failed with VerifyError, not just the call site.
package android.graphics;

public class RectF implements android.os.Parcelable {
    public float left, top, right, bottom;

    public RectF() {}
    public RectF(float l, float t, float r, float b) { left = l; top = t; right = r; bottom = b; }
    public RectF(RectF o) { if (o != null) { left = o.left; top = o.top; right = o.right; bottom = o.bottom; } }
    public RectF(Rect src) { if (src != null) { left = src.left; top = src.top; right = src.right; bottom = src.bottom; } }

    public final float width()  { return right - left; }
    public final float height() { return bottom - top; }
    public final float centerX(){ return (left + right) * 0.5f; }
    public final float centerY(){ return (top + bottom) * 0.5f; }
    public final boolean isEmpty() { return left >= right || top >= bottom; }

    public void setEmpty() { left = right = top = bottom = 0; }
    public void set(float l, float t, float r, float b) { left = l; top = t; right = r; bottom = b; }
    public void set(RectF o) { if (o != null) set(o.left, o.top, o.right, o.bottom); }
    public void set(Rect r) { if (r != null) set(r.left, r.top, r.right, r.bottom); }

    public void offset(float dx, float dy) {
        left += dx; right += dx; top += dy; bottom += dy;
    }
    public void offsetTo(float newLeft, float newTop) {
        right += newLeft - left; bottom += newTop - top;
        left = newLeft; top = newTop;
    }
    /** AOSP: shrink by (dx, dy) on each side. Negative values grow. */
    public void inset(float dx, float dy) {
        left += dx; top += dy; right -= dx; bottom -= dy;
    }
    /** AOSP API-29 four-arg variant: independent insets per edge. */
    public void inset(float insetLeft, float insetTop, float insetRight, float insetBottom) {
        left += insetLeft; top += insetTop; right -= insetRight; bottom -= insetBottom;
    }

    /** ⚠ THE EMPTY CHECK IS PART OF THE CONTRACT (RectF.java:288-330): "An
     *  empty rectangle never contains any point" / "never contains another
     *  rectangle". Without it a collapsed rect reports containing itself. */
    public boolean contains(float x, float y) {
        return left < right && top < bottom          // check for empty first
                && x >= left && x < right && y >= top && y < bottom;
    }
    public boolean contains(float l, float t, float r, float b) {
        return left < right && top < bottom
                && left <= l && top <= t && right >= r && bottom >= b;
    }
    public boolean contains(RectF r) {
        return r != null && contains(r.left, r.top, r.right, r.bottom);
    }

    public boolean intersect(float l, float t, float r, float b) {
        if (left < r && l < right && top < b && t < bottom) {
            if (left < l) left = l;
            if (top  < t) top  = t;
            if (right  > r) right  = r;
            if (bottom > b) bottom = b;
            return true;
        }
        return false;
    }
    public boolean intersect(RectF r) {
        return r != null && intersect(r.left, r.top, r.right, r.bottom);
    }
    /** AOSP RectF.setIntersect(RectF,RectF): sets THIS to the intersection of a
     *  and b (this need not alias either) and reports whether they overlapped.
     *  Absent here, so an app compiled against the real android.jar took a
     *  NoSuchMethodError. The null guard follows this file's existing
     *  RectF-arg convention (see intersect(RectF) above) rather than AOSP's
     *  unchecked dereference. */
    public boolean setIntersect(RectF a, RectF b) {
        if (a != null && b != null
                && a.left < b.right && b.left < a.right
                && a.top < b.bottom && b.top < a.bottom) {
            left = Math.max(a.left, b.left);
            top = Math.max(a.top, b.top);
            right = Math.min(a.right, b.right);
            bottom = Math.min(a.bottom, b.bottom);
            return true;
        }
        return false;
    }
    public boolean intersects(float l, float t, float r, float b) {
        return left < r && l < right && top < b && t < bottom;
    }
    public static boolean intersects(RectF a, RectF b) {
        return a != null && b != null
            && a.left < b.right && b.left < a.right
            && a.top  < b.bottom && b.top  < a.bottom;
    }

    public void union(float l, float t, float r, float b) {
        if (l < r && t < b) {
            if (left < right && top < bottom) {
                if (left > l) left = l;
                if (top  > t) top  = t;
                if (right  < r) right  = r;
                if (bottom < b) bottom = b;
            } else {
                left = l; top = t; right = r; bottom = b;
            }
        }
    }
    public void union(RectF r) { if (r != null) union(r.left, r.top, r.right, r.bottom); }
    public void union(float x, float y) {
        if (x < left) left = x; else if (x > right) right = x;
        if (y < top)  top  = y; else if (y > bottom) bottom = y;
    }

    public void sort() {
        if (left  > right ) { float t = left; left = right; right = t; }
        if (top   > bottom) { float t = top;  top  = bottom; bottom = t; }
    }

    /** AOSP rounds through android.util.FastMath.round (RectF.java:444-451),
     *  NOT Math.round. FastMath is a 16.8 fixed-point trick --
     *  `long lx = (long)(value * (65536 * 256f)); return (int)((lx + 0x800000) >> 24);`
     *  -- whose documented contract is "faster than Math.round() though it may
     *  return slightly different results. It does not try to handle (in any
     *  meaningful way) NaN or infinities." The difference is not academic: it
     *  WRAPS instead of saturating, so a runaway measured size of 1e10 rounds
     *  to 1410065408 on a device and to Integer.MAX_VALUE here. A layout that
     *  rounds a garbage size then gets a different SIGN of garbage on each
     *  host, which is a divergence an app can see. The arithmetic is inlined
     *  here rather than routed through android.util.FastMath so that this
     *  file's behaviour cannot silently change out from under it. */
    private static int fastRound(float value) {
        long lx = (long) (value * (65536 * 256f));
        return (int) ((lx + 0x800000) >> 24);
    }
    public final void round(Rect dst) {
        if (dst == null) return;
        dst.set(fastRound(left), fastRound(top), fastRound(right), fastRound(bottom));
    }
    public final void roundOut(Rect dst) {
        if (dst == null) return;
        dst.set((int) Math.floor(left), (int) Math.floor(top),
                (int) Math.ceil(right),  (int) Math.ceil(bottom));
    }

    // AOSP defines RectF as a VALUE type with equals/hashCode. Without them the
    // inherited identity comparison makes two identical RectFs unequal, so any
    // `if (bounds.equals(newBounds)) return;` early-out silently never fires
    // and a RectF used as a map key or in a Set behaves wrongly.
    // Source: frameworks/base/graphics/java/android/graphics/RectF.java
    @Override public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof RectF)) return false;
        RectF o = (RectF) obj;
        return Float.compare(left, o.left) == 0 && Float.compare(top, o.top) == 0 && Float.compare(right, o.right) == 0 && Float.compare(bottom, o.bottom) == 0;
    }

    @Override public int hashCode() {
        int r = left != 0.0f ? Float.floatToIntBits(left) : 0;
        r = 31 * r + (top != 0.0f ? Float.floatToIntBits(top) : 0);
        r = 31 * r + (right != 0.0f ? Float.floatToIntBits(right) : 0);
        r = 31 * r + (bottom != 0.0f ? Float.floatToIntBits(bottom) : 0);
        return r;
    }

    // Parcelable. Mirrors frameworks/base/.../RectF.java exactly: 4 floats,
    // left/top/right/bottom order, describeContents()->0 (see class-header
    // comment for why the `implements` + these methods are load-bearing).
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel out, int flags) {
        out.writeFloat(left); out.writeFloat(top);
        out.writeFloat(right); out.writeFloat(bottom);
    }
    public void readFromParcel(android.os.Parcel in) {
        left = in.readFloat(); top = in.readFloat();
        right = in.readFloat(); bottom = in.readFloat();
    }
    public static final android.os.Parcelable.Creator<RectF> CREATOR =
        new android.os.Parcelable.Creator<RectF>() {
            @Override public RectF createFromParcel(android.os.Parcel in) {
                RectF r = new RectF();
                r.readFromParcel(in);
                return r;
            }
            @Override public RectF[] newArray(int size) { return new RectF[size]; }
        };

    /** AOSP RectF.toString (RectF.java:106). Unlike Rect, RectF uses a plain
     *  comma between every component. Absent here entirely, so it inherited
     *  Object.toString and emitted an identity hash. */
    @Override public String toString() {
        return "RectF(" + left + ", " + top + ", " + right + ", " + bottom + ")";
    }

    /** AOSP RectF.toShortString() (RectF.java) -- the compact "[l,t][r,b]" form
     *  used by dumpsys output and by apps that log rects. */
    public String toShortString() {
        return "[" + left + "," + top + "][" + right + "," + bottom + "]";
    }
}

