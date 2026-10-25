// android.drm.DrmRights -- a rights object an app has obtained, on its way to
// DrmManagerClient.saveRights.
//
// Mirrors frameworks/base/drm/java/android/drm/DrmRights.java (android16-release).
//
// SCOPE NOTE: this class only CARRIES a rights blob the app already has -- it
// reads a file the app names, keeps the bytes, and exposes them. It evaluates
// nothing, decrypts nothing, and grants nothing; whether those rights are ever
// honoured is the plug-in's question, and Mac.apk has no plug-in (saveRights
// answers ERROR_UNKNOWN). Implementing the carrier is interoperability;
// implementing the evaluation would not be, and is not done.
package android.drm;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

@Deprecated
public class DrmRights {
    private byte[] mData;
    private String mMimeType;
    private String mAccountId;
    private String mSubscriptionId;

    public DrmRights(String rightsFilePath, String mimeType) {
        instantiate(new File(rightsFilePath), mimeType);
    }

    public DrmRights(String rightsFilePath, String mimeType, String accountId) {
        this(rightsFilePath, mimeType);
        mAccountId = accountId;
    }

    public DrmRights(String rightsFilePath, String mimeType, String accountId,
            String subscriptionId) {
        this(rightsFilePath, mimeType);
        mAccountId = accountId;
        mSubscriptionId = subscriptionId;
    }

    public DrmRights(File rightsFile, String mimeType) {
        instantiate(rightsFile, mimeType);
    }

    private void instantiate(File rightsFile, String mimeType) {
        try {
            mData = DrmUtils.readBytes(rightsFile);
        } catch (IOException e) {
            // AOSP prints the stack trace and carries on to the validity check
            // below, which then throws IllegalArgumentException. Kept: the
            // exception type an app catches must not change.
            e.printStackTrace();
        }
        mMimeType = mimeType;
        if (!isValid()) {
            throw new IllegalArgumentException("mimeType: " + mMimeType + ","
                    + "data: " + Arrays.toString(mData));
        }
    }

    public DrmRights(ProcessedData data, String mimeType) {
        if (data == null) {
            throw new IllegalArgumentException("data is null");
        }
        mData = data.getData();
        mAccountId = data.getAccountId();
        mSubscriptionId = data.getSubscriptionId();
        mMimeType = mimeType;
        if (!isValid()) {
            throw new IllegalArgumentException("mimeType: " + mMimeType + ","
                    + "data: " + Arrays.toString(mData));
        }
    }

    public byte[] getData() { return mData; }
    public String getMimeType() { return mMimeType; }
    public String getAccountId() { return mAccountId; }
    public String getSubscriptionId() { return mSubscriptionId; }

    /* package */ boolean isValid() {
        return (null != mMimeType && !mMimeType.equals("")
                && null != mData && mData.length > 0);
    }
}
