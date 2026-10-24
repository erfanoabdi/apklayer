// android.text.SpanWatcher -- the callback a Spannable fires when a markup span
// it carries is added/removed/moved. AOSP declares it as an INTERFACE (extends
// NoCopySpan); our prior auto-generated stub was a concrete class, so any
// AndroidX/IME/emoji helper that `implements SpanWatcher` failed verification
// (you cannot implement a class) -- the same IncompatibleClassChangeError class
// the Animatable2 / InputConnection stubs hit. v1.0.425.
// Source: frameworks/base/core/java/android/text/SpanWatcher.java
package android.text;

public interface SpanWatcher extends NoCopySpan {
    void onSpanAdded(Spannable text, Object what, int start, int end);
    void onSpanRemoved(Spannable text, Object what, int start, int end);
    void onSpanChanged(Spannable text, Object what, int ostart, int oend, int nstart, int nend);
}
