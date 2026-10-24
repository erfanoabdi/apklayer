package android.view.accessibility;

// AOSP frameworks/base/core/java/android/view/accessibility/AccessibilityEventSource.java.
// The interface implemented by classes that are a source of AccessibilityEvents --
// AOSP's View declares `implements ... AccessibilityEventSource` alongside
// Drawable.Callback and KeyEvent.Callback. Just two methods, both of which View
// already had under a different (non-declared) contract:
//
//   sendAccessibilityEvent(int eventType)
//   sendAccessibilityEventUnchecked(AccessibilityEvent event)
//
// WHY THIS FILE EXISTS. Same type-identity defect class as ContentInterface /
// ComponentCallbacks2 / Cloneable elsewhere in this audit
// (docs/type-identity-audit_2026-08-12.md): View had both methods with AOSP-
// matching descriptors but never declared the type, so `instanceof
// AccessibilityEventSource` answered false and a caller holding a View only
// through this interface type (an androidx a11y helper, a KeyEvent-callback
// dispatcher that also probes for this) would throw IncompatibleClassChangeError
// on the invokeinterface, same failure shape as the Drawable.Callback bug
// documented at View.java's own class-level comment.
//
// Both signatures were checked against View.java's existing
// sendAccessibilityEvent(int) / sendAccessibilityEventUnchecked(AccessibilityEvent)
// before the declaration went on View -- see that file's "AOSP accessibility
// hook pairs" block. Getting a descriptor wrong here is worse than the gap: it
// would leave the method abstract, and every app/framework View subclass would
// throw AbstractMethodError at a call site far from here instead of a clean,
// named NoSuchMethodError.
public interface AccessibilityEventSource {

    void sendAccessibilityEvent(int eventType);

    void sendAccessibilityEventUnchecked(AccessibilityEvent event);
}
