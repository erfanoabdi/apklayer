// android.widget.TabHost -- the container that coordinates a TabWidget (the
// row of tabs) with a FrameLayout (the tab content). Was a bare `extends
// Object` stub (broke instanceof FrameLayout and any TabHost inflate). Now the
// real FrameLayout with addTab/setCurrentTab/TabSpec and content-switching via
// TabContentFactory or a content view id. AOSP:
//   frameworks/base/core/java/android/widget/TabHost.java
//   public class TabHost extends FrameLayout implements ViewTreeObserver.OnTouchModeChangeListener
package android.widget;

import android.content.Context;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class TabHost extends FrameLayout
        implements android.view.ViewTreeObserver.OnTouchModeChangeListener {
    public interface OnTabChangeListener {
        void onTabChanged(String tabId);
    }
    public interface TabContentFactory {
        View createTabContent(String tag);
    }

    private TabWidget mTabWidget;
    private FrameLayout mTabContent;
    private final List<TabSpec> mTabSpecs = new ArrayList<TabSpec>();
    private int mCurrentTab = -1;
    private View mCurrentView;
    private OnTabChangeListener mOnTabChangeListener;

    public TabHost(Context context) { super(context); }
    public TabHost(Context context, android.util.AttributeSet attrs) { super(context, attrs); }
    /** AOSP API 21 four-argument constructor. AOSP's three-argument form is
     *  exactly this(context, attrs, defStyleAttr, 0), so delegating the other
     *  way is identical for defStyleRes == 0; a non-zero defStyleRes is
     *  DROPPED here, as in the existing FrameLayout/Button four-argument forms
     *  (TextView, ProgressBar and their subclasses honour it). Without it an
     *  app subclass calling the four-argument
     *  super() dies with NoSuchMethodError; see debug/ctorsdk_gate.sh. */
    public TabHost(Context context, android.util.AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        this(context, attrs, defStyleAttr);
    }
    public TabHost(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    // AOSP setup() wires the standard child ids (tabs / tabcontent).
    public void setup() {
        mTabWidget = (TabWidget) findViewById(0x01020013); // android.R.id.tabs
        if (mTabWidget != null) {
            mTabWidget.setTabSelectionListener(new TabWidget.OnTabSelectionChanged() {
                @Override public void onTabSelectionChanged(int tabIndex, boolean clicked) {
                    setCurrentTab(tabIndex);
                }
            });
        }
        mTabContent = (FrameLayout) findViewById(0x01020011); // android.R.id.tabcontent
    }

    public TabWidget getTabWidget() { return mTabWidget; }
    public FrameLayout getTabContentView() { return mTabContent; }

    public TabSpec newTabSpec(String tag) { return new TabSpec(tag); }

    public void addTab(TabSpec tabSpec) {
        if (tabSpec.mIndicatorStrategy == null) {
            throw new IllegalArgumentException("you must specify a way to create the tab indicator.");
        }
        if (tabSpec.mContentStrategy == null) {
            throw new IllegalArgumentException("you must specify a way to create the tab content");
        }
        View tabIndicator = tabSpec.mIndicatorStrategy.createIndicatorView();
        if (mTabWidget != null && tabIndicator != null) {
            mTabWidget.addView(tabIndicator);
        }
        mTabSpecs.add(tabSpec);
        if (mCurrentTab == -1) setCurrentTab(0);
    }

    public void clearAllTabs() {
        if (mTabWidget != null) mTabWidget.removeAllViews();
        if (mTabContent != null) mTabContent.removeAllViews();
        mTabSpecs.clear();
        mCurrentTab = -1;
        mCurrentView = null;
    }

    public int getCurrentTab() { return mCurrentTab; }
    public String getCurrentTabTag() {
        return (mCurrentTab >= 0 && mCurrentTab < mTabSpecs.size())
                ? mTabSpecs.get(mCurrentTab).mTag : null;
    }
    public View getCurrentTabView() {
        return mTabWidget != null ? mTabWidget.getChildTabViewAt(mCurrentTab) : null;
    }
    public View getCurrentView() { return mCurrentView; }

    public void setCurrentTab(int index) {
        if (index < 0 || index >= mTabSpecs.size() || index == mCurrentTab) {
            if (index == mCurrentTab) return;
        }
        if (index < 0 || index >= mTabSpecs.size()) return;

        if (mCurrentView != null && mTabContent != null) {
            mTabContent.removeView(mCurrentView);
        }
        mCurrentTab = index;
        TabSpec spec = mTabSpecs.get(index);
        mCurrentView = spec.mContentStrategy.getContentView();
        if (mTabContent != null && mCurrentView != null) {
            if (mCurrentView.getParent() == null) {
                mTabContent.addView(mCurrentView);
            }
            mCurrentView.setVisibility(View.VISIBLE);
        }
        if (mTabWidget != null) mTabWidget.setCurrentTab(index);
        if (mOnTabChangeListener != null) {
            mOnTabChangeListener.onTabChanged(getCurrentTabTag());
        }
    }

    public void setCurrentTabByTag(String tag) {
        for (int i = 0; i < mTabSpecs.size(); i++) {
            if (mTabSpecs.get(i).mTag.equals(tag)) { setCurrentTab(i); return; }
        }
    }

    public void setOnTabChangedListener(OnTabChangeListener l) { mOnTabChangeListener = l; }

    // ---- TabSpec + strategies (mirrors AOSP's private strategy interfaces) ----
    private interface IndicatorStrategy { View createIndicatorView(); }
    private interface ContentStrategy { View getContentView(); void tabClosed(); }

    static final boolean MACAPK_TABHOST_LABEL_APPEARANCE =
        !"0".equals(System.getenv("MACAPK_TABHOST_LABEL_APPEARANCE"));

    /** An AttributeSet holding one layout element's android: attributes, so a
     *  stand-in for a platform layout we cannot inflate hands its widget the
     *  same constructor input the layout would. Null (the bare constructor) if
     *  the parser is unavailable. */
    private static android.util.AttributeSet macapkElementAttrs(String androidAttrs) {
        try {
            org.xmlpull.v1.XmlPullParser p = android.util.Xml.newPullParser();
            p.setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
            p.setInput(new java.io.StringReader("<TextView xmlns:android="
                + "\"http://schemas.android.com/apk/res/android\" " + androidAttrs + "/>"));
            while (p.next() != org.xmlpull.v1.XmlPullParser.START_TAG) { }
            return android.util.Xml.asAttributeSet(p);
        } catch (Throwable t) {
            return null;
        }
    }

    public class TabSpec {
        private final String mTag;
        private IndicatorStrategy mIndicatorStrategy;
        private ContentStrategy mContentStrategy;

        private TabSpec(String tag) { mTag = tag; }

        public String getTag() { return mTag; }

        public TabSpec setIndicator(final CharSequence label) {
            mIndicatorStrategy = new IndicatorStrategy() {
                @Override public View createIndicatorView() {
                    // AOSP LabelIndicatorStrategy (android14-release TabHost.java
                    // :643-667) inflates the TabWidget's tabLayout
                    // (Widget.Material.TabWidget -> layout/tab_indicator_material.xml)
                    // and sets the label on its title TextView, which carries
                    // style="@android:style/Widget.Material.TabText" ->
                    // Widget.Material.ActionBar.TabText (styles_material.xml
                    // :997-1001): textAppearance TextAppearance.Material.Widget.TabWidget
                    // (the Button appearance, 14sp medium all-caps, with
                    // @color/tab_indicator_text_material), ellipsize marquee,
                    // maxLines 2. Compiled platform layouts are not readable yet, so
                    // the label is built with that appearance as its element
                    // attribute; a bare `new TextView(ctx)` takes textViewStyle's
                    // grey small appearance instead. Exact for Material themes
                    // only (Holo / classic use tab_indicator_holo / tab_indicator).
                    // MACAPK_TABHOST_LABEL_APPEARANCE=0 restores the bare view.
                    TextView tv = new TextView(getContext(), MACAPK_TABHOST_LABEL_APPEARANCE
                        ? macapkElementAttrs(
                            "android:textAppearance=\"@android:style/TextAppearance.Material.Widget.TabWidget\"")
                        : null);
                    if (MACAPK_TABHOST_LABEL_APPEARANCE) {
                        tv.setMaxLines(2);
                        tv.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
                    }
                    tv.setText(label);
                    return tv;
                }
            };
            return this;
        }

        public TabSpec setIndicator(final CharSequence label,
                final android.graphics.drawable.Drawable icon) {
            return setIndicator(label);
        }

        public TabSpec setIndicator(final View view) {
            mIndicatorStrategy = new IndicatorStrategy() {
                @Override public View createIndicatorView() { return view; }
            };
            return this;
        }

        public TabSpec setContent(final int viewId) {
            mContentStrategy = new ContentStrategy() {
                @Override public View getContentView() {
                    View v = mTabContent != null ? mTabContent.findViewById(viewId) : null;
                    if (v != null) v.setVisibility(View.VISIBLE);
                    return v;
                }
                @Override public void tabClosed() {}
            };
            return this;
        }

        public TabSpec setContent(final TabContentFactory contentFactory) {
            mContentStrategy = new ContentStrategy() {
                private View mView;
                @Override public View getContentView() {
                    if (mView == null) mView = contentFactory.createTabContent(mTag);
                    if (mView != null) mView.setVisibility(View.VISIBLE);
                    return mView;
                }
                @Override public void tabClosed() {
                    if (mView != null) mView.setVisibility(View.GONE);
                }
            };
            return this;
        }

        public TabSpec setContent(final android.content.Intent intent) {
            mContentStrategy = new ContentStrategy() {
                @Override public View getContentView() { return new View(getContext()); }
                @Override public void tabClosed() {}
            };
            return this;
        }
    }

    // ---- API-36 surface completion ------------------------------------
    // AOSP declares `class TabHost extends FrameLayout implements
    // ViewTreeObserver.OnTouchModeChangeListener`; the interface and its one
    // method were missing, so a TabHost could not be registered on a
    // ViewTreeObserver at all.
    /** AOSP TabHost :228 -- the body is a comment: "No longer used, but kept to
     *  maintain API compatibility." Mirroring it exactly. */
    @Override
    public void onTouchModeChanged(boolean isInTouchMode) { }
}
