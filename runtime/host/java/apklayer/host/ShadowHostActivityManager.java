package apklayer.host;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.TaskInfo;
import android.app.Application;
import android.app.IActivityManager;
import android.os.Process;
import java.util.List;
import java.util.Objects;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowAppTask;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowActivityManager;

/** Robolectric's activity manager, with the calls that cross processes (receivers, broadcasts) going to the instance hub once the app is connected to it. */
@Implements(value = ActivityManager.class, isInAndroidSdk = false)
public class ShadowHostActivityManager extends ShadowActivityManager {
    @Implementation protected static IActivityManager getService() {
        return HubActivityManager.service(ShadowActivityManager.getService());
    }

    /** This process under its own name (Robolectric names it after the package): apps tell their main process from a secondary one (android:process) by it. */
    @Implementation
    @Override
    protected List<ActivityManager.RunningAppProcessInfo> getRunningAppProcesses() {
        List<ActivityManager.RunningAppProcessInfo> processes = super.getRunningAppProcesses();
        if (processes != null) for (ActivityManager.RunningAppProcessInfo p : processes) if (p.pid == Process.myPid()) p.processName = Application.getProcessName();
        return processes;
    }

    /**
     * The process's one task (AppHost's stack), as Android gives an app its own tasks: apps that watch their task find it running (Meet's
     * TaskMonitor left every call: "its associated task is no longer running ... runningTasks=[[]]").
     */
    @Implementation
    @Override
    protected List<ActivityManager.RunningTaskInfo> getRunningTasks(int maxNum) {
        List<Activity> task = task();
        return task.isEmpty() || maxNum < 1 ? List.of() : List.of(describe(new ActivityManager.RunningTaskInfo(), task));
    }

    @Implementation
    @Override
    protected List<ActivityManager.AppTask> getAppTasks() {
        List<Activity> task = task();
        if (task.isEmpty()) return List.of();
        ActivityManager.AppTask appTask = ShadowAppTask.newInstance();
        ((ShadowAppTask) Shadow.extract(appTask)).setTaskInfo(describe(new ActivityManager.RecentTaskInfo(), task));
        return List.of(appTask);
    }

    /** The task's live activities, the top one first; AppHost changes its stack on the main thread, a caller may be on another. */
    private static List<Activity> task() {
        for (int attempt = 0; ; attempt++) {
            try {
                return AppHost.task().stream().filter(Objects::nonNull).toList();
            } catch (java.util.ConcurrentModificationException e) {
                if (attempt == 2) return List.of();
            }
        }
    }

    @SuppressWarnings("deprecation")  // RunningTaskInfo.id and RecentTaskInfo.id/persistentId: older apps read them
    private static <T extends TaskInfo> T describe(T info, List<Activity> task) {
        Activity top = task.get(0), base = task.get(task.size() - 1);
        info.taskId = top.getTaskId();
        info.isRunning = true;
        info.isVisible = true;
        info.numActivities = task.size();
        info.topActivity = top.getComponentName();
        info.baseActivity = base.getComponentName();
        info.origActivity = base.getComponentName();
        info.baseIntent = base.getIntent();
        if (info instanceof ActivityManager.RunningTaskInfo r) r.id = info.taskId;
        if (info instanceof ActivityManager.RecentTaskInfo r) { r.id = info.taskId; r.persistentId = info.taskId; }
        return info;
    }
}
