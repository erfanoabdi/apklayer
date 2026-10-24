// android.database.CharArrayBuffer -- the reusable char[] target that
// Cursor.copyStringToBuffer fills (the classic CursorAdapter / SimpleCursorAdapter
// binding path avoids a String allocation per row by copying into this). AOSP
// exposes the public `data` + `sizeCopied` fields directly; v1.0.426 gave the
// previously-empty stub that real shape so copyStringToBuffer can populate it.
// Source: frameworks/base/core/java/android/database/CharArrayBuffer.java
package android.database;

public class CharArrayBuffer {
    public char[] data;
    public int sizeCopied;

    public CharArrayBuffer(int size) { data = new char[size]; }
    public CharArrayBuffer(char[] data) { this.data = data; }
}
