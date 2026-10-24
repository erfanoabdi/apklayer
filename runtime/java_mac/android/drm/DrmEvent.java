// android.drm.DrmEvent -- the base event the legacy DRM framework delivers to
// an app's OnEventListener. DrmInfoEvent and DrmErrorEvent extend it.
//
// Mirrors frameworks/base/drm/java/android/drm/DrmEvent.java (android16-release).
// Both constructors are PROTECTED in AOSP and stay protected here: an app
// subclasses this to mint its own events, it does not construct one directly.
//
// Mac.apk never delivers one -- there is no plug-in to raise an event -- so
// these types exist so an app's listener implementation resolves and its
// event-handling code compiles and links.
package android.drm;

import java.util.HashMap;

@Deprecated
public class DrmEvent {
    // AOSP comment, kept because it is a real constraint on this file: "Please
    // do not add type constants in this class. More event type constants should
    // go to DrmInfoEvent or DrmErrorEvent classes."
    public static final int TYPE_ALL_RIGHTS_REMOVED = 1001;
    public static final int TYPE_DRM_INFO_PROCESSED = 1002;

    public static final String DRM_INFO_STATUS_OBJECT = "drm_info_status_object";
    public static final String DRM_INFO_OBJECT = "drm_info_object";

    private final int mUniqueId;
    private final int mType;
    private String mMessage = "";
    private HashMap<String, Object> mAttributes = new HashMap<String, Object>();

    protected DrmEvent(int uniqueId, int type, String message,
            HashMap<String, Object> attributes) {
        mUniqueId = uniqueId;
        mType = type;
        if (null != message) mMessage = message;
        if (null != attributes) mAttributes = attributes;
    }

    protected DrmEvent(int uniqueId, int type, String message) {
        mUniqueId = uniqueId;
        mType = type;
        if (null != message) mMessage = message;
    }

    public int getUniqueId() { return mUniqueId; }
    public int getType() { return mType; }
    public String getMessage() { return mMessage; }
    public Object getAttribute(String key) { return mAttributes.get(key); }
}
