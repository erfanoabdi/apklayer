// android.app.TaskInfo -- the public base that ActivityManager.RecentTaskInfo and
// ActivityManager.RunningTaskInfo extend since API 29. The fields an app reads off
// a task (taskId, baseActivity, topActivity, numActivities, ...) live HERE, not on
// the subclasses, so with the base missing every one of those reads is a
// NoSuchFieldError even though the subclass type resolved fine.
//
// Source (android-30 android.jar):
//   public class android.app.TaskInfo {
//     public android.content.ComponentName baseActivity;
//     public android.content.Intent baseIntent;
//     public boolean isRunning;
//     public int numActivities;
//     public android.content.ComponentName origActivity;
//     public android.app.ActivityManager$TaskDescription taskDescription;
//     public int taskId;
//     public android.content.ComponentName topActivity;
//   }
package android.app;

import android.content.ComponentName;
import android.content.Intent;

public class TaskInfo {

    /** The component launched as the FIRST activity in the task. */
    public ComponentName baseActivity;

    /** The Intent that started the task's root activity. */
    public Intent baseIntent;

    /** True while the task has at least one running activity. */
    public boolean isRunning;

    /** Number of activities in the task. */
    public int numActivities;

    /** Set when the root activity was an alias; the aliased target. */
    public ComponentName origActivity;

    /** The task's last-set description (label / icon / colours). */
    public ActivityManager.TaskDescription taskDescription;

    /** The system-assigned task id. */
    public int taskId;

    /** The activity currently on top of the task. */
    public ComponentName topActivity;

    /** AOSP TaskInfo.isVisible(): {@code return isVisible;} over a package-private
     *  field the system server fills in (TaskInfo.java:389-391). Mac.apk hosts
     *  ONE task in ONE window, and the only task an app can obtain a TaskInfo for
     *  is that one, so it is visible whenever it exists. */
    public boolean isVisible() { return true; }

    TaskInfo() {}

    @Override public String toString() {
        return "TaskInfo{taskId=" + taskId
                + " baseActivity=" + baseActivity
                + " topActivity=" + topActivity
                + " numActivities=" + numActivities + "}";
    }
}
