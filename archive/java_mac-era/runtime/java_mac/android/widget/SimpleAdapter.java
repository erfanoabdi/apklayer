// android.widget.SimpleAdapter -- maps a List<Map<String,?>> onto rows of a
// layout, binding each "from" key to a "to" view id. Was Absent. AOSP:
//   frameworks/base/core/java/android/widget/SimpleAdapter.java
//   public class SimpleAdapter extends BaseAdapter
//       implements Filterable, ThemedSpinnerAdapter
package android.widget;

import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SimpleAdapter extends BaseAdapter
        implements Filterable, ThemedSpinnerAdapter {

    public interface ViewBinder {
        boolean setViewValue(View view, Object data, String textRepresentation);
    }

    private int[] mTo;
    private String[] mFrom;
    private ViewBinder mViewBinder;

    private List<? extends Map<String, ?>> mData;

    private int mResource;
    private int mDropDownResource;
    private LayoutInflater mInflater;

    private final Context mContext;
    private Resources.Theme mDropDownViewTheme;

    private SimpleFilter mFilter;
    private ArrayList<Map<String, ?>> mUnfilteredData;

    public SimpleAdapter(Context context, List<? extends Map<String, ?>> data,
            int resource, String[] from, int[] to) {
        mContext = context;
        mData = data;
        mResource = mDropDownResource = resource;
        mFrom = from;
        mTo = to;
        mInflater = LayoutInflater.from(context);
    }

    @Override public int getCount() { return mData != null ? mData.size() : 0; }

    @Override public Object getItem(int position) { return mData.get(position); }

    @Override public long getItemId(int position) { return position; }

    @Override public View getView(int position, View convertView, ViewGroup parent) {
        return createViewFromResource(position, convertView, parent, mResource);
    }

    private View createViewFromResource(int position, View convertView,
            ViewGroup parent, int resource) {
        View v = convertView == null
                ? mInflater.inflate(resource, parent, false) : convertView;
        bindView(position, v);
        return v;
    }

    public void setDropDownViewResource(int resource) {
        mDropDownResource = resource;
    }

    @Override public View getDropDownView(int position, View convertView,
            ViewGroup parent) {
        return createViewFromResource(position, convertView, parent,
                mDropDownResource);
    }

    private void bindView(int position, View view) {
        final Map<String, ?> dataSet = mData.get(position);
        if (dataSet == null) return;

        final ViewBinder binder = mViewBinder;
        final String[] from = mFrom;
        final int[] to = mTo;
        final int count = to.length;

        for (int i = 0; i < count; i++) {
            final View v = view.findViewById(to[i]);
            if (v != null) {
                final Object data = dataSet.get(from[i]);
                String text = data == null ? "" : data.toString();

                boolean bound = false;
                if (binder != null) {
                    bound = binder.setViewValue(v, data, text);
                }
                if (!bound) {
                    if (v instanceof Checkable) {
                        if (data instanceof Boolean) {
                            ((Checkable) v).setChecked((Boolean) data);
                        } else if (v instanceof TextView) {
                            setViewText((TextView) v, text);
                        }
                    } else if (v instanceof TextView) {
                        setViewText((TextView) v, text);
                    } else if (v instanceof ImageView) {
                        if (data instanceof Integer) {
                            setViewImage((ImageView) v, (Integer) data);
                        } else {
                            setViewImage((ImageView) v, text);
                        }
                    }
                }
            }
        }
    }

    public ViewBinder getViewBinder() { return mViewBinder; }
    public void setViewBinder(ViewBinder viewBinder) { mViewBinder = viewBinder; }

    public void setViewImage(ImageView v, int value) { v.setImageResource(value); }

    public void setViewImage(ImageView v, String value) {
        try {
            v.setImageResource(Integer.parseInt(value));
        } catch (NumberFormatException nfe) {
            v.setImageURI(android.net.Uri.parse(value));
        }
    }

    public void setViewText(TextView v, String text) { v.setText(text); }

    @Override public Filter getFilter() {
        if (mFilter == null) mFilter = new SimpleFilter();
        return mFilter;
    }

    @Override public void setDropDownViewTheme(Resources.Theme theme) {
        mDropDownViewTheme = theme;
    }

    @Override public Resources.Theme getDropDownViewTheme() {
        return mDropDownViewTheme;
    }

    private class SimpleFilter extends Filter {
        @Override @SuppressWarnings("unchecked")
        protected FilterResults performFiltering(CharSequence prefix) {
            FilterResults results = new FilterResults();

            if (mUnfilteredData == null) {
                mUnfilteredData = new ArrayList<Map<String, ?>>(mData);
            }

            if (prefix == null || prefix.length() == 0) {
                ArrayList<Map<String, ?>> list =
                        new ArrayList<Map<String, ?>>(mUnfilteredData);
                results.values = list;
                results.count = list.size();
            } else {
                String prefixString = prefix.toString().toLowerCase();
                ArrayList<Map<String, ?>> unfilteredValues = mUnfilteredData;
                int count = unfilteredValues.size();
                ArrayList<Map<String, ?>> newValues =
                        new ArrayList<Map<String, ?>>(count);

                for (int i = 0; i < count; i++) {
                    Map<String, ?> h = unfilteredValues.get(i);
                    if (h != null) {
                        int len = mTo.length;
                        for (int j = 0; j < len; j++) {
                            String str = String.valueOf(h.get(mFrom[j]));
                            String[] words = str.split(" ");
                            for (String word : words) {
                                if (word.toLowerCase().startsWith(prefixString)) {
                                    newValues.add(h);
                                    break;
                                }
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
            mData = (List<? extends Map<String, ?>>) results.values;
            if (results.count > 0) notifyDataSetChanged();
            else notifyDataSetInvalidated();
        }
    }
}
