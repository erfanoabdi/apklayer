// android.drm.DrmErrorEvent -- a DRM failure event (rights not installed,
// not supported, no internet connection, out of memory ...). Delivered to
// DrmManagerClient.OnErrorListener.
//
// Mirrors frameworks/base/drm/java/android/drm/DrmErrorEvent.java
// (android16-release).
//
// TYPE_NOT_SUPPORTED (2003) is the code that describes this device honestly:
// no DRM plug-in is installed, so nothing can be processed. Nothing here EVER
// raises it, though -- see the listener note in DrmManagerClient. Every entry
// point that could fail does so through its documented RETURN value
// (ERROR_UNKNOWN, a null, a STATUS_ERROR) rather than by inventing an
// asynchronous error the app did not schedule.
package android.drm;

import java.util.HashMap;

@Deprecated
public class DrmErrorEvent extends DrmEvent {
    public static final int TYPE_RIGHTS_NOT_INSTALLED = 2001;
    public static final int TYPE_RIGHTS_RENEWAL_NOT_ALLOWED = 2002;
    public static final int TYPE_NOT_SUPPORTED = 2003;
    public static final int TYPE_OUT_OF_MEMORY = 2004;
    public static final int TYPE_NO_INTERNET_CONNECTION = 2005;
    public static final int TYPE_PROCESS_DRM_INFO_FAILED = 2006;
    public static final int TYPE_REMOVE_ALL_RIGHTS_FAILED = 2007;
    public static final int TYPE_ACQUIRE_DRM_INFO_FAILED = 2008;

    public DrmErrorEvent(int uniqueId, int type, String message) {
        super(uniqueId, type, message);
    }

    public DrmErrorEvent(int uniqueId, int type, String message,
            HashMap<String, Object> attributes) {
        super(uniqueId, type, message, attributes);
    }
}
