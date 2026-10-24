// android.app.backup.RestoreObserver -- callbacks during a restore op.
// Stub so BackupManager.requestRestore signatures resolve.
package android.app.backup;

public abstract class RestoreObserver {
    public void restoreStarting(int numPackages) {}
    public void onUpdate(int nowBeingRestored, String currentPackage) {}
    public void restoreFinished(int error) {}
}
