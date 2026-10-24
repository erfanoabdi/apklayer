// android.app.role.RoleManager
//
// AOSP's role service (default browser/SMS/dialer/etc.). Apps fetch it via
// getSystemService(ROLE_SERVICE) to check whether they hold a role or to request
// one. Mac.apk has no role assignment, so the app holds no roles and roles are
// not available -- the honest "no role system" result. Mirrors
// frameworks/base/core/java/android/app/role/RoleManager.java.
package android.app.role;

import android.content.Context;
import android.content.Intent;

public final class RoleManager {

    public static final String ROLE_BROWSER = "android.app.role.BROWSER";
    public static final String ROLE_SMS     = "android.app.role.SMS";
    public static final String ROLE_DIALER  = "android.app.role.DIALER";
    public static final String ROLE_HOME    = "android.app.role.HOME";
    public static final String ROLE_ASSISTANT = "android.app.role.ASSISTANT";

    public RoleManager() {}
    public RoleManager(Context context) {}

    public boolean isRoleAvailable(String roleName) { return false; }
    public boolean isRoleHeld(String roleName) { return false; }

    /** AOSP createRequestRoleIntent(): an Intent to start for the role-grant
     *  prompt. We hand back a bare Intent; the (absent) prompt yields
     *  RESULT_CANCELED, so the role is never granted. */
    public Intent createRequestRoleIntent(String roleName) {
        return new Intent();
    }
}
