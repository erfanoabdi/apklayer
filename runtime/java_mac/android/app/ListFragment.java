package android.app;
import android.view.View;
import android.widget.ListAdapter;
import android.widget.ListView;
/** A Fragment that hosts a ListView. Mac.apk provides the API surface; the
 *  ListView is created lazily. API 11 (deprecated). */
public class ListFragment extends Fragment {
    private ListAdapter mAdapter;
    private ListView mList;
    public ListFragment() {}
    public void setListAdapter(ListAdapter adapter) { mAdapter = adapter; if (mList != null) mList.setAdapter(adapter); }
    public ListAdapter getListAdapter() { return mAdapter; }
    public ListView getListView() { return mList; }
    public void setEmptyText(CharSequence text) {}
    public void setSelection(int position) { if (mList != null) mList.setSelection(position); }
    public long getSelectedItemId() { return mList != null ? mList.getSelectedItemId() : -1; }
    public int getSelectedItemPosition() { return mList != null ? mList.getSelectedItemPosition() : -1; }
    public void setListShown(boolean shown) {}
    public void setListShownNoAnimation(boolean shown) {}
    public void onListItemClick(ListView l, View v, int position, long id) {}
}
