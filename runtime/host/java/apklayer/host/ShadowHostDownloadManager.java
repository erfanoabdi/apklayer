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

    /** The requests go to the provider too: it schedules its download job, which fetches the file (Robolectric only lists the request). */
    @Implementation
    protected long enqueue(DownloadManager.Request request) {
        return Shadow.directlyOn(real, DownloadManager.class, "enqueue", ClassParameter.from(DownloadManager.Request.class, request));
    }

    @Implementation
    protected int remove(long... ids) {
        return Shadow.directlyOn(real, DownloadManager.class, "remove", ClassParameter.from(long[].class, ids));
    }

    @Implementation
    protected android.net.Uri getUriForDownloadedFile(long id) {
        return Shadow.directlyOn(real, DownloadManager.class, "getUriForDownloadedFile", ClassParameter.from(long.class, id));
    }

    @Implementation
    protected String getMimeTypeForDownloadedFile(long id) {
        return Shadow.directlyOn(real, DownloadManager.class, "getMimeTypeForDownloadedFile", ClassParameter.from(long.class, id));
    }

    @Implementation
    protected android.os.ParcelFileDescriptor openDownloadedFile(long id) throws java.io.FileNotFoundException {
        return Shadow.directlyOn(real, DownloadManager.class, "openDownloadedFile", ClassParameter.from(long.class, id));
    }
}
