// android.widget.Spinner -- AbsSpinner that shows the selected item and, on
// click, presents a list of choices (dialog or dropdown mode). Was a bare
// `extends Object` stub (broke instanceof AbsSpinner/AdapterView and any
// spinner inflate). Now a real AbsSpinner subclass with the full selection
// model, prompt, and dropdown-mode surface. The choice popup is presented via
// a ListPopupWindow (best-effort render); the selection model and the
// OnItemSelected callback are fully faithful regardless. AOSP:
//   frameworks/base/core/java/android/widget/Spinner.java
//   public class Spinner extends AbsSpinner implements DialogInterface.OnClickListener
package android.widget;

import android.content.Context;
import android.content.DialogInterface;
import android.view.View;

public class Spinner extends AbsSpinner implements DialogInterface.OnClickListener {
    public static final int MODE_DIALOG   = 0;
    public static final int MODE_DROPDOWN = 1;

    private CharSequence mPrompt;
    private int mGravity = android.view.Gravity.CENTER;
    private final int mSpinnerMode;
    private final ListPopupWindow mPopup;

    public Spinner(Context context) { this(context, MODE_DROPDOWN); }
    public Spinner(Context context, int mode) {
        super(context);
        mSpinnerMode = mode;
        mPopup = macapkNewDropdown(context);
        installClick();
        macapkReadGravity(context, null, 0);
    }
    public Spinner(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
        mSpinnerMode = MODE_DROPDOWN;
        mPopup = macapkNewDropdown(context);
        installClick();
        macapkReadGravity(context, attrs, 0);
    }
    /** AOSP API 21 four-argument constructor. AOSP's three-argument form is
     *  exactly this(context, attrs, defStyleAttr, 0), so delegating the other
     *  way is identical for defStyleRes == 0; a non-zero defStyleRes is
     *  DROPPED here, as in the existing FrameLayout/Button four-argument forms
     *  (TextView, ProgressBar and their subclasses honour it). Without it an
     *  app subclass calling the four-argument
     *  super() dies with NoSuchMethodError; see debug/ctorsdk_gate.sh. */
    public Spinner(Context context, android.util.AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        this(context, attrs, defStyleAttr);
    }
    public Spinner(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mSpinnerMode = MODE_DROPDOWN;
        mPopup = macapkNewDropdown(context);
        installClick();
        macapkReadGravity(context, attrs, defStyleAttr);
    }

    /** Labelled bisect gate (wave 5, 2026-09-26): AOSP Spinner is clickable
     *  through its STYLE (Widget.Spinner sets `clickable` true, core/res
     *  styles.xml:644-656) and shows its popup from its own performClick
     *  override, only when no OnClickListener handled the click
     *  (Spinner.java :785-797):
     *      boolean handled = super.performClick();
     *      if (!handled) { handled = true; if (!mPopup.isShowing()) mPopup.show(...); }
     *  The legacy port instead installed an OnClickListener on itself whose
     *  onClick called performClick() -- and performClick() began with
     *  super.performClick(), which invokes that same listener. So every tap on
     *  a Spinner recursed until StackOverflowError and the popup was never
     *  reached (measured offline: debug/w5widgets_gate.sh row sp-click, whose
     *  control reproduces the overflow). MACAPK_SPINNER_CLICK_AOSP=0 restores
     *  the self-listener. */
    static final boolean MACAPK_SPINNER_CLICK_AOSP =
            !"0".equals(System.getenv("MACAPK_SPINNER_CLICK_AOSP"));

    private void installClick() {
        if (MACAPK_SPINNER_CLICK_AOSP) {
            // Stands in for Widget.Spinner's `clickable` attribute, which this
            // constructor does not read.
            setClickable(true);
            return;
        }
        setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { performClick(); }
        });
    }

    /** AOSP Spinner's constructor: `mGravity = a.getInt(R.styleable.Spinner_gravity,
     *  Gravity.CENTER)` from the styled attributes, whose default style is
     *  R.attr.spinnerStyle for every constructor that does not name one
     *  (Spinner(Context) -> Spinner(Context, AttributeSet) ->
     *  this(context, attrs, R.attr.spinnerStyle)). Only on the ported layout
     *  (MACAPK_SPINNER_RECYCLE), the one consumer of mGravity: it places the
     *  selected child, and the ported measure lets that child be narrower than
     *  the spinner, so the theme's gravity (Widget.Material.Spinner answers
     *  start|center_vertical) becomes visible. Framework ids from
     *  android.R.attr: gravity 0x010100af, spinnerStyle 0x01010081. */
    private void macapkReadGravity(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        if (!MACAPK_SPINNER_RECYCLE || context == null) return;
        try {
            final android.content.res.TypedArray a = context.obtainStyledAttributes(attrs,
                    new int[] { 0x010100af /* android.R.attr.gravity */ },
                    defStyleAttr != 0 ? defStyleAttr : 0x01010081 /* android.R.attr.spinnerStyle */, 0);
            mGravity = a.getInt(0, android.view.Gravity.CENTER);
            a.recycle();
        } catch (Throwable ignored) {}
    }

    @Override int macapkSelectionGravity() { return mGravity; }

    /** AOSP Spinner.onMeasure (:613-622): an AT_MOST spinner is as wide as its
     *  widest item (measureContentWidth), capped at the spec. Ported-path only
     *  (MACAPK_SPINNER_WIDTH, see AbsSpinner). */
    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (!MACAPK_SPINNER_WIDTH) return;
        if (mPopup != null && MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.AT_MOST) {
            final int measuredWidth = getMeasuredWidth();
            setMeasuredDimension(Math.min(Math.max(measuredWidth,
                    measureContentWidth(getAdapter(), getBackground())),
                    MeasureSpec.getSize(widthMeasureSpec)),
                    getMeasuredHeight());
        }
    }

    /** AOSP Spinner.MAX_ITEMS_MEASURED (:75). */
    private static final int MAX_ITEMS_MEASURED = 15;
    private final android.graphics.Rect mTempRect = new android.graphics.Rect();

    /** AOSP Spinner.measureContentWidth (:859-899). The UNSPECIFIED specs use
     *  makeMeasureSpec where AOSP writes makeSafeMeasureSpec; the two differ
     *  only for targetSdk < M, where AOSP sends size 0 with UNSPECIFIED. */
    int measureContentWidth(SpinnerAdapter adapter, android.graphics.drawable.Drawable background) {
        if (adapter == null) {
            return 0;
        }
        int width = 0;
        View itemView = null;
        int itemType = 0;
        final int widthMeasureSpec =
            MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.UNSPECIFIED);
        final int heightMeasureSpec =
            MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.UNSPECIFIED);
        // Make sure the number of items we'll measure is capped. If it's a huge data set
        // with wildly varying sizes, oh well.
        int start = Math.max(0, getSelectedItemPosition());
        final int end = Math.min(adapter.getCount(), start + MAX_ITEMS_MEASURED);
        final int count = end - start;
        start = Math.max(0, start - (MAX_ITEMS_MEASURED - count));
        for (int i = start; i < end; i++) {
            final int positionType = adapter.getItemViewType(i);
            if (positionType != itemType) {
                itemType = positionType;
                itemView = null;
            }
            itemView = adapter.getView(i, itemView, this);
            if (itemView.getLayoutParams() == null) {
                itemView.setLayoutParams(new android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            itemView.measure(widthMeasureSpec, heightMeasureSpec);
            width = Math.max(width, itemView.getMeasuredWidth());
        }
        // Add background padding to measured width
        if (background != null) {
            background.getPadding(mTempRect);
            width += mTempRect.left + mTempRect.right;
        }
        return width;
    }

    @Override public boolean performClick() {
        if (MACAPK_SPINNER_CLICK_AOSP) {
            // AOSP :785-797: a registered OnClickListener wins; otherwise the
            // click opens the popup.
            boolean handled = super.performClick();
            if (!handled) {
                handled = true;
                if (!mPopup.isShowing()) macapkShowPopup();
            }
            return handled;
        }
        super.performClick();
        macapkShowPopup();
        return true;
    }

    /** The popup half of performClick, unchanged from the legacy port. */
    private void macapkShowPopup() {
        if (mAdapter == null || mAdapter.getCount() == 0) return;
        mPopup.setAdapter((ListAdapter) wrapForPopup());
        mPopup.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view,
                    int position, long id) {
                setSelection(position);
                mPopup.dismiss();
            }
        });
        mPopup.setAnchorView(this);
        mPopup.show();
    }

    // The Spinner adapter is a SpinnerAdapter; ListPopupWindow expects a
    // ListAdapter. Most spinner adapters (ArrayAdapter/BaseAdapter) implement
    // both, so reuse directly when possible, else wrap.
    private Adapter wrapForPopup() {
        if (mAdapter instanceof ListAdapter) return mAdapter;
        return new BaseAdapter() {
            @Override public int getCount() { return mAdapter.getCount(); }
            @Override public Object getItem(int p) { return mAdapter.getItem(p); }
            @Override public long getItemId(int p) { return mAdapter.getItemId(p); }
            @Override public View getView(int p, View c, android.view.ViewGroup g) {
                return mAdapter.getDropDownView(p, c, g);
            }
        };
    }

    @Override public void onClick(DialogInterface dialog, int which) {
        setSelection(which);
        dialog.dismiss();
    }

    public void setPrompt(CharSequence prompt) { mPrompt = prompt; }
    public void setPromptId(int promptId) { mPrompt = getContext().getString(promptId); }
    public CharSequence getPrompt() { return mPrompt; }

    public void setGravity(int gravity) { mGravity = gravity; requestLayout(); }
    public int getGravity() { return mGravity; }

    public int getDropDownHorizontalOffset() { return mPopup.getHorizontalOffset(); }
    public void setDropDownHorizontalOffset(int pixels) { mPopup.setHorizontalOffset(pixels); }
    public int getDropDownVerticalOffset() { return mPopup.getVerticalOffset(); }
    public void setDropDownVerticalOffset(int pixels) { mPopup.setVerticalOffset(pixels); }
    public int getDropDownWidth() { return mPopup.getWidth(); }
    public void setDropDownWidth(int pixels) { mPopup.setWidth(pixels); }
    public int getSpinnerMode() { return mSpinnerMode; }

    // AOSP Spinner.setDropDownViewTheme/getDropDownViewTheme was `{}` /
    // hardcoded null with no backing field. Scoped to the accessor-round-trip
    // fix: AOSP's real setter also rebuilds the drop-down adapter's
    // LayoutInflater against the new theme so dropdown item views inflate
    // themed differently from the Spinner itself -- this class's dropdown
    // (mPopup, adjacent accessors above) does not model a separate inflation
    // theme, so wiring that through is a separate, larger feature.
    private android.content.res.Resources.Theme mDropDownViewTheme;
    public void setDropDownViewTheme(android.content.res.Resources.Theme theme) { mDropDownViewTheme = theme; }
    public android.content.res.Resources.Theme getDropDownViewTheme() { return mDropDownViewTheme; }

    private android.graphics.drawable.Drawable mPopupBackground;
    /** AOSP Spinner.setPopupBackgroundDrawable (API 16): background of the
     *  dropdown popup (drawn by the overlay stack when the dropdown shows). */
    public void setPopupBackgroundDrawable(android.graphics.drawable.Drawable background) {
        mPopupBackground = background;
    }
    public android.graphics.drawable.Drawable getPopupBackground() { return mPopupBackground; }
    public void setPopupBackgroundResource(int resId) { mPopupBackground = null; }


    public android.content.Context getPopupContext() { return getContext(); }

    /** AOSP Spinner's two widest constructors:
     *    Spinner(Context, AttributeSet, int defStyleAttr, int defStyleRes, int mode)
     *    Spinner(Context, AttributeSet, int, int, int mode, Resources.Theme popupTheme)
     *  Unlike the 3-arg form above, these take the DIALOG/DROPDOWN mode
     *  explicitly rather than defaulting to MODE_DROPDOWN, so a Spinner built
     *  through them honours the caller's mode. The popupTheme form additionally
     *  inflates the drop-down against another theme; this port builds no themed
     *  popup context (same as ListPopupWindow's themed constructors), so that
     *  argument has nothing to apply to. */
    public Spinner(Context context, android.util.AttributeSet attrs,
            int defStyleAttr, int defStyleRes, int mode) {
        super(context, attrs, defStyleAttr);
        mSpinnerMode = mode;
        mPopup = macapkNewDropdown(context);
        installClick();
        macapkReadGravity(context, attrs, defStyleAttr);
    }
    public Spinner(Context context, android.util.AttributeSet attrs,
            int defStyleAttr, int defStyleRes, int mode,
            android.content.res.Resources.Theme popupTheme) {
        this(context, attrs, defStyleAttr, defStyleRes, mode);
    }
    /** MACAPK_OVERLAY_NOTFOCUSABLE_NONMODAL (shared with WindowOverlayStack),
     *  default ON: AOSP Spinner.DropdownPopup's constructor calls
     *  `setAnchorView(Spinner.this); setModal(true); setPromptPosition(...)`
     *  (android14 Spinner.java:1207-1213), and ListPopupWindow.setModal is
     *  `mPopup.setFocusable(modal)`. Without it this popup is
     *  NOT_FOCUSABLE|WATCH_OUTSIDE_TOUCH, which the overlay stack now treats as
     *  non-modal (AOSP LayoutParams.isModal), so a tap outside the dropdown would
     *  dismiss it AND click whatever is behind it. =0 restores the old popup
     *  (not focusable; the old stack swallowed the outside tap). AutoCompleteTextView
     *  keeps its non-modal popup, which is what AOSP does. */
    private static ListPopupWindow macapkNewDropdown(Context context) {
        ListPopupWindow p = new ListPopupWindow(context);
        if (!"0".equals(System.getenv("MACAPK_OVERLAY_NOTFOCUSABLE_NONMODAL"))) p.setModal(true);
        return p;
    }
}
