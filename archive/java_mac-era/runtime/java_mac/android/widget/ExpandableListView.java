// android.widget.ExpandableListView -- a ListView that shows a two-level
// (group/child) list. Mac.apk doesn't surface a functional expandable list to
// any current target; this exists so the CLASS resolves.
//
// Why Mac.apk needs it (v1.0.406): Reddit bundles the AppsFlyer SDK, whose
// com.appsflyer.internal.AFf1mSDK references android.widget.ExpandableListView.
// With the class absent, AFf1mSDK.<clinit> threw ExceptionInInitializerError
// (caused by NoClassDefFoundError: android/widget/ExpandableListView) on an
// AppsFlyer background thread. The SDK isn't on the UI path, but the class
// being missing is a real framework gap; providing the AOSP-shaped class lets
// the SDK initialize.
//
// AOSP source mirrored: frameworks/base/core/java/android/widget/
// ExpandableListView.java (extends ListView; PACKED_POSITION_* constants;
// the group/child listener interfaces; ExpandableListContextMenuInfo). The
// adapter machinery is omitted until a target actually drives an expandable
// list; add on demand.
package android.widget;

import android.content.Context;
import android.view.View;

public class ExpandableListView extends ListView {

    /** The packed position represents a group. */
    public static final int PACKED_POSITION_TYPE_GROUP = 0;
    /** The packed position represents a child. */
    public static final int PACKED_POSITION_TYPE_CHILD = 1;
    /** The packed position represents a neither/null position. */
    public static final int PACKED_POSITION_TYPE_NULL = 2;

    /** The value for a packed position that represents neither/null. */
    public static final long PACKED_POSITION_VALUE_NULL = 0x00000000FFFFFFFFL;

    public ExpandableListView(Context context) { super(context); }
    public ExpandableListView(Context context, android.util.AttributeSet attrs) { super(context, attrs); }
    /** AOSP API 21 four-argument constructor. AOSP's three-argument form is
     *  exactly this(context, attrs, defStyleAttr, 0), so delegating the other
     *  way is identical for defStyleRes == 0; a non-zero defStyleRes is
     *  DROPPED here, as in the existing FrameLayout/Button four-argument forms
     *  (TextView, ProgressBar and their subclasses honour it). Without it an
     *  app subclass calling the four-argument
     *  super() dies with NoSuchMethodError; see debug/ctorsdk_gate.sh. */
    public ExpandableListView(Context context, android.util.AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        this(context, attrs, defStyleAttr);
    }
    public ExpandableListView(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    // ---- Adapter + expansion state (v1.0 widget-bucket close) ----
    // ExpandableListView holds an ExpandableListAdapter (NOT a plain
    // ListAdapter). We track which groups are expanded and dispatch
    // expand/collapse + group/child clicks faithfully. Row rendering reuses
    // the AbsListView recycler via an internal flattening adapter.
    private ExpandableListAdapter mExpandableAdapter;
    private final java.util.HashSet<Integer> mExpandedGroups =
            new java.util.HashSet<Integer>();
    private OnGroupClickListener mGroupClickListener;
    private OnChildClickListener mChildClickListener;
    private OnGroupExpandListener mGroupExpandListener;
    private OnGroupCollapseListener mGroupCollapseListener;

    public void setAdapter(ExpandableListAdapter adapter) {
        mExpandableAdapter = adapter;
        super.setAdapter(adapter == null ? null : new FlatteningAdapter());
    }
    public ExpandableListAdapter getExpandableListAdapter() { return mExpandableAdapter; }

    public boolean isGroupExpanded(int groupPosition) {
        return mExpandedGroups.contains(groupPosition);
    }

    public boolean expandGroup(int groupPosition) {
        if (mExpandableAdapter == null) return false;
        if (mExpandedGroups.add(groupPosition)) {
            mExpandableAdapter.onGroupExpanded(groupPosition);
            if (mGroupExpandListener != null) mGroupExpandListener.onGroupExpand(groupPosition);
            ((FlatteningAdapter) getAdapter()).notifyDataSetChanged();
            return true;
        }
        return false;
    }
    public boolean expandGroup(int groupPosition, boolean animate) { return expandGroup(groupPosition); }

    public boolean collapseGroup(int groupPosition) {
        if (mExpandableAdapter == null) return false;
        if (mExpandedGroups.remove(groupPosition)) {
            mExpandableAdapter.onGroupCollapsed(groupPosition);
            if (mGroupCollapseListener != null) mGroupCollapseListener.onGroupCollapse(groupPosition);
            ((FlatteningAdapter) getAdapter()).notifyDataSetChanged();
            return true;
        }
        return false;
    }

    // Maps a flat list position to its (group, child) packed position by
    // walking groups and their expanded children in order.
    public long getExpandableListPosition(int flatListPosition) {
        if (mExpandableAdapter == null) return PACKED_POSITION_VALUE_NULL;
        int pos = 0;
        int groups = mExpandableAdapter.getGroupCount();
        for (int g = 0; g < groups; g++) {
            if (pos == flatListPosition) return getPackedPositionForGroup(g);
            pos++;
            if (mExpandedGroups.contains(g)) {
                int children = mExpandableAdapter.getChildrenCount(g);
                if (flatListPosition < pos + children) {
                    return getPackedPositionForChild(g, flatListPosition - pos);
                }
                pos += children;
            }
        }
        return PACKED_POSITION_VALUE_NULL;
    }

    // Flattens groups + expanded children into the single-list AbsListView
    // recycler, dispatching the right group/child views and click callbacks.
    private final class FlatteningAdapter extends BaseAdapter {
        @Override public int getCount() {
            if (mExpandableAdapter == null) return 0;
            int n = mExpandableAdapter.getGroupCount();
            for (Integer g : mExpandedGroups) {
                if (g >= 0 && g < mExpandableAdapter.getGroupCount()) {
                    n += mExpandableAdapter.getChildrenCount(g);
                }
            }
            return n;
        }
        @Override public Object getItem(int position) { return Long.valueOf(getExpandableListPosition(position)); }
        @Override public long getItemId(int position) { return position; }
        @Override public android.view.View getView(int position, android.view.View convertView,
                android.view.ViewGroup parent) {
            long packed = getExpandableListPosition(position);
            int type = getPackedPositionType(packed);
            final int group = getPackedPositionGroup(packed);
            if (type == PACKED_POSITION_TYPE_CHILD) {
                final int child = getPackedPositionChild(packed);
                boolean last = child == mExpandableAdapter.getChildrenCount(group) - 1;
                android.view.View v = mExpandableAdapter.getChildView(group, child, last, convertView, parent);
                v.setOnClickListener(new android.view.View.OnClickListener() {
                    @Override public void onClick(android.view.View view) {
                        if (mChildClickListener != null) {
                            mChildClickListener.onChildClick(ExpandableListView.this, view,
                                    group, child, mExpandableAdapter.getChildId(group, child));
                        }
                    }
                });
                return v;
            } else {
                android.view.View v = mExpandableAdapter.getGroupView(group,
                        isGroupExpanded(group), convertView, parent);
                v.setOnClickListener(new android.view.View.OnClickListener() {
                    @Override public void onClick(android.view.View view) {
                        boolean handled = mGroupClickListener != null
                                && mGroupClickListener.onGroupClick(ExpandableListView.this, view,
                                        group, mExpandableAdapter.getGroupId(group));
                        if (!handled) {
                            if (isGroupExpanded(group)) collapseGroup(group);
                            else expandGroup(group);
                        }
                    }
                });
                return v;
            }
        }
    }

    // ---- Indicator + child divider (AOSP API cluster) --------------------
    //
    // Organic Maps drives these. AOSP source: frameworks/base/core/java/
    // android/widget/ExpandableListView.java (android11-release) -- the fields
    // at lines 137-175, setChildDivider at 525, setIndicatorBounds at 1234,
    // setIndicatorBoundsRelative at 1251, and resolveIndicator at 311-333.
    //
    // CHILD_INDICATOR_INHERIT means "use the general indicator's bound".

    /** Denotes when a child indicator should inherit this bound from the
     *  generic indicator bounds (AOSP ExpandableListView.java:179). */
    public static final int CHILD_INDICATOR_INHERIT = -1;

    /** Denotes an undefined value for an indicator (AOSP:184). Distinct from
     *  CHILD_INDICATOR_INHERIT so that resolveIndicator's `>= 0` test can tell
     *  "never set" from a legitimate bound of 0. */
    private static final int INDICATOR_UNDEFINED = -2;

    private android.graphics.drawable.Drawable mChildDivider;
    private android.graphics.drawable.Drawable mGroupIndicator;
    private android.graphics.drawable.Drawable mChildIndicator;

    /** Left/right bounds for drawing the indicator. AOSP reads these from
     *  indicatorLeft/indicatorRight with a default of 0 (AOSP:252-255). */
    private int mIndicatorLeft;
    private int mIndicatorRight;
    /** Start/end bounds for drawing the indicator; these WIN over left/right
     *  once set, which is what resolveIndicator implements. AOSP defaults them
     *  to INDICATOR_UNDEFINED when the app supports RTL (AOSP:269-274). */
    private int mIndicatorStart = INDICATOR_UNDEFINED;
    private int mIndicatorEnd = INDICATOR_UNDEFINED;

    private int mChildIndicatorLeft = CHILD_INDICATOR_INHERIT;
    private int mChildIndicatorRight = CHILD_INDICATOR_INHERIT;
    private int mChildIndicatorStart = CHILD_INDICATOR_INHERIT;
    private int mChildIndicatorEnd = CHILD_INDICATOR_INHERIT;

    /**
     * Sets the drawable drawn adjacent to every CHILD in the list (groups keep
     * the ListView divider). Drawn at the same height as the normal divider.
     *
     * NOTE: the drawable is stored faithfully, but nothing consults it yet.
     * AOSP draws it by overriding ListView's package-private
     * drawDivider(Canvas, Rect, int) hook (ExpandableListView.java:539-553);
     * this tree's ListView has no divider-drawing pass at all, so there is no
     * hook to override. The gap is in ListView, not here -- storing the
     * drawable is the whole of this setter's contract, and a getter would
     * report it correctly.
     */
    public void setChildDivider(android.graphics.drawable.Drawable childDivider) {
        mChildDivider = childDivider;
    }

    /** Sets the indicator drawn next to a group. AOSP re-derives the right
     *  bound from the drawable's intrinsic width when the bound is still 0
     *  (ExpandableListView.java:1216-1221). */
    public void setGroupIndicator(android.graphics.drawable.Drawable groupIndicator) {
        mGroupIndicator = groupIndicator;
        if (mIndicatorRight == 0 && mGroupIndicator != null) {
            mIndicatorRight = mIndicatorLeft + mGroupIndicator.getIntrinsicWidth();
        }
    }

    /** Sets the indicator drawn next to a child. */
    public void setChildIndicator(android.graphics.drawable.Drawable childIndicator) {
        mChildIndicator = childIndicator;
    }

    /**
     * Sets the drawing bounds for the indicators, relative to this View's LEFT
     * edge. At minimum the group indicator is affected; the child indicator is
     * too when its own bounds are set to inherit.
     */
    public void setIndicatorBounds(int left, int right) {
        mIndicatorLeft = left;
        mIndicatorRight = right;
        resolveIndicator();
    }

    /**
     * Sets the drawing bounds for the indicators, relative to this View's START
     * edge -- so they swap meaning under an RTL layout direction. Start/end
     * always takes precedence over left/right once defined.
     */
    public void setIndicatorBoundsRelative(int start, int end) {
        mIndicatorStart = start;
        mIndicatorEnd = end;
        resolveIndicator();
    }

    /** Sets the drawing bounds for the child indicator relative to the left
     *  edge; CHILD_INDICATOR_INHERIT means inherit the general indicator's. */
    public void setChildIndicatorBounds(int left, int right) {
        mChildIndicatorLeft = left;
        mChildIndicatorRight = right;
        resolveChildIndicator();
    }

    /** Sets the drawing bounds for the child indicator relative to the start
     *  edge; CHILD_INDICATOR_INHERIT means inherit the general indicator's. */
    public void setChildIndicatorBoundsRelative(int start, int end) {
        mChildIndicatorStart = start;
        mChildIndicatorEnd = end;
        resolveChildIndicator();
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        resolveIndicator();
        resolveChildIndicator();
    }

    /** AOSP resolveIndicator (ExpandableListView.java:311-333). Start/end
     *  indicator always takes precedence over left/right when defined, and
     *  which of left/right a start maps to flips with the layout direction. */
    private void resolveIndicator() {
        final boolean isLayoutRtl = isLayoutRtl();
        if (isLayoutRtl) {
            if (mIndicatorStart >= 0) {
                mIndicatorRight = mIndicatorStart;
            }
            if (mIndicatorEnd >= 0) {
                mIndicatorLeft = mIndicatorEnd;
            }
        } else {
            if (mIndicatorStart >= 0) {
                mIndicatorLeft = mIndicatorStart;
            }
            if (mIndicatorEnd >= 0) {
                mIndicatorRight = mIndicatorEnd;
            }
        }
        if (mIndicatorRight == 0 && mGroupIndicator != null) {
            mIndicatorRight = mIndicatorLeft + mGroupIndicator.getIntrinsicWidth();
        }
    }

    /** AOSP resolveChildIndicator (ExpandableListView.java:339-357). */
    private void resolveChildIndicator() {
        final boolean isLayoutRtl = isLayoutRtl();
        if (isLayoutRtl) {
            if (mChildIndicatorStart >= CHILD_INDICATOR_INHERIT) {
                mChildIndicatorRight = mChildIndicatorStart;
            }
            if (mChildIndicatorEnd >= CHILD_INDICATOR_INHERIT) {
                mChildIndicatorLeft = mChildIndicatorEnd;
            }
        } else {
            if (mChildIndicatorStart >= CHILD_INDICATOR_INHERIT) {
                mChildIndicatorLeft = mChildIndicatorStart;
            }
            if (mChildIndicatorEnd >= CHILD_INDICATOR_INHERIT) {
                mChildIndicatorRight = mChildIndicatorEnd;
            }
        }
    }

    // ---- Packed-position helpers (AOSP static utilities) ----

    public static long getPackedPositionForChild(int groupPosition, int childPosition) {
        return (((long) PACKED_POSITION_TYPE_CHILD) << 62)
                | (((long) groupPosition & 0x7FFFFFFFL) << 32)
                | (childPosition & 0xFFFFFFFFL);
    }
    public static long getPackedPositionForGroup(int groupPosition) {
        return (((long) PACKED_POSITION_TYPE_GROUP) << 62)
                | (((long) groupPosition & 0x7FFFFFFFL) << 32);
    }
    public static int getPackedPositionType(long packedPosition) {
        if (packedPosition == PACKED_POSITION_VALUE_NULL) return PACKED_POSITION_TYPE_NULL;
        return (int) ((packedPosition & (0x4000000000000000L)) != 0
                ? PACKED_POSITION_TYPE_CHILD : PACKED_POSITION_TYPE_GROUP);
    }
    public static int getPackedPositionGroup(long packedPosition) {
        if (packedPosition == PACKED_POSITION_VALUE_NULL) return -1;
        return (int) ((packedPosition & 0x7FFFFFFF00000000L) >> 32);
    }
    public static int getPackedPositionChild(long packedPosition) {
        if (packedPosition == PACKED_POSITION_VALUE_NULL) return -1;
        if ((packedPosition & 0x4000000000000000L) == 0) return -1;
        return (int) (packedPosition & 0xFFFFFFFFL);
    }

    // ---- Listener interfaces (AOSP) ----

    public interface OnGroupClickListener {
        boolean onGroupClick(ExpandableListView parent, View v, int groupPosition, long id);
    }
    public interface OnChildClickListener {
        boolean onChildClick(ExpandableListView parent, View v,
                int groupPosition, int childPosition, long id);
    }
    public interface OnGroupCollapseListener {
        void onGroupCollapse(int groupPosition);
    }
    public interface OnGroupExpandListener {
        void onGroupExpand(int groupPosition);
    }

    public void setOnGroupClickListener(OnGroupClickListener l) { mGroupClickListener = l; }
    public void setOnChildClickListener(OnChildClickListener l) { mChildClickListener = l; }
    public void setOnGroupCollapseListener(OnGroupCollapseListener l) { mGroupCollapseListener = l; }
    public void setOnGroupExpandListener(OnGroupExpandListener l) { mGroupExpandListener = l; }

    /** Extra menu information specific to an ExpandableListView provided to the
     *  ContextMenu.ContextMenuInfo callback. */
    public static class ExpandableListContextMenuInfo
            implements android.view.ContextMenu.ContextMenuInfo {
        public View targetView;
        public long packedPosition;
        public long id;
        public ExpandableListContextMenuInfo(View targetView, long packedPosition, long id) {
            this.targetView = targetView;
            this.packedPosition = packedPosition;
            this.id = id;
        }
    }

    // ==================================================================
    // API-36 surface completion (apidiff: android.widget.ExpandableListView had
    // 5 public members missing -- the selection family and the inverse of
    // getExpandableListPosition). AOSP:
    // frameworks/base/core/java/android/widget/ExpandableListView.java
    // (android16-release); cited line numbers are from that file.
    // ==================================================================

    /** AOSP :929 -- the inverse of getExpandableListPosition: packed position
     *  -> flat list position.
     *  ⚠ AOSP additionally shifts by the header count (getAbsoluteFlatPosition /
     *  getFlatPositionForConnector). This class's getExpandableListPosition
     *  above does NOT apply that shift, so applying it on only one side would
     *  break the round trip these two are supposed to form. The pair is
     *  therefore consistent with each other and both are header-relative; that
     *  is a pre-existing divergence from AOSP in getExpandableListPosition, not
     *  one introduced here. */
    public int getFlatListPosition(long packedPosition) {
        if (mExpandableAdapter == null) return AdapterView.INVALID_POSITION;
        final int wantType = getPackedPositionType(packedPosition);
        if (wantType == PACKED_POSITION_TYPE_NULL) return AdapterView.INVALID_POSITION;
        final int wantGroup = getPackedPositionGroup(packedPosition);
        final int wantChild = getPackedPositionChild(packedPosition);

        int pos = 0;
        final int groups = mExpandableAdapter.getGroupCount();
        for (int g = 0; g < groups; g++) {
            if (g == wantGroup && wantType == PACKED_POSITION_TYPE_GROUP) return pos;
            pos++;
            if (mExpandedGroups.contains(g)) {
                final int children = mExpandableAdapter.getChildrenCount(g);
                if (g == wantGroup && wantType == PACKED_POSITION_TYPE_CHILD) {
                    if (wantChild < 0 || wantChild >= children) {
                        return AdapterView.INVALID_POSITION;
                    }
                    return pos + wantChild;
                }
                pos += children;
            } else if (g == wantGroup) {
                // The group is collapsed, so the child has no flat position.
                return AdapterView.INVALID_POSITION;
            }
        }
        return AdapterView.INVALID_POSITION;
    }

    /** AOSP :947 -- verbatim. */
    public long getSelectedPosition() {
        return getExpandableListPosition(getSelectedItemPosition());
    }

    /** AOSP :961 -- verbatim; -1 when nothing is selected. */
    public long getSelectedId() {
        final long packedPos = getSelectedPosition();
        if (packedPos == PACKED_POSITION_VALUE_NULL) return -1;
        final int groupPos = getPackedPositionGroup(packedPos);
        if (getPackedPositionType(packedPos) == PACKED_POSITION_TYPE_GROUP) {
            return mExpandableAdapter.getGroupId(groupPos);
        }
        return mExpandableAdapter.getChildId(groupPos, getPackedPositionChild(packedPos));
    }

    /** AOSP :980 -- verbatim in effect: resolve the group's flat position and
     *  select it. */
    public void setSelectedGroup(int groupPosition) {
        final int flat = getFlatListPosition(getPackedPositionForGroup(groupPosition));
        if (flat != AdapterView.INVALID_POSITION) super.setSelection(flat);
    }

    /** AOSP :1001 -- if the child's group is collapsed, expand it first only
     *  when the caller asked; otherwise refuse and report it. */
    public boolean setSelectedChild(int groupPosition, int childPosition,
            boolean shouldExpandGroup) {
        int flat = getFlatListPosition(getPackedPositionForChild(groupPosition, childPosition));
        if (flat == AdapterView.INVALID_POSITION) {
            if (!shouldExpandGroup) return false;
            expandGroup(groupPosition);
            flat = getFlatListPosition(getPackedPositionForChild(groupPosition, childPosition));
            if (flat == AdapterView.INVALID_POSITION) {
                // AOSP throws IllegalStateException("Could not find child") here.
                throw new IllegalStateException("Could not find child");
            }
        }
        super.setSelection(flat);
        return true;
    }
}
