package apklayer.host;

import android.content.AttributionSourceState;
import android.content.Context;
import android.content.pm.PackageManager;
import android.permission.IPermissionChecker;
import org.robolectric.shadows.ShadowServiceManager;

/**
 * The "permission_checker" service (system_server's PermissionCheckerService) that ContentProvider's Transport and PermissionChecker ask. Each source of the attribution
 * chain is checked as the Context would check it: another app by the hub (its package's grants), this app by its own. App ops are not modelled: granted is
 * GRANTED, anything else HARD_DENIED.
 */
final class HubPermissionChecker extends IPermissionChecker.Stub {
    static void install() { ShadowServiceManager.addBinderService(Context.PERMISSION_CHECKER_SERVICE, IPermissionChecker.class, new HubPermissionChecker()); }

    @Override public int checkPermission(String permission, AttributionSourceState source, String message, boolean forDataDelivery, boolean startDataDelivery,
            boolean fromDatasource, int attributedOp) {
        Context context = android.app.ActivityThread.currentApplication();
        for (AttributionSourceState element = source; element != null; element = element.next != null && element.next.length > 0 ? element.next[0] : null) {
            if (fromDatasource && element == source) continue;
            if (context.checkPermission(permission, element.pid, element.uid) != PackageManager.PERMISSION_GRANTED) return PERMISSION_HARD_DENIED;
        }
        return PERMISSION_GRANTED;
    }

    @Override public int checkOp(int op, AttributionSourceState source, String message, boolean forDataDelivery, boolean startDataDelivery) { return PERMISSION_GRANTED; }
    @Override public void finishDataDelivery(int op, AttributionSourceState source, boolean fromDatasource) {}
}
