// android.drm.DrmManagerClient -- the LEGACY (pre-MediaDrm) DRM framework's
// entry point. An app asks it whether a file is protected, what its real MIME
// type is, whether rights exist, and (on a device with a plug-in) to acquire
// or apply those rights.
//
// Mirrors frameworks/base/drm/java/android/drm/DrmManagerClient.java
// (android16-release).
//
// =============================================================================
// WHAT THIS DEVICE REPORTS, AND WHY -- read before changing any return value
// =============================================================================
// Mac.apk has NO DRM plug-in. That is not a gap being papered over: it is a
// state real Android devices are in whenever no engine is installed that
// claims the content, and AOSP has a documented answer for every entry point
// in that state. Those answers are what this file returns:
//
//   canHandle                  false   -- no engine claims any MIME type
//   getAvailableDrmEngines     empty   -- derived from the support-info list
//   getAvailableDrmSupportInfo empty   -- @NonNull, so empty, never null
//   checkRightsStatus          RIGHTS_NOT_ACQUIRED
//   getConstraints/getMetadata null    -- AOSP: "Null in case of failure"
//   getOriginalMimeType        null    -- nothing can un-wrap the container
//   getDrmObjectType           UNKNOWN
//   acquireDrmInfo             null    -- AOSP's failure return
//   acquireRights/processDrmInfo/removeRights/removeAllRights/saveRights
//                              ERROR_UNKNOWN
//   openConvertSession         INVALID_SESSION (-1)
//   convertData/closeConvertSession  DrmConvertedStatus.STATUS_ERROR
//
// ⚠ checkRightsStatus USED TO RETURN 0, WHICH IS RIGHTS_VALID. That was wrong
// in the single most dangerous direction available in this API: 0 is
// DrmStore.RightsStatus.RIGHTS_VALID, i.e. "this device holds valid rights to
// this protected content", asserted about a file we cannot evaluate at all
// because we have no engine to evaluate it with. An app that gates playback on
// checkRightsStatus == RIGHTS_VALID -- which is the documented idiom -- was
// being told to proceed. Reporting RIGHTS_NOT_ACQUIRED (3) is both the honest
// answer for a device with no plug-in and the only one consistent with this
// project's rule that we do not defeat licensing or copy protection.
//
// SCOPE, EXPLICITLY. Implementing this API SURFACE is interoperability work:
// an app that links against android.drm now loads, runs, and can ask its
// questions. Answering those questions in a way that unlocks protected content
// is not, and is not done here. Concretely, the following are DELIBERATELY not
// implemented and will not be:
//   * the convert session (openConvertSession/convertData/closeConvertSession)
//     -- the transform that turns protected bytes into playable bytes;
//   * any evaluation of a DrmRights blob that would let saveRights report
//     success and thereby claim rights were installed;
//   * any answer other than the plug-in-absent one above, in particular any
//     RIGHTS_VALID that we did not verify (we cannot verify one at all).
// If a future change wants a different answer here, the question to settle
// first is not "what makes the app happy" but "what does this device actually
// know", and with no plug-in the answer to that does not change.
//
// The ARGUMENT VALIDATION below is AOSP's, kept in AOSP's order, because it is
// the part of this class that IS fully observable on any device: an app that
// passes an empty path or an invalid DrmStore.Action gets
// IllegalArgumentException on Android and gets it here too, before any of the
// answers above is reached.
package android.drm;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

@Deprecated
public class DrmManagerClient implements AutoCloseable {

    public static final int ERROR_NONE = 0;
    public static final int ERROR_UNKNOWN = -2000;

    /** AOSP DrmManagerClient.java:64. Returned by openConvertSession when no
     *  engine will take the conversion -- which is always, here. */
    public static final int INVALID_SESSION = -1;

    /** AOSP's listener types. They are stored and never invoked: no plug-in
     *  means no asynchronous DRM activity, so there is no event to deliver.
     *  That is different from dropping the setters -- an app that registers a
     *  listener and waits for an event would hang either way, but an app that
     *  cannot even CALL setOnEventListener fails at link time on a code path it
     *  may never have needed. */
    public interface OnInfoListener {
        public void onInfo(DrmManagerClient client, DrmInfoEvent event);
    }
    public interface OnEventListener {
        public void onEvent(DrmManagerClient client, DrmEvent event);
    }
    public interface OnErrorListener {
        public void onError(DrmManagerClient client, DrmErrorEvent event);
    }

    private final Context mContext;
    private volatile boolean mClosed;

    private OnInfoListener mOnInfoListener;
    private OnEventListener mOnEventListener;
    private OnErrorListener mOnErrorListener;

    public DrmManagerClient(Context context) {
        mContext = context;
    }

    public void setOnInfoListener(OnInfoListener infoListener) {
        mOnInfoListener = infoListener;
    }

    public void setOnEventListener(OnEventListener eventListener) {
        mOnEventListener = eventListener;
    }

    public void setOnErrorListener(OnErrorListener errorListener) {
        mOnErrorListener = errorListener;
    }

    /** AOSP :365 derives this from the support-info list, so with no plug-ins
     *  it is an empty array rather than null. */
    public String[] getAvailableDrmEngines() {
        return new String[0];
    }

    /** AOSP :384. Declared @NonNull, so the "nothing installed" answer is an
     *  empty collection. */
    public Collection<DrmSupportInfo> getAvailableDrmSupportInfo() {
        return Collections.emptyList();
    }

    // ---- canHandle -------------------------------------------------------

    public boolean canHandle(String path, String mimeType) {
        if ((null == path || path.equals("")) && (null == mimeType || mimeType.equals(""))) {
            throw new IllegalArgumentException("Path or the mimetype should be non null");
        }
        return false;
    }

    public boolean canHandle(Uri uri, String mimeType) {
        if ((null == uri || Uri.EMPTY == uri) && (null == mimeType || mimeType.equals(""))) {
            throw new IllegalArgumentException("Uri or the mimetype should be non null");
        }
        return canHandle(convertUriToPath(uri), mimeType);
    }

    // ---- rights status ---------------------------------------------------

    public int checkRightsStatus(String path) {
        return checkRightsStatus(path, DrmStore.Action.DEFAULT);
    }

    public int checkRightsStatus(Uri uri) {
        if (null == uri || Uri.EMPTY == uri) {
            throw new IllegalArgumentException("Given uri is not valid");
        }
        return checkRightsStatus(convertUriToPath(uri));
    }

    /** See the header: RIGHTS_NOT_ACQUIRED, never RIGHTS_VALID. */
    public int checkRightsStatus(String path, int action) {
        if (null == path || path.equals("") || !DrmStore.Action.isValid(action)) {
            throw new IllegalArgumentException("Given path or action is not valid");
        }
        return DrmStore.RightsStatus.RIGHTS_NOT_ACQUIRED;
    }

    public int checkRightsStatus(Uri uri, int action) {
        if (null == uri || Uri.EMPTY == uri) {
            throw new IllegalArgumentException("Given uri is not valid");
        }
        return checkRightsStatus(convertUriToPath(uri), action);
    }

    // ---- constraints / metadata -----------------------------------------

    public ContentValues getConstraints(String path, int action) {
        if (null == path || path.equals("") || !DrmStore.Action.isValid(action)) {
            throw new IllegalArgumentException("Given usage or path is invalid/null");
        }
        return null;
    }

    public ContentValues getConstraints(Uri uri, int action) {
        if (null == uri || Uri.EMPTY == uri) {
            throw new IllegalArgumentException("Uri should be non null");
        }
        return getConstraints(convertUriToPath(uri), action);
    }

    public ContentValues getMetadata(String path) {
        if (null == path || path.equals("")) {
            throw new IllegalArgumentException("Given path is invalid/null");
        }
        return null;
    }

    public ContentValues getMetadata(Uri uri) {
        if (null == uri || Uri.EMPTY == uri) {
            throw new IllegalArgumentException("Uri should be non null");
        }
        return getMetadata(convertUriToPath(uri));
    }

    // ---- object type / original mime type --------------------------------

    public int getDrmObjectType(String path, String mimeType) {
        if ((null == path || path.equals("")) && (null == mimeType || mimeType.equals(""))) {
            throw new IllegalArgumentException("Path or the mimetype should be non null");
        }
        return DrmStore.DrmObjectType.UNKNOWN;
    }

    public int getDrmObjectType(Uri uri, String mimeType) {
        if ((null == uri || Uri.EMPTY == uri) && (null == mimeType || mimeType.equals(""))) {
            throw new IllegalArgumentException("Uri or the mimetype should be non null");
        }
        String path = "";
        try {
            path = convertUriToPath(uri);
        } catch (Exception e) {
            // AOSP: "Even uri is invalid the mimetype shall be valid, so allow
            // to proceed further."
        }
        return getDrmObjectType(path, mimeType);
    }

    /** AOSP opens the file to hand the plug-in an fd alongside the path, then
     *  returns whatever the plug-in says the wrapped content really is. With no
     *  plug-in there is nothing to un-wrap, and AOSP's own answer in that case
     *  is null -- so the file is not opened at all here. Note AOSP returns null
     *  rather than throwing for a nonexistent path too. */
    public String getOriginalMimeType(String path) {
        if (null == path || path.equals("")) {
            throw new IllegalArgumentException("Given path should be non null");
        }
        return null;
    }

    public String getOriginalMimeType(Uri uri) {
        if (null == uri || Uri.EMPTY == uri) {
            throw new IllegalArgumentException("Given uri is not valid");
        }
        return getOriginalMimeType(convertUriToPath(uri));
    }

    // ---- rights acquisition ----------------------------------------------

    /** AOSP :545. Returns null on failure, which is the state here: no engine
     *  can produce DrmInfo for the request. */
    public DrmInfo acquireDrmInfo(DrmInfoRequest drmInfoRequest) {
        if (null == drmInfoRequest || !drmInfoRequest.isValid()) {
            throw new IllegalArgumentException("Given drmInfoRequest is invalid/null");
        }
        return null;
    }

    /** AOSP :552 is literally `acquireDrmInfo` then `processDrmInfo`, with
     *  ERROR_UNKNOWN when the first step yields null. That composition is kept
     *  rather than short-circuited, so the argument validation an app relies on
     *  still runs in AOSP's order. */
    public int acquireRights(DrmInfoRequest drmInfoRequest) {
        DrmInfo drmInfo = acquireDrmInfo(drmInfoRequest);
        if (null == drmInfo) {
            return ERROR_UNKNOWN;
        }
        return processDrmInfo(drmInfo);
    }

    /** AOSP :528 posts the work to its event thread and reports ERROR_UNKNOWN
     *  when there is no handler to post to -- which is the shape here: nothing
     *  can process the info, so ERROR_UNKNOWN. */
    public int processDrmInfo(DrmInfo drmInfo) {
        if (null == drmInfo || !drmInfo.isValid()) {
            throw new IllegalArgumentException("Given drmInfo is invalid/null");
        }
        return ERROR_UNKNOWN;
    }

    /** AOSP :466 writes the rights blob to `rightsPath` when one is given, then
     *  asks the plug-in to install it. The file write is DELIBERATELY not
     *  performed here: without an engine the install can only fail, and writing
     *  the blob to disk first would leave an app believing rights had been
     *  staged. The IllegalArgumentException for invalid rights is AOSP's and
     *  still fires. Declared `throws IOException` to match AOSP's signature --
     *  an app's catch block must still compile. */
    public int saveRights(DrmRights drmRights, String rightsPath, String contentPath)
            throws IOException {
        if (null == drmRights || !drmRights.isValid()) {
            throw new IllegalArgumentException("Given drmRights or contentPath is not valid");
        }
        return ERROR_UNKNOWN;
    }

    public int removeRights(String path) {
        if (null == path || path.equals("")) {
            throw new IllegalArgumentException("Given path should be non null");
        }
        return ERROR_UNKNOWN;
    }

    public int removeRights(Uri uri) {
        if (null == uri || Uri.EMPTY == uri) {
            throw new IllegalArgumentException("Given uri is not valid");
        }
        return removeRights(convertUriToPath(uri));
    }

    /** AOSP :758 reports ERROR_UNKNOWN when it has no event handler to post the
     *  removal to. No rights are held, so there is nothing to remove and
     *  nothing to post. */
    public int removeAllRights() {
        return ERROR_UNKNOWN;
    }

    // ---- convert session (NOT implemented -- see the scope note in the header)

    public int openConvertSession(String mimeType) {
        if (null == mimeType || mimeType.equals("")) {
            throw new IllegalArgumentException("Path or the mimeType should be non null");
        }
        return INVALID_SESSION;
    }

    /** STATUS_ERROR carries no converted data, which is why convertedData is
     *  null and offset 0 -- DrmConvertedStatus permits that combination for a
     *  non-OK status. */
    public DrmConvertedStatus convertData(int convertId, byte[] inputData) {
        if (null == inputData || 0 >= inputData.length) {
            throw new IllegalArgumentException("Given inputData should be non null");
        }
        return new DrmConvertedStatus(DrmConvertedStatus.STATUS_ERROR, null, 0);
    }

    public DrmConvertedStatus closeConvertSession(int convertId) {
        return new DrmConvertedStatus(DrmConvertedStatus.STATUS_ERROR, null, 0);
    }

    // ---- lifecycle -------------------------------------------------------

    /** AOSP marks release() deprecated in favour of close(); both exist. */
    public void release() {
        mClosed = true;
    }

    @Override public void close() {
        release();
    }

    // ---- helpers ---------------------------------------------------------

    /** AOSP :810 convertUriToPath. Mirrored including the exception messages,
     *  because getDrmObjectType(Uri, String) CATCHES what this throws and
     *  proceeds, so the throw/no-throw boundary is behaviour an app can see.
     *
     *  The content:// branch really does query MediaStore here rather than
     *  giving up -- resolving a content Uri to a file path is ordinary
     *  ContentResolver work with nothing DRM-specific about it, and an app that
     *  hands us a media-store Uri should get the same IllegalArgumentException
     *  (or not) that it gets on a device. */
    private String convertUriToPath(Uri uri) {
        String path = null;
        if (null != uri) {
            String scheme = uri.getScheme();
            if (null == scheme || scheme.equals("")
                    || scheme.equals(android.content.ContentResolver.SCHEME_FILE)) {
                path = uri.getPath();

            } else if (scheme.equals("http") || scheme.equals("https")) {
                path = uri.toString();

            } else if (scheme.equals(android.content.ContentResolver.SCHEME_CONTENT)) {
                String[] projection = new String[] { MediaStore.MediaColumns.DATA };
                Cursor cursor = null;
                try {
                    if (null != mContext && null != mContext.getContentResolver()) {
                        cursor = mContext.getContentResolver()
                                .query(uri, projection, null, null, null);
                    }
                    if (null == cursor || 0 == cursor.getCount() || !cursor.moveToFirst()) {
                        throw new IllegalArgumentException(
                                "Given Uri could not be found in media store");
                    }
                    int pathIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
                    path = cursor.getString(pathIndex);
                } finally {
                    if (null != cursor) cursor.close();
                }

            } else {
                throw new IllegalArgumentException("Given Uri scheme is not supported");
            }
        }
        return path;
    }
}
