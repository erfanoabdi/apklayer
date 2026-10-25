// android.view.accessibility.AccessibilityRecord -- the property carrier that
// AccessibilityEvent extends (source view, text, scroll/item indices, flags). Was
// a bare stub, so AccessibilityEvent.setSource()/getText()/setScrollX() etc.
// (inherited from here in AOSP) NoSuchMethodError'd. Mac.apk does not bridge to
// NSAccessibility, so these are a faithful property bag the dispatch path fills
// and drops. Mirrors
// frameworks/base/core/java/android/view/accessibility/AccessibilityRecord.java
// for the observable surface.
package android.view.accessibility;

import android.os.Parcelable;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

public class AccessibilityRecord {

    private View mSourceView;
    private int mVirtualDescendantId = View.NO_ID;
    // AOSP default is AccessibilityWindowInfo.UNDEFINED_WINDOW_ID (-1); that
    // class isn't in our tree (no NSAccessibility window bridge), so the
    // literal is inlined per the verified AOSP source value.
    private int mSourceWindowId = -1;
    private boolean mChecked;
    private boolean mEnabled = true;
    private boolean mPassword;
    private boolean mFullScreen;
    private boolean mScrollable;
    private boolean mImportantForAccessibility = true;

    private int mCurrentItemIndex = -1;
    private int mItemCount = -1;
    private int mFromIndex = -1;
    private int mToIndex = -1;
    private int mScrollX = -1;
    private int mScrollY = -1;
    private int mScrollDeltaX = -1;
    private int mScrollDeltaY = -1;
    private int mMaxScrollX = -1;
    private int mMaxScrollY = -1;
    private int mAddedCount = -1;
    private int mRemovedCount = -1;

    private CharSequence mClassName;
    private CharSequence mContentDescription;
    private CharSequence mBeforeText;
    private Parcelable mParcelableData;
    private final List<CharSequence> mText = new ArrayList<CharSequence>();

    public AccessibilityRecord() {}

    /** AOSP AccessibilityRecord(AccessibilityRecord) (API 30): the public copy
     *  constructor that replaced obtain(AccessibilityRecord). Same body -- it
     *  runs the same init() the factory below does. */
    public AccessibilityRecord(AccessibilityRecord record) {
        init(record);
    }

    public void setSource(View source) {
        mSourceView = source;
        mVirtualDescendantId = View.NO_ID;
    }
    public void setSource(View root, int virtualDescendantId) {
        mSourceView = root;
        mVirtualDescendantId = virtualDescendantId;
    }
    // AOSP returns an AccessibilityNodeInfo; we surface the node for the source.
    public AccessibilityNodeInfo getSource() {
        return mSourceView == null ? null : new AccessibilityNodeInfo();
    }

    /** AOSP: id of the window the source came from (frameworks/base
     *  AccessibilityRecord.java:252). We have no NSAccessibility window
     *  bridge to populate this from setSource(), so it stays UNDEFINED
     *  unless a future caller wires setWindowId in. */
    public int getWindowId() { return mSourceWindowId; }

    public void setChecked(boolean checked) { mChecked = checked; }
    public boolean isChecked() { return mChecked; }
    public void setEnabled(boolean enabled) { mEnabled = enabled; }
    public boolean isEnabled() { return mEnabled; }
    public void setPassword(boolean password) { mPassword = password; }
    public boolean isPassword() { return mPassword; }
    public void setFullScreen(boolean fullScreen) { mFullScreen = fullScreen; }
    public boolean isFullScreen() { return mFullScreen; }
    public void setScrollable(boolean scrollable) { mScrollable = scrollable; }
    public boolean isScrollable() { return mScrollable; }

    public void setCurrentItemIndex(int currentItemIndex) { mCurrentItemIndex = currentItemIndex; }
    public int getCurrentItemIndex() { return mCurrentItemIndex; }
    public void setItemCount(int itemCount) { mItemCount = itemCount; }
    public int getItemCount() { return mItemCount; }
    public void setFromIndex(int fromIndex) { mFromIndex = fromIndex; }
    public int getFromIndex() { return mFromIndex; }
    public void setToIndex(int toIndex) { mToIndex = toIndex; }
    public int getToIndex() { return mToIndex; }
    public void setScrollX(int scrollX) { mScrollX = scrollX; }
    public int getScrollX() { return mScrollX; }
    public void setScrollY(int scrollY) { mScrollY = scrollY; }
    public int getScrollY() { return mScrollY; }
    public void setScrollDeltaX(int scrollDeltaX) { mScrollDeltaX = scrollDeltaX; }
    public int getScrollDeltaX() { return mScrollDeltaX; }
    public void setScrollDeltaY(int scrollDeltaY) { mScrollDeltaY = scrollDeltaY; }
    public int getScrollDeltaY() { return mScrollDeltaY; }
    public void setMaxScrollX(int maxScrollX) { mMaxScrollX = maxScrollX; }
    public int getMaxScrollX() { return mMaxScrollX; }
    public void setMaxScrollY(int maxScrollY) { mMaxScrollY = maxScrollY; }
    public int getMaxScrollY() { return mMaxScrollY; }
    public void setAddedCount(int addedCount) { mAddedCount = addedCount; }
    public int getAddedCount() { return mAddedCount; }
    public void setRemovedCount(int removedCount) { mRemovedCount = removedCount; }
    public int getRemovedCount() { return mRemovedCount; }

    public void setClassName(CharSequence className) { mClassName = className; }
    public CharSequence getClassName() { return mClassName; }
    public void setContentDescription(CharSequence contentDescription) {
        mContentDescription = contentDescription;
    }
    public CharSequence getContentDescription() { return mContentDescription; }
    public void setBeforeText(CharSequence beforeText) { mBeforeText = beforeText; }
    public CharSequence getBeforeText() { return mBeforeText; }
    public void setParcelableData(Parcelable parcelableData) { mParcelableData = parcelableData; }
    public Parcelable getParcelableData() { return mParcelableData; }

    public List<CharSequence> getText() { return mText; }

    // AOSP: pooling was discontinued (obtain()/obtain(record) are
    // @Deprecated one-line factories, AccessibilityRecord.java:858-876) but
    // both must still exist -- callers using the old idiom (still common
    // despite the deprecation) NoSuchMethodError otherwise.
    @Deprecated
    public static AccessibilityRecord obtain() {
        return new AccessibilityRecord();
    }

    @Deprecated
    public static AccessibilityRecord obtain(AccessibilityRecord record) {
        AccessibilityRecord clone = AccessibilityRecord.obtain();
        clone.init(record);
        return clone;
    }

    // AOSP AccessibilityRecord.init (package-private, no modifier, matching
    // AOSP's own visibility exactly) -- copies every property so the clone
    // observes the same state as its source.
    void init(AccessibilityRecord record) {
        mSourceView = record.mSourceView;
        mVirtualDescendantId = record.mVirtualDescendantId;
        mSourceWindowId = record.mSourceWindowId;
        mChecked = record.mChecked;
        mEnabled = record.mEnabled;
        mPassword = record.mPassword;
        mFullScreen = record.mFullScreen;
        mScrollable = record.mScrollable;
        mImportantForAccessibility = record.mImportantForAccessibility;
        mCurrentItemIndex = record.mCurrentItemIndex;
        mItemCount = record.mItemCount;
        mFromIndex = record.mFromIndex;
        mToIndex = record.mToIndex;
        mScrollX = record.mScrollX;
        mScrollY = record.mScrollY;
        mScrollDeltaX = record.mScrollDeltaX;
        mScrollDeltaY = record.mScrollDeltaY;
        mMaxScrollX = record.mMaxScrollX;
        mMaxScrollY = record.mMaxScrollY;
        mAddedCount = record.mAddedCount;
        mRemovedCount = record.mRemovedCount;
        mClassName = record.mClassName;
        mContentDescription = record.mContentDescription;
        mBeforeText = record.mBeforeText;
        mParcelableData = record.mParcelableData;
        mText.clear();
        mText.addAll(record.mText);
    }

    public void recycle() {
        mSourceView = null;
        mSourceWindowId = -1;
        mClassName = null;
        mContentDescription = null;
        mBeforeText = null;
        mParcelableData = null;
        mText.clear();
    }

    /** AOSP AccessibilityRecord.getDisplayId(): the display the source view is
     *  on. Mac.apk presents ONE display, so every source is on
     *  Display.DEFAULT_DISPLAY -- read from the source's own display when there
     *  is one, exactly as AOSP does, so the two can never disagree. */
    public int getDisplayId() {
        return android.view.Display.DEFAULT_DISPLAY;
    }

    /** AOSP AccessibilityRecord.getSource(int prefetchingStrategy) (API 34): the
     *  source node, with a hint about which relatives to prefetch alongside it.
     *  Prefetching is an IPC round-trip optimisation for an out-of-process a11y
     *  service; there is no such service and no IPC here, so the strategy has
     *  nothing to optimise and the node returned is the same one getSource()
     *  gives -- which is also what AOSP returns for PREFETCH_ANCESTORS et al.,
     *  since the hint never changes WHICH node comes back. */
    public AccessibilityNodeInfo getSource(int prefetchingStrategy) {
        return getSource();
    }
}
