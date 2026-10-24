package android.service.quicksettings;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * Base for a quick-settings tile service. Apps extend it; Mac.apk has no
 * quick-settings panel, so the tile is never shown and the callbacks never
 * fire, but the type + lifecycle must exist for the subclass to compile.
 *
 * Source: frameworks/base/core/java/android/service/quicksettings/TileService.java
 */
public class TileService extends Service {

    public static final String ACTION_QS_TILE = "android.service.quicksettings.action.QS_TILE";
    public static final String META_DATA_ACTIVE_TILE =
            "android.service.quicksettings.ACTIVE_TILE";
    // Transcribed verbatim from /tmp/aosp_api/android16.txt (signature format
    // 2.0). Both were absent: an app naming either had no field to resolve
    // through reflection, and our own manifest parsing had no name to match.
    public static final String ACTION_QS_TILE_PREFERENCES =
            "android.service.quicksettings.action.QS_TILE_PREFERENCES";
    public static final String META_DATA_TOGGLEABLE_TILE =
            "android.service.quicksettings.TOGGLEABLE_TILE";

    @Override public IBinder onBind(Intent intent) { return null; }

    public void onTileAdded() { }
    public void onTileRemoved() { }
    public void onStartListening() { }
    public void onStopListening() { }
    public void onClick() { }

    public final Tile getQsTile() { return null; }

    public final boolean isLocked() { return false; }
    public final boolean isSecure() { return false; }

    public final void unlockAndRun(Runnable runnable) {
        if (runnable != null) runnable.run();
    }

    /**
     * API 31. AOSP shows the dialog over the collapsed quick-settings panel.
     * There is no panel to collapse and no window token to attach a dialog to,
     * so this cannot show anything -- but it must not throw either, because a
     * tile calls it straight out of onClick(). AOSP itself silently does
     * nothing when the tile is not currently listening.
     */
    public final void showDialog(android.app.Dialog dialog) { }

    /** @deprecated at API 34 in favour of the PendingIntent overload. */
    @Deprecated
    public final void startActivityAndCollapse(Intent intent) { }
    public final void startActivityAndCollapse(android.app.PendingIntent pendingIntent) { }

    public static void requestListeningState(android.content.Context context,
            android.content.ComponentName component) { }
}
