package apklayer.host;

import android.content.AttributionSourceState;
import android.content.Context;
import android.content.pm.PackageManager;
import android.permission.IPermissionChecker;
import org.robolectric.shadows.ShadowServiceManager;

/**
 * The "permission_checker" service (system_server's PermissionCheckerService) that ContentProvider's Transport and PermissionChecker ask. Each source of the attribution
 * chain is checked as the Context would check it: another app by the hub (its package's grants), this app by its own. App ops are not modelled: granted is
 * GRANTED, anything else HARD_DENIED. The hub publishes one on the real service manager for the native services (audioserver's recording checks), from the
 * packages' grants.
 */
final class HubPermissionChecker extends IPermissionChecker.Stub {
    private final java.util.function.BiPredicate<AttributionSourceState, String> granted;
    private HubPermissionChecker(java.util.function.BiPredicate<AttributionSourceState, String> granted) { this.granted = granted; }

    static void install() {
        ShadowServiceManager.addBinderService(Context.PERMISSION_CHECKER_SERVICE, IPermissionChecker.class, new HubPermissionChecker((source, permission) ->
                android.app.ActivityThread.currentApplication().checkPermission(permission, source.pid, source.uid) == PackageManager.PERMISSION_GRANTED));
    }

    static boolean publish(HubPackages packages) {
        return RealBinder.publish(Context.PERMISSION_CHECKER_SERVICE, new HubPermissionChecker((source, permission) -> packages.uidHolds(source.uid, permission)));
    }

    @Override public int checkPermission(String permission, AttributionSourceState source, String message, boolean forDataDelivery, boolean startDataDelivery,
            boolean fromDatasource, int attributedOp) {
        for (AttributionSourceState element = source; element != null; element = element.next != null && element.next.length > 0 ? element.next[0] : null) {
            if (fromDatasource && element == source) continue;
            if (!granted.test(element, permission)) return PERMISSION_HARD_DENIED;
        }
        return PERMISSION_GRANTED;
    }

    @Override public int checkOp(int op, AttributionSourceState source, String message, boolean forDataDelivery, boolean startDataDelivery) { return PERMISSION_GRANTED; }
    @Override public void finishDataDelivery(int op, AttributionSourceState source, boolean fromDatasource) {}
}
