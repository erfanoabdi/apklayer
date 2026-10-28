package apklayer.host;

import android.content.ContentProvider;
import android.content.ContentProviderClient;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.RemoteException;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowContentProviderClient;
import org.robolectric.util.ReflectionHelpers;

/** The Bundle form of query (what DocumentsUI calls): Robolectric only has the legacy one, which a DocumentsProvider refuses. */
@Implements(value = ContentProviderClient.class, isInAndroidSdk = false)
public class ShadowHostContentProviderClient extends ShadowContentProviderClient {
    @Implementation
    protected Cursor query(Uri url, String[] projection, Bundle queryArgs, CancellationSignal signal) throws RemoteException {
        return ReflectionHelpers.<ContentProvider>getField(this, "provider").query(url, projection, queryArgs, signal);
    }
}
