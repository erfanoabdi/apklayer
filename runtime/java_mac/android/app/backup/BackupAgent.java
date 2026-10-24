// android.app.backup.BackupAgent -- per-app backup logic. Cocos2d-x's
// BackupAgent class subclasses BackupAgentHelper which extends this.
package android.app.backup;

import android.content.Context;
import android.content.ContextWrapper;
import android.os.ParcelFileDescriptor;

public abstract class BackupAgent extends ContextWrapper {
    public static final int TYPE_EOF       = 0;
    public static final int TYPE_FILE      = 1;
    public static final int TYPE_DIRECTORY = 2;
    public static final int TYPE_SYMLINK   = 3;

    public BackupAgent() { super(null); }

    public void onCreate() {}
    public void onDestroy() {}
    public abstract void onBackup(ParcelFileDescriptor oldState,
                                  BackupDataOutput data,
                                  ParcelFileDescriptor newState) throws java.io.IOException;
    public abstract void onRestore(BackupDataInput data, int appVersionCode,
                                   ParcelFileDescriptor newState) throws java.io.IOException;
    public void onFullBackup(FullBackupDataOutput data) throws java.io.IOException {}
    public void onRestoreFinished() {}
    public void onQuotaExceeded(long backupDataBytes, long quotaBytes) {}

    public void onRestoreFile(android.os.ParcelFileDescriptor data, long size, java.io.File destination,
            int type, long mode, long mtime) throws java.io.IOException { }

    public final void fullBackupFile(java.io.File file, FullBackupDataOutput output) { }

    /** AOSP BackupAgent.FLAG_CLIENT_SIDE_ENCRYPTION_ENABLED /
     *  FLAG_DEVICE_TO_DEVICE_TRANSFER: the transport flags reported by
     *  BackupDataOutput.getTransportFlags(), which is 0 here (no transport --
     *  see that method). */
    public static final int FLAG_CLIENT_SIDE_ENCRYPTION_ENABLED = 1;
    public static final int FLAG_DEVICE_TO_DEVICE_TRANSFER = 2;
}
