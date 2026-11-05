package apklayer.host;

import android.app.IntentService;
import org.robolectric.annotation.Implements;

/**
 * An IntentService's shadow must be a ShadowHostService: Robolectric runs ShadowHostService's stopSelf on the object's own shadow,
 * and with Robolectric's ShadowIntentService (which adds no behaviour) that cast failed. Every IntentService calls stopSelf(startId)
 * after its intent, so the process died there (JW Library's DownloadService).
 */
@Implements(value = IntentService.class, isInAndroidSdk = false)
public class ShadowHostIntentService extends ShadowHostService {
}
