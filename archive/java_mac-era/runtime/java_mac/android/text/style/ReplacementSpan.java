// android.text.style.ReplacementSpan -- AOSP base for spans that replace a
// run of text with custom-drawn content (inline images, custom glyphs).
// AOSP: frameworks/base/core/java/android/text/style/ReplacementSpan.java
//   public abstract class ReplacementSpan extends MetricAffectingSpan
// with abstract getSize()/draw() and a content-description accessor pair.
//
// The previous v1.0.92 bare stub had only a no-arg ctor and was not abstract,
// so DynamicDrawableSpan / ImageSpan could not extend it faithfully. Promoting
// it to the real abstract contract is the prerequisite for ImageSpan; nothing
// in-tree subclassed the stub (it had no overridable members), so this is a
// safe replacement.
package android.text.style;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;

public abstract class ReplacementSpan extends MetricAffectingSpan {
    private CharSequence mContentDescription = null;

    public abstract int getSize(Paint paint, CharSequence text,
                                int start, int end, Paint.FontMetricsInt fm);

    public abstract void draw(Canvas canvas, CharSequence text,
                              int start, int end, float x,
                              int top, int y, int bottom, Paint paint);

    public CharSequence getContentDescription() { return mContentDescription; }

    public void setContentDescription(CharSequence contentDescription) {
        mContentDescription = contentDescription;
    }

    // AOSP: ReplacementSpan deliberately does NOT touch the paint in the
    // measure/draw-state callbacks (its size/draw fully own the run).
    @Override public void updateMeasureState(TextPaint p) {}
    @Override public void updateDrawState(TextPaint ds) {}
}
