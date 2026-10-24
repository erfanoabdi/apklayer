// android.util.AtomicFile -- helper for atomically rewriting a single file,
// using a ".bak" backup so a crash mid-write leaves either the old or the new
// content (never a truncated file). AOSP API-17+; the contract mirrors
// frameworks/base/core/java/android/util/AtomicFile.java exactly so callers
// (duolingo's preference/state persistence; androidx, WorkManager, etc.) behave
// the same. It was a NoClassDefFoundError gap. Backed by plain java.io on the
// macOS filesystem (the android-root mirror); no Android-specific FS semantics.
package android.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class AtomicFile {
    private final File mBaseName;
    private final File mBackupName;

    public AtomicFile(File baseName) {
        mBaseName = baseName;
        mBackupName = new File(baseName.getPath() + ".bak");
    }
    // API-30 ctor carrying a metrics tag; Mac.apk has no metrics, so ignore it.
    public AtomicFile(File baseName, String commitTag) { this(baseName); }

    public File getBaseFile() { return mBaseName; }

    /** Delete the file (and any backup). */
    public void delete() {
        mBaseName.delete();
        mBackupName.delete();
    }

    /** Start a new write: if a base file exists, move it aside to the backup so
     *  a failed write can be rolled back. Returns a stream to write the new
     *  contents; finish with finishWrite() or failWrite(). */
    public FileOutputStream startWrite() throws IOException {
        // Roll any leftover backup forward first (recover from a prior crash).
        if (mBaseName.exists()) {
            if (!mBackupName.exists()) {
                if (!mBaseName.renameTo(mBackupName)) {
                    // Couldn't rename aside; fall through and overwrite in place.
                    android_log("startWrite: couldn't rename " + mBaseName + " to backup");
                }
            } else {
                mBaseName.delete();   // backup already holds the good copy
            }
        }
        try {
            return new FileOutputStream(mBaseName);
        } catch (FileNotFoundException e) {
            File parent = mBaseName.getParentFile();
            if (parent != null && parent.mkdirs()) {
                try { return new FileOutputStream(mBaseName); }
                catch (FileNotFoundException e2) { throw new IOException("Failed to create " + mBaseName, e2); }
            }
            throw new IOException("Failed to create " + mBaseName, e);
        }
    }

    /** Commit a successful write: flush+sync+close, then drop the backup. */
    public void finishWrite(FileOutputStream str) {
        if (str == null) return;
        try {
            str.flush();
            try { str.getFD().sync(); } catch (IOException ignored) {}
            str.close();
            mBackupName.delete();
        } catch (IOException e) {
            android_log("finishWrite failed: " + e);
        }
    }

    /** Abort a write: close the stream and restore the backup over the base. */
    public void failWrite(FileOutputStream str) {
        if (str == null) return;
        try {
            str.flush();
            try { str.getFD().sync(); } catch (IOException ignored) {}
            str.close();
            mBaseName.delete();
            mBackupName.renameTo(mBaseName);
        } catch (IOException e) {
            android_log("failWrite failed: " + e);
        }
    }

    /** Open the file for reading, first recovering a backup if a prior write
     *  failed (backup present means the base may be incomplete). */
    public FileInputStream openRead() throws FileNotFoundException {
        if (mBackupName.exists()) {
            mBaseName.delete();
            mBackupName.renameTo(mBaseName);
        }
        return new FileInputStream(mBaseName);
    }

    /** Read the entire file into a byte[] (recovering a backup first). */
    public byte[] readFully() throws IOException {
        try (FileInputStream stream = openRead()) {
            int pos = 0;
            int avail = stream.available();
            byte[] data = new byte[avail > 0 ? avail : 4096];
            for (;;) {
                int amt = stream.read(data, pos, data.length - pos);
                if (amt <= 0) return java.util.Arrays.copyOf(data, pos);
                pos += amt;
                if (pos >= data.length) {
                    byte[] grown = new byte[data.length * 2];
                    System.arraycopy(data, 0, grown, 0, data.length);
                    data = grown;
                }
            }
        }
    }

    /** API-30 helper. */
    public long getLastModifiedTime() { return mBaseName.lastModified(); }

    private static void android_log(String msg) {
        try { com.macapk.loader.NativeLog.w("AtomicFile", msg); } catch (Throwable ignored) {}
    }
}
