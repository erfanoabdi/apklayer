// android.drm.DrmConvertedStatus -- the result of one step of a DRM CONVERT
// session (openConvertSession / convertData / closeConvertSession).
//
// Mirrors frameworks/base/drm/java/android/drm/DrmConvertedStatus.java
// (android16-release). A pure result carrier; the constructor's status-code
// validation is AOSP's and is observable.
//
// SCOPE NOTE: the convert session is the legacy framework's transform path
// (the OMA "forward lock" / .fl flow) -- the operation that turns protected
// bytes into bytes a player can consume. Mac.apk has no plug-in and does not
// implement it: DrmManagerClient.convertData/closeConvertSession answer
// STATUS_ERROR, which is what a real device with no matching plug-in answers.
// This class carries that answer; it never performs a conversion.
package android.drm;

@Deprecated
public class DrmConvertedStatus {
    public static final int STATUS_OK = 1;
    public static final int STATUS_INPUTDATA_ERROR = 2;
    public static final int STATUS_ERROR = 3;

    public final int statusCode;
    public final byte[] convertedData;
    public final int offset;

    public DrmConvertedStatus(int statusCode, byte[] convertedData, int offset) {
        if (!isValidStatusCode(statusCode)) {
            throw new IllegalArgumentException("Unsupported status code: " + statusCode);
        }
        this.statusCode = statusCode;
        this.convertedData = convertedData;
        this.offset = offset;
    }

    private boolean isValidStatusCode(int statusCode) {
        return statusCode == STATUS_OK
                || statusCode == STATUS_INPUTDATA_ERROR
                || statusCode == STATUS_ERROR;
    }
}
