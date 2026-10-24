// android.drm.DrmInfoStatus -- the outcome of processing a DrmInfo, delivered
// as the DrmEvent.DRM_INFO_STATUS_OBJECT attribute of a TYPE_DRM_INFO_PROCESSED
// event.
//
// Mirrors frameworks/base/drm/java/android/drm/DrmInfoStatus.java
// (android16-release). The three constructor checks are AOSP's, in AOSP's
// order (info type, then status code, then MIME type), because which one fires
// first is observable in the exception message.
//
// Nothing here ever constructs one: no plug-in processes DrmInfo, so no event
// carrying a status is ever delivered.
package android.drm;

@Deprecated
public class DrmInfoStatus {
    public static final int STATUS_OK = 1;
    public static final int STATUS_ERROR = 2;

    public final int statusCode;
    public final int infoType;
    public final String mimeType;
    public final ProcessedData data;

    public DrmInfoStatus(int statusCode, int infoType, ProcessedData data, String mimeType) {
        if (!DrmInfoRequest.isValidType(infoType)) {
            throw new IllegalArgumentException("infoType: " + infoType);
        }
        if (!isValidStatusCode(statusCode)) {
            throw new IllegalArgumentException("Unsupported status code: " + statusCode);
        }
        // AOSP's `mimeType == ""` reference comparison, kept verbatim -- see
        // the same note in DrmSupportInfo.
        if (mimeType == null || mimeType == "") {
            throw new IllegalArgumentException("mimeType is null or an empty string");
        }
        this.statusCode = statusCode;
        this.infoType = infoType;
        this.data = data;
        this.mimeType = mimeType;
    }

    private boolean isValidStatusCode(int statusCode) {
        return statusCode == STATUS_OK || statusCode == STATUS_ERROR;
    }
}
