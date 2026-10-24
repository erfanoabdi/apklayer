// android.widget.ListAdapter -- Adapter specialization that adds
// per-item enabled/disabled queries. Apps reference it as a parameter
// type for ListView.setAdapter(...).
package android.widget;

public interface ListAdapter extends Adapter {
    boolean areAllItemsEnabled();
    boolean isEnabled(int position);
}
