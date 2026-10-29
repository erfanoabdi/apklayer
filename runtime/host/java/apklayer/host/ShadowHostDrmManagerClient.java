package apklayer.host;

import android.drm.DrmManagerClient;
import android.drm.DrmSupportInfo;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/** There is no DRM service: no scheme is supported (MediaProvider's scanner asks which DRM formats it may skip when it is created; the native answer is null there). */
@Implements(DrmManagerClient.class)
public class ShadowHostDrmManagerClient {
    @Implementation protected java.util.Collection<DrmSupportInfo> getAvailableDrmSupportInfo() { return java.util.Collections.emptyList(); }
    @Implementation protected boolean canHandle(String path, String mimeType) { return false; }
    @Implementation protected boolean canHandle(android.net.Uri uri, String mimeType) { return false; }
}
