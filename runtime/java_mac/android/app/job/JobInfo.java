// android.app.job.JobInfo -- description of a scheduled job.
// AOSP API-21+.
//
// v1.0.103: now actually STORES the values its Builder receives.
// Before v1.0.103 every setter on Builder returned `this` but
// discarded the value, and JobInfo.getExtras() always returned an
// empty PersistableBundle. WorkManager's SystemJobScheduler calls
// builder.setExtras(extras) with EXTRA_WORK_SPEC_ID set to the
// WorkSpec UUID; SystemJobService.onStartJob then reads it back
// via JobParameters.getExtras().getString(EXTRA_WORK_SPEC_ID).
// With our previous no-op setter, every onStartJob saw an empty
// bundle and logged `WorkSpec id not found!` then returned false,
// dropping the job. GreedyScheduler's parallel constraint-tracking
// path still launched the workers, so the regression was hidden
// behind log spam -- but any path that ONLY routes through
// JobScheduler (e.g. jobs with constraints requiring real OS-level
// gating like requiresDeviceIdle / requiresCharging) was silently
// broken. Real fix: store + return everything the Builder takes.
package android.app.job;

import android.content.ComponentName;
import android.os.PersistableBundle;

public class JobInfo implements android.os.Parcelable {
    /** MACAPK_JOB_CONTENT_TRIGGER, the content-URI trigger gate (default ON; see
     *  MacApkJobScheduler's header). Read HERE rather than in the scheduler so
     *  building a JobInfo never forces JobScheduler's class init (which
     *  constructs the STUB engine); MacApkJobScheduler.CONTENT_TRIGGER reads
     *  this one value. Package-private: not API surface. */
    static final boolean MACAPK_CONTENT_TRIGGER =
            !"0".equals(System.getenv("MACAPK_JOB_CONTENT_TRIGGER"));

    public static final int BACKOFF_POLICY_LINEAR      = 0;
    public static final int BACKOFF_POLICY_EXPONENTIAL = 1;
    public static final int NETWORK_TYPE_NONE          = 0;
    public static final int NETWORK_TYPE_ANY           = 1;
    public static final int NETWORK_TYPE_UNMETERED     = 2;
    public static final int NETWORK_TYPE_NOT_ROAMING   = 3;
    /** AOSP JobInfo.java:157. NETWORK_TYPE_METERED is a deprecated ALIAS
     *  for this same value (JobInfo.java:170), which is why both are 4. */
    public static final int NETWORK_TYPE_CELLULAR      = 4;
    public static final int NETWORK_TYPE_METERED       = NETWORK_TYPE_CELLULAR;

    // ---- backoff + period bounds, verbatim from AOSP JobInfo.java --------
    // 178: DEFAULT_INITIAL_BACKOFF_MILLIS = 30000L
    // 183: MAX_BACKOFF_DELAY_MILLIS = 5 * 60 * 60 * 1000  (5 hours)
    // 211: MIN_PERIOD_MILLIS = 15 * 60 * 1000L            (private in AOSP)
    // 214: MIN_FLEX_MILLIS   =  5 * 60 * 1000L            (private in AOSP)
    // 222: MIN_BACKOFF_MILLIS = 10 * 1000L
    // 258: DEFAULT_BACKOFF_POLICY = BACKOFF_POLICY_EXPONENTIAL
    // These are load-bearing for the scheduler's reschedule-on-failure
    // math (MacApkJobScheduler.backoffDelayMillis mirrors
    // JobSchedulerService.getRescheduleJobForFailureLocked, which reads
    // MAX_BACKOFF_DELAY_MILLIS and clamps the per-policy minimum to
    // MIN_BACKOFF_MILLIS).
    public static final long DEFAULT_INITIAL_BACKOFF_MILLIS = 30000L;
    public static final long MAX_BACKOFF_DELAY_MILLIS = 5 * 60 * 60 * 1000L;
    public static final long MIN_BACKOFF_MILLIS = 10 * 1000L;
    public static final int DEFAULT_BACKOFF_POLICY = BACKOFF_POLICY_EXPONENTIAL;
    private static final long MIN_PERIOD_MILLIS = 15 * 60 * 1000L;
    private static final long MIN_FLEX_MILLIS = 5 * 60 * 1000L;

    /** AOSP JobInfo.java:232/243/251 -- the static accessors apps and
     *  WorkManager use to clamp a requested period/flex/backoff. */
    public static long getMinPeriodMillis()  { return MIN_PERIOD_MILLIS; }
    public static long getMinFlexMillis()    { return MIN_FLEX_MILLIS; }
    public static long getMinBackoffMillis() { return MIN_BACKOFF_MILLIS; }

    private final int id;
    private final ComponentName service;
    private final PersistableBundle extras;
    private final int requiredNetworkType;
    private final boolean requiresCharging;
    private final boolean requiresDeviceIdle;
    private final boolean requiresBatteryNotLow;
    private final boolean requiresStorageNotLow;
    private final boolean periodic;
    private final long intervalMillis;
    private final long flexMillis;
    private final long minLatencyMillis;
    private final long maxExecutionDelayMillis;
    private final long initialBackoffMillis;
    private final int backoffPolicy;
    private final boolean persisted;
    private final boolean importantWhileForeground;
    private final boolean prefetch;
    private final boolean expedited;
    private final TriggerContentUri[] triggerContentUris;
    private final long triggerContentUpdateDelay;
    private final long triggerContentMaxDelay;

    private JobInfo(Builder b) {
        this.id = b.id;
        this.service = b.service;
        this.extras = b.extras != null ? b.extras : new PersistableBundle();
        this.requiredNetworkType = b.requiredNetworkType;
        this.requiresCharging = b.requiresCharging;
        this.requiresDeviceIdle = b.requiresDeviceIdle;
        this.requiresBatteryNotLow = b.requiresBatteryNotLow;
        this.requiresStorageNotLow = b.requiresStorageNotLow;
        this.periodic = b.periodic;
        this.intervalMillis = b.intervalMillis;
        this.flexMillis = b.flexMillis;
        this.minLatencyMillis = b.minLatencyMillis;
        this.maxExecutionDelayMillis = b.maxExecutionDelayMillis;
        this.initialBackoffMillis = b.initialBackoffMillis;
        this.backoffPolicy = b.backoffPolicy;
        this.persisted = b.persisted;
        this.importantWhileForeground = b.importantWhileForeground;
        this.prefetch = b.prefetch;
        this.expedited = b.expedited;
        this.triggerContentUris = b.triggerContentUris.isEmpty() ? null
                : b.triggerContentUris.toArray(new TriggerContentUri[0]);
        this.triggerContentUpdateDelay = b.triggerContentUpdateDelay;
        this.triggerContentMaxDelay = b.triggerContentMaxDelay;
        this.priority = b.priority;
        this.userInitiated = b.userInitiated;
        this.estimatedDownloadBytes = b.estimatedDownloadBytes;
        this.estimatedUploadBytes = b.estimatedUploadBytes;
        this.minimumChunkBytes = b.minimumChunkBytes;
        this.requiredNetwork = b.requiredNetwork;
        this.transientExtras = b.transientExtras != null
                ? b.transientExtras : new android.os.Bundle();
        this.clipData = b.clipData;
        this.clipGrantFlags = b.clipGrantFlags;
        this.traceTag = b.traceTag;
        this.debugTags = new java.util.LinkedHashSet<String>(b.debugTags);
    }

    // Legacy direct-construct ctor kept so anything still calling
    // `new JobInfo(id, svc)` outside the Builder path keeps working.
    public JobInfo(int id, ComponentName service) {
        this.id = id;
        this.service = service;
        this.extras = new PersistableBundle();
        this.requiredNetworkType = NETWORK_TYPE_NONE;
        this.requiresCharging = false;
        this.requiresDeviceIdle = false;
        this.requiresBatteryNotLow = false;
        this.requiresStorageNotLow = false;
        this.periodic = false;
        this.intervalMillis = 0;
        this.flexMillis = 0;
        this.minLatencyMillis = 0;
        this.maxExecutionDelayMillis = 0;
        // AOSP JobInfo.Builder:1283-1284 defaults these even when the app
        // never calls setBackoffCriteria, and the scheduler's
        // reschedule-on-failure math reads them -- a 0 initial backoff
        // would make every retry fire immediately.
        this.initialBackoffMillis = DEFAULT_INITIAL_BACKOFF_MILLIS;
        this.backoffPolicy = DEFAULT_BACKOFF_POLICY;
        this.persisted = false;
        this.importantWhileForeground = false;
        this.prefetch = false;
        this.expedited = false;
        this.triggerContentUris = null;
        // "Unset", as the Builder now says it (see Builder.triggerContentUpdateDelay).
        this.triggerContentUpdateDelay = MACAPK_CONTENT_TRIGGER ? -1L : 0L;
        this.triggerContentMaxDelay = MACAPK_CONTENT_TRIGGER ? -1L : 0L;
        this.priority = PRIORITY_DEFAULT;
        this.userInitiated = false;
        this.estimatedDownloadBytes = NETWORK_BYTES_UNKNOWN;
        this.estimatedUploadBytes = NETWORK_BYTES_UNKNOWN;
        this.minimumChunkBytes = NETWORK_BYTES_UNKNOWN;
        this.requiredNetwork = null;
        this.transientExtras = new android.os.Bundle();
        this.clipData = null;
        this.clipGrantFlags = 0;
        this.traceTag = null;
        this.debugTags = new java.util.LinkedHashSet<String>();
    }

    public int getId()                      { return id; }
    public ComponentName getService()       { return service; }
    public PersistableBundle getExtras()    { return extras; }
    public int getNetworkType()             { return requiredNetworkType; }
    public boolean isRequireCharging()      { return requiresCharging; }
    public boolean isRequireDeviceIdle()    { return requiresDeviceIdle; }
    public boolean isRequireBatteryNotLow() { return requiresBatteryNotLow; }
    public boolean isRequireStorageNotLow() { return requiresStorageNotLow; }
    public boolean isPeriodic()             { return periodic; }
    public long getIntervalMillis()         { return intervalMillis; }
    public long getFlexMillis()             { return flexMillis; }
    public long getMinLatencyMillis()       { return minLatencyMillis; }
    public long getMaxExecutionDelayMillis(){ return maxExecutionDelayMillis; }
    public long getInitialBackoffMillis()   { return initialBackoffMillis; }
    public int getBackoffPolicy()           { return backoffPolicy; }
    public boolean isPersisted()            { return persisted; }
    public boolean isImportantWhileForeground() { return importantWhileForeground; }
    public boolean isPrefetch()             { return prefetch; }
    /** AOSP isExpedited (API 31). */
    public boolean isExpedited()            { return expedited; }
    /** AOSP getTriggerContentUris (API 24): null when the job has none. */
    public TriggerContentUri[] getTriggerContentUris() { return triggerContentUris; }
    public long getTriggerContentUpdateDelay() { return triggerContentUpdateDelay; }
    public long getTriggerContentMaxDelay()    { return triggerContentMaxDelay; }

    // ================================================================
    // AOSP API-36 surface completion (android.app.job.JobInfo).
    // AOSP ref: apex/jobscheduler/framework/java/android/app/job/JobInfo.java
    // (android16-release). Everything below is state the app SETS on the
    // Builder and READS back off the JobInfo -- WorkManager's
    // SystemJobScheduler does exactly that round trip for the network-bytes
    // estimate and the priority -- so each one is stored for real, and the
    // Builder halves that previously discarded their argument now keep it.
    // ================================================================

    /** AOSP PRIORITY_* (API 33): the job's priority within this app's own jobs.
     *  Note these are NOT the same numbering as importance or process state. */
    public static final int PRIORITY_MIN = 100;
    public static final int PRIORITY_LOW = 200;
    public static final int PRIORITY_DEFAULT = 300;
    public static final int PRIORITY_HIGH = 400;
    public static final int PRIORITY_MAX = 500;

    /** AOSP NETWORK_BYTES_UNKNOWN: "Sentinel value indicating that bytes are
     *  unknown", returned by the estimate getters when the app supplied none. */
    public static final int NETWORK_BYTES_UNKNOWN = -1;

    /** AOSP getPriority(): PRIORITY_DEFAULT unless the Builder set one. */
    public int getPriority() { return priority; }

    /** AOSP isUserInitiated() (API 34): whether this is a user-initiated job
     *  (an explicit user action such as a download the user started), which the
     *  system schedules more aggressively. */
    public boolean isUserInitiated() { return userInitiated; }

    /** AOSP getEstimatedNetworkDownloadBytes(): the app's own estimate, or
     *  NETWORK_BYTES_UNKNOWN. */
    public long getEstimatedNetworkDownloadBytes() { return estimatedDownloadBytes; }
    /** AOSP getEstimatedNetworkUploadBytes(). */
    public long getEstimatedNetworkUploadBytes() { return estimatedUploadBytes; }
    /** AOSP getMinimumNetworkChunkBytes(): "the smallest piece of data that
     *  cannot be easily paused and resumed", or NETWORK_BYTES_UNKNOWN. */
    public long getMinimumNetworkChunkBytes() { return minimumChunkBytes; }

    /** AOSP getRequiredNetwork() (API 28): the NetworkRequest the job needs, or
     *  null. Builder.setRequiredNetwork above already folds a request down to
     *  NETWORK_TYPE_ANY for the scheduler's constraint check; keep the original
     *  object as well so this getter answers what the app handed in rather than
     *  a reconstruction. */
    public android.net.NetworkRequest getRequiredNetwork() { return requiredNetwork; }

    /** AOSP getTransientExtras(): "Bundle of extras which are returned to your
     *  application at execution time, but not persisted by the system" -- the
     *  Bundle (not PersistableBundle) sibling of getExtras(). Never null. */
    public android.os.Bundle getTransientExtras() { return transientExtras; }

    /** AOSP getClipData() / getClipGrantFlags(): a ClipData whose URI grants
     *  are handed to the job, and the Intent.FLAG_GRANT_* flags they carry. */
    public android.content.ClipData getClipData() { return clipData; }
    public int getClipGrantFlags() { return clipGrantFlags; }

    /** AOSP getTraceTag() (API 35): "the trace tag" the system attaches to this
     *  job's Perfetto slice, or null. Builder.setTraceTag above discarded it;
     *  it is now kept so this getter can answer. */
    public String getTraceTag() { return traceTag; }

    /** AOSP getDebugTags() (API 35): "the set of debug tags attached to this
     *  job" -- an unmodifiable, never-null set. */
    public java.util.Set<String> getDebugTags() {
        return java.util.Collections.unmodifiableSet(debugTags);
    }

    private final int priority;
    private final boolean userInitiated;
    private final long estimatedDownloadBytes;
    private final long estimatedUploadBytes;
    private final long minimumChunkBytes;
    private final android.net.NetworkRequest requiredNetwork;
    private final android.os.Bundle transientExtras;
    private final android.content.ClipData clipData;
    private final int clipGrantFlags;
    private final String traceTag;
    private final java.util.Set<String> debugTags;

    @Override public int describeContents() { return 0; }

    /** AOSP JobInfo.writeToParcel: JobInfo travels to the JobScheduler service
     *  over Binder, and an app may also stash one in a Bundle. Mac.apk's
     *  scheduler is in-process (MacApkJobScheduler), so nothing here NEEDS the
     *  marshalling -- but the pair must exist and must round-trip, because the
     *  Parcelable contract is what an app relies on. Written in the field order
     *  of this class; readFromParcel below mirrors it exactly.
     *
     *  NetworkRequest and ClipData are written through the generic parcelable
     *  slot rather than a typed one, so a null on either side stays a null. */
    @Override public void writeToParcel(android.os.Parcel out, int flags) {
        out.writeInt(id);
        out.writeTypedObject(service, flags);
        out.writePersistableBundle(extras);
        out.writeBundle(transientExtras);
        out.writeInt(requiredNetworkType);
        out.writeInt(requiresCharging ? 1 : 0);
        out.writeInt(requiresDeviceIdle ? 1 : 0);
        out.writeInt(requiresBatteryNotLow ? 1 : 0);
        out.writeInt(requiresStorageNotLow ? 1 : 0);
        out.writeInt(periodic ? 1 : 0);
        out.writeLong(intervalMillis);
        out.writeLong(flexMillis);
        out.writeLong(minLatencyMillis);
        out.writeLong(maxExecutionDelayMillis);
        out.writeLong(initialBackoffMillis);
        out.writeInt(backoffPolicy);
        out.writeInt(persisted ? 1 : 0);
        out.writeInt(importantWhileForeground ? 1 : 0);
        out.writeInt(prefetch ? 1 : 0);
        out.writeInt(expedited ? 1 : 0);
        out.writeTypedArray(triggerContentUris, flags);
        out.writeLong(triggerContentUpdateDelay);
        out.writeLong(triggerContentMaxDelay);
        out.writeInt(priority);
        out.writeInt(userInitiated ? 1 : 0);
        out.writeLong(estimatedDownloadBytes);
        out.writeLong(estimatedUploadBytes);
        out.writeLong(minimumChunkBytes);
        out.writeInt(clipGrantFlags);
        out.writeString(traceTag);
        out.writeStringArray(debugTags.toArray(new String[0]));
    }

    /** The read half of writeToParcel, used by CREATOR. A JobInfo has no
     *  no-argument constructor (AOSP's is Builder-only too), so this rebuilds
     *  through the Builder, which is also what keeps the two halves honest. */
    private static JobInfo fromParcel(android.os.Parcel in) {
        int id = in.readInt();
        ComponentName service = in.readTypedObject(ComponentName.CREATOR);
        Builder b = new Builder(id, service);
        b.extras = in.readPersistableBundle();
        b.transientExtras = in.readBundle();
        b.requiredNetworkType = in.readInt();
        b.requiresCharging = in.readInt() != 0;
        b.requiresDeviceIdle = in.readInt() != 0;
        b.requiresBatteryNotLow = in.readInt() != 0;
        b.requiresStorageNotLow = in.readInt() != 0;
        b.periodic = in.readInt() != 0;
        b.intervalMillis = in.readLong();
        b.flexMillis = in.readLong();
        b.minLatencyMillis = in.readLong();
        b.maxExecutionDelayMillis = in.readLong();
        b.initialBackoffMillis = in.readLong();
        b.backoffPolicy = in.readInt();
        b.persisted = in.readInt() != 0;
        b.importantWhileForeground = in.readInt() != 0;
        b.prefetch = in.readInt() != 0;
        b.expedited = in.readInt() != 0;
        TriggerContentUri[] uris = in.createTypedArray(TriggerContentUri.CREATOR);
        if (uris != null) {
            for (TriggerContentUri u : uris) if (u != null) b.triggerContentUris.add(u);
        }
        b.triggerContentUpdateDelay = in.readLong();
        b.triggerContentMaxDelay = in.readLong();
        b.priority = in.readInt();
        b.userInitiated = in.readInt() != 0;
        b.estimatedDownloadBytes = in.readLong();
        b.estimatedUploadBytes = in.readLong();
        b.minimumChunkBytes = in.readLong();
        b.clipGrantFlags = in.readInt();
        b.traceTag = in.readString();
        String[] tags = in.createStringArray();
        if (tags != null) {
            for (String t : tags) if (t != null) b.debugTags.add(t);
        }
        return b.build();
    }

    public static final android.os.Parcelable.Creator<JobInfo> CREATOR =
            new android.os.Parcelable.Creator<JobInfo>() {
        @Override public JobInfo createFromParcel(android.os.Parcel in) { return fromParcel(in); }
        @Override public JobInfo[] newArray(int size) { return new JobInfo[size]; }
    };

    public static class Builder {
        final int id;
        final ComponentName service;
        PersistableBundle extras;
        int requiredNetworkType = NETWORK_TYPE_NONE;
        boolean requiresCharging;
        boolean requiresDeviceIdle;
        boolean requiresBatteryNotLow;
        boolean requiresStorageNotLow;
        boolean periodic;
        long intervalMillis;
        long flexMillis;
        long minLatencyMillis;
        long maxExecutionDelayMillis;
        long initialBackoffMillis = DEFAULT_INITIAL_BACKOFF_MILLIS;
        int backoffPolicy = DEFAULT_BACKOFF_POLICY;
        boolean persisted;
        boolean importantWhileForeground;
        boolean prefetch;
        boolean expedited;
        final java.util.ArrayList<TriggerContentUri> triggerContentUris =
                new java.util.ArrayList<>();
        // AOSP Builder (android14-release :1194-1195) starts both at -1, which
        // means "unset": the scheduler then applies the platform defaults, a 10 s
        // update delay and a 2 min maximum (JobStatus :1496-1510). 0 is NOT unset
        // -- it is a request for the 500 ms / 1 s floors -- so a Builder that
        // started at 0 turned every app that never calls the setters into a
        // 20x-faster trigger. Gated with the trigger engine itself
        // (MACAPK_JOB_CONTENT_TRIGGER=0 restores the old 0/0).
        long triggerContentUpdateDelay = MACAPK_CONTENT_TRIGGER ? -1L : 0L;
        long triggerContentMaxDelay = MACAPK_CONTENT_TRIGGER ? -1L : 0L;
        // API-33..36 additions; see the JobInfo getters above.
        int priority = PRIORITY_DEFAULT;
        boolean userInitiated;
        long estimatedDownloadBytes = NETWORK_BYTES_UNKNOWN;
        long estimatedUploadBytes = NETWORK_BYTES_UNKNOWN;
        long minimumChunkBytes = NETWORK_BYTES_UNKNOWN;
        android.net.NetworkRequest requiredNetwork;
        android.os.Bundle transientExtras;
        android.content.ClipData clipData;
        int clipGrantFlags;
        String traceTag;
        final java.util.LinkedHashSet<String> debugTags = new java.util.LinkedHashSet<String>();

        public Builder(int id, ComponentName service) {
            this.id = id; this.service = service;
        }
        public Builder setRequiredNetworkType(int type) { this.requiredNetworkType = type; return this; }
        /** AOSP setTraceTag(String) (API 35): "Sets the trace tag for the job."
         *  Was discarding its argument, which made JobInfo.getTraceTag()
         *  unimplementable. AOSP silently ignores an over-long or malformed tag
         *  rather than throwing. */
        public Builder setTraceTag(String traceTag) { this.traceTag = traceTag; return this; }

        /** AOSP addDebugTag(String tag) (API 35): "Add a debug tag to this job"
         *  -- free-form labels surfaced in dumpsys. AOSP rejects a null or
         *  blank tag with IllegalArgumentException and caps the set at
         *  32 tags. */
        public Builder addDebugTag(String tag) {
            if (tag == null || tag.trim().isEmpty()) {
                throw new IllegalArgumentException("Debug tag must not be blank");
            }
            debugTags.add(tag.trim());
            return this;
        }
        /** AOSP removeDebugTag(String tag). */
        public Builder removeDebugTag(String tag) { debugTags.remove(tag); return this; }

        /** AOSP setPriority(int) (API 33): "Sets the priority of the job."
         *  AOSP validates the value is one of the PRIORITY_* constants. */
        public Builder setPriority(int priority) {
            if (priority != PRIORITY_MIN && priority != PRIORITY_LOW
                    && priority != PRIORITY_DEFAULT && priority != PRIORITY_HIGH
                    && priority != PRIORITY_MAX) {
                throw new IllegalArgumentException("Invalid priority level provided: " + priority);
            }
            this.priority = priority;
            return this;
        }

        /** AOSP setUserInitiated(boolean) (API 34): "Setting this to true
         *  indicates that this job is designed to support the app's core
         *  functionality and is initiated by the user." */
        public Builder setUserInitiated(boolean userInitiated) {
            this.userInitiated = userInitiated;
            return this;
        }

        /** AOSP setEstimatedNetworkBytes(long downloadBytes, long uploadBytes):
         *  the app's own transfer estimate, used by the system to pick a moment
         *  and a network. AOSP rejects a negative value that is not
         *  NETWORK_BYTES_UNKNOWN. */
        public Builder setEstimatedNetworkBytes(long downloadBytes, long uploadBytes) {
            if (downloadBytes < 0 && downloadBytes != NETWORK_BYTES_UNKNOWN) {
                throw new IllegalArgumentException("Invalid download bytes");
            }
            if (uploadBytes < 0 && uploadBytes != NETWORK_BYTES_UNKNOWN) {
                throw new IllegalArgumentException("Invalid upload bytes");
            }
            this.estimatedDownloadBytes = downloadBytes;
            this.estimatedUploadBytes = uploadBytes;
            return this;
        }

        /** AOSP setMinimumNetworkChunkBytes(long): "the smallest piece of data
         *  that cannot be easily paused and resumed." AOSP rejects 0 and any
         *  negative other than NETWORK_BYTES_UNKNOWN. */
        public Builder setMinimumNetworkChunkBytes(long chunkSizeBytes) {
            if (chunkSizeBytes != NETWORK_BYTES_UNKNOWN && chunkSizeBytes <= 0) {
                throw new IllegalArgumentException("Minimum chunk size must be positive");
            }
            this.minimumChunkBytes = chunkSizeBytes;
            return this;
        }

        /** AOSP setTransientExtras(Bundle): extras handed to the job at
         *  execution time but never persisted (unlike setExtras). */
        public Builder setTransientExtras(android.os.Bundle extras) {
            this.transientExtras = extras;
            return this;
        }

        /** AOSP setClipData(ClipData clip, int grantFlags): "Set a ClipData
         *  associated with this Job. The main purpose of providing a ClipData is
         *  to allow granting of URI permissions." AOSP requires the grant flags
         *  to be a subset of the read/write URI grants. */
        public Builder setClipData(android.content.ClipData clip, int grantFlags) {
            this.clipData = clip;
            this.clipGrantFlags = grantFlags;
            return this;
        }
        /** AOSP setRequiredNetwork (API 28) takes a full NetworkRequest.
         *  Mac.apk models one always-present unmetered WiFi transport
         *  (ConnectivityManager), so any NetworkRequest a job can express
         *  reduces to "a network is required": record it as NETWORK_TYPE_ANY
         *  rather than discarding it, so the scheduler still knows the job
         *  wants a Network handed to it. */
        public Builder setRequiredNetwork(android.net.NetworkRequest networkRequest) {
            this.requiredNetwork = networkRequest;
            if (networkRequest != null && this.requiredNetworkType == NETWORK_TYPE_NONE) {
                this.requiredNetworkType = NETWORK_TYPE_ANY;
            }
            return this;
        }
        public Builder setExpedited(boolean expedited) { this.expedited = expedited; return this; }
        public Builder setRequiresCharging(boolean v) { this.requiresCharging = v; return this; }
        public Builder setRequiresDeviceIdle(boolean v) { this.requiresDeviceIdle = v; return this; }
        public Builder setRequiresBatteryNotLow(boolean v) { this.requiresBatteryNotLow = v; return this; }
        public Builder setRequiresStorageNotLow(boolean v) { this.requiresStorageNotLow = v; return this; }
        public Builder setPeriodic(long ms) {
            this.periodic = true; this.intervalMillis = ms; this.flexMillis = ms; return this;
        }
        public Builder setPeriodic(long ms, long flexMs) {
            this.periodic = true; this.intervalMillis = ms; this.flexMillis = flexMs; return this;
        }
        public Builder setMinimumLatency(long ms) { this.minLatencyMillis = ms; return this; }
        public Builder setOverrideDeadline(long ms) { this.maxExecutionDelayMillis = ms; return this; }
        public Builder setBackoffCriteria(long initialBackoffMs, int policy) {
            this.initialBackoffMillis = initialBackoffMs; this.backoffPolicy = policy; return this;
        }
        public Builder setPersisted(boolean v) { this.persisted = v; return this; }
        public Builder setExtras(PersistableBundle extras) { this.extras = extras; return this; }
        public Builder setImportantWhileForeground(boolean v) { this.importantWhileForeground = v; return this; }
        public Builder setPrefetch(boolean v) { this.prefetch = v; return this; }
        /** AOSP addTriggerContentUri (API 24): observe a content URI and run the
         *  job when it changes -- and ONLY then: "it will not execute until
         *  there has been a change report for one or more of them" (android14
         *  JobInfo.java:1730-1733). MacApkJobScheduler registers a
         *  ContentObserver per trigger and starts the job after the update
         *  delay, reporting the change through JobParameters
         *  .getTriggeredContentUris().
         *
         *  CORRECTED 2026-09-26: this comment used to say the trigger "never
         *  fires the job". The opposite was true: the scheduler ignored the
         *  trigger and ran the job on its one-shot timer at once, and an app
         *  following the documented re-schedule-from-onStartJob idiom looped
         *  (Fossify NewPhotoFetcher, ~9.5 starts a second). */
        public Builder addTriggerContentUri(JobInfo.TriggerContentUri uri) {
            if (uri != null) this.triggerContentUris.add(uri);
            return this;
        }
        public Builder setTriggerContentUpdateDelay(long durationMs) {
            this.triggerContentUpdateDelay = durationMs; return this;
        }
        public Builder setTriggerContentMaxDelay(long durationMs) {
            this.triggerContentMaxDelay = durationMs; return this;
        }
        public JobInfo build() { return new JobInfo(this); }
    }

    /**
     * A content URI whose change should trigger the job, plus the flags that
     * say whether descendants count.
     *
     * WorkManager's ContentUriTriggers constraint constructs one of these for
     * every content-trigger work request, so the class must exist for such a
     * WorkRequest to be built at all -- independently of whether the trigger
     * ever fires.
     *
     * Source: frameworks/base/core/java/android/app/job/JobInfo.java
     * (nested class TriggerContentUri)
     */
    public static final class TriggerContentUri implements android.os.Parcelable {

        /** Also trigger when a descendant of this URI changes. */
        public static final int FLAG_NOTIFY_FOR_DESCENDANTS = 1 << 0;

        private final android.net.Uri mUri;
        private final int mFlags;

        public TriggerContentUri(android.net.Uri uri, int flags) {
            if (uri == null) throw new NullPointerException("uri");
            mUri = uri;
            mFlags = flags;
        }

        public android.net.Uri getUri() { return mUri; }

        public int getFlags() { return mFlags; }

        @Override public boolean equals(Object o) {
            if (!(o instanceof TriggerContentUri)) return false;
            TriggerContentUri t = (TriggerContentUri) o;
            return mUri == null ? t.mUri == null
                    : (mUri.equals(t.mUri) && mFlags == t.mFlags);
        }

        @Override public int hashCode() {
            return (mUri == null ? 0 : mUri.hashCode()) * 31 + mFlags;
        }

        @Override public int describeContents() { return 0; }

        @Override public void writeToParcel(android.os.Parcel out, int flags) {
            android.net.Uri.writeToParcel(out, mUri);
            out.writeInt(mFlags);
        }

        public static final Creator<TriggerContentUri> CREATOR =
                new Creator<TriggerContentUri>() {
            @Override public TriggerContentUri createFromParcel(android.os.Parcel in) {
                return new TriggerContentUri(
                        android.net.Uri.CREATOR.createFromParcel(in), in.readInt());
            }
            @Override public TriggerContentUri[] newArray(int size) {
                return new TriggerContentUri[size];
            }
        };
    }
}
