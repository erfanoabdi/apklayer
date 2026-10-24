// android.widget.TabWidget -- the horizontal strip of tab buttons used by
// TabHost. Was a bare `extends Object` stub (broke instanceof LinearLayout and
// any TabHost inflate). Now the real horizontal LinearLayout with the
// tab-selection surface. AOSP:
//   frameworks/base/core/java/android/widget/TabWidget.java
//   public class TabWidget extends LinearLayout implements OnFocusChangeListener
package android.widget;

import android.content.Context;
import android.view.View;

public class TabWidget extends LinearLayout
        implements android.view.View.OnFocusChangeListener {
    public interface OnTabSelectionChanged {
        void onTabSelectionChanged(int tabIndex, boolean clicked);
    }

    private OnTabSelectionChanged mSelectionChangedListener;
    private int mSelectedTab = 0;
    private boolean mStripEnabled = true;

    public TabWidget(Context context) { super(context); init(); }
    public TabWidget(Context context, android.util.AttributeSet attrs) {
        super(context, attrs); init();
    }
    /** AOSP API 21 four-argument constructor. AOSP's three-argument form is
     *  exactly this(context, attrs, defStyleAttr, 0), so delegating the other
     *  way is identical for defStyleRes == 0; a non-zero defStyleRes is
     *  DROPPED here, as in the existing FrameLayout/Button four-argument forms
     *  (TextView, ProgressBar and their subclasses honour it). Without it an
     *  app subclass calling the four-argument
     *  super() dies with NoSuchMethodError; see debug/ctorsdk_gate.sh. */
    public TabWidget(Context context, android.util.AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        this(context, attrs, defStyleAttr);
    }
    public TabWidget(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr); init();
    }

    private void init() { setOrientation(HORIZONTAL); }

    public View getChildTabViewAt(int index) { return getChildAt(index); }
    public int  getTabCount() { return getChildCount(); }

    public void addView(View child) {
        super.addView(child);
        final int index = getChildCount() - 1;
        child.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (mSelectionChangedListener != null) {
                    mSelectionChangedListener.onTabSelectionChanged(index, true);
                }
            }
        });
    }

    public void setCurrentTab(int index) {
        if (index < 0 || index >= getTabCount()) return;
        if (mSelectedTab >= 0 && mSelectedTab < getTabCount()) {
            getChildTabViewAt(mSelectedTab).setSelected(false);
        }
        mSelectedTab = index;
        getChildTabViewAt(mSelectedTab).setSelected(true);
    }

    public void focusCurrentTab(int index) { setCurrentTab(index); }

    public void setTabSelectionListener(OnTabSelectionChanged listener) {
        mSelectionChangedListener = listener;
    }

    public void setStripEnabled(boolean stripEnabled) { mStripEnabled = stripEnabled; }
    public boolean isStripEnabled() { return mStripEnabled; }
    // AOSP TabWidget.setLeftStripDrawable/:311 getLeftStripDrawable and the
    // right-hand pair store mLeftStrip/mRightStrip; the four setters here used
    // to discard, so the (previously missing) getters had nothing to read. The
    // strips are not painted by this port, so this is state only.
    public void setLeftStripDrawable(android.graphics.drawable.Drawable d) { mLeftStrip = d; }
    public void setLeftStripDrawable(int resId) {
        setLeftStripDrawable(getContext().getDrawable(resId));
    }
    public void setRightStripDrawable(android.graphics.drawable.Drawable d) { mRightStrip = d; }
    public void setRightStripDrawable(int resId) {
        setRightStripDrawable(getContext().getDrawable(resId));
    }
    /** AOSP TabWidget (android16-release core/java/android/widget/TabWidget.java
     *  :261-264) exists ONLY to re-document the inherited method:
     *
     *      @Override
     *      public void setDividerDrawable(@Nullable Drawable drawable) {
     *          super.setDividerDrawable(drawable);
     *      }
     *
     *  Ours had an EMPTY body, which is not a documentation override but a
     *  silent one: it shadowed LinearLayout.setDividerDrawable's real 36-line
     *  implementation, so a TabWidget's `android:divider` was accepted and
     *  discarded and no tab divider could ever paint. Found by
     *  `debug/stubscan.py` (a no-op override of a method whose superclass
     *  assigns state -- the same shape as the ImageView/TextView setFrame bug
     *  fixed earlier today). */
    @Override
    public void setDividerDrawable(android.graphics.drawable.Drawable d) {
        super.setDividerDrawable(d);
    }

    // ---- API-36 surface completion ------------------------------------
    // AOSP declares `class TabWidget extends LinearLayout implements
    // OnFocusChangeListener` -- the interface itself was missing here, so
    // TabHost's `instanceof` / listener wiring could never see a TabWidget as
    // a focus listener.
    private android.graphics.drawable.Drawable mLeftStrip;
    private android.graphics.drawable.Drawable mRightStrip;

    /** AOSP :311 / :350. */
    public android.graphics.drawable.Drawable getLeftStripDrawable()  { return mLeftStrip; }
    public android.graphics.drawable.Drawable getRightStripDrawable() { return mRightStrip; }

    /** AOSP :581 -- the body is a comment: "No-op. Tab selection is separate
     *  from keyboard focus." Mirroring it exactly. */
    @Override
    public void onFocusChange(android.view.View v, boolean hasFocus) { }
}
