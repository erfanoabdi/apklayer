// android.widget.ArrayAdapter -- the canonical adapter backing a list with
// an in-memory array/list of objects, rendering each as a TextView. Was a
// bare `extends Object` stub, which broke instanceof BaseAdapter and every
// Spinner/ListView populated via ArrayAdapter.createFromResource. Now the
// real BaseAdapter subclass with add/insert/remove/clear, position lookup,
// a TextView-rendering getView, and a Filter. AOSP:
//   frameworks/base/core/java/android/widget/ArrayAdapter.java
//   public class ArrayAdapter<T> extends BaseAdapter
//       implements Filterable, ThemedSpinnerAdapter
package android.widget;

import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public class ArrayAdapter<T> extends BaseAdapter
        implements Filterable, ThemedSpinnerAdapter {

    private final Object mLock = new Object();

    private List<T> mObjects;
    // When a filter is active mObjects is the filtered view and
    // mOriginalValues holds the full set (AOSP semantics).
    private ArrayList<T> mOriginalValues;

    private final int mResource;
    private int mDropDownResource;
    private final int mFieldId;
    private boolean mNotifyOnChange = true;

    private final Context mContext;
    private final LayoutInflater mInflater;
    private LayoutInflater mDropDownInflater;
    private Resources.Theme mDropDownViewTheme;

    private ArrayFilter mFilter;

    public ArrayAdapter(Context context, int resource) {
        this(context, resource, 0, new ArrayList<T>());
    }

    public ArrayAdapter(Context context, int resource, int textViewResourceId) {
        this(context, resource, textViewResourceId, new ArrayList<T>());
    }

    public ArrayAdapter(Context context, int resource, T[] objects) {
        this(context, resource, 0, new ArrayList<T>(Arrays.asList(objects)));
    }

    public ArrayAdapter(Context context, int resource, int textViewResourceId,
            T[] objects) {
        this(context, resource, textViewResourceId,
                new ArrayList<T>(Arrays.asList(objects)));
    }

    public ArrayAdapter(Context context, int resource, List<T> objects) {
        this(context, resource, 0, objects);
    }

    public ArrayAdapter(Context context, int resource, int textViewResourceId,
            List<T> objects) {
        mContext = context;
        mInflater = LayoutInflater.from(context);
        mResource = mDropDownResource = resource;
        mObjects = objects;
        mFieldId = textViewResourceId;
    }

    public void add(T object) {
        synchronized (mLock) {
            if (mOriginalValues != null) mOriginalValues.add(object);
            else mObjects.add(object);
        }
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    public void addAll(Collection<? extends T> collection) {
        synchronized (mLock) {
            if (mOriginalValues != null) mOriginalValues.addAll(collection);
            else mObjects.addAll(collection);
        }
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    @SafeVarargs
    public final void addAll(T... items) {
        synchronized (mLock) {
            if (mOriginalValues != null) {
                java.util.Collections.addAll(mOriginalValues, items);
            } else {
                java.util.Collections.addAll(mObjects, items);
            }
        }
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    public void insert(T object, int index) {
        synchronized (mLock) {
            if (mOriginalValues != null) mOriginalValues.add(index, object);
            else mObjects.add(index, object);
        }
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    public void remove(T object) {
        synchronized (mLock) {
            if (mOriginalValues != null) mOriginalValues.remove(object);
            else mObjects.remove(object);
        }
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    public void clear() {
        synchronized (mLock) {
            if (mOriginalValues != null) mOriginalValues.clear();
            else mObjects.clear();
        }
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    public void sort(Comparator<? super T> comparator) {
        synchronized (mLock) {
            if (mOriginalValues != null) {
                java.util.Collections.sort(mOriginalValues, comparator);
            } else {
                java.util.Collections.sort(mObjects, comparator);
            }
        }
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    @Override public void notifyDataSetChanged() {
        super.notifyDataSetChanged();
        mNotifyOnChange = true;
    }

    public void setNotifyOnChange(boolean notifyOnChange) {
        mNotifyOnChange = notifyOnChange;
    }

    public Context getContext() { return mContext; }

    @Override public int getCount() { return mObjects.size(); }

    @Override public T getItem(int position) { return mObjects.get(position); }

    public int getPosition(T item) { return mObjects.indexOf(item); }

    @Override public long getItemId(int position) { return position; }

    @Override public View getView(int position, View convertView, ViewGroup parent) {
        return createViewFromResource(mInflater, position, convertView, parent,
                mResource);
    }

    @Override public View getDropDownView(int position, View convertView,
            ViewGroup parent) {
        final LayoutInflater inflater = mDropDownInflater == null
                ? mInflater : mDropDownInflater;
        return createViewFromResource(inflater, position, convertView, parent,
                mDropDownResource);
    }

    public void setDropDownViewResource(int resource) {
        mDropDownResource = resource;
    }

    private View createViewFromResource(LayoutInflater inflater, int position,
            View convertView, ViewGroup parent, int resource) {
        final View view;
        final TextView text;

        if (convertView == null) {
            view = resource != 0
                    ? inflater.inflate(resource, parent, false)
                    : new TextView(mContext);
        } else {
            view = convertView;
        }

        // AOSP: if a fieldId was given, find that child TextView; else assume
        // the inflated view is itself a TextView. We tolerate a non-TextView
        // by falling back to a fresh TextView so getView never throws.
        if (view instanceof TextView) {
            text = (TextView) view;
        } else if (mFieldId != 0 && view instanceof ViewGroup) {
            View t = view.findViewById(mFieldId);
            text = (t instanceof TextView) ? (TextView) t : new TextView(mContext);
        } else {
            text = new TextView(mContext);
        }

        final T item = getItem(position);
        if (item instanceof CharSequence) {
            text.setText((CharSequence) item);
        } else {
            text.setText(item == null ? "" : item.toString());
        }
        return view;
    }

    public static ArrayAdapter<CharSequence> createFromResource(Context context,
            int textArrayResId, int textViewResId) {
        Resources r = context.getResources();
        CharSequence[] arr;
        try {
            arr = r != null ? r.getTextArray(textArrayResId) : new CharSequence[0];
        } catch (Throwable t) {
            arr = new CharSequence[0];
        }
        return new ArrayAdapter<CharSequence>(context, textViewResId, arr);
    }

    @Override public Filter getFilter() {
        if (mFilter == null) mFilter = new ArrayFilter();
        return mFilter;
    }

    @Override public void setDropDownViewTheme(Resources.Theme theme) {
        mDropDownViewTheme = theme;
        if (theme == null) {
            mDropDownInflater = null;
        } else {
            mDropDownInflater = mInflater;
        }
    }

    @Override public Resources.Theme getDropDownViewTheme() {
        return mDropDownViewTheme;
    }

    // AOSP ArrayFilter: case-insensitive prefix match over toString(), with
    // an original-values snapshot so repeated filtering composes correctly.
    private class ArrayFilter extends Filter {
        @Override protected FilterResults performFiltering(CharSequence prefix) {
            final FilterResults results = new FilterResults();
            if (mOriginalValues == null) {
                synchronized (mLock) {
                    mOriginalValues = new ArrayList<T>(mObjects);
                }
            }
            if (prefix == null || prefix.length() == 0) {
                final ArrayList<T> list;
                synchronized (mLock) { list = new ArrayList<T>(mOriginalValues); }
                results.values = list;
                results.count = list.size();
            } else {
                final String prefixString = prefix.toString().toLowerCase();
                final ArrayList<T> values;
                synchronized (mLock) {
                    values = new ArrayList<T>(mOriginalValues);
                }
                final ArrayList<T> newValues = new ArrayList<T>();
                for (T value : values) {
                    final String valueText = value == null
                            ? "" : value.toString().toLowerCase();
                    if (valueText.startsWith(prefixString)) {
                        newValues.add(value);
                    } else {
                        for (String word : valueText.split(" ")) {
                            if (word.startsWith(prefixString)) {
                                newValues.add(value);
                                break;
                            }
                        }
                    }
                }
                results.values = newValues;
                results.count = newValues.size();
            }
            return results;
        }

        @Override @SuppressWarnings("unchecked")
        protected void publishResults(CharSequence constraint, FilterResults results) {
            mObjects = (List<T>) results.values;
            if (results.count > 0) notifyDataSetChanged();
            else notifyDataSetInvalidated();
        }
    }
}
