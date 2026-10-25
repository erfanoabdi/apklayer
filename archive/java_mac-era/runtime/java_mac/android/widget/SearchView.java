// android.widget.SearchView -- a search box (commonly placed in a Toolbar)
// with submit/change query callbacks. Was Absent. The query model, the
// OnQueryTextListener, iconified/expanded state, and submit dispatch are
// faithful; the magnifier/clear-button chrome is not painted. AOSP:
//   frameworks/base/core/java/android/widget/SearchView.java
//   public class SearchView extends LinearLayout implements CollapsibleActionView
//
// NOTE (was stale, corrected with the API-36 gap pass): this used to say
// android.view.CollapsibleActionView "is a non-interface placeholder in this
// framework". It has been a real interface since v1.0.92's fix (see that
// file's own header -- MenuItemImpl gates the expand/collapse notification on
// `actionView instanceof CollapsibleActionView`, so a placeholder CLASS made
// the check permanently false). The interface is now declared here, which is
// what makes a Toolbar search-icon expand actually reach
// onActionViewExpanded().
package android.widget;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

public class SearchView extends LinearLayout
        implements android.view.CollapsibleActionView {
    public interface OnQueryTextListener {
        boolean onQueryTextSubmit(String query);
        boolean onQueryTextChange(String newText);
    }
    public interface OnCloseListener {
        boolean onClose();
    }
    public interface OnSuggestionListener {
        boolean onSuggestionSelect(int position);
        boolean onSuggestionClick(int position);
    }

    private final EditText mSearchSrcTextView;
    private OnQueryTextListener mOnQueryTextListener;
    private OnCloseListener mOnCloseListener;
    private OnSuggestionListener mOnSuggestionListener;
    private CharSequence mQueryHint;
    private boolean mIconified = true;
    private boolean mSubmitButtonEnabled;
    private boolean mQueryRefinement;

    public SearchView(Context context) { this(context, null); }
    public SearchView(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        mSearchSrcTextView = new EditText(context);
        addView(mSearchSrcTextView);
        mSearchSrcTextView.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                if (mOnQueryTextListener != null) {
                    mOnQueryTextListener.onQueryTextChange(s.toString());
                }
            }
        });
    }
    public SearchView(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        this(context, attrs);
    }

    public void setOnQueryTextListener(OnQueryTextListener listener) {
        mOnQueryTextListener = listener;
    }
    public void setOnCloseListener(OnCloseListener listener) { mOnCloseListener = listener; }
    public void setOnSuggestionListener(OnSuggestionListener listener) {
        mOnSuggestionListener = listener;
    }
    public void setOnSearchClickListener(View.OnClickListener listener) {}

    public CharSequence getQuery() { return mSearchSrcTextView.getText(); }

    public void setQuery(CharSequence query, boolean submit) {
        mSearchSrcTextView.setText(query);
        if (query != null) {
            try { mSearchSrcTextView.setSelection(query.length()); } catch (Throwable ignored) {}
        }
        if (submit && query != null && query.length() > 0) onSubmitQuery();
    }

    private void onSubmitQuery() {
        CharSequence query = mSearchSrcTextView.getText();
        if (query != null && query.length() > 0) {
            if (mOnQueryTextListener == null
                    || !mOnQueryTextListener.onQueryTextSubmit(query.toString())) {
                // AOSP would launch the search intent here; we have no search
                // manager backend, so the listener is the terminal handler.
            }
        }
    }

    public void setQueryHint(CharSequence hint) {
        mQueryHint = hint;
        mSearchSrcTextView.setHint(hint);
    }
    public CharSequence getQueryHint() { return mQueryHint; }

    public void setIconifiedByDefault(boolean iconified) {
        mIconified = iconified;
        mIconifiedByDefault = iconified;
    }
    /** AOSP's own misspelled, @Deprecated form. AOSP :~1000 gives BOTH this and
     *  isIconifiedByDefault() the same body, `return mIconifiedByDefault`.
     *  This used to read mIconified, i.e. the CURRENT iconified state, which
     *  diverges from a device the moment setIconified() is called after
     *  setIconifiedByDefault() -- and would have made the two spellings answer
     *  differently once the correctly-spelled one existed. */
    @Deprecated
    public boolean isIconfiedByDefault() { return mIconifiedByDefault; }
    public void setIconified(boolean iconify) { mIconified = iconify; }
    public boolean isIconified() { return mIconified; }

    public void setSubmitButtonEnabled(boolean enabled) { mSubmitButtonEnabled = enabled; }
    public boolean isSubmitButtonEnabled() { return mSubmitButtonEnabled; }

    public void setQueryRefinementEnabled(boolean enable) { mQueryRefinement = enable; }
    public boolean isQueryRefinementEnabled() { return mQueryRefinement; }

    // AOSP SearchView.setMaxWidth/getMaxWidth and setSuggestionsAdapter/
    // getSuggestionsAdapter were `{}` / hardcoded null/0 with no backing
    // field. Scoped to the accessor-round-trip fix: AOSP's real setMaxWidth
    // also calls requestLayout() and onMeasure clamps to it, and
    // setSuggestionsAdapter wires the adapter into the live suggestions
    // ListView -- this class does not model either the max-width measure
    // clamp or a real suggestions dropdown, so wiring further than the
    // accessor contract itself is a separate, larger feature.
    // Default 0 per AOSP (frameworks/base core/java/android/widget/
    // SearchView.java ~line 169: `private int mMaxWidth;`, no initializer);
    // 0 is AOSP's own "no clamp" sentinel (onMeasure only applies mMaxWidth
    // when it is `> 0`), not literally a zero-width view.
    private int mMaxWidth;
    public void setMaxWidth(int maxpixels) { mMaxWidth = maxpixels; }
    public int getMaxWidth() { return mMaxWidth; }
    private CursorAdapter mSuggestionsAdapter;
    public void setSuggestionsAdapter(CursorAdapter adapter) { mSuggestionsAdapter = adapter; }
    public CursorAdapter getSuggestionsAdapter() { return mSuggestionsAdapter; }

    // CollapsibleActionView-equivalent surface (called by ActionView host).
    public void onActionViewExpanded() {
        mIconified = false;
        mSearchSrcTextView.requestFocus();
    }
    public void onActionViewCollapsed() {
        setQuery("", false);
        clearFocus();
        mIconified = true;
        if (mOnCloseListener != null) mOnCloseListener.onClose();
    }

    // ==================================================================
    // API-36 surface completion (apidiff: android.widget.SearchView had 8
    // public members missing, including the CollapsibleActionView interface
    // declaration itself). AOSP:
    // frameworks/base/core/java/android/widget/SearchView.java (android16).
    // ==================================================================

    /** AOSP keeps the "default" state separately from the CURRENT state: a
     *  SearchView that starts iconified can be expanded without changing what
     *  it collapses back to. */
    private boolean mIconifiedByDefault = true;
    private android.app.SearchableInfo mSearchable;
    private View.OnFocusChangeListener mOnQueryTextFocusChangeListener;

    /** AOSP's 4-arg constructor. Present in the API-36 signature file
     *  (android16.txt:61952) and absent here, so an app inflating a themed
     *  SearchView through the (Context, AttributeSet, int, int) path found no
     *  constructor at all. */
    public SearchView(Context context, android.util.AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        this(context, attrs);
    }

    public boolean isIconifiedByDefault() { return mIconifiedByDefault; }

    /** AOSP setImeOptions/getImeOptions/setInputType/getInputType all forward
     *  to the query EditText, which in this tree implements all four for real
     *  (TextView.java setImeOptions/:2356, setInputType/:2282). */
    public void setImeOptions(int imeOptions) { mSearchSrcTextView.setImeOptions(imeOptions); }
    public int  getImeOptions() { return mSearchSrcTextView.getImeOptions(); }
    public void setInputType(int inputType) { mSearchSrcTextView.setInputType(inputType); }
    public int  getInputType() { return mSearchSrcTextView.getInputType(); }

    /** AOSP setOnQueryTextFocusChangeListener: the listener is notified when the
     *  QUERY FIELD (not the SearchView as a whole) gains or loses focus, so it
     *  is installed on the inner EditText and reported with the SearchView as
     *  the view -- which is what AOSP's own onQueryTextFocusChange does. */
    public void setOnQueryTextFocusChangeListener(View.OnFocusChangeListener listener) {
        mOnQueryTextFocusChangeListener = listener;
        mSearchSrcTextView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean hasFocus) {
                if (mOnQueryTextFocusChangeListener != null) {
                    mOnQueryTextFocusChangeListener.onFocusChange(SearchView.this, hasFocus);
                }
            }
        });
    }

    /** AOSP setSearchableInfo: adopt the searchable activity's declared input
     *  type, IME options and hint. AOSP additionally shows/hides the voice
     *  button from getVoiceSearchEnabled() -- this tree's SearchableInfo
     *  answers false there unconditionally (it does not parse searchable.xml;
     *  see android/app/SearchableInfo.java's header) and we paint no voice
     *  button, so that branch has nothing to do. AOSP also treats a null
     *  argument as "not searchable" and leaves the field configuration alone,
     *  which is what the null guard below does. */
    public void setSearchableInfo(android.app.SearchableInfo searchable) {
        mSearchable = searchable;
        if (mSearchable == null) return;
        mSearchSrcTextView.setInputType(mSearchable.getInputType());
        mSearchSrcTextView.setImeOptions(mSearchable.getImeOptions());
        final int hintId = mSearchable.getHintId();
        if (hintId != 0) {
            try { setQueryHint(getContext().getText(hintId)); } catch (Throwable ignored) {}
        }
    }
}
