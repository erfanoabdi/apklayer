// android.provider.DocumentsProvider -- the ContentProvider subclass that exposes
// documents to the Storage Access Framework: an app's own provider, and Mac.apk's
// system one (com.macapk.loader.HostDocumentsProvider, standing in for
// ExternalStorageProvider).
//
// History. The class first landed (2026-07-29) so an app's subclass could LOAD at
// all (com.nesbox.tic's TICDocumentsProvider died with NoClassDefFoundError on its
// superclass), with a comment saying "Mac.apk has no SAF client, so nothing
// produces such a URI". The document picker (v1.0.1653) made that false: it hands
// out externalstorage document URIs, and OPEN_DOCUMENT_TREE (v1.0.1654) hands out
// tree URIs whose children an app lists and edits through this class.
//
// v1.0.1654, MACAPK_DOCSPROVIDER_DISPATCH (default ON): the AOSP URI / call()
// dispatch, mirrored from android14-release
// core/java/android/provider/DocumentsProvider.java:
//   attachInfo + registerAuthority, the eight-URI UriMatcher      :164-203
//   enforceTree / enforceTreeForExtraUris                          :221-240
//   query(uri, projection, queryArgs, signal) routing roots / recent /
//     search / document / children, the tree variants and manage mode :909-944
//   getSortClause                                                  :946-955
//   getType / getTypeAnonymous / canonicalize                      :962-1022
//   insert / delete / update refused                               :1050-1076
//   call -> callUnchecked: EJECT_ROOT, IS_CHILD_DOCUMENT, CREATE, RENAME,
//     DELETE, COPY, MOVE, REMOVE, FIND_DOCUMENT_PATH, GET_DOCUMENT_METADATA,
//     CREATE_WEB_LINK_INTENT, with the same Bundle keys and results  :1083-1305
//   revokeDocumentPermission                                       :1307-1312
//   openFile / openAssetFile / openTypedAssetFile -> openDocument /
//     openTypedDocument / openDocumentThumbnail                    :1319-1467
// plus the concrete defaults whose pre-change bodies differed from AOSP
// (removeDocument, queryRecentDocuments(4), querySearchDocuments(Bundle),
// getDocumentType, openTypedDocument, getDocumentStreamTypes, the
// findDocumentPath message). With the gate =0 every one of those is the
// pre-change body, kept verbatim in the `!DISPATCH` arm, and no URI or call is
// routed: ContentProvider's defaults answer, exactly as before.
//
// Deliberate divergences (labelled):
//   * NOT final. AOSP declares query / getType / openFile / openAssetFile /
//     openTypedAssetFile / insert / delete / update final. They stay overridable
//     here so the =0 arm is exactly the pre-change class (which did not declare
//     them at all) and so HostDocumentsProvider can keep its pre-change path
//     behind the same gate. No app can override them: they are final in the SDK
//     it compiles against.
//   * The two pre-O query forms ROUTE instead of throwing. AOSP throws
//     UnsupportedOperationException("Pre-Android-O query format not supported.")
//     because its Binder transport only ever calls the Bundle form. Mac.apk's
//     ContentResolver IS the transport and calls the 5-argument form directly, so
//     here it builds the queryArgs Bundle exactly as AOSP ContentResolver.query
//     does (createSqlQueryBundle, ContentResolver.java) and takes the Bundle form.
//   * Per-call permission enforcement (enforceReadPermissionInner /
//     enforceWritePermissionInner in callUnchecked, AOSP :1097-1305) runs since
//     v1.0.1657 (MACAPK_URI_GRANT_ENFORCE): a no-op for the app's OWN provider
//     (ContentProvider's `isSameApp(uid, mMyUid)`), the URI-grant check for
//     HostDocumentsProvider (another package). With =0 the only check is
//     HostDocumentsProvider's own per-pick one (a READ-only pick refuses writes).
//   * attachInfo's validity checks run, as on a device. HostDocumentsProvider's
//     ProviderInfo is synthesized by Boot, so it supplies its manifest's
//     MANAGE_DOCUMENTS read/write permission itself (ExternalStorageProvider's
//     AndroidManifest.xml:11-17). The one app provider in the corpus
//     (com.nesbox.tic TICDocumentsProvider) declares exported, grantUriPermissions
//     and MANAGE_DOCUMENTS, checked by parsing its manifest.
package android.provider;

import android.content.AttributionSource;
import android.content.ClipDescription;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.UriMatcher;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.os.ParcelableException;
import android.content.res.AssetFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Path;

import java.io.FileNotFoundException;
import java.util.LinkedList;
import java.util.Objects;

public abstract class DocumentsProvider extends ContentProvider {
    private static final String TAG = "DocumentsProvider";

    /** MACAPK_DOCSPROVIDER_DISPATCH, default ON. */
    static final boolean DISPATCH = !"0".equals(System.getenv("MACAPK_DOCSPROVIDER_DISPATCH"));

    /** @hide Mac.apk: whether the AOSP URI / call() dispatch is on. */
    public static boolean macapkDispatch() { return DISPATCH; }

    private static final int MATCH_ROOTS = 1;
    private static final int MATCH_ROOT = 2;
    private static final int MATCH_RECENT = 3;
    private static final int MATCH_SEARCH = 4;
    private static final int MATCH_DOCUMENT = 5;
    private static final int MATCH_CHILDREN = 6;
    private static final int MATCH_DOCUMENT_TREE = 7;
    private static final int MATCH_CHILDREN_TREE = 8;

    private String mAuthority;

    private UriMatcher mMatcher;

    /** AOSP :164-181. */
    @Override
    public void attachInfo(Context context, ProviderInfo info) {
        if (DISPATCH) {
            registerAuthority(info.authority);

            // Validity check our setup
            if (!info.exported) {
                throw new SecurityException("Provider must be exported");
            }
            if (!info.grantUriPermissions) {
                throw new SecurityException("Provider must grantUriPermissions");
            }
            if (!android.Manifest.permission.MANAGE_DOCUMENTS.equals(info.readPermission)
                    || !android.Manifest.permission.MANAGE_DOCUMENTS.equals(info.writePermission)) {
                throw new SecurityException("Provider must be protected by MANAGE_DOCUMENTS");
            }
        }
        super.attachInfo(context, info);
    }

    /** AOSP :191-203. */
    private void registerAuthority(String authority) {
        mAuthority = authority;

        mMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        mMatcher.addURI(mAuthority, "root", MATCH_ROOTS);
        mMatcher.addURI(mAuthority, "root/*", MATCH_ROOT);
        mMatcher.addURI(mAuthority, "root/*/recent", MATCH_RECENT);
        mMatcher.addURI(mAuthority, "root/*/search", MATCH_SEARCH);
        mMatcher.addURI(mAuthority, "document/*", MATCH_DOCUMENT);
        mMatcher.addURI(mAuthority, "document/*/children", MATCH_CHILDREN);
        mMatcher.addURI(mAuthority, "tree/*/document/*", MATCH_DOCUMENT_TREE);
        mMatcher.addURI(mAuthority, "tree/*/document/*/children", MATCH_CHILDREN_TREE);
    }

    private int match(Uri uri) {
        return mMatcher != null ? mMatcher.match(uri) : UriMatcher.NO_MATCH;
    }

    public boolean isChildDocument(String parentDocumentId, String documentId) {
        return false;
    }

    /** AOSP :221-225. */
    private void enforceTreeForExtraUris(Bundle extras) {
        enforceTree(extras.getParcelable(DocumentsContract.EXTRA_URI, Uri.class));
        enforceTree(extras.getParcelable(DocumentsContract.EXTRA_PARENT_URI, Uri.class));
        enforceTree(extras.getParcelable(DocumentsContract.EXTRA_TARGET_URI, Uri.class));
    }

    /** AOSP :228-240: a tree URI may only name the tree's root or a descendant
     *  of it, as the provider's isChildDocument decides. */
    private void enforceTree(Uri documentUri) {
        if (documentUri != null && DocumentsContract.isTreeUri(documentUri)) {
            final String parent = DocumentsContract.getTreeDocumentId(documentUri);
            final String child = DocumentsContract.getDocumentId(documentUri);
            if (Objects.equals(parent, child)) {
                return;
            }
            if (!isChildDocument(parent, child)) {
                throw new SecurityException(
                        "Document " + child + " is not a descendant of " + parent);
            }
        }
    }

    private Uri validateIncomingNullableUri(Uri uri) {
        return uri == null ? null : validateIncomingUri(uri);
    }

    /** AOSP ContentProvider.validateIncomingUri (ContentProvider.java:2727-2769),
     *  which our ContentProvider does not carry: the authority must be one this
     *  provider serves (SecurityException naming both), and "//" runs in the path
     *  are collapsed. One user, so no user-id check. */
    private Uri validateIncomingUri(Uri uri) throws SecurityException {
        String auth = uri.getAuthority();
        String bare = auth;
        if (bare != null) {
            int at = bare.lastIndexOf('@');
            if (at >= 0) bare = bare.substring(at + 1);
        }
        boolean ours = false;
        if (mAuthority != null && bare != null) {
            for (String a : mAuthority.split(";")) {
                if (bare.equals(a.trim())) { ours = true; break; }
            }
        }
        if (!ours) {
            throw new SecurityException("The authority " + auth + " does not match the one of the "
                    + "contentProvider: " + mAuthority);
        }
        final String encodedPath = uri.getEncodedPath();
        if (encodedPath != null && encodedPath.indexOf("//") != -1) {
            return uri.buildUpon().encodedPath(encodedPath.replaceAll("//+", "/")).build();
        }
        return uri;
    }

    public String createDocument(String parentDocumentId, String mimeType, String displayName)
            throws FileNotFoundException {
        throw new UnsupportedOperationException("Create not supported");
    }

    public String renameDocument(String documentId, String displayName)
            throws FileNotFoundException {
        throw new UnsupportedOperationException("Rename not supported");
    }

    public void deleteDocument(String documentId) throws FileNotFoundException {
        throw new UnsupportedOperationException("Delete not supported");
    }

    public String copyDocument(String sourceDocumentId, String targetParentDocumentId)
            throws FileNotFoundException {
        throw new UnsupportedOperationException("Copy not supported");
    }

    public String moveDocument(String sourceDocumentId, String sourceParentDocumentId,
                               String targetParentDocumentId) throws FileNotFoundException {
        throw new UnsupportedOperationException("Move not supported");
    }

    /** AOSP :374-378 throws "Remove not supported". The pre-change body deleted
     *  the document instead; that stays as the =0 arm. */
    public void removeDocument(String documentId, String parentDocumentId)
            throws FileNotFoundException {
        if (DISPATCH) throw new UnsupportedOperationException("Remove not supported");
        deleteDocument(documentId);
    }

    /** AOSP :401-405 (the message ends with a period there). */
    public DocumentsContract.Path findDocumentPath(String parentDocumentId, String childDocumentId)
            throws FileNotFoundException {
        throw new UnsupportedOperationException(DISPATCH
                ? "findDocumentPath not supported." : "findDocumentPath not supported");
    }

    public IntentSender createWebLinkIntent(String documentId, Bundle options)
            throws FileNotFoundException {
        throw new UnsupportedOperationException("createWebLink is not supported.");
    }

    public abstract Cursor queryRoots(String[] projection) throws FileNotFoundException;

    public Cursor queryRecentDocuments(String rootId, String[] projection)
            throws FileNotFoundException {
        throw new UnsupportedOperationException("Recent not supported");
    }

    /** AOSP :504-515: delegates to the 2-argument form and marks no query
     *  argument as honoured. */
    public Cursor queryRecentDocuments(String rootId, String[] projection, Bundle queryArgs,
                                       CancellationSignal signal) throws FileNotFoundException {
        if (!DISPATCH) return queryRecentDocuments(rootId, projection);
        if (rootId == null) throw new NullPointerException("rootId can not be null");
        Cursor c = queryRecentDocuments(rootId, projection);
        Bundle extras = new Bundle();
        c.setExtras(extras);
        extras.putStringArray(ContentResolver.EXTRA_HONORED_ARGS, new String[0]);
        return c;
    }

    public abstract Cursor queryDocument(String documentId, String[] projection)
            throws FileNotFoundException;

    public abstract Cursor queryChildDocuments(String parentDocumentId, String[] projection,
                                               String sortOrder) throws FileNotFoundException;

    public Cursor queryChildDocuments(String parentDocumentId, String[] projection,
                                       Bundle queryArgs) throws FileNotFoundException {
        return queryChildDocuments(parentDocumentId, projection, getSortClause(queryArgs));
    }

    /** @hide AOSP :624-629; DocumentsUI's manage mode. */
    public Cursor queryChildDocumentsForManage(String parentDocumentId, String[] projection,
                                               String sortOrder) throws FileNotFoundException {
        throw new UnsupportedOperationException("Manage not supported");
    }

    public Cursor querySearchDocuments(String rootId, String query, String[] projection)
            throws FileNotFoundException {
        throw new UnsupportedOperationException("Search not supported");
    }

    /** AOSP :716-722: the API-29 form delegates to the 3-argument one with the
     *  display-name query argument. The pre-change body threw. */
    public Cursor querySearchDocuments(String rootId, String[] projection, Bundle queryArgs)
            throws FileNotFoundException {
        if (!DISPATCH) throw new UnsupportedOperationException("Search not supported");
        if (rootId == null) throw new NullPointerException("rootId can not be null");
        if (queryArgs == null) throw new NullPointerException("queryArgs can not be null");
        return querySearchDocuments(rootId, DocumentsContract.getSearchDocumentsQuery(queryArgs),
                projection);
    }

    public void ejectRoot(String rootId) {
        throw new UnsupportedOperationException("Eject not supported");
    }

    public Bundle getDocumentMetadata(String documentId) throws FileNotFoundException {
        throw new UnsupportedOperationException("Metadata not supported");
    }

    /** AOSP :772-783 reads COLUMN_MIME_TYPE out of queryDocument's cursor (and
     *  throws on a null cursor or a missing column). The =0 arm is the pre-change
     *  null-tolerant read. */
    public String getDocumentType(String documentId) throws FileNotFoundException {
        final Cursor cursor = queryDocument(documentId, null);
        if (DISPATCH) {
            try {
                if (cursor.moveToFirst()) {
                    return cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE));
                } else {
                    return null;
                }
            } finally {
                closeQuietly(cursor);
            }
        }
        try {
            if (cursor != null && cursor.moveToFirst()) {
                int col = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE);
                return col >= 0 ? cursor.getString(col) : null;
            }
            return null;
        } finally {
            closeQuietly(cursor);
        }
    }

    private static void closeQuietly(Cursor c) {
        if (c != null) {
            try { c.close(); } catch (Throwable ignored) {}
        }
    }

    public abstract ParcelFileDescriptor openDocument(String documentId, String mode,
                                                       CancellationSignal signal)
            throws FileNotFoundException;

    public AssetFileDescriptor openDocumentThumbnail(String documentId, Point sizeHint,
                                                      CancellationSignal signal)
            throws FileNotFoundException {
        throw new UnsupportedOperationException("Thumbnails not supported");
    }

    /** AOSP :868-872 throws FileNotFoundException. The =0 arm is the pre-change
     *  UnsupportedOperationException. */
    public AssetFileDescriptor openTypedDocument(String documentId, String mimeTypeFilter,
                                                  Bundle opts, CancellationSignal signal)
            throws FileNotFoundException {
        if (DISPATCH) throw new FileNotFoundException("The requested MIME type is not supported.");
        throw new UnsupportedOperationException("Typed documents not supported");
    }

    // ---- ContentProvider plumbing: the URI dispatch -----------------------

    /** AOSP throws here (see the header); Mac.apk's ContentResolver calls this
     *  form, so it is routed to the Bundle form the way AOSP ContentResolver.query
     *  builds the Bundle. */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        if (!DISPATCH) return super.query(uri, projection, selection, selectionArgs, sortOrder);
        return query(uri, projection, createSqlQueryBundle(selection, selectionArgs, sortOrder), null);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder,
                        CancellationSignal cancellationSignal) {
        if (!DISPATCH) {
            return super.query(uri, projection, selection, selectionArgs, sortOrder,
                    cancellationSignal);
        }
        return query(uri, projection, createSqlQueryBundle(selection, selectionArgs, sortOrder),
                cancellationSignal);
    }

    /** AOSP :909-944. */
    @Override
    public Cursor query(Uri uri, String[] projection, Bundle queryArgs,
                        CancellationSignal cancellationSignal) {
        if (!DISPATCH) return super.query(uri, projection, queryArgs, cancellationSignal);
        try {
            switch (match(uri)) {
                case MATCH_ROOTS:
                    return queryRoots(projection);
                case MATCH_RECENT:
                    return queryRecentDocuments(
                            DocumentsContract.getRootId(uri), projection, queryArgs,
                            cancellationSignal);
                case MATCH_SEARCH:
                    return querySearchDocuments(DocumentsContract.getRootId(uri), projection,
                            queryArgs);
                case MATCH_DOCUMENT:
                case MATCH_DOCUMENT_TREE:
                    enforceTree(uri);
                    return queryDocument(DocumentsContract.getDocumentId(uri), projection);
                case MATCH_CHILDREN:
                case MATCH_CHILDREN_TREE:
                    enforceTree(uri);
                    if (DocumentsContract.isManageMode(uri)) {
                        return queryChildDocumentsForManage(
                                DocumentsContract.getDocumentId(uri), projection,
                                getSortClause(queryArgs));
                    } else {
                        return queryChildDocuments(DocumentsContract.getDocumentId(uri),
                                projection, queryArgs);
                    }
                default:
                    throw new UnsupportedOperationException("Unsupported Uri " + uri);
            }
        } catch (FileNotFoundException e) {
            android.util.Log.w(TAG, "Failed during query", e);
            return null;
        }
    }

    /** AOSP ContentResolver.createSqlQueryBundle(String, String[], String). */
    private static Bundle createSqlQueryBundle(String selection, String[] selectionArgs,
                                               String sortOrder) {
        if (selection == null && selectionArgs == null && sortOrder == null) {
            return null;
        }
        Bundle queryArgs = new Bundle();
        if (selection != null) {
            queryArgs.putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection);
        }
        if (selectionArgs != null) {
            queryArgs.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs);
        }
        if (sortOrder != null) {
            queryArgs.putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder);
        }
        return queryArgs;
    }

    /** AOSP :946-955: the SQL sort order, else one built from the sort columns
     *  (ContentResolver.createSqlSortClause). The =0 arm reads only the former. */
    private static String getSortClause(Bundle queryArgs) {
        if (!DISPATCH) {
            if (queryArgs == null) return null;
            Object raw = queryArgs.get(ContentResolver.QUERY_ARG_SQL_SORT_ORDER);
            return raw instanceof String ? (String) raw : null;
        }
        queryArgs = queryArgs != null ? queryArgs : Bundle.EMPTY;
        String sortClause = queryArgs.getString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER);
        if (sortClause == null && queryArgs.containsKey(ContentResolver.QUERY_ARG_SORT_COLUMNS)) {
            sortClause = createSqlSortClause(queryArgs);
        }
        return sortClause;
    }

    /** AOSP ContentResolver.createSqlSortClause(Bundle). */
    private static String createSqlSortClause(Bundle queryArgs) {
        String[] columns = queryArgs.getStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS);
        if (columns == null || columns.length == 0) {
            throw new IllegalArgumentException("Can't create sort clause without columns.");
        }
        String query = String.join(", ", columns);
        int collation = queryArgs.getInt(
                ContentResolver.QUERY_ARG_SORT_COLLATION, java.text.Collator.IDENTICAL);
        if (collation == java.text.Collator.PRIMARY || collation == java.text.Collator.SECONDARY) {
            query += " COLLATE NOCASE";
        }
        int sortDir = queryArgs.getInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, Integer.MIN_VALUE);
        if (sortDir != Integer.MIN_VALUE) {
            switch (sortDir) {
                case ContentResolver.QUERY_SORT_DIRECTION_ASCENDING:
                    query += " ASC";
                    break;
                case ContentResolver.QUERY_SORT_DIRECTION_DESCENDING:
                    query += " DESC";
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported sort direction value."
                            + " See ContentResolver documentation for details.");
            }
        }
        return query;
    }

    /** AOSP :962-980. */
    @Override
    public String getType(Uri uri) {
        if (!DISPATCH) return null;
        try {
            switch (match(uri)) {
                case MATCH_ROOT:
                    return DocumentsContract.Root.MIME_TYPE_ITEM;
                case MATCH_DOCUMENT:
                case MATCH_DOCUMENT_TREE:
                    enforceTree(uri);
                    return getDocumentType(DocumentsContract.getDocumentId(uri));
                default:
                    return null;
            }
        } catch (FileNotFoundException e) {
            android.util.Log.w(TAG, "Failed during getType", e);
            return null;
        }
    }

    /**
     * AOSP :985-992: the type answerable WITHOUT a permission grant -- only a
     * single-root URI ("root/*", MATCH_ROOT). The =0 arm is the pre-change check,
     * which tested the ROOTS shape (one segment) instead.
     */
    public final String getTypeAnonymous(Uri uri) {
        if (DISPATCH) {
            switch (match(uri)) {
                case MATCH_ROOT:
                    return DocumentsContract.Root.MIME_TYPE_ITEM;
                default:
                    return null;
            }
        }
        if (uri != null && DocumentsContract.isRootsUri(getContext(), uri)) {
            return DocumentsContract.Root.MIME_TYPE_ITEM;
        }
        return null;
    }

    /** AOSP :1007-1022: a tree document URI canonicalizes to the plain document
     *  URI, with the caller's modes extended to it (macapkGrantToCaller). */
    @Override
    public Uri canonicalize(Uri uri) {
        if (!DISPATCH) return super.canonicalize(uri);
        final Context context = getContext();
        switch (match(uri)) {
            case MATCH_DOCUMENT_TREE:
                enforceTree(uri);
                final Uri narrowUri = DocumentsContract.buildDocumentUri(uri.getAuthority(),
                        DocumentsContract.getDocumentId(uri));
                if (context != null) {
                    final int modeFlags = getCallingOrSelfUriPermissionModeFlags(context, uri);
                    context.grantUriPermission(getCallingPackage(), narrowUri, modeFlags);
                }
                macapkGrantToCaller(uri, narrowUri);
                return narrowUri;
        }
        return null;
    }

    /** The effect of AOSP's `context.grantUriPermission(getCallingPackage(), to,
     *  getCallingOrSelfUriPermissionModeFlags(context, from))` when this provider
     *  stands in for ANOTHER package (HostDocumentsProvider): UriGrantsManagerService
     *  records the grant for the calling app (ActivityManagerService
     *  .grantUriPermission :6401-6431). Mac.apk's Context.grantUriPermission is the
     *  APP's (a no-op, one app), so the record is made here. For the app's own
     *  provider there is nothing to record: the app already holds its own URIs.
     *  MACAPK_URI_GRANT_ENFORCE=0: nothing. */
    private void macapkGrantToCaller(Uri from, Uri to) {
        if (!android.content.MacApkUriGrants.ENFORCE || !macapkIsAnotherPackage()) return;
        final int modeFlags = android.content.MacApkUriGrants.callingModeFlags(from);
        if ((modeFlags & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION)) == 0) return;
        android.content.MacApkUriGrants.grantFromProvider(to, modeFlags);
    }

    /** AOSP :1025-1044. */
    private static int getCallingOrSelfUriPermissionModeFlags(Context context, Uri uri) {
        int modeFlags = 0;
        if (context.checkCallingOrSelfUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                == PackageManager.PERMISSION_GRANTED) {
            modeFlags |= Intent.FLAG_GRANT_READ_URI_PERMISSION;
        }
        if (context.checkCallingOrSelfUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                == PackageManager.PERMISSION_GRANTED) {
            modeFlags |= Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        }
        if (context.checkCallingOrSelfUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                == PackageManager.PERMISSION_GRANTED) {
            modeFlags |= Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION;
        }
        return modeFlags;
    }

    /** AOSP :1050-1054. */
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        if (!DISPATCH) return super.insert(uri, values);
        throw new UnsupportedOperationException("Insert not supported");
    }

    /** AOSP :1061-1065. */
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        if (!DISPATCH) return super.delete(uri, selection, selectionArgs);
        throw new UnsupportedOperationException("Delete not supported");
    }

    /** AOSP :1070-1076. */
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        if (!DISPATCH) return super.update(uri, values, selection, selectionArgs);
        throw new UnsupportedOperationException("Update not supported");
    }

    /** AOSP :1083-1095: platform ("android:") methods are dispatched below; a
     *  FileNotFoundException crosses as ParcelableException, which
     *  DocumentsContract unwraps again. */
    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (!DISPATCH || !method.startsWith("android:")) {
            return super.call(method, arg, extras);
        }
        try {
            return callUnchecked(method, arg, extras);
        } catch (FileNotFoundException e) {
            throw new ParcelableException(e);
        }
    }

    /** {@hide} Review issue 3 (2026-09-28): every enforce*PermissionInner call
     *  below is the literal AOSP shape, passing getCallingAttributionSource()
     *  as an argument -- evaluated before the call, so unconditionally, even
     *  though the callee's own ENFORCE/same-app fast path (ContentProvider
     *  .enforceReadPermissionInner) would return at once. On a device that
     *  argument is a cheap Binder-populated ThreadLocal read; here
     *  AttributionSource.myAttributionSource() does real work on first touch
     *  per thread. This computes it only when enforcement will actually run,
     *  matching macapkEnforceRead/Write's own guard (ContentProvider.java) --
     *  the parameter is otherwise unused by either enforce*Inner body. */
    private AttributionSource macapkAttrSourceIfEnforcing() {
        return (android.content.MacApkUriGrants.ENFORCE && macapkIsAnotherPackage())
                ? getCallingAttributionSource() : null;
    }

    /** AOSP :1097-1305, the read / write permission enforcement aside (header). */
    private Bundle callUnchecked(String method, String arg, Bundle extras)
            throws FileNotFoundException {

        final Context context = getContext();
        final Bundle out = new Bundle();

        // If the URI is a tree URI performs some validation.
        enforceTreeForExtraUris(extras);

        final Uri extraUri = validateIncomingNullableUri(
                extras.getParcelable(DocumentsContract.EXTRA_URI, Uri.class));
        final Uri extraTargetUri = validateIncomingNullableUri(
                extras.getParcelable(DocumentsContract.EXTRA_TARGET_URI, Uri.class));
        final Uri extraParentUri = validateIncomingNullableUri(
                extras.getParcelable(DocumentsContract.EXTRA_PARENT_URI, Uri.class));

        if (DocumentsContract.METHOD_EJECT_ROOT.equals(method)) {
            final Uri rootUri = extraUri;
            enforceWritePermissionInner(rootUri, macapkAttrSourceIfEnforcing());
            final String rootId = DocumentsContract.getRootId(rootUri);
            ejectRoot(rootId);
            return out;
        }

        final Uri documentUri = extraUri;
        final String authority = documentUri.getAuthority();
        final String documentId = DocumentsContract.getDocumentId(documentUri);

        if (!mAuthority.equals(authority)) {
            throw new SecurityException(
                    "Requested authority " + authority + " doesn't match provider " + mAuthority);
        }

        if (DocumentsContract.METHOD_IS_CHILD_DOCUMENT.equals(method)) {
            enforceReadPermissionInner(documentUri, macapkAttrSourceIfEnforcing());
            final Uri childUri = extraTargetUri;
            final String childAuthority = childUri.getAuthority();
            final String childId = DocumentsContract.getDocumentId(childUri);

            out.putBoolean(
                    DocumentsContract.EXTRA_RESULT,
                    mAuthority.equals(childAuthority)
                            && isChildDocument(documentId, childId));

        } else if (DocumentsContract.METHOD_CREATE_DOCUMENT.equals(method)) {
            enforceWritePermissionInner(documentUri, macapkAttrSourceIfEnforcing());
            final String mimeType = extras.getString(Document.COLUMN_MIME_TYPE);
            final String displayName = extras.getString(Document.COLUMN_DISPLAY_NAME);
            final String newDocumentId = createDocument(documentId, mimeType, displayName);

            // No need to issue new grants here, since caller either has
            // manage permission or a prefix grant. We might generate a
            // tree style URI if that's how they called us.
            final Uri newDocumentUri = DocumentsContract.buildDocumentUriMaybeUsingTree(
                    documentUri, newDocumentId);
            out.putParcelable(DocumentsContract.EXTRA_URI, newDocumentUri);

        } else if (DocumentsContract.METHOD_CREATE_WEB_LINK_INTENT.equals(method)) {
            enforceWritePermissionInner(documentUri, macapkAttrSourceIfEnforcing());
            final Bundle options = extras.getBundle(DocumentsContract.EXTRA_OPTIONS);
            final IntentSender intentSender = createWebLinkIntent(documentId, options);

            out.putParcelable(DocumentsContract.EXTRA_RESULT, intentSender);

        } else if (DocumentsContract.METHOD_RENAME_DOCUMENT.equals(method)) {
            enforceWritePermissionInner(documentUri, macapkAttrSourceIfEnforcing());
            final String displayName = extras.getString(Document.COLUMN_DISPLAY_NAME);
            final String newDocumentId = renameDocument(documentId, displayName);

            if (newDocumentId != null) {
                final Uri newDocumentUri = DocumentsContract.buildDocumentUriMaybeUsingTree(
                        documentUri, newDocumentId);

                // If caller came in with a narrow grant, issue them a
                // narrow grant for the newly renamed document.
                if (!DocumentsContract.isTreeUri(newDocumentUri) && context != null) {
                    final int modeFlags = getCallingOrSelfUriPermissionModeFlags(context,
                            documentUri);
                    context.grantUriPermission(getCallingPackage(), newDocumentUri, modeFlags);
                }
                if (!DocumentsContract.isTreeUri(newDocumentUri)) {
                    macapkGrantToCaller(documentUri, newDocumentUri);
                }

                out.putParcelable(DocumentsContract.EXTRA_URI, newDocumentUri);

                // Original document no longer exists, clean up any grants.
                revokeDocumentPermission(documentId);
            }

        } else if (DocumentsContract.METHOD_DELETE_DOCUMENT.equals(method)) {
            enforceWritePermissionInner(documentUri, macapkAttrSourceIfEnforcing());
            deleteDocument(documentId);

            // Document no longer exists, clean up any grants.
            revokeDocumentPermission(documentId);

        } else if (DocumentsContract.METHOD_COPY_DOCUMENT.equals(method)) {
            final Uri targetUri = extraTargetUri;
            final String targetId = DocumentsContract.getDocumentId(targetUri);

            enforceReadPermissionInner(documentUri, macapkAttrSourceIfEnforcing());
            enforceWritePermissionInner(targetUri, macapkAttrSourceIfEnforcing());

            final String newDocumentId = copyDocument(documentId, targetId);

            if (newDocumentId != null) {
                final Uri newDocumentUri = DocumentsContract.buildDocumentUriMaybeUsingTree(
                        documentUri, newDocumentId);

                if (!DocumentsContract.isTreeUri(newDocumentUri) && context != null) {
                    final int modeFlags = getCallingOrSelfUriPermissionModeFlags(context,
                            documentUri);
                    context.grantUriPermission(getCallingPackage(), newDocumentUri, modeFlags);
                }
                if (!DocumentsContract.isTreeUri(newDocumentUri)) {
                    macapkGrantToCaller(documentUri, newDocumentUri);
                }

                out.putParcelable(DocumentsContract.EXTRA_URI, newDocumentUri);
            }

        } else if (DocumentsContract.METHOD_MOVE_DOCUMENT.equals(method)) {
            final Uri parentSourceUri = extraParentUri;
            final String parentSourceId = DocumentsContract.getDocumentId(parentSourceUri);
            final Uri targetUri = extraTargetUri;
            final String targetId = DocumentsContract.getDocumentId(targetUri);

            enforceWritePermissionInner(documentUri, macapkAttrSourceIfEnforcing());
            enforceReadPermissionInner(parentSourceUri, macapkAttrSourceIfEnforcing());
            enforceWritePermissionInner(targetUri, macapkAttrSourceIfEnforcing());

            final String newDocumentId = moveDocument(documentId, parentSourceId, targetId);

            if (newDocumentId != null) {
                final Uri newDocumentUri = DocumentsContract.buildDocumentUriMaybeUsingTree(
                        documentUri, newDocumentId);

                if (!DocumentsContract.isTreeUri(newDocumentUri) && context != null) {
                    final int modeFlags = getCallingOrSelfUriPermissionModeFlags(context,
                            documentUri);
                    context.grantUriPermission(getCallingPackage(), newDocumentUri, modeFlags);
                }
                if (!DocumentsContract.isTreeUri(newDocumentUri)) {
                    macapkGrantToCaller(documentUri, newDocumentUri);
                }

                out.putParcelable(DocumentsContract.EXTRA_URI, newDocumentUri);
            }

        } else if (DocumentsContract.METHOD_REMOVE_DOCUMENT.equals(method)) {
            final Uri parentSourceUri = extraParentUri;
            final String parentSourceId = DocumentsContract.getDocumentId(parentSourceUri);

            enforceReadPermissionInner(parentSourceUri, macapkAttrSourceIfEnforcing());
            enforceWritePermissionInner(documentUri, macapkAttrSourceIfEnforcing());
            removeDocument(documentId, parentSourceId);

            // It's responsibility of the provider to revoke any grants, as the document may be
            // still attached to another parents.
        } else if (DocumentsContract.METHOD_FIND_DOCUMENT_PATH.equals(method)) {
            final boolean isTreeUri = DocumentsContract.isTreeUri(documentUri);

            if (isTreeUri) {
                enforceReadPermissionInner(documentUri, macapkAttrSourceIfEnforcing());
            } else if (context != null) {
                context.enforceCallingPermission(android.Manifest.permission.MANAGE_DOCUMENTS, null);
            }

            final String parentDocumentId = isTreeUri
                    ? DocumentsContract.getTreeDocumentId(documentUri)
                    : null;

            Path path = findDocumentPath(parentDocumentId, documentId);

            // Ensure provider doesn't leak information to unprivileged callers.
            if (isTreeUri) {
                if (!Objects.equals(path.getPath().get(0), parentDocumentId)) {
                    android.util.Log.wtf(TAG, "Provider doesn't return path from the tree root. Expected: "
                            + parentDocumentId + " found: " + path.getPath().get(0));

                    LinkedList<String> docs = new LinkedList<>(path.getPath());
                    while (docs.size() > 1 && !Objects.equals(docs.getFirst(), parentDocumentId)) {
                        docs.removeFirst();
                    }
                    path = new Path(null, docs);
                }

                if (path.getRootId() != null) {
                    android.util.Log.wtf(TAG, "Provider returns root id :"
                            + path.getRootId() + " unexpectedly. Erase root id.");
                    path = new Path(null, path.getPath());
                }
            }

            out.putParcelable(DocumentsContract.EXTRA_RESULT, path);
        } else if (DocumentsContract.METHOD_GET_DOCUMENT_METADATA.equals(method)) {
            return getDocumentMetadata(documentId);
        } else {
            throw new UnsupportedOperationException("Method not supported " + method);
        }

        return out;
    }

    /** AOSP :1307-1312: revoke every grant to the document and to a tree rooted
     *  at it. Context.revokeUriPermission is a no-op here (one app), so the
     *  records MacApkUriGrants keeps for this app are revoked directly -- the
     *  persisted ones included, as UriGrantsManagerService does
     *  (MACAPK_PERSISTED_URI_GRANTS). The =0 arm is the pre-change no-op. */
    public final void revokeDocumentPermission(String documentId) {
        if (!DISPATCH) return;
        final Context context = getContext();
        final Uri doc = DocumentsContract.buildDocumentUri(mAuthority, documentId);
        final Uri tree = DocumentsContract.buildTreeDocumentUri(mAuthority, documentId);
        if (context != null) {
            context.revokeUriPermission(doc, ~0);
            context.revokeUriPermission(tree, ~0);
        }
        android.content.MacApkUriGrants.revoke(doc);
        android.content.MacApkUriGrants.revoke(tree);
    }

    /** AOSP :1319-1323. */
    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!DISPATCH) return super.openFile(uri, mode);
        enforceTree(uri);
        return openDocument(DocumentsContract.getDocumentId(uri), mode, null);
    }

    /** AOSP :1330-1335. */
    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode, CancellationSignal signal)
            throws FileNotFoundException {
        if (!DISPATCH) return super.openFile(uri, mode, signal);
        enforceTree(uri);
        return openDocument(DocumentsContract.getDocumentId(uri), mode, signal);
    }

    /** AOSP :1343-1349. */
    @Override
    public AssetFileDescriptor openAssetFile(Uri uri, String mode) throws FileNotFoundException {
        if (!DISPATCH) return super.openAssetFile(uri, mode);
        enforceTree(uri);
        final ParcelFileDescriptor fd = openDocument(DocumentsContract.getDocumentId(uri), mode, null);
        return fd != null ? new AssetFileDescriptor(fd, 0, -1) : null;
    }

    /** AOSP :1357-1363. */
    @Override
    public AssetFileDescriptor openAssetFile(Uri uri, String mode, CancellationSignal signal)
            throws FileNotFoundException {
        if (!DISPATCH) return super.openAssetFile(uri, mode, signal);
        enforceTree(uri);
        final ParcelFileDescriptor fd = openDocument(DocumentsContract.getDocumentId(uri), mode,
                signal);
        return fd != null ? new AssetFileDescriptor(fd, 0, -1) : null;
    }

    /** AOSP :1372-1376. */
    @Override
    public AssetFileDescriptor openTypedAssetFile(Uri uri, String mimeTypeFilter, Bundle opts)
            throws FileNotFoundException {
        if (!DISPATCH) return super.openTypedAssetFile(uri, mimeTypeFilter, opts);
        return openTypedAssetFileImpl(uri, mimeTypeFilter, opts, null);
    }

    /** AOSP :1385-1390. */
    @Override
    public AssetFileDescriptor openTypedAssetFile(Uri uri, String mimeTypeFilter, Bundle opts,
                                                  CancellationSignal signal)
            throws FileNotFoundException {
        if (!DISPATCH) return super.openTypedAssetFile(uri, mimeTypeFilter, opts, signal);
        return openTypedAssetFileImpl(uri, mimeTypeFilter, opts, signal);
    }

    /** AOSP :1405-1430: the document's own type when it is streamable (not
     *  virtual) and matches the filter. The =0 arm is the pre-change null. */
    public String[] getDocumentStreamTypes(String documentId, String mimeTypeFilter) {
        if (!DISPATCH) return null;
        Cursor cursor = null;
        try {
            cursor = queryDocument(documentId, null);
            if (cursor.moveToFirst()) {
                final String mimeType =
                        cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE));
                final long flags =
                        cursor.getLong(cursor.getColumnIndexOrThrow(Document.COLUMN_FLAGS));
                if ((flags & Document.FLAG_VIRTUAL_DOCUMENT) == 0 && mimeType != null
                        && mimeTypeMatches(mimeType, mimeTypeFilter)) {
                    return new String[] { mimeType };
                }
            }
        } catch (FileNotFoundException e) {
            return null;
        } finally {
            closeQuietly(cursor);
        }
        return null;
    }

    /** AOSP android.content.MimeTypeFilter.matches(String, String). */
    private static boolean mimeTypeMatches(String mimeType, String filter) {
        if (mimeType == null) return false;
        final String[] mimeTypeParts = mimeType.split("/");
        final String[] filterParts = filter.split("/");
        if (filterParts.length != 2) {
            throw new IllegalArgumentException(
                    "Ill-formatted MIME type filter. Must be type/subtype.");
        }
        if (filterParts[0].isEmpty() || filterParts[1].isEmpty()) {
            throw new IllegalArgumentException(
                    "Ill-formatted MIME type filter. Type or subtype empty.");
        }
        if (mimeTypeParts.length != 2) return false;
        if (!"*".equals(filterParts[0]) && !filterParts[0].equals(mimeTypeParts[0])) return false;
        if (!"*".equals(filterParts[1]) && !filterParts[1].equals(mimeTypeParts[1])) return false;
        return true;
    }

    /** AOSP :1437-1441. */
    @Override
    public String[] getStreamTypes(Uri uri, String mimeTypeFilter) {
        if (!DISPATCH) return super.getStreamTypes(uri, mimeTypeFilter);
        enforceTree(uri);
        return getDocumentStreamTypes(DocumentsContract.getDocumentId(uri), mimeTypeFilter);
    }

    /** AOSP :1446-1467: a size hint asks for the thumbnail; "*" + "/" + "*", or a
     *  filter the document's own type satisfies, is the plain open; anything else
     *  is the subclass's openTypedDocument. */
    private AssetFileDescriptor openTypedAssetFileImpl(Uri uri, String mimeTypeFilter, Bundle opts,
                                                       CancellationSignal signal)
            throws FileNotFoundException {
        enforceTree(uri);
        final String documentId = DocumentsContract.getDocumentId(uri);
        if (opts != null && opts.containsKey(ContentResolver.EXTRA_SIZE)) {
            final Point sizeHint = opts.getParcelable(ContentResolver.EXTRA_SIZE, Point.class);
            return openDocumentThumbnail(documentId, sizeHint, signal);
        }
        if ("*/*".equals(mimeTypeFilter)) {
            // If they can take anything, the untyped open call is good enough.
            return openAssetFile(uri, "r");
        }
        final String baseType = getType(uri);
        if (baseType != null && ClipDescription.compareMimeTypes(baseType, mimeTypeFilter)) {
            // Use old untyped open call if this provider has a type for this
            // URI and it matches the request.
            return openAssetFile(uri, "r");
        }
        // For any other yet unhandled case, let the provider subclass handle it.
        return openTypedDocument(documentId, mimeTypeFilter, opts, signal);
    }
}
