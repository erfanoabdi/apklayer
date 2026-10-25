// android.view.ScrollCaptureCallback -- AOSP declares this an INTERFACE, and
// that KIND is load-bearing.
//
// WHY (found 2026-07-29, same audit as ViewManager / ViewTranslationCallback):
// we shipped it as a `public class` (v1.0.92 auto-generated stub), so a guest
// class that correctly lists it in its `interfaces` array fails to link with
// IncompatibleClassChangeError. Compose is a direct implementor -- compose-ui
// registers a ComposeScrollCaptureCallback on the AndroidComposeView -- so this
// is in the same cluster-wide category as ViewTranslationCallback rather than a
// single app's problem.
//
// Surface verified against
// frameworks/base/core/java/android/view/ScrollCaptureCallback.java -- four
// abstract methods, no defaults:
//   void onScrollCaptureSearch(CancellationSignal, Consumer<Rect>)
//   void onScrollCaptureStart(ScrollCaptureSession, CancellationSignal, Runnable)
//   void onScrollCaptureImageRequest(ScrollCaptureSession, CancellationSignal,
//                                    Rect, Consumer<Rect>)
//   void onScrollCaptureEnd(Runnable)
//
// Declaring them (rather than leaving the interface empty) is what makes a guest
// implementor's @Override legal and lets any framework-side caller we add later
// dispatch through the interface.
package android.view;

import android.graphics.Rect;
import android.os.CancellationSignal;
import java.util.function.Consumer;

/** Callback for scroll capture (long-screenshot) of a scrolling View. */
public interface ScrollCaptureCallback {
    void onScrollCaptureSearch(CancellationSignal signal, Consumer<Rect> onReady);

    void onScrollCaptureStart(ScrollCaptureSession session,
                              CancellationSignal signal, Runnable onReady);

    void onScrollCaptureImageRequest(ScrollCaptureSession session,
                                     CancellationSignal signal, Rect captureArea,
                                     Consumer<Rect> onComplete);

    void onScrollCaptureEnd(Runnable onReady);
}
