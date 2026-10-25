// android.app.backup.BackupAgentHelper -- BackupAgent that delegates
// to a list of BackupHelpers. Cocos2d-x's BackupAgent subclasses this.
package android.app.backup;

import android.os.ParcelFileDescriptor;

import java.util.LinkedHashMap;
import java.util.Map;

public class BackupAgentHelper extends BackupAgent {
    private final Map<String, BackupHelper> helpers = new LinkedHashMap<>();

    public BackupAgentHelper() {}

    public void addHelper(String keyPrefix, BackupHelper helper) {
        helpers.put(keyPrefix, helper);
    }

    @Override public void onBackup(ParcelFileDescriptor oldState,
                                   BackupDataOutput data,
                                   ParcelFileDescriptor newState) throws java.io.IOException {
        for (BackupHelper h : helpers.values()) {
            h.performBackup(oldState, data, newState);
            h.writeNewStateDescription(newState);
        }
    }

    @Override public void onRestore(BackupDataInput data, int appVersionCode,
                                    ParcelFileDescriptor newState) throws java.io.IOException {}
}
