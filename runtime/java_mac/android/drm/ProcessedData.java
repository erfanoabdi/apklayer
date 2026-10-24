// android.drm.ProcessedData -- the payload a DRM plug-in hands back after it
// processes a DrmInfo (typically the rights blob plus the account it is bound
// to). Constructed only by the framework, never by an app: AOSP's two
// constructors are package-private and current.txt publishes only the getters.
//
// Mirrors frameworks/base/drm/java/android/drm/ProcessedData.java
// (android16-release), including the "_NO_USER" account-id default.
//
// Nothing in Mac.apk ever produces one -- there is no DRM plug-in to process
// anything -- so this type exists so that DrmRights(ProcessedData, String) and
// DrmInfoStatus.data resolve.
package android.drm;

@Deprecated
public class ProcessedData {
    private final byte[] mData;
    private String mAccountId = "_NO_USER";
    private String mSubscriptionId = "";

    /* package */ ProcessedData(byte[] data, String accountId) {
        mData = data;
        mAccountId = accountId;
    }

    /* package */ ProcessedData(byte[] data, String accountId, String subscriptionId) {
        mData = data;
        mAccountId = accountId;
        mSubscriptionId = subscriptionId;
    }

    public byte[] getData() { return mData; }
    public String getAccountId() { return mAccountId; }
    public String getSubscriptionId() { return mSubscriptionId; }
}
