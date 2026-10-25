// android.widget.BaseExpandableListAdapter -- convenience base for
// ExpandableListAdapter implementations, with observer management and the
// default combined-id packing. Was Absent. AOSP:
//   frameworks/base/core/java/android/widget/BaseExpandableListAdapter.java
//   public abstract class BaseExpandableListAdapter
//       implements ExpandableListAdapter, HeterogeneousExpandableList
package android.widget;

import android.database.DataSetObservable;
import android.database.DataSetObserver;

public abstract class BaseExpandableListAdapter
        implements ExpandableListAdapter, HeterogeneousExpandableList {

    private final DataSetObservable mDataSetObservable = new DataSetObservable();

    @Override public void registerDataSetObserver(DataSetObserver observer) {
        mDataSetObservable.registerObserver(observer);
    }
    @Override public void unregisterDataSetObserver(DataSetObserver observer) {
        mDataSetObservable.unregisterObserver(observer);
    }

    public void notifyDataSetInvalidated() { mDataSetObservable.notifyInvalidated(); }
    public void notifyDataSetChanged() { mDataSetObservable.notifyChanged(); }

    @Override public boolean areAllItemsEnabled() { return true; }
    @Override public void onGroupExpanded(int groupPosition) {}
    @Override public void onGroupCollapsed(int groupPosition) {}

    @Override public long getCombinedChildId(long groupId, long childId) {
        return 0x8000000000000000L | ((groupId & 0x7FFFFFFF) << 32)
                | (childId & 0xFFFFFFFF);
    }
    @Override public long getCombinedGroupId(long groupId) {
        return (groupId & 0x7FFFFFFF) << 32;
    }

    @Override public boolean isEmpty() { return getGroupCount() == 0; }

    @Override public int getGroupType(int groupPosition) { return 0; }
    @Override public int getChildType(int groupPosition, int childPosition) { return 0; }
    @Override public int getGroupTypeCount() { return 1; }
    @Override public int getChildTypeCount() { return 1; }
}
