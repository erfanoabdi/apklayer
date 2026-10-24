// android.app.backup.FullBackupDataOutput -- handed to onFullBackup.
// Stub.
package android.app.backup;

public class FullBackupDataOutput {
    // AOSP FullBackupDataOutput (android11-release) stores this from its
    // constructor: "private final int mTransportFlags;" set by both real
    // constructors (FullBackupDataOutput(long quota, int transportFlags) and
    // the ParcelFileDescriptor-taking overload). Nothing in Mac.apk
    // currently constructs a FullBackupDataOutput -- like the rest of this
    // package (see BackupDataOutput's identical no-transport stub note),
    // there is no real backup transport wiring, only the linkage surface --
    // so this constructor is the AOSP shape, ready for whenever a real
    // caller exists, defaulting to 0 ("no transport flags") until then.
    private final int mTransportFlags;

    public FullBackupDataOutput() { this(0); }
    public FullBackupDataOutput(int transportFlags) { this.mTransportFlags = transportFlags; }

    public long getQuota() { return Long.MAX_VALUE; }

    /** AOSP FullBackupDataOutput.getTransportFlags(): "Returns flags with
     *  additional information about the backup transport. For supported
     *  flags see BackupAgent." A stored-field getter, not a hardcoded
     *  constant like getQuota() above -- see the constructor note. */
    public int getTransportFlags() { return mTransportFlags; }
}
