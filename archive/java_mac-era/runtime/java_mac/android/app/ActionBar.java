// android.app.ActionBar -- the app's title/navigation bar (the platform,
// non-AppCompat one returned by Activity.getActionBar()). AOSP API-11+.
//
// AOSP ref: frameworks/base/core/java/android/app/ActionBar.java
// (declared abstract). Mac.apk has no system action-bar chrome; plain-
// theme apps still call getActionBar().setTitle(...) / hide() / etc., so
// we provide a concrete instance that records title/subtitle/display
// options and treats show/hide as state toggles. AppCompat apps use
// androidx.appcompat's ActionBar (a different class) and never reach this.
package android.app;

public class ActionBar {
    // Navigation modes (deprecated but referenced).
    public static final int NAVIGATION_MODE_STANDARD = 0;
    public static final int NAVIGATION_MODE_LIST     = 1;
    public static final int NAVIGATION_MODE_TABS     = 2;

    // Display option flags.
    public static final int DISPLAY_USE_LOGO      = 0x1;
    public static final int DISPLAY_SHOW_HOME     = 0x2;
    public static final int DISPLAY_HOME_AS_UP    = 0x4;
    public static final int DISPLAY_SHOW_TITLE    = 0x8;
    public static final int DISPLAY_SHOW_CUSTOM   = 0x10;

    private CharSequence mTitle;
    private CharSequence mSubtitle;
    private int mDisplayOptions = DISPLAY_SHOW_TITLE | DISPLAY_SHOW_HOME;
    private int mNavigationMode = NAVIGATION_MODE_STANDARD;
    private boolean mShowing = true;
    private android.view.View mCustomView;

    public void setTitle(CharSequence title) { mTitle = title; }
    public void setTitle(int resId) {}
    public CharSequence getTitle() { return mTitle; }
    public void setSubtitle(CharSequence subtitle) { mSubtitle = subtitle; }
    public void setSubtitle(int resId) {}
    public CharSequence getSubtitle() { return mSubtitle; }

    public void setDisplayOptions(int options) { mDisplayOptions = options; }
    public void setDisplayOptions(int options, int mask) { mDisplayOptions = (mDisplayOptions & ~mask) | (options & mask); }
    public int getDisplayOptions() { return mDisplayOptions; }
    public void setDisplayUseLogoEnabled(boolean useLogo) { setFlag(DISPLAY_USE_LOGO, useLogo); }
    public void setDisplayShowHomeEnabled(boolean showHome) { setFlag(DISPLAY_SHOW_HOME, showHome); }
    public void setDisplayHomeAsUpEnabled(boolean showHomeAsUp) { setFlag(DISPLAY_HOME_AS_UP, showHomeAsUp); }
    public void setDisplayShowTitleEnabled(boolean showTitle) { setFlag(DISPLAY_SHOW_TITLE, showTitle); }
    public void setDisplayShowCustomEnabled(boolean showCustom) { setFlag(DISPLAY_SHOW_CUSTOM, showCustom); }
    private void setFlag(int flag, boolean on) { if (on) mDisplayOptions |= flag; else mDisplayOptions &= ~flag; }

    public void setHomeButtonEnabled(boolean enabled) {}
    public void setHomeAsUpIndicator(android.graphics.drawable.Drawable indicator) {}
    public void setHomeAsUpIndicator(int resId) {}
    // AOSP core/java/android/app/ActionBar.java (android13-release) :1032-1047.
    // The literal base ActionBar class throws UnsupportedOperationException
    // for a non-zero setElevation() and hardcodes getElevation() to 0 -- but
    // that body is the degenerate fallback for configurations with no working
    // action bar; on a real device Activity.getActionBar() practically always
    // returns a WindowDecorActionBar (not SDK-visible, so not mirrored here
    // directly), whose setElevation/getElevation are a plain, always-succeeds
    // round trip. This file's own header comment positions our ActionBar as
    // that concrete stand-in ("we provide a concrete instance"), so round-trip
    // storage -- not the throw -- is the faithful choice: it matches what the
    // overwhelming majority of real devices do, and does not risk crashing an
    // app that sets a normal Material elevation value.
    private float mElevation;
    public void setElevation(float elevation) { mElevation = elevation; }
    public float getElevation() { return mElevation; }

    public void setBackgroundDrawable(android.graphics.drawable.Drawable d) {}
    public void setIcon(int resId) {}
    public void setIcon(android.graphics.drawable.Drawable icon) {}
    public void setLogo(int resId) {}
    public void setLogo(android.graphics.drawable.Drawable logo) {}

    public void setCustomView(android.view.View view) { mCustomView = view; }
    public void setCustomView(android.view.View view, LayoutParams layoutParams) { mCustomView = view; }
    public void setCustomView(int resId) {}
    public android.view.View getCustomView() { return mCustomView; }

    public void setNavigationMode(int mode) { mNavigationMode = mode; }
    public int getNavigationMode() { return mNavigationMode; }

    public void show() { mShowing = true; }
    public void hide() { mShowing = false; }
    public boolean isShowing() { return mShowing; }

    public int getHeight() { return 0; }
    public android.content.Context getThemedContext() { return null; }

    public void addOnMenuVisibilityListener(OnMenuVisibilityListener listener) {}
    public void removeOnMenuVisibilityListener(OnMenuVisibilityListener listener) {}

    // Deprecated tab API (NAVIGATION_MODE_TABS). Kept for old apps.
    public Tab newTab() { return new TabImpl(); }

    /** Concrete Tab backing newTab(). Records its fields; selection is a
     *  no-op (no tab chrome rendered). */
    private static final class TabImpl extends Tab {
        private int mPosition = INVALID_POSITION;
        private Object mTag;
        private android.view.View mCustom;
        private android.graphics.drawable.Drawable mIcon;
        private CharSequence mText;
        private CharSequence mContentDesc;
        private TabListener mListener;
        @Override public int getPosition() { return mPosition; }
        @Override public Object getTag() { return mTag; }
        @Override public Tab setTag(Object obj) { mTag = obj; return this; }
        @Override public Tab setCustomView(android.view.View view) { mCustom = view; return this; }
        @Override public Tab setCustomView(int layoutResId) { return this; }
        @Override public android.view.View getCustomView() { return mCustom; }
        @Override public Tab setIcon(android.graphics.drawable.Drawable icon) { mIcon = icon; return this; }
        @Override public Tab setIcon(int resId) { return this; }
        @Override public android.graphics.drawable.Drawable getIcon() { return mIcon; }
        @Override public Tab setText(CharSequence text) { mText = text; return this; }
        @Override public Tab setText(int resId) { return this; }
        @Override public CharSequence getText() { return mText; }
        @Override public Tab setTabListener(TabListener listener) { mListener = listener; return this; }
        @Override public void select() {}
        @Override public Tab setContentDescription(int resId) { return this; }
        @Override public Tab setContentDescription(CharSequence contentDesc) { mContentDesc = contentDesc; return this; }
        @Override public CharSequence getContentDescription() { return mContentDesc; }
    }
    // AOSP core/java/android/app/ActionBar.java (android13-release) tab-
    // navigation API (deprecated since API 21, but still linked by legacy
    // apps). Previously all eight of these were no-ops/null/0 stubs with no
    // backing list at all -- addTab() then getTabCount()/getTabAt() always
    // reported empty regardless of what was added.
    //
    // DEVIATION, deliberate: real AOSP's selectTab() opens a FragmentTransaction
    // and dispatches TabListener.onTabUnselected/onTabSelected through it so a
    // listener can swap fragment content as part of the same transaction. This
    // class has no Activity/FragmentManager reference to source that
    // transaction from (getThemedContext() is itself a pre-existing null stub),
    // so listener dispatch is not wired -- only the list/selection STATE that
    // getTabCount()/getTabAt()/getSelectedTab() observe. Wiring the dispatch
    // needs an ActionBar-to-Activity link that does not exist yet; flagged
    // rather than half-built.
    private final java.util.ArrayList<Tab> mTabs = new java.util.ArrayList<>();
    private Tab mSelectedTab;
    public void addTab(Tab tab) { addTab(tab, mTabs.size(), false); }
    public void addTab(Tab tab, boolean setSelected) { addTab(tab, mTabs.size(), setSelected); }
    public void addTab(Tab tab, int position) { addTab(tab, position, false); }
    public void addTab(Tab tab, int position, boolean setSelected) {
        if (tab == null) return;
        mTabs.add(position, tab);
        if (setSelected || mSelectedTab == null) mSelectedTab = tab;
    }
    public void removeTab(Tab tab) {
        if (mTabs.remove(tab) && mSelectedTab == tab) {
            mSelectedTab = mTabs.isEmpty() ? null : mTabs.get(0);
        }
    }
    public void removeTabAt(int position) {
        if (position < 0 || position >= mTabs.size()) return;
        removeTab(mTabs.get(position));
    }
    public void removeAllTabs() { mTabs.clear(); mSelectedTab = null; }
    public void selectTab(Tab tab) { if (mTabs.contains(tab)) mSelectedTab = tab; }
    public Tab getSelectedTab() { return mSelectedTab; }
    public Tab getTabAt(int index) { return index >= 0 && index < mTabs.size() ? mTabs.get(index) : null; }
    public int getTabCount() { return mTabs.size(); }

    public interface OnMenuVisibilityListener { void onMenuVisibilityChanged(boolean isVisible); }
    public interface OnNavigationListener { boolean onNavigationItemSelected(int itemPosition, long itemId); }

    public interface TabListener {
        void onTabSelected(Tab tab, FragmentTransaction ft);
        void onTabUnselected(Tab tab, FragmentTransaction ft);
        void onTabReselected(Tab tab, FragmentTransaction ft);
    }

    public static abstract class Tab {
        public static final int INVALID_POSITION = -1;
        public abstract int getPosition();
        public abstract Object getTag();
        public abstract Tab setTag(Object obj);
        public abstract Tab setCustomView(android.view.View view);
        public abstract Tab setCustomView(int layoutResId);
        public abstract android.view.View getCustomView();
        public abstract Tab setIcon(android.graphics.drawable.Drawable icon);
        public abstract Tab setIcon(int resId);
        public abstract android.graphics.drawable.Drawable getIcon();
        public abstract Tab setText(CharSequence text);
        public abstract Tab setText(int resId);
        public abstract CharSequence getText();
        public abstract Tab setTabListener(TabListener listener);
        public abstract void select();
        public abstract Tab setContentDescription(int resId);
        public abstract Tab setContentDescription(CharSequence contentDesc);
        public abstract CharSequence getContentDescription();
    }

    public static class LayoutParams extends android.view.ViewGroup.MarginLayoutParams {
        public int gravity = -1;
        public LayoutParams(android.content.Context c, android.util.AttributeSet attrs) { super(c, attrs); }
        public LayoutParams(int width, int height) { super(width, height); }
        public LayoutParams(int width, int height, int gravity) { super(width, height); this.gravity = gravity; }
        public LayoutParams(int gravity) { super(WRAP_CONTENT, WRAP_CONTENT); this.gravity = gravity; }
        public LayoutParams(LayoutParams source) { super(source); this.gravity = source.gravity; }
        public LayoutParams(android.view.ViewGroup.LayoutParams source) { super(source); }
    }

    public void setHomeActionContentDescription(int resId) { }
    public void setHomeActionContentDescription(CharSequence description) { }

    // ================================================================
    // AOSP API-36 surface completion (android.app.ActionBar).
    // AOSP ref: frameworks/base/core/java/android/app/ActionBar.java
    // (android16-release). Where AOSP declares the member abstract, the body
    // below is what AOSP's own ActionBarImpl answers for the state this class
    // is actually in.
    // ================================================================

    // ---- List navigation (NAVIGATION_MODE_LIST) ----
    // AOSP's ActionBarImpl installs the adapter into a Spinner in the action-bar
    // view and calls the listener on selection. This class has no action-bar
    // view (see the DEVIATION note above the tab list), so the STATE is kept --
    // which is what getNavigationItemCount / getSelectedNavigationIndex report,
    // and what an app reads back -- while nothing is drawn. Mastodon calls
    // setListNavigationCallbacks.

    private android.widget.SpinnerAdapter mListNavAdapter;
    private OnNavigationListener mListNavListener;
    private int mSelectedNavIndex;

    /** AOSP setListNavigationCallbacks(SpinnerAdapter, OnNavigationListener):
     *  "Set the adapter and navigation callback for list navigation mode."
     *  @deprecated matches AOSP (action-bar navigation modes are deprecated). */
    @Deprecated
    public void setListNavigationCallbacks(android.widget.SpinnerAdapter adapter,
            OnNavigationListener callback) {
        mListNavAdapter = adapter;
        mListNavListener = callback;
        if (mSelectedNavIndex >= getNavigationItemCount()) mSelectedNavIndex = 0;
    }

    /** AOSP setSelectedNavigationItem(int position): "Set the selected
     *  navigation item in list or tabbed navigation modes." AOSP's ActionBarImpl
     *  throws IllegalStateException when the current mode is neither, and
     *  dispatches the selection callback; both are reproduced.
     *  @deprecated matches AOSP. */
    @Deprecated
    public void setSelectedNavigationItem(int position) {
        switch (getNavigationMode()) {
            case NAVIGATION_MODE_TABS:
                selectTab(getTabAt(position));
                break;
            case NAVIGATION_MODE_LIST:
                mSelectedNavIndex = position;
                if (mListNavListener != null && mListNavAdapter != null
                        && position >= 0 && position < mListNavAdapter.getCount()) {
                    try {
                        mListNavListener.onNavigationItemSelected(position,
                                mListNavAdapter.getItemId(position));
                    } catch (Throwable ignored) {}
                }
                break;
            default:
                throw new IllegalStateException(
                        "setSelectedNavigationIndex not valid for current navigation mode");
        }
    }

    /** AOSP getSelectedNavigationIndex(): "Get the position of the selected
     *  navigation item in list or tabbed navigation modes", or -1 if there is
     *  none. @deprecated matches AOSP. */
    @Deprecated
    public int getSelectedNavigationIndex() {
        switch (getNavigationMode()) {
            case NAVIGATION_MODE_TABS: {
                Tab t = getSelectedTab();
                if (t == null) return -1;
                for (int i = 0; i < getTabCount(); i++) {
                    if (getTabAt(i) == t) return i;
                }
                return -1;
            }
            case NAVIGATION_MODE_LIST:
                return mListNavAdapter == null ? -1 : mSelectedNavIndex;
            default:
                return -1;
        }
    }

    /** AOSP getNavigationItemCount(): "Get the number of navigation items
     *  present in the current navigation mode." @deprecated matches AOSP. */
    @Deprecated
    public int getNavigationItemCount() {
        switch (getNavigationMode()) {
            case NAVIGATION_MODE_TABS:  return getTabCount();
            case NAVIGATION_MODE_LIST:  return mListNavAdapter == null ? 0 : mListNavAdapter.getCount();
            default:                    return 0;
        }
    }

    // ---- Hide-on-scroll / offset ----
    // AOSP's BASE class (not ActionBarImpl) implements these four, and two of
    // them THROW for any non-default request: "an action bar configuration that
    // does not support it" is precisely our configuration -- there is no
    // action-bar view to offset or to auto-hide -- so the base bodies are the
    // correct ones to mirror verbatim rather than silently accepting the call.

    /** AOSP getHideOffset(): {@code return 0;}. */
    public int getHideOffset() { return 0; }

    /** AOSP setHideOffset(int): throws UnsupportedOperationException for a
     *  non-zero offset, verbatim. */
    public void setHideOffset(int offset) {
        if (offset != 0) {
            throw new UnsupportedOperationException("Setting an explicit action bar hide offset "
                    + "is not supported in this action bar configuration.");
        }
    }

    /** AOSP setHideOnContentScrollEnabled(boolean): throws
     *  UnsupportedOperationException when enabling, verbatim. */
    public void setHideOnContentScrollEnabled(boolean hideOnContentScroll) {
        if (hideOnContentScroll) {
            throw new UnsupportedOperationException("Hide on content scroll is not supported in "
                    + "this action bar configuration.");
        }
    }

    /** AOSP isHideOnContentScrollEnabled(): {@code return false;}. */
    public boolean isHideOnContentScrollEnabled() { return false; }

    /** AOSP setStackedBackgroundDrawable(Drawable): empty body in the base class
     *  -- the stacked (tab row) background only exists in a split/stacked
     *  action-bar layout, which this configuration does not have. */
    public void setStackedBackgroundDrawable(android.graphics.drawable.Drawable d) { }

    /** AOSP setSplitBackgroundDrawable(Drawable): empty body in the base class,
     *  for the same reason. */
    public void setSplitBackgroundDrawable(android.graphics.drawable.Drawable d) { }
}
