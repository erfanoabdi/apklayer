// android.widget.Adapter -- root interface for the AdapterView family.
// We never actually back a list with rendered rows, so this is just a
// surface for the verifier and for apps that take Adapter references
// and stash them somewhere.
package android.widget;

import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;

public interface Adapter {
    int IGNORE_ITEM_VIEW_TYPE = -1;
    int NO_SELECTION         = Integer.MIN_VALUE;

    void registerDataSetObserver(DataSetObserver observer);
    void unregisterDataSetObserver(DataSetObserver observer);

    int getCount();
    Object getItem(int position);
    long getItemId(int position);
    boolean hasStableIds();
    View getView(int position, View convertView, ViewGroup parent);
    int getItemViewType(int position);
    int getViewTypeCount();
    boolean isEmpty();

    /** AOSP Adapter.getAutofillOptions() (API 27, Adapter.java:165) -- a
     *  DEFAULT method, so every existing implementer keeps compiling; it
     *  supplies the autofill service with the set of values this adapter can
     *  produce. AOSP's default body is `return null`. */
    default CharSequence[] getAutofillOptions() { return null; }
}
