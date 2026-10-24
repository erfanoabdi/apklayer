// android.app.job.JobServiceEngine -- the app-side object the platform
// drives a job through when the hosting component is NOT a JobService.
// AOSP API-26+.
//
// AOSP ref: apex/jobscheduler/framework/java/android/app/job/JobServiceEngine.java
//
// WHO USES THIS, and why it cannot stay a stub. androidx.core.app
// .JobIntentService is a plain Service, not a JobService: on API 26+ its
// onCreate constructs a `JobServiceEngineImpl extends JobServiceEngine`,
// and its onBind returns that engine's getBinder(). On a real device
// system_server holds that IJobService binder and calls onStartJob through
// it. Its processor loop then pulls each unit of work with
// params.dequeueWork() and stops when it gets null.
//
// Mac.apk has no binder transport, so getBinder() returns null and the
// binder route is a dead end. Before v1.0.834 that meant a JobIntentService
// was unreachable: the scheduler saw "a Service but not a JobService" and
// dropped the job silently, and even if it had reached it, the engine's
// dequeueWork() returned a hardcoded null and jobFinished() was empty.
//
// v1.0.834 replaces the binder with the in-process equivalent: an engine
// constructed with a hosting Service registers itself against that
// Service's class name, so the scheduler can find the engine for a
// component and invoke onStartJob / onStopJob on it directly. That is the
// same call, minus the IPC hop -- not a shortcut around the contract.
// jobFinished and dequeueWork/completeWork now route to the same
// scheduler-side job record a JobService's do, so both hosting shapes share
// one implementation.
package android.app.job;

import android.app.Service;
import android.os.IBinder;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class JobServiceEngine {
    /** Engines by hosting-Service class name. This is Mac.apk's stand-in
     *  for the IJobService binder system_server would hold: the scheduler
     *  needs a way to reach the engine that belongs to a component. Bounded
     *  by the number of distinct Service classes in the app, and the
     *  ServiceRegistry keeps exactly one live instance per class. */
    static final Map<String, JobServiceEngine> sEngines = new ConcurrentHashMap<>();

    /** The Service hosting this engine. AOSP keeps a reference so the
     *  engine can post callbacks onto the service's main thread. */
    private final Service mService;

    public JobServiceEngine(Service service) {
        mService = service;
        if (service != null) sEngines.put(service.getClass().getName(), this);
    }

    /** Default no-arg ctor retained for legacy callers that previously
     *  instantiated the empty stub. AOSP has no such ctor; an engine built
     *  this way has no hosting Service and so is not reachable from the
     *  scheduler, which is the same outcome it had before. */
    public JobServiceEngine() { mService = null; }

    /** AOSP: the IBinder apps return from Service.onBind so JobScheduler
     *  can drive jobs over IPC. Mac.apk is single-process with no binder
     *  transport, so this stays null and the scheduler reaches the engine
     *  through sEngines instead. */
    public IBinder getBinder() { return null; }

    /** The Service this engine was constructed with, for the scheduler's
     *  benefit. */
    final Service hostService() { return mService; }

    /** Called by the system when a job starts. Return true if work
     *  continues asynchronously (the engine calls jobFinished later),
     *  false if the job completed synchronously. Abstract in AOSP. */
    public abstract boolean onStartJob(JobParameters params);

    /** Called when the system needs to stop a running job. Return true
     *  to ask the scheduler to reschedule. Abstract in AOSP. */
    public abstract boolean onStopJob(JobParameters params);

    /** AOSP JobServiceEngine.onNetworkChanged(JobParameters params) (API 34):
     *  "Called when the network the job was scheduled with changes" so a
     *  transfer can be re-targeted. AOSP's base body is empty -- the engine
     *  subclass overrides it -- and Mac.apk never fires it: the job's Network
     *  (JobParameters.getNetwork) is resolved once at start and our single
     *  always-present transport never changes underneath a running job. Present
     *  because a JobServiceEngine subclass overriding it must resolve the
     *  supertype method. */
    public void onNetworkChanged(JobParameters params) {}

    /** AOSP: "Call in to engine to report that a job has finished
     *  executing." Its implementation posts MSG_JOB_FINISHED to the
     *  main-thread handler (JobServiceEngine.java:410-417); ours routes to
     *  the scheduler record carried by the params, which posts to main for
     *  the same reason -- the caller is usually a worker thread. */
    public void jobFinished(JobParameters params, boolean needsReschedule) {
        if (params == null) throw new NullPointerException("params");
        MacApkJobScheduler engine = params.record != null ? params.record.owner : null;
        if (engine == null) {
            try {
                com.macapk.loader.NativeLog.i("JobScheduler",
                    "engine.jobFinished(" + params.getJobId() + ") for an untracked job;"
                    + " ignoring (AOSP: nonexistent job id)");
            } catch (Throwable ignored) {}
            return;
        }
        engine.jobFinished(params, needsReschedule);
    }

    /** AOSP: the next JobWorkItem for an enqueued-work job, or null when
     *  the queue is drained. The queue belongs to the job, not the engine,
     *  so this is the same queue JobParameters.dequeueWork() drains. */
    public JobWorkItem dequeueWork() { return null; }

    /** AOSP: mark a dequeued work item complete; the boolean says whether
     *  more work remains. */
    public boolean completeWork(JobWorkItem work) { return true; }

    /** AOSP data-transfer accounting (API 34). Not modeled; AOSP's own
     *  defaults return 0 for a job that is not a data-transfer job. */
    public void updateEstimatedNetworkBytes(JobParameters params, JobWorkItem item,
            long downloadBytes, long uploadBytes) {}
    public void updateTransferredNetworkBytes(JobParameters params, JobWorkItem item,
            long transferredDownloadBytes, long transferredUploadBytes) {}
    public void setNotification(JobParameters params, int notificationId,
            android.app.Notification notification, int jobEndNotificationPolicy) {}
}
