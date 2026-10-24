// Mac.apk PointerIcon: minimum AOSP-shape surface so
// androidx.core.view.PointerIconCompat$Api24Impl (called from
// BottomNavigationView's <init> chain and many other Material widgets)
// links and runs. We do not render cursor icons on the macOS host --
// the kept state is purely for caller pass-through (e.g. if F-Droid
// later asks "is this hover icon the HAND type, then show finger
// affordance" we return what was set, but no system pointer change
// happens).
//
// AOSP surface mirrored:
//   - getSystemIcon(Context, int)  -- the actual v1.0.94 trigger
//   - create(Bitmap, float, float)
//   - load(Resources, int)
//   - TYPE_* constants (stable values per AOSP PointerIconType)
//
// AOSP source:
//   https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/PointerIcon.java
//
// 2026-08-23 conformance sweep: added `implements Parcelable`. AOSP's
// writeToParcel (PointerIcon.java L423-437) writes only mType for a
// system icon (the reader re-derives it from resources) and writes the
// bitmap + hotspot + drop-shadow flag for TYPE_CUSTOM, throwing
// IllegalStateException if a custom icon was never actually loaded
// (isLoaded() false). That threw case is now REAL here too: create()
// previously discarded the bitmap it was handed, so every "custom" icon
// was permanently unloaded and any caller that stored one in a Bundle
// got null back on read (the -Xverify:none silent-null bug this sweep
// exists to close). create() now keeps what it is given, matching AOSP.
// load(Resources, int) still does not decode a drawable resource into a
// bitmap (no decoder plumbing here), so a load()-built icon stays
// unloaded and, per AOSP's own contract, throwing on parcel is the
// honest answer rather than fabricating pixels.
package android.view;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;

public final class PointerIcon implements android.os.Parcelable {

    // Type constants. Values match AOSP's PointerIconType enum so
    // caller-passed ints round-trip cleanly through getSystemIcon.
    public static final int TYPE_CUSTOM = -1;
    public static final int TYPE_NULL = 0;
    public static final int TYPE_ARROW = 1000;
    public static final int TYPE_CONTEXT_MENU = 1001;
    public static final int TYPE_HAND = 1002;
    public static final int TYPE_HELP = 1003;
    public static final int TYPE_WAIT = 1004;
    public static final int TYPE_CELL = 1006;
    public static final int TYPE_CROSSHAIR = 1007;
    public static final int TYPE_TEXT = 1008;
    public static final int TYPE_VERTICAL_TEXT = 1009;
    public static final int TYPE_ALIAS = 1010;
    public static final int TYPE_COPY = 1011;
    public static final int TYPE_NO_DROP = 1012;
    public static final int TYPE_ALL_SCROLL = 1013;
    public static final int TYPE_HORIZONTAL_DOUBLE_ARROW = 1014;
    public static final int TYPE_VERTICAL_DOUBLE_ARROW = 1015;
    public static final int TYPE_TOP_RIGHT_DIAGONAL_DOUBLE_ARROW = 1016;
    public static final int TYPE_TOP_LEFT_DIAGONAL_DOUBLE_ARROW = 1017;
    public static final int TYPE_ZOOM_IN = 1018;
    public static final int TYPE_ZOOM_OUT = 1019;
    public static final int TYPE_GRAB = 1020;
    public static final int TYPE_GRABBING = 1021;
    public static final int TYPE_HANDWRITING = 1022;
    public static final int TYPE_DEFAULT = TYPE_ARROW;

    private final int mType;
    private Bitmap mBitmap;
    private float mHotSpotX;
    private float mHotSpotY;
    private boolean mDrawNativeDropShadow;

    private PointerIcon(int type) {
        this.mType = type;
    }

    public PointerIcon() {
        this.mType = TYPE_NULL;
    }

    // PointerIconCompat$Api24Impl.getSystemIcon -- the trigger that
    // brought this class onto the radar at v1.0.94.
    public static PointerIcon getSystemIcon(Context context, int type) {
        return new PointerIcon(type);
    }

    // PointerIconCompat$Api24Impl.create
    public static PointerIcon create(Bitmap bitmap, float hotSpotX, float hotSpotY) {
        PointerIcon icon = new PointerIcon(TYPE_CUSTOM);
        icon.mBitmap = bitmap;
        icon.mHotSpotX = hotSpotX;
        icon.mHotSpotY = hotSpotY;
        return icon;
    }

    // PointerIconCompat$Api24Impl.load
    public static PointerIcon load(Resources resources, int resourceId) {
        return new PointerIcon(TYPE_CUSTOM);
    }

    public int getType() {
        return mType;
    }

    private boolean isLoaded() {
        return mBitmap != null && mHotSpotX >= 0 && mHotSpotX < mBitmap.getWidth()
                && mHotSpotY >= 0 && mHotSpotY < mBitmap.getHeight();
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel out, int flags) {
        out.writeInt(mType);
        if (mType != TYPE_CUSTOM) {
            // A system icon type is re-derived from resources on read, so
            // there is nothing else to write (AOSP: same short-circuit).
            return;
        }
        if (!isLoaded()) {
            throw new IllegalStateException("Custom icon should be loaded upon creation");
        }
        mBitmap.writeToParcel(out, flags);
        out.writeFloat(mHotSpotX);
        out.writeFloat(mHotSpotY);
        out.writeBoolean(mDrawNativeDropShadow);
    }

    public static final android.os.Parcelable.Creator<PointerIcon> CREATOR =
            new android.os.Parcelable.Creator<PointerIcon>() {
        @Override public PointerIcon createFromParcel(android.os.Parcel in) {
            int type = in.readInt();
            if (type != TYPE_CUSTOM) {
                return new PointerIcon(type);
            }
            PointerIcon icon = PointerIcon.create(
                    android.graphics.Bitmap.CREATOR.createFromParcel(in),
                    in.readFloat(), in.readFloat());
            icon.mDrawNativeDropShadow = in.readBoolean();
            return icon;
        }
        @Override public PointerIcon[] newArray(int size) { return new PointerIcon[size]; }
    };
}
