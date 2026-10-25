// android.app.backup.BackupManager -- "tell the system my data
// changed" service. Cocos2d-x calls dataChanged() after writing user
// prefs. There's no backup transport in Mac.apk; calls are no-ops.
package android.app.backup;

import android.content.Context;

public class BackupManager {
    public BackupManager(Context context) {}
    public void dataChanged() {}
    public static void dataChanged(String packageName) {}
    public int requestRestore(RestoreObserver observer) { return 0; }

    /** AOSP getUserForAncestralSerialNumber(long ancestralSerialNumber) (API 29):
     *  "Returns a UserHandle for the user that has ancestral serial number
     *  ancestralSerialNumber", used during a device-to-device restore to map a
     *  profile on the OLD device to one on the new. AOSP returns null when no
     *  user matches -- and Mac.apk has no restore session, no ancestral device
     *  and exactly one user, so nothing can match. */
    public android.os.UserHandle getUserForAncestralSerialNumber(long ancestralSerialNumber) {
        return null;
    }
}
