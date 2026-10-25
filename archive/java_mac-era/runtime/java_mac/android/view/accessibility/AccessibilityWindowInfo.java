package android.view.accessibility;

import android.graphics.Rect;
import android.graphics.Region;
import android.os.LocaleList;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * A window as seen by an accessibility service.
 *
 * Only an accessibility service ever receives one (via
 * AccessibilityService.getWindows()); a normal app references the TYPE while
 * compiling code that walks a window list it will never be given. Mac.apk has
 * no accessibility service, so the list is empty and this describes an empty
 * window -- but the class must exist and its methods must link, which is why 22
 * of 37 corpus apps need it.
 *
 * AOSP's obtain()/recycle() are an object-pool API. Pooling here would add a
 * recycled-use hazard for no benefit (nothing produces these at volume), so
 * obtain() allocates and recycle() is a no-op -- both remain safe to call in
 * the AOSP-documented order.
 *
 * Source: frameworks/base/core/java/android/view/accessibility/AccessibilityWindowInfo.java
 * https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/core/java/android/view/accessibility/AccessibilityWindowInfo.java
 */
public final class AccessibilityWindowInfo implements Parcelable {

    public static final int TYPE_APPLICATION = 1;
    public static final int TYPE_INPUT_METHOD = 2;
    public static final int TYPE_SYSTEM = 3;
    public static final int TYPE_ACCESSIBILITY_OVERLAY = 4;
    public static final int TYPE_SPLIT_SCREEN_DIVIDER = 5;
    public static final int TYPE_MAGNIFICATION_OVERLAY = 6;

    public static final int UNDEFINED_WINDOW_ID = -1;
    public static final int ANY_WINDOW_ID = -2;

    private int mId = UNDEFINED_WINDOW_ID;
    private int mType = TYPE_APPLICATION;
    private int mLayer = -1;
    private int mDisplayId = 0;
    private long mTransitionTime;
    private CharSequence mTitle;
    private final Rect mBoundsInScreen = new Rect();

    public AccessibilityWindowInfo() { }

    /** AOSP AccessibilityWindowInfo(AccessibilityWindowInfo) (API 30): the
     *  public copy constructor that replaced obtain(AccessibilityWindowInfo).
     *  This class holds no mutable per-instance state beyond what its own
     *  getters compute (every one answers from the single Mac.apk window), so
     *  the copy is structurally identical to the original -- which is what a
     *  copy constructor has to produce. */
    public AccessibilityWindowInfo(AccessibilityWindowInfo info) { }

    public static AccessibilityWindowInfo obtain() {
        return new AccessibilityWindowInfo();
    }

    public static AccessibilityWindowInfo obtain(AccessibilityWindowInfo info) {
        AccessibilityWindowInfo out = new AccessibilityWindowInfo();
        if (info != null) {
            out.mId = info.mId;
            out.mType = info.mType;
            out.mLayer = info.mLayer;
            out.mDisplayId = info.mDisplayId;
            out.mTransitionTime = info.mTransitionTime;
            out.mTitle = info.mTitle;
            out.mBoundsInScreen.set(info.mBoundsInScreen);
        }
        return out;
    }

    /** No pool to return to; safe to call, per the class comment. */
    public void recycle() { }

    public int getId() { return mId; }

    public int getType() { return mType; }

    public int getLayer() { return mLayer; }

    public int getDisplayId() { return mDisplayId; }

    public long getTransitionTimeMillis() { return mTransitionTime; }

    public CharSequence getTitle() { return mTitle; }

    public LocaleList getLocales() { return LocaleList.getDefault(); }

    /** AOSP writes into the caller's Rect rather than returning one. */
    public void getBoundsInScreen(Rect outBounds) {
        if (outBounds != null) outBounds.set(mBoundsInScreen);
    }

    public void getRegionInScreen(Region outRegion) {
        if (outRegion != null) outRegion.set(mBoundsInScreen);
    }

    /** No accessibility service, so there is no node tree to hand back. */
    public AccessibilityNodeInfo getRoot() { return null; }

    public AccessibilityNodeInfo getRoot(int prefetchingStrategy) { return null; }

    public AccessibilityNodeInfo getAnchor() { return null; }

    public AccessibilityWindowInfo getParent() { return null; }

    public int getChildCount() { return 0; }

    public AccessibilityWindowInfo getChild(int index) {
        throw new IndexOutOfBoundsException("window has no children");
    }

    public boolean isActive() { return false; }

    public boolean isFocused() { return false; }

    public boolean isAccessibilityFocused() { return false; }

    public boolean isInPictureInPictureMode() { return false; }

    @Override public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof AccessibilityWindowInfo)) return false;
        return mId == ((AccessibilityWindowInfo) obj).mId;
    }

    @Override public int hashCode() { return mId; }

    @Override public String toString() {
        return "AccessibilityWindowInfo[id=" + mId + ", type=" + typeToString(mType) + "]";
    }

    public static String typeToString(int type) {
        switch (type) {
            case TYPE_APPLICATION:            return "TYPE_APPLICATION";
            case TYPE_INPUT_METHOD:           return "TYPE_INPUT_METHOD";
            case TYPE_SYSTEM:                 return "TYPE_SYSTEM";
            case TYPE_ACCESSIBILITY_OVERLAY:  return "TYPE_ACCESSIBILITY_OVERLAY";
            case TYPE_SPLIT_SCREEN_DIVIDER:   return "TYPE_SPLIT_SCREEN_DIVIDER";
            case TYPE_MAGNIFICATION_OVERLAY:  return "TYPE_MAGNIFICATION_OVERLAY";
            default:                          return "<UNKNOWN:" + type + ">";
        }
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mId);
        dest.writeInt(mType);
        dest.writeInt(mLayer);
        dest.writeInt(mDisplayId);
        dest.writeLong(mTransitionTime);
        android.text.TextUtils.writeToParcel(mTitle, dest, flags);
        mBoundsInScreen.writeToParcel(dest, flags);
    }

    public static final Creator<AccessibilityWindowInfo> CREATOR =
            new Creator<AccessibilityWindowInfo>() {
        @Override public AccessibilityWindowInfo createFromParcel(Parcel in) {
            AccessibilityWindowInfo w = new AccessibilityWindowInfo();
            w.mId = in.readInt();
            w.mType = in.readInt();
            w.mLayer = in.readInt();
            w.mDisplayId = in.readInt();
            w.mTransitionTime = in.readLong();
            w.mTitle = android.text.TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
            w.mBoundsInScreen.readFromParcel(in);
            return w;
        }
        @Override public AccessibilityWindowInfo[] newArray(int size) {
            return new AccessibilityWindowInfo[size];
        }
    };
}
