// android.app.TaskStackBuilder -- builds a synthetic back stack of
// Intents (used for notification deep-links: tapping a notification
// should land on a screen with a correct Up/Back chain). AOSP API-11+.
//
// AOSP ref: frameworks/base/core/java/android/app/TaskStackBuilder.java.
// Mac.apk is single-window, so the "stack" collapses to launching the
// top (most specific) Intent. getPendingIntent wraps the whole stack in
// a PendingIntent.getActivities token so a later send() launches it.
package android.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import java.util.ArrayList;

public class TaskStackBuilder {
    private final Context mSourceContext;
    private final ArrayList<Intent> mIntents = new ArrayList<>();

    private TaskStackBuilder(Context a) { mSourceContext = a; }

    public static TaskStackBuilder create(Context context) { return new TaskStackBuilder(context); }
    @Deprecated public static TaskStackBuilder from(Context context) { return create(context); }

    public TaskStackBuilder addNextIntent(Intent nextIntent) { if (nextIntent != null) mIntents.add(nextIntent); return this; }
    public TaskStackBuilder addNextIntentWithParentStack(Intent nextIntent) {
        // AOSP walks the parent-activity chain via PackageManager; we
        // don't model the manifest's parentActivityName graph, so we
        // just add the intent itself (the deep-link target).
        return addNextIntent(nextIntent);
    }
    public TaskStackBuilder addParentStack(Context sourceContext) { return this; }
    public TaskStackBuilder addParentStack(android.app.Activity sourceActivity) { return this; }
    public TaskStackBuilder addParentStack(Class<?> sourceActivityClass) { return this; }
    public TaskStackBuilder addParentStack(ComponentName sourceActivityName) { return this; }

    public int getIntentCount() { return mIntents.size(); }
    public Intent editIntentAt(int index) { return mIntents.get(index); }
    public Intent[] getIntents() { return mIntents.toArray(new Intent[0]); }

    /** Wrap the whole stack in a PendingIntent. A later send() launches
     *  it (our PendingIntent.getActivities runs Context.startActivities,
     *  which lands on the top intent). */
    public PendingIntent getPendingIntent(int requestCode, int flags) {
        return getPendingIntent(requestCode, flags, null);
    }
    public PendingIntent getPendingIntent(int requestCode, int flags, Bundle options) {
        if (mIntents.isEmpty()) {
            throw new IllegalStateException("No intents added to TaskStackBuilder; cannot getPendingIntent");
        }
        return PendingIntent.getActivities(mSourceContext, requestCode, getIntents(), flags, options);
    }

    /** Immediately launch the stack (no notification involved). */
    public void startActivities() { startActivities(null); }
    public void startActivities(Bundle options) {
        if (mIntents.isEmpty()) {
            throw new IllegalStateException("No intents added to TaskStackBuilder; cannot startActivities");
        }
        if (mSourceContext != null) mSourceContext.startActivities(getIntents(), options);
    }
}
