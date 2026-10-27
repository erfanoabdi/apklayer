package apklayer.host;

import android.content.AttributionSource;
import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.IContentProvider;
import android.content.OperationApplicationException;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.DeadObjectException;
import android.os.IBinder;
import android.os.ICancellationSignal;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

/**
 * A provider of another app, as this app's ContentResolver sees it: registered under the authority, every call goes to the real IContentProvider (the provider's own
 * ContentProvider.Transport in its own process, fetched through the hub, which starts that process when it is not running). The calls are the ones ContentProviderClient
 * makes; the class itself is not used because Robolectric replaces its methods with calls to an in-process provider.
 */
final class RemoteProvider extends ContentProvider {
    private interface Op<T> { T run(IContentProvider provider, AttributionSource source) throws RemoteException, FileNotFoundException, OperationApplicationException; }

    private final Context context;
    private final String authority;
    private final String readPermission, writePermission;
    private IContentProvider provider;

    RemoteProvider(Context context, String authority, String readPermission, String writePermission) {
        this.context = context; this.authority = authority; this.readPermission = readPermission; this.writePermission = writePermission;
        ReflectionHelpers.setField(ContentProvider.class, this, "mContext", context);
        ReflectionHelpers.setField(ContentProvider.class, this, "mCallingAttributionSource", new ThreadLocal<AttributionSource>());
        ReflectionHelpers.setField(ContentProvider.class, this, "mAuthority", authority);
        ReflectionHelpers.setField(ContentProvider.class, this, "mAuthorities", new String[] {authority});  // the Transport of the resolver's side validates uris against it
    }

    /**
     * Every app JVM runs as the same Linux uid, so the provider's own check ("the caller has my uid") always passes: the calling app's grants are checked here, as
     * ContentProvider.Transport would for another uid (through the hub: it knows permissions other apps declare).
     */
    private void enforce(String permission, boolean write, Uri uri) {
        if (permission == null || (uri != null && HubClient.current().uriGranted(uri, write ? 2 : 1)) || HubClient.current().checkPermission(android.os.Process.myPid(), permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        throw new SecurityException("Permission Denial: " + (write ? "writing " : "reading ") + authority + " requires " + permission + ", or grantUriPermission()");
    }

    private synchronized IContentProvider provider(boolean fresh) {
        if (provider != null && !fresh) return provider;
        IBinder binder = HubProtocol.call(HubClient.current().binder(), HubProtocol.DESCRIPTOR, HubProtocol.GET_PROVIDER, request -> request.writeString(authority), reply -> reply.readStrongBinder());
        if (binder == null) throw new IllegalArgumentException("Unknown provider " + authority);
        provider = android.content.ContentProviderNative.asInterface(binder);
        return provider;
    }

    /** One try, and one more with a fresh connection when the provider's process died in between (the hub starts it again). */
    private <T> T call(Op<T> operation) throws FileNotFoundException, OperationApplicationException {
        for (int attempt = 0; ; attempt++) {
            try { return operation.run(provider(attempt > 0), context.getAttributionSource()); }
            catch (DeadObjectException dead) { if (attempt > 0) throw new IllegalStateException("Provider " + authority + " died", dead); }
            catch (RemoteException failure) { throw new IllegalStateException("Provider " + authority + " failed", failure); }
        }
    }

    private <T> T plain(Op<T> operation) {
        try { return call(operation); }
        catch (FileNotFoundException | OperationApplicationException checked) { throw new IllegalStateException(checked); }
    }

    private static Bundle sql(String selection, String[] args, String sort) {
        return ReflectionHelpers.callStaticMethod(ContentResolver.class, "createSqlQueryBundle", ClassParameter.from(String.class, selection),
                ClassParameter.from(String[].class, args), ClassParameter.from(String.class, sort));
    }

    @Override public boolean onCreate() { return true; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return query(uri, projection, sql(selection, selectionArgs, sortOrder), null);
    }
    @Override public Cursor query(Uri uri, String[] projection, Bundle queryArgs, CancellationSignal signal) {
        enforce(readPermission, false, uri);
        return plain((p, source) -> {
            ICancellationSignal remote = null;
            if (signal != null) { signal.throwIfCanceled(); remote = p.createCancellationSignal(); signal.setRemote(remote); }
            return p.query(source, uri, projection, queryArgs, remote);
        });
    }
    @Override public String getType(Uri uri) { enforce(readPermission, false, uri); return plain((p, source) -> p.getType(source, uri)); }
    @Override public Uri insert(Uri uri, ContentValues values) { enforce(writePermission, true, uri); return plain((p, source) -> p.insert(source, uri, values, null)); }
    @Override public int bulkInsert(Uri uri, ContentValues[] values) { enforce(writePermission, true, uri); return plain((p, source) -> p.bulkInsert(source, uri, values)); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { enforce(writePermission, true, uri); return plain((p, source) -> p.delete(source, uri, sql(selection, selectionArgs, null))); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        enforce(writePermission, true, uri);
        return plain((p, source) -> p.update(source, uri, values, sql(selection, selectionArgs, null)));
    }
    @Override public Bundle call(String method, String arg, Bundle extras) { return call(authority, method, arg, extras); }
    @Override public Bundle call(String authority, String method, String arg, Bundle extras) { return plain((p, source) -> p.call(source, authority, method, arg, extras)); }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        enforce(mode.contains("w") ? writePermission : readPermission, mode.contains("w"), uri);
        try { return call((p, source) -> p.openFile(source, uri, mode, null)); } catch (OperationApplicationException impossible) { throw new IllegalStateException(impossible); }
    }
    @Override public AssetFileDescriptor openAssetFile(Uri uri, String mode) throws FileNotFoundException {
        enforce(mode.contains("w") ? writePermission : readPermission, mode.contains("w"), uri);
        try { return call((p, source) -> p.openAssetFile(source, uri, mode, null)); } catch (OperationApplicationException impossible) { throw new IllegalStateException(impossible); }
    }
    @Override public String[] getStreamTypes(Uri uri, String mimeTypeFilter) { return plain((p, source) -> p.getStreamTypes(source, uri, mimeTypeFilter)); }
    @Override public ContentProviderResult[] applyBatch(ArrayList<ContentProviderOperation> operations) throws OperationApplicationException {
        try { return call((p, source) -> p.applyBatch(source, authority, operations)); } catch (FileNotFoundException impossible) { throw new IllegalStateException(impossible); }
    }
    @Override public Uri canonicalize(Uri url) { return plain((p, source) -> p.canonicalize(source, url)); }
}
