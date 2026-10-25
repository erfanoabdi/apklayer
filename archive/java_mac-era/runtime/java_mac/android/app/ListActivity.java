// android.app.ListActivity -- an Activity that hosts a single ListView
// bound to a ListAdapter. Legacy (pre-RecyclerView) but still common in
// older apps. AOSP API-1+.
//
// AOSP ref: frameworks/base/core/java/android/app/ListActivity.java.
// We mirror the public surface: setListAdapter/getListAdapter,
// getListView, getSelectedItemId/Position, the onListItemClick callback,
// and the magic R.id.list lookup. Our ListView doesn't render in the
// macOS window the way Android draws it, but the adapter wiring +
// item-click dispatch behave correctly for apps that drive logic off
// the list.
package android.app;

import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;

public class ListActivity extends Activity {
    /** AOSP uses com.android.internal.R.id.list (0x0102000a). Apps that
     *  inflate a layout containing @android:id/list rely on this exact
     *  id so getListView() finds the ListView. */
    private static final int ANDROID_R_ID_LIST = 0x0102000a;

    private ListAdapter mAdapter;
    private ListView mList;
    private boolean mFinishedStart = false;

    private final Handler mHandler = new Handler();
    private final Runnable mRequestFocus = new Runnable() {
        public void run() { if (mList != null) mList.focusableViewAvailable(mList); }
    };

    private final AdapterView.OnItemClickListener mOnClickListener = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
            onListItemClick((ListView) parent, v, position, id);
        }
    };

    /** Override point: a list item was clicked. */
    protected void onListItemClick(ListView l, View v, int position, long id) {}

    @Override protected void onRestoreInstanceState(Bundle state) {
        ensureList();
        super.onRestoreInstanceState(state);
    }

    @Override protected void onDestroy() {
        mHandler.removeCallbacks(mRequestFocus);
        super.onDestroy();
    }

    public void setListAdapter(ListAdapter adapter) {
        synchronized (this) {
            ensureList();
            mAdapter = adapter;
            if (mList != null) mList.setAdapter(adapter);
        }
    }

    public void setSelection(int position) { if (mList != null) mList.setSelection(position); }
    public int getSelectedItemPosition() { return mList != null ? mList.getSelectedItemPosition() : AdapterView.INVALID_POSITION; }
    public long getSelectedItemId() { return mList != null ? mList.getSelectedItemId() : AdapterView.INVALID_ROW_ID; }
    public ListView getListView() { ensureList(); return mList; }
    public ListAdapter getListAdapter() { return mAdapter; }

    private void ensureList() {
        if (mList != null) return;
        // AOSP looks up @android:id/list in the content view. If the app
        // hasn't called setContentView with a list layout, we lazily
        // create a ListView so getListView() never returns null.
        View v = findViewById(ANDROID_R_ID_LIST);
        if (v instanceof ListView) {
            mList = (ListView) v;
        } else {
            mList = new ListView(this);
        }
        mList.setOnItemClickListener(mOnClickListener);
        if (mAdapter != null) {
            ListAdapter adapter = mAdapter;
            mAdapter = null;
            setListAdapter(adapter);
        } else {
            mHandler.post(mRequestFocus);
        }
        mFinishedStart = true;
    }
}
