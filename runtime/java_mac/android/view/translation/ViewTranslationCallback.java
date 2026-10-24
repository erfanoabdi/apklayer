// android.view.translation.ViewTranslationCallback -- AOSP declares this an
// INTERFACE, and that KIND is load-bearing.
//
// WHY (found 2026-07-29): we shipped it as a `public class` (a v1.0.92
// auto-generated stub). A guest class that correctly lists it in its
// `interfaces` array then fails to link with IncompatibleClassChangeError --
// the JVM refuses an interface slot filled by a class. The offender that
// matters is `androidx.compose.ui.platform.AndroidComposeView$AndroidComposeView
// TranslationCallback`, which every Compose app ships, so the defect is
// cluster-wide rather than one app's problem; it is also the last residual link
// failure on Fruit Ninja. Same defect family as android/net/http's
// `UrlRequest$Callback` / `BidirectionalStream$Callback`, both of which were
// shipped as abstract classes against an AOSP interface and fixed the same way.
//
// `debug/framework_lint.py` could not see this: it diffs against android-30's
// android.jar, and this type is API 31 (S), so it is one of the 731 of 2,653
// android/* classes (27.6%) that are invisible to the lint.
//
// Surface mirrors frameworks/base/core/java/android/view/translation/
// ViewTranslationCallback.java (three abstract methods + two defaults). The
// defaults must stay `default` bodies, not abstract: an implementor is allowed
// to omit them, and declaring them abstract would make such a class unloadable.
package android.view.translation;

import android.view.View;

/** Callback for handling translated information show/hide in a View. The
 *  platform collects ViewTranslationRequests via
 *  View.dispatchCreateViewTranslationRequest, sends them to the translation
 *  service, then calls onShowTranslation(View) once a response arrives. */
public interface ViewTranslationCallback {
    /** @return true if the callback handled showing the translation. */
    boolean onShowTranslation(View view);

    /** @return true if the callback handled hiding the translation. */
    boolean onHideTranslation(View view);

    /** @return true if the callback handled clearing the translation. */
    boolean onClearTranslation(View view);

    /** Opt in to padding around the translated text. AOSP's interface default
     *  is a no-op; TextView's implementation overrides it. */
    default void enableContentPadding() {}

    /** Duration of the show/hide animation. AOSP's default is a no-op. */
    default void setAnimationDurationMillis(int durationMillis) {}
}
