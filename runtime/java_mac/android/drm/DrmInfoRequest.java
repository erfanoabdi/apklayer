// android.drm.DrmInfoRequest -- an app's request for DRM information:
// register a device, unregister it, acquire rights, or ask how a rights
// acquisition is progressing. Handed to DrmManagerClient.acquireDrmInfo /
// acquireRights.
//
// Mirrors frameworks/base/drm/java/android/drm/DrmInfoRequest.java
// (android16-release). A pure request carrier, so it is faithfully
// implemented -- including the constructor's validity check, which throws
// IllegalArgumentException for an unknown info type or an empty MIME type
// before any plug-in is consulted.
package android.drm;

import java.util.HashMap;
import java.util.Iterator;

@Deprecated
public class DrmInfoRequest {
    public static final int TYPE_REGISTRATION_INFO = 1;
    public static final int TYPE_UNREGISTRATION_INFO = 2;
    public static final int TYPE_RIGHTS_ACQUISITION_INFO = 3;
    public static final int TYPE_RIGHTS_ACQUISITION_PROGRESS_INFO = 4;

    public static final String ACCOUNT_ID = "account_id";
    public static final String SUBSCRIPTION_ID = "subscription_id";

    private final int mInfoType;
    private final String mMimeType;
    private final HashMap<String, Object> mRequestInformation = new HashMap<String, Object>();

    public DrmInfoRequest(int infoType, String mimeType) {
        mInfoType = infoType;
        mMimeType = mimeType;
        if (!isValid()) {
            throw new IllegalArgumentException(
                    "infoType: " + infoType + "," + "mimeType: " + mimeType);
        }
    }

    public String getMimeType() { return mMimeType; }
    public int getInfoType() { return mInfoType; }
    public void put(String key, Object value) { mRequestInformation.put(key, value); }
    public Object get(String key) { return mRequestInformation.get(key); }
    public Iterator<String> keyIterator() { return mRequestInformation.keySet().iterator(); }
    public Iterator<Object> iterator() { return mRequestInformation.values().iterator(); }

    /* package */ boolean isValid() {
        return (null != mMimeType && !mMimeType.equals("")
                && null != mRequestInformation && isValidType(mInfoType));
    }

    /* package */ static boolean isValidType(int infoType) {
        switch (infoType) {
        case TYPE_REGISTRATION_INFO:
        case TYPE_UNREGISTRATION_INFO:
        case TYPE_RIGHTS_ACQUISITION_INFO:
        case TYPE_RIGHTS_ACQUISITION_PROGRESS_INFO:
            return true;
        }
        return false;
    }
}
