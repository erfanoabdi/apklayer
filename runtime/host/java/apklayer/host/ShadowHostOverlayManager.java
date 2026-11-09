package apklayer.host;

import android.content.om.OverlayInfo;
import android.content.om.OverlayManager;
import android.os.UserHandle;
import java.util.ArrayList;
import java.util.List;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/**
 * There is no OverlayManagerService and no runtime resource overlay installed: the service's answers on a device without overlays. Robolectric's
 * ShadowOverlayManager left getOverlayInfosForTarget to the null "overlay" proxy, whose null DocumentsUI's ThemeOverlayManager iterated (an NPE in
 * its AsyncTask at every start).
 */
@Implements(value = OverlayManager.class, isInAndroidSdk = false)
public class ShadowHostOverlayManager {
    @Implementation protected OverlayInfo getOverlayInfo(String packageName, UserHandle user) { return null; }
    @Implementation protected List<OverlayInfo> getOverlayInfosForTarget(String targetPackageName, UserHandle user) { return new ArrayList<>(); }
}
