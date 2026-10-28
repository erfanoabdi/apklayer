package apklayer.host;

import android.app.DownloadManager;
import android.database.Cursor;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowDownloadManager;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

/** Queries go to the DownloadProvider (the installed one, through the hub) instead of Robolectric's in-memory list: DownloadStorageProvider lists the downloads from it. */
@Implements(value = DownloadManager.class, isInAndroidSdk = false)
public class ShadowHostDownloadManager extends ShadowDownloadManager {
    @RealObject private DownloadManager real;

    @Implementation
    protected Cursor query(DownloadManager.Query query) {
        return Shadow.directlyOn(real, DownloadManager.class, "query", ClassParameter.from(DownloadManager.Query.class, query));
    }
}
