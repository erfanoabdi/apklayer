// android.text.Selection -- AOSP static helpers for the text cursor /
// selection range on a Spannable. Real Android stores the range as
// two zero-length marker spans (SELECTION_START / SELECTION_END) on
// the Spannable itself; we mirror that contract with the same marker
// objects so getSelectionStart/End round-trip through any Spannable
// whose get/setSpan works, and degrade to -1 when the text isn't a
// Spanned. Calculator's formula view reads/writes the selection from
// its action-mode plumbing.
// AOSP: frameworks/base/core/java/android/text/Selection.java
package android.text;

public class Selection {
    private Selection() { /* cannot instantiate */ }

    // AOSP uses private singleton marker classes `START`/`END` that both
    // `implements NoCopySpan` (frameworks/base core/java/android/text/
    // Selection.java: "private static final class START implements
    // NoCopySpan { }"). Identity is what matters for the marker itself, but
    // NoCopySpan-ness matters too: a copy-constructed or sliced Spannable
    // (SpannableStringBuilder's ctor / subSequence) must NOT carry another
    // buffer's selection markers along. NoCopySpan.Concrete is AOSP's own
    // documented stand-in for "just a new Object() that is also no-copy".
    public static final Object SELECTION_START = new NoCopySpan.Concrete();
    public static final Object SELECTION_END = new NoCopySpan.Concrete();

    // AOSP Spanned.SPAN_POINT_POINT (0x22). Our Spanned interface
    // predates this constant and its SPAN_* values diverge from
    // AOSP's; the flag is inert in our span stores, so carry the
    // AOSP literal for fidelity.
    private static final int SPAN_POINT_POINT = 0x22;

    public static int getSelectionStart(CharSequence text) {
        if (text instanceof Spanned) {
            try { return ((Spanned) text).getSpanStart(SELECTION_START); }
            catch (Throwable ignored) {}
        }
        return -1;
    }

    public static int getSelectionEnd(CharSequence text) {
        if (text instanceof Spanned) {
            try { return ((Spanned) text).getSpanStart(SELECTION_END); }
            catch (Throwable ignored) {}
        }
        return -1;
    }

    /** AOSP Selection.java:78-100 delegates to the memory-aware form, so the
     *  public entry point gets the same equality guard and the same span flags.
     *  Ours was a separate, weaker copy. */
    public static void setSelection(Spannable text, int start, int stop) {
        setSelection(text, start, stop, -1);
    }

    public static void setSelection(Spannable text, int index) {
        setSelection(text, index, index);
    }

    public static void selectAll(Spannable text) {
        if (text == null) return;
        setSelection(text, 0, text.length());
    }

    public static void removeSelection(Spannable text) {
        if (text == null) return;
        try {
            text.removeSpan(SELECTION_START);
            text.removeSpan(SELECTION_END);
        } catch (Throwable ignored) {}
    }

    public static void extendSelection(android.text.Spannable text, int index) {
        // AOSP Selection.java:167-169 moves only the selection EDGE
        // (SELECTION_END), leaving the anchor (SELECTION_START) alone. Writing
        // both spans -- which setSelection(text, getSelectionStart(text), index)
        // does -- is equivalent as long as getSelectionStart() answers, but the
        // AOSP form is also correct when the text has no selection yet.
        if (text == null) return;
        try {
            if (text.getSpanStart(SELECTION_END) != index) {
                text.setSpan(SELECTION_END, index, index, SPAN_POINT_POINT);
            }
        } catch (Throwable ignored) {}
    }

    // ---------------------------------------------------------------------
    // Moving the selection within a Layout.
    //
    // AOSP Selection.java:190-540. These are what BaseMovementMethod /
    // ArrowKeyMovementMethod call for every arrow key, shift-arrow, Home/End
    // and ctrl-Up/Down in an EditText, so all seventeen being absent meant a
    // NoSuchMethodError on the FIRST cursor key press in any app that installs
    // a movement method.
    //
    // ⚠ AOSP threads a "memory horizontal" through the vertical moves (a
    // SELECTION_MEMORY span plus a MemoryTextWatcher, Selection.java:104-146)
    // so that walking down through a short line and back up returns to the
    // ORIGINAL column instead of the short line's end. That machinery is
    // reproduced below with the same span identity and the same watcher
    // lifetime, because without it repeated Up/Down drifts the caret left --
    // a difference a user sees immediately.
    // ---------------------------------------------------------------------

    private static final char PARAGRAPH_SEPARATOR = '\n';

    /** AOSP Selection.java:582-584 -- a NoCopySpan marker, like the two above. */
    private static final Object SELECTION_MEMORY = new NoCopySpan.Concrete();

    /** AOSP Selection.java:124-146 MemoryTextWatcher: the remembered column is
     *  only valid until the text changes, so the first updateMemory() installs
     *  a watcher that drops both itself and the memory span on the next edit. */
    public static final class MemoryTextWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override public void afterTextChanged(Editable s) {
            s.removeSpan(SELECTION_MEMORY);
            s.removeSpan(this);
        }
    }

    private static int getSelectionMemory(CharSequence text) {
        if (text instanceof Spanned) {
            try { return ((Spanned) text).getSpanStart(SELECTION_MEMORY); }
            catch (Throwable ignored) {}
        }
        return -1;
    }

    private static void setSelection(Spannable text, int start, int stop, int memory) {
        if (text == null) return;
        int ostart = getSelectionStart(text);
        int oend = getSelectionEnd(text);
        if (ostart != start || oend != stop) {
            try {
                // SPAN_INTERMEDIATE on the START marker is load-bearing, not
                // decoration. A caret move sets both markers, and a SpanWatcher
                // sees each setSpan separately -- so without it, an
                // onSelectionChanged listener fires TWICE per move, the first
                // time with a half-updated pair (new start, old end). AOSP's
                // TextView.spanChange skips the INTERMEDIATE half for exactly
                // that reason. The equality guard above matters for the same
                // family of reason: our SpannableStringBuilder sends
                // onSpanChanged unconditionally, so re-setting an unchanged
                // selection would storm the watcher.
                text.setSpan(SELECTION_START, start, start,
                        SPAN_POINT_POINT | Spanned.SPAN_INTERMEDIATE);
                text.setSpan(SELECTION_END, stop, stop, SPAN_POINT_POINT);
            } catch (Throwable ignored) {}
            updateMemory(text, memory);
        }
    }

    private static void extendSelection(Spannable text, int index, int memory) {
        if (text == null) return;
        try {
            if (text.getSpanStart(SELECTION_END) != index) {
                text.setSpan(SELECTION_END, index, index, SPAN_POINT_POINT);
            }
        } catch (Throwable ignored) {}
        updateMemory(text, memory);
    }

    private static void updateMemory(Spannable text, int memory) {
        if (memory > -1) {
            int currentMemory = getSelectionMemory(text);
            if (memory != currentMemory) {
                try {
                    text.setSpan(SELECTION_MEMORY, memory, memory, SPAN_POINT_POINT);
                    if (currentMemory == -1) {
                        // First value on this buffer -- install the watcher.
                        text.setSpan(new MemoryTextWatcher(), 0, text.length(),
                                Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                    }
                } catch (Throwable ignored) {}
            }
        } else {
            removeMemory(text);
        }
    }

    private static void removeMemory(Spannable text) {
        try {
            text.removeSpan(SELECTION_MEMORY);
            MemoryTextWatcher[] watchers =
                    text.getSpans(0, text.length(), MemoryTextWatcher.class);
            if (watchers != null) {
                for (MemoryTextWatcher watcher : watchers) text.removeSpan(watcher);
            }
        } catch (Throwable ignored) {}
    }

    /** AOSP Selection.java:229-261 setSelectionAndMemory(). */
    private static void setSelectionAndMemory(Spannable text, Layout layout, int line, int end,
            int direction, boolean extend) {
        int move;
        int newMemory;

        if (layout.getParagraphDirection(line)
                == layout.getParagraphDirection(line + direction)) {
            int memory = getSelectionMemory(text);
            if (memory > -1) {
                // We have a memory position
                float h = layout.getPrimaryHorizontal(memory);
                move = layout.getOffsetForHorizontal(line + direction, h);
                newMemory = memory;
            } else {
                // Create a new memory position
                float h = layout.getPrimaryHorizontal(end);
                move = layout.getOffsetForHorizontal(line + direction, h);
                newMemory = end;
            }
        } else {
            move = layout.getLineStart(line + direction);
            newMemory = -1;
        }

        if (extend) {
            extendSelection(text, move, newMemory);
        } else {
            setSelection(text, move, move, newMemory);
        }
    }

    /** AOSP Selection.java:196-227. */
    public static boolean moveUp(Spannable text, Layout layout) {
        int start = getSelectionStart(text);
        int end = getSelectionEnd(text);

        if (start != end) {
            int min = Math.min(start, end);
            int max = Math.max(start, end);

            setSelection(text, min);

            if (min == 0 && max == text.length()) {
                return false;
            }
            return true;
        } else {
            int line = layout.getLineForOffset(end);

            if (line > 0) {
                setSelectionAndMemory(text, layout, line, end, -1, false);
                return true;
            } else if (end != 0) {
                setSelection(text, 0);
                return true;
            }
        }
        return false;
    }

    /** AOSP Selection.java:268-297. */
    public static boolean moveDown(Spannable text, Layout layout) {
        int start = getSelectionStart(text);
        int end = getSelectionEnd(text);

        if (start != end) {
            int min = Math.min(start, end);
            int max = Math.max(start, end);

            setSelection(text, max);

            if (min == 0 && max == text.length()) {
                return false;
            }
            return true;
        } else {
            int line = layout.getLineForOffset(end);

            if (line < layout.getLineCount() - 1) {
                setSelectionAndMemory(text, layout, line, end, 1, false);
                return true;
            } else if (end != text.length()) {
                setSelection(text, text.length());
                return true;
            }
        }
        return false;
    }

    /** AOSP Selection.java:304-322. */
    public static boolean moveLeft(Spannable text, Layout layout) {
        int start = getSelectionStart(text);
        int end = getSelectionEnd(text);

        if (start != end) {
            setSelection(text, chooseHorizontal(layout, -1, start, end));
            return true;
        } else {
            int to = layout.getOffsetToLeftOf(end);
            if (to != end) {
                setSelection(text, to);
                return true;
            }
        }
        return false;
    }

    /** AOSP Selection.java:329-347. */
    public static boolean moveRight(Spannable text, Layout layout) {
        int start = getSelectionStart(text);
        int end = getSelectionEnd(text);

        if (start != end) {
            setSelection(text, chooseHorizontal(layout, 1, start, end));
            return true;
        } else {
            int to = layout.getOffsetToRightOf(end);
            if (to != end) {
                setSelection(text, to);
                return true;
            }
        }
        return false;
    }

    /** AOSP Selection.java:358-377. */
    public static boolean moveToParagraphStart(Spannable text, Layout layout) {
        int start = getSelectionStart(text);
        int end = getSelectionEnd(text);

        if (start != end) {
            setSelection(text, chooseHorizontal(layout, -1, start, end));
            return true;
        } else {
            int to = TextUtils.lastIndexOf(text, PARAGRAPH_SEPARATOR, start - 1);
            if (to == -1) {
                to = 0;  // If not found, use the document start offset as a paragraph start.
            }
            if (to != end) {
                setSelection(text, to);
                return true;
            }
        }
        return false;
    }

    /** AOSP Selection.java:386-404. */
    public static boolean moveToParagraphEnd(Spannable text, Layout layout) {
        int start = getSelectionStart(text);
        int end = getSelectionEnd(text);

        if (start != end) {
            setSelection(text, chooseHorizontal(layout, 1, start, end));
            return true;
        } else {
            int to = TextUtils.indexOf(text, PARAGRAPH_SEPARATOR, end + 1);
            if (to == -1) {
                to = text.length();
            }
            if (to != end) {
                setSelection(text, to);
                return true;
            }
        }
        return false;
    }

    /** AOSP Selection.java:412-423. */
    public static boolean extendToParagraphStart(Spannable text) {
        int end = getSelectionEnd(text);
        int to = TextUtils.lastIndexOf(text, PARAGRAPH_SEPARATOR, end - 1);
        if (to == -1) {
            to = 0;  // If not found, use the document start offset as a paragraph start.
        }
        if (to != end) {
            extendSelection(text, to);
            return true;
        }
        return false;
    }

    /** AOSP Selection.java:431-442. */
    public static boolean extendToParagraphEnd(Spannable text) {
        int end = getSelectionEnd(text);
        int to = TextUtils.indexOf(text, PARAGRAPH_SEPARATOR, end + 1);
        if (to == -1) {
            to = text.length();
        }
        if (to != end) {
            extendSelection(text, to);
            return true;
        }
        return false;
    }

    /** AOSP Selection.java:448-461. Note AOSP returns true on every path. */
    public static boolean extendUp(Spannable text, Layout layout) {
        int end = getSelectionEnd(text);
        int line = layout.getLineForOffset(end);

        if (line > 0) {
            setSelectionAndMemory(text, layout, line, end, -1, true);
            return true;
        } else if (end != 0) {
            extendSelection(text, 0);
            return true;
        }
        return true;
    }

    /** AOSP Selection.java:467-480. */
    public static boolean extendDown(Spannable text, Layout layout) {
        int end = getSelectionEnd(text);
        int line = layout.getLineForOffset(end);

        if (line < layout.getLineCount() - 1) {
            setSelectionAndMemory(text, layout, line, end, 1, true);
            return true;
        } else if (end != text.length()) {
            extendSelection(text, text.length(), -1);
            return true;
        }
        return true;
    }

    /** AOSP Selection.java:486-496. */
    public static boolean extendLeft(Spannable text, Layout layout) {
        int end = getSelectionEnd(text);
        int to = layout.getOffsetToLeftOf(end);

        if (to != end) {
            extendSelection(text, to);
            return true;
        }
        return true;
    }

    /** AOSP Selection.java:502-512. */
    public static boolean extendRight(Spannable text, Layout layout) {
        int end = getSelectionEnd(text);
        int to = layout.getOffsetToRightOf(end);

        if (to != end) {
            extendSelection(text, to);
            return true;
        }
        return true;
    }

    /** AOSP Selection.java:514-518. */
    public static boolean extendToLeftEdge(Spannable text, Layout layout) {
        int where = findEdge(text, layout, -1);
        extendSelection(text, where);
        return true;
    }

    /** AOSP Selection.java:520-524. */
    public static boolean extendToRightEdge(Spannable text, Layout layout) {
        int where = findEdge(text, layout, 1);
        extendSelection(text, where);
        return true;
    }

    /** AOSP Selection.java:526-530. */
    public static boolean moveToLeftEdge(Spannable text, Layout layout) {
        int where = findEdge(text, layout, -1);
        setSelection(text, where);
        return true;
    }

    /** AOSP Selection.java:532-536. */
    public static boolean moveToRightEdge(Spannable text, Layout layout) {
        int where = findEdge(text, layout, 1);
        setSelection(text, where);
        return true;
    }

    /** AOSP Selection.java:571-586 findEdge(). */
    private static int findEdge(Spannable text, Layout layout, int dir) {
        int pt = getSelectionEnd(text);
        int line = layout.getLineForOffset(pt);
        int pdir = layout.getParagraphDirection(line);

        if (dir * pdir < 0) {
            return layout.getLineStart(line);
        } else {
            int end = layout.getLineEnd(line);
            if (line == layout.getLineCount() - 1) return end;
            else return end - 1;
        }
    }

    /** AOSP Selection.java:588-624 chooseHorizontal(). */
    private static int chooseHorizontal(Layout layout, int direction, int off1, int off2) {
        int line1 = layout.getLineForOffset(off1);
        int line2 = layout.getLineForOffset(off2);

        if (line1 == line2) {
            // same line, so it goes by pure physical direction
            float h1 = layout.getPrimaryHorizontal(off1);
            float h2 = layout.getPrimaryHorizontal(off2);

            if (direction < 0) {
                return (h1 < h2) ? off1 : off2;   // to left
            } else {
                return (h1 > h2) ? off1 : off2;   // to right
            }
        } else {
            // Different lines: which one is "left" depends on the paragraph's
            // directionality. AOSP checks only ONE end and says so.
            int line = layout.getLineForOffset(off1);
            int textdir = layout.getParagraphDirection(line);

            if (textdir == direction) return Math.max(off1, off2);
            else return Math.min(off1, off2);
        }
    }
}

