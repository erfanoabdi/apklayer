// android.content.ContentProviderClient -- a stable handle to a single
// ContentProvider, returned by ContentResolver.acquireContentProviderClient.
//
// On real Android this wraps the IContentProvider Binder transport so a
// caller can issue several CRUD calls against one provider without
// re-resolving the authority each time. Mac.apk runs the provider in the
// same JVM, so this simply forwards to the live ContentProvider object
// the registry resolved. Every method mirrors the matching
// ContentResolver/ContentProvider signature so apps that hold a client
// (e.g. DocumentsContract consumers, SyncAdapters, anything that calls
// acquire* then reuses the handle) work unchanged.
//
// AOSP ref:
//   frameworks/base/core/java/android/content/ContentProviderClient.java
package android.content;

import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;

// Permission checks (v1.0.1657, MACAPK_URI_GRANT_ENFORCE): this client is part of the
// in-process transport, so each call runs ContentProvider.Transport's check first
// (macapkEnforce* / macapkTransportGetType, ContentProvider.java), exactly as
// ContentResolver does; call() and getStreamTypes check nothing, as in AOSP.
public class ContentProviderClient implements AutoCloseable {
    private final ContentResolver mResolver;
    private final ContentProvider mProvider;

    // Legacy no-arg ctor kept so any prior call site that referenced the
    // old empty stub still compiles. Such a client has no provider and
    // returns the empty results below.
    public ContentProviderClient() {
        this.mResolver = null;
        this.mProvider = null;
    }

    public ContentProviderClient(ContentResolver resolver, ContentProvider provider) {
        this.mResolver = resolver;
        this.mProvider = provider;
    }

    // ---- HOW PROVIDER EXCEPTIONS ARE HANDLED (narrowed 2026-08-23) --------
    //
    // AOSP's ContentProviderClient catches exactly ONE thing, in every method,
    // and RETHROWS it (frameworks/base/core/java/android/content/
    // ContentProviderClient.java, android16-release, fetched 2026-08-23 --
    // 16 identical sites, e.g. :203, :221, :241):
    //     } catch (DeadObjectException e) {
    //         if (!mStable) { mContentResolver.unstableProviderDied(mContentProvider); }
    //         throw e;
    //     } finally { afterRemote(); }
    // The catch exists only to mark the binder peer dead; the exception still
    // reaches the caller, and NOTHING else is caught at all. Every method
    // already declares `throws RemoteException`, so a caller holding a client
    // is expected to handle failure.
    //
    // These methods used to `catch (Throwable t)` and return null/0/url, which
    // silently converted a provider's SQLiteConstraintException,
    // IllegalArgumentException or NPE into "no rows" / "nothing changed". That
    // is a wrong answer where AOSP gives a diagnosable failure. DeadObjectException
    // cannot occur here (the provider is in this same JVM, reached by a direct
    // call, and ContentProvider declares no RemoteException), so the faithful
    // in-process form is no catch at all.
    //
    // See ContentResolver.java's matching block for the full rationale and for
    // the MediaStoreProvider routing that had to land BEFORE this narrowing.

    public Cursor query(Uri url, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder)
            throws android.os.RemoteException {
        if (mProvider == null) return null;
        mProvider.macapkEnforceRead(url);
        return mProvider.query(url, projection, selection, selectionArgs, sortOrder);
    }

    public Cursor query(Uri url, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder,
                        CancellationSignal cancellationSignal)
            throws android.os.RemoteException {
        if (mProvider == null) return null;
        mProvider.macapkEnforceRead(url);
        return mProvider.query(url, projection, selection, selectionArgs, sortOrder, cancellationSignal);
    }

    public Cursor query(Uri uri, String[] projection, Bundle queryArgs,
                        CancellationSignal cancellationSignal)
            throws android.os.RemoteException {
        if (mProvider == null) return null;
        mProvider.macapkEnforceRead(uri);
        return mProvider.query(uri, projection, queryArgs, cancellationSignal);
    }

    public String getType(Uri url) throws android.os.RemoteException {
        if (mProvider == null) return null;
        return mProvider.macapkTransportGetType(url);   // Transport.getType
    }

    public String[] getStreamTypes(Uri url, String mimeTypeFilter)
            throws android.os.RemoteException {
        if (mProvider == null) return null;
        return mProvider.getStreamTypes(url, mimeTypeFilter);
    }

    public Uri canonicalize(Uri url) throws android.os.RemoteException {
        if (mProvider == null) return null;
        mProvider.macapkEnforceRead(url);
        return mProvider.canonicalize(url);
    }

    public Uri uncanonicalize(Uri url) throws android.os.RemoteException {
        if (mProvider == null) return null;
        mProvider.macapkEnforceRead(url);
        return mProvider.uncanonicalize(url);
    }

    public Uri insert(Uri url, ContentValues initialValues)
            throws android.os.RemoteException {
        if (mProvider == null) return null;
        mProvider.macapkEnforceWrite(url);
        return mProvider.insert(url, initialValues);
    }

    public int bulkInsert(Uri url, ContentValues[] initialValues)
            throws android.os.RemoteException {
        if (mProvider == null) return 0;
        mProvider.macapkEnforceWrite(url);
        return mProvider.bulkInsert(url, initialValues);
    }

    public int delete(Uri url, String selection, String[] selectionArgs)
            throws android.os.RemoteException {
        if (mProvider == null) return 0;
        mProvider.macapkEnforceWrite(url);
        return mProvider.delete(url, selection, selectionArgs);
    }

    public int update(Uri url, ContentValues values, String selection,
                      String[] selectionArgs) throws android.os.RemoteException {
        if (mProvider == null) return 0;
        mProvider.macapkEnforceWrite(url);
        return mProvider.update(url, values, selection, selectionArgs);
    }

    // ---- The Bundle-carrying (API 30) overloads. AOSP passes `extras`
    // straight through to the provider's own extras-taking method, which is
    // where a caller's QUERY_ARG_*/sync-to-network hints are read. Delegating
    // to the provider (not to the older String-selection overload) is what
    // makes those hints reach it.
    /** AOSP ContentProviderClient.insert(Uri, ContentValues, Bundle). */
    public Uri insert(Uri url, ContentValues initialValues, Bundle extras)
            throws android.os.RemoteException {
        if (mProvider == null) return null;
        mProvider.macapkEnforceWrite(url);
        return mProvider.insert(url, initialValues, extras);
    }

    /** AOSP ContentProviderClient.delete(Uri, Bundle). */
    public int delete(Uri url, Bundle extras) throws android.os.RemoteException {
        if (mProvider == null) return 0;
        mProvider.macapkEnforceWrite(url);
        return mProvider.delete(url, extras);
    }

    /** AOSP ContentProviderClient.update(Uri, ContentValues, Bundle). */
    public int update(Uri url, ContentValues values, Bundle extras)
            throws android.os.RemoteException {
        if (mProvider == null) return 0;
        mProvider.macapkEnforceWrite(url);
        return mProvider.update(url, values, extras);
    }

    /** AOSP ContentProviderClient.refresh(Uri, Bundle, CancellationSignal) --
     *  "return true if the provider actually refreshed". The provider's own
     *  default is false. */
    public boolean refresh(Uri url, Bundle extras, CancellationSignal cancellationSignal)
            throws android.os.RemoteException {
        if (mProvider == null) return false;
        mProvider.macapkEnforceRead(url);
        return mProvider.refresh(url, extras, cancellationSignal);
    }

    /** AOSP openTypedAssetFile(Uri, String, Bundle, CancellationSignal). Note
     *  the NAME differs from the older openTypedAssetFileDescriptor pair below
     *  -- both exist upstream and both are public API, so both are here. The
     *  CancellationSignal is not consulted: the provider call is synchronous
     *  and in-process, so there is no window in which a cancel could land, and
     *  the same is already true of the sibling overloads above. */
    public AssetFileDescriptor openTypedAssetFile(Uri uri, String mimeTypeFilter, Bundle opts,
                                                  CancellationSignal signal)
            throws android.os.RemoteException, java.io.FileNotFoundException {
        if (mProvider == null) return null;
        mProvider.macapkEnforceFile(uri, "r");
        return mProvider.openTypedAssetFile(uri, mimeTypeFilter, opts, signal);
    }

    public ParcelFileDescriptor openFile(Uri url, String mode)
            throws android.os.RemoteException, java.io.FileNotFoundException {
        if (mProvider == null) return null;
        mProvider.macapkEnforceFile(url, mode);
        return mProvider.openFile(url, mode, null);   // Transport.openFile is 3-arg
    }

    public ParcelFileDescriptor openFile(Uri url, String mode, CancellationSignal signal)
            throws android.os.RemoteException, java.io.FileNotFoundException {
        return openFile(url, mode);
    }

    public AssetFileDescriptor openAssetFile(Uri url, String mode)
            throws android.os.RemoteException, java.io.FileNotFoundException {
        if (mProvider == null) return null;
        mProvider.macapkEnforceFile(url, mode);
        return mProvider.openAssetFile(url, mode, null);   // Transport.openAssetFile is 3-arg
    }

    public AssetFileDescriptor openAssetFile(Uri url, String mode, CancellationSignal signal)
            throws android.os.RemoteException, java.io.FileNotFoundException {
        return openAssetFile(url, mode);
    }

    public final AssetFileDescriptor openTypedAssetFileDescriptor(Uri uri,
            String mimeType, Bundle opts)
            throws android.os.RemoteException, java.io.FileNotFoundException {
        if (mProvider == null) return null;
        mProvider.macapkEnforceFile(uri, "r");
        return mProvider.openTypedAssetFile(uri, mimeType, opts, null);   // Transport's 4-arg call
    }

    public final AssetFileDescriptor openTypedAssetFileDescriptor(Uri uri,
            String mimeType, Bundle opts, CancellationSignal signal)
            throws android.os.RemoteException, java.io.FileNotFoundException {
        return openTypedAssetFileDescriptor(uri, mimeType, opts);
    }

    public Bundle call(String method, String arg, Bundle extras)
            throws android.os.RemoteException {
        if (mProvider == null) return null;
        return mProvider.call(method, arg, extras);
    }

    public Bundle call(String authority, String method, String arg, Bundle extras)
            throws android.os.RemoteException {
        if (mProvider == null) return null;
        return mProvider.call(authority, method, arg, extras);
    }

    /** AOSP returns the wrapped ContentProvider when the client is local
     *  (same process). Mac.apk is always local, so expose it -- some
     *  framework code (DocumentsProvider helpers) unwraps a local client. */
    public ContentProvider getLocalContentProvider() { return mProvider; }

    /** AOSP ContentProviderClient.applyBatch (ContentProviderClient.java:501-517):
     *  the transport ContentResolver.applyBatch actually routes through. On a
     *  device it marshals the operations to the provider's process; we host
     *  providers IN-PROCESS, so the transport collapses to a direct call --
     *  which is the same code path AOSP's transport terminates at
     *  (ContentProvider.applyBatch).
     *
     *  Added v1.0.940 because the ContentProviderOperation work had to reach
     *  around its absence and call getLocalContentProvider().applyBatch(...)
     *  from ContentResolver. That was behaviour-preserving but put the
     *  transport knowledge in the wrong class; this is its AOSP home. */
    public ContentProviderResult[] applyBatch(java.util.ArrayList<ContentProviderOperation> operations)
            throws android.os.RemoteException, OperationApplicationException {
        // AOSP's base applyBatch ignores the authority (one provider may
        // serve several), so null is the same answer as any name here.
        return applyBatch(null, operations);
    }

    public ContentProviderResult[] applyBatch(String authority,
            java.util.ArrayList<ContentProviderOperation> operations)
            throws android.os.RemoteException, OperationApplicationException {
        if (mProvider == null) throw new android.os.RemoteException("no provider");
        mProvider.macapkEnforceBatch(operations);   // Transport.applyBatch
        return mProvider.applyBatch(authority, operations);
    }

    public ContentResolver getContentResolver() { return mResolver; }

    /** AOSP ContentProviderClient.release(): drops the reference to the
     *  provider. In-process there is nothing to release; returns true to
     *  signal "was a valid, now-released handle" as AOSP does. */
    public boolean release() { return mProvider != null; }

    @Override public void close() { release(); }
}
