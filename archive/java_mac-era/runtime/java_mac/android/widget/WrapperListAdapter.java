// android.widget.WrapperListAdapter -- a ListAdapter that wraps another
// ListAdapter (used by HeaderViewListAdapter so callers can reach the
// underlying adapter). AOSP:
//   frameworks/base/core/java/android/widget/WrapperListAdapter.java
package android.widget;

public interface WrapperListAdapter extends ListAdapter {
    ListAdapter getWrappedAdapter();
}
