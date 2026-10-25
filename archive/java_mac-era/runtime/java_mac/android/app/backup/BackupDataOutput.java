// android.app.backup.BackupDataOutput -- write side of the BackupHelper
// interface. Stub: no real backup transport exists in Mac.apk.
package android.app.backup;

import java.io.FileDescriptor;

public class BackupDataOutput {
    public BackupDataOutput() {}
    public int  writeEntityHeader(String key, int dataSize) { return 0; }
    public int  writeEntityData(byte[] data, int size)      { return 0; }
    public long getQuota() { return Long.MAX_VALUE; }
    public FileDescriptor getFileDescriptor() { return null; }

    /** AOSP getTransportFlags() (API 28): "Returns the flags with which the
     *  backup transport was created", a bitmask of
     *  BackupAgent.FLAG_CLIENT_SIDE_ENCRYPTION_ENABLED /
     *  FLAG_DEVICE_TO_DEVICE_TRANSFER. Mac.apk has no backup transport at all
     *  (see the class comment), so no transport flag is set -- 0, which is also
     *  AOSP's value for a plain cloud transport with neither feature. An agent
     *  branches on this to decide whether to include sensitive data, so
     *  claiming a flag we cannot honour would be the harmful answer. */
    public long getTransportFlags() { return 0L; }
}
