// android.text.SpannedString -- the immutable sibling of SpannableString.
// Per cs.android.com/.../text/SpannedString.java it is
//   public final class SpannedString extends SpannableStringInternal
//       implements CharSequence, GetChars, Spanned
// i.e. it holds character data + a fixed span list captured at
// construction time and exposes only the read-only Spanned surface
// (no setSpan/removeSpan). Html.fromHtml and TextUtils return one of
// these; HCR 1.43 referenced it (NoClassDefFoundError, non-fatal).
//
// Mac.apk stores the spans copied from the source CharSequence so the
// getSpans/getSpanStart/... contract is honored exactly like
// SpannableString, just without mutation.
package android.text;

import java.util.ArrayList;
import java.util.List;

public final class SpannedString implements CharSequence, GetChars, Spanned {
    private final String text;
    private final List<SpanEntry> spans = new ArrayList<>();

    private static class SpanEntry {
        Object what;
        int start, end, flags;
        SpanEntry(Object w, int s, int e, int f) {
            what = w; start = s; end = e; flags = f;
        }
    }

    /** AOSP SpannableStringInternal.getChars(int, int, char[], int) -- see the
     *  same method on SpannableString for why implementing GetChars is part of
     *  the contract and not just a convenience. */
    @Override
    public void getChars(int start, int end, char[] dest, int destoff) {
        text.getChars(start, end, dest, destoff);
    }

    public SpannedString(CharSequence src) { this(src, 0, src != null ? src.length() : 0); }

    // AOSP SpannedString's private (CharSequence,int,int) ctor + subSequence
    // (frameworks/base core/java/android/text/SpannedString.java): "public
    // CharSequence subSequence(int start, int end) { return new
    // SpannedString(this, start, end); }". The public 1-arg ctor is
    // `this(source, 0, source.length())`, so this slicing ctor also backs the
    // ordinary full-text copy path (no behavior change there). Matching
    // SpannableString's sibling ctor, NoCopySpan is NOT filtered here: "For
    // the backward compatibility reasons, this constructor copies all spans
    // including NoCopySpan."
    private SpannedString(CharSequence src, int start, int end) {
        // v1.0.841: same fix as the SpannableString sibling ctor -- slice
        // through a plain String (src.toString().substring(...)), never
        // through src.subSequence(...). AOSP's SpannableStringInternal ctor
        // does `mText = source.toString().substring(start, end)`; calling
        // src.subSequence(start, end) here instead recurses straight back into
        // THIS class's own subSequence() override (which calls `new
        // SpannedString(this, start, end)`, re-entering this ctor with the
        // SAME src) -- an unconditional StackOverflowError on every
        // SpannedString.subSequence() call. See SpannableString.java for the
        // full citation + how it was found (-Xlog:exceptions on a live crash).
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

    public static SpannedString valueOf(CharSequence src) {
        if (src instanceof SpannedString ss) return ss;
        return new SpannedString(src);
    }

    @Override public int length() { return text.length(); }
    @Override public char charAt(int i) { return text.charAt(i); }
    // AOSP: `new SpannedString(this, start, end)` -- slices from `this` (a
    // Spanned), not the plain backing String, so overlapping spans survive.
    // The previous impl substringed the plain `text` field, dropping every
    // span on every subSequence() call (Html.fromHtml results, TextUtils
    // return values, any app slicing styled text).
    @Override public CharSequence subSequence(int start, int end) {
        return new SpannedString(this, start, end);
    }
    @Override public String toString() { return text; }

    // AOSP SpannableStringInternal.getSpans (frameworks/base core/java/
    // android/text/SpannableStringInternal.java): boundary-inclusive overlap
    // (a zero-length span sitting exactly at a boundary, or a zero-length
    // query getSpans(N,N,kind), both match) and the result is ordered by
    // SPAN_PRIORITY descending, ties in insertion order. The previous impl
    // used the strict-interior test `e.end<=start || e.start>=end` (already
    // identified and fixed in the SpannableString/SpannableStringBuilder
    // siblings, but left stale here) and never sorted by priority at all, so
    // a low-priority span could paint over a high-priority one.
    @Override public <T> T[] getSpans(int start, int end, Class<T> type) {
        List<SpanEntry> matched = new ArrayList<>();
        for (SpanEntry e : spans) {
            if (e.end >= start && e.start <= end
                    && (e.start == e.end || start == end
                        || (e.start < end && e.end > start))) {
                if (type == null || type.isInstance(e.what)) matched.add(e);
            }
        }
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
    @Override public int nextSpanTransition(int start, int limit, Class<?> type) {
        int next = limit;
        for (SpanEntry e : spans) {
            if (type != null && !type.isInstance(e.what)) continue;
            if (e.start > start && e.start < next) next = e.start;
            if (e.end   > start && e.end   < next) next = e.end;
        }
        return next;
    }
}
