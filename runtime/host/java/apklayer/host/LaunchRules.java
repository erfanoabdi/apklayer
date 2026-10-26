package apklayer.host;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import java.util.List;

/**
 * Android's task rules for starting an activity (launch modes and intent flags), as a decision the host executes
 * on its activity stack: reuse an instance already there (onNewIntent) instead of creating another, clearing
 * what is above it, or clearing the whole stack. Without it a pair of activities that start each other with
 * CLEAR_TOP/SINGLE_TOP (Nextcloud's login and first-run screens) pile up forever.
 */
final class LaunchRules {
    private LaunchRules() {}

    /**
     * @param clearAll destroy every activity first (CLEAR_TASK)
     * @param above    entries above the target instance to destroy first, or -1 if no instance is involved
     * @param reuse    deliver onNewIntent to the target instead of creating a new activity; if false (CLEAR_TOP on a
     *                 standard-mode activity) the target is destroyed too and a fresh one is created
     */
    record Plan(boolean clearAll, int above, boolean reuse) {
        static final Plan NONE = new Plan(false, -1, false);
    }

    /** @param stackTopFirst the classes of the activities in the task, the top one first */
    static Plan plan(List<Class<?>> stackTopFirst, Class<?> cls, int flags, int launchMode) {
        if ((flags & Intent.FLAG_ACTIVITY_CLEAR_TASK) != 0 && (flags & Intent.FLAG_ACTIVITY_NEW_TASK) != 0) return new Plan(true, -1, false);
        boolean taskMode = launchMode == ActivityInfo.LAUNCH_SINGLE_TASK || launchMode == ActivityInfo.LAUNCH_SINGLE_INSTANCE;
        boolean clearTop = (flags & Intent.FLAG_ACTIVITY_CLEAR_TOP) != 0 || taskMode;
        boolean singleTop = (flags & Intent.FLAG_ACTIVITY_SINGLE_TOP) != 0 || launchMode == ActivityInfo.LAUNCH_SINGLE_TOP || taskMode;
        int at = stackTopFirst.indexOf(cls);
        if (at < 0) return Plan.NONE;
        if (clearTop) return new Plan(false, at, singleTop);
        if (singleTop && at == 0) return new Plan(false, 0, true);
        return Plan.NONE;
    }
}
