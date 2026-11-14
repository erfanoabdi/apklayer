package apklayer.host;

import java.io.FileDescriptor;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/** libcore.io.BlockGuardOs: Robolectric's stand-in makes close() do nothing, so ParcelFileDescriptor.close and Os.close left every descriptor open; here it closes. */
@Implements(className = "libcore.io.BlockGuardOs", isInAndroidSdk = false)
public class ShadowHostRealBlockGuardOs {
    @Implementation protected void close(FileDescriptor fd) throws android.system.ErrnoException { libcore.io.Libcore.rawOs.close(fd); }
}
