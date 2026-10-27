package hello.cpprovider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

public class OpenProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sortOrder) {
        MatrixCursor cursor = new MatrixCursor(new String[] {"word"});
        cursor.addRow(new Object[] {"open"});
        return cursor;
    }
    @Override public android.os.ParcelFileDescriptor openFile(Uri uri, String mode) throws java.io.FileNotFoundException {
        try {
            java.io.File file = new java.io.File(getContext().getCacheDir(), "shared.txt");
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) { out.write(("file from provider pid " + android.os.Process.myPid()).getBytes()); }
            return android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (java.io.IOException failure) { throw new java.io.FileNotFoundException(failure.toString()); }
    }
    @Override public String getType(Uri uri) { return "text/plain"; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
}
