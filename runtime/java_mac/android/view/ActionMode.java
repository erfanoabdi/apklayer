// android.view.ActionMode -- AOSP-style transient contextual action
// bar. AppCompat exposes one for "selection" UIs (multi-select in a
// list). Mac.apk stubs the API surface so AppCompat code links.
package android.view;

public abstract class ActionMode {
    public interface Callback {
        boolean onCreateActionMode(ActionMode mode, Menu menu);
        boolean onPrepareActionMode(ActionMode mode, Menu menu);
        boolean onActionItemClicked(ActionMode mode, MenuItem item);
        void onDestroyActionMode(ActionMode mode);
    }
    /** API 23+ floating-toolbar callback. AOSP verbatim: default
     *  onGetContentRect reports the whole view as the content rect.
     *  TextView subclasses (e.g. Calculator's formula view) extend
     *  this for custom selection toolbars. */
    public static abstract class Callback2 implements ActionMode.Callback {
        public void onGetContentRect(ActionMode mode, View view,
                                     android.graphics.Rect outRect) {
            if (view != null) {
                outRect.set(0, 0, view.getWidth(), view.getHeight());
            } else {
                outRect.set(0, 0, 0, 0);
            }
        }
    }
    public abstract void finish();
    public abstract void invalidate();
    public abstract void setTitle(CharSequence title);
    public abstract void setTitle(int resId);
    public abstract void setSubtitle(CharSequence subtitle);
    public abstract void setSubtitle(int resId);
    public abstract void setCustomView(View view);
    public abstract CharSequence getTitle();
    public abstract CharSequence getSubtitle();
    public abstract View getCustomView();
    public abstract Menu getMenu();
    public abstract MenuInflater getMenuInflater();
    public Object getTag() { return null; }
    public void setTag(Object tag) {}
    // AOSP: `public boolean getTitleOptionalHint()` -- it reports the HINT flag set
    // by setTitleOptionalHint(boolean), not a title string. Returning CharSequence
    // was a wrong descriptor for a name that reads like a getter for getTitle().
    // frameworks/base/core/java/android/view/ActionMode.java
    public boolean getTitleOptionalHint() { return mTitleOptionalHint; }
    private boolean mTitleOptionalHint;
    public void setTitleOptionalHint(boolean titleOptional) { mTitleOptionalHint = titleOptional; }
    public boolean isTitleOptional() { return false; }
    public void setType(int type) {}
    public int  getType() { return 0; }
    public static final int TYPE_PRIMARY = 0;
    public static final int TYPE_FLOATING = 1;

    public void invalidateContentRect() { }

    /** AOSP ActionMode.hide(long): the base class body is a literal no-op.
     *  core/java/android/view/ActionMode.java (android11-release, :217-228):
     *    "Hide the action mode view from obstructing the content below for a
     *    short duration. This only makes sense for action modes that support
     *    dynamic positioning on the screen. If this method is called again
     *    before the hide duration expires, the later hide call will cancel
     *    the former and then take effect."
     *      public void hide(long duration) {}
     *  A visible effect only appears in a concrete subclass with on-screen
     *  positioning (the TYPE_FLOATING selection toolbar), which schedules a
     *  timed re-show. Mac.apk has no such positioning system for ActionMode,
     *  so the AOSP base no-op is the faithful behaviour here, not a stand-in
     *  for a missing one.
     *
     *  Was entirely absent, so any concrete ActionMode reaching this call
     *  threw NoSuchMethodError. Confirmed via Telegram's
     *  TextSelectionHelper$$ExternalSyntheticApiModelOutline7.m(ActionMode,
     *  long) -> invokevirtual ActionMode.hide:(J)V, the selection floating
     *  toolbar's own hide-during-scroll call. */
    public void hide(long duration) {}

    /** AOSP ActionMode.onWindowFocusChanged(boolean) (API 24): the window
     *  hosting this action mode gained or lost focus, so a floating toolbar can
     *  hide itself. AOSP's base-class body is empty -- only
     *  FloatingActionMode overrides it. */
    public void onWindowFocusChanged(boolean hasWindowFocus) {}
}
