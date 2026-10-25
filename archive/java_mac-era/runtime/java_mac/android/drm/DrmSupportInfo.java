// android.drm.DrmSupportInfo -- what one DRM plug-in claims it can handle:
// a set of MIME types, a set of file suffixes, and a human-readable
// description.
//
// Mirrors frameworks/base/drm/java/android/drm/DrmSupportInfo.java
// (android16-release), including the misspelled getDescriprition() -- that
// typo is published API (current.txt carries BOTH getDescriprition and
// getDescription) and an app compiled against it would NoSuchMethodError
// without it.
//
// Mac.apk has no DRM plug-in, so DrmManagerClient.getAvailableDrmSupportInfo()
// returns an EMPTY collection and nothing here is ever populated by the
// framework. The class is fully implemented anyway because an app may build
// one itself to describe what IT supports, and because AOSP's argument
// validation is observable.
package android.drm;

import java.util.ArrayList;
import java.util.Iterator;

@Deprecated
public class DrmSupportInfo {
    private final ArrayList<String> mFileSuffixList = new ArrayList<String>();
    private final ArrayList<String> mMimeTypeList = new ArrayList<String>();
    private String mDescription = "";

    public void addMimeType(String mimeType) {
        if (mimeType == null) {
            throw new IllegalArgumentException("mimeType is null");
        }
        // AOSP compares with == against the "" literal, which only catches an
        // interned empty literal, not a computed empty string. Mirrored exactly
        // rather than "fixed": tightening it to .isEmpty() would reject inputs
        // a device accepts, which is a behaviour change an app can see.
        if (mimeType == "") {
            throw new IllegalArgumentException("mimeType is an empty string");
        }
        mMimeTypeList.add(mimeType);
    }

    public void addFileSuffix(String fileSuffix) {
        // AOSP does NOT null-check this one, unlike addMimeType. Same shape kept.
        if (fileSuffix == "") {
            throw new IllegalArgumentException("fileSuffix is an empty string");
        }
        mFileSuffixList.add(fileSuffix);
    }

    public Iterator<String> getMimeTypeIterator() { return mMimeTypeList.iterator(); }
    public Iterator<String> getFileSuffixIterator() { return mFileSuffixList.iterator(); }

    public void setDescription(String description) {
        if (description == null) {
            throw new IllegalArgumentException("description is null");
        }
        if (description == "") {
            throw new IllegalArgumentException("description is an empty string");
        }
        mDescription = description;
    }

    /** AOSP's misspelling, kept because it is published API. */
    public String getDescriprition() { return mDescription; }

    public String getDescription() { return mDescription; }

    @Override public int hashCode() {
        return mFileSuffixList.hashCode() + mMimeTypeList.hashCode() + mDescription.hashCode();
    }

    @Override public boolean equals(Object object) {
        if (object instanceof DrmSupportInfo) {
            DrmSupportInfo info = (DrmSupportInfo) object;
            return mFileSuffixList.equals(info.mFileSuffixList)
                    && mMimeTypeList.equals(info.mMimeTypeList)
                    && mDescription.equals(info.mDescription);
        }
        return false;
    }

    /* package */ boolean isSupportedMimeType(String mimeType) {
        if (null != mimeType && !mimeType.equals("")) {
            for (int i = 0; i < mMimeTypeList.size(); i++) {
                if (mMimeTypeList.get(i).startsWith(mimeType)) return true;
            }
        }
        return false;
    }

    /* package */ boolean isSupportedFileSuffix(String fileSuffix) {
        return mFileSuffixList.contains(fileSuffix);
    }
}
