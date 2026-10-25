// android.app.admin.SecurityLog -- "Definitions for working with security logs",
// the tag/level constants and the SecurityEvent record a device owner retrieves
// with DevicePolicyManager.retrieveSecurityLogs. API 24.
//
// AOSP ref: frameworks/base/core/java/android/app/admin/SecurityLog.java
// (android16-release). The class is a constant holder plus the nested
// SecurityEvent; the actual log buffer lives in the system and is only readable
// by a device owner, which Mac.apk never has -- so no SecurityEvent is ever
// produced here (see DevicePolicyManager.retrieveSecurityLogs).
package android.app.admin;

import android.os.Parcel;
import android.os.Parcelable;

public class SecurityLog {

    /** AOSP LEVEL_*: the severity of a security event. */
    public static final int LEVEL_INFO = 1;
    public static final int LEVEL_WARNING = 2;
    public static final int LEVEL_ERROR = 3;

    // AOSP TAG_*: the event tag, which selects how getData()'s payload is
    // shaped. Values are AOSP's verbatim -- an app switches on them.
    public static final int TAG_ADB_SHELL_CMD = 210002;
    public static final int TAG_ADB_SHELL_INTERACTIVE = 210001;
    public static final int TAG_APP_PROCESS_START = 210005;
    public static final int TAG_BACKUP_SERVICE_TOGGLED = 210044;
    public static final int TAG_BLUETOOTH_CONNECTION = 210039;
    public static final int TAG_BLUETOOTH_DISCONNECTION = 210040;
    public static final int TAG_CAMERA_POLICY_SET = 210034;
    public static final int TAG_CERT_AUTHORITY_INSTALLED = 210029;
    public static final int TAG_CERT_AUTHORITY_REMOVED = 210030;
    public static final int TAG_CERT_VALIDATION_FAILURE = 210033;
    public static final int TAG_CRYPTO_SELF_TEST_COMPLETED = 210031;
    public static final int TAG_KEYGUARD_DISABLED_FEATURES_SET = 210021;
    public static final int TAG_KEYGUARD_DISMISSED = 210006;
    public static final int TAG_KEYGUARD_DISMISS_AUTH_ATTEMPT = 210007;
    public static final int TAG_KEYGUARD_SECURED = 210008;
    public static final int TAG_KEY_DESTRUCTION = 210026;
    public static final int TAG_KEY_GENERATED = 210024;
    public static final int TAG_KEY_IMPORT = 210025;
    public static final int TAG_KEY_INTEGRITY_VIOLATION = 210032;
    public static final int TAG_LOGGING_STARTED = 210011;
    public static final int TAG_LOGGING_STOPPED = 210012;
    public static final int TAG_LOG_BUFFER_SIZE_CRITICAL = 210015;
    public static final int TAG_MAX_PASSWORD_ATTEMPTS_SET = 210020;
    public static final int TAG_MAX_SCREEN_LOCK_TIMEOUT_SET = 210019;
    public static final int TAG_MEDIA_MOUNT = 210013;
    public static final int TAG_MEDIA_UNMOUNT = 210014;
    public static final int TAG_OS_SHUTDOWN = 210010;
    public static final int TAG_OS_STARTUP = 210009;
    public static final int TAG_PACKAGE_INSTALLED = 210041;
    public static final int TAG_PACKAGE_UNINSTALLED = 210043;
    public static final int TAG_PACKAGE_UPDATED = 210042;
    public static final int TAG_PASSWORD_CHANGED = 210036;
    public static final int TAG_PASSWORD_COMPLEXITY_REQUIRED = 210035;
    public static final int TAG_PASSWORD_COMPLEXITY_SET = 210017;
    public static final int TAG_PASSWORD_EXPIRATION_SET = 210016;
    public static final int TAG_PASSWORD_HISTORY_LENGTH_SET = 210018;
    public static final int TAG_REMOTE_LOCK = 210022;
    public static final int TAG_SYNC_RECV_FILE = 210003;
    public static final int TAG_SYNC_SEND_FILE = 210004;
    public static final int TAG_USER_RESTRICTION_ADDED = 210027;
    public static final int TAG_USER_RESTRICTION_REMOVED = 210028;
    public static final int TAG_WIFI_CONNECTION = 210037;
    public static final int TAG_WIFI_DISCONNECTION = 210038;
    public static final int TAG_WIPE_FAILURE = 210023;

    public SecurityLog() { }

    /** AOSP SecurityLog.SecurityEvent: one entry from the security log. */
    public static final class SecurityEvent implements Parcelable {

        private final long mId;
        private final int mTag;
        private final int mLogLevel;
        private final long mTimeNanos;
        private final Object mData;

        /** @hide -- AOSP's constructors are @hide; only the system produces
         *  events. */
        SecurityEvent(long id, int tag, int logLevel, long timeNanos, Object data) {
            mId = id;
            mTag = tag;
            mLogLevel = logLevel;
            mTimeNanos = timeNanos;
            mData = data;
        }

        private SecurityEvent(Parcel in) {
            mId = in.readLong();
            mTag = in.readInt();
            mLogLevel = in.readInt();
            mTimeNanos = in.readLong();
            mData = null;
        }

        /** AOSP getTag(): "the tag of this log entry, which specifies entry's
         *  semantics." */
        public int getTag() { return mTag; }

        /** AOSP getData(): "the payload contained in this log entry or null...
         *  The type and format of the payload is dictated by the tag." */
        public Object getData() { return mData; }

        /** AOSP getTimeNanos(): "the timestamp in nano seconds when this event
         *  was logged." */
        public long getTimeNanos() { return mTimeNanos; }

        /** AOSP getId(): "the id of the event, unique within the log." */
        public long getId() { return mId; }

        /** AOSP getLogLevel(): LEVEL_INFO / LEVEL_WARNING / LEVEL_ERROR. */
        public int getLogLevel() { return mLogLevel; }

        @Override public int describeContents() { return 0; }

        @Override public void writeToParcel(Parcel dest, int flags) {
            dest.writeLong(mId);
            dest.writeInt(mTag);
            dest.writeInt(mLogLevel);
            dest.writeLong(mTimeNanos);
        }

        public static final Parcelable.Creator<SecurityEvent> CREATOR =
                new Parcelable.Creator<SecurityEvent>() {
            @Override public SecurityEvent createFromParcel(Parcel in) {
                return new SecurityEvent(in);
            }
            @Override public SecurityEvent[] newArray(int size) {
                return new SecurityEvent[size];
            }
        };
    }

    /** AOSP TAG_NFC_ENABLED / TAG_NFC_DISABLED: declared in AOSP as
     *  SecurityLogTags.SECURITY_NFC_ENABLED / _DISABLED, whose numeric values
     *  come from core/java/android/app/admin/SecurityLogTags.logtags
     *  (210045 security_nfc_enabled, 210046 security_nfc_disabled). Spelled as
     *  the literals because the generated SecurityLogTags class is not part of
     *  the public surface. */
    public static final int TAG_NFC_ENABLED = 210045;
    public static final int TAG_NFC_DISABLED = 210046;
}
