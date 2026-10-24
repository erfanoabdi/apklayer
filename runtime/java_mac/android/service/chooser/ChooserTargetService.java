// android.service.chooser.ChooserTargetService -- the Service an app publishes
// so the system share sheet can ask it for deep-link ChooserTargets.
//
// The SERVICE half is real: it extends android.app.Service, so an app's
// subclass is a normal component.
//
// onBind() returns NULL. On Android it returns the binder the share sheet uses
// to call onGetChooserTargets(). Mac.apk has no system share sheet, so nothing
// can bind it, and returning a binder that nothing is connected to would be a
// success with no backing. Null is also android.app.Service's own default for
// a service that offers no binding, so callers already handle it. The
// consequence is simply that onGetChooserTargets() is never called -- and no
// caller is left waiting, because the share sheet is the only thing that would
// have asked.
//
// @Deprecated on AOSP (superseded by ShortcutManager sharing shortcuts), but
// still referenced by apps in the corpus, which is why the type must link.
//
// Mirrors core/java/android/service/chooser/ChooserTargetService.java
// (android11-release).
package android.service.chooser;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;

import java.util.List;

@Deprecated
public abstract class ChooserTargetService extends Service {

    public static final String SERVICE_INTERFACE = "android.service.chooser.ChooserTargetService";

    public static final String META_DATA_NAME = "android.service.chooser.chooser_target_service";

    public static final String BIND_PERMISSION =
            "android.permission.BIND_CHOOSER_TARGET_SERVICE";

    /**
     * Called by the system share sheet to collect targets. Never invoked here:
     * there is no share sheet, and onBind returns null so nothing can call in.
     * Present to be overridden, which is why the class had to link.
     */
    public abstract List<ChooserTarget> onGetChooserTargets(ComponentName targetActivityName,
            IntentFilter matchedFilter);

    // Not final: AOSP declares this as a plain override, unlike
    // ConnectionService.onBind which IS final. Mirroring the modifier matters
    // because a subclass is allowed to override this one.
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
