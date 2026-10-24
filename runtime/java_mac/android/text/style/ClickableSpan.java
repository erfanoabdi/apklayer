// android.text.style.ClickableSpan -- AOSP: a clickable, link-styled range
// (Linkify, HtmlCompat <a>, Compose ClickableText interop). Abstract onClick.
// v1.0.423.
package android.text.style;
import android.text.TextPaint;
import android.view.View;
public abstract class ClickableSpan extends CharacterStyle implements UpdateAppearance {
    public abstract void onClick(View widget);
    @Override public void updateDrawState(TextPaint tp) {
        tp.setColor(tp.linkColor != 0 ? tp.linkColor : 0xFF1A73E8);
        tp.setUnderlineText(true);
    }
}
