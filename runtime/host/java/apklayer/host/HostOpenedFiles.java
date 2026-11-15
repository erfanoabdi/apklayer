package apklayer.host;

import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.util.List;

/**
 * The host files the person opened with this app (`apklayer open`, cli/open.cpp): the CLI copies one file into the app's cache, opened/&lt;token&gt;/data with its type
 * in opened/&lt;token&gt;/type, and starts the app with an ACTION_VIEW of content://apklayer.opened/&lt;token&gt;/&lt;name&gt; and FLAG_GRANT_READ_URI_PERMISSION. This
 * provider answers that Uri only (read only, the file and its OpenableColumns): the app never sees the host path or the rest of the folder.
 */
final class HostOpenedFiles extends ContentProvider {
    static final String AUTHORITY = "apklayer.opened";
    private File[] roots;  // the app's cache, and the instance's tmp where the sandbox keeps the copy (the app's own folder is out of the CLI's reach)

    static void register(Application app) {
        HostOpenedFiles provider = new HostOpenedFiles();
        ProviderInfo info = new ProviderInfo();
        info.name = HostOpenedFiles.class.getName(); info.packageName = app.getPackageName(); info.authority = AUTHORITY; info.exported = true;
        info.applicationInfo = app.getApplicationInfo();
        provider.attachInfo(app, info);
        provider.roots = new File[] {new File(app.getCacheDir(), "opened"), new File(System.getProperty("java.io.tmpdir", "/tmp"), "opened")};
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(AUTHORITY, provider);
    }

    /** The token's directory, null for a Uri that is not one of the opened files. */
    private File dir(Uri uri) {
        List<String> segments = uri.getPathSegments();
        if (segments.size() != 2 || !segments.get(0).matches("[0-9a-f]{8,32}")) return null;
        for (File root : roots) {
            File dir = new File(root, segments.get(0));
            if (new File(dir, "data").isFile()) return dir;
        }
        return null;
    }

    @Override public boolean onCreate() { return true; }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File dir = dir(uri);
        if (dir == null || !mode.equals("r")) throw new FileNotFoundException(uri + ": only read access to a file opened with this app");
        return ParcelFileDescriptor.open(new File(dir, "data"), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        File dir = dir(uri);
        if (dir == null) return null;
        String[] columns = projection != null ? projection : new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        Object[] row = new Object[columns.length];
        for (int i = 0; i < columns.length; i++)
            row[i] = OpenableColumns.DISPLAY_NAME.equals(columns[i]) ? uri.getLastPathSegment() : OpenableColumns.SIZE.equals(columns[i]) ? (Object) new File(dir, "data").length() : null;
        MatrixCursor cursor = new MatrixCursor(columns);
        cursor.addRow(row);
        return cursor;
    }

    @Override public String getType(Uri uri) {
        File dir = dir(uri);
        try { return dir == null ? null : Files.readString(new File(dir, "type").toPath()).trim(); }
        catch (java.io.IOException e) { return "application/octet-stream"; }
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException("read only"); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException("read only"); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { throw new UnsupportedOperationException("read only"); }
}
