// android.text.style.MaskFilterSpan -- AOSP span that applies a MaskFilter
// (e.g. BlurMaskFilter) to the covered text range. Per frameworks/base/
// core/java/android/text/style/MaskFilterSpan.java: updateDrawState just
// does textPaint.setMaskFilter(mFilter). Sibling of ForegroundColorSpan /
// BackgroundColorSpan; Paint already carries a MaskFilter field
// (android.graphics.Paint#setMaskFilter/getMaskFilter, inherited by
// TextPaint), so this only routes a span's stored filter into it at
// draw time -- Canvas may or may not approximate the visual effect, but
// the span-application contract itself is exact.
package android.text.style;

import android.graphics.MaskFilter;
import android.text.TextPaint;

public class MaskFilterSpan extends CharacterStyle implements UpdateAppearance {
    private final MaskFilter mFilter;

    public MaskFilterSpan(MaskFilter filter) { mFilter = filter; }

    public MaskFilter getMaskFilter() { return mFilter; }

    @Override
    public void updateDrawState(TextPaint ds) {
        ds.setMaskFilter(mFilter);
    }
}
