// android.widget.HeterogeneousExpandableList -- optional interface an
// ExpandableListAdapter implements to provide multiple group/child view types.
// Was Absent. AOSP:
//   frameworks/base/core/java/android/widget/HeterogeneousExpandableList.java
package android.widget;

public interface HeterogeneousExpandableList {
    int getGroupType(int groupPosition);
    int getChildType(int groupPosition, int childPosition);
    int getGroupTypeCount();
    int getChildTypeCount();
}
