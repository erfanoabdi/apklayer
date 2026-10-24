// android.view.ActionProvider -- AOSP's "rich menu interaction in a single
// component" base class: it builds the action view a MenuItem shows in the
// ActionBar, can own a submenu, can perform a default action, and can drive
// the bound MenuItem's visibility.
//
// AOSP ref: frameworks/base/core/java/android/view/ActionProvider.java
//   public abstract class ActionProvider
//   ActionProvider(Context) is the ONLY constructor upstream, because menu
//   XML inflation of android:actionProviderClass reflectively looks up the
//   single-Context constructor. Every real subclass (ShareActionProvider and
//   every app-authored provider) therefore opens with super(context), which
//   is why the previous no-arg-only stub made those subclasses unusable at
//   construction time, not merely unwired.
//
// v1.0.502: filled in the whole AOSP behavioral surface (both
// onCreateActionView overloads, the visibility protocol, the submenu hooks,
// and the SubUiVisibilityListener / VisibilityListener nested interfaces --
// ActionProvider$VisibilityListener alone is referenced by 23 of the 37
// corpus apps).
package android.view;

import android.content.Context;
import android.util.Log;

public abstract class ActionProvider {
    private static final String TAG = "ActionProvider";
    private SubUiVisibilityListener mSubUiVisibilityListener;
    private VisibilityListener mVisibilityListener;

    /** AOSP's only constructor. Subclasses must implement it too so menu XML
     *  inflation can construct them from a Context. */
    public ActionProvider(Context context) {
    }

    /** NOT part of the Android API surface. Kept solely because
     *  android.widget.ShareActionProvider still opens with super(); that file
     *  is outside this change's scope. Remove this constructor once that call
     *  site becomes super(context). */
    public ActionProvider() {
    }

    /**
     * AOSP factory method for new action views. Upstream this is abstract
     * (and @NonNull); we leave it concrete for the same reason as the no-arg
     * constructor above -- android.widget.ShareActionProvider does not
     * implement it yet. No app is affected: an app-authored subclass compiled
     * against the real android.jar always supplies an override, since
     * upstream would not let it compile otherwise.
     *
     * @deprecated use {@link #onCreateActionView(MenuItem)}
     */
    @Deprecated
    public View onCreateActionView() {
        return null;
    }

    /** AOSP: the API-16+ overload. The default delegates to the deprecated
     *  no-argument form so providers written for older platforms still work. */
    public View onCreateActionView(MenuItem forItem) {
        return onCreateActionView();
    }

    /** AOSP: whether {@link #isVisible()} participates in the bound MenuItem's
     *  visibility at all. Default false. */
    public boolean overridesItemVisibility() {
        return false;
    }

    /** AOSP: consulted only when {@link #overridesItemVisibility()} is true.
     *  An item the app explicitly hid stays hidden regardless. Default true. */
    public boolean isVisible() {
        return true;
    }

    /** AOSP: push the current visibility to the bound MenuItem. A no-op unless
     *  a VisibilityListener is set AND this provider overrides visibility. */
    public void refreshVisibility() {
        if (mVisibilityListener != null && overridesItemVisibility()) {
            mVisibilityListener.onActionProviderVisibilityChanged(isVisible());
        }
    }

    /** AOSP: the last stop in the menu-selection chain (after the item's click
     *  listener, Activity/Fragment onOptionsItemSelected, and the item's
     *  Intent). Default does nothing and returns false. */
    public boolean onPerformDefaultAction() {
        return false;
    }

    /** AOSP: whether the backing item should have an associated submenu.
     *  Default false. */
    public boolean hasSubMenu() {
        return false;
    }

    /** AOSP: called between {@link #onPerformDefaultAction()} and showing the
     *  submenu, so providers can populate it just before display. */
    public void onPrepareSubMenu(SubMenu subMenu) {
    }

    /** AOSP @hide: tell the system an action view's anchored popup opened or
     *  closed, which changes how other visibility notifications behave. */
    public void subUiVisibilityChanged(boolean isVisible) {
        if (mSubUiVisibilityListener != null) {
            mSubUiVisibilityListener.onSubUiVisibilityChanged(isVisible);
        }
    }

    /** AOSP @hide: internal use only. */
    public void setSubUiVisibilityListener(SubUiVisibilityListener listener) {
        mSubUiVisibilityListener = listener;
    }

    /** AOSP: set by the MenuItem implementation the provider is bound to.
     *  Upstream logs when one is replaced while another is still live, because
     *  that means the same provider instance is being shared by two items. */
    public void setVisibilityListener(VisibilityListener listener) {
        if (mVisibilityListener != null) {
            Log.w(TAG, "setVisibilityListener: Setting a new ActionProvider.VisibilityListener " +
                    "when one is already set. Are you reusing this " + getClass().getSimpleName() +
                    " instance while it is still in use somewhere else?");
        }
        mVisibilityListener = listener;
    }

    /** AOSP @hide: drop both listeners so the provider can be rebound. */
    public void reset() {
        mVisibilityListener = null;
        mSubUiVisibilityListener = null;
    }

    /** AOSP @hide: internal use only. */
    public interface SubUiVisibilityListener {
        public void onSubUiVisibilityChanged(boolean isVisible);
    }

    /** AOSP: notified from {@link ActionProvider#refreshVisibility()}. Only
     *  MenuItem implementations should register one. */
    public interface VisibilityListener {
        public void onActionProviderVisibilityChanged(boolean isVisible);
    }
}
