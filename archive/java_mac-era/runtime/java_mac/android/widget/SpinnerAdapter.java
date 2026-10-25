// android.widget.SpinnerAdapter -- the other specialization of Adapter.
// Apps that import android.widget.SpinnerAdapter (or assignments from
// BaseAdapter) need it on the classpath even when no spinner gets shown.
package android.widget;

import android.view.View;
import android.view.ViewGroup;

public interface SpinnerAdapter extends Adapter {
    View getDropDownView(int position, View convertView, ViewGroup parent);
}
