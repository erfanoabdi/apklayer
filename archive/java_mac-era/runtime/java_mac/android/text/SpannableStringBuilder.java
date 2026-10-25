// android.text.SpannableStringBuilder -- mutable string with markup spans.
// Per AOSP source at
// https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android13-release/core/java/android/text/SpannableStringBuilder.java
// implements Spannable + Editable + CharSequence + Appendable.
// Mac.apk impl backs it with a StringBuilder + a List<SpanEntry> (AOSP
// uses a gap buffer + parallel arrays + an interval tree; we trade that
// performance for a much smaller, easier-to-audit store -- the
// OBSERVABLE contract is what apps/AndroidX depend on, not the internal
// data structure). Spans stored here are NOT inert: android.widget.
// TextView.onDraw and android.text.Layout.draw both consult
// CharacterStyle spans when painting a line.
package android.text;

import java.util.ArrayList;
import java.util.List;

public class SpannableStringBuilder implements CharSequence, Spannable, Editable, Appendable {
    private final StringBuilder buf;
    private final List<SpanEntry> spans = new ArrayList<>();

    private static final class SpanEntry {
        Object what;
        int start;
        int end;
        int flags;
        SpanEntry(Object what, int start, int end, int flags) {
            this.what = what; this.start = start; this.end = end; this.flags = flags;
        }
    }

    public SpannableStringBuilder() { this.buf = new StringBuilder(); }
    public SpannableStringBuilder(CharSequence text) {
        this.buf = new StringBuilder(text != null ? text : "");
        if (text instanceof Spanned sp) copySpansFrom(sp, 0, sp.length(), 0);
    }
    public SpannableStringBuilder(CharSequence text, int start, int end) {
        this.buf = new StringBuilder();
        if (text != null) buf.append(text, Math.max(0, start), Math.min(text.length(), end));
        if (text instanceof Spanned sp) copySpansFrom(sp, start, end, 0);
    }

    // AOSP SpannableStringBuilder(CharSequence,int,int) ctor: "if (spans[i]
    // instanceof NoCopySpan) { continue; }" (frameworks/base core/java/
    // android/text/SpannableStringBuilder.java, ~line 85). A whole-buffer
    // copy or subSequence must NOT carry NoCopySpan markers -- Selection.
    // SELECTION_START/END, a TextWatcher hookup -- into the new instance;
    // those belong to exactly one live buffer. Without this, slicing styled
    // text (TextUtils.ellipsize, Layout line-wrapping, subSequence) could
    // smuggle another Editable's live selection/watcher into a throwaway copy.
    private void copySpansFrom(Spanned src, int srcStart, int srcEnd, int destOff) {
        Object[] arr = src.getSpans(srcStart, srcEnd, Object.class);
        if (arr == null) return;
        for (Object s : arr) {
            if (s instanceof NoCopySpan) continue;
            int st = Math.max(srcStart, src.getSpanStart(s)) - srcStart + destOff;
            int en = Math.min(srcEnd, src.getSpanEnd(s)) - srcStart + destOff;
            int fl = src.getSpanFlags(s);
            spans.add(new SpanEntry(s, st, en, fl));
        }
    }

    // CharSequence
    @Override public int length() { return buf.length(); }
    @Override public char charAt(int index) { return buf.charAt(index); }
    // AOSP: "Return a new CharSequence containing a copy of the specified
    // range of this buffer, including the overlapping spans" -- literally
    // `return new SpannableStringBuilder(this, start, end);` (frameworks/
    // base SpannableStringBuilder.java). The previous impl sliced the PLAIN
    // java.lang.StringBuilder (`buf`, which has no idea `spans` exists), so
    // every subSequence() -- used by TextUtils.ellipsize, Layout line
    // breaking, and any app slicing styled text -- silently dropped every
    // span on the copy.
    @Override public CharSequence subSequence(int start, int end) {
        return new SpannableStringBuilder(this, start, end);
    }
    @Override public String toString() { return buf.toString(); }

    // Editable -- all mutators route through replace(int,int,CharSequence,int,int)
    // so span-offset tracking (below) runs uniformly, exactly as AOSP funnels
    // append/insert/delete through replace -> change().
    @Override public SpannableStringBuilder append(CharSequence text) {
        return replace(length(), length(), text, 0, text == null ? 0 : text.length());
    }
    @Override public SpannableStringBuilder append(CharSequence text, int start, int end) {
        return replace(length(), length(), text, start, end);
    }
    @Override public SpannableStringBuilder append(char text) {
        return append(String.valueOf(text));
    }
    public SpannableStringBuilder append(CharSequence text, Object what, int flags) {
        int start = length();
        append(text);
        setSpan(what, start, length(), flags);
        return this;
    }
    @Override public SpannableStringBuilder replace(int st, int en, CharSequence source) {
        return replace(st, en, source, 0, source == null ? 0 : source.length());
    }
    // AOSP SpannableStringBuilder.replace (frameworks/base core/java/android/text/
    // SpannableStringBuilder.java:512-584): filter -> capture the covering
    // TextWatchers -> notify before -> mutate (shift existing spans, splice in
    // the incoming text's own spans) -> notify text-changed/after -> notify
    // SpanWatchers LAST ("Span watchers need to be called after text watchers,
    // which may update the layout").
    @Override public SpannableStringBuilder replace(int st, int en, CharSequence source, int start, int end) {
        // AOSP checkRange("replace", st, en) -- SpannableStringBuilder.java:1316-1333,
        // which runs BEFORE filters and before any watcher fires:
        //   if (end < start)                 "... has end before start"
        //   if (start > len || end > len)    "... ends beyond length N"
        //   if (start < 0 || end < 0)        "... starts before 0"
        //
        // We had none, so a bad range reached java.lang.StringBuilder and threw
        // StringIndexOutOfBoundsException from deep inside it -- AFTER we had
        // already fired beforeTextChanged with bogus indices, and with a message
        // naming an internal offset instead of the operation. Both are visible to
        // an app. (StringIndexOutOfBoundsException extends IndexOutOfBoundsException,
        // so no existing catch narrows; this only makes the throw earlier and
        // truthful.)
        //
        // Deliberately a THROW, not a clamp: `getText().insert(-1, x)` throws on a
        // device. Clamping belongs at the editing layer, the way AOSP's
        // QwertyKeyListener resets a negative selection to 0 before editing.
        {
            final int len = length();
            if (en < st) throw new IndexOutOfBoundsException(
                    "replace (" + st + " ... " + en + ") has end before start");
            if (st > len || en > len) throw new IndexOutOfBoundsException(
                    "replace (" + st + " ... " + en + ") ends beyond length " + len);
            if (st < 0 || en < 0) throw new IndexOutOfBoundsException(
                    "replace (" + st + " ... " + en + ") starts before 0");
        }
        // AOSP itself never calls this with a null CharSequence (delete() passes
        // "", not null; @throws NullPointerException is documented for the
        // public entry points). Mac.apk's own delete() uses null as an internal
        // sentinel, so normalize here rather than make every filter/watcher/
        // span-merge step downstream special-case it.
        if (source == null) { source = ""; start = 0; end = 0; }

        // Run every registered InputFilter, chaining each one's output into the
        // next -- a filter may shorten (LengthFilter), transform (AllCaps), or
        // reject ("") the insertion (frameworks/base SpannableStringBuilder.
        // java:516-525).
        for (InputFilter f : mFilters) {
            CharSequence repl = f.filter(source, start, end, this, st, en);
            if (repl != null) { source = repl; start = 0; end = repl.length(); }
        }

        final int origLen = en - st;
        final int newLen = end - start;

        // A genuinely no-op replace (nothing removed, nothing inserted, and no
        // span in `source` would attach at that point) must not notify
        // TextWatchers of a change that never happened (frameworks/base
        // SpannableStringBuilder.java:530-534).
        if (origLen == 0 && newLen == 0 && !hasNonExclusiveExclusiveSpanAt(source, start)) {
            return this;
        }

        // Captured BEFORE the edit, over the range being replaced; the same
        // array is reused for before/text-changed/after so all three callbacks
        // see the same watcher set even if the edit itself adds/removes one.
        TextWatcher[] textWatchers = getSpans(st, st + origLen, TextWatcher.class);
        sendBeforeTextChanged(textWatchers, st, origLen, newLen);

        // Shift/expand/collapse EXISTING span offsets BEFORE mutating the buffer so the
        // index math runs in the OLD coordinate space (AOSP SpannableStringBuilder.
        // change -> updatedIntervalBound). The live Editable model (every EditText /
        // Compose BasicTextField interop) relies on this: Selection.SELECTION_START/
        // END are zero-length POINT_POINT spans that must ride the text as the user
        // types -- without offset tracking they pointed at stale positions after any
        // edit, so the cursor/selection desynced. v1.0.425.
        //
        // updateSpansForReplace only MUTATES span offsets and RETURNS the
        // SpanWatcher notifications that shift implies; it must not fire them
        // itself, because AOSP's contract (replace() -> change() -> ... ->
        // sendToSpanWatchers(), frameworks/base SpannableStringBuilder.java)
        // is that watchers observe a self-consistent post-edit CharSequence.
        // Firing before `buf` is mutated would let a watcher read stale text.
        List<Runnable> pendingSpanNotifications = updateSpansForReplace(st, en, newLen);
        buf.replace(st, en, source.subSequence(start, end).toString());

        // AOSP change() (same file, ~line 419): if the inserted text is itself
        // Spanned, splice its spans in at their mapped offsets, skipping any
        // span object already attached here by identity. This step was missing
        // entirely: ssb.append(spannedPiece) / insert(spannedPiece) silently
        // dropped that piece's ForegroundColorSpan / StyleSpan / whatever even
        // though calling setSpan() directly always worked -- the single most
        // common way apps build styled text incrementally was a no-op.
        if (source instanceof Spanned) {
            Spanned sp = (Spanned) source;
            Object[] incoming = sp.getSpans(start, end, Object.class);
            for (Object span : incoming) {
                if (getSpanStart(span) >= 0) continue;   // already attached here
                int spanStart = Math.max(start, sp.getSpanStart(span));
                int spanEnd = Math.min(end, sp.getSpanEnd(span));
                final int newStart = spanStart - start + st;
                final int newEnd = spanEnd - start + st;
                final int flags = sp.getSpanFlags(span);
                spans.add(new SpanEntry(span, newStart, newEnd, flags));
                final Object fSpan = span;
                pendingSpanNotifications.add(() -> sendSpanAdded(fSpan, newStart, newEnd));
            }
        }

        sendTextChanged(textWatchers, st, origLen, newLen);
        sendAfterTextChanged(textWatchers);

        // Span watchers fire LAST: "Span watchers need to be called after text
        // watchers, which may update the layout" (frameworks/base
        // SpannableStringBuilder.java:580-581).
        for (Runnable r : pendingSpanNotifications) r.run();
        return this;
    }

    /** AOSP hasNonExclusiveExclusiveSpanAt (frameworks/base SpannableStringBuilder.
     *  java:586-598): true if a span other than a plain SPAN_EXCLUSIVE_EXCLUSIVE
     *  one sits exactly at `offset` in `text` -- used only to decide whether a
     *  0-length replace() is worth notifying about. */
    private static boolean hasNonExclusiveExclusiveSpanAt(CharSequence text, int offset) {
        if (!(text instanceof Spanned)) return false;
        Spanned sp = (Spanned) text;
        Object[] spansAt = sp.getSpans(offset, offset, Object.class);
        for (Object s : spansAt) {
            if (sp.getSpanFlags(s) != Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) return true;
        }
        return false;
    }

    // AOSP sendBeforeTextChanged/sendTextChanged/sendAfterTextChanged
    // (frameworks/base SpannableStringBuilder.java:1253-1281).
    // AOSP SpannableStringBuilder.mTextWatcherDepth (SpannableStringBuilder.java
    // :1808), incremented around EACH of the three dispatch loops below
    // (:1254-1281) and read by getTextWatcherDepth(). A depth > 1 means a
    // TextWatcher's own edit re-entered the dispatch -- the standard way an
    // Editable-formatting watcher detects and breaks its own recursion.
    private int mTextWatcherDepth;

    /** AOSP SpannableStringBuilder.getTextWatcherDepth() (:1250-1252). 0 when
     *  not currently dispatching. */
    public int getTextWatcherDepth() {
        return mTextWatcherDepth;
    }

    private void sendBeforeTextChanged(TextWatcher[] watchers, int start, int before, int after) {
        mTextWatcherDepth++;
        try {
            for (TextWatcher w : watchers) w.beforeTextChanged(this, start, before, after);
        } finally {
            mTextWatcherDepth--;
        }
    }
    private void sendTextChanged(TextWatcher[] watchers, int start, int before, int after) {
        mTextWatcherDepth++;
        try {
            for (TextWatcher w : watchers) w.onTextChanged(this, start, before, after);
        } finally {
            mTextWatcherDepth--;
        }
    }
    private void sendAfterTextChanged(TextWatcher[] watchers) {
        mTextWatcherDepth++;
        try {
            for (TextWatcher w : watchers) w.afterTextChanged(this);
        } finally {
            mTextWatcherDepth--;
        }
    }

    /**
     * AOSP SpannableStringBuilder.getTextRunCursor(int, int, int, int, int,
     * Paint) (:1566-1569) -- the DEPRECATED int-direction form, which is a
     * one-liner over the boolean form:
     *     return getTextRunCursor(contextStart, contextEnd, dir == 1, offset, cursorOpt, p);
     * (`dir == 1` is the legacy Paint.DIRECTION_RTL.) The boolean form itself
     * is @hide, so this is the only one an app can call.
     *
     * The real work is Paint.getTextRunCursor, which walks to the nearest valid
     * cursor position (a grapheme-cluster boundary) in the requested direction.
     * AOSP splits the call around its gap buffer; ours has no gap, so the whole
     * sequence is handed over directly.
     */
    @Deprecated
    public int getTextRunCursor(int contextStart, int contextEnd, int dir, int offset,
            int cursorOpt, android.graphics.Paint p) {
        if (p == null) throw new NullPointerException();
        return p.getTextRunCursor(this, contextStart, contextEnd, dir == 1, offset, cursorOpt);
    }
    @Override public SpannableStringBuilder insert(int where, CharSequence text) {
        return replace(where, where, text, 0, text == null ? 0 : text.length());
    }
    @Override public SpannableStringBuilder insert(int where, CharSequence text, int start, int end) {
        return replace(where, where, text, start, end);
    }
    @Override public SpannableStringBuilder delete(int st, int en) {
        return replace(st, en, null, 0, 0);
    }

    // AOSP SpannableStringBuilder.updatedIntervalBound, re-expressed in plain
    // (non-gap-buffer) coordinates. Replacing [start,end) with newLen chars
    // (delta = newLen - (end-start)) repositions one span boundary at `off`.
    // `movesRight` encodes the documented expand-on-insert contract (Spanned):
    //   EXCLUSIVE start (POINT, hi nibble == 2) moves right on insert-at-start;
    //   INCLUSIVE end   (POINT, lo nibble == 2) moves right on insert-at-end.
    // A boundary before the change is fixed; at or after `end` it shifts by delta;
    // inside the changed region it sticks to the right (start+newLen) when it
    // expands, else collapses to start.
    private static int updateOffset(int off, int start, int end, int newLen, boolean movesRight) {
        int delta = newLen - (end - start);
        if (off < start) return off;
        if (off >= end && off > start) return off + delta;
        // off in [start, end): boundary inside / at the left edge of the change
        return movesRight ? start + newLen : start;
    }

    // Mutates span offsets for a pending replace and returns the SpanWatcher
    // notifications that shift implies, DEFERRED (not fired) -- see the
    // replace() call site for why firing has to wait until after `buf` is
    // mutated. Only genuinely-moved or genuinely-dropped spans queue a
    // notification; a span the edit didn't touch (updateOffset returns its
    // input unchanged) queues nothing, matching AOSP's sendToSpanWatchers
    // contract of only reporting spans whose bounds actually changed.
    private List<Runnable> updateSpansForReplace(int start, int end, int newLen) {
        List<Runnable> pending = new ArrayList<>();
        for (int i = spans.size() - 1; i >= 0; i--) {
            SpanEntry e = spans.get(i);
            final Object what = e.what;
            final int oldStart = e.start, oldEnd = e.end;
            boolean startMovesRight = ((e.flags >> 4) & 0x3) == 2;  // POINT/EXCLUSIVE start
            boolean endMovesRight   = (e.flags & 0x3) == 2;         // POINT/INCLUSIVE end
            e.start = updateOffset(oldStart, start, end, newLen, startMovesRight);
            e.end   = updateOffset(oldEnd,   start, end, newLen, endMovesRight);
            // AOSP removeSpansForChange: a non-empty SPAN_EXCLUSIVE_EXCLUSIVE span
            // entirely consumed by the edit is dropped, not left zero-length.
            if ((e.flags & 0x33) == 0x21            // SPAN_EXCLUSIVE_EXCLUSIVE
                    && oldStart < oldEnd && e.start == e.end
                    && oldStart >= start && oldEnd <= end) {
                spans.remove(i);
                // AOSP removeSpansForChange -> removeSpan(i, 0) -> sendSpanRemoved
                // (frameworks/base SpannableStringBuilder.java) reports the span's
                // extents as they were before the edit consumed it.
                pending.add(() -> sendSpanRemoved(what, oldStart, oldEnd));
            } else if (e.start != oldStart || e.end != oldEnd) {
                final int newStart = e.start, newEnd = e.end;
                pending.add(() -> sendSpanChanged(what, oldStart, oldEnd, newStart, newEnd));
            }
        }
        return pending;
    }

    // AOSP sendSpanAdded/sendSpanRemoved/sendSpanChanged (frameworks/base
    // SpannableStringBuilder.java:1283-1309, quoted verbatim below in
    // structure): notify every attached SpanWatcher whose own span range
    // overlaps the affected region. Selection.setSelection() moves the
    // SELECTION_START/END marker spans on every cursor move, and any
    // EditText helper / IME composing-region tracker / rich-text editor
    // that implements SpanWatcher relies on these firing to react.
    private void sendSpanAdded(Object what, int start, int end) {
        SpanWatcher[] recip = getSpans(start, end, SpanWatcher.class);
        for (SpanWatcher w : recip) w.onSpanAdded(this, what, start, end);
    }
    private void sendSpanRemoved(Object what, int start, int end) {
        SpanWatcher[] recip = getSpans(start, end, SpanWatcher.class);
        for (SpanWatcher w : recip) w.onSpanRemoved(this, what, start, end);
    }
    private void sendSpanChanged(Object what, int oldStart, int oldEnd, int newStart, int newEnd) {
        SpanWatcher[] recip = getSpans(
            Math.min(oldStart, newStart),
            Math.min(Math.max(oldEnd, newEnd), length()),
            SpanWatcher.class);
        for (SpanWatcher w : recip) w.onSpanChanged(this, what, oldStart, oldEnd, newStart, newEnd);
    }
    @Override public void clear() { buf.setLength(0); spans.clear(); }
    @Override public void clearSpans() { spans.clear(); }

    // AOSP setFilters/getFilters + mFilters field (frameworks/base
    // SpannableStringBuilder.java:1595-1607): filters are stored AND run
    // from replace() below. The previous impl accepted and silently
    // discarded the array -- InputFilter.LengthFilter never enforced a max
    // length, InputFilter.AllCaps never upper-cased, and any custom filter
    // an app installed via Editable.setFilters() had no effect whatsoever.
    private static final InputFilter[] NO_FILTERS = new InputFilter[0];
    private InputFilter[] mFilters = NO_FILTERS;
    @Override public void setFilters(InputFilter[] filters) {
        if (filters == null) throw new IllegalArgumentException();
        mFilters = filters;
    }
    @Override public InputFilter[] getFilters() { return mFilters; }

    // Spannable / Spanned
    @Override public void setSpan(Object what, int start, int end, int flags) {
        // v1.0.365: a span object is attached at most ONCE -- re-setSpan
        // MOVES the existing entry in place (AOSP SpannableStringBuilder
        // searches mSpans for `what` and updates it). The old impl
        // appended a duplicate every call, and getSpanStart returns the
        // FIRST match, so re-setting the singleton Selection.SELECTION_
        // START/END markers (which apps re-set on every cursor move) read
        // the STALE oldest position. That made Google Calculator's
        // backspace cursor-convergence loop never converge -> infinite
        // recursion -> main-thread stack overflow. Move-in-place fixes
        // the Spannable contract for every EditText-based app.
        for (SpanEntry e : spans) {
            if (e.what == what) {
                int oldStart = e.start, oldEnd = e.end;
                e.start = start; e.end = end; e.flags = flags;
                // AOSP: re-setSpan on an already-attached span sends onSpanChanged,
                // unconditionally (even if start/end/flags are identical to before).
                sendSpanChanged(what, oldStart, oldEnd, start, end);
                return;
            }
        }
        spans.add(new SpanEntry(what, start, end, flags));
        sendSpanAdded(what, start, end);
    }
    @Override public void removeSpan(Object what) {
        // Collect-then-fire (not fire-while-iterating): a SpanWatcher callback
        // is arbitrary app code that could reentrantly call back into this
        // Spannable (e.g. removeSpan/setSpan from within onSpanRemoved), which
        // would corrupt an in-progress index-based removal loop over `spans`.
        List<SpanEntry> removed = null;
        for (int i = spans.size() - 1; i >= 0; i--) {
            if (spans.get(i).what == what) {
                if (removed == null) removed = new ArrayList<>();
                removed.add(spans.remove(i));
            }
        }
        if (removed != null) {
            for (SpanEntry e : removed) sendSpanRemoved(what, e.start, e.end);
        }
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
    @Override public <T> T[] getSpans(int queryStart, int queryEnd, Class<T> type) {
        java.util.ArrayList<SpanEntry> matched = new java.util.ArrayList<>();
        for (SpanEntry e : spans) {
            // AOSP SpannableStringInternal overlap test: also keeps a zero-length
            // span at a boundary and matches a zero-length query getSpans(N,N,kind)
            // (the strict-interior test e.start<end && e.end>start dropped both).
            if (e.end >= queryStart && e.start <= queryEnd
                    && (e.start == e.end || queryStart == queryEnd
                        || (e.start < queryEnd && e.end > queryStart))) {
                if (type == null || type.isInstance(e.what)) matched.add(e);
            }
        }
        // AOSP SpannableStringInternal.getSpans orders by priority (the high byte
        // of the flags, SPAN_PRIORITY): higher-priority spans come first, equal
        // priority keeps insertion order (List.sort is stable). Wrong ordering
        // makes a low-priority span paint over a high-priority one (a selection/
        // highlight under a foreground-color span). v1.0.426. No-op for the common
        // all-zero-priority case. */
        matched.sort((a, b) ->
            ((b.flags & Spanned.SPAN_PRIORITY) >> Spanned.SPAN_PRIORITY_SHIFT)
          - ((a.flags & Spanned.SPAN_PRIORITY) >> Spanned.SPAN_PRIORITY_SHIFT));
        @SuppressWarnings("unchecked")
        T[] arr = (T[]) java.lang.reflect.Array.newInstance(
            type != null ? type : Object.class, matched.size());
        for (int i = 0; i < matched.size(); i++) {
            @SuppressWarnings("unchecked") T c = (T) matched.get(i).what;
            arr[i] = c;
        }
        return arr;
    }
    @Override public int nextSpanTransition(int start, int limit, Class type) {
        int best = limit;
        for (SpanEntry e : spans) {
            if (type != null && !type.isInstance(e.what)) continue;
            if (e.start > start && e.start < best) best = e.start;
            if (e.end > start && e.end < best) best = e.end;
        }
        return best;
    }

    // GetChars
    public void getChars(int start, int end, char[] dest, int destOff) {
        buf.getChars(start, end, dest, destOff);
    }

    public static SpannableStringBuilder valueOf(CharSequence source) {
        return (source instanceof SpannableStringBuilder) ? (SpannableStringBuilder) source : new SpannableStringBuilder(source);
    }
}

