// v1.0.124: AOSP SpannableString = immutable CharSequence + mutable span
// store. Compose UI's AndroidParagraphIntrinsics constructs one from
// the text + spans during measure; without the (CharSequence) ctor
// the layout pass throws NoSuchMethodError mid-RV-dispatch.
//
// Per cs.android.com/.../text/SpannableString.java SpannableString
// extends SpannableStringInternal implements CharSequence, GetChars,
// Spannable. Our base provides character storage + span list.
package android.text;

import java.util.ArrayList;
import java.util.List;

public class SpannableString implements CharSequence, GetChars, Spannable {
    private final String text;
    private final List<SpanEntry> spans = new ArrayList<>();

    private static class SpanEntry {
        Object what;
        int start, end, flags;
        SpanEntry(Object w, int s, int e, int f) {
            what = w; start = s; end = e; flags = f;
        }
    }

    /**
     * AOSP SpannableStringInternal.getChars(int, int, char[], int)
     * (frameworks/base core/java/android/text/SpannableStringInternal.java) --
     * the GetChars fast path every text-layout consumer uses instead of
     * charAt-per-index. Declaring the INTERFACE matters as much as the method:
     * AOSP's SpannableString implements GetChars, and TextUtils/Layout branch
     * on `instanceof GetChars` before choosing the bulk copy, so without it we
     * silently took the slow path AND failed the type check.
     */
    @Override
    public void getChars(int start, int end, char[] dest, int destoff) {
        text.getChars(start, end, dest, destoff);
    }

    public SpannableString() { this(""); }
    public SpannableString(CharSequence src) { this(src, 0, src != null ? src.length() : 0); }

    // AOSP SpannableString's private (CharSequence,int,int) ctor + subSequence
    // (frameworks/base core/java/android/text/SpannableString.java): "public
    // final CharSequence subSequence(int start, int end) { return new
    // SpannableString(this, start, end); }". The public 1-arg ctor is
    // `this(source, 0, source.length())`, so this slicing ctor also backs the
    // ordinary full-text copy path -- no behavior change there, but it now
    // also lets subSequence() carry spans instead of dropping every one of
    // them (see the old subSequence below).
    //
    // AOSP's comment on the 1-arg ctor: "For the backward compatibility
    // reasons, this constructor copies all spans including NoCopySpan" -- so,
    // unlike SpannableStringBuilder, NoCopySpan is deliberately NOT filtered
    // here.
    private SpannableString(CharSequence src, int start, int end) {
        // v1.0.841: MUST slice through a plain String, never through
        // src.subSequence(...). AOSP's real SpannableStringInternal(CharSequence,
        // int,int,boolean) ctor (frameworks/base core/java/android/text/
        // SpannableStringInternal.java, fetched 2026-07-30) does exactly this:
        // `mText = source.toString().substring(start, end);` -- collapse to a
        // plain String FIRST, then use String.substring (a terminal operation
        // with no relation to any CharSequence's subSequence override). Calling
        // src.subSequence(start, end) here instead is an infinite loop the
        // moment `src` is a SpannableString (or anything else that reaches this
        // same override): subSequence() below calls `new SpannableString(this,
        // start, end)`, whose ctor called src.subSequence(...) again with the
        // SAME `src` -- unconditional StackOverflowError on every
        // SpannableString.subSequence() call, independent of input. Confirmed
        // via -Xlog:exceptions on a live crash: the unwind is a pure two-frame
        // cycle, SpannableString.subSequence <-> SpannableString.<init>
        // (CharSequence,int,int), which is exactly this shape. Hit by Compose
        // text layout (AndroidParagraph / LineHeightStyleSpan handling) on any
        // styled text, so it silently blanked Wikipedia's Explore feed and
        // Reddit's whole Compose-hosted UI (both reach TextView/Compose text
        // measurement with a SpannableString somewhere in the chain).
        this.text = src != null ? src.toString().substring(start, end) : "";
        if (src instanceof Spanned sp) {
            Object[] os = sp.getSpans(start, end, Object.class);
            if (os != null) {
                for (Object o : os) {
                    int s = Math.max(start, sp.getSpanStart(o)) - start;
                    int e = Math.min(end, sp.getSpanEnd(o)) - start;
                    int f = sp.getSpanFlags(o);
                    spans.add(new SpanEntry(o, s, e, f));
                }
            }
        }
    }

    public static SpannableString valueOf(CharSequence src) {
        if (src instanceof SpannableString ss) return ss;
        return new SpannableString(src);
    }

    @Override public int length() { return text.length(); }
    @Override public char charAt(int i) { return text.charAt(i); }
    // AOSP: `new SpannableString(this, start, end)` -- slices from `this`
    // (a Spanned), not from the plain backing String, so overlapping spans
    // survive the slice. The previous impl substringed the plain `text`
    // field, which knows nothing about `spans`, so every subSequence()
    // (TextUtils.ellipsize, Layout line-wrapping, any app that slices
    // styled text) silently dropped every span on the copy.
    @Override public CharSequence subSequence(int start, int end) {
        return new SpannableString(this, start, end);
    }
    @Override public String toString() { return text; }

    @Override public void setSpan(Object what, int start, int end, int flags) {
        if (what == null) return;
        // Remove any existing span entry with the same key.
        for (int i = spans.size() - 1; i >= 0; i--) {
            if (spans.get(i).what == what) spans.remove(i);
        }
        spans.add(new SpanEntry(what, start, end, flags));
    }
    @Override public void removeSpan(Object what) {
        for (int i = spans.size() - 1; i >= 0; i--) {
            if (spans.get(i).what == what) spans.remove(i);
        }
    }
    @Override public <T> T[] getSpans(int start, int end, Class<T> type) {
        List<SpanEntry> matched = new ArrayList<>();
        for (SpanEntry e : spans) {
            // AOSP boundary-inclusive overlap test (keeps a zero-length span at a
            // boundary and a zero-length query getSpans(N,N,kind)); the old
            // strict-interior test e.end>start && e.start<end dropped both.
            if (e.end >= start && e.start <= end
                    && (e.start == e.end || start == end
                        || (e.start < end && e.end > start))) {
                if (type == null || type.isInstance(e.what)) matched.add(e);
            }
        }
        // AOSP orders by SPAN_PRIORITY (high byte of flags) descending, stable --
        // so a high-priority span (selection/highlight) is returned before a
        // lower-priority one. Was insertion order.
        matched.sort((a, b) ->
            ((b.flags & Spanned.SPAN_PRIORITY) >> Spanned.SPAN_PRIORITY_SHIFT)
          - ((a.flags & Spanned.SPAN_PRIORITY) >> Spanned.SPAN_PRIORITY_SHIFT));
        @SuppressWarnings("unchecked")
        T[] arr = (T[]) java.lang.reflect.Array.newInstance(
            type == null ? Object.class : type, matched.size());
        for (int i = 0; i < matched.size(); i++) {
            @SuppressWarnings("unchecked") T t = (T) matched.get(i).what;
            arr[i] = t;
        }
        return arr;
    }
    @Override public int getSpanStart(Object what) {
        for (SpanEntry e : spans) if (e.what == what) return e.start;
        return -1;
    }
    @Override public int getSpanEnd(Object what) {
        for (SpanEntry e : spans) if (e.what == what) return e.end;
        return -1;
    }
    @Override public int getSpanFlags(Object what) {
        for (SpanEntry e : spans) if (e.what == what) return e.flags;
        return 0;
    }
    @Override public int nextSpanTransition(int start, int limit, Class type) {
        int next = limit;
        for (SpanEntry e : spans) {
            if (type != null && !type.isInstance(e.what)) continue;
            if (e.start > start && e.start < next) next = e.start;
            if (e.end   > start && e.end   < next) next = e.end;
        }
        return next;
    }
}
