// android.app.backup.FileBackupHelper -- BackupHelper that snapshots
// arbitrary files. Stub: no-op.
package android.app.backup;

import android.content.Context;
import android.os.ParcelFileDescriptor;

public class FileBackupHelper implements BackupHelper {
    public FileBackupHelper(Context context, String... files) {}

    @Override public void performBackup(ParcelFileDescriptor oldState,
                                        BackupDataOutput data,
                                        ParcelFileDescriptor newState) {}
    @Override public void restoreEntity(BackupDataInputStream data) {}
    @Override public void writeNewStateDescription(ParcelFileDescriptor newState) {}
}
