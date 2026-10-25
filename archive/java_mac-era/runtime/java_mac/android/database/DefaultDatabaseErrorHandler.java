// android.database.DefaultDatabaseErrorHandler -- the error handler
// SQLiteOpenHelper installs when an app does not supply its own. On
// AOSP it deletes (or, for an attached db, detaches) a database file
// that SQLite reported as corrupt so the next open can recreate it.
// Mac.apk mirrors that: close the handle and unlink the backing file
// (best-effort) so a subsequent open starts clean. In-memory databases
// have no file to delete and are left alone.
// Source: frameworks/base/core/java/android/database/DefaultDatabaseErrorHandler.java
package android.database;

import android.database.sqlite.SQLiteDatabase;

import java.io.File;

public class DefaultDatabaseErrorHandler implements DatabaseErrorHandler {

    @Override
    public void onCorruption(SQLiteDatabase dbObj) {
        String path = null;
        try {
            path = dbObj.getPath();
        } catch (Throwable ignored) {}

        // Close first so the file handle is released before we unlink.
        try {
            if (dbObj.isOpen()) dbObj.close();
        } catch (Throwable ignored) {}

        if (path == null || path.isEmpty() || ":memory:".equals(path)) {
            // Nothing on disk to remove.
            return;
        }
        try {
            File f = new File(path);
            if (f.exists()) {
                // Drop the main db file plus the -journal / -wal / -shm
                // sidecars SQLite may have left behind.
                deleteQuietly(f);
                deleteQuietly(new File(path + "-journal"));
                deleteQuietly(new File(path + "-wal"));
                deleteQuietly(new File(path + "-shm"));
            }
        } catch (Throwable ignored) {}
    }

    private static void deleteQuietly(File f) {
        try { if (f.exists()) f.delete(); } catch (Throwable ignored) {}
    }
}
