package android.app;
import android.content.Intent;
import java.util.Collections;
import java.util.Map;
/** A record of one app start (reason / timing / mode). API 35. Mac.apk does not
 *  keep historical start records, so an app that queries them gets a default
 *  record rather than a crash.
 *  Source: frameworks/base/core/java/android/app/ApplicationStartInfo.java */
public final class ApplicationStartInfo implements android.os.Parcelable {
    public static final int START_REASON_LAUNCHER = 5;
    public static final int STARTUP_STATE_STARTED = 1;
    public static final int START_TYPE_UNSET = 0;

    /** AOSP START_COMPONENT_* (ApplicationStartInfo.java:215-231): which
     *  component type caused the process to start. */
    public static final int START_COMPONENT_ACTIVITY = 1;
    public static final int START_COMPONENT_BROADCAST = 2;
    public static final int START_COMPONENT_CONTENT_PROVIDER = 3;
    public static final int START_COMPONENT_SERVICE = 4;
    public static final int START_COMPONENT_OTHER = 5;
    public ApplicationStartInfo() { }
    public int getPid() { return android.os.Process.myPid(); }
    public int getRealUid() { return android.os.Process.myUid(); }
    public int getPackageUid() { return android.os.Process.myUid(); }
    public int getDefiningUid() { return android.os.Process.myUid(); }
    public String getProcessName() { return ""; }
    public int getReason() { return START_REASON_LAUNCHER; }
    public int getStartupState() { return STARTUP_STATE_STARTED; }
    public int getStartType() { return START_TYPE_UNSET; }
    public int getLaunchMode() { return 0; }
    public boolean wasForceStopped() { return false; }

    /** AOSP getStartComponent(): "the component type that caused the app to
     *  start", set for any startup state. Mac.apk boots a process for exactly
     *  one reason -- the launcher opening the app's main Activity -- which is
     *  the same event getReason() already reports as START_REASON_LAUNCHER, so
     *  the component is START_COMPONENT_ACTIVITY. */
    public int getStartComponent() { return START_COMPONENT_ACTIVITY; }
    public Intent getIntent() { return null; }
    public Map<Integer, Long> getStartupTimestamps() { return Collections.emptyMap(); }

    // 2026-08-23 conformance sweep: AOSP's ApplicationStartInfo carries ~15
    // real fields written to the parcel (ApplicationStartInfo.java
    // L734-807: startup state, pid, uids, package/process name, reason,
    // a timestamp map, start type/intent/launch-mode, force-stopped flag,
    // creation time, start component). This class stores NONE of those --
    // every getter above either returns a live process query
    // (Process.myPid()/myUid(), re-evaluated on each call) or a hardcoded
    // constant, so there is no per-instance state a write/read pair could
    // lose. An honest round trip here writes nothing and reconstructs a
    // fresh, equally-stateless instance on read -- not a lossy shortcut,
    // because nothing was ever carried in the object to begin with.
    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) { }

    public static final android.os.Parcelable.Creator<ApplicationStartInfo> CREATOR =
            new android.os.Parcelable.Creator<ApplicationStartInfo>() {
        @Override public ApplicationStartInfo createFromParcel(android.os.Parcel in) {
            return new ApplicationStartInfo();
        }
        @Override public ApplicationStartInfo[] newArray(int size) {
            return new ApplicationStartInfo[size];
        }
    };

    // ---- AOSP API-36 constant completion (android.app.ApplicationStartInfo) ----
    // Values extracted verbatim from the AOSP source for this class
    // (android16-release). javac INLINES a compile-time constant into the app at
    // ITS build time, so a shipped APK cannot observe a missing one at runtime --
    // these are here for our own framework code, for anything compiled against
    // THIS jar, and so the surface is complete rather than nearly complete.
    public static final int LAUNCH_MODE_SINGLE_INSTANCE = 2;
    public static final int LAUNCH_MODE_SINGLE_INSTANCE_PER_TASK = 4;
    public static final int LAUNCH_MODE_SINGLE_TASK = 3;
    public static final int LAUNCH_MODE_SINGLE_TOP = 1;
    public static final int LAUNCH_MODE_STANDARD = 0;
    public static final int STARTUP_STATE_ERROR = 1;
    public static final int STARTUP_STATE_FIRST_FRAME_DRAWN = 2;
    public static final int START_REASON_ALARM = 0;
    public static final int START_REASON_BACKUP = 1;
    public static final int START_REASON_BOOT_COMPLETE = 2;
    public static final int START_REASON_BROADCAST = 3;
    public static final int START_REASON_CONTENT_PROVIDER = 4;
    public static final int START_REASON_JOB = 5;
    public static final int START_REASON_LAUNCHER_RECENTS = 7;
    public static final int START_REASON_OTHER = 8;
    public static final int START_REASON_PUSH = 9;
    public static final int START_REASON_SERVICE = 10;
    public static final int START_REASON_START_ACTIVITY = 11;
    public static final int START_TIMESTAMP_APPLICATION_ONCREATE = 2;
    public static final int START_TIMESTAMP_BIND_APPLICATION = 3;
    public static final int START_TIMESTAMP_FIRST_FRAME = 4;
    public static final int START_TIMESTAMP_FORK = 1;
    public static final int START_TIMESTAMP_FULLY_DRAWN = 5;
    public static final int START_TIMESTAMP_INITIAL_RENDERTHREAD_FRAME = 6;
    public static final int START_TIMESTAMP_LAUNCH = 0;
    public static final int START_TIMESTAMP_RESERVED_RANGE_SYSTEM = 20;
    public static final int START_TIMESTAMP_RESERVED_RANGE_DEVELOPER = 30;
    public static final int START_TIMESTAMP_RESERVED_RANGE_DEVELOPER_START = START_TIMESTAMP_RESERVED_RANGE_SYSTEM + 1;
    public static final int START_TIMESTAMP_SURFACEFLINGER_COMPOSITION_COMPLETE = 7;
    public static final int START_TYPE_COLD = 1;
    public static final int START_TYPE_HOT = 3;
    public static final int START_TYPE_WARM = 2;
}
