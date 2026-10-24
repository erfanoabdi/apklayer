// com.android.internal.widget.DialogTitle -- the TextView every platform alert
// dialog uses for its title. Port of android14-release
// core/java/com/android/internal/widget/DialogTitle.java (the whole class is a
// constructor set plus the one onMeasure below).
//
// AOSP, verbatim (:51-78):
//
//     @Override
//     protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
//         super.onMeasure(widthMeasureSpec, heightMeasureSpec);
//         final Layout layout = getLayout();
//         if (layout != null) {
//             final int lineCount = layout.getLineCount();
//             if (lineCount > 0) {
//                 final int ellipsisCount = layout.getEllipsisCount(lineCount - 1);
//                 if (ellipsisCount > 0) {
//                     setSingleLine(false);
//                     setMaxLines(2);
//                     final TypedArray a = mContext.obtainStyledAttributes(null,
//                             android.R.styleable.TextAppearance, android.R.attr.textAppearanceMedium,
//                             android.R.style.TextAppearance_Medium);
//                     final int textSize = a.getDimensionPixelSize(
//                             android.R.styleable.TextAppearance_textSize, 0);
//                     if (textSize != 0) {
//                         // textSize is already expressed in pixels
//                         setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
//                     }
//                     a.recycle();
//                     super.onMeasure(widthMeasureSpec, heightMeasureSpec);
//                 }
//             }
//         }
//     }
//
// i.e. a title that does not fit on its one line is re-laid out on TWO lines at
// the theme's textAppearanceMedium size (18sp in both the root Theme and
// Theme.Material). Both alert_dialog.xml (:53) and
// alert_dialog_title_material.xml (:44) use this class for @id/alertTitle, so a
// long title wraps on every platform AlertDialog. We had no such class, so
// android.app.AlertDialog built a plain single-line ellipsizing TextView and a
// long title always ended in "..." on ONE line. Measured victim: Geometry Dash
// Meltdown's AppLovin consent dialog, whose title the emulator shows on two
// lines (docs/fleet-games-smashhit-gd-meltdown-jumplite_2026-09-26.md, G3).
//
// Three details differ from a literal transcription, each for a reason:
//
//   - WHICH layout the ellipsis is read from. AOSP's getLayout() is the layout
//     onMeasure just rebuilt at the width it was offered. Ours rebuilds lazily
//     from getWidth(), i.e. the width of the LAST layout pass, and only falls
//     back to the measured width before the first layout. A dialog is measured
//     at several widths per traversal (Dialog's config_prefDialogWidth pre-pass
//     ladder), so reading getLayout() would test the new measure against a
//     stale width. The equivalent question for a single-line title -- "does the
//     displayed text fit in the width this measure produced?" -- is answered
//     from the measured width directly. Once the title is on two lines AOSP's
//     test can only re-apply the same values, so the one-line question is the
//     whole of the behaviour.
//   - IDEMPOTENCE. AOSP calls the three setters on every measure that finds an
//     ellipsis. Ours each request a layout, and our overlay stack re-measures a
//     floating window on every frame, so re-applying unchanged values would
//     request a layout per frame forever for a title longer than two lines.
//     Each setter runs only when its value actually changes, which leaves every
//     observable result identical.
//   - The TextAppearance lookup. There is no android.R.styleable in this tree;
//     the one attribute AOSP reads (TextAppearance_textSize) is passed as a
//     one-element array of android.R.attr.textSize, which is the same query.
package com.android.internal.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.TextView;

public class DialogTitle extends TextView {
    /** android.R.attr.textAppearanceMedium / textSize and
     *  android.R.style.TextAppearance_Medium (public-final.xml; frozen). */
    private static final int ATTR_TEXT_APPEARANCE_MEDIUM = 0x01010041;
    private static final int ATTR_TEXT_SIZE = 0x01010095;
    private static final int STYLE_TEXT_APPEARANCE_MEDIUM = 0x01030044;

    /** Whether this title has already switched to the two-line form (our copy of
     *  "maxLines == 2 && !singleLine", which TextView does not expose). */
    private boolean mMacapkWrapped;

    public DialogTitle(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public DialogTitle(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public DialogTitle(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DialogTitle(Context context) {
        super(context);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (mMacapkWrapped || !macapkLastLineEllipsized()) return;
        mMacapkWrapped = true;
        setSingleLine(false);
        setMaxLines(2);
        final int textSize = macapkMediumTextSizePx();
        if (textSize != 0 && getTextSize() != textSize) {
            // textSize is already expressed in pixels
            setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /** AOSP's `layout.getEllipsisCount(lineCount - 1) > 0` for the single-line
     *  title this is before it wraps: the displayed text is wider than the text
     *  box this measure produced (see the class comment for why the measured
     *  width and not getLayout()). Only a view that is still single-line with an
     *  ellipsize mode set can be ellipsized at all. */
    private boolean macapkLastLineEllipsized() {
        if (!isSingleLine() || getEllipsize() == null) return false;
        final CharSequence text = getText();
        if (text == null || text.length() == 0) return false;
        final int avail = getMeasuredWidth() - getCompoundPaddingLeft() - getCompoundPaddingRight();
        if (avail <= 0) return false;
        final android.text.TextPaint paint = getPaint();
        if (paint == null) return false;
        return paint.measureText(text, 0, text.length()) > avail;
    }

    /** `obtainStyledAttributes(null, TextAppearance, textAppearanceMedium,
     *  TextAppearance_Medium).getDimensionPixelSize(TextAppearance_textSize, 0)`. */
    private int macapkMediumTextSizePx() {
        android.content.res.TypedArray a = null;
        try {
            a = getContext().obtainStyledAttributes(null, new int[] { ATTR_TEXT_SIZE },
                    ATTR_TEXT_APPEARANCE_MEDIUM, STYLE_TEXT_APPEARANCE_MEDIUM);
            return a.getDimensionPixelSize(0, 0);
        } catch (Throwable t) {
            return 0;
        } finally {
            if (a != null) try { a.recycle(); } catch (Throwable ignored) { }
        }
    }
}
