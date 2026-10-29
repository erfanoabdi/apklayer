package apklayer.host;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowContentResolver;

/** notifyChange also reaches the observers of the other apps: the hub tells every other running app, which notifies its own observers (Robolectric's, in-process). */
@Implements(value = ContentResolver.class, isInAndroidSdk = false)
public class ShadowHostContentResolver extends ShadowContentResolver {
    /** The content service's per-uri cache (DocumentsUI reads its roots through it): no service here, so nothing is cached. */
    @Implementation protected android.os.Bundle getCache(Uri key) { return null; }
    @Implementation protected void putCache(Uri key, android.os.Bundle value) {}
    /** Sync status listeners belong to the content service, which does not exist here: a handle that does nothing (Tasks registers one at start). */
    @Implementation protected static Object addStatusChangeListener(int mask, android.content.SyncStatusObserver observer) { return new Object(); }
    @Implementation protected static void removeStatusChangeListener(Object handle) {}
    /** Android's ContentResolver.getType answers null when the provider throws (an Intent whose data is a provider's root uri asks it while it is sent: the calendar's PROVIDER_CHANGED). */
    @Implementation @Override protected String getType(Uri uri) {
        try { return super.getType(uri); }
        catch (RuntimeException failure) { System.out.println("[apklayer] getType(" + uri + ") failed: " + failure.getMessage()); return null; }
    }
    private static boolean hub() { return HubClient.connected(); }
    private static void tell(Uri uri, int flags) { if (uri != null && hub()) HubClient.current().notifyChange(uri, flags); }

    @Implementation @Override protected void notifyChange(Uri uri, ContentObserver observer, boolean syncToNetwork) { super.notifyChange(uri, observer, syncToNetwork); tell(uri, 0); }
    @Implementation @Override protected void notifyChange(Uri uri, ContentObserver observer) { super.notifyChange(uri, observer); tell(uri, 0); }
    @Implementation @Override protected void notifyChange(Uri uri, ContentObserver observer, int flags) { super.notifyChange(uri, observer, flags); tell(uri, flags); }

    /** From the hub: this app's own observers only (no echo back). */
    static void deliver(Context context, Uri uri, int flags) {
        org.robolectric.shadow.api.Shadow.<ShadowHostContentResolver>extract(context.getContentResolver()).local(uri, flags);
    }
    private void local(Uri uri, int flags) { super.notifyChange(uri, null, flags); }
}
