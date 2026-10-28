package apklayer.host;

import java.io.File;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/** StatFs answers with the host file system's real sizes (Robolectric's registered-stats fake reports 0 bytes free: WhatsApp then shows "Storage is full"). */
@Implements(value = android.os.StatFs.class, isInAndroidSdk = false)
public class ShadowHostStatFs {
    private static final long BLOCK = 4096;
    private File path = new File("/");

    @Implementation
    protected void __constructor__(String path) { this.path = new File(path); }
    @Implementation
    protected void restat(String path) { this.path = new File(path); }

    private File existing() { File f = path; while (f != null && !f.exists()) f = f.getParentFile(); return f == null ? new File("/") : f; }

    @Implementation protected int getBlockSize() { return (int) BLOCK; }
    @Implementation protected long getBlockSizeLong() { return BLOCK; }
    @Implementation protected int getBlockCount() { return (int) getBlockCountLong(); }
    @Implementation protected long getBlockCountLong() { return existing().getTotalSpace() / BLOCK; }
    @Implementation protected int getFreeBlocks() { return (int) getFreeBlocksLong(); }
    @Implementation protected long getFreeBlocksLong() { return existing().getFreeSpace() / BLOCK; }
    @Implementation protected int getAvailableBlocks() { return (int) getAvailableBlocksLong(); }
    @Implementation protected long getAvailableBlocksLong() { return existing().getUsableSpace() / BLOCK; }
    @Implementation protected long getTotalBytes() { return existing().getTotalSpace(); }
    @Implementation protected long getFreeBytes() { return existing().getFreeSpace(); }
    @Implementation protected long getAvailableBytes() { return existing().getUsableSpace(); }
}
