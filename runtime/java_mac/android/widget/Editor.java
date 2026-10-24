package android.widget;

/** AOSP android.widget.Editor, the TEXT CARET subset
 *  (frameworks/base core/java/android/widget/Editor.java, android14-release;
 *  identical in android11-release for every member below).
 *
 *  WHY IT EXISTS (cluster E, docs/widget-fidelity-cluster_2026-09-25.md). This
 *  tree had no Editor and no caret pipeline at all: TextView.isCursorVisible()
 *  answered a constant true, setCursorVisible() discarded its argument,
 *  getOffsetForPosition() answered -1, and nothing ever painted a cursor. So a
 *  focused field showed no insertion point and a tap could not move one.
 *  Measured victims: OpenCalculator (a 2dp #4bb6e8 caret after the last digit on
 *  the emulator, x=784..787 in emu_0/emu_1, moved before the root sign by the
 *  step-2 tap in emu_2; nothing on ours) and Google Calculator (a 2dp #445e91
 *  caret at the end of the empty formula, emu_2 x=762..765).
 *
 *  WHAT IS PORTED, with AOSP's field and method names kept on purpose. Apps
 *  reach into this class by reflection -- Telegram's EditTextBoldCursor reads
 *  TextView.mEditor, Editor.mShowCursor and mCursorVisible to draw its own
 *  caret -- so the names are part of the observable surface, not decoration:
 *    - the blink clock: BLINK, mShowCursor, shouldRenderCursor, the Blink
 *      runnable, makeBlink / suspendBlink / resumeBlink and the lifecycle
 *      hooks that drive them (attach, detach, window focus, screen state);
 *    - the drawable caret: loadCursorDrawable, updateCursorPosition,
 *      clampHorizontalPosition, drawCursor;
 *    - focus: the caret half of onFocusChanged (mShowCursor, onTakeFocus,
 *      select-all, makeBlink, mTouchFocusSelected);
 *    - touch: onTouchEvent's ACTION_DOWN reset and onTouchUpEvent's
 *      "move the cursor to the tap".
 *
 *  WHAT IS NOT (deliberately; each is a separate subsystem): the insertion and
 *  selection handles and their controllers, the floating action mode, the
 *  magnifier, spell check, undo, drag and drop, the IME extract/batch-edit
 *  state and the showSoftInput-on-tap. None of them decides whether or where the
 *  caret is drawn.
 *
 *  THREADING. AOSP posts the Blink through mTextView.postDelayed, i.e. the main
 *  looper, and it touches the view tree (getLayout, invalidate). Ours goes
 *  through an OWNER-ONLY main Handler instead: during the lifecycle handshake
 *  the OS-main render drain runs plain main-queue posts concurrently with the
 *  owner loop (Handler.macapkCreateOwnerOnly's header), and a view-tree post
 *  must not land there. The runnable, its 500ms cadence and its cancel rules
 *  are AOSP's; only the queue it rides is narrowed.
 *
 *  Every entry point is reached only when TextView.MACAPK_TEXT_CARET is on (the
 *  TextView never creates an Editor otherwise), so the bisect gate is total. */
public class Editor {
    /** Editor.java:183. Half the blink period, in ms. */
    static final int BLINK = 500;

    final TextView mTextView;

    /** Editor.java:339 -- uptime at which the current blink phase started. */
    long mShowCursor;
    /** Editor.java:340 -- the magnifier forces the caret on; kept for the
     *  shouldRenderCursor shape, never set by this port. */
    boolean mRenderCursorRegardlessTiming;
    /** Editor.java:341. */
    private Blink mBlink;
    /** Editor.java:346 -- setCursorVisible's backing store. */
    boolean mCursorVisible = true;
    /** Editor.java:326 -- a tap moved focus here this gesture (didTouchFocusSelect). */
    boolean mTouchFocusSelected;
    /** Editor.java:325 -- the selection was set while unfocused, so
     *  onFocusChanged restores it after onTakeFocus (:1602-1613). */
    boolean mSelectionMoved;
    /** Editor.java:378 -- the caret drawable, loaded once (loadCursorDrawable). */
    android.graphics.drawable.Drawable mDrawableForCursor = null;
    /** Editor.java:419. */
    private android.graphics.Rect mTempRect;

    private android.os.Handler mBlinkHandler;

    Editor(TextView textView) {
        mTextView = textView;
    }

    // ---- lifecycle (Editor.java:697-760, :1080-1104, :1726-1747) ----------

    void onAttachedToWindow() {
        // "Call resumeBlink here instead of makeBlink to ensure that if mBlink
        // is not null the Blink object is uncancelled." (:727-730)
        resumeBlink();
    }

    void onDetachedFromWindow() {
        suspendBlink();
    }

    /** View.SCREEN_STATE_OFF / SCREEN_STATE_ON (AOSP View.java: 0 and 1; this
     *  tree's View does not declare the constants yet). */
    private static final int SCREEN_STATE_OFF = 0x0;
    private static final int SCREEN_STATE_ON = 0x1;

    void onScreenStateChanged(int screenState) {
        switch (screenState) {
            case SCREEN_STATE_ON:  resumeBlink();  break;
            case SCREEN_STATE_OFF: suspendBlink(); break;
            default: break;
        }
    }

    void onWindowFocusChanged(boolean hasWindowFocus) {
        if (hasWindowFocus) resumeBlink();
        else suspendBlink();
    }

    private void suspendBlink() {
        if (mBlink != null) mBlink.cancel();
    }

    private void resumeBlink() {
        if (mBlink != null) mBlink.uncancel();
        makeBlink();
    }

    // ---- the blink clock (Editor.java:975-989, :2912-2975) ---------------

    /** :975-978 -- "The default value is true, even when there is no
     *  associated Editor". */
    private boolean isCursorVisible() {
        return mCursorVisible && mTextView.isTextEditable();
    }

    /** :980-989 -- on for the first BLINK ms of every 2*BLINK window measured
     *  from mShowCursor. Pure function of the clock: onDraw asks it, and the
     *  Blink runnable only has to invalidate on the edges. */
    boolean shouldRenderCursor() {
        if (!isCursorVisible()) return false;
        if (mRenderCursorRegardlessTiming) return true;
        final long showCursorDelta = android.os.SystemClock.uptimeMillis() - mShowCursor;
        return showCursorDelta % (2 * BLINK) < BLINK;
    }

    // ---- FRAMESPEED: the caret is RECORDED on the draw after an invalidate ----
    //
    // WHAT AOSP DRAWS. The blink is two separate things on a device: the Blink
    // runnable INVALIDATES the caret rect every BLINK ms (:2950-2967), and the
    // draw decides on/off from the clock (shouldRenderCursor, :980-989). The
    // draw only runs when the TextView was invalidated: a frame caused by any
    // OTHER view replays the TextView's recorded display list, because
    // View.updateDisplayListIfDirty (android14 View.java:22703-22715) re-records
    // only when PFLAG_DRAWING_CACHE_VALID was cleared or a re-record was forced,
    // and what does either is invalidateInternal(..., invalidateCache=true)
    // (View.java:19984-19987) or a requestLayout/forceLayout -- i.e. something
    // done to THIS view. So on Android the caret flips exactly when the Blink's
    // invalidate lands, never earlier.
    //
    // WHAT WE DID. Our software traversal re-runs TextView.onDraw on EVERY frame
    // and re-read the clock each time. Any frame drawn between the clock edge
    // (mShowCursor + k*BLINK) and the Blink's own invalidate -- the idle
    // heartbeat above all -- flipped the caret first, and the Blink's frame then
    // changed nothing. The Blink re-posts BLINK ms after it RAN (:2965, same as
    // AOSP), so its lag behind the clock edge grows every tick and the heartbeat
    // lands inside that window for long stretches. Measured on the 2026-09-25
    // tablet fleet capture (build java=b946d50a): OpenCalculator ran 10/300
    // draw-only Blink frames throughout, yet from ~100 s in EVERY heartbeat was
    // an "[heartbeat-diff] un-invalidated change" at the caret's bbox
    // (x3152-3168, y20-272), 10/10 per window; the unit converter's caret
    // (x64-80, y218-267) the same, 33 of 34 windows. Those two carets were the
    // ONLY un-invalidated changes in that capture: the other 13 apps logging
    // [heartbeat-diff] read changed=0 in every window. That kept the heartbeat
    // pinned at 30 vsyncs and made the Blink's frames empty.
    //
    // THE FIX. The on/off answer is recorded by the first draw after this view
    // was invalidated (TextView's invalidate / requestLayout / forceLayout
    // overrides call macapkNoteInvalidated; the last two set PFLAG_INVALIDATED
    // on a device, View.java:27039-27040, which draw() turns into
    // mRecreateDisplayList at :23601) or after the blink phase restarted (makeBlink /
    // onFocusChanged move mShowCursor -- an edit, a move, a focus gain), and
    // every later draw REPLAYS it -- the display-list semantics, for the one
    // clock-dependent thing TextView draws. Everything else about the caret
    // (position, drawable, visibility, focus) is still computed live.
    // MACAPK_FRAMESPEED_CARET=0 restores the per-draw clock read exactly.
    // Gate: debug/framespeed_gate.sh caret (positive control: the =0 arm must
    // show the early flip). docs/frame-speed-caret-clip-cull_2026-09-26.md.
    static final boolean FRAMESPEED_CARET =
        !"0".equals(System.getenv("MACAPK_FRAMESPEED_CARET"));
    private boolean mMacapkCaretRecorded;
    private boolean mMacapkCaretDrawnOn;
    private long mMacapkCaretShowCursorAtRecord;
    private boolean mMacapkCaretInvalidated = true;

    /** The TextView was invalidated: the next draw re-records the caret. */
    void macapkNoteInvalidated() { mMacapkCaretInvalidated = true; }

    /** The draw's "is the caret on" -- shouldRenderCursor() at the recording
     *  draw, replayed until the view is invalidated or the phase restarts. */
    boolean macapkShouldDrawCursor() {
        if (!FRAMESPEED_CARET) return shouldRenderCursor();
        if (mMacapkCaretRecorded && !mMacapkCaretInvalidated
                && mMacapkCaretShowCursorAtRecord == mShowCursor && isCursorVisible()) {
            return mMacapkCaretDrawnOn;
        }
        final boolean on = shouldRenderCursor();
        mMacapkCaretRecorded = true;
        mMacapkCaretDrawnOn = on;
        mMacapkCaretShowCursorAtRecord = mShowCursor;
        mMacapkCaretInvalidated = false;
        return on;
    }

    /** :2912-2923 -- focused, visible, editable, with a COLLAPSED selection. */
    private boolean shouldBlink() {
        if (!isCursorVisible() || !mTextView.isFocused()
                || mTextView.getWindowVisibility() != android.view.View.VISIBLE) return false;
        final int start = mTextView.getSelectionStart();
        if (start < 0) return false;
        final int end = mTextView.getSelectionEnd();
        if (end < 0) return false;
        return start == end;
    }

    /** :2925-2937 -- restart the phase (the caret is ON right after any edit,
     *  move or focus gain) and schedule the next edge. */
    void makeBlink() {
        if (shouldBlink()) {
            mShowCursor = android.os.SystemClock.uptimeMillis();
            if (mBlink == null) mBlink = new Blink();
            mBlink.uncancel();
            removeBlink(mBlink);
            postBlink(mBlink, BLINK);
        } else {
            if (mBlink != null) removeBlink(mBlink);
        }
    }

    /** :2944-2948 (@VisibleForTesting public). */
    public boolean isBlinking() {
        if (mBlink == null) return false;
        return !mBlink.mCancelled;
    }

    /** :2950-2975. Invalidates ONLY the caret's rect, once per BLINK, and only
     *  while shouldBlink() -- so an idle focused field costs two small
     *  invalidates a second and nothing in between. The draw itself never
     *  invalidates. */
    private class Blink implements Runnable {
        private boolean mCancelled;

        public void run() {
            if (mCancelled) return;
            removeBlink(this);
            if (shouldBlink()) {
                if (mTextView.getLayout() != null) {
                    mTextView.invalidateCursorPath();
                }
                postBlink(this, BLINK);
            }
        }

        void cancel() {
            if (!mCancelled) {
                removeBlink(this);
                mCancelled = true;
            }
        }

        void uncancel() {
            mCancelled = false;
        }
    }

    private android.os.Handler blinkHandler() {
        android.os.Handler h = mBlinkHandler;
        if (h == null) {
            android.os.Looper main = android.os.Looper.getMainLooper();
            if (main == null) return null;
            mBlinkHandler = h = android.os.Handler.macapkCreateOwnerOnly(main);
        }
        return h;
    }

    private void postBlink(Runnable r, long delay) {
        try {
            android.os.Handler h = blinkHandler();
            if (h != null) h.postDelayed(r, delay);
        } catch (Throwable ignored) {
            // No main looper (an offline harness): the caret still renders
            // from the clock; only the edge invalidation is lost.
        }
    }

    private void removeBlink(Runnable r) {
        try {
            android.os.Handler h = mBlinkHandler;
            if (h != null) h.removeCallbacks(r);
        } catch (Throwable ignored) {}
    }

    // ---- focus and touch (Editor.java:1560-1654, :1773-1805, :2695-2716) --

    /** The caret half of :1560-1654. */
    void onFocusChanged(boolean focused, int direction) {
        mShowCursor = android.os.SystemClock.uptimeMillis();
        if (focused) {
            final int selStart = mTextView.getSelectionStart();
            final int selEnd = mTextView.getSelectionEnd();
            final android.text.Editable text = mTextView.getEditableText();
            // !mFrozenWithFocus is always true here (no saved-state freeze).
            // getLastTapPosition() needs the selection controller, which is not
            // ported, so AOSP's own `lastTapPosition < 0` branch applies.
            final android.text.method.MovementMethod movement = mTextView.getMovementMethod();
            if (movement != null && text != null) {
                try { movement.onTakeFocus(mTextView, text, direction); } catch (Throwable ignored) {}
            }
            // "Someone intentionally set the selection, so let them do whatever
            // it is that they wanted to do instead of the default on-focus
            // behavior." (:1604-1613)
            if (mSelectionMoved && selStart >= 0 && selEnd >= 0 && text != null) {
                android.text.Selection.setSelection(text, selStart, selEnd);
            }
            if (mTextView.macapkSelectAllOnFocus()) {
                mTextView.selectAll();
            }
            mTouchFocusSelected = true;
            mSelectionMoved = false;
            makeBlink();
        }
    }

    /** :1798-1804 -- the per-gesture reset; "it will be re-set if
     *  super.onTouchEvent causes focus to move to the view". */
    void onTouchEvent(android.view.MotionEvent event) {
        if (event != null && event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
            mTouchFocusSelected = false;
        }
    }

    /** :2695-2716 -- a finished tap on a focused editable view moves the
     *  insertion point to the tapped offset. The selection-mode, easy-correction
     *  and action-mode branches after it are not ported. */
    void onTouchUpEvent(android.view.MotionEvent event) {
        final boolean selectAllGotFocus =
            mTextView.macapkSelectAllOnFocus() && mTextView.didTouchFocusSelect();
        final android.text.Editable text = mTextView.getEditableText();
        if (!selectAllGotFocus && text != null && text.length() > 0) {
            final int offset = mTextView.getOffsetForPosition(event.getX(), event.getY());
            if (offset >= 0) android.text.Selection.setSelection(text, Math.min(offset, text.length()));
        }
    }

    // ---- the drawable caret (Editor.java:2323-2330, :2389-2403, :2826-2878,
    //      :6512-6516) ------------------------------------------------------

    /** :6512-6516 -- loaded ONCE. AOSP never reloads it, which is why its own
     *  javadoc warns that a later setTextCursorDrawable "will not be visible
     *  until the cursor is hidden and then drawn again". Mirrored as is. */
    void loadCursorDrawable() {
        if (mDrawableForCursor == null) {
            mDrawableForCursor = mTextView.getTextCursorDrawable();
        }
    }

    /** :2389-2403 -- bounds of the caret drawable in LAYOUT coordinates, from
     *  the line box and the primary horizontal of the selection. */
    void updateCursorPosition() {
        updateCursorPosition(mTextView.getLayout());
    }

    /** The same, over the Layout the caller already holds (the draw path). */
    void updateCursorPosition(android.text.Layout layout) {
        loadCursorDrawable();
        if (mDrawableForCursor == null) return;
        if (layout == null) return;
        final int offset = mTextView.getSelectionStart();
        final int line = layout.getLineForOffset(offset);
        final int top = layout.getLineTop(line);
        final int bottom = layout.getLineBottom(line, /* includeLineSpacing= */ false);
        // AOSP passes getPrimaryHorizontal(offset, shouldClampCursor(line)).
        // This tree's Layout is LTR-only and its getPrimaryHorizontal already
        // clamps into the line's visible range, so the clamped and unclamped
        // answers coincide (Layout.getCursorPath's header records the same).
        updateCursorPosition(top, bottom, layout.getPrimaryHorizontal(offset));
    }

    /** :2826-2835 -- the drawable is widened by its own padding: for
     *  text_cursor_material (an <inset android:inset="2dp"> around a 2dp
     *  rectangle) the bounds are 6dp wide and 2dp taller on each side, and the
     *  visible 2dp bar lands exactly on [horizontal, horizontal + 2dp) x
     *  [top, bottom]. */
    private void updateCursorPosition(int top, int bottom, float horizontal) {
        loadCursorDrawable();
        final int left = clampHorizontalPosition(mDrawableForCursor, horizontal);
        final int width = mDrawableForCursor.getIntrinsicWidth();
        mDrawableForCursor.setBounds(left, top - mTempRect.top, left + width,
                bottom + mTempRect.bottom);
    }

    /** :2848-2878, verbatim. At the right edge the drawable is pulled inside
     *  the view (so an end-aligned caret stays visible in the last 2dp of the
     *  content box -- OpenCalculator's x=784..787 with a 788 content edge); at
     *  the left edge it is pushed out by its own padding. */
    private int clampHorizontalPosition(android.graphics.drawable.Drawable drawable, float horizontal) {
        horizontal = Math.max(0.5f, horizontal - 0.5f);
        if (mTempRect == null) mTempRect = new android.graphics.Rect();

        int drawableWidth = 0;
        if (drawable != null) {
            drawable.getPadding(mTempRect);
            drawableWidth = drawable.getIntrinsicWidth();
        } else {
            mTempRect.setEmpty();
        }

        final int scrollX = mTextView.getScrollX();
        final float horizontalDiff = horizontal - scrollX;
        final int viewClippedWidth = mTextView.getWidth() - mTextView.getCompoundPaddingLeft()
                - mTextView.getCompoundPaddingRight();

        final int left;
        if (horizontalDiff >= (viewClippedWidth - 1f)) {
            // at the rightmost position
            left = viewClippedWidth + scrollX - (drawableWidth - mTempRect.right);
        } else if (Math.abs(horizontalDiff) <= 1f
                || (android.text.TextUtils.isEmpty(mTextView.getText())
                        && (TextView.VERY_WIDE - scrollX) <= (viewClippedWidth + 1f)
                        && horizontal <= 1f)) {
            // at the leftmost position
            left = scrollX - mTempRect.left;
        } else {
            left = (int) horizontal - mTempRect.left;
        }
        return left;
    }

    /** :2323-2330. */
    void drawCursor(android.graphics.Canvas canvas, int cursorOffsetVertical) {
        final boolean translate = cursorOffsetVertical != 0;
        if (translate) canvas.translate(0, cursorOffsetVertical);
        if (mDrawableForCursor != null) {
            mDrawableForCursor.draw(canvas);
        }
        if (translate) canvas.translate(0, -cursorOffsetVertical);
    }

    /** :2820-2824 (@VisibleForTesting public). */
    public android.graphics.drawable.Drawable getCursorDrawable() {
        return mDrawableForCursor;
    }
}
