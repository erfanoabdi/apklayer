package apklayer.host;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadows.ShadowActivity;

/** The first activity of the app's task is the task root (Robolectric's Activity.isTaskRoot is false, so launcher trampolines finish without starting the real screen). */
@Implements(value = android.app.Activity.class, isInAndroidSdk = false)
public class ShadowHostActivity extends ShadowActivity {
    @RealObject private android.app.Activity real;
    /** startActivityIfNeeded starts the activity (AnkiDroid's launcher trampoline): Robolectric's version records nothing. */
    @Implementation protected boolean startActivityIfNeeded(android.content.Intent intent, int requestCode) {
        if (requestCode >= 0) real.startActivityForResult(intent, requestCode); else real.startActivity(intent);
        return true;
    }
    @Implementation protected boolean startActivityIfNeeded(android.content.Intent intent, int requestCode, android.os.Bundle options) {
        if (requestCode >= 0) real.startActivityForResult(intent, requestCode, options); else real.startActivity(intent, options);
        return true;
    }
    /** navigateUpTo goes to ActivityClient, which nothing answers here: Task.navigateUpTo's rules on the host's task. The activity and those above
     *  the parent (its nearest instance below) finish; the up-intent is then started under the launch rules (a parent that takes it, single-top or
     *  CLEAR_TOP|SINGLE_TOP, gets onNewIntent; otherwise a new one replaces it). Without the parent in the task only this activity finishes: false. */
    @Implementation protected boolean navigateUpTo(android.content.Intent upIntent) {
        android.content.ComponentName dest = upIntent.getComponent() != null ? upIntent.getComponent() : upIntent.resolveActivity(real.getPackageManager());
        android.content.pm.ActivityInfo info;
        try {
            info = dest == null ? null : real.getPackageManager().getActivityInfo(dest, 0);
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            info = null;
        }
        java.util.List<android.app.Activity> task = new java.util.ArrayList<>(AppHost.task());
        if (!task.contains(real)) task.add(0, real);  // still being started (its resume ran the looper): it goes on top
        int self = task.indexOf(real), parent = -1;
        String parentClass = info == null ? null : info.targetActivity != null ? info.targetActivity : info.name;  // an <activity-alias> names its class
        for (int i = self + 1; parentClass != null && i < task.size() && parent < 0; i++)
            if (task.get(i) != null && task.get(i).getClass().getName().equals(parentClass)) parent = i;
        for (int i = self; i < (parent < 0 ? self + 1 : parent); i++) if (task.get(i) != null) task.get(i).finish();
        if (parent < 0) return false;
        android.app.Activity up = task.get(parent);
        java.util.List<Class<?>> after = AppHost.task().stream().<Class<?>>map(a -> a == null ? null : a.getClass()).toList();
        if (!LaunchRules.plan(after, up.getClass(), upIntent.getFlags(), info.launchMode).reuse()) up.finish();  // START_SUCCESS: the new one replaces it
        real.startActivity(new android.content.Intent(upIntent).setComponent(dest));
        return true;
    }
    @Implementation protected boolean isTaskRoot() { return !Boolean.getBoolean("apklayer.notaskroot") && AppHost.isTaskRoot(real); }
}
