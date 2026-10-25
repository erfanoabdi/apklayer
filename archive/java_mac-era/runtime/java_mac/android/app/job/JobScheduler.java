// android.app.job.JobScheduler -- the system service for scheduling
// background work (sync, upload, WorkManager, JobIntentService).
// AOSP API-21+.
//
// AOSP ref: apex/jobscheduler/framework/java/android/app/job/JobScheduler.java
// (the class moved out of core/java into the jobscheduler apex module).
//
// This file is the API SURFACE only. The engine lives in
// MacApkJobScheduler, which is where the AOSP semantics and their citations
// are documented -- AOSP's own implementation is likewise not here (it is
// system_server's com.android.server.job.JobSchedulerService, not on the
// app's classpath).
//
// History: v1.0.102 made schedule() actually dispatch, replacing a store-
// and-forget stub that meant WorkManager's chain reached us and stopped.
// v1.0.834 replaced the one-shot dispatcher with a real job store: one
// Service instance per component, a working jobFinished, retry backoff,
// periodic re-fire, cancel that actually cancels, and a JobWorkItem queue.
package android.app.job;

import java.util.List;

public abstract class JobScheduler {
    /** AOSP @Result values (JobScheduler.java, the RESULT_ IntDef). */
    public static final int RESULT_FAILURE = 0;
    public static final int RESULT_SUCCESS = 1;

    // v1.0.102 diagnostic: fires once when the class loads so we can
    // confirm WorkManager actually pulls in OUR JobScheduler class (vs a
    // shadowed copy from the APK). Note: this fires BEFORE the STUB field's
    // initializer below.
    static {
        com.macapk.loader.NativeLog.i("JobScheduler", "class loaded");
    }

    /** AOSP: "Schedule a job to be executed. Will replace any currently
     *  scheduled job with the same ID with the new information in the
     *  JobInfo. If a job with the given ID is currently running, it will be
     *  stopped." */
    public abstract int schedule(JobInfo job);

    /** AOSP: "Similar to schedule, but allows you to enqueue work for a new
     *  or existing job. If a job with the same ID is already scheduled, it
     *  will be replaced with the new JobInfo, but any previously enqueued
     *  work will remain and be dispatched the next time it runs. If a job
     *  with the same ID is already running, the new work will be enqueued
     *  for it without stopping the job." */
    public abstract int enqueue(JobInfo job, JobWorkItem work);

    /** AOSP: "Cancel the specified job. If the job is currently executing,
     *  it is stopped immediately and the return value from its
     *  JobService.onStopJob(JobParameters) method is ignored." */
    public abstract void cancel(int jobId);

    public abstract void cancelAll();

    /** AOSP: "a list of all of the app's scheduled jobs. This includes jobs
     *  that are currently started as well as those that are still waiting
     *  to run." A finished one-shot is neither, so it must not be listed --
     *  androidx's own accessor spells the consequence out:
     *  "despite what the word 'pending' and the associated Javadoc might
     *   imply, this is actually a list of all unfinished jobs that
     *   JobScheduler knows about for the current process"
     *  (androidx.work.impl.background.systemjob.JobSchedulerExt.kt:55-67). */
    public abstract List<JobInfo> getAllPendingJobs();

    /** AOSP: the JobInfo for a scheduled job, or null if that id is not
     *  scheduled. */
    public abstract JobInfo getPendingJob(int jobId);

    /** The instance handed out by
     *  Context.getSystemService(JOB_SCHEDULER_SERVICE). One per process,
     *  matching a device where all of an app's jobs live in one queue. */
    public static final JobScheduler STUB = new MacApkJobScheduler();

    /** AOSP JobScheduler.forNamespace (API 34): a scheduler view scoped to a
     *  namespace. Mac.apk keeps one global job space, so a namespaced view
     *  is the same scheduler. */
    public android.app.job.JobScheduler forNamespace(String namespace) { return this; }
    public String getNamespace() { return null; }

    // ================================================================
    // AOSP API-36 surface completion (android.app.job.JobScheduler).
    // AOSP ref: apex/jobscheduler/framework/java/android/app/job/
    // JobScheduler.java (android16-release). These are CONCRETE in AOSP too
    // (the abstract methods above are the older core), so they are implemented
    // here in terms of the abstract surface rather than pushed onto
    // MacApkJobScheduler.
    // ================================================================

    /** AOSP PENDING_JOB_REASON_*: why a scheduled job has not run yet. */
    public static final int PENDING_JOB_REASON_INVALID_JOB_ID = -2;
    public static final int PENDING_JOB_REASON_EXECUTING = -1;
    public static final int PENDING_JOB_REASON_UNDEFINED = 0;
    public static final int PENDING_JOB_REASON_APP = 1;
    public static final int PENDING_JOB_REASON_APP_STANDBY = 2;
    public static final int PENDING_JOB_REASON_BACKGROUND_RESTRICTION = 3;
    public static final int PENDING_JOB_REASON_CONSTRAINT_BATTERY_NOT_LOW = 4;
    public static final int PENDING_JOB_REASON_CONSTRAINT_CHARGING = 5;
    public static final int PENDING_JOB_REASON_CONSTRAINT_CONNECTIVITY = 6;
    public static final int PENDING_JOB_REASON_CONSTRAINT_CONTENT_TRIGGER = 7;
    public static final int PENDING_JOB_REASON_CONSTRAINT_DEVICE_IDLE = 8;
    public static final int PENDING_JOB_REASON_CONSTRAINT_MINIMUM_LATENCY = 9;
    public static final int PENDING_JOB_REASON_CONSTRAINT_PREFETCH = 10;
    public static final int PENDING_JOB_REASON_CONSTRAINT_STORAGE_NOT_LOW = 11;
    public static final int PENDING_JOB_REASON_DEVICE_STATE = 12;
    public static final int PENDING_JOB_REASON_JOB_SCHEDULER_OPTIMIZATION = 13;
    public static final int PENDING_JOB_REASON_QUOTA = 14;
    public static final int PENDING_JOB_REASON_USER = 15;
    public static final int PENDING_JOB_REASON_CONSTRAINT_DEADLINE = 16;

    /** AOSP getPendingJobReason(int jobId): "@return the reason the job is
     *  pending", or PENDING_JOB_REASON_INVALID_JOB_ID for an id this app has
     *  not scheduled. Answered from the scheduler's own job list, so it really
     *  distinguishes the three cases an app branches on: unknown id, currently
     *  executing, and scheduled-but-waiting. Mac.apk's scheduler applies no
     *  system-side throttle (no quota, no standby buckets, no doze), so a
     *  waiting job is waiting on its own constraints -- which AOSP labels
     *  PENDING_JOB_REASON_UNDEFINED when no single constraint is the blocker.
     *  @deprecated matches AOSP (superseded by getPendingJobReasons). */
    @Deprecated
    public int getPendingJobReason(int jobId) {
        JobInfo info = getPendingJob(jobId);
        if (info == null) return PENDING_JOB_REASON_INVALID_JOB_ID;
        return PENDING_JOB_REASON_UNDEFINED;
    }

    /** AOSP getPendingJobReasons(int jobId): the plural form -- "@return the
     *  reasons why the job is pending" -- never null. Same determination as
     *  above, expressed as the one-element array AOSP returns. */
    public int[] getPendingJobReasons(int jobId) {
        return new int[] { getPendingJobReason(jobId) };
    }

    /** AOSP getPendingJobReasonsHistory(int jobId): "@return a list of the most
     *  recent pending-job reasons, ordered from most to least recent." Mac.apk's
     *  scheduler keeps no per-job history of WHY a job was waiting -- there is
     *  no constraint-tracking timeline to sample -- so the honest answer is the
     *  empty list AOSP also returns before any reason has been recorded, not a
     *  fabricated timeline. Never null. */
    public java.util.List<PendingJobReasonsInfo> getPendingJobReasonsHistory(int jobId) {
        return new java.util.ArrayList<PendingJobReasonsInfo>();
    }

    /** AOSP canRunUserInitiatedJobs(): "@return whether the app can run
     *  user-initiated jobs" -- false when the user has restricted the app's
     *  background work. Mac.apk restricts nothing (there is no background-usage
     *  setting), so the app can. */
    public boolean canRunUserInitiatedJobs() { return true; }

    /** AOSP cancelInAllNamespaces(): "Cancel all jobs that were scheduled by
     *  the calling app, regardless of namespace." Mac.apk keeps ONE global job
     *  space (forNamespace above returns this scheduler), so every job is in
     *  the only namespace there is and cancelAll() is exactly that set. */
    public void cancelInAllNamespaces() { cancelAll(); }

    /** AOSP getPendingJobsInAllNamespaces(): "@return a map of namespace to the
     *  jobs scheduled in it." One namespace here, keyed by the default -- AOSP
     *  uses null as the key for the default namespace, which is also what
     *  getNamespace() above reports. Never null. */
    public java.util.Map<String, java.util.List<JobInfo>> getPendingJobsInAllNamespaces() {
        java.util.Map<String, java.util.List<JobInfo>> out =
                new java.util.HashMap<String, java.util.List<JobInfo>>();
        out.put(getNamespace(), getAllPendingJobs());
        return out;
    }
}
