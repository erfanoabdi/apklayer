// android.view.ViewParent -- interface ViewGroup implements. Default
// methods keep stub overrides minimal in subclasses.
package android.view;

import android.graphics.Rect;
import android.graphics.Point;
import android.view.accessibility.AccessibilityEvent;

public interface ViewParent {
    default void requestLayout() {}
    default boolean isLayoutRequested() { return false; }
    default void requestTransparentRegion(View child) {}
    default ViewParent getParent() { return null; }
    default void invalidateChild(View child, Rect dirty) {}
    default ViewParent invalidateChildInParent(int[] location, Rect dirty) { return null; }
    default void requestDisallowInterceptTouchEvent(boolean disallow) {}
    default boolean showContextMenuForChild(View originalView) { return false; }
    default void createContextMenu(android.view.ContextMenu menu) {}
    default void clearChildFocus(View child) {}
    default View focusSearch(View v, int direction) { return null; }
    default void focusableViewAvailable(View v) {}
    default void recomputeViewAttributes(View child) {}
    default void bringChildToFront(View child) {}
    default boolean getChildVisibleRect(View child, Rect r, Point offset) { return true; }
    default void onDescendantInvalidated(View child, View target) {}
    default void requestChildFocus(View child, View focused) {}
    default boolean canResolveLayoutDirection() { return true; }
    default boolean isLayoutDirectionResolved() { return true; }
    default int getLayoutDirection() { return 0; }
    default void childDrawableStateChanged(View child) {}

    // Accessibility bubble-up surface (AOSP frameworks/base core/java/android/
    // view/ViewParent.java:362 requestSendAccessibilityEvent, :423
    // notifySubtreeAccessibilityStateChanged). AOSP declares both as REQUIRED
    // (non-default) interface members, so every real ViewParent implementer
    // supplies a body. That matters here because androidx.core.view.ViewCompat /
    // AccessibilityDelegateCompat and ViewGroupCompat call them directly against
    // a ViewParent-typed reference (child.getParent().notifySubtreeAccessibility
    // StateChanged(...)) -- unconditionally, not gated on a service actually
    // running -- so their total absence is a hard NoSuchMethodError for any
    // widget that links against them, not merely a missing-feature gap.
    // View.sendAccessibilityEventUnchecked bubbles a populated event up via
    // getParent().requestSendAccessibilityEvent(this, event) so ancestors can
    // augment/veto before AccessibilityManager sends it; notifySubtreeAccessibi
    // lityStateChanged tells an ancestor a descendant's a11y-relevant subtree
    // changed shape. Mac.apk bridges no accessibility service to NSAccessibility
    // and AccessibilityManager.isEnabled() is honestly false (View.
    // sendAccessibilityEventInternal), so the correct default mirrors a real
    // device with no a11y service connected: the event is declined (false, not
    // actually sent) and the subtree-changed notification is a no-op. ViewGroup
    // does not override either method, so these interface defaults are what
    // every ViewGroup answers.
    default boolean requestSendAccessibilityEvent(View child, AccessibilityEvent event) { return false; }
    default void notifySubtreeAccessibilityStateChanged(View child, View source, int changeType) {}

    // NestedScrollingParent surface (API 21). RecyclerView / NestedScrollView /
    // CoordinatorLayout call these on their ViewParent during touch dispatch
    // (RecyclerView.onInterceptTouchEvent -> startNestedScroll ->
    // ViewParentCompat$Api21Impl -> parent.onStartNestedScroll). Without the
    // declarations the APK's invokeinterface throws NoSuchMethodError mid-
    // dispatch and the entire MotionEvent is dropped, so plain-View apps look
    // like they ignore touch. macOS has no scroll handoff, so a parent that
    // declines nested scrolling (returns false / no-ops) is correct -- the
    // child still scrolls itself. Signatures mirror AOSP frameworks/base
    // core/java/android/view/ViewParent.java exactly. getNestedScrollAxes()
    // lives on ViewGroup in AOSP, not here.
    default boolean onStartNestedScroll(View child, View target, int nestedScrollAxes) { return false; }
    default void onNestedScrollAccepted(View child, View target, int nestedScrollAxes) {}
    default void onStopNestedScroll(View target) {}
    default void onNestedScroll(View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {}
    default void onNestedPreScroll(View target, int dx, int dy, int[] consumed) {}
    default boolean onNestedFling(View target, float velocityX, float velocityY, boolean consumed) { return false; }
    default boolean onNestedPreFling(View target, float velocityX, float velocityY) { return false; }

    // ---- The rest of AOSP's ViewParent surface (frameworks/base
    // core/java/android/view/ViewParent.java, android16-release). AOSP declares
    // all of these as REQUIRED interface members; they are `default` here for the
    // same reason the block above is -- Mac.apk has ViewParent implementers
    // (ViewRootImpl, overlay hosts) that predate the full surface, and an
    // interface method with no body would break them at compile time while the
    // app-visible signature is identical either way. ViewGroup / View supply the
    // real bodies where one exists, so these defaults are only reached by a
    // parent that genuinely has nothing to say.

    /** AOSP ViewParent.canResolveTextDirection(). A parent can resolve text
     *  direction once it is itself resolvable; the root answers true. */
    default boolean canResolveTextDirection() { return true; }
    /** AOSP ViewParent.isTextDirectionResolved(). */
    default boolean isTextDirectionResolved() { return true; }
    /** AOSP ViewParent.getTextDirection(). TEXT_DIRECTION_FIRST_STRONG (1) is
     *  View's default resolved value. */
    default int getTextDirection() { return View.TEXT_DIRECTION_FIRST_STRONG; }
    /** AOSP ViewParent.canResolveTextAlignment(). */
    default boolean canResolveTextAlignment() { return true; }
    /** AOSP ViewParent.isTextAlignmentResolved(). */
    default boolean isTextAlignmentResolved() { return true; }
    /** AOSP ViewParent.getTextAlignment(). TEXT_ALIGNMENT_GRAVITY (1) is View's
     *  default resolved value. */
    default int getTextAlignment() { return View.TEXT_ALIGNMENT_GRAVITY; }

    /** AOSP ViewParent.childHasTransientStateChanged(View, boolean): a child's
     *  transient state (an in-flight animation, a pending a11y announcement)
     *  turned on or off. AOSP's ViewGroup forwards it up so a recycling parent
     *  such as RecyclerView can refuse to recycle the child; a parent with no
     *  recycling has nothing to do. */
    default void childHasTransientStateChanged(View child, boolean hasTransientState) {}

    /** AOSP ViewParent.getParentForAccessibility(): the closest ancestor that is
     *  a meaningful a11y node. A parent that is not itself a View has none. */
    default ViewParent getParentForAccessibility() { return null; }

    /** AOSP ViewParent.requestFitSystemWindows(): ask for a fitSystemWindows
     *  pass. Mac.apk dispatches no WindowInsets by design, so the default is a
     *  no-op -- ViewGroup/View still forward it upward. */
    default void requestFitSystemWindows() {}

    /** AOSP ViewParent.requestChildRectangleOnScreen(View, Rect, boolean).
     *  A parent that cannot scroll returns false ("nothing was moved"). */
    default boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
        return false;
    }

    /** AOSP ViewParent.keyboardNavigationClusterSearch(View, int). */
    default View keyboardNavigationClusterSearch(View currentCluster, int direction) { return null; }

    /** AOSP ViewParent.showContextMenuForChild(View, float, float) (API 24):
     *  the anchored form used by View.showContextMenu(x, y). AOSP's default
     *  ViewGroup implementation delegates upward; with no ancestor the chain
     *  ends in false, which is what a View with no context menu observes. */
    default boolean showContextMenuForChild(View originalView, float x, float y) { return false; }

    /** AOSP ViewParent.startActionModeForChild(View, ActionMode.Callback). */
    default ActionMode startActionModeForChild(View originalView, ActionMode.Callback callback) {
        return null;
    }
    /** AOSP ViewParent.startActionModeForChild(View, ActionMode.Callback, int)
     *  (API 23, the TYPE_FLOATING form). */
    default ActionMode startActionModeForChild(View originalView, ActionMode.Callback callback,
            int type) {
        return null;
    }

    /** AOSP ViewParent.onNestedPrePerformAccessibilityAction(View, int, Bundle):
     *  give the parent first refusal on an accessibility action targeted at a
     *  child. Returning false means "not consumed, let the child handle it",
     *  which is AOSP's ViewGroup behaviour when no ancestor consumes. */
    default boolean onNestedPrePerformAccessibilityAction(View target, int action,
            android.os.Bundle arguments) {
        return false;
    }

    /** AOSP ViewParent.findOnBackInvokedDispatcherForChild(View, View) (API 33),
     *  declared `default` in AOSP too, returning null. */
    default android.window.OnBackInvokedDispatcher findOnBackInvokedDispatcherForChild(
            View child, View requester) {
        return null;
    }
}
