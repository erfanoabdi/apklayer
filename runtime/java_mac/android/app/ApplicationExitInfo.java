// android.app.ApplicationExitInfo -- one recorded process death, as handed back
// by ActivityManager.getHistoricalProcessExitReasons(). AOSP:
//   frameworks/base/core/java/android/app/ApplicationExitInfo.java
//   public final class ApplicationExitInfo implements Parcelable
//
// v1.0.92 shipped this as an empty stub, so every accessor threw
// NoSuchMethodError. Crash-reporting SDKs (Crashlytics, Bugsnag, Sentry, and
// hand-rolled equivalents) read this API early in Application.onCreate to
// report why the PREVIOUS process instance died, and a NoSuchMethodError is an
// Error, not an Exception, so their ordinary catch(Exception) guards do not
// intercept it and the whole SDK init chain aborts.
//
// This is the real data class: fields, AOSP's getters, the REASON_/SUBREASON_
// constant sets, and Parcelable. It is deliberately a pure value object -- the
// records themselves are produced by the caller that queries the exit history.
// Mac.apk keeps no persistent process-exit history yet, so
// ActivityManager.getHistoricalProcessExitReasons() is still missing; see the
// AOSP-conformance follow-up. Populating a record uses the same public setters
// AOSP exposes (they are @hide there, but public, and the platform's own
// ActivityManagerService is what calls them).
package android.app;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.UserHandle;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public final class ApplicationExitInfo implements Parcelable {
    /**
     * Application process died due to unknown reason.
     */
    public static final int REASON_UNKNOWN = 0;

    /**
     * Application process exit normally by itself, for example,
     * via {@link java.lang.System#exit}; {@link #getStatus} will specify the exit code.
     */
    public static final int REASON_EXIT_SELF = 1;

    /**
     * Application process died due to the result of an OS signal; for example,
     * {@link android.system.OsConstants#SIGKILL}; {@link #getStatus} will specify the signal number.
     */
    public static final int REASON_SIGNALED = 2;

    /**
     * Application process was killed by the system low memory killer.
     */
    public static final int REASON_LOW_MEMORY = 3;

    /**
     * Application process died because of an unhandled exception in Java code.
     */
    public static final int REASON_CRASH = 4;

    /**
     * Application process died because of a native code crash.
     */
    public static final int REASON_CRASH_NATIVE = 5;

    /**
     * Application process was killed due to being unresponsive (ANR).
     */
    public static final int REASON_ANR = 6;

    /**
     * Application process was killed because of initialization failure,
     * for example, it took too long to attach to the system, or there was
     * an error while initializing the process.
     */
    public static final int REASON_INITIALIZATION_FAILURE = 7;

    /**
     * Application process was killed due to a runtime permission change.
     */
    public static final int REASON_PERMISSION_CHANGE = 8;

    /**
     * Application process was killed by the system due to excessive resource usage.
     */
    public static final int REASON_EXCESSIVE_RESOURCE_USAGE = 9;

    /**
     * Application process was killed because of the user request, for example,
     * user clicked the "Force stop" button of the application in the Application Settings.
     */
    public static final int REASON_USER_REQUESTED = 10;

    /**
     * Application process was killed, because the user it is running as on devices
     * with mutlpile users, was stopped.
     */
    public static final int REASON_USER_STOPPED = 11;

    /**
     * Application process was killed because its dependency was going away, for example,
     * a stable content provider connection's client will be killed if the provider is killed.
     */
    public static final int REASON_DEPENDENCY_DIED = 12;

    /**
     * Application process was killed by the system for various other reasons which are
     * not by problems in apps and are not actionable by apps.
     */
    public static final int REASON_OTHER = 13;

    /**
     * Application process was killed by App Freezer, for example, because it receives
     * sync binder transactions while being frozen.
     */
    public static final int REASON_FREEZER = 14;

    /**
     * Application process was killed because the app was disabled, or any of its component
     * states have changed without {@link android.content.pm.PackageManager#DONT_KILL_APP}.
     */
    public static final int REASON_PACKAGE_STATE_CHANGE = 15;

    /**
     * Application process was killed because it was updated.
     */
    public static final int REASON_PACKAGE_UPDATED = 16;

    /**
     * Application process kills subreason is unknown.
     */
    public static final int SUBREASON_UNKNOWN = 0;

    /** Application process was killed because user quit it on the "wait for debugger" dialog. */
    public static final int SUBREASON_WAIT_FOR_DEBUGGER = 1;

    /** Application process was killed by the activity manager because there were too many
     *  cached processes. */
    public static final int SUBREASON_TOO_MANY_CACHED = 2;

    /** Application process was killed by the activity manager because there were too many
     *  empty processes. */
    public static final int SUBREASON_TOO_MANY_EMPTY = 3;

    /** Application process was killed by the activity manager because there were too many
     *  cached processes and this process had been in an empty state for a long time. */
    public static final int SUBREASON_TRIM_EMPTY = 4;

    /** Application process was killed by the activity manager because system was on
     *  memory pressure and this process took large amount of cached memory. */
    public static final int SUBREASON_LARGE_CACHED = 5;

    /** Application process was killed by the activity manager because the system was on
     *  low memory pressure. */
    public static final int SUBREASON_MEMORY_PRESSURE = 6;

    /** Application process was killed by the activity manager due to excessive CPU usage. */
    public static final int SUBREASON_EXCESSIVE_CPU = 7;

    /** System update has done and this process needs to be killed. */
    public static final int SUBREASON_SYSTEM_UPDATE_DONE = 8;

    /** Kill all foreground services, for now it only occurs when enabling the quiet mode
     *  for the managed profile. */
    public static final int SUBREASON_KILL_ALL_FG = 9;

    /** All background processes except certain ones were killed, for now it only occurs
     *  when the low memory killer is disabled. */
    public static final int SUBREASON_KILL_ALL_BG_EXCEPT = 10;

    /** The process associated with the UID was explicitly killed. */
    public static final int SUBREASON_KILL_UID = 11;

    /** The process was explicitly killed with its pid. */
    public static final int SUBREASON_KILL_PID = 12;

    /** The start of the process was invalid. */
    public static final int SUBREASON_INVALID_START = 13;

    /** The process was killed because it's in an invalid state. */
    public static final int SUBREASON_INVALID_STATE = 14;

    /** The process was killed when it's imperceptible to user. */
    public static final int SUBREASON_IMPERCEPTIBLE = 15;

    /** The process was killed because it's being moved out from LRU list. */
    public static final int SUBREASON_REMOVE_LRU = 16;

    /** The process was killed because it's isolated and was in a cached state. */
    public static final int SUBREASON_ISOLATED_NOT_NEEDED = 17;

    /** The process was killed because it's in forced-app-standby state and its uid
     *  state is idle. */
    public static final int SUBREASON_CACHED_IDLE_FORCED_APP_STANDBY = 18;

    /** The process was killed because it fetched a sync binder transaction while frozen. */
    public static final int SUBREASON_FREEZER_BINDER_IOCTL = 19;

    /** The process was killed because it received a sync binder transaction while frozen. */
    public static final int SUBREASON_FREEZER_BINDER_TRANSACTION = 20;

    /** The process was killed because of force-stop. */
    public static final int SUBREASON_FORCE_STOP = 21;

    /** The process was killed because the task it belonged to was removed. */
    public static final int SUBREASON_REMOVE_TASK = 22;

    /** The process was killed because the app was stopped. */
    public static final int SUBREASON_STOP_APP = 23;

    /** The process was killed because of background restriction. */
    public static final int SUBREASON_KILL_BACKGROUND = 24;

    /** The process was killed because the package was updated. */
    public static final int SUBREASON_PACKAGE_UPDATE = 25;

    /** The process was killed because it had an undelivered broadcast for too long. */
    public static final int SUBREASON_UNDELIVERED_BROADCAST = 26;

    /** The process was killed because its SDK sandbox process died. */
    public static final int SUBREASON_SDK_SANDBOX_DIED = 27;

    /** The process was killed because its SDK sandbox was no longer being used. */
    public static final int SUBREASON_SDK_SANDBOX_NOT_NEEDED = 28;

    /** The process was killed because it created too many binder objects. */
    public static final int SUBREASON_EXCESSIVE_BINDER_OBJECTS = 29;

    /** The process was killed by the kernel OOM killer. */
    public static final int SUBREASON_OOM_KILL = 30;

    /** The process was killed because its async binder buffer was full while frozen. */
    public static final int SUBREASON_FREEZER_BINDER_ASYNC_FULL = 31;

    /** The process was killed because it sent too many outgoing broadcasts while cached. */
    public static final int SUBREASON_EXCESSIVE_OUTGOING_BROADCASTS_WHILE_CACHED = 32;

    private int mPid;
    private int mRealUid;
    private int mPackageUid;
    private int mDefiningUid;
    private String mProcessName;
    private int mReason;
    private int mStatus;
    private int mImportance;
    private long mPss;
    private long mRss;
    private long mTimestamp;
    private String mDescription;
    private int mSubReason;
    private int mConnectionGroup;
    private String mPackageName;
    private String[] mPackageList;
    private byte[] mState;
    private File mTraceFile;

    public ApplicationExitInfo() {}

    public ApplicationExitInfo(ApplicationExitInfo other) {
        mPid = other.mPid;
        mRealUid = other.mRealUid;
        mPackageUid = other.mPackageUid;
        mDefiningUid = other.mDefiningUid;
        mProcessName = other.mProcessName;
        mReason = other.mReason;
        mStatus = other.mStatus;
        mImportance = other.mImportance;
        mPss = other.mPss;
        mRss = other.mRss;
        mTimestamp = other.mTimestamp;
        mDescription = other.mDescription;
        mSubReason = other.mSubReason;
        mConnectionGroup = other.mConnectionGroup;
        mPackageName = other.mPackageName;
        mPackageList = other.mPackageList;
        mState = other.mState;
        mTraceFile = other.mTraceFile;
    }

    private ApplicationExitInfo(Parcel in) {
        mPid = in.readInt();
        mRealUid = in.readInt();
        mPackageUid = in.readInt();
        mDefiningUid = in.readInt();
        mProcessName = in.readString();
        mReason = in.readInt();
        mStatus = in.readInt();
        mImportance = in.readInt();
        mPss = in.readLong();
        mRss = in.readLong();
        mTimestamp = in.readLong();
        mDescription = in.readString();
        mSubReason = in.readInt();
        mConnectionGroup = in.readInt();
        mPackageName = in.readString();
        mPackageList = in.createStringArray();
        mState = in.createByteArray();
    }

    /**
     * The process id of the process that died.
     */
    public int getPid() {
        return mPid;
    }

    /**
     * The kernel user identifier of the process, most of the time the system uses this
     * to do access control checks.
     */
    public int getRealUid() {
        return mRealUid;
    }

    /**
     * Return the user id of the package which this process was running as.
     */
    public int getPackageUid() {
        return mPackageUid;
    }

    /**
     * Return the defining kernel user identifier, maybe different from {@link #getRealUid} and
     * {@link #getPackageUid}, if an external service has the
     * {@link android.R.styleable#AndroidManifestService_useAppZygote android:useAppZygote} set
     * to true.
     */
    public int getDefiningUid() {
        return mDefiningUid;
    }

    /**
     * The actual process name it was running with.
     */
    public String getProcessName() {
        return mProcessName;
    }

    /**
     * The reason code of the process's death.
     */
    public int getReason() {
        return mReason;
    }

    /**
     * The exit status argument of exit() if the application calls it, or the signal
     * number if the application is signaled.
     */
    public int getStatus() {
        return mStatus;
    }

    /**
     * The importance of the process that it used to have before the death.
     */
    public int getImportance() {
        return mImportance;
    }

    /**
     * Last proportional set size of the memory that the process had used in kB.
     *
     * <p class="note">Note: This is the value from last sampling on the process,
     * it's NOT the exact memory information prior to its death; and it'll be zero
     * if the process died before the first sampling.</p>
     */
    public long getPss() {
        return mPss;
    }

    /**
     * Last resident set size of the memory that the process had used in kB.
     *
     * <p class="note">Note: This is the value from last sampling on the process,
     * it's NOT the exact memory information prior to its death; and it'll be zero
     * if the process died before the first sampling.</p>
     */
    public long getRss() {
        return mRss;
    }

    /**
     * The timestamp of the process's death, in milliseconds since the Epoch.
     */
    public long getTimestamp() {
        return mTimestamp;
    }

    /**
     * The human readable description of the process's death, given by the system; could be null.
     */
    public String getDescription() {
        return mDescription;
    }

    /**
     * Return the user id of the record on a multi-user system.
     */
    public UserHandle getUserHandle() {
        return UserHandle.of(UserHandle.getUserId(mRealUid));
    }

    /**
     * Return the state data set by calling
     * {@link android.app.ActivityManager#setProcessStateSummary(byte[])} from the process
     * before its death.
     */
    public byte[] getProcessStateSummary() {
        return mState;
    }

    /**
     * Return the InputStream to the traces that was taken by the system prior to the death
     * of the process; typically available when the reason is {@link #REASON_ANR}, and from
     * API 31 also a tombstone for {@link #REASON_CRASH_NATIVE}. AOSP documents that this may
     * still return null, because the traces live in a global circular buffer and can be
     * overwritten by newer crashes.
     */
    public InputStream getTraceInputStream() throws IOException {
        if (mTraceFile == null || !mTraceFile.isFile()) {
            return null;
        }
        return new FileInputStream(mTraceFile);
    }

    /**
     * Similar to {@link #getTraceInputStream} but returns the File object.
     */
    public File getTraceFile() {
        return mTraceFile;
    }

    /**
     * A subtype reason in conjunction with {@link #getReason}.
     */
    public int getSubReason() {
        return mSubReason;
    }

    /**
     * The connection group this process belongs to, if there is any.
     */
    public int getConnectionGroup() {
        return mConnectionGroup;
    }

    /**
     * Name of first package running in this process.
     */
    public String getPackageName() {
        return mPackageName;
    }

    /**
     * List of packages running in this process.
     */
    public String[] getPackageList() {
        return mPackageList;
    }

    // The setters below are @hide in AOSP but public: the platform's own
    // ActivityManagerService constructs records with them. They are the only
    // supported way for this framework to populate an exit-history record.
    public void setPid(final int pid) {
        mPid = pid;
    }

    public void setRealUid(final int uid) {
        mRealUid = uid;
    }

    public void setPackageUid(final int uid) {
        mPackageUid = uid;
    }

    public void setDefiningUid(final int uid) {
        mDefiningUid = uid;
    }

    public void setProcessName(final String processName) {
        mProcessName = processName;
    }

    public void setReason(final int reason) {
        mReason = reason;
    }

    public void setStatus(final int status) {
        mStatus = status;
    }

    public void setImportance(final int importance) {
        mImportance = importance;
    }

    public void setPss(final long pss) {
        mPss = pss;
    }

    public void setRss(final long rss) {
        mRss = rss;
    }

    public void setTimestamp(final long timestamp) {
        mTimestamp = timestamp;
    }

    public void setDescription(final String description) {
        mDescription = description;
    }

    public void setSubReason(final int subReason) {
        mSubReason = subReason;
    }

    public void setConnectionGroup(final int connectionGroup) {
        mConnectionGroup = connectionGroup;
    }

    public void setPackageName(final String packageName) {
        mPackageName = packageName;
    }

    public void setPackageList(final String[] packageList) {
        mPackageList = packageList;
    }

    public void setProcessStateSummary(final byte[] state) {
        mState = state;
    }

    public void setTraceFile(final File traceFile) {
        mTraceFile = traceFile;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mPid);
        dest.writeInt(mRealUid);
        dest.writeInt(mPackageUid);
        dest.writeInt(mDefiningUid);
        dest.writeString(mProcessName);
        dest.writeInt(mReason);
        dest.writeInt(mStatus);
        dest.writeInt(mImportance);
        dest.writeLong(mPss);
        dest.writeLong(mRss);
        dest.writeLong(mTimestamp);
        dest.writeString(mDescription);
        dest.writeInt(mSubReason);
        dest.writeInt(mConnectionGroup);
        dest.writeString(mPackageName);
        dest.writeStringArray(mPackageList);
        dest.writeByteArray(mState);
    }

    public static final Parcelable.Creator<ApplicationExitInfo> CREATOR =
            new Parcelable.Creator<ApplicationExitInfo>() {
        @Override
        public ApplicationExitInfo createFromParcel(Parcel in) {
            return new ApplicationExitInfo(in);
        }

        @Override
        public ApplicationExitInfo[] newArray(int size) {
            return new ApplicationExitInfo[size];
        }
    };

    public static String reasonCodeToString(int reason) {
        switch (reason) {
            case REASON_EXIT_SELF:
                return "EXIT_SELF";
            case REASON_SIGNALED:
                return "SIGNALED";
            case REASON_LOW_MEMORY:
                return "LOW_MEMORY";
            case REASON_CRASH:
                return "APP CRASH(EXCEPTION)";
            case REASON_CRASH_NATIVE:
                return "APP CRASH(NATIVE)";
            case REASON_ANR:
                return "ANR";
            case REASON_INITIALIZATION_FAILURE:
                return "INITIALIZATION FAILURE";
            case REASON_PERMISSION_CHANGE:
                return "PERMISSION CHANGE";
            case REASON_EXCESSIVE_RESOURCE_USAGE:
                return "EXCESSIVE RESOURCE USAGE";
            case REASON_USER_REQUESTED:
                return "USER REQUESTED";
            case REASON_USER_STOPPED:
                return "USER STOPPED";
            case REASON_DEPENDENCY_DIED:
                return "DEPENDENCY DIED";
            case REASON_OTHER:
                return "OTHER KILLS BY SYSTEM";
            case REASON_FREEZER:
                return "FREEZER";
            case REASON_PACKAGE_STATE_CHANGE:
                return "STATE CHANGE";
            case REASON_PACKAGE_UPDATED:
                return "PACKAGE UPDATED";
            default:
                return "UNKNOWN";
        }
    }

    public static String subreasonToString(int subreason) {
        switch (subreason) {
            case SUBREASON_WAIT_FOR_DEBUGGER:
                return "WAIT FOR DEBUGGER";
            case SUBREASON_TOO_MANY_CACHED:
                return "TOO MANY CACHED PROCS";
            case SUBREASON_TOO_MANY_EMPTY:
                return "TOO MANY EMPTY PROCS";
            case SUBREASON_TRIM_EMPTY:
                return "TRIM EMPTY";
            case SUBREASON_LARGE_CACHED:
                return "LARGE CACHED";
            case SUBREASON_MEMORY_PRESSURE:
                return "MEMORY PRESSURE";
            case SUBREASON_EXCESSIVE_CPU:
                return "EXCESSIVE CPU USAGE";
            case SUBREASON_SYSTEM_UPDATE_DONE:
                return "SYSTEM UPDATE_DONE";
            case SUBREASON_KILL_ALL_FG:
                return "KILL ALL FG";
            case SUBREASON_KILL_ALL_BG_EXCEPT:
                return "KILL ALL BG EXCEPT";
            case SUBREASON_KILL_UID:
                return "KILL UID";
            case SUBREASON_KILL_PID:
                return "KILL PID";
            case SUBREASON_INVALID_START:
                return "INVALID START";
            case SUBREASON_INVALID_STATE:
                return "INVALID STATE";
            case SUBREASON_IMPERCEPTIBLE:
                return "IMPERCEPTIBLE";
            case SUBREASON_REMOVE_LRU:
                return "REMOVE LRU";
            case SUBREASON_ISOLATED_NOT_NEEDED:
                return "ISOLATED NOT NEEDED";
            case SUBREASON_CACHED_IDLE_FORCED_APP_STANDBY:
                return "CACHED IDLE FORCED APP STANDBY";
            case SUBREASON_FREEZER_BINDER_IOCTL:
                return "FREEZER BINDER IOCTL";
            case SUBREASON_FREEZER_BINDER_TRANSACTION:
                return "FREEZER BINDER TRANSACTION";
            case SUBREASON_FORCE_STOP:
                return "FORCE STOP";
            case SUBREASON_REMOVE_TASK:
                return "REMOVE TASK";
            case SUBREASON_STOP_APP:
                return "STOP APP";
            case SUBREASON_KILL_BACKGROUND:
                return "KILL BACKGROUND";
            case SUBREASON_PACKAGE_UPDATE:
                return "PACKAGE UPDATE";
            case SUBREASON_UNDELIVERED_BROADCAST:
                return "UNDELIVERED BROADCAST";
            case SUBREASON_SDK_SANDBOX_DIED:
                return "SDK SANDBOX DIED";
            case SUBREASON_SDK_SANDBOX_NOT_NEEDED:
                return "SDK SANDBOX NOT NEEDED";
            case SUBREASON_EXCESSIVE_BINDER_OBJECTS:
                return "EXCESSIVE BINDER OBJECTS";
            case SUBREASON_OOM_KILL:
                return "OOM KILL";
            case SUBREASON_FREEZER_BINDER_ASYNC_FULL:
                return "FREEZER BINDER ASYNC FULL";
            case SUBREASON_EXCESSIVE_OUTGOING_BROADCASTS_WHILE_CACHED:
                return "EXCESSIVE OUTGOING BROADCASTS WHILE CACHED";
            default:
                return "UNKNOWN";
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ApplicationExitInfo(timestamp=");
        sb.append(new java.text.SimpleDateFormat().format(new java.util.Date(mTimestamp)));
        sb.append(" pid=").append(mPid);
        sb.append(" realUid=").append(mRealUid);
        sb.append(" packageUid=").append(mPackageUid);
        sb.append(" definingUid=").append(mDefiningUid);
        sb.append(" user=").append(UserHandle.getUserId(mPackageUid));
        sb.append(" process=").append(mProcessName);
        sb.append(" reason=").append(mReason).append(" (")
                .append(reasonCodeToString(mReason)).append(")");
        sb.append(" subreason=").append(mSubReason).append(" (")
                .append(subreasonToString(mSubReason)).append(")");
        sb.append(" status=").append(mStatus);
        sb.append(" importance=").append(mImportance);
        sb.append(" pss=").append(mPss);
        sb.append(" rss=").append(mRss);
        sb.append(" description=").append(mDescription);
        sb.append(" state=").append((mState == null || mState.length == 0)
                ? "empty" : Integer.toString(mState.length) + " bytes");
        sb.append(" trace=").append(mTraceFile);
        return sb.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (other == null || !(other instanceof ApplicationExitInfo)) {
            return false;
        }
        ApplicationExitInfo o = (ApplicationExitInfo) other;
        return mPid == o.mPid && mRealUid == o.mRealUid && mPackageUid == o.mPackageUid
                && mDefiningUid == o.mDefiningUid && mReason == o.mReason
                && mSubReason == o.mSubReason && mImportance == o.mImportance
                && mStatus == o.mStatus && mTimestamp == o.mTimestamp
                && mConnectionGroup == o.mConnectionGroup
                && java.util.Objects.equals(mProcessName, o.mProcessName)
                && java.util.Objects.equals(mPackageName, o.mPackageName)
                && java.util.Objects.equals(mDescription, o.mDescription);
    }

    @Override
    public int hashCode() {
        int result = mPid;
        result = 31 * result + mRealUid;
        result = 31 * result + mPackageUid;
        result = 31 * result + mDefiningUid;
        result = 31 * result + mReason;
        result = 31 * result + mSubReason;
        result = 31 * result + mImportance;
        result = 31 * result + mStatus;
        result = 31 * result + mConnectionGroup;
        result = 31 * result + (int) mTimestamp;
        result = 31 * result + java.util.Objects.hashCode(mProcessName);
        result = 31 * result + java.util.Objects.hashCode(mPackageName);
        result = 31 * result + java.util.Objects.hashCode(mDescription);
        return result;
    }
}
