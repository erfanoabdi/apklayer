// android.text.method.AllCapsTransformationMethod -- AOSP's transformation
// behind `android:textAllCaps` / `TextView.setAllCaps(true)` (and every
// Material button style, which sets textAllCaps in its default style).
//
// AOSP (frameworks/base/core/java/android/text/method/AllCapsTransformationMethod.java,
// android13-release) is locale-aware and span-preserving; its core is:
//     @Override
//     public CharSequence getTransformation(CharSequence source, View view) {
//         if (!mEnabled) { return source; }
//         ...
//         return TextUtils.toUpperCase(locale, source, /* copySpans */ true);
//     }
// We do the locale-aware upcase; span preservation is not modelled here
// because this tree's transformation seam (TextView.macapkDisplayText) hands
// draw/measure a CharSequence and the span lookup keys off the DRAWN text --
// an all-caps transform is length-preserving for the Latin/most scripts our
// corpus uses, so offsets line up. A script where upcasing changes length
// (e.g. German eszett -> SS) would shift spans; noted rather than pretended
// away.
//
// Locale comes from the view's configuration when available, matching AOSP's
// `view.getTextLocale()` behaviour, so a Turkish locale gets the dotted-I
// rules rather than the ROOT ones.
package android.text.method;

import android.graphics.Rect;
import android.view.View;

public class AllCapsTransformationMethod implements TransformationMethod {

    @Override
    public CharSequence getTransformation(CharSequence source, View view) {
        if (source == null) return null;
        java.util.Locale loc = null;
        try {
            if (view instanceof android.widget.TextView tv) loc = tv.getTextLocale();
        } catch (Throwable ignored) {}
        if (loc == null) loc = java.util.Locale.getDefault();
        try { return source.toString().toUpperCase(loc); }
        catch (Throwable t) { return source; }
    }

    @Override
    public void onFocusChanged(View view, CharSequence sourceText, boolean focused,
                                int direction, Rect previouslyFocusedRect) { }
}
