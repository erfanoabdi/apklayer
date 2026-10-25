// android.view.accessibility.AccessibilityRequestPreparer -- lets a view do
// asynchronous work (start rendering an off-screen page, say) BEFORE an
// accessibility service's extra-data request is answered.
// Source: frameworks/base
// core/java/android/view/accessibility/AccessibilityRequestPreparer.java
// (android16-release).
//
// Mac.apk bridges no accessibility service, so AccessibilityManager.isEnabled()
// is honestly false and no request is ever prepared -- onPrepareExtraData is
// never called, exactly as on a device with no service connected.
package android.view.accessibility;

import java.lang.ref.WeakReference;

public abstract class AccessibilityRequestPreparer {
    public static final int REQUEST_TYPE_EXTRA_DATA = 0x00000001;

    private final WeakReference<android.view.View> mViewRef;
    private final int mAccessibilityViewId;

    public AccessibilityRequestPreparer(android.view.View view, int requestTypes) {
        if (!view.isAttachedToWindow()) {
            throw new IllegalStateException("View must be attached to a window");
        }
        mViewRef = new WeakReference<android.view.View>(view);
        // AOSP keys its preparer table by View.getAccessibilityViewId(), an
        // @hide accessor this tree does not have; the view's own id is the
        // stable identity available here and nothing consults the value (no
        // a11y service is bridged, so no preparer table is ever walked).
        mAccessibilityViewId = view.getId();
    }

    /** AOSP: the view this preparer was registered for, or null once it has
     *  been collected. Weak on AOSP too. */
    public android.view.View getView() { return mViewRef.get(); }

    /** @hide -- AOSP keeps the id so the manager can key its preparer table. */
    int getAccessibilityViewId() { return mAccessibilityViewId; }

    public abstract void onPrepareExtraData(int virtualViewId, String extraDataKey,
            android.os.Bundle args, android.os.Message preparationFinishedMessage);
}
