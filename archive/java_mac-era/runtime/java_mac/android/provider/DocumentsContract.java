// android.provider.DocumentsContract -- the contract for the Storage
// Access Framework (SAF). Apps that use ACTION_OPEN_DOCUMENT_TREE / the
// document picker call these static helpers to build and parse the
// content:// URIs the picker returns. The helpers are PURE URI math
// (no provider backend needed), so they are implemented faithfully per
// AOSP path structure (content://auth/document/<id>,
// content://auth/tree/<treeId>/document/<docId>, etc).
//
// v1.0.1654 (MACAPK_DOCSPROVIDER_DISPATCH, default ON): the call() wrappers below
// now mirror AOSP's exception contract too (try / catch Exception /
// rethrowIfNecessary), because DocumentsProvider now really dispatches them and a
// provider can throw. The URI builders and parsers were already AOSP-exact
// (checked line by line against android14-release :908-1296 for this change).
// Missing public constants, the Document / Root flags, buildBaseDocumentUri,
// buildDocumentUriMaybeUsingTree, isContentUri, setManageMode / isManageMode and
// openImageThumbnail were added; getSearchDocumentsQuery(Bundle) now reads
// QUERY_ARG_DISPLAY_NAME as AOSP does. See
// docs/document-picker-host-panel_2026-09-27.md.
package android.provider;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import java.io.FileNotFoundException;
import java.util.List;

public final class DocumentsContract {
    private DocumentsContract() {}

    private static final String PATH_ROOT = "root";
    private static final String PATH_RECENT = "recent";
    private static final String PATH_DOCUMENT = "document";
    private static final String PATH_CHILDREN = "children";
    private static final String PATH_SEARCH = "search";
    private static final String PATH_TREE = "tree";

    public static final String EXTRA_INITIAL_URI = "android.provider.extra.INITIAL_URI";
    public static final String EXTRA_EXCLUDE_SELF = "android.provider.extra.EXCLUDE_SELF";
    public static final String EXTRA_ORIENTATION = "android.provider.extra.ORIENTATION";
    public static final String EXTRA_PROMPT = "android.provider.extra.PROMPT";
    // These two are bare "info"/"error", NOT namespaced like the EXTRA_* around them.
    // frameworks/base/core/java/android/provider/DocumentsContract.java (android-11.0.0_r48)
    public static final String EXTRA_INFO = "info";
    public static final String EXTRA_ERROR = "error";
    public static final String EXTRA_LOADING = "loading";
    public static final String EXTRA_TARGET_URI = "android.content.extra.TARGET_URI";

    public static final String PROVIDER_INTERFACE = "android.content.action.DOCUMENTS_PROVIDER";

    private static final String TAG = "DocumentsContract";

    // AOSP android14-release DocumentsContract.java :97-289, the public constants
    // this file did not carry yet. Values verbatim.
    @Deprecated
    public static final String EXTRA_PACKAGE_NAME = android.content.Intent.EXTRA_PACKAGE_NAME;
    /** @hide SystemApi */
    public static final String EXTRA_SHOW_ADVANCED = "android.provider.extra.SHOW_ADVANCED";
    public static final String QUERY_ARG_DISPLAY_NAME = "android:query-arg-display-name";
    public static final String QUERY_ARG_MIME_TYPES = "android:query-arg-mime-types";
    public static final String QUERY_ARG_FILE_SIZE_OVER = "android:query-arg-file-size-over";
    public static final String QUERY_ARG_LAST_MODIFIED_AFTER =
            "android:query-arg-last-modified-after";
    public static final String QUERY_ARG_EXCLUDE_MEDIA = "android:query-arg-exclude-media";
    public static final String ACTION_DOCUMENT_SETTINGS = "android.provider.action.DOCUMENT_SETTINGS";
    /** @hide SystemApi */
    public static final String ACTION_MANAGE_DOCUMENT = "android.provider.action.MANAGE_DOCUMENT";
    /** @hide SystemApi */
    public static final String ACTION_DOCUMENT_ROOT_SETTINGS =
            "android.provider.action.DOCUMENT_ROOT_SETTINGS";
    /** @hide SystemApi */
    public static final String EXTERNAL_STORAGE_PROVIDER_AUTHORITY =
            "com.android.externalstorage.documents";
    /** @hide SystemApi. AOSP spells it Downloads.Impl.AUTHORITY, whose value is this. */
    public static final String DOWNLOADS_PROVIDER_AUTHORITY = "downloads";
    /** @hide */
    public static final String EXTERNAL_STORAGE_PRIMARY_EMULATED_ROOT_ID = "primary";
    /** @hide */
    public static final String PACKAGE_DOCUMENTS_UI = "com.android.documentsui";
    public static final String METADATA_TYPES = "android:documentMetadataTypes";
    public static final String METADATA_EXIF = "android:documentExif";
    public static final String METADATA_TREE_COUNT = "android:metadataTreeCount";
    public static final String METADATA_TREE_SIZE = "android:metadataTreeSize";
    /** @hide :884 */
    public static final String EXTRA_URI_PERMISSIONS = "uriPermissions";
    /** @hide :898-899 */
    private static final String PARAM_QUERY = "query";
    private static final String PARAM_MANAGE = "manage";

    // ContentProvider#call() method names + the Bundle keys the create/
    // delete/rename/move/remove helpers below marshal through them. AOSP
    // frameworks/base core/java/android/provider/DocumentsContract.java.
    public static final String METHOD_CREATE_DOCUMENT = "android:createDocument";
    public static final String METHOD_DELETE_DOCUMENT = "android:deleteDocument";
    public static final String METHOD_RENAME_DOCUMENT = "android:renameDocument";
    public static final String METHOD_COPY_DOCUMENT = "android:copyDocument";
    public static final String METHOD_MOVE_DOCUMENT = "android:moveDocument";
    public static final String METHOD_REMOVE_DOCUMENT = "android:removeDocument";
    public static final String METHOD_IS_CHILD_DOCUMENT = "android:isChildDocument";
    public static final String METHOD_EJECT_ROOT = "android:ejectRoot";
    public static final String METHOD_FIND_DOCUMENT_PATH = "android:findDocumentPath";
    public static final String METHOD_CREATE_WEB_LINK_INTENT = "android:createWebLinkIntent";
    public static final String METHOD_GET_DOCUMENT_METADATA = "android:getDocumentMetadata";
    /** @hide AOSP's options bundle key for createWebLinkIntent. */
    public static final String EXTRA_OPTIONS = "options";

    public static final String EXTRA_URI = "uri";
    public static final String EXTRA_PARENT_URI = "parentUri";
    public static final String EXTRA_RESULT = "result";

    public static Uri buildRootsUri(String authority) {
        return new Uri.Builder().scheme("content")
                .authority(authority).appendPath(PATH_ROOT).build();
    }

    public static Uri buildRootUri(String authority, String rootId) {
        return new Uri.Builder().scheme("content")
                .authority(authority).appendPath(PATH_ROOT).appendPath(rootId).build();
    }

    public static Uri buildRecentDocumentsUri(String authority, String rootId) {
        return new Uri.Builder().scheme("content").authority(authority)
                .appendPath(PATH_ROOT).appendPath(rootId).appendPath(PATH_RECENT).build();
    }

    public static Uri buildDocumentUri(String authority, String documentId) {
        return new Uri.Builder().scheme("content").authority(authority)
                .appendPath(PATH_DOCUMENT).appendPath(documentId).build();
    }

    public static Uri buildTreeDocumentUri(String authority, String documentId) {
        return new Uri.Builder().scheme("content").authority(authority)
                .appendPath(PATH_TREE).appendPath(documentId).build();
    }

    public static Uri buildDocumentUriUsingTree(Uri treeUri, String documentId) {
        return new Uri.Builder().scheme("content").authority(treeUri.getAuthority())
                .appendPath(PATH_TREE).appendPath(getTreeDocumentId(treeUri))
                .appendPath(PATH_DOCUMENT).appendPath(documentId).build();
    }

    /** AOSP :976-983. */
    public static Uri buildBaseDocumentUri(String authority) {
        return new Uri.Builder().scheme("content").authority(authority)
                .appendPath(PATH_DOCUMENT).build();
    }

    /** @hide AOSP :1016-1022 -- a tree-form URI when the base is a tree URI.
     *  DocumentsProvider.call uses it for every id it hands back. */
    public static Uri buildDocumentUriMaybeUsingTree(Uri baseUri, String documentId) {
        if (isTreeUri(baseUri)) {
            return buildDocumentUriUsingTree(baseUri, documentId);
        } else {
            return buildDocumentUri(baseUri.getAuthority(), documentId);
        }
    }

    public static Uri buildChildDocumentsUri(String authority, String parentDocumentId) {
        return new Uri.Builder().scheme("content").authority(authority)
                .appendPath(PATH_DOCUMENT).appendPath(parentDocumentId)
                .appendPath(PATH_CHILDREN).build();
    }

    public static Uri buildChildDocumentsUriUsingTree(Uri treeUri, String parentDocumentId) {
        return new Uri.Builder().scheme("content").authority(treeUri.getAuthority())
                .appendPath(PATH_TREE).appendPath(getTreeDocumentId(treeUri))
                .appendPath(PATH_DOCUMENT).appendPath(parentDocumentId)
                .appendPath(PATH_CHILDREN).build();
    }

    public static Uri buildSearchDocumentsUri(String authority, String rootId, String query) {
        return new Uri.Builder().scheme("content").authority(authority)
                .appendPath(PATH_ROOT).appendPath(rootId).appendPath(PATH_SEARCH)
                .appendQueryParameter("query", query).build();
    }

    public static String getRootId(Uri rootUri) {
        final List<String> paths = rootUri.getPathSegments();
        if (paths.size() >= 2 && PATH_ROOT.equals(paths.get(0))) {
            return paths.get(1);
        }
        throw new IllegalArgumentException("Invalid URI: " + rootUri);
    }

    public static String getDocumentId(Uri documentUri) {
        final List<String> paths = documentUri.getPathSegments();
        if (paths.size() >= 2 && PATH_DOCUMENT.equals(paths.get(0))) {
            return paths.get(1);
        }
        if (paths.size() >= 4 && PATH_TREE.equals(paths.get(0))
                && PATH_DOCUMENT.equals(paths.get(2))) {
            return paths.get(3);
        }
        throw new IllegalArgumentException("Invalid URI: " + documentUri);
    }

    public static String getTreeDocumentId(Uri documentUri) {
        final List<String> paths = documentUri.getPathSegments();
        if (paths.size() >= 2 && PATH_TREE.equals(paths.get(0))) {
            return paths.get(1);
        }
        throw new IllegalArgumentException("Invalid URI: " + documentUri);
    }

    public static String getSearchDocumentsQuery(Uri searchDocumentsUri) {
        return searchDocumentsUri.getQueryParameter("query");
    }

    /** AOSP :1305-1308: `bundle.getString(QUERY_ARG_DISPLAY_NAME, "")` after a
     *  null check that throws. This read the key "query" (the URI parameter name,
     *  not the Bundle key), so a provider's querySearchDocuments(rootId,
     *  projection, queryArgs) default always saw null. Fixed under
     *  MACAPK_DOCSPROVIDER_DISPATCH; =0 keeps the old read. */
    public static String getSearchDocumentsQuery(Bundle bundle) {
        if (!DocumentsProvider.DISPATCH) {
            return bundle != null ? bundle.getString("query") : null;
        }
        if (bundle == null) throw new NullPointerException("bundle can not be null");
        return bundle.getString(QUERY_ARG_DISPLAY_NAME, "" /* defaultValue */);
    }

    /** @hide SystemApi, AOSP :1317-1320. */
    public static Uri setManageMode(Uri uri) {
        if (uri == null) throw new NullPointerException("uri can not be null");
        return uri.buildUpon().appendQueryParameter(PARAM_MANAGE, "true").build();
    }

    /** @hide SystemApi, AOSP :1328-1331. DocumentsProvider.query routes a
     *  children URI carrying it to queryChildDocumentsForManage. */
    public static boolean isManageMode(Uri uri) {
        if (uri == null) throw new NullPointerException("uri can not be null");
        return uri.getBooleanQueryParameter(PARAM_MANAGE, false);
    }

    /** AOSP :1218-1220. */
    public static boolean isContentUri(Uri uri) {
        return uri != null && "content".equals(uri.getScheme());
    }

    public static boolean isDocumentUri(Context context, Uri uri) {
        if (uri == null || !"content".equals(uri.getScheme())) return false;
        final List<String> paths = uri.getPathSegments();
        if (paths.size() == 2) return PATH_DOCUMENT.equals(paths.get(0));
        if (paths.size() == 4) {
            return PATH_TREE.equals(paths.get(0)) && PATH_DOCUMENT.equals(paths.get(2));
        }
        return false;
    }

    public static boolean isTreeUri(Uri uri) {
        final List<String> paths = uri.getPathSegments();
        return paths.size() >= 2 && PATH_TREE.equals(paths.get(0));
    }

    public static boolean isRootUri(Context context, Uri uri) {
        if (uri == null || !"content".equals(uri.getScheme())) return false;
        final List<String> paths = uri.getPathSegments();
        return paths.size() == 2 && PATH_ROOT.equals(paths.get(0));
    }

    // AOSP's create/delete/rename/move/removeDocument are thin wrappers that
    // build a Bundle and call ContentResolver.call(authority, METHOD_X, null,
    // bundle); the actual work happens in the app's own DocumentsProvider
    // that call() dispatches to. Our ContentResolver.call() already routes
    // through ContentProviderRegistry (manifest-driven, authority-keyed) to
    // the live provider, so these just need to build the right Bundle.
    //
    // AOSP trusts call() to always either succeed or throw (a real Binder
    // transaction to an unknown authority raises IllegalArgumentException);
    // our in-process ContentResolver.call() instead returns null when no
    // provider is registered for the authority (see ContentResolver.java).
    // Most Mac.apk apps never bundle a DocumentsProvider at all, so blindly
    // dereferencing that null the way AOSP's source does would turn "no SAF
    // provider present" into a new crash where the previous stub was a safe
    // no-op. We fail soft (null/false) for THAT case -- no provider serves
    // the authority -- and only that case.
    //
    // Everything else follows AOSP under MACAPK_DOCSPROVIDER_DISPATCH (default
    // ON): each wrapper is `try { ... } catch (Exception e) { Log.w;
    // rethrowIfNecessary(e); return <failure>; }` (android14-release :1371-1724),
    // so a provider's own RuntimeException reaches an app targeting O+, a
    // FileNotFoundException the provider wrapped in ParcelableException is
    // rethrown as itself, and anything else becomes null / false. With the gate
    // =0 each wrapper runs its pre-change body, kept verbatim in the else arm.

    /** AOSP :1767-1778 (rethrowIfNecessary): only apps targeting O and above see
     *  the provider's exception; older targets get the null / false answer. */
    private static void rethrowIfNecessary(Exception e) throws FileNotFoundException {
        if (dalvik.system.VMRuntime.getRuntime().getTargetSdkVersion() >= 26 /* O */) {
            if (e instanceof android.os.ParcelableException) {
                ((android.os.ParcelableException) e).maybeRethrow(FileNotFoundException.class);
            } else if (e instanceof android.os.RemoteException) {
                throw ((android.os.RemoteException) e).rethrowAsRuntimeException();
            } else if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
        }
    }

    /** Mac.apk fail-soft: true when no provider serves `authority` (the one case
     *  where our ContentResolver.call returns null instead of throwing). */
    private static boolean macapkNoProvider(android.content.ContentResolver content,
            String authority) {
        android.content.ContentProviderClient c = content.acquireContentProviderClient(authority);
        if (c == null) return true;
        c.release();
        return false;
    }

    public static Uri createDocument(android.content.ContentResolver content,
            Uri parentDocumentUri, String mimeType, String displayName)
            throws FileNotFoundException {
        if (DocumentsProvider.DISPATCH) {
            try {
                if (macapkNoProvider(content, parentDocumentUri.getAuthority())) return null;
                final Bundle in = new Bundle();
                in.putParcelable(EXTRA_URI, parentDocumentUri);
                in.putString(Document.COLUMN_MIME_TYPE, mimeType);
                in.putString(Document.COLUMN_DISPLAY_NAME, displayName);
                final Bundle out = content.call(parentDocumentUri.getAuthority(),
                        METHOD_CREATE_DOCUMENT, null, in);
                return out.getParcelable(EXTRA_URI, Uri.class);
            } catch (Exception e) {
                android.util.Log.w(TAG, "Failed to create document", e);
                rethrowIfNecessary(e);
                return null;
            }
        }
        final Bundle in = new Bundle();
        in.putParcelable(EXTRA_URI, parentDocumentUri);
        in.putString(Document.COLUMN_MIME_TYPE, mimeType);
        in.putString(Document.COLUMN_DISPLAY_NAME, displayName);
        final Bundle out = content.call(parentDocumentUri.getAuthority(),
                METHOD_CREATE_DOCUMENT, null, in);
        return out != null ? out.getParcelable(EXTRA_URI, Uri.class) : null;
    }

    public static boolean deleteDocument(android.content.ContentResolver content,
            Uri documentUri) throws FileNotFoundException {
        if (DocumentsProvider.DISPATCH) {
            try {
                if (macapkNoProvider(content, documentUri.getAuthority())) return false;
                final Bundle in = new Bundle();
                in.putParcelable(EXTRA_URI, documentUri);
                content.call(documentUri.getAuthority(), METHOD_DELETE_DOCUMENT, null, in);
                return true;
            } catch (Exception e) {
                android.util.Log.w(TAG, "Failed to delete document", e);
                rethrowIfNecessary(e);
                return false;
            }
        }
        final Bundle in = new Bundle();
        in.putParcelable(EXTRA_URI, documentUri);
        final Bundle out = content.call(documentUri.getAuthority(),
                METHOD_DELETE_DOCUMENT, null, in);
        return out != null;
    }

    public static Uri renameDocument(android.content.ContentResolver content,
            Uri documentUri, String displayName) throws FileNotFoundException {
        if (DocumentsProvider.DISPATCH) {
            try {
                if (macapkNoProvider(content, documentUri.getAuthority())) return null;
                final Bundle in = new Bundle();
                in.putParcelable(EXTRA_URI, documentUri);
                in.putString(Document.COLUMN_DISPLAY_NAME, displayName);
                final Bundle out = content.call(documentUri.getAuthority(),
                        METHOD_RENAME_DOCUMENT, null, in);
                final Uri outUri = out.getParcelable(EXTRA_URI, Uri.class);
                return (outUri != null) ? outUri : documentUri;
            } catch (Exception e) {
                android.util.Log.w(TAG, "Failed to rename document", e);
                rethrowIfNecessary(e);
                return null;
            }
        }
        final Bundle in = new Bundle();
        in.putParcelable(EXTRA_URI, documentUri);
        in.putString(Document.COLUMN_DISPLAY_NAME, displayName);
        final Bundle out = content.call(documentUri.getAuthority(),
                METHOD_RENAME_DOCUMENT, null, in);
        if (out == null) return null;
        final Uri outUri = out.getParcelable(EXTRA_URI, Uri.class);
        return (outUri != null) ? outUri : documentUri;
    }

    public static Uri moveDocument(android.content.ContentResolver content,
            Uri sourceDocumentUri, Uri sourceParentDocumentUri,
            Uri targetParentDocumentUri) throws FileNotFoundException {
        if (DocumentsProvider.DISPATCH) {
            try {
                if (macapkNoProvider(content, sourceDocumentUri.getAuthority())) return null;
                final Bundle in = new Bundle();
                in.putParcelable(EXTRA_URI, sourceDocumentUri);
                in.putParcelable(EXTRA_PARENT_URI, sourceParentDocumentUri);
                in.putParcelable(EXTRA_TARGET_URI, targetParentDocumentUri);
                final Bundle out = content.call(sourceDocumentUri.getAuthority(),
                        METHOD_MOVE_DOCUMENT, null, in);
                return out.getParcelable(EXTRA_URI, Uri.class);
            } catch (Exception e) {
                android.util.Log.w(TAG, "Failed to move document", e);
                rethrowIfNecessary(e);
                return null;
            }
        }
        final Bundle in = new Bundle();
        in.putParcelable(EXTRA_URI, sourceDocumentUri);
        in.putParcelable(EXTRA_PARENT_URI, sourceParentDocumentUri);
        in.putParcelable(EXTRA_TARGET_URI, targetParentDocumentUri);
        final Bundle out = content.call(sourceDocumentUri.getAuthority(),
                METHOD_MOVE_DOCUMENT, null, in);
        return out != null ? out.getParcelable(EXTRA_URI, Uri.class) : null;
    }

    /** AOSP: removes a document from a specific parent (a document reachable
     *  from multiple parents is only detached from this one, not deleted
     *  outright -- that's deleteDocument's job). Was MISSING_METHOD, the
     *  single highest-napps gap this class had. */
    public static boolean removeDocument(android.content.ContentResolver content,
            Uri documentUri, Uri parentDocumentUri) throws FileNotFoundException {
        if (DocumentsProvider.DISPATCH) {
            try {
                if (macapkNoProvider(content, documentUri.getAuthority())) return false;
                final Bundle in = new Bundle();
                in.putParcelable(EXTRA_URI, documentUri);
                in.putParcelable(EXTRA_PARENT_URI, parentDocumentUri);
                content.call(documentUri.getAuthority(), METHOD_REMOVE_DOCUMENT, null, in);
                return true;
            } catch (Exception e) {
                android.util.Log.w(TAG, "Failed to remove document", e);
                rethrowIfNecessary(e);
                return false;
            }
        }
        final Bundle in = new Bundle();
        in.putParcelable(EXTRA_URI, documentUri);
        in.putParcelable(EXTRA_PARENT_URI, parentDocumentUri);
        final Bundle out = content.call(documentUri.getAuthority(),
                METHOD_REMOVE_DOCUMENT, null, in);
        return out != null;
    }

    /** AOSP `android.provider.DocumentsContract.Path` (API 26+) -- the return
     *  type of DocumentsProvider.findDocumentPath, so the provider class cannot
     *  be declared without it.
     *  AOSP: https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/
     *    main/core/java/android/provider/DocumentsContract.java
     *    public static final class Path implements Parcelable
     *    public Path(@Nullable String rootId, List<String> path)
     *    @Nullable public String getRootId()   public List<String> getPath() */
    public static final class Path implements android.os.Parcelable {
        private final String mRootId;
        private final java.util.List<String> mPath;

        public Path(String rootId, java.util.List<String> path) {
            mRootId = rootId;
            mPath = path;
        }

        public String getRootId() { return mRootId; }
        public java.util.List<String> getPath() { return mPath; }

        @Override public int describeContents() { return 0; }
        @Override public void writeToParcel(android.os.Parcel dest, int flags) {
            dest.writeString(mRootId);
            dest.writeStringList(mPath);
        }
        @Override public String toString() {
            return "DocumentsContract.Path{rootId=" + mRootId + ", path=" + mPath + "}";
        }

        public static final Creator<Path> CREATOR = new Creator<Path>() {
            @Override public Path createFromParcel(android.os.Parcel in) {
                return new Path(in.readString(), in.createStringArrayList());
            }
            @Override public Path[] newArray(int size) { return new Path[size]; }
        };
    }

    public static final class Document {
        private Document() {}
        public static final String COLUMN_DOCUMENT_ID = "document_id";
        public static final String COLUMN_MIME_TYPE = "mime_type";
        public static final String COLUMN_DISPLAY_NAME = "_display_name";
        public static final String COLUMN_SUMMARY = "summary";
        public static final String COLUMN_LAST_MODIFIED = "last_modified";
        public static final String COLUMN_ICON = "icon";
        public static final String COLUMN_FLAGS = "flags";
        public static final String COLUMN_SIZE = "_size";

        public static final String MIME_TYPE_DIR = "vnd.android.document/directory";

        // The three were rotated by one bit here. AOSP order is THUMBNAIL(1),
        // WRITE(1<<1), DELETE(1<<2) -- these are OR'd into COLUMN_FLAGS and tested by
        // DocumentFile/SAF clients that inlined the real values at compile time, so a
        // rotation silently grants/denies the wrong capability.
        // frameworks/base/core/java/android/provider/DocumentsContract.java (android-11.0.0_r48)
        public static final int FLAG_SUPPORTS_THUMBNAIL = 1;
        public static final int FLAG_SUPPORTS_WRITE = 1 << 1;
        public static final int FLAG_SUPPORTS_DELETE = 1 << 2;
        public static final int FLAG_DIR_PREFERS_GRID = 1 << 4;
        public static final int FLAG_DIR_PREFERS_LAST_MODIFIED = 1 << 5;
        public static final int FLAG_VIRTUAL_DOCUMENT = 1 << 9;
        public static final int FLAG_SUPPORTS_COPY = 1 << 7;
        public static final int FLAG_SUPPORTS_MOVE = 1 << 8;
        public static final int FLAG_SUPPORTS_REMOVE = 1 << 10;
        public static final int FLAG_SUPPORTS_RENAME = 1 << 6;
        // AOSP android14-release :457-583, the rest of Document's flags.
        public static final int FLAG_DIR_SUPPORTS_CREATE = 1 << 3;
        public static final int FLAG_SUPPORTS_SETTINGS = 1 << 11;
        public static final int FLAG_WEB_LINKABLE = 1 << 12;
        public static final int FLAG_PARTIAL = 1 << 13;
        public static final int FLAG_SUPPORTS_METADATA = 1 << 14;
        public static final int FLAG_DIR_BLOCKS_OPEN_DOCUMENT_TREE = 1 << 15;
    }

    public static final class Root {
        private Root() {}
        public static final String COLUMN_ROOT_ID = "root_id";
        public static final String COLUMN_FLAGS = "flags";
        public static final String COLUMN_ICON = "icon";
        public static final String COLUMN_TITLE = "title";
        public static final String COLUMN_SUMMARY = "summary";
        public static final String COLUMN_DOCUMENT_ID = "document_id";
        public static final String COLUMN_AVAILABLE_BYTES = "available_bytes";
        public static final String COLUMN_CAPACITY_BYTES = "capacity_bytes";
        public static final String COLUMN_MIME_TYPES = "mime_types";
        public static final String COLUMN_QUERY_ARGS = "query_args";

        public static final String MIME_TYPE_ITEM = "vnd.android.document/root";

        public static final int FLAG_LOCAL_ONLY = 1 << 1;
        public static final int FLAG_SUPPORTS_CREATE = 1;
        public static final int FLAG_SUPPORTS_RECENTS = 1 << 2;
        public static final int FLAG_SUPPORTS_SEARCH = 1 << 3;
        public static final int FLAG_SUPPORTS_IS_CHILD = 1 << 4;
        // AOSP android14-release :766-818, the rest of Root's flags.
        public static final int FLAG_SUPPORTS_EJECT = 1 << 5;
        public static final int FLAG_EMPTY = 1 << 6;
        /** @hide SystemApi */
        public static final int FLAG_ADVANCED = 1 << 16;
        /** @hide SystemApi */
        public static final int FLAG_HAS_SETTINGS = 1 << 17;
        /** @hide SystemApi */
        public static final int FLAG_REMOVABLE_SD = 1 << 18;
        /** @hide SystemApi */
        public static final int FLAG_REMOVABLE_USB = 1 << 19;
    }

    // ========================================================================
    // API-36 surface added 2026-08-24.
    //
    // Every one of these is a THIN WRAPPER over ContentResolver.call() into the
    // app's OWN DocumentsProvider -- exactly as in AOSP. Nothing here needs a
    // system service. The one behavioural difference is the same one the block
    // above already documents: AOSP's Binder call() throws for an unknown
    // authority, while Mac.apk's in-process call() returns null, so these fail
    // soft (null / false / no-op) instead of turning "no SAF provider bundled"
    // into a crash.
    // Source: frameworks/base/core/java/android/provider/DocumentsContract.java
    // ========================================================================

    /** Copies a document into a new parent; the provider does the work.
     *  AOSP :1487-1503 under MACAPK_DOCSPROVIDER_DISPATCH. */
    public static Uri copyDocument(android.content.ContentResolver content,
            Uri sourceDocumentUri, Uri targetParentDocumentUri)
            throws java.io.FileNotFoundException {
        if (DocumentsProvider.DISPATCH) {
            try {
                if (macapkNoProvider(content, sourceDocumentUri.getAuthority())) return null;
                final Bundle in = new Bundle();
                in.putParcelable(EXTRA_URI, sourceDocumentUri);
                in.putParcelable(EXTRA_TARGET_URI, targetParentDocumentUri);
                final Bundle out = content.call(sourceDocumentUri.getAuthority(),
                        METHOD_COPY_DOCUMENT, null, in);
                return out.getParcelable(EXTRA_URI, Uri.class);
            } catch (Exception e) {
                android.util.Log.w(TAG, "Failed to copy document", e);
                rethrowIfNecessary(e);
                return null;
            }
        }
        final Bundle in = new Bundle();
        in.putParcelable(EXTRA_URI, sourceDocumentUri);
        in.putParcelable(EXTRA_TARGET_URI, targetParentDocumentUri);
        final Bundle out = content.call(sourceDocumentUri.getAuthority(),
                METHOD_COPY_DOCUMENT, null, in);
        return out != null ? out.getParcelable(EXTRA_URI, Uri.class) : null;
    }

    /** True when childDocumentUri is a descendant of parentDocumentUri.
     *  AOSP :1399-1426 under MACAPK_DOCSPROVIDER_DISPATCH: a null answer or one
     *  without EXTRA_RESULT is a RemoteException inside the try, which
     *  rethrowIfNecessary turns into a RuntimeException for an O+ app. */
    public static boolean isChildDocument(android.content.ContentResolver content,
            Uri parentDocumentUri, Uri childDocumentUri) throws java.io.FileNotFoundException {
        if (content == null) throw new NullPointerException("content can not be null");
        if (parentDocumentUri == null) {
            throw new NullPointerException("parentDocumentUri can not be null");
        }
        if (childDocumentUri == null) {
            throw new NullPointerException("childDocumentUri can not be null");
        }
        if (DocumentsProvider.DISPATCH) {
            try {
                if (macapkNoProvider(content, parentDocumentUri.getAuthority())) return false;
                final Bundle in = new Bundle();
                in.putParcelable(EXTRA_URI, parentDocumentUri);
                in.putParcelable(EXTRA_TARGET_URI, childDocumentUri);
                final Bundle out = content.call(parentDocumentUri.getAuthority(),
                        METHOD_IS_CHILD_DOCUMENT, null, in);
                if (out == null) {
                    throw new android.os.RemoteException(
                            "Failed to get a response from isChildDocument query.");
                }
                if (!out.containsKey(EXTRA_RESULT)) {
                    throw new android.os.RemoteException("Response did not include result field..");
                }
                return out.getBoolean(EXTRA_RESULT);
            } catch (Exception e) {
                android.util.Log.w(TAG, "Failed to create document", e);
                rethrowIfNecessary(e);
                return false;
            }
        }
        final Bundle in = new Bundle();
        in.putParcelable(EXTRA_URI, parentDocumentUri);
        in.putParcelable(EXTRA_TARGET_URI, childDocumentUri);
        final Bundle out = content.call(parentDocumentUri.getAuthority(),
                METHOD_IS_CHILD_DOCUMENT, null, in);
        if (out == null || !out.containsKey(EXTRA_RESULT)) return false;
        return out.getBoolean(EXTRA_RESULT);
    }

    /** Asks the provider to eject the removable volume backing a root.
     *  AOSP :1565-1575: every failure is only logged. */
    public static void ejectRoot(android.content.ContentResolver content, Uri rootUri) {
        if (DocumentsProvider.DISPATCH) {
            try {
                final Bundle in = new Bundle();
                in.putParcelable(EXTRA_URI, rootUri);
                content.call(rootUri.getAuthority(), METHOD_EJECT_ROOT, null, in);
            } catch (Exception e) {
                android.util.Log.w(TAG, "Failed to eject", e);
            }
            return;
        }
        final Bundle in = new Bundle();
        in.putParcelable(EXTRA_URI, rootUri);
        content.call(rootUri.getAuthority(), METHOD_EJECT_ROOT, null, in);
    }

    /** Returns the provider's metadata bundle for a document, or null.
     *  AOSP :1607-1623 under MACAPK_DOCSPROVIDER_DISPATCH. */
    public static Bundle getDocumentMetadata(android.content.ContentResolver content,
            Uri documentUri) throws java.io.FileNotFoundException {
        if (DocumentsProvider.DISPATCH) {
            if (content == null) throw new NullPointerException("content can not be null");
            if (documentUri == null) throw new NullPointerException("documentUri can not be null");
            try {
                final Bundle in = new Bundle();
                in.putParcelable(EXTRA_URI, documentUri);
                return content.call(documentUri.getAuthority(), METHOD_GET_DOCUMENT_METADATA,
                        null, in);
            } catch (Exception e) {
                android.util.Log.w(TAG, "Failed to get document metadata");
                rethrowIfNecessary(e);
                return null;
            }
        }
        final Bundle in = new Bundle();
        in.putParcelable(EXTRA_URI, documentUri);
        return content.call(documentUri.getAuthority(), METHOD_GET_DOCUMENT_METADATA, null, in);
    }

    /** Returns the path from the root down to a document, or null.
     *  AOSP :1637-1652 under MACAPK_DOCSPROVIDER_DISPATCH. */
    public static Path findDocumentPath(android.content.ContentResolver content, Uri treeUri)
            throws java.io.FileNotFoundException {
        if (DocumentsProvider.DISPATCH) {
            try {
                if (macapkNoProvider(content, treeUri.getAuthority())) return null;
                final Bundle in = new Bundle();
                in.putParcelable(EXTRA_URI, treeUri);
                final Bundle out = content.call(treeUri.getAuthority(),
                        METHOD_FIND_DOCUMENT_PATH, null, in);
                return out.getParcelable(EXTRA_RESULT, Path.class);
            } catch (Exception e) {
                android.util.Log.w(TAG, "Failed to find path", e);
                rethrowIfNecessary(e);
                return null;
            }
        }
        final Bundle in = new Bundle();
        in.putParcelable(EXTRA_URI, treeUri);
        final Bundle out = content.call(treeUri.getAuthority(),
                METHOD_FIND_DOCUMENT_PATH, null, in);
        return out != null ? out.getParcelable(EXTRA_RESULT, Path.class) : null;
    }

    /** Asks the provider for a shareable web link to a document.
     *  AOSP :1704-1724 under MACAPK_DOCSPROVIDER_DISPATCH. */
    public static android.content.IntentSender createWebLinkIntent(
            android.content.ContentResolver content, Uri uri, Bundle options)
            throws java.io.FileNotFoundException {
        if (DocumentsProvider.DISPATCH) {
            try {
                if (macapkNoProvider(content, uri.getAuthority())) return null;
                final Bundle in = new Bundle();
                in.putParcelable(EXTRA_URI, uri);
                if (options != null) {
                    in.putBundle(EXTRA_OPTIONS, options);
                }
                final Bundle out = content.call(uri.getAuthority(),
                        METHOD_CREATE_WEB_LINK_INTENT, null, in);
                return out.getParcelable(EXTRA_RESULT, android.content.IntentSender.class);
            } catch (Exception e) {
                android.util.Log.w(TAG, "Failed to create a web link intent", e);
                rethrowIfNecessary(e);
                return null;
            }
        }
        final Bundle in = new Bundle();
        in.putParcelable(EXTRA_URI, uri);
        // AOSP nests the caller's options under EXTRA_OPTIONS so they cannot
        // collide with the keys above.
        if (options != null) {
            in.putBundle(EXTRA_OPTIONS, options);
        }
        final Bundle out = content.call(uri.getAuthority(),
                METHOD_CREATE_WEB_LINK_INTENT, null, in);
        return out != null
                ? out.getParcelable(EXTRA_RESULT, android.content.IntentSender.class) : null;
    }

    /** @hide AOSP :1733-1763: the EXIF thumbnail range when the image has one,
     *  otherwise the whole file. FileSystemProvider.openDocumentThumbnail (:591-596)
     *  serves image thumbnails through it. Our ExifInterface.getThumbnailRange
     *  returns null, so today this always takes the whole-file branch, as AOSP
     *  does for an image without an embedded thumbnail. */
    public static android.content.res.AssetFileDescriptor openImageThumbnail(java.io.File file)
            throws FileNotFoundException {
        final android.os.ParcelFileDescriptor pfd = android.os.ParcelFileDescriptor.open(
                file, android.os.ParcelFileDescriptor.MODE_READ_ONLY);
        try {
            final android.media.ExifInterface exif =
                    new android.media.ExifInterface(file.getAbsolutePath());
            final long[] thumb = exif.getThumbnailRange();
            if (thumb != null) {
                Bundle extras = null;
                switch (exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, -1)) {
                    case android.media.ExifInterface.ORIENTATION_ROTATE_90:
                        extras = new Bundle(1);
                        extras.putInt(EXTRA_ORIENTATION, 90);
                        break;
                    case android.media.ExifInterface.ORIENTATION_ROTATE_180:
                        extras = new Bundle(1);
                        extras.putInt(EXTRA_ORIENTATION, 180);
                        break;
                    case android.media.ExifInterface.ORIENTATION_ROTATE_270:
                        extras = new Bundle(1);
                        extras.putInt(EXTRA_ORIENTATION, 270);
                        break;
                }
                return new android.content.res.AssetFileDescriptor(pfd, thumb[0], thumb[1], extras);
            }
        } catch (java.io.IOException e) {
        }
        return new android.content.res.AssetFileDescriptor(pfd, 0,
                android.content.res.AssetFileDescriptor.UNKNOWN_LENGTH, null);
    }

    /**
     * AOSP opens the provider's thumbnail asset and decodes it, sizing the
     * decode to the requested Point. Reproduced here over the same
     * openTypedAssetFileDescriptor path; returns null when the provider has no
     * thumbnail, as AOSP does.
     */
    public static android.graphics.Bitmap getDocumentThumbnail(
            android.content.ContentResolver content, Uri documentUri,
            android.graphics.Point size, android.os.CancellationSignal signal)
            throws java.io.FileNotFoundException {
        final Bundle openOpts = new Bundle();
        openOpts.putParcelable(android.content.ContentResolver.EXTRA_SIZE, size);
        android.content.res.AssetFileDescriptor afd = null;
        try {
            afd = content.openTypedAssetFileDescriptor(documentUri, "image/*", openOpts, signal);
            if (afd == null) return null;
            final java.io.FileDescriptor fd = afd.getFileDescriptor();
            if (fd == null) return null;
            final android.graphics.BitmapFactory.Options opts =
                    new android.graphics.BitmapFactory.Options();
            return android.graphics.BitmapFactory.decodeFileDescriptor(fd, null, opts);
        } catch (java.io.FileNotFoundException e) {
            throw e;
        } catch (Exception e) {
            // AOSP logs and returns null rather than propagating a provider bug.
            return null;
        } finally {
            if (afd != null) {
                try { afd.close(); } catch (java.io.IOException ignored) { }
            }
        }
    }

    /**
     * AOSP: true when the URI is a ROOTS uri (one path segment, "root") backed
     * by a DocumentsProvider. Pure URI shape plus a provider-presence check;
     * the shape half is exact here.
     */
    public static boolean isRootsUri(Context context, Uri uri) {
        if (context == null) throw new NullPointerException("context can not be null");
        if (uri == null || !"content".equals(uri.getScheme())) return false;
        final java.util.List<String> paths = uri.getPathSegments();
        return paths.size() == 1 && PATH_ROOT.equals(paths.get(0));
    }
}
