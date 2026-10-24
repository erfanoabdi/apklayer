// android.widget.AdapterViewAnimator -- an AdapterView that shows ONE adapter
// item at a time and animates between them (base of AdapterViewFlipper /
// StackView). Was Absent. We honor the displayed-child model + adapter; the
// in/out animations are stored but switching is instant. AOSP:
//   frameworks/base/core/java/android/widget/AdapterViewAnimator.java
//   public abstract class AdapterViewAnimator extends AdapterView<Adapter>
//       implements RemoteViewsAdapter.RemoteAdapterConnectionCallback, Advanceable
package android.widget;

import android.animation.AnimatorInflater;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.database.DataSetObserver;
import android.view.View;

public class AdapterViewAnimator extends AdapterView<Adapter> implements Advanceable {
    Adapter mAdapter;
    int mWhichChild = 0;
    private View mCurrentView;
    private ObjectAnimator mInAnimation;
    private ObjectAnimator mOutAnimation;
    private boolean mLoopViews = true;

    private final DataSetObserver mObserver = new DataSetObserver() {
        @Override public void onChanged() { refreshChild(); }
        @Override public void onInvalidated() { refreshChild(); }
    };

    public AdapterViewAnimator(Context context) { super(context); }
    public AdapterViewAnimator(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
    }
    /** AOSP API 21 four-argument constructor. AOSP's three-argument form is
     *  exactly this(context, attrs, defStyleAttr, 0), so delegating the other
     *  way is identical for defStyleRes == 0; a non-zero defStyleRes is
     *  DROPPED here, as in the existing FrameLayout/Button four-argument forms
     *  (TextView, ProgressBar and their subclasses honour it). Without it an
     *  app subclass calling the four-argument
     *  super() dies with NoSuchMethodError; see debug/ctorsdk_gate.sh. */
    public AdapterViewAnimator(Context context, android.util.AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        this(context, attrs, defStyleAttr);
    }
    public AdapterViewAnimator(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override public Adapter getAdapter() { return mAdapter; }
    @Override public void setAdapter(Adapter adapter) {
        if (mAdapter != null) {
            try { mAdapter.unregisterDataSetObserver(mObserver); } catch (Throwable ignored) {}
        }
        mAdapter = adapter;
        this.adapter = adapter;
        if (adapter != null) {
            try { adapter.registerDataSetObserver(mObserver); } catch (Throwable ignored) {}
        }
        mWhichChild = 0;
        refreshChild();
    }

    public void setRemoteViewsAdapter(android.content.Intent intent) {}

    @Override public int getCount() { return mAdapter != null ? mAdapter.getCount() : 0; }

    public void setDisplayedChild(int whichChild) {
        if (mAdapter == null) return;
        int count = mAdapter.getCount();
        if (count == 0) return;
        if (whichChild >= count) whichChild = mLoopViews ? 0 : count - 1;
        else if (whichChild < 0) whichChild = mLoopViews ? count - 1 : 0;
        mWhichChild = whichChild;
        refreshChild();
    }
    public int getDisplayedChild() { return mWhichChild; }

    public void showNext() { setDisplayedChild(mWhichChild + 1); }
    public void showPrevious() { setDisplayedChild(mWhichChild - 1); }

    private void refreshChild() {
        removeAllViewsInLayout();
        if (mAdapter == null || mAdapter.getCount() == 0) { mCurrentView = null; return; }
        if (mWhichChild >= mAdapter.getCount()) mWhichChild = 0;
        mCurrentView = mAdapter.getView(mWhichChild, null, this);
        if (mCurrentView != null) {
            if (mCurrentView.getLayoutParams() == null) {
                mCurrentView.setLayoutParams(new android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT));
            }
            addViewInLayout(mCurrentView, 0, mCurrentView.getLayoutParams());
        }
        requestLayout();
        invalidate();
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (mCurrentView != null) {
            mCurrentView.layout(getPaddingLeft(), getPaddingTop(),
                    getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());
        }
    }

    @Override public View getSelectedView() { return mCurrentView; }
    public View getCurrentView() { return mCurrentView; }

    // AOSP frameworks/base core/java/android/widget/AdapterViewAnimator.java
    // (android15-release, ~lines 873-936): the in/out animation pair is typed
    // ObjectAnimator, not Animation -- AdapterViewAnimator predates the
    // Animation-framework subclasses used by ViewAnimator/ViewFlipper and was
    // built on the property-animation API from the start. A wrong return/param
    // type here is a JVM descriptor mismatch (getInAnimation()Landroid/view/
    // animation/Animation; vs the real ()Landroid/animation/ObjectAnimator;), so
    // any guest bytecode calling this the real way never binds to ours at all.
    // Found by debug/framework_lint.py's class-shape diff against android.jar.
    public ObjectAnimator getInAnimation() { return mInAnimation; }
    public void setInAnimation(ObjectAnimator inAnimation) { mInAnimation = inAnimation; }
    public ObjectAnimator getOutAnimation() { return mOutAnimation; }
    public void setOutAnimation(ObjectAnimator outAnimation) { mOutAnimation = outAnimation; }
    public void setInAnimation(Context context, int resourceID) {
        setInAnimation((ObjectAnimator) AnimatorInflater.loadAnimator(context, resourceID));
    }
    public void setOutAnimation(Context context, int resourceID) {
        setOutAnimation((ObjectAnimator) AnimatorInflater.loadAnimator(context, resourceID));
    }
    public void setAnimateFirstView(boolean animate) {}

    @Override public void advance() { showNext(); }
    @Override public void fyiWillBeAdvancedByHostKThx() {}

    // ---- API-36 surface completion ------------------------------------
    // AOSP AdapterViewAnimator implements
    // RemoteViewsAdapter.RemoteAdapterConnectionCallback; the three callbacks
    // below were missing. There is no RemoteViewsAdapter in this runtime (it
    // binds to a RemoteViewsService across a Binder we do not have), so
    // setRemoteViewsAdapter above produces none -- which puts these on AOSP's
    // own null-adapter paths. AOSP:
    // frameworks/base/core/java/android/widget/AdapterViewAnimator.java
    // (android16-release) :1046 / :1053 / :1078.
    private boolean mDeferNotifyDataSetChanged;

    /** AOSP :1046 -- verbatim. */
    public void deferNotifyDataSetChanged() { mDeferNotifyDataSetChanged = true; }

    /** AOSP :1053 -- returns false when the adapter was newly swapped in. With
     *  no remote adapter there is nothing to swap, so the deferred notify is
     *  flushed against the adapter we do have and AOSP's `return false` stands. */
    public boolean onRemoteAdapterConnected() {
        if (mDeferNotifyDataSetChanged) {
            mDeferNotifyDataSetChanged = false;
            if (mAdapter instanceof BaseAdapter) ((BaseAdapter) mAdapter).notifyDataSetChanged();
        }
        return false;
    }

    /** AOSP :1078 -- the body is a comment and nothing else: the adapter is
     *  deliberately kept so the cached items stay displayed. */
    public void onRemoteAdapterDisconnected() { }
}
