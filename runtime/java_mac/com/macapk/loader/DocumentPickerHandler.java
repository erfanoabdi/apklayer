// DocumentsUI's picker, realised with the macOS open/save panel (v1.0.1653;
// OPEN_DOCUMENT_TREE and EXTRA_INITIAL_URI since v1.0.1654).
//
// Rung 1 of the host ladder: macOS has a native document picker. On a device,
// ACTION_GET_CONTENT, ACTION_OPEN_DOCUMENT, ACTION_CREATE_DOCUMENT and
// ACTION_OPEN_DOCUMENT_TREE are served by DocumentsUI (a full-screen activity: the
// caller is paused and stopped while it is up) and answer with RESULT_OK +
// content:// document (or tree) URIs + URI grants, or RESULT_CANCELED. Before
// this, Mac.apk had no component for them at all: the import / export buttons of
// Aegis, AnkiDroid, OpenSudoku, NewPipe and the like were dead (and since the
// start-resolution work, honestly "No Activity found").
//
// The result mirrors DocumentsUI's picker/ActionHandler.onPickFinished (android14,
// read from LineageOS lineage-21.0 :491-535): ONE uri -> setData; several ->
// setClipData(new ClipData(null, acceptMimes, item0) + items); GET_CONTENT grants
// READ only, OPEN_DOCUMENT_TREE grants READ | WRITE | PERSISTABLE | PREFIX
// (:518-522), OPEN_DOCUMENT / CREATE_DOCUMENT grant READ | WRITE | PERSISTABLE.
// A tree's URI is DocumentsContract.buildTreeDocumentUri(authority, docId)
// (ActionHandler.pickDocument :430-445 -> ConfirmFragment -> finishPicking).
// acceptMimes is State.initAcceptMimes (:140-146): EXTRA_MIME_TYPES when present,
// else the intent type, else "*/*" (PickActivity.includeState :260). The URIs are
// served by HostDocumentsProvider under ExternalStorageProvider's authority, for
// exactly the files (or the directory) the user chose, with exactly the grant the
// action gives. The grant is recorded for the app at the point DocumentsUI's
// result is delivered (MacApkUriGrants.grantFromIntent, the analogue of
// ActivityRecord.sendResult's grantUriPermissionUncheckedFromIntent), which is
// what takePersistableUriPermission later needs.
//
// Gates (each =0 restores the exact pre-change path):
//   MACAPK_DOC_PICKER=0      no host row, no picker (v1.0.1653).
//   MACAPK_DOC_TREE=0        OPEN_DOCUMENT_TREE is not a picker intent (and so
//                            stays unresolved). Trees also need
//                            MACAPK_DOCSPROVIDER_DISPATCH, which lists their
//                            children.
//   MACAPK_DOC_INITIAL_URI=0 EXTRA_INITIAL_URI is ignored and the panel opens
//                            wherever it last was (the v1.0.1653 behaviour).
// MACAPK_DOCPICK_TEST_PATHS (colon-separated) answers WITHOUT a panel -- a
// harness seam for the offline gate and live checks; it is logged on use. For
// OPEN_DOCUMENT_TREE, give a directory.
package com.macapk.loader;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.MacApkUriGrants;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class DocumentPickerHandler {
    private static final String TAG = "Mac.apk.Documents";

    static final boolean DOC_PICKER = !"0".equals(System.getenv("MACAPK_DOC_PICKER"));
    /** MACAPK_DOC_TREE, default ON (v1.0.1654). */
    static final boolean TREE = !"0".equals(System.getenv("MACAPK_DOC_TREE"));
    /** MACAPK_DOC_INITIAL_URI, default ON (v1.0.1654). */
    static final boolean INITIAL_URI = !"0".equals(System.getenv("MACAPK_DOC_INITIAL_URI"));
    private static final String TEST_PATHS = System.getenv("MACAPK_DOCPICK_TEST_PATHS");

    /** nativePickEx modes (document_picker.mm). */
    static final int MODE_OPEN = 0, MODE_SAVE = 1, MODE_TREE = 2;

    private static final class Pending {
        final Activity.MacApkHostResult token;
        final SpecialAccessHandler.Cover cover;
        final String action;
        final String[] acceptMimes;
        Pending(Activity.MacApkHostResult token, SpecialAccessHandler.Cover cover, String action,
                String[] acceptMimes) {
            this.token = token; this.cover = cover; this.action = action; this.acceptMimes = acceptMimes;
        }
    }
    private static final ConcurrentHashMap<Long, Pending> sPending = new ConcurrentHashMap<>();
    private static final AtomicLong sSeq = new AtomicLong(1);

    private DocumentPickerHandler() {}

    /** OPEN_DOCUMENT_TREE is served: its gate, and the dispatch its children need. */
    static boolean treeOn() {
        return TREE && DocumentsProvider.macapkDispatch();
    }

    /** Is this a document-picker intent (the PURE predicate; no extras)? */
    static boolean isPickerIntent(Intent intent) {
        if (!DOC_PICKER || intent == null) return false;
        String a = intent.getAction();
        return Intent.ACTION_GET_CONTENT.equals(a)
            || Intent.ACTION_OPEN_DOCUMENT.equals(a)
            || Intent.ACTION_CREATE_DOCUMENT.equals(a)
            || (treeOn() && Intent.ACTION_OPEN_DOCUMENT_TREE.equals(a))
            || isPhotoPicker(intent);
    }

    /** MACAPK_PHOTO_PICKER (labelled bisect gate, default ON; =0 leaves
     *  PICK_IMAGES unresolved exactly as before v1.0.1658): the system photo
     *  picker's action, served by the same macOS open panel filtered to what
     *  PhotoPickerActivity shows. AOSP: MediaProvider's PhotoPickerActivity
     *  filters android.provider.action.PICK_IMAGES with no type, image/* or
     *  video/* (packages/providers/MediaProvider AndroidManifest.xml). androidx
     *  ActivityResultContracts.PickVisualMedia launches exactly this action on
     *  API 33+, so with it unresolved every app using the modern photo picker
     *  got ActivityNotFoundException. */
    static final boolean PHOTO_PICKER = !"0".equals(System.getenv("MACAPK_PHOTO_PICKER"));

    static boolean isPhotoPicker(Intent intent) {
        return PHOTO_PICKER && intent != null
            && android.provider.MediaStore.ACTION_PICK_IMAGES.equals(intent.getAction());
    }

    /** DocumentsUI State.initAcceptMimes + PickActivity.includeState: the
     *  EXTRA_MIME_TYPES array when the intent carries one, otherwise the intent's
     *  own type, otherwise any type. The two are never merged.
     *
     *  The photo picker (PICK_IMAGES) filters more strictly, exactly as MediaProvider's
     *  MimeFilterUtils.getMimeTypeFilters does (packages/providers/MediaProvider, main
     *  src/com/android/providers/media/photopicker/util/MimeFilterUtils.java :60-88):
     *  EXTRA_MIME_TYPES wins when EVERY entry is image/ or video/; a non-media entry
     *  (or an empty / null array) throws IllegalArgumentException("Invalid
     *  EXTRA_MIME_TYPES value, only media mime type filters are accepted"), which
     *  PhotoPickerActivity.onCreate catches by finishing with no result
     *  (finishWithoutLoggingCancelledResult) -- returned here as NULL, which handle()
     *  delivers as RESULT_CANCELED; otherwise a media getType() is the filter; and any
     *  other type, "*"+"/*" included, means "no filter" = all images and videos. */
    static String[] acceptMimes(Intent real) {
        if (isPhotoPicker(real)) return photoPickerMimes(real);
        if (real.hasExtra(Intent.EXTRA_MIME_TYPES)) {
            String[] extra = null;
            try { extra = real.getStringArrayExtra(Intent.EXTRA_MIME_TYPES); } catch (Throwable ignored) {}
            if (extra != null) return extra;
        }
        return new String[] { real.getType() == null ? "*/*" : real.getType() };
    }

    /** MimeFilterUtils.isMimeTypeMedia: an image/ or video/ type. */
    private static boolean isMediaMime(String m) {
        if (m == null) return false;
        String l = m.toLowerCase(java.util.Locale.ROOT);
        return l.startsWith("image/") || l.startsWith("video/");
    }

    /** acceptMimes for PICK_IMAGES; null = the photo picker would refuse the request. */
    private static String[] photoPickerMimes(Intent real) {
        if (real.hasExtra(Intent.EXTRA_MIME_TYPES)) {
            String[] extra = null;
            try { extra = real.getStringArrayExtra(Intent.EXTRA_MIME_TYPES); } catch (Throwable ignored) {}
            if (extra == null || extra.length == 0) return null;
            for (String m : extra) if (!isMediaMime(m)) return null;
            return extra;
        }
        String t = real.getType();
        return isMediaMime(t) ? new String[] { t } : new String[] { "image/*", "video/*" };
    }

    /**
     * Where the panel should open for EXTRA_INITIAL_URI, or null for "wherever it
     * last was". DocumentsUI ActionHandler.launchToInitialUri (:174-200): only a
     * ROOT or a DOCUMENT URI is honoured (a bare tree URI is neither and is
     * ignored); a restricted one (Android/data, obb, sandbox and below,
     * shouldPreemptivelyRestrictRequestedInitialUri :216-253) falls back to the
     * default location; a document that is not a directory opens at its parent
     * (AbstractActionHandler.onStackToLaunchToLoaded :838-842). "One of ours": the panel can only be
     * pointed at a place this provider can resolve, so another authority's URI,
     * or an id we never minted, is ignored -- as DocumentsUI ignores a URI it
     * cannot load (AbstractActionHandler :830-835). We have no roots, so a root URI is never ours.
     */
    static String initialDirFor(Intent real) {
        if (!INITIAL_URI || real == null) return null;
        Uri u = null;
        try { u = real.getParcelableExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.class); }
        catch (Throwable ignored) {}
        if (u == null) return null;
        File f = HostDocumentsProvider.fileForUri(u);
        if (f == null) return null;
        if (HostDocumentsProvider.restrictedInitial(f)) {
            NativeLog.w(TAG, "picker: EXTRA_INITIAL_URI " + u + " is restricted; default location");
            return null;
        }
        File dir = f.isDirectory() ? f : f.getParentFile();
        return dir != null && dir.isDirectory() ? dir.getAbsolutePath() : null;
    }

    /** Claim the caller's result synchronously (inside startActivityForResult's
     *  try), cover it like the full-screen DocumentsUI, and show the panel. */
    static boolean handle(Intent outer, Intent real, SpecialAccessHandler.Cover cover) {
        final Activity.MacApkHostResult token = Activity.macapkClaimHostResult(outer);
        final SpecialAccessHandler.Cover c = SpecialAccessHandler.RESULT_ORDER_AOSP ? cover : null;
        final String action = real.getAction();
        final boolean create = Intent.ACTION_CREATE_DOCUMENT.equals(action);
        final boolean tree = treeOn() && Intent.ACTION_OPEN_DOCUMENT_TREE.equals(action);
        // The photo picker's multi-select switch is EXTRA_PICK_IMAGES_MAX > 1
        // (MediaStore.EXTRA_PICK_IMAGES_MAX), not EXTRA_ALLOW_MULTIPLE.
        final boolean multiple = !tree && (real.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
            || (isPhotoPicker(real)
                && real.getIntExtra(android.provider.MediaStore.EXTRA_PICK_IMAGES_MAX, 1) > 1));
        final String[] acceptMimes = acceptMimes(real);
        if (acceptMimes == null) {
            // The photo picker refused the request (see acceptMimes): a cancel, delivered
            // through the same claim / cover / owner-post path as a dismissed panel.
            NativeLog.w(TAG, "picker: " + action + " has a non-media EXTRA_MIME_TYPES "
                    + "(PhotoPickerActivity finishes with no result) -- cancelled");
            long cid = sSeq.getAndIncrement();
            sPending.put(cid, new Pending(token, c, action, new String[] { "image/*", "video/*" }));
            if (c != null) c.cover();
            ActivityHost.macapkPostOnOwner(() -> finish(cid, null));
            return true;
        }
        final String initialDir = initialDirFor(real);
        String title = create ? real.getStringExtra(Intent.EXTRA_TITLE) : null;
        long id = sSeq.getAndIncrement();
        sPending.put(id, new Pending(token, c, action, acceptMimes));
        NativeLog.i(TAG, "picker: " + action + " types=" + java.util.Arrays.toString(acceptMimes) + " multiple=" + multiple
                + (title != null ? " title=" + title : "") + (initialDir != null ? " initial=" + initialDir : "")
                + " forResult=" + (token != null));
        if (c != null) c.cover();
        if (TEST_PATHS != null && !TEST_PATHS.isEmpty()) {
            NativeLog.w(TAG, "picker: MACAPK_DOCPICK_TEST_PATHS answers without a panel (harness seam)");
            final String[] paths = TEST_PATHS.split(":");
            ActivityHost.macapkPostOnOwner(() -> finish(id, paths));
            return true;
        }
        boolean shown = false;
        try {
            if (tree || initialDir != null) {
                int mode = tree ? MODE_TREE : create ? MODE_SAVE : MODE_OPEN;
                try {
                    shown = nativePickEx(mode, multiple, acceptMimes, title, initialDir, id);
                } catch (UnsatisfiedLinkError e) {
                    // A native side from before nativePickEx: the plain panel, minus
                    // the starting place; a tree pick has no older panel to fall to.
                    if (tree) throw e;
                    shown = nativePick(create, multiple, acceptMimes, title, id);
                }
            } else {
                shown = nativePick(create, multiple, acceptMimes, title, id);
            }
        } catch (Throwable t) {
            NativeLog.w(TAG, "picker: native panel unavailable (" + t + ") -- cancelled");
        }
        if (!shown) ActivityHost.macapkPostOnOwner(() -> finish(id, null));
        return true;
    }

    /** JNI, from the AppKit thread when the panel closes: the chosen paths, or
     *  null / empty when the user cancelled. */
    public static void macapkOnPicked(long id, String[] paths) {
        ActivityHost.macapkPostOnOwner(() -> finish(id, paths));
    }

    /** JNI, from the AppKit thread while the TREE panel is up, when the user
     *  presses the choose button: why DocumentsUI would refuse this directory
     *  (HostDocumentsProvider.blockedFromTree), or null to accept it. The panel
     *  shows DocumentsUI's message and stays open, as PickFragment keeps
     *  "Use this folder" disabled. Pure file checks; no looper, no locks. */
    public static String macapkTreeBlockReason(String path) {
        try {
            return path == null ? null : HostDocumentsProvider.blockedFromTree(new File(path));
        } catch (Throwable t) {
            return String.valueOf(t.getMessage());
        }
    }

    private static void finish(long id, String[] paths) {
        Pending p = sPending.remove(id);
        if (p == null) return;
        Intent data = resultData(p.action, p.acceptMimes, paths);
        if (data == null) {
            NativeLog.i(TAG, "picker: cancelled");
            deliver(p, Activity.RESULT_CANCELED, null);
            return;
        }
        deliver(p, Activity.RESULT_OK, data);
    }

    /** DocumentsUI's answer for the chosen paths: the result Intent, or null for
     *  RESULT_CANCELED (nothing chosen, or nothing usable). Pure apart from
     *  minting ids, recording the grant for the receiving app and, for
     *  CREATE_DOCUMENT, creating the named file. */
    static Intent resultData(String action, String[] acceptMimes, String[] paths) {
        if (treeOn() && Intent.ACTION_OPEN_DOCUMENT_TREE.equals(action)) {
            return treeResultData(paths);
        }
        final boolean create = Intent.ACTION_CREATE_DOCUMENT.equals(action);
        // GET_CONTENT hands out a READ grant only (onPickFinished :516-517), and
        // so does the photo picker (its result carries FLAG_GRANT_READ_URI_PERMISSION).
        final boolean grantWrite = !Intent.ACTION_GET_CONTENT.equals(action)
            && !(PHOTO_PICKER && android.provider.MediaStore.ACTION_PICK_IMAGES.equals(action));
        java.util.List<Uri> uris = new java.util.ArrayList<>();
        if (paths != null) {
            for (String path : paths) {
                if (path == null || path.isEmpty()) continue;
                File f = new File(path);
                if (create && !f.exists()) {
                    // NSSavePanel names the file; DocumentsUI's CREATE_DOCUMENT
                    // creates the (empty) document before answering.
                    try { f.createNewFile(); } catch (Throwable t) {
                        NativeLog.w(TAG, "picker: could not create " + f + ": " + t);
                        continue;
                    }
                }
                if (!f.isFile()) continue;
                uris.add(HostDocumentsProvider.mint(f, grantWrite));
            }
        }
        if (uris.isEmpty()) return null;
        Intent data = new Intent();
        if (uris.size() == 1) {
            data.setData(uris.get(0));
        } else {
            ClipData clip = new ClipData(null, acceptMimes, new ClipData.Item(uris.get(0)));
            for (int i = 1; i < uris.size(); i++) clip.addItem(new ClipData.Item(uris.get(i)));
            data.setClipData(clip);
        }
        data.addFlags(grantWrite
                ? Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                : Intent.FLAG_GRANT_READ_URI_PERMISSION);
        NativeLog.i(TAG, "picker: " + uris.size() + " document(s) -> " + uris.get(0)
                + (uris.size() > 1 ? " (+" + (uris.size() - 1) + ")" : ""));
        MacApkUriGrants.grantFromIntent(data);
        return data;
    }

    /** ACTION_OPEN_DOCUMENT_TREE: ONE directory. A path that is not a directory,
     *  or one DocumentsUI would not let the user choose (blockedFromTree), is no
     *  result: on a device the user can only back out of such a folder, so the
     *  app sees RESULT_CANCELED (the panel normally refuses it before this). */
    private static Intent treeResultData(String[] paths) {
        String path = null;
        if (paths != null) {
            for (String s : paths) if (s != null && !s.isEmpty()) { path = s; break; }
        }
        if (path == null) return null;
        File dir = new File(path);
        if (!dir.isDirectory()) {
            NativeLog.w(TAG, "picker: tree pick is not a directory: " + dir);
            return null;
        }
        String why = HostDocumentsProvider.blockedFromTree(dir);
        if (why != null) {
            NativeLog.w(TAG, "picker: DocumentsUI refuses " + dir + " as a tree (" + why + ")");
            return null;
        }
        String rootId;
        try { rootId = HostDocumentsProvider.mintTree(dir); }
        catch (Throwable t) {
            NativeLog.w(TAG, "picker: could not grant " + dir + ": " + t);
            return null;
        }
        Intent data = new Intent();
        data.setData(DocumentsContract.buildTreeDocumentUri(HostDocumentsProvider.AUTHORITY, rootId));
        data.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        NativeLog.i(TAG, "picker: tree -> " + data.getData());
        MacApkUriGrants.grantFromIntent(data);
        return data;
    }

    private static void deliver(Pending p, int resultCode, Intent data) {
        if (p.token == null) {
            if (p.cover != null) p.cover.uncover();
            return;
        }
        if (p.cover != null) {
            SpecialAccessHandler.finishWithResult(p.cover, p.token, resultCode, data);
        } else {
            p.token.deliver(resultCode, data);
        }
    }

    /** The macOS panel (runtime/src/document_picker.mm), bound softly: a runtime
     *  whose native side lacks it throws UnsatisfiedLinkError, which handle()
     *  turns into a cancel. Asynchronous: returns once the panel is scheduled;
     *  the answer arrives through macapkOnPicked. */
    private static native boolean nativePick(boolean save, boolean multiple, String[] mimeTypes,
                                             String suggestedName, long id);

    /** The same panel with a mode (MODE_OPEN / MODE_SAVE / MODE_TREE) and a
     *  starting directory (null = where it last was). Registered separately and
     *  softly, so an older native side still serves nativePick. */
    private static native boolean nativePickEx(int mode, boolean multiple, String[] mimeTypes,
                                               String suggestedName, String initialDir, long id);
}
