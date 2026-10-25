package android.content;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;

// AOSP frameworks/base/core/java/android/content/ContentInterface.java (API 29+).
// The interface ContentProvider and ContentResolver share so that code can be
// written against either one. Quoting AOSP's own javadoc intent: it exposes "the
// core API of a content provider" without the caller caring which side it holds.
//
// WHY THIS FILE EXISTS. Our ContentProvider implemented 14 of these 16 methods
// already but never declared the type, so `instanceof ContentInterface` answered
// false and any cast threw -- the same type-identity defect that stopped
// Thunderbird booting through android.app.Application
// (docs/application-attachbasecontext-never-ran_2026-08-12.md).
//
// EVERY SIGNATURE HERE WAS CHECKED AGAINST android11-release BEFORE THE
// DECLARATION WENT ON ContentProvider, because getting one descriptor wrong is
// worse than the gap it fixes: an interface method our ContentProvider does not
// actually implement stays abstract, and then every app-defined provider
// subclass throws AbstractMethodError at a call site far from here instead of a
// clean, named NoSuchMethodError.
//
// `throws RemoteException` is kept to match AOSP's descriptors for anyone
// compiling against us. It is a checked exception, so an implementation is free
// to declare fewer -- which is why ContentProvider's existing methods satisfy
// these without being touched.
public interface ContentInterface {
    Cursor query(Uri uri, String[] projection, Bundle queryArgs,
            CancellationSignal cancellationSignal) throws android.os.RemoteException;

    String getType(Uri uri) throws android.os.RemoteException;

    String[] getStreamTypes(Uri uri, String mimeTypeFilter)
            throws android.os.RemoteException;

    Uri canonicalize(Uri uri) throws android.os.RemoteException;

    Uri uncanonicalize(Uri uri) throws android.os.RemoteException;

    boolean refresh(Uri uri, Bundle extras, CancellationSignal cancellationSignal)
            throws android.os.RemoteException;

    int checkUriPermission(Uri uri, int uid, int modeFlags)
            throws android.os.RemoteException;

    Uri insert(Uri uri, ContentValues initialValues, Bundle extras)
            throws android.os.RemoteException;

    int bulkInsert(Uri uri, ContentValues[] initialValues)
            throws android.os.RemoteException;

    int delete(Uri uri, Bundle extras) throws android.os.RemoteException;

    int update(Uri uri, ContentValues values, Bundle extras)
            throws android.os.RemoteException;

    android.os.ParcelFileDescriptor openFile(Uri uri, String mode,
            CancellationSignal signal)
            throws android.os.RemoteException, java.io.FileNotFoundException;

    android.content.res.AssetFileDescriptor openAssetFile(Uri uri, String mode,
            CancellationSignal signal)
            throws android.os.RemoteException, java.io.FileNotFoundException;

    android.content.res.AssetFileDescriptor openTypedAssetFile(Uri uri,
            String mimeTypeFilter, Bundle opts, CancellationSignal signal)
            throws android.os.RemoteException, java.io.FileNotFoundException;

    ContentProviderResult[] applyBatch(String authority,
            java.util.ArrayList<ContentProviderOperation> operations)
            throws android.os.RemoteException, OperationApplicationException;

    Bundle call(String authority, String method, String arg, Bundle extras)
            throws android.os.RemoteException;
}
