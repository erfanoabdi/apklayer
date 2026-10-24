// android.app.backup.BackupDataInput -- low-level reader handed to
// BackupAgent.onRestore. Stub.
package android.app.backup;

public class BackupDataInput {
    public boolean readNextHeader() throws java.io.IOException { return false; }
    public String  getKey()      { return ""; }
    public int     getDataSize() { return 0; }
    public int     readEntityData(byte[] data, int offset, int size) { return 0; }
    public void    skipEntityData() {}
}
