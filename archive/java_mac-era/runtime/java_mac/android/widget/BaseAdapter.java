// android.widget.BaseAdapter -- the standard convenience base for
// ListAdapter+SpinnerAdapter implementations. Apps subclass this even
// when their list never gets displayed, so the abstract surface needs
// to compile.
package android.widget;

import android.database.DataSetObservable;
import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;

public abstract class BaseAdapter implements ListAdapter, SpinnerAdapter {
    private final DataSetObservable observable = new DataSetObservable();

    @Override public boolean hasStableIds()    { return false; }
    @Override public boolean isEmpty()         { return getCount() == 0; }
    @Override public int  getItemViewType(int position) { return 0; }
    @Override public int  getViewTypeCount()   { return 1; }

    @Override public boolean areAllItemsEnabled() { return true; }
    @Override public boolean isEnabled(int position) { return true; }

    @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return getView(position, convertView, parent);
    }

    @Override public void registerDataSetObserver(DataSetObserver observer) {
        observable.registerObserver(observer);
    }
    @Override public void unregisterDataSetObserver(DataSetObserver observer) {
        observable.unregisterObserver(observer);
    }
    public void notifyDataSetChanged() { observable.notifyChanged(); }
    public void notifyDataSetInvalidated() { observable.notifyInvalidated(); }

    /** AOSP BaseAdapter.java:91/:98 -- the concrete override of
     *  Adapter.getAutofillOptions plus its setter. Note the setter is VARARGS
     *  in AOSP (`CharSequence... options`), which compiles to the same
     *  CharSequence[] parameter an app's array call binds to. */
    private CharSequence[] mAutofillOptions;

    @Override public CharSequence[] getAutofillOptions() { return mAutofillOptions; }
    public void setAutofillOptions(CharSequence... options) { mAutofillOptions = options; }
}
