package android.database;
// AOSP's Cursor extends java.io.Closeable (since API 16) so apps can
// use try-with-resources and Room can iterate via AutoCloseable.
// Without this declaration, the JVM throws
// IncompatibleClassChangeError when Room casts a Cursor to Closeable.
// Mirrored from frameworks/base/core/java/android/database/Cursor.java.
public interface Cursor extends java.io.Closeable {
    // Column value types (API 11+). AOSP Cursor.FIELD_TYPE_* -- used by
    // AbstractWindowedCursor.is{Null,Blob,String,Long,Float} and by
    // Room/SQLite consumers that branch on getType().
    int FIELD_TYPE_NULL    = 0;
    int FIELD_TYPE_INTEGER = 1;
    int FIELD_TYPE_FLOAT   = 2;
    int FIELD_TYPE_STRING  = 3;
    int FIELD_TYPE_BLOB    = 4;
    /** AOSP Cursor.getType(columnIndex): the value type of the column at the
     *  current row. Was declared only on concrete cursors (SQLiteCursorImpl /
     *  AbstractWindowedCursor), not on the interface -> Room/AndroidX calling it
     *  through a bare Cursor reference didn't resolve. Default returns STRING;
     *  concrete cursors override with the real type. v1.0.423. */
    default int getType(int columnIndex) { return FIELD_TYPE_STRING; }
    int getCount();
    int getPosition();
    boolean move(int offset);
    boolean moveToPosition(int position);
    boolean moveToFirst();
    boolean moveToLast();
    boolean moveToNext();
    boolean moveToPrevious();
    boolean isFirst();
    boolean isLast();
    boolean isBeforeFirst();
    boolean isAfterLast();
    boolean isClosed();
    int getColumnIndex(String columnName);
    int getColumnIndexOrThrow(String columnName);
    String getColumnName(int columnIndex);
    String[] getColumnNames();
    int getColumnCount();
    byte[] getBlob(int columnIndex);
    String getString(int columnIndex);
    short getShort(int columnIndex);
    int getInt(int columnIndex);
    long getLong(int columnIndex);
    float getFloat(int columnIndex);
    double getDouble(int columnIndex);
    boolean isNull(int columnIndex);
    void close();
    void registerContentObserver(ContentObserver observer);
    void unregisterContentObserver(ContentObserver observer);
    void registerDataSetObserver(DataSetObserver observer);
    void unregisterDataSetObserver(DataSetObserver observer);
    void setNotificationUri(android.content.ContentResolver cr, android.net.Uri uri);
    // Deprecated (pre-API 11) lifecycle methods still called by older apps
    // (HCR 1.8.1). default no-ops so implementors don't have to override and
    // the invokeinterface resolves. deactivate() releases resources but keeps
    // the cursor reusable; requery() re-runs the query (we treat both as no-ops
    // since our cursors hold their result for their lifetime).
    default void deactivate() {}
    default boolean requery() { return true; }

    // v1.0.426 Tier-2: interface defaults so consumers calling these through a
    // bare Cursor reference resolve (an unresolved interface method = Abstract
    // MethodError). copyStringToBuffer is on the classic CursorAdapter binding
    // path; getExtras/respond carry paging/query metadata; getNotificationUri
    // matters for ContentObserver-driven refresh.
    default void copyStringToBuffer(int columnIndex, CharArrayBuffer buffer) {
        if (buffer == null) return;
        String s = getString(columnIndex);
        if (s == null) s = "";
        if (buffer.data == null || buffer.data.length < s.length()) buffer.data = new char[s.length()];
        s.getChars(0, s.length(), buffer.data, 0);
        buffer.sizeCopied = s.length();
    }
    default android.os.Bundle getExtras() { return android.os.Bundle.EMPTY; }
    default void setExtras(android.os.Bundle extras) {}
    default android.os.Bundle respond(android.os.Bundle extras) { return android.os.Bundle.EMPTY; }
    default android.net.Uri getNotificationUri() { return null; }
    default boolean getWantsAllOnMoveCalls() { return false; }

    default void setNotificationUris(android.content.ContentResolver cr, java.util.List<android.net.Uri> uris) { }
    default java.util.List<android.net.Uri> getNotificationUris() { return null; }
}

