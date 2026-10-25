// android.graphics.Rect -- public-field rectangle. Apps mutate fields
// directly so we keep them public.
package android.graphics;

import android.os.Parcel;
import android.os.Parcelable;

public final class Rect implements Parcelable {
    public int left, top, right, bottom;
    public Rect() {}
    public Rect(int l, int t, int r, int b) { left = l; top = t; right = r; bottom = b; }
    public Rect(Rect o) { if (o != null) { left = o.left; top = o.top; right = o.right; bottom = o.bottom; } }
    public final int width()  { return right - left; }
    public final int height() { return bottom - top; }
    public final int centerX() { return (left + right) / 2; }
    public final int centerY() { return (top + bottom) / 2; }
    /** AOSP: float-precision center. SwipeRefreshLayout's
     *  CircleImageView positions the spinner via these (the int
     *  centerX/Y would round-trip and the spinner would jitter on
     *  rotation). Mirrors AOSP frameworks/base/.../graphics/Rect.java
     *  exactCenterX(): `return (left + right) * 0.5f;`. */
    public final float exactCenterX() { return (left + right) * 0.5f; }
    public final float exactCenterY() { return (top + bottom) * 0.5f; }
    public final boolean isEmpty() { return left >= right || top >= bottom; }
    public void setEmpty() { left = right = top = bottom = 0; }
    public void set(int l, int t, int r, int b) { left = l; top = t; right = r; bottom = b; }
    public void set(Rect o) { if (o != null) set(o.left, o.top, o.right, o.bottom); }
    /** ⚠ THE EMPTY CHECK IS PART OF THE CONTRACT, not an optimization: "An
     *  empty rectangle never contains any point" / "never contains another
     *  rectangle" (Rect.java:465-510). Without it a zero-size or INVERTED rect
     *  reports that it contains itself, and any hit-test or invalidate-rect
     *  guard written as `if (bounds.contains(r))` fires on a collapsed view. */
    public boolean contains(int x, int y) {
        return left < right && top < bottom          // check for empty first
                && x >= left && x < right && y >= top && y < bottom;
    }
    /** AOSP Rect.contains(int,int,int,int) (Rect.java:489-495). Absent here
     *  entirely, so an app compiled against the real android.jar took a
     *  NoSuchMethodError out of the middle of whatever it was doing. */
    public boolean contains(int left, int top, int right, int bottom) {
        return this.left < this.right && this.top < this.bottom
                && this.left <= left && this.top <= top
                && this.right >= right && this.bottom >= bottom;
    }
    public boolean contains(Rect r) {
        return r != null && left < right && top < bottom
                && left <= r.left && top <= r.top && right >= r.right && bottom >= r.bottom;
    }
    public void offset(int dx, int dy) { left += dx; right += dx; top += dy; bottom += dy; }
    public void offsetTo(int newLeft, int newTop) { right += newLeft - left; bottom += newTop - top; left = newLeft; top = newTop; }
    public void inset(int dx, int dy) { left += dx; right -= dx; top += dy; bottom -= dy; }
    /** AOSP Rect.inset(Insets) (API 29). Insets are DIRECTIONAL: right/bottom
     *  shrink, they are not mirrored like the (dx,dy) form. */
    public void inset(Insets insets) {
        if (insets == null) return;
        left += insets.left; top += insets.top; right -= insets.right; bottom -= insets.bottom;
    }
    /** AOSP Rect.inset(int,int,int,int) (API 29). Same directional contract. */
    public void inset(int left, int top, int right, int bottom) {
        this.left += left; this.top += top; this.right -= right; this.bottom -= bottom;
    }
    public boolean intersect(int l, int t, int r, int b) {
        if (left < r && l < right && top < b && t < bottom) {
            if (left < l) left = l;
            if (top < t) top = t;
            if (right > r) right = r;
            if (bottom > b) bottom = b;
            return true;
        }
        return false;
    }
    public boolean intersect(Rect r) { return r != null && intersect(r.left, r.top, r.right, r.bottom); }
    /** AOSP: non-mutating overlap test (distinct from the mutating
     *  intersect(...) above). Mirrors frameworks/base/.../Rect.java
     *  intersects(int,int,int,int). */
    public boolean intersects(int l, int t, int r, int b) {
        return left < r && l < right && top < b && t < bottom;
    }
    /** AOSP static non-mutating overlap test between two rects. */
    public static boolean intersects(Rect a, Rect b) {
        return a != null && b != null
                && a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom;
    }
    /** AOSP: sets this rect to the intersection of a and b (this need not
     *  already alias a) and reports whether they overlapped. Mirrors
     *  frameworks/base/.../Rect.java setIntersect(Rect,Rect); the null
     *  guard follows this file's existing Rect-arg convention (see
     *  intersect(Rect)/union(Rect) above) rather than AOSP's NPE-on-null. */
    public boolean setIntersect(Rect a, Rect b) {
        if (a != null && b != null
                && a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom) {
            left = Math.max(a.left, b.left);
            top = Math.max(a.top, b.top);
            right = Math.min(a.right, b.right);
            bottom = Math.min(a.bottom, b.bottom);
            return true;
        }
        return false;
    }
    public void union(int l, int t, int r, int b) {
        if ((l < r) && (t < b)) {
            if ((this.left < this.right) && (this.top < this.bottom)) {
                if (left > l) left = l;
                if (top > t) top = t;
                if (right < r) right = r;
                if (bottom < b) bottom = b;
            } else {
                left = l; top = t; right = r; bottom = b;
            }
        }
    }
    public void union(Rect r) { if (r != null) union(r.left, r.top, r.right, r.bottom); }
    /** AOSP Rect.union(int x, int y) -- the POINT union (Rect.java:663-682).
     *  Note the documented asymmetry: it is an else-if chain, so a point beyond
     *  BOTH edges of an inverted rect moves only one of them, and "there is no
     *  check to see that this rectangle is non-empty". Reproduce it literally;
     *  a min/max pair would answer differently on an inverted rect. */
    public void union(int x, int y) {
        if (x < left) left = x; else if (x > right) right = x;
        if (y < top) top = y; else if (y > bottom) bottom = y;
    }
    /** AOSP Rect.sort (Rect.java:691-702): swap the edges if they crossed over.
     *  Callers compute edges separately and then sort, so a missing sort() left
     *  an inverted rect that isEmpty() and contains() nothing. */
    public void sort() {
        if (left > right) { int t = left; left = right; right = t; }
        if (top > bottom) { int t = top; top = bottom; bottom = t; }
    }

    /** AOSP Rect.flattenToString / unflattenFromString (Rect.java:184-221).
     *  "WARNING: Do not change the format of this string, it must be preserved
     *  because Rects are saved in this flattened format" -- it is the on-disk
     *  form for a saved Rect, so the space separation and the accepted pattern
     *  are both contract. unflattenFromString returns NULL (never throws) for
     *  anything that does not match. */
    public String flattenToString() {
        return left + " " + top + " " + right + " " + bottom;
    }
    /** Holder class, exactly as AOSP does it (Rect.java:53-66): keeping the
     *  Pattern out of Rect's own <clinit> avoids paying a regex compile on the
     *  first touch of a class this hot. */
    private static final class UnflattenHelper {
        private static final java.util.regex.Pattern FLATTENED_PATTERN =
                java.util.regex.Pattern.compile("(-?\\d+) (-?\\d+) (-?\\d+) (-?\\d+)");
    }
    public static Rect unflattenFromString(String str) {
        if (str == null || str.length() == 0) return null;
        java.util.regex.Matcher matcher = UnflattenHelper.FLATTENED_PATTERN.matcher(str);
        if (!matcher.matches()) return null;
        return new Rect(Integer.parseInt(matcher.group(1)),
                        Integer.parseInt(matcher.group(2)),
                        Integer.parseInt(matcher.group(3)),
                        Integer.parseInt(matcher.group(4)));
    }
    /** AOSP Rect.toString (Rect.java:130) separates top from right with " - ",
     *  not ", ": `Rect(0, 0 - 32, 32)`. It looks like a typo and is not; it has
     *  been that way since API 1 and app code parses it. */
    @Override public String toString() { return "Rect(" + left + ", " + top + " - " + right + ", " + bottom + ")"; }
    /** AOSP compact form, e.g. "[10,20][30,40]". Mirrors
     *  frameworks/base/.../Rect.java toShortString(). */
    public String toShortString() {
        return "[" + left + "," + top + "][" + right + "," + bottom + "]";
    }

    // Parcelable. Mirrors frameworks/base/.../Rect.java exactly: 4 ints,
    // left/top/right/bottom order, describeContents()->0. Needed because
    // Bundle.putParcelable(String,Parcelable) / Intent.putExtra(String,Parcelable)
    // declare Parcelable-typed formal params -- an app class compiled against
    // real android.jar that stores a Rect through one of those verifies its
    // stack type against android.os.Parcelable at class-load time, and without
    // this the whole containing class fails with VerifyError, not just the call site.
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeInt(left); out.writeInt(top);
        out.writeInt(right); out.writeInt(bottom);
    }
    public void readFromParcel(Parcel in) {
        left = in.readInt(); top = in.readInt();
        right = in.readInt(); bottom = in.readInt();
    }
    public static final Parcelable.Creator<Rect> CREATOR = new Parcelable.Creator<Rect>() {
        @Override public Rect createFromParcel(Parcel in) {
            Rect r = new Rect();
            r.readFromParcel(in);
            return r;
        }
        @Override public Rect[] newArray(int size) { return new Rect[size]; }
    };

    // AOSP defines Rect as a VALUE type with equals/hashCode. Without them the
    // inherited identity comparison makes two identical Rects unequal, so any
    // `if (bounds.equals(newBounds)) return;` early-out silently never fires
    // and a Rect used as a map key or in a Set behaves wrongly.
    // Source: frameworks/base/graphics/java/android/graphics/Rect.java
    @Override public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Rect)) return false;
        Rect o = (Rect) obj;
        return left == o.left && top == o.top && right == o.right && bottom == o.bottom;
    }

    @Override public int hashCode() {
        int r = left;
        r = 31 * r + top;
        r = 31 * r + right;
        r = 31 * r + bottom;
        return r;
    }
}
