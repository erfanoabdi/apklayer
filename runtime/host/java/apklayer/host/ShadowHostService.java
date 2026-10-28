package apklayer.host;

import android.app.Service;
import android.content.ComponentName;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadows.ShadowService;

/** With the hub, stopSelf really stops the service (and the hub forgets it started) instead of only being recorded. */
@Implements(value = Service.class, isInAndroidSdk = false)
public class ShadowHostService extends ShadowService {
    @RealObject private Service service;

    private static boolean hub() { return HubClient.connected(); }
    private boolean stop(int startId) { return HubServices.stopSelf(new ComponentName(service, service.getClass().getName()), startId); }

    @Implementation @Override protected void stopSelf() { if (hub()) stop(-1); else super.stopSelf(); }
    @Implementation @Override protected void stopSelf(int id) { if (hub()) stop(id); else super.stopSelf(id); }
    @Implementation @Override protected boolean stopSelfResult(int id) { return hub() ? stop(id) : super.stopSelfResult(id); }
}
