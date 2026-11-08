package hello.system;

import android.content.Context;
import android.os.SystemClock;

/** A message posted in Application.onCreate runs after the first activity's onCreate: ActivityThread's launch transaction is queued before it (App, Main). */
final class LaunchOrderCheck implements Check {
    @Override public String run(Context context) throws Exception {
        long start = SystemClock.uptimeMillis();
        while (App.postSawActivity == null && SystemClock.uptimeMillis() - start < 3000) Thread.sleep(20);
        Check.require(Boolean.TRUE.equals(App.postSawActivity), "Application.onCreate's post ran " + (App.postSawActivity == null ? "never" : "before the activity's onCreate"));
        return "Application.onCreate's post ran after the first activity's onCreate";
    }
}
