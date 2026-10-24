// android.view.autofill.AutofillManager -- API 26+ autofill service
// stub. Compose chains check isEnabled / isAutofillSupported during
// init; null service throws "Autofill service could not be located"
// which aborts the binder chain. v1.0.98.
package android.view.autofill;

public class AutofillManager {
    public AutofillManager() {}

    /** Inner AutofillCallback. Compose's AndroidComposeView.onAttachedToWindow
     *  loads its class even if it never instantiates one. */
    public static abstract class AutofillCallback {
        public static final int EVENT_INPUT_HIDDEN  = 2;
        public static final int EVENT_INPUT_SHOWN   = 1;
        public static final int EVENT_INPUT_UNAVAILABLE = 3;
        public AutofillCallback() {}
        public void onAutofillEvent(android.view.View view, int event) {}
        public void onAutofillEvent(android.view.View view, int virtualId, int event) {}
    }
    public boolean isEnabled() { return false; }
    public boolean isAutofillSupported() { return false; }
    public void requestAutofill(android.view.View view) {}
    // AOSP requestAutofill(View, int, Rect): the virtual-view overload, for a
    // child the parent draws itself into its own Canvas (Compose text fields,
    // WebView nodes, virtualized editable lists). It is a DIFFERENT method from
    // the 1-arg form, not a convenience wrapper, so the 1-arg form alone left
    // every such call site with NoSuchMethodError. Real signature per
    // frameworks/base/core/java/android/view/autofill/AutofillManager.java.
    public void requestAutofill(android.view.View view, int virtualId,
                                android.graphics.Rect absBounds) {}
    public void cancel() {}
    public void commit() {}
    public void notifyValueChanged(android.view.View view) {}
    // AOSP types the third parameter AutofillValue, not Object. Ours took
    // Object, so the descriptor an app actually emits
    // (Landroid/view/View;ILandroid/view/autofill/AutofillValue;)V did not
    // resolve -> NoSuchMethodError; JVM method resolution is by exact
    // descriptor, so a widening Object parameter never matches.
    public void notifyValueChanged(android.view.View view, int virtualId,
                                   AutofillValue value) {}
    public void notifyViewEntered(android.view.View view) {}
    public void notifyViewExited(android.view.View view) {}
    // Compose's AndroidComposeView calls this from its measure/layout pass to
    // report node visibility to autofill; a missing method threw NoSuchMethodError
    // in the measure pass. No autofill service -> inert. v1.0.429.
    public void notifyViewVisibilityChanged(android.view.View view, int virtualId, boolean isVisible) {}
    public void notifyViewEntered(android.view.View view, int virtualId, android.graphics.Rect bounds) {}
    public void notifyViewExited(android.view.View view, int virtualId) {}
    public boolean hasEnabledAutofillServices() { return false; }
    public void registerCallback(AutofillCallback callback) {}
    public void unregisterCallback(AutofillCallback callback) {}

    // ======================================================================
    //  AOSP API-36 surface completion (Mac.apk API-gap lane, android.view).
    //
    //  Mac.apk bridges NO autofill service. isEnabled() and
    //  hasEnabledAutofillServices() above already say so honestly, and every
    //  answer below is the one AOSP gives when no service is bound -- a real,
    //  common device state (it is what every device reports before the user
    //  picks an autofill provider), not a stand-in for "unimplemented".
    // ======================================================================

    /** AOSP AutofillManager.getAutofillServiceComponentName(): the currently
     *  bound service, or null when there is none. */
    public android.content.ComponentName getAutofillServiceComponentName() { return null; }

    /** AOSP AutofillManager.disableAutofillServices(): turn autofill off for
     *  this app. Nothing is enabled, so there is nothing to disable. */
    public void disableAutofillServices() {}

    /** AOSP AutofillManager.getNextAutofillId(): mint a fresh AutofillId for a
     *  view the app is about to create. AOSP allocates from the session's id
     *  space and returns NULL when there is no session -- which is the state
     *  here, since no service is bound. */
    public android.view.autofill.AutofillId getNextAutofillId() { return null; }

    /** AOSP AutofillManager.isFieldClassificationEnabled(): whether the bound
     *  service asked for field classification. There is no bound service. */
    public boolean isFieldClassificationEnabled() { return false; }

    /** AOSP AutofillManager.getAvailableFieldClassificationAlgorithms() /
     *  getDefaultFieldClassificationAlgorithm(): what the SERVICE offers. With
     *  no service, AOSP returns an empty list and null respectively. */
    public java.util.List<String> getAvailableFieldClassificationAlgorithms() {
        return java.util.Collections.emptyList();
    }
    public String getDefaultFieldClassificationAlgorithm() { return null; }

    /** AOSP AutofillManager.getUserDataId(): the id of the UserData the bound
     *  service set for field classification. Null with no service. */
    public String getUserDataId() { return null; }

    /** AOSP AutofillManager.notifyViewClicked(View) / (View, int virtualId)
     *  (API 34): tell the session the user clicked a (possibly virtual) view.
     *  No session exists, so AOSP's own "no session" early-out applies. */
    public void notifyViewClicked(android.view.View view) {}
    public void notifyViewClicked(android.view.View view, int virtualId) {}

    /** AOSP AutofillManager.notifyViewVisibilityChanged(View, boolean): the
     *  non-virtual form of the (View, int, boolean) overload already above. */
    public void notifyViewVisibilityChanged(android.view.View view, boolean isVisible) {}

    /** AOSP AutofillManager.notifyVirtualViewsReady(View, SparseArray) (API 34,
     *  deprecated at 36): a host view reports the autofill hints of its virtual
     *  children now that they exist. No session, nothing to report to. */
    @Deprecated
    public void notifyVirtualViewsReady(android.view.View view,
            android.util.SparseArray<VirtualViewFillInfo> infos) {}

    /** AOSP AutofillManager.showAutofillDialog(View) / (View, int) (API 33,
     *  deprecated at 36): ask the bound service to show its fill dialog. AOSP
     *  returns FALSE when the dialog was not shown, which is the case with no
     *  service bound. */
    @Deprecated
    public boolean showAutofillDialog(android.view.View view) { return false; }
    @Deprecated
    public boolean showAutofillDialog(android.view.View view, int virtualId) { return false; }

    // ⚠ AOSP's getUserData() / setUserData(UserData) are NOT implemented, and
    // deliberately: both name android.service.autofill.UserData, which does not
    // exist in this tree and lives outside this lane's directory
    // (runtime/java/android/service/autofill/). Declaring them with Object in
    // place of the real type would NOT close the gap -- a caller invokes the
    // exact AOSP descriptor, so an Object-typed method is a different method and
    // still misses. Add the type first, then these two (getUserData returns null
    // and setUserData is a no-op, since no service is bound).
}
