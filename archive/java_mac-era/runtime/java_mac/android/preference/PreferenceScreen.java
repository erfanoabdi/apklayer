// android.preference.PreferenceScreen -- the root container of a preference
// hierarchy, and also the thing a nested <PreferenceScreen> tag produces (which
// on AOSP opens a NEW screen when clicked).
//
// AOSP reference:
//   https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/
//     core/java/android/preference/PreferenceScreen.java
//   public final class PreferenceScreen extends PreferenceGroup
//           implements AdapterView.OnItemClickListener, DialogInterface.OnDismissListener
//   public ListAdapter getRootAdapter()
//   protected ListAdapter onCreateRootAdapter()
//   public void bind(ListView listView)
//   public Dialog getDialog()
//   public void onItemClick(AdapterView, View, int position, long id)
//   public void onDismiss(DialogInterface)
//
// Two divergences:
//  - NOT `final`, and it keeps the historical no-arg constructor, because
//    Mac.apk code and app code in the tree already construct it that way.
//  - The AdapterView/Dialog surface is not implemented: it needs the
//    preference-XML inflater plus a real preference list host, which Mac.apk
//    does not have yet (see PreferenceActivity.addPreferencesFromResource).
//    getRootAdapter()/getDialog() therefore return null rather than pretending.
//
// It now extends PreferenceGroup instead of Preference, which is what gives it
// AOSP's own getSharedPreferences()/findPreference()/addPreference(boolean)
// surface -- `PreferenceScreen.getSharedPreferences()` was one of the three
// measured gaps for ch.logixisland.anuto (debug/preflight.py, 2026-07-29).
package android.preference;

import android.content.Context;
import android.util.AttributeSet;

@SuppressWarnings("deprecation")
public class PreferenceScreen extends PreferenceGroup
        implements android.widget.AdapterView.OnItemClickListener,
                   android.content.DialogInterface.OnDismissListener {

    public PreferenceScreen(Context context, AttributeSet attrs) { super(context, attrs, 0, 0); }
    public PreferenceScreen(Context context) { super(context, null, 0, 0); }

    /** Historical Mac.apk shape; not AOSP (whose only constructor is
     *  package-private `PreferenceScreen(Context, AttributeSet)`). Kept so
     *  existing `new PreferenceScreen()` call sites keep working. */
    public PreferenceScreen() { super(); }

    /** AOSP: false -- a nested PreferenceScreen opens its own screen, so its
     *  children are NOT on the same screen as the group row. */
    @Override
    protected boolean isOnSameScreenAsChildren() { return false; }

    private android.widget.ListAdapter mRootAdapter;
    private android.app.Dialog mDialog;

    /** AOSP body: the ListAdapter backing the preference ListView, created once.
     *  This used to return null with a note that "Mac.apk has no preference list
     *  host"; PreferenceGroupAdapter is that host's data source and
     *  PreferenceActivity now builds the ListView, so there is a real adapter. */
    public android.widget.ListAdapter getRootAdapter() {
        if (mRootAdapter == null) mRootAdapter = onCreateRootAdapter();
        return mRootAdapter;
    }

    /** AOSP body, verbatim. */
    protected android.widget.ListAdapter onCreateRootAdapter() {
        return new PreferenceGroupAdapter(this);
    }

    /** AOSP body, verbatim: wire this screen's rows into a ListView. */
    public void bind(android.widget.ListView listView) {
        listView.setOnItemClickListener(this);
        listView.setAdapter(getRootAdapter());

        onAttachedToActivity();
    }

    /** AOSP body: a nested PreferenceScreen with children and no intent/fragment
     *  opens its children in a dialog. AOSP inflates
     *  com.android.internal.R.layout.preference_list_fragment for it; Mac.apk has
     *  no internal layouts, so the dialog's content is built programmatically --
     *  a ListView bound through the same bind() path, so the ROWS are identical
     *  to the ones the main screen would show. */
    @Override
    protected void onClick() {
        if (getIntent() != null || getFragment() != null || getPreferenceCount() == 0) {
            return;
        }
        showDialog(null);
    }

    private void showDialog(android.os.Bundle state) {
        Context context = getContext();
        if (context == null) return;

        android.widget.ListView listView = new android.widget.ListView(context);
        listView.setId(ID_LIST);
        bind(listView);

        android.app.Dialog dialog = mDialog = new android.app.Dialog(context);
        final CharSequence title = getTitle();
        if (title != null && title.length() > 0) dialog.setTitle(title);
        dialog.setContentView(listView);
        dialog.setOnDismissListener(this);
        if (state != null) {
            try { dialog.onRestoreInstanceState(state); } catch (Throwable ignored) {}
        }
        dialog.show();
    }

    /** Frozen AOSP public id, verified against core/res/res/values/public-final.xml:
     *    <public type="id" name="list" id="0x0102000a" />
     *  AOSP's dialog layout gives its ListView com.android.internal.R.id.list,
     *  which is the same number, and app code does
     *  findViewById(android.R.id.list) on a preference screen. */
    private static final int ID_LIST = 0x0102000a;

    /** AOSP: the dialog this screen is currently showing, or null. */
    public android.app.Dialog getDialog() { return mDialog; }

    /** AOSP body. */
    public void onDismiss(android.content.DialogInterface dialog) {
        mDialog = null;
    }

    /** AOSP body, verbatim: route a row tap to the Preference it represents.
     *  Header views are subtracted first, which is what keeps the mapping right
     *  on a ListView that an app gave a header to. */
    public void onItemClick(android.widget.AdapterView parent, android.view.View view,
                            int position, long id) {
        if (parent instanceof android.widget.ListView) {
            position -= ((android.widget.ListView) parent).getHeaderViewsCount();
        }
        Object item = getRootAdapter().getItem(position);
        if (!(item instanceof Preference)) return;

        final Preference preference = (Preference) item;
        preference.performClick(this);
    }
}
