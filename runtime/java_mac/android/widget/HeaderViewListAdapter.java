// android.widget.HeaderViewListAdapter -- wraps a ListAdapter to prepend
// header views and append footer views, exposing the combined sequence as a
// single ListAdapter. ListView.addHeaderView/addFooterView build one of
// these around the app's adapter. Was a bare stub. AOSP:
//   frameworks/base/core/java/android/widget/HeaderViewListAdapter.java
//   public class HeaderViewListAdapter implements WrapperListAdapter, Filterable
package android.widget;

import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;

public class HeaderViewListAdapter implements WrapperListAdapter, Filterable {

    // AOSP holds header/footer entries in ListView.FixedViewInfo records.
    public static class FixedViewInfo {
        public View view;
        public Object data;
        public boolean isSelectable;
    }

    private final ListAdapter mAdapter;
    private final ArrayList<FixedViewInfo> mHeaderViewInfos;
    private final ArrayList<FixedViewInfo> mFooterViewInfos;
    private final boolean mAreAllFixedViewsSelectable;
    private final boolean mIsFilterable;

    public HeaderViewListAdapter(ArrayList<FixedViewInfo> headerViewInfos,
            ArrayList<FixedViewInfo> footerViewInfos, ListAdapter adapter) {
        mAdapter = adapter;
        mIsFilterable = adapter instanceof Filterable;
        mHeaderViewInfos = headerViewInfos != null
                ? headerViewInfos : new ArrayList<FixedViewInfo>();
        mFooterViewInfos = footerViewInfos != null
                ? footerViewInfos : new ArrayList<FixedViewInfo>();
        mAreAllFixedViewsSelectable =
                areAllListInfosSelectable(mHeaderViewInfos)
                        && areAllListInfosSelectable(mFooterViewInfos);
    }

    public int getHeadersCount() { return mHeaderViewInfos.size(); }
    public int getFootersCount() { return mFooterViewInfos.size(); }

    @Override public boolean isEmpty() {
        return mAdapter == null || mAdapter.isEmpty();
    }

    private boolean areAllListInfosSelectable(ArrayList<FixedViewInfo> infos) {
        if (infos != null) {
            for (FixedViewInfo info : infos) {
                if (!info.isSelectable) return false;
            }
        }
        return true;
    }

    public boolean removeHeader(View v) {
        for (int i = 0; i < mHeaderViewInfos.size(); i++) {
            if (mHeaderViewInfos.get(i).view == v) {
                mHeaderViewInfos.remove(i);
                return true;
            }
        }
        return false;
    }

    public boolean removeFooter(View v) {
        for (int i = 0; i < mFooterViewInfos.size(); i++) {
            if (mFooterViewInfos.get(i).view == v) {
                mFooterViewInfos.remove(i);
                return true;
            }
        }
        return false;
    }

    @Override public int getCount() {
        int total = getFootersCount() + getHeadersCount();
        return mAdapter != null ? total + mAdapter.getCount() : total;
    }

    @Override public boolean areAllItemsEnabled() {
        return mAdapter == null
                || (mAreAllFixedViewsSelectable && mAdapter.areAllItemsEnabled());
    }

    @Override public boolean isEnabled(int position) {
        int numHeaders = getHeadersCount();
        if (position < numHeaders) {
            return mHeaderViewInfos.get(position).isSelectable;
        }
        final int adjPosition = position - numHeaders;
        int adapterCount = 0;
        if (mAdapter != null) {
            adapterCount = mAdapter.getCount();
            if (adjPosition < adapterCount) {
                return mAdapter.isEnabled(adjPosition);
            }
        }
        return mFooterViewInfos.get(adjPosition - adapterCount).isSelectable;
    }

    @Override public Object getItem(int position) {
        int numHeaders = getHeadersCount();
        if (position < numHeaders) {
            return mHeaderViewInfos.get(position).data;
        }
        final int adjPosition = position - numHeaders;
        int adapterCount = 0;
        if (mAdapter != null) {
            adapterCount = mAdapter.getCount();
            if (adjPosition < adapterCount) {
                return mAdapter.getItem(adjPosition);
            }
        }
        return mFooterViewInfos.get(adjPosition - adapterCount).data;
    }

    @Override public long getItemId(int position) {
        int numHeaders = getHeadersCount();
        if (mAdapter != null && position >= numHeaders) {
            int adjPosition = position - numHeaders;
            int adapterCount = mAdapter.getCount();
            if (adjPosition < adapterCount) {
                return mAdapter.getItemId(adjPosition);
            }
        }
        return -1;
    }

    @Override public boolean hasStableIds() {
        return mAdapter != null && mAdapter.hasStableIds();
    }

    @Override public View getView(int position, View convertView, ViewGroup parent) {
        int numHeaders = getHeadersCount();
        if (position < numHeaders) {
            return mHeaderViewInfos.get(position).view;
        }
        final int adjPosition = position - numHeaders;
        int adapterCount = 0;
        if (mAdapter != null) {
            adapterCount = mAdapter.getCount();
            if (adjPosition < adapterCount) {
                return mAdapter.getView(adjPosition, convertView, parent);
            }
        }
        return mFooterViewInfos.get(adjPosition - adapterCount).view;
    }

    @Override public int getItemViewType(int position) {
        int numHeaders = getHeadersCount();
        if (mAdapter != null && position >= numHeaders) {
            int adjPosition = position - numHeaders;
            int adapterCount = mAdapter.getCount();
            if (adjPosition < adapterCount) {
                return mAdapter.getItemViewType(adjPosition);
            }
        }
        return AdapterView.ITEM_VIEW_TYPE_HEADER_OR_FOOTER;
    }

    @Override public int getViewTypeCount() {
        return mAdapter != null ? mAdapter.getViewTypeCount() : 1;
    }

    @Override public void registerDataSetObserver(DataSetObserver observer) {
        if (mAdapter != null) mAdapter.registerDataSetObserver(observer);
    }

    @Override public void unregisterDataSetObserver(DataSetObserver observer) {
        if (mAdapter != null) mAdapter.unregisterDataSetObserver(observer);
    }

    @Override public Filter getFilter() {
        if (mIsFilterable) return ((Filterable) mAdapter).getFilter();
        return null;
    }

    @Override public ListAdapter getWrappedAdapter() { return mAdapter; }
}
