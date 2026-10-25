// android.provider.BaseColumns -- the _id / _count column names every
// Android content contract implements. Tiny but widely depended on:
// MediaStore, ContactsContract, CalendarContract, and app-defined
// contracts all "implements BaseColumns". Mirrors AOSP exactly.
package android.provider;

public interface BaseColumns {
    public static final String _ID = "_id";
    public static final String _COUNT = "_count";
}
