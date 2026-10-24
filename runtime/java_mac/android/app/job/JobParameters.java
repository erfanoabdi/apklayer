// android.app.job.JobParameters -- the args to JobService.onStartJob /
// onStopJob, and the handle the app passes back to
// JobService.jobFinished(params, wantsReschedule).
//
// AOSP ref: apex/jobscheduler/framework/java/android/app/job/JobParameters.java
// (the class moved out of core/java into the jobscheduler apex module; the
// public surface is unchanged).
//
// v1.0.102: became a real wrapper around the JobInfo so WorkManager's
// SystemJobService could read EXTRA_WORK_SPEC_ID back out of getExtras().
//
// v1.0.834: carries the two pieces of live per-execution state AOSP's
// JobParameters carries, both of which were previously hardcoded:
//
//   * the STOP REASON (getStopReason). AOSP populates this before invoking
//     onStopJob so the app can tell "cancelled by the app" from "the
//     network constraint lapsed". WorkManager 2.8+ reads it in
//     SystemJobService.onStopJob and records it on the WorkSpec, so a
//     hardcoded 0 (STOP_REASON_UNDEFINED) mislabels every stop.
//   * the JobWorkItem QUEUE (dequeueWork / completeWork). JobScheduler
//     .enqueue(JobInfo, JobWorkItem) is the API androidx.core.app
//     .JobIntentService is built on: its processor loop pulls each item
//     with params.dequeueWork() and stops when it gets null. Returning a
//     hardcoded null meant every JobIntentService saw an empty queue and
//     exited immediately, having silently dropped the work.
//
// The queue lives in the scheduler's per-job record (there is exactly one
// queue per job id, shared across executions -- AOSP's enqueue() javadoc:
// "any previously enqueued work will remain and be dispatched the next
// time it runs"), so JobParameters holds a package-private reference to it
// rather than a copy.
package android.app.job;

import android.os.Bundle;
import android.os.PersistableBundle;

public class JobParameters implements android.os.Parcelable {
    // ---- STOP_REASON_* (API 31) ------------------------------------------
    // Values verbatim from AOSP JobParameters.java:195-282. WorkManager maps
    // these onto its own WorkInfo.getStopReason(), so the numbering matters.
    public static final int STOP_REASON_UNDEFINED = 0;
    public static final int STOP_REASON_CANCELLED_BY_APP = 1;
    public static final int STOP_REASON_PREEMPT = 2;
    public static final int STOP_REASON_TIMEOUT = 3;
    public static final int STOP_REASON_DEVICE_STATE = 4;
    public static final int STOP_REASON_CONSTRAINT_BATTERY_NOT_LOW = 5;
    public static final int STOP_REASON_CONSTRAINT_CHARGING = 6;
    public static final int STOP_REASON_CONSTRAINT_CONNECTIVITY = 7;
    public static final int STOP_REASON_CONSTRAINT_DEVICE_IDLE = 8;
    public static final int STOP_REASON_CONSTRAINT_STORAGE_NOT_LOW = 9;
    public static final int STOP_REASON_QUOTA = 10;
    public static final int STOP_REASON_BACKGROUND_RESTRICTION = 11;
    public static final int STOP_REASON_APP_STANDBY = 12;
    public static final int STOP_REASON_USER = 13;
    public static final int STOP_REASON_SYSTEM_PROCESSING = 14;
    public static final int STOP_REASON_ESTIMATED_APP_LAUNCH_TIME_CHANGED = 15;

    /** MACAPK_JOB_AUTOFINISH_ON_DRAIN (default ON; lane fdroid-install-gridlayout,
     *  review issue 1, 2026-09-28): auto-finish a JobWorkItem-based job when
     *  dequeueWork() drains its queue with nothing still outstanding, exactly
     *  as AOSP's JobServiceContext.doDequeueWork does -- see the javadoc on
     *  dequeueWork() below for the cited source and the full mechanism.
     *  =0 restores the pre-fix path byte-for-byte: dequeueWork() still
     *  returns null the same way, but nothing calls jobFinished for it, so
     *  MacApkJobScheduler.JobRecord.running (and .live) are never cleared by
     *  draining alone -- the bug this lane fixes: a JobIntentService-shaped
     *  service (androidx.core.app.JobIntentService, whose compat
     *  JobServiceEngineImpl never calls jobFinished itself) leaves the
     *  record "running" forever after its first execution, so
     *  MacApkJobScheduler.scheduleInternal's "if (!rec.running)
     *  armLocked(...)" never re-arms a later enqueue() in the same process --
     *  every Install after the first download of an F-Droid session enqueued
     *  work that was never dispatched. */
    private static final boolean AUTOFINISH_ON_DRAIN =
            !"0".equals(System.getenv("MACAPK_JOB_AUTOFINISH_ON_DRAIN"));

    private final JobInfo job;

    /** The scheduler-side job record this execution belongs to. Package-
     *  private: set by MacApkJobScheduler when it builds the params for an
     *  execution, and null for an app-constructed JobParameters. */
    MacApkJobScheduler.JobRecord record;
    /** Populated by the scheduler immediately before onStopJob, per AOSP. */
    int stopReason = STOP_REASON_UNDEFINED;
    /** Snapshotted at construction, NOT read through to the job record.
     *  AOSP's JobParameters takes both as final ctor args, and the
     *  distinction is load-bearing: the record is reused across executions,
     *  so reading through it would let a later execution (or the completion
     *  bookkeeping) mutate what a still-live JobParameters reports. */
    boolean overrideDeadlineExpired;
    android.net.Network network;

    public JobParameters() { this.job = null; }
    public JobParameters(JobInfo job) { this.job = job; }

    JobParameters(JobInfo job, MacApkJobScheduler.JobRecord record) {
        this.job = job;
        this.record = record;
    }

    public int getJobId() { return job != null ? job.getId() : 0; }

    /** AOSP: @NonNull -- an empty bundle rather than null when the job
     *  carried no extras. */
    public PersistableBundle getExtras() {
        return job != null ? job.getExtras() : new PersistableBundle();
    }
    /** AOSP getTransientExtras(): @NonNull -- the Bundle the job was scheduled
     *  with via JobInfo.Builder.setTransientExtras. Was an unconditional empty
     *  Bundle, which silently dropped whatever the app set; JobInfo now carries
     *  it, so read it through. */
    public Bundle getTransientExtras() {
        return job != null ? job.getTransientExtras() : new Bundle();
    }

    /** AOSP: true when this execution was started because the job's
     *  setOverrideDeadline() expired rather than because its constraints
     *  were met. */
    public boolean isOverrideDeadlineExpired() { return overrideDeadlineExpired; }

    public boolean isExpeditedJob() { return job != null && job.isExpedited(); }

    /** AOSP isUserInitiatedJob (API 34). Mac.apk never promotes a job to
     *  user-initiated (there is no Task Manager affordance that could then
     *  demote it), so this is false -- the same answer a real device gives
     *  for a job that did not request it. */
    public boolean isUserInitiatedJob() {
        // The job's OWN request is now recorded on JobInfo (API 34
        // setUserInitiated), so report what the app asked for rather than a
        // constant false. Mac.apk still never treats such a job differently --
        // there is no Task Manager affordance and no quota to exempt it from --
        // but an app checking "was I started as user-initiated" gets its own
        // answer back instead of a contradiction.
        return job != null && job.isUserInitiated();
    }

    /** AOSP getJobNamespace (API 34): the namespace the job was scheduled
     *  in, or null for the default namespace. Mac.apk keeps one global job
     *  space (JobScheduler.forNamespace returns this), so: null. */
    public String getJobNamespace() { return null; }

    /** AOSP: the Network the job may use. Non-null only when the job
     *  declared a network requirement, and then only while that
     *  requirement is satisfied. WorkManager hands this straight to the
     *  Worker via WorkerParameters.getNetwork(). */
    public android.net.Network getNetwork() {
        if (job == null || job.getNetworkType() == JobInfo.NETWORK_TYPE_NONE) return null;
        return network;
    }

    /** The changes a content-URI trigger reported for THIS execution, set by
     *  MacApkJobScheduler as it starts the job (the AOSP JobServiceContext
     *  arrays, :354-362) and snapshotted like the deadline flag above. Null
     *  for a job started any other way, and always null with
     *  MACAPK_JOB_CONTENT_TRIGGER=0. */
    android.net.Uri[] triggeredContentUris;
    String[] triggeredContentAuthorities;

    /** AOSP (android14-release :447-457): "reports which URIs have triggered
     *  the job. This will be null if either no URIs have triggered it (it went
     *  off due to a deadline or other reason), or the number of changed URIs
     *  is too large to report." Wave 5 (2026-09-26): this was a constant null,
     *  because the scheduler never started a job BECAUSE of a content change
     *  (it ran trigger jobs on a timer instead); it now does, and reports what
     *  changed. WorkManager's SystemJobService copies both arrays into
     *  WorkerParameters.RuntimeExtras, and Fossify's NewPhotoFetcher reads them
     *  to find the new photos. */
    public android.net.Uri[] getTriggeredContentUris() { return triggeredContentUris; }
    /** AOSP (:459-469): "It will only be null if no authorities have triggered
     *  it -- that is, the job executed for some other reason, such as a
     *  deadline expiring." */
    public String[] getTriggeredContentAuthorities() { return triggeredContentAuthorities; }

    /** AOSP: meaningful only inside onStopJob; STOP_REASON_UNDEFINED
     *  elsewhere. */
    public int getStopReason() { return stopReason; }

    public java.util.List<JobWorkItem> getJobWorkItems() {
        return record != null ? record.snapshotWork() : null;
    }

    /** AOSP getClipData() / getClipGrantFlags(): the ClipData the job was
     *  scheduled with (JobInfo.Builder.setClipData) and its grant flags. Both
     *  were hardcoded; JobInfo now carries them. */
    public android.content.ClipData getClipData() {
        return job != null ? job.getClipData() : null;
    }
    public int getClipGrantFlags() { return job != null ? job.getClipGrantFlags() : 0; }

    /** AOSP dequeueWork (API 26): the next JobWorkItem for an
     *  enqueued-work job, or null when the queue is drained.
     *
     *  AOSP JobServiceContext.doDequeueWork (android13-release
     *  apex/jobscheduler/service/.../JobServiceContext.java:495-515):
     *      final JobWorkItem work = mRunningJob.dequeueWorkLocked();
     *      if (work == null && !mRunningJob.hasExecutingWorkLocked()) {
     *          mParams.setStopReason(..., "last work dequeued");
     *          // This will finish the job.
     *          doCallbackLocked(false, "last work dequeued");
     *      }
     *  A null return ends the job ONLY once nothing dequeued earlier is
     *  still outstanding (JobStatus.hasExecutingWorkLocked); the app never
     *  calls jobFinished for that case itself (androidx.core.app
     *  .JobIntentService's compat engine relies on exactly this -- it
     *  stops its work loop on a null dequeueWork() and never touches
     *  jobFinished). If some other dequeued item is still being processed
     *  (e.g. on another thread) a null return here does NOT end the job:
     *  it ends only when that last outstanding item is completeWork()'d
     *  and a later dequeueWork() call finds the queue still empty. */
    public android.app.job.JobWorkItem dequeueWork() {
        if (record == null) return null;
        android.app.job.JobWorkItem w = record.dequeueWork();
        if (AUTOFINISH_ON_DRAIN && w == null && !record.hasExecutingWork()) {
            record.owner.jobFinished(this, false);
        }
        return w;
    }

    /** AOSP completeWork (API 26): marks a dequeued work item finished.
     *  AOSP throws IllegalArgumentException when the item was not dequeued
     *  from this job; we log instead, because an app that ran against our
     *  previous always-null dequeueWork may hold items we never handed
     *  out and throwing would turn a silent gap into a new crash. */
    public void completeWork(android.app.job.JobWorkItem work) {
        if (record != null) record.completeWork(work);
    }

    // ---- AOSP API-36 surface completion: Parcelable ----
    // AOSP's JobParameters crosses the Binder from the JobScheduler service to
    // the app's JobService. Mac.apk's scheduler is in-process
    // (MacApkJobScheduler), so nothing here NEEDS the marshalling -- but the
    // Parcelable contract is public API and an app may parcel one itself.
    //
    // The two fields that are NOT written are `record` and `network`: the job
    // record is a live in-process handle with no wire form (AOSP's equivalent is
    // an IBinder callback token, which is likewise not app-reconstructible), and
    // a Network is a handle to a live connectivity object rather than a value.
    // Reading either back on the far side would produce a JobParameters that
    // silently cannot complete its work, so they are deliberately dropped --
    // dequeueWork() on an unparcelled instance returns null, which is the same
    // "no work" answer a caller already handles.

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel out, int flags) {
        out.writeTypedObject(job, flags);
        out.writeInt(stopReason);
        out.writeInt(overrideDeadlineExpired ? 1 : 0);
    }

    private JobParameters(android.os.Parcel in) {
        this.job = in.readTypedObject(JobInfo.CREATOR);
        this.stopReason = in.readInt();
        this.overrideDeadlineExpired = in.readInt() != 0;
    }

    public static final android.os.Parcelable.Creator<JobParameters> CREATOR =
            new android.os.Parcelable.Creator<JobParameters>() {
        @Override public JobParameters createFromParcel(android.os.Parcel in) {
            return new JobParameters(in);
        }
        @Override public JobParameters[] newArray(int size) { return new JobParameters[size]; }
    };

    /** AOSP JobParameters.STOP_REASON_TIMEOUT_ABANDONED (API 36): the job was
     *  stopped because it timed out after its calling context was abandoned. */
    public static final int STOP_REASON_TIMEOUT_ABANDONED = 16;
}
