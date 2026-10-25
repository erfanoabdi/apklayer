// android.drm.DrmInfoEvent -- an informational DRM event (rights installed,
// rights removed, waiting for rights, account already registered ...).
// Delivered to DrmManagerClient.OnInfoListener.
//
// Mirrors frameworks/base/drm/java/android/drm/DrmInfoEvent.java
// (android16-release). Unlike DrmEvent's, these constructors are PUBLIC --
// current.txt publishes both -- so an app can construct one, typically in its
// own tests.
package android.drm;

import java.util.HashMap;

@Deprecated
public class DrmInfoEvent extends DrmEvent {
    public static final int TYPE_ALREADY_REGISTERED_BY_ANOTHER_ACCOUNT = 1;
    public static final int TYPE_REMOVE_RIGHTS = 2;
    public static final int TYPE_RIGHTS_INSTALLED = 3;
    public static final int TYPE_WAIT_FOR_RIGHTS = 4;
    public static final int TYPE_ACCOUNT_ALREADY_REGISTERED = 5;
    public static final int TYPE_RIGHTS_REMOVED = 6;

    public DrmInfoEvent(int uniqueId, int type, String message) {
        super(uniqueId, type, message);
    }

    public DrmInfoEvent(int uniqueId, int type, String message,
            HashMap<String, Object> attributes) {
        super(uniqueId, type, message, attributes);
    }
}
