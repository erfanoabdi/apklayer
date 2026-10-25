// android.widget.FilterQueryProvider -- lets a CursorAdapter run a custom
// query when its filter constraint changes. AOSP:
//   frameworks/base/core/java/android/widget/FilterQueryProvider.java
package android.widget;

import android.database.Cursor;

public interface FilterQueryProvider {
    Cursor runQuery(CharSequence constraint);
}
