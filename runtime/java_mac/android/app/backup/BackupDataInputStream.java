// android.app.backup.BackupDataInputStream -- read side of BackupHelper.
// Stub.
package android.app.backup;

import java.io.IOException;
import java.io.InputStream;

public class BackupDataInputStream extends InputStream {
    public String getKey()  { return ""; }
    public int    size()     { return 0; }
    @Override public int read() throws IOException { return -1; }
}
