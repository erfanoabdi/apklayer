package hello.cpprovider;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;
import java.util.ArrayList;
import java.util.List;

/** An in-memory table of (id, name) rows behind content://hello.cpprovider.items/items, read and write protected by permissions. */
public class ItemsProvider extends ContentProvider {
    static final List<String> ITEMS = new ArrayList<>(List.of("alpha", "beta"));

    @Override public boolean onCreate() { System.out.println("[cpp] ItemsProvider.onCreate pid=" + Process.myPid()); return true; }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sortOrder) {
        MatrixCursor cursor = new MatrixCursor(new String[] {"_id", "name"});
        synchronized (ITEMS) { for (int n = 0; n < ITEMS.size(); n++) cursor.addRow(new Object[] {n + 1L, ITEMS.get(n)}); }
        cursor.setNotificationUri(getContext().getContentResolver(), uri);
        return cursor;
    }

    @Override public String getType(Uri uri) { return "vnd.android.cursor.dir/vnd.hello.item"; }

    @Override public Uri insert(Uri uri, ContentValues values) {
        Uri added;
        synchronized (ITEMS) { ITEMS.add(values.getAsString("name")); added = ContentUris.withAppendedId(uri, ITEMS.size()); }
        getContext().getContentResolver().notifyChange(uri, null);
        return added;
    }

    @Override public int delete(Uri uri, String selection, String[] args) {
        synchronized (ITEMS) { int n = ITEMS.size(); ITEMS.clear(); return n; }
    }

    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) {
        synchronized (ITEMS) { int n = ITEMS.size(); for (int i = 0; i < n; i++) ITEMS.set(i, values.getAsString("name") + i); return n; }
    }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        Uri items = Uri.parse("content://hello.cpprovider.items/items");
        if (method.equals("grant")) getContext().grantUriPermission(arg, items, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION | android.content.Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        if (method.equals("revoke")) getContext().revokeUriPermission(items, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Bundle out = new Bundle();
        out.putInt("pid", Process.myPid());
        out.putInt("callerPid", Binder.getCallingPid());
        out.putString("echo", method + ":" + arg + ":" + (extras == null ? null : extras.getString("note")));
        return out;
    }
}
