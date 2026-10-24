// android.drm.DrmInfo -- the information a DRM plug-in returns from
// acquireDrmInfo, or that an app builds to hand to processDrmInfo. It carries
// an opaque byte payload plus a bag of attributes.
//
// Mirrors frameworks/base/drm/java/android/drm/DrmInfo.java (android16-release).
// A pure data carrier -- it interprets nothing about the payload -- so it is
// implemented faithfully, validity check and all: AOSP throws
// IllegalArgumentException for a null/empty payload or an unknown info type,
// and an app relies on that to catch its own mistakes.
//
// The (int, String path, String) constructor reads the payload off disk, which
// is the app's own file, not protected content.
package android.drm;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;

@Deprecated
public class DrmInfo {
    private byte[] mData;
    private final String mMimeType;
    private final int mInfoType;
    private final HashMap<String, Object> mAttributes = new HashMap<String, Object>();

    public DrmInfo(int infoType, byte[] data, String mimeType) {
        mInfoType = infoType;
        mMimeType = mimeType;
        mData = data;
        if (!isValid()) {
            throw new IllegalArgumentException("infoType: " + infoType + ","
                    + "mimeType: " + mimeType + "," + "data: " + Arrays.toString(data));
        }
    }

    public DrmInfo(int infoType, String path, String mimeType) {
        mInfoType = infoType;
        mMimeType = mimeType;
        try {
            mData = DrmUtils.readBytes(path);
        } catch (IOException e) {
            mData = null;
        }
        if (!isValid()) {
            // AOSP builds a message here and then throws the NO-ARG
            // IllegalArgumentException, discarding it. Mirrored, because the
            // absence of a message is what an app's logs will show on a device.
            throw new IllegalArgumentException();
        }
    }

    public void put(String key, Object value) { mAttributes.put(key, value); }
    public Object get(String key) { return mAttributes.get(key); }
    public Iterator<String> keyIterator() { return mAttributes.keySet().iterator(); }
    public Iterator<Object> iterator() { return mAttributes.values().iterator(); }
    public byte[] getData() { return mData; }
    public String getMimeType() { return mMimeType; }
    public int getInfoType() { return mInfoType; }

    /* package */ boolean isValid() {
        return (null != mMimeType && !mMimeType.equals("")
                && null != mData && mData.length > 0
                && DrmInfoRequest.isValidType(mInfoType));
    }
}
