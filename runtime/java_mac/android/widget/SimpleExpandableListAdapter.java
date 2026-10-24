// android.widget.SimpleExpandableListAdapter -- maps lists of Maps onto group
// and child rows of an ExpandableListView. Was Absent. AOSP:
//   frameworks/base/core/java/android/widget/SimpleExpandableListAdapter.java
//   public class SimpleExpandableListAdapter extends BaseExpandableListAdapter
package android.widget;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;
import java.util.Map;

public class SimpleExpandableListAdapter extends BaseExpandableListAdapter {
    private final List<? extends Map<String, ?>> mGroupData;
    private final int mExpandedGroupLayout;
    private final int mCollapsedGroupLayout;
    private final String[] mGroupFrom;
    private final int[] mGroupTo;

    private final List<? extends List<? extends Map<String, ?>>> mChildData;
    private final int mChildLayout;
    private final int mLastChildLayout;
    private final String[] mChildFrom;
    private final int[] mChildTo;

    private final LayoutInflater mInflater;

    public SimpleExpandableListAdapter(Context context,
            List<? extends Map<String, ?>> groupData,
            int groupLayout, String[] groupFrom, int[] groupTo,
            List<? extends List<? extends Map<String, ?>>> childData,
            int childLayout, String[] childFrom, int[] childTo) {
        this(context, groupData, groupLayout, groupLayout, groupFrom, groupTo,
                childData, childLayout, childLayout, childFrom, childTo);
    }

    public SimpleExpandableListAdapter(Context context,
            List<? extends Map<String, ?>> groupData,
            int expandedGroupLayout, int collapsedGroupLayout,
            String[] groupFrom, int[] groupTo,
            List<? extends List<? extends Map<String, ?>>> childData,
            int childLayout, String[] childFrom, int[] childTo) {
        this(context, groupData, expandedGroupLayout, collapsedGroupLayout,
                groupFrom, groupTo, childData, childLayout, childLayout,
                childFrom, childTo);
    }

    public SimpleExpandableListAdapter(Context context,
            List<? extends Map<String, ?>> groupData,
            int expandedGroupLayout, int collapsedGroupLayout,
            String[] groupFrom, int[] groupTo,
            List<? extends List<? extends Map<String, ?>>> childData,
            int childLayout, int lastChildLayout,
            String[] childFrom, int[] childTo) {
        mGroupData = groupData;
        mExpandedGroupLayout = expandedGroupLayout;
        mCollapsedGroupLayout = collapsedGroupLayout;
        mGroupFrom = groupFrom;
        mGroupTo = groupTo;
        mChildData = childData;
        mChildLayout = childLayout;
        mLastChildLayout = lastChildLayout;
        mChildFrom = childFrom;
        mChildTo = childTo;
        mInflater = LayoutInflater.from(context);
    }

    @Override public Object getChild(int groupPosition, int childPosition) {
        return mChildData.get(groupPosition).get(childPosition);
    }
    @Override public long getChildId(int groupPosition, int childPosition) { return childPosition; }
    @Override public int getChildrenCount(int groupPosition) {
        return mChildData.get(groupPosition).size();
    }
    @Override public Object getGroup(int groupPosition) { return mGroupData.get(groupPosition); }
    @Override public int getGroupCount() { return mGroupData.size(); }
    @Override public long getGroupId(int groupPosition) { return groupPosition; }
    @Override public boolean hasStableIds() { return true; }
    @Override public boolean isChildSelectable(int groupPosition, int childPosition) { return true; }

    @Override public View getGroupView(int groupPosition, boolean isExpanded,
            View convertView, ViewGroup parent) {
        View v = convertView != null ? convertView
                : mInflater.inflate(isExpanded ? mExpandedGroupLayout : mCollapsedGroupLayout,
                        parent, false);
        bindView(v, mGroupData.get(groupPosition), mGroupFrom, mGroupTo);
        return v;
    }

    @Override public View getChildView(int groupPosition, int childPosition,
            boolean isLastChild, View convertView, ViewGroup parent) {
        View v = convertView != null ? convertView
                : mInflater.inflate(isLastChild ? mLastChildLayout : mChildLayout,
                        parent, false);
        bindView(v, mChildData.get(groupPosition).get(childPosition), mChildFrom, mChildTo);
        return v;
    }

    private void bindView(View view, Map<String, ?> data, String[] from, int[] to) {
        if (data == null) return;
        for (int i = 0; i < to.length; i++) {
            View v = view.findViewById(to[i]);
            if (v instanceof TextView) {
                Object value = data.get(from[i]);
                ((TextView) v).setText(value == null ? "" : value.toString());
            }
        }
    }

    /** AOSP SimpleExpandableListAdapter.java:288 / :239 -- the two inflate
     *  hooks getGroupView/getChildView call, exposed so a subclass can supply
     *  a different view. Verbatim. */
    public View newGroupView(boolean isExpanded, ViewGroup parent) {
        return mInflater.inflate(isExpanded ? mExpandedGroupLayout : mCollapsedGroupLayout,
                parent, false);
    }
    public View newChildView(boolean isLastChild, ViewGroup parent) {
        return mInflater.inflate(isLastChild ? mLastChildLayout : mChildLayout, parent, false);
    }
}
