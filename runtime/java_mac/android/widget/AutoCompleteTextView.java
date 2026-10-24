// android.widget.AutoCompleteTextView -- an EditText that shows a filtered
// suggestion dropdown as the user types. Was a bare `extends Object` stub
// (broke instanceof TextView/EditText and every autocomplete field). Now the
// real EditText subclass: it watches its own text, runs the adapter's Filter
// past the threshold, and drives a ListPopupWindow of suggestions; picking a
// suggestion replaces the text. AOSP:
//   frameworks/base/core/java/android/widget/AutoCompleteTextView.java
//   public class AutoCompleteTextView extends EditText
//       implements Filter.FilterListener
package android.widget;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;

public class AutoCompleteTextView extends EditText
        implements Filter.FilterListener {

    public interface OnDismissListener { void onDismiss(); }

    private ListAdapter mAdapter;
    private Filter mFilter;
    private int mThreshold = 2;
    private final ListPopupWindow mPopup;
    private AdapterView.OnItemClickListener mItemClickListener;
    private AdapterView.OnItemSelectedListener mItemSelectedListener;
    private OnDismissListener mDismissListener;
    private CharSequence mCompletionHint;
    private boolean mBlockCompletion;
    private Validator mValidator;
    // Backs setRawInputType/setInputType/getInputType: TextView's own pair is
    // a hard no-op (TextView.java has no editor state to bypass), so getting
    // a real round-trip for THIS class (Material3 ExposedDropdownMenu calls
    // setRawInputType(TYPE_NULL) to make a non-editable trigger field, then
    // reads it back) needs its own field rather than a TextView-wide change.
    private int mInputType = InputType.TYPE_NULL;
    private int mDropDownAnchorId = View.NO_ID;
    // Substitute for KeyEvent.DispatcherState-based ACTION_DOWN/ACTION_UP
    // tracking: View.getKeyDispatcherState() always returns null in this
    // tree, so AOSP's real tracking chain (state.startTracking/isTracking)
    // can never fire here. A local flag gets the same down-then-up dismiss
    // behavior without depending on that stubbed mechanism.
    private boolean mBackKeyTracking;

    public interface Validator {
        boolean isValid(CharSequence text);
        CharSequence fixText(CharSequence invalidText);
    }

    public AutoCompleteTextView(Context context) { this(context, null); }
    public AutoCompleteTextView(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
        mPopup = new ListPopupWindow(context);
        mPopup.setAnchorView(this);
        installWatcher();
    }
    /** AOSP API 21 four-argument constructor: chains to the parent's four-argument
     *  form so defStyleRes reaches TextView (AOSP does the same, e.g. EditText.java:100-101).
     *  Without it an app subclass calling the four-argument super() dies with
     *  NoSuchMethodError; see debug/ctorsdk_gate.sh. */
    public AutoCompleteTextView(Context context, android.util.AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mPopup = new ListPopupWindow(context);
        mPopup.setAnchorView(this);
        installWatcher();
    }
    public AutoCompleteTextView(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mPopup = new ListPopupWindow(context);
        mPopup.setAnchorView(this);
        installWatcher();
    }

    private void installWatcher() {
        addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                if (!mBlockCompletion) onTextEdited(s);
            }
        });
        mPopup.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view,
                    int position, long id) {
                performCompletion(view, position, id);
            }
        });
    }

    private void onTextEdited(CharSequence s) {
        if (mFilter == null) return;
        if (enoughToFilter()) {
            mFilter.filter(s, this);
        } else {
            dismissDropDown();
        }
    }

    public <T extends ListAdapter & Filterable> void setAdapter(T adapter) {
        mAdapter = adapter;
        mFilter = adapter != null ? ((Filterable) adapter).getFilter() : null;
        mPopup.setAdapter(adapter);
    }

    public ListAdapter getAdapter() { return mAdapter; }

    public void setThreshold(int threshold) {
        if (threshold <= 0) threshold = 1;
        mThreshold = threshold;
    }
    public int getThreshold() { return mThreshold; }

    public boolean enoughToFilter() {
        return getText().length() >= mThreshold;
    }

    public void setOnItemClickListener(AdapterView.OnItemClickListener l) {
        mItemClickListener = l;
    }
    public AdapterView.OnItemClickListener getOnItemClickListener() {
        return mItemClickListener;
    }
    public void setOnItemSelectedListener(AdapterView.OnItemSelectedListener l) {
        mItemSelectedListener = l;
        mPopup.setOnItemSelectedListener(l);
    }
    public AdapterView.OnItemSelectedListener getOnItemSelectedListener() {
        return mItemSelectedListener;
    }
    public void setOnDismissListener(OnDismissListener l) { mDismissListener = l; }

    public void setCompletionHint(CharSequence hint) { mCompletionHint = hint; }
    public CharSequence getCompletionHint() { return mCompletionHint; }

    public void setValidator(Validator validator) { mValidator = validator; }
    public Validator getValidator() { return mValidator; }
    public void performValidation() {
        if (mValidator == null) return;
        CharSequence text = getText();
        if (text != null && text.length() > 0 && !mValidator.isValid(text)) {
            setText(mValidator.fixText(text));
        }
    }

    public void showDropDown() {
        if (mAdapter != null && mAdapter.getCount() > 0) mPopup.show();
    }
    public void dismissDropDown() { mPopup.dismiss(); }
    public boolean isPopupShowing() { return mPopup.isShowing(); }

    public int getDropDownWidth() { return mPopup.getWidth(); }
    public void setDropDownWidth(int width) { mPopup.setWidth(width); }
    public int getDropDownHeight() { return mPopup.getHeight(); }
    public void setDropDownHeight(int height) { mPopup.setHeight(height); }
    public int getDropDownHorizontalOffset() { return mPopup.getHorizontalOffset(); }
    public void setDropDownHorizontalOffset(int offset) { mPopup.setHorizontalOffset(offset); }
    public int getDropDownVerticalOffset() { return mPopup.getVerticalOffset(); }
    public void setDropDownVerticalOffset(int offset) { mPopup.setVerticalOffset(offset); }
    public void setDropDownBackgroundDrawable(android.graphics.drawable.Drawable d) {
        mPopup.setBackgroundDrawable(d);
    }
    public void setDropDownBackgroundResource(int id) {}

    /** AOSP: the id of the view the drop-down anchors to, or {@link View#NO_ID}
     *  if none was specified. */
    public int getDropDownAnchor() { return mDropDownAnchorId; }

    /** AOSP also nulls the popup's explicit anchor view here so the id takes
     *  over at next show() (ListPopupWindow resolves the id to a View lazily).
     *  Our ListPopupWindow has no id -> View resolution (see
     *  ListPopupWindow.java) and its show() only calls showAsDropDown() when
     *  its anchor is non-null, so nulling it here would silently stop the
     *  drop-down from ever showing again for the common case (no explicit
     *  anchor id set, relying on the constructor's default this-anchor). We
     *  store the id for a faithful getDropDownAnchor() readback without that
     *  regression; wiring real id -> View resolution is a ListPopupWindow.java
     *  change, out of this file's scope. */
    public void setDropDownAnchor(int id) { mDropDownAnchorId = id; }

    public ListView getListView() { return mPopup.getListView(); }

    /** AOSP: directly changes the content type integer, bypassing
     *  setInputType's normal side-effecting logic (key listener swap,
     *  single-line toggle, password transformation) -- used by the standard
     *  Material3 ExposedDropdownMenu pattern to set TYPE_NULL on a
     *  non-editable trigger field. Our setInputType has no such side effects
     *  to bypass (see the field comment above), so this is equivalent to it
     *  here; the important AOSP contract this preserves is that
     *  getInputType() afterwards returns exactly what was passed in. */
    public void setRawInputType(int type) { mInputType = type; }

    @Override public void setInputType(int type) { mInputType = type; }
    @Override public int getInputType() { return mInputType; }

    /** AOSP: clears the drop-down's list selection. May only be temporary,
     *  as user input will often bring it back. */
    public void clearListSelection() { mPopup.clearListSelection(); }

    /** AOSP: moves the drop-down's list selection to `position`. */
    public void setListSelection(int position) { mPopup.setSelection(position); }

    /** AOSP: the position of the drop-down's current list selection, or
     *  {@link AdapterView#INVALID_POSITION} if there is no drop-down or no
     *  selection. */
    public int getListSelection() { return mPopup.getSelectedItemPosition(); }

    /** AOSP: thin passthrough to the internal ListPopupWindow's input-method
     *  visibility policy (INPUT_METHOD_FROM_FOCUSABLE / _NEEDED / _NOT_NEEDED). */
    public void setInputMethodMode(int mode) { mPopup.setInputMethodMode(mode); }
    public int getInputMethodMode() { return mPopup.getInputMethodMode(); }

    /** AOSP: re-runs the filter against the CURRENT text. Normally redundant
     *  with the per-keystroke TextWatcher path (installWatcher), but exists
     *  for callers that fetch suggestions asynchronously and need to
     *  explicitly re-trigger filtering once results become available, or
     *  flush stale results once there is no longer enough text to filter. */
    public final void refreshAutoCompleteResults() {
        if (enoughToFilter()) {
            if (mFilter != null) performFiltering(getText(), 0);
        } else {
            dismissDropDown();
            if (mFilter != null) mFilter.filter(null);
        }
    }

    /** AOSP: special-cases BACK/ESCAPE while the drop-down is showing so the
     *  key dismisses the suggestions instead of propagating to the IME or
     *  the enclosing Activity. Real AOSP defers the dismiss to ACTION_UP via
     *  a KeyEvent.DispatcherState obtained from getKeyDispatcherState(),
     *  which always returns null in this tree (View.java); mBackKeyTracking
     *  reproduces the same down-then-up gating locally instead of depending
     *  on that stub. There is no View-level onKeyPreIme in this tree for
     *  this to @Override (the framework key-dispatch chain that would call
     *  it polymorphically lives in View.java, not owned by this change), so
     *  this is reachable today only via a direct call. */
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE)
                && isPopupShowing()) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                mBackKeyTracking = true;
                return true;
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                boolean wasTracking = mBackKeyTracking;
                mBackKeyTracking = false;
                if (wasTracking && !event.isCanceled()) {
                    dismissDropDown();
                    return true;
                }
            }
        }
        return false;
    }

    protected void performFiltering(CharSequence text, int keyCode) {
        if (mFilter != null) mFilter.filter(text, this);
    }

    public void performCompletion() {
        performCompletion(null, mPopup.getSelectedItemPosition(), -1);
    }

    private void performCompletion(View selectedView, int position, long id) {
        if (isPopupShowing()) {
            Object selectedItem;
            if (position < 0) selectedItem = mPopup.getSelectedItem();
            else selectedItem = mAdapter.getItem(position);

            CharSequence completion = convertSelectionToString(selectedItem);
            mBlockCompletion = true;
            replaceText(completion);
            mBlockCompletion = false;

            if (mItemClickListener != null) {
                final ListView list = mPopup.getListView();
                mItemClickListener.onItemClick(list, selectedView, position, id);
            }
        }
        dismissDropDown();
    }

    protected CharSequence convertSelectionToString(Object selectedItem) {
        return mFilter != null
                ? mFilter.convertResultToString(selectedItem)
                : String.valueOf(selectedItem);
    }

    protected void replaceText(CharSequence text) {
        setText(text);
        Editable editable = getText();
        if (editable != null) setSelection(editable.length());
    }

    /** AOSP updateDropDownForFilter (AutoCompleteTextView.java): the drop-down
     *  opens only `if (hasFocus() && hasWindowFocus() && mPopupCanBeUpdated)`.
     *  Ours opened for ANY filter result, so programmatic text on an unfocused
     *  field -- which reaches the watcher since TextView.setText sends
     *  afterTextChanged like AOSP -- popped the list open by itself (ytdlnis's
     *  Container exposed-dropdown, issue #5). hasWindowFocus is not required
     *  here: our dialog windows do not all report it. MACAPK_ACTV_FOCUS_GATE=0
     *  restores the unconditional show. */
    static final boolean MACAPK_ACTV_FOCUS_GATE =
        !"0".equals(System.getenv("MACAPK_ACTV_FOCUS_GATE"));
    @Override public void onFilterComplete(int count) {
        if (count > 0 && getText().length() >= mThreshold
                && (!MACAPK_ACTV_FOCUS_GATE || hasFocus())) {
            showDropDown();
        } else {
            dismissDropDown();
        }
    }

    public android.graphics.drawable.Drawable getDropDownBackground() { return null; }

    /** AOSP: `filter == false` sets mBlockCompletion around setText so the
     *  watcher does not filter (and isPerformingCompletion() reads true). */
    public void setText(CharSequence text, boolean filter) {
        if (filter) { setText(text); return; }
        mBlockCompletion = true;
        try { setText(text); } finally { mBlockCompletion = false; }
    }

    // ==================================================================
    // API-36 surface completion (apidiff: android.widget.AutoCompleteTextView
    // had 5 public members missing). AOSP:
    // frameworks/base/core/java/android/widget/AutoCompleteTextView.java
    // (android16-release); cited line numbers are from that file.
    // ==================================================================

    /** AOSP's 5-arg constructor (Context, AttributeSet, defStyleAttr,
     *  defStyleRes, Resources.Theme) -- the popup-theme form, used when a
     *  Spinner/SearchView wants the drop-down inflated against a different
     *  theme from the text field. This class builds no themed popup context,
     *  so it chains like the other constructors do. */
    public AutoCompleteTextView(Context context, android.util.AttributeSet attrs,
            int defStyleAttr, int defStyleRes, android.content.res.Resources.Theme popupTheme) {
        this(context, attrs, defStyleAttr, defStyleRes);
    }

    /** AOSP getFilter(): the Filter of the current adapter, or null. AOSP keeps
     *  mFilter alongside mAdapter and sets both in setAdapter; this class
     *  already does the same (mFilter, set in setAdapter above). */
    public Filter getFilter() { return mFilter; }

    /** AOSP :703 / :716 -- the @Deprecated spellings of
     *  getOnItemClickListener/getOnItemSelectedListener, same fields. */
    @Deprecated
    public AdapterView.OnItemClickListener getItemClickListener() { return mItemClickListener; }
    @Deprecated
    public AdapterView.OnItemSelectedListener getItemSelectedListener() {
        return mItemSelectedListener;
    }

    /** AOSP :1099 -- true only while a completion is being applied, so a
     *  TextWatcher can tell an app edit from the widget's own. mBlockCompletion
     *  is the same flag this class already sets around a completion. */
    public boolean isPerformingCompletion() { return mBlockCompletion; }
}
