package apklayer.host;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/**
 * Stands in for the MediaProvider ("media") until the tree's own is installed: the framework calls it to scan a file before handing it out
 * (FileSystemProvider.openFileForRead -> MediaStore.scanFile) and reads the answer without a null check: an empty answer means "not in the media store". Nothing is indexed.
 */
final class HostMediaStub extends ContentProvider {
    HostMediaStub() {  // the incoming-call check compares the uri authority with these
        org.robolectric.util.ReflectionHelpers.setField(ContentProvider.class, this, "mAuthority", "media");
        org.robolectric.util.ReflectionHelpers.setField(ContentProvider.class, this, "mAuthorities", new String[] {"media"});
        org.robolectric.util.ReflectionHelpers.setField(ContentProvider.class, this, "mCallingAttributionSource", new ThreadLocal<android.content.AttributionSource>());
    }
    @Override public boolean onCreate() { return true; }
    @Override public Bundle call(String authority, String method, String arg, Bundle extras) {
        return new Bundle();  // no media uri for the file: callers then open the file itself
    }
    @Override public Bundle call(String method, String arg, Bundle extras) { return call("media", method, arg, extras); }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return new android.database.MatrixCursor(projection != null ? projection : new String[0]); }  // nothing is indexed
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
