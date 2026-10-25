package android.window;
/** Predictive-back callback that also receives progress/animation events.
 *  Extends OnBackInvokedCallback; the animation callbacks default to no-ops so a
 *  non-animating app only needs onBackInvoked. API 34.
 *  Source: frameworks/base/core/java/android/window/OnBackAnimationCallback.java */
public interface OnBackAnimationCallback extends OnBackInvokedCallback {
    default void onBackStarted(BackEvent backEvent) { }
    default void onBackProgressed(BackEvent backEvent) { }
    default void onBackCancelled() { }
}
