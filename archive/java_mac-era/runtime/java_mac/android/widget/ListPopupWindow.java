// android.widget.ListPopupWindow -- a PopupWindow that contains a ListView,
// anchored to a view. Used by Spinner's dropdown and AutoCompleteTextView's
// suggestion list. Was Absent. The list model + item-click dispatch are
// faithful; the on-screen popup render is best-effort (delegated to the
// in-tree PopupWindow, which tracks show/dismiss state). AOSP:
//   frameworks/base/core/java/android/widget/ListPopupWindow.java
//   public class ListPopupWindow
package android.widget;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

public class ListPopupWindow {
    public static final int POSITION_PROMPT_ABOVE = 0;
    public static final int POSITION_PROMPT_BELOW = 1;
    public static final int MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT;
    public static final int WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT;
    public static final int INPUT_METHOD_FROM_FOCUSABLE = 0;
    public static final int INPUT_METHOD_NEEDED = 1;
    public static final int INPUT_METHOD_NOT_NEEDED = 2;

    private final Context mContext;
    private final PopupWindow mPopup;
    private ListAdapter mAdapter;
    private ListView mList;
    private View mAnchor;
    private AdapterView.OnItemClickListener mItemClickListener;
    private AdapterView.OnItemSelectedListener mItemSelectedListener;

    private int mWidth = WRAP_CONTENT;
    private int mHeight = WRAP_CONTENT;
    private int mHorizontalOffset = 0;
    private int mVerticalOffset = 0;
    private boolean mModal;

    public ListPopupWindow(Context context) {
        mContext = context;
        mPopup = new PopupWindow(context);
    }
    public ListPopupWindow(Context context, android.util.AttributeSet attrs) {
        this(context);
    }
    public ListPopupWindow(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        this(context);
    }
    public ListPopupWindow(Context context, android.util.AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        this(context);
    }

    public void setAdapter(ListAdapter adapter) {
        mAdapter = adapter;
        if (mList != null) mList.setAdapter(adapter);
    }

    public void setAnchorView(View anchor) { mAnchor = anchor; }
    public View getAnchorView() { return mAnchor; }

    public void setWidth(int width) { mWidth = width; }
    public int  getWidth() { return mWidth; }
    public void setHeight(int height) { mHeight = height; }
    public int  getHeight() { return mHeight; }
    public void setContentWidth(int width) { mWidth = width; }

    public void setHorizontalOffset(int offset) { mHorizontalOffset = offset; }
    public int  getHorizontalOffset() { return mHorizontalOffset; }
    public void setVerticalOffset(int offset) { mVerticalOffset = offset; }
    public int  getVerticalOffset() { return mVerticalOffset; }

    public void setModal(boolean modal) {
        mModal = modal;
        mPopup.setFocusable(modal);
    }
    public boolean isModal() { return mModal; }

    public void setOnItemClickListener(AdapterView.OnItemClickListener listener) {
        mItemClickListener = listener;
    }
    public void setOnItemSelectedListener(AdapterView.OnItemSelectedListener listener) {
        mItemSelectedListener = listener;
    }

    public void setPromptView(View prompt) {}
    // AOSP ListPopupWindow.setPromptPosition/:285 getPromptPosition -- a stored
    // int, POSITION_PROMPT_ABOVE by default. It used to be discarded, which is
    // why the getter (added below) had nothing to read.
    public void setPromptPosition(int position) { mPromptPosition = position; }
    // AOSP :353/:370 delegate straight to the PopupWindow, and this tree's
    // PopupWindow has both accessors already; these two used to swallow the
    // value, so the popup never saw an input-method mode a caller had set and
    // isInputMethodNotNeeded() could not have answered correctly.
    public void setInputMethodMode(int mode) { mPopup.setInputMethodMode(mode); }
    public int  getInputMethodMode() { return mPopup.getInputMethodMode(); }
    public void setSoftInputMode(int mode) { mPopup.setSoftInputMode(mode); }
    public void setListSelector(android.graphics.drawable.Drawable selector) {
        mDropDownListHighlight = selector;
    }
    public void setBackgroundDrawable(android.graphics.drawable.Drawable d) {
        mPopup.setBackgroundDrawable(d);
    }
    public void setSelection(int position) {
        if (mList != null) mList.setSelection(position);
    }
    public void setOnDismissListener(PopupWindow.OnDismissListener listener) {
        mPopup.setOnDismissListener(listener);
    }

    public ListView getListView() { return mList; }
    public int getSelectedItemPosition() {
        return mList != null ? mList.getSelectedItemPosition()
                : AdapterView.INVALID_POSITION;
    }
    public Object getSelectedItem() {
        return mList != null ? mList.getSelectedItem() : null;
    }
    public View getSelectedView() {
        return mList != null ? mList.getSelectedView() : null;
    }

    private ListView buildList() {
        ListView list = new ListView(mContext);
        list.setAdapter(mAdapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view,
                    int position, long id) {
                if (mItemClickListener != null) {
                    mItemClickListener.onItemClick(parent, view, position, id);
                }
            }
        });
        if (mItemSelectedListener != null) {
            list.setOnItemSelectedListener(mItemSelectedListener);
        }
        return list;
    }

    public void show() {
        if (mList == null) mList = buildList();
        else mList.setAdapter(mAdapter);
        mPopup.setContentView(mList);
        int w = mWidth == WRAP_CONTENT && mAnchor != null
                ? mAnchor.getWidth() : mWidth;
        mPopup.setWidth(w);
        mPopup.setHeight(mHeight);
        // AOSP ListPopupWindow.show(): `mPopup.setOutsideTouchable(
        // !mForceIgnoreOutsideTouch && !mDropDownAlwaysVisible);` -- neither of
        // those internal flags is settable through the public API we expose, so
        // the answer is unconditionally true. This became load-bearing when
        // PopupWindow started producing real layout params: it is what puts
        // FLAG_WATCH_OUTSIDE_TOUCH on the window so an outside tap reaches the
        // popup as ACTION_OUTSIDE rather than being silently swallowed.
        mPopup.setOutsideTouchable(true);
        if (mAnchor != null) {
            mPopup.showAsDropDown(mAnchor, mHorizontalOffset, mVerticalOffset);
        }
    }

    public void dismiss() { mPopup.dismiss(); }
    public boolean isShowing() { return mPopup.isShowing(); }

    public void postShow() { show(); }
    public void clearListSelection() {}
    public boolean performItemClick(int position) {
        if (mList != null && mAdapter != null) {
            return mList.performItemClick(null, position,
                    mAdapter.getItemId(position));
        }
        return false;
    }

    public android.view.View.OnTouchListener createDragToOpenListener(android.view.View src) { return null; }

    // ==================================================================
    // API-36 surface completion (apidiff: android.widget.ListPopupWindow had 14
    // public members missing). Bodies mirror
    // frameworks/base/core/java/android/widget/ListPopupWindow.java
    // (android16-release); cited line numbers are from that file.
    // ==================================================================

    private int mPromptPosition = POSITION_PROMPT_ABOVE;
    private int mDropDownGravity = android.view.Gravity.NO_GRAVITY;
    private android.graphics.Rect mEpicenterBounds;
    private android.graphics.drawable.Drawable mDropDownListHighlight;

    /** AOSP :285. */
    public int getPromptPosition() { return mPromptPosition; }

    /** AOSP :507 -- stored only; AOSP consumes it when it positions the drop
     *  down, which this port does through PopupWindow.showAsDropDown. */
    public void setDropDownGravity(int gravity) { mDropDownGravity = gravity; }

    /** AOSP :404 / :414 / :386 / :370 / :583 -- straight delegation to the
     *  backing PopupWindow, which has all four accessors. */
    public void setAnimationStyle(int animationStyle) { mPopup.setAnimationStyle(animationStyle); }
    public int  getAnimationStyle() { return mPopup.getAnimationStyle(); }
    public android.graphics.drawable.Drawable getBackground() { return mPopup.getBackground(); }
    public int  getSoftInputMode() { return mPopup.getSoftInputMode(); }
    public void setWindowLayoutType(int layoutType) { mPopup.setWindowLayoutType(layoutType); }

    /** AOSP :485 / :497 -- copy in, copy out. */
    public void setEpicenterBounds(android.graphics.Rect bounds) {
        mEpicenterBounds = bounds != null ? new android.graphics.Rect(bounds) : null;
    }
    public android.graphics.Rect getEpicenterBounds() {
        return mEpicenterBounds != null ? new android.graphics.Rect(mEpicenterBounds) : null;
    }

    /** AOSP :844 -- verbatim. */
    public boolean isInputMethodNotNeeded() {
        return mPopup.getInputMethodMode() == INPUT_METHOD_NOT_NEEDED;
    }

    /** AOSP :897 -- INVALID_ROW_ID unless the popup is actually showing. */
    public long getSelectedItemId() {
        if (!isShowing() || mList == null) return AdapterView.INVALID_ROW_ID;
        return mList.getSelectedItemId();
    }

    // ---- key filtering -------------------------------------------------
    // A non-modal ListPopupWindow does not own the focus, so its host view
    // (AutoCompleteTextView, Spinner, a Toolbar search field) forwards its own
    // onKeyDown/onKeyUp/onKeyPreIme here and lets the popup drive the list.
    // Without these three the drop-down could not be keyboard-navigated at all.

    /** AOSP KeyEvent.isConfirmKey (view/KeyEvent.java:2472) -- not present on
     *  this tree's KeyEvent (that is the android.view surface), so the same
     *  four key codes are tested here. */
    private static boolean isConfirmKey(int keyCode) {
        switch (keyCode) {
            case android.view.KeyEvent.KEYCODE_DPAD_CENTER:
            case android.view.KeyEvent.KEYCODE_ENTER:
            case android.view.KeyEvent.KEYCODE_SPACE:
            case android.view.KeyEvent.KEYCODE_NUMPAD_ENTER:
                return true;
            default:
                return false;
        }
    }

    /** AOSP :952. Structure kept: ignore SPACE (the list would select on it),
     *  ignore confirm keys while nothing is selected, block the edge case where
     *  arrowing past the end would move focus out of the popup, otherwise let
     *  the list consume the key and swallow the navigation keys so the host's
     *  focus does not move.
     *  One simplification, stated rather than hidden: AOSP asks
     *  mPopup.isAboveAnchor() for `below` and consults
     *  DropDownListView.lookForSelectablePosition for the first/last SELECTABLE
     *  row. This port's popup is always shown via showAsDropDown (below the
     *  anchor) and its ListView has no lookForSelectablePosition, so `below` is
     *  true and the bounds are the plain first/last adapter positions -- which
     *  is exactly AOSP's own answer whenever the adapter reports
     *  areAllItemsEnabled(). */
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        if (!isShowing() || mList == null) return false;
        if (keyCode == android.view.KeyEvent.KEYCODE_SPACE) return false;
        final int curIndex = mList.getSelectedItemPosition();
        if (curIndex < 0 && isConfirmKey(keyCode)) return false;

        final boolean below = true;
        final int firstItem = 0;
        final int lastItem = (mAdapter != null) ? mAdapter.getCount() - 1 : Integer.MIN_VALUE;

        if (below && keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP && curIndex <= firstItem) {
            // AOSP: block the key so focus cannot escape upward out of the list.
            clearListSelection();
            mPopup.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);
            show();
            return true;
        }

        final boolean consumed = mList.onKeyDown(keyCode, event);
        if (consumed) {
            mPopup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
            show();
            switch (keyCode) {
                case android.view.KeyEvent.KEYCODE_ENTER:
                case android.view.KeyEvent.KEYCODE_DPAD_CENTER:
                case android.view.KeyEvent.KEYCODE_DPAD_DOWN:
                case android.view.KeyEvent.KEYCODE_DPAD_UP:
                case android.view.KeyEvent.KEYCODE_NUMPAD_ENTER:
                    return true;
            }
        } else if (below && keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN
                && curIndex == lastItem) {
            return true;
        }
        return false;
    }

    /** AOSP :1049 -- verbatim. */
    public boolean onKeyUp(int keyCode, android.view.KeyEvent event) {
        if (isShowing() && mList != null && mList.getSelectedItemPosition() >= 0) {
            final boolean consumed = mList.onKeyUp(keyCode, event);
            if (consumed && isConfirmKey(keyCode)) {
                // AOSP: a confirm key on a selected row commits it and closes.
                dismiss();
            }
            return consumed;
        }
        return false;
    }

    /** AOSP :1073 -- BACK/ESCAPE dismiss the popup instead of reaching the IME.
     *  AOSP tracks the DOWN through the anchor's KeyEvent.DispatcherState so a
     *  long-press or a cancelled UP does not dismiss; View.getKeyDispatcherState()
     *  returns null in this tree (android/view/View.java:7236), and AOSP's own
     *  code guards for exactly that null, so the tracking half is skipped the
     *  way AOSP skips it when there is no dispatcher state. */
    public boolean onKeyPreIme(int keyCode, android.view.KeyEvent event) {
        if ((keyCode == android.view.KeyEvent.KEYCODE_BACK
                || keyCode == android.view.KeyEvent.KEYCODE_ESCAPE) && isShowing()) {
            final View anchorView = mAnchor;
            if (event.getAction() == android.view.KeyEvent.ACTION_DOWN
                    && event.getRepeatCount() == 0) {
                android.view.KeyEvent.DispatcherState state =
                        anchorView != null ? anchorView.getKeyDispatcherState() : null;
                if (state != null) state.startTracking(event, this);
                return true;
            } else if (event.getAction() == android.view.KeyEvent.ACTION_UP) {
                android.view.KeyEvent.DispatcherState state =
                        anchorView != null ? anchorView.getKeyDispatcherState() : null;
                if (state != null) {
                    state.handleUpEvent(event);
                    if (event.isTracking() && !event.isCanceled()) {
                        dismiss();
                        return true;
                    }
                } else {
                    // No dispatcher state to track through: the UP is the
                    // dismiss, which is what a device does for the same gesture.
                    dismiss();
                    return true;
                }
            }
        }
        return false;
    }
}
