// android.graphics.Insets -- immutable representation of an
// inset region (left, top, right, bottom). Per AOSP source at
// https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/graphics/java/android/graphics/Insets.java
// Insets.NONE is a shared sentinel for "all-zero" usage. Apps
// often write `if (insets == Insets.NONE) return;` so the
// shared instance matters.
package android.graphics;

public final class Insets implements android.os.Parcelable {
    public static final Insets NONE = new Insets(0, 0, 0, 0);

    public final int left;
    public final int top;
    public final int right;
    public final int bottom;

    public Insets() { this(0, 0, 0, 0); }
    private Insets(int left, int top, int right, int bottom) {
        this.left = left; this.top = top; this.right = right; this.bottom = bottom;
    }

    public static Insets of(int left, int top, int right, int bottom) {
        if (left == 0 && top == 0 && right == 0 && bottom == 0) return NONE;
        return new Insets(left, top, right, bottom);
    }
    public static Insets of(Rect r) {
        return r == null ? NONE : of(r.left, r.top, r.right, r.bottom);
    }
    public static Insets add(Insets a, Insets b) {
        if (a == null) a = NONE;
        if (b == null) b = NONE;
        return of(a.left + b.left, a.top + b.top, a.right + b.right, a.bottom + b.bottom);
    }
    public static Insets subtract(Insets a, Insets b) {
        if (a == null) a = NONE;
        if (b == null) b = NONE;
        return of(a.left - b.left, a.top - b.top, a.right - b.right, a.bottom - b.bottom);
    }
    public static Insets max(Insets a, Insets b) {
        if (a == null) a = NONE;
        if (b == null) b = NONE;
        return of(Math.max(a.left, b.left), Math.max(a.top, b.top),
                  Math.max(a.right, b.right), Math.max(a.bottom, b.bottom));
    }
    public static Insets min(Insets a, Insets b) {
        if (a == null) a = NONE;
        if (b == null) b = NONE;
        return of(Math.min(a.left, b.left), Math.min(a.top, b.top),
                  Math.min(a.right, b.right), Math.min(a.bottom, b.bottom));
    }
    public Rect toRect() { return new Rect(left, top, right, bottom); }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Insets)) return false;
        Insets other = (Insets) o;
        return other.left == left && other.top == top && other.right == right && other.bottom == bottom;
    }
    @Override public int hashCode() {
        int h = left; h = 31 * h + top; h = 31 * h + right; h = 31 * h + bottom; return h;
    }
    @Override public String toString() {
        return "Insets{left=" + left + ", top=" + top + ", right=" + right + ", bottom=" + bottom + "}";
    }

    // --- Parcelable (AOSP Insets.java). Note createFromParcel does NOT fold
    //     an all-zero read onto NONE: AOSP constructs a fresh instance, so an
    //     app that round-trips Insets.NONE gets an equal-but-not-identical
    //     object, exactly as on device.
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel out, int flags) {
        out.writeInt(left); out.writeInt(top); out.writeInt(right); out.writeInt(bottom);
    }
    public static final android.os.Parcelable.Creator<Insets> CREATOR =
            new android.os.Parcelable.Creator<Insets>() {
        @Override public Insets createFromParcel(android.os.Parcel in) {
            return new Insets(in.readInt(), in.readInt(), in.readInt(), in.readInt());
        }
        @Override public Insets[] newArray(int size) { return new Insets[size]; }
    };
}
