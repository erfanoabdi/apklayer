// android.widget.Filterable -- a data source whose contents can be
// filtered by a constraint (typed query). ArrayAdapter / CursorAdapter /
// SimpleAdapter implement it; AutoCompleteTextView pulls the filter off
// its adapter via this interface. Was Absent, which broke any cast of an
// adapter to Filterable. AOSP:
//   frameworks/base/core/java/android/widget/Filterable.java
package android.widget;

public interface Filterable {
    Filter getFilter();
}
