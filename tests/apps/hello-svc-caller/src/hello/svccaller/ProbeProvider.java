package hello.svccaller;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

/** Scenario "own": a multiprocess provider; Android creates it in every process of the app except an isolated one. */
public class ProbeProvider extends ContentProvider {
    static volatile boolean created;

    @Override public boolean onCreate() { created = true; return true; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
}
