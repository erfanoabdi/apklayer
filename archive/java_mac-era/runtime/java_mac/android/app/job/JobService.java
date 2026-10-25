// android.app.job.JobService -- the entry point JobScheduler calls back
// into. androidx.work's SystemJobService, Firebase datatransport's
// JobInfoSchedulerService and GMS's AppMeasurementJobService all extend it.
//
// AOSP ref: apex/jobscheduler/framework/java/android/app/job/JobService.java
//
// The header comment here used to say "Mac.apk doesn't execute background
// jobs (single-app, foreground) ... the WorkManager jobs registered never
// actually fire". That stopped being true at v1.0.102, when JobScheduler
// started dispatching for real -- but jobFinished stayed the empty stub the
// sentence described, for 700-odd versions.
//
// v1.0.834: jobFinished is wired. AOSP's implementation is a one-line
// delegate to the engine (JobService.java:188-190):
//
//     public final void jobFinished(JobParameters params, boolean wantsReschedule) {
//         mEngine.jobFinished(params, wantsReschedule);
//     }
//
// and the engine posts it to the main-thread handler
// (JobServiceEngine.java:410-417) rather than acting on the caller's thread,
// because a Worker calls it from its own executor. Mac.apk does the same:
// JobParameters carries the scheduler-side record for this execution, so
// the call routes back without JobService needing to know the scheduler
// singleton -- mirroring AOSP's params-carries-the-callback shape.
//
// What rides on it, quoted from AOSP's javadoc:
//   "You can request that the job be scheduled again by passing true as the
//    wantsReschedule parameter. This will apply back-off policy for the job"
//   "When false is returned for a periodic job, the job will be rescheduled
//    according to its periodic policy."
// So while it was a no-op there was no retry-with-backoff and a periodic
// job ran exactly once per process.
package android.app.job;

import android.app.Service;

public abstract class JobService extends Service {
    /** AOSP: job services must be protected with this permission or the
     *  system ignores them. Mac.apk has no permission enforcement between
     *  its own scheduler and the app, but apps reference the constant. */
    public static final String PERMISSION_BIND = "android.permission.BIND_JOB_SERVICE";

    /** AOSP JOB_END_NOTIFICATION_POLICY_* (API 34), used with
     *  setNotification for user-initiated jobs. */
    public static final int JOB_END_NOTIFICATION_POLICY_DETACH = 0;
    public static final int JOB_END_NOTIFICATION_POLICY_REMOVE = 1;

    /** AOSP: "Like all other component lifecycle callbacks, this method
     *  executes on your application's main thread." Mac.apk's scheduler
     *  posts every dispatch to the main looper for that reason. Return true
     *  to keep the job alive until jobFinished; false means it is already
     *  done and onStopJob will NOT be invoked. */
    public abstract boolean onStartJob(JobParameters params);

    /** AOSP: called when the job must stop before it called jobFinished --
     *  a constraint lapsed, it was cancelled, or it timed out.
     *  JobParameters.getStopReason() says which. Return true to ask for a
     *  reschedule under the job's retry criteria. */
    public abstract boolean onStopJob(JobParameters params);

    public final void jobFinished(JobParameters params, boolean needsReschedule) {
        if (params == null) throw new NullPointerException("params");
        MacApkJobScheduler engine = params.record != null ? params.record.owner : null;
        if (engine == null) {
            // An app-constructed JobParameters, or one whose job has been
            // cancelled. AOSP logs "finishJob() called for a nonexistent
            // job id." and drops it.
            try {
                com.macapk.loader.NativeLog.i("JobScheduler",
                    "jobFinished(" + params.getJobId() + ") for a job this scheduler is"
                    + " not tracking; ignoring (AOSP: nonexistent job id)");
            } catch (Throwable ignored) {}
            return;
        }
        engine.jobFinished(params, needsReschedule);
    }

    /** AOSP onNetworkChanged (API 31): the network satisfying the job's
     *  constraint changed. Mac.apk models a single always-present transport
     *  (ConnectivityManager), so the network a job is given never changes
     *  mid-execution and this is never invoked -- the default AOSP body
     *  only logs a warning, so matching it costs nothing. */
    public void onNetworkChanged(JobParameters params) {}

    /** AOSP data-transfer job reporting (API 34). Mac.apk does not run
     *  JobScheduler's data-transfer accounting, and AOSP's own defaults
     *  return 0 for a job that is not a data-transfer job. */
    public final void updateEstimatedNetworkBytes(JobParameters params,
            long downloadBytes, long uploadBytes) {}
    public final void updateTransferredNetworkBytes(JobParameters params,
            long transferredDownloadBytes, long transferredUploadBytes) {}
    /** AOSP updateEstimatedNetworkBytes(JobParameters, JobWorkItem, long, long):
     *  the per-work-item form -- an app that enqueued several JobWorkItems
     *  updates the estimate for ONE of them. Same accounting as the
     *  whole-job form above, which Mac.apk does not run. */
    public final void updateEstimatedNetworkBytes(JobParameters params, JobWorkItem jobWorkItem,
            long downloadBytes, long uploadBytes) {}
    /** AOSP updateTransferredNetworkBytes(JobParameters, JobWorkItem, long,
     *  long): the per-work-item form of the transferred-bytes report. */
    public final void updateTransferredNetworkBytes(JobParameters params, JobWorkItem jobWorkItem,
            long transferredDownloadBytes, long transferredUploadBytes) {}
    public long getTransferredDownloadBytes(JobParameters params) { return 0; }
    public long getTransferredUploadBytes(JobParameters params) { return 0; }

    /** AOSP setNotification (API 34): ties a notification to the job's
     *  lifecycle, required only for user-initiated jobs.
     *  JobParameters.isUserInitiatedJob() is always false here, so no job
     *  ever requires one and the ANR AOSP raises after 10s cannot apply. */
    public final void setNotification(JobParameters params, int notificationId,
            android.app.Notification notification, int jobEndNotificationPolicy) {}
}
