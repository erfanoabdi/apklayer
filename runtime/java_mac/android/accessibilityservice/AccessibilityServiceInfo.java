package android.accessibilityservice;

import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Description of an installed accessibility service: what it can do, what
 * feedback it gives, and how it was declared.
 *
 * Apps read these from AccessibilityManager.getInstalledAccessibilityServiceList()
 * / getEnabledAccessibilityServiceList() to decide whether a screen reader is
 * present. Mac.apk has no accessibility services installed, so those lists are
 * empty and no instance is normally produced -- but the CLASS must exist,
 * because the code that iterates the (empty) list still references the type and
 * fails to link without it. That is why 28 of 37 corpus apps reference it.
 *
 * Source: frameworks/base/core/java/android/accessibilityservice/AccessibilityServiceInfo.java
 * https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/core/java/android/accessibilityservice/AccessibilityServiceInfo.java
 */
public class AccessibilityServiceInfo implements Parcelable {

    public static final int FEEDBACK_SPOKEN = 0x0000001;
    public static final int FEEDBACK_HAPTIC = 0x0000002;
    public static final int FEEDBACK_AUDIBLE = 0x0000004;
    public static final int FEEDBACK_VISUAL = 0x0000008;
    public static final int FEEDBACK_GENERIC = 0x0000010;
    public static final int FEEDBACK_BRAILLE = 0x0000020;
    public static final int FEEDBACK_ALL_MASK = 0xFFFFFFFF;

    public static final int DEFAULT = 0x0000001;
    public static final int FLAG_INCLUDE_NOT_IMPORTANT_VIEWS = 0x0000002;
    public static final int FLAG_REQUEST_TOUCH_EXPLORATION_MODE = 0x0000004;
    public static final int FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY = 0x00000008;
    public static final int FLAG_REPORT_VIEW_IDS = 0x00000010;
    public static final int FLAG_REQUEST_FILTER_KEY_EVENTS = 0x00000020;
    public static final int FLAG_RETRIEVE_INTERACTIVE_WINDOWS = 0x00000040;
    public static final int FLAG_ENABLE_ACCESSIBILITY_VOLUME = 0x00000080;
    public static final int FLAG_REQUEST_ACCESSIBILITY_BUTTON = 0x00000100;
    public static final int FLAG_REQUEST_FINGERPRINT_GESTURES = 0x00000200;
    public static final int FLAG_REQUEST_SHORTCUT_WARNING_DIALOG_SPOKEN_FEEDBACK = 0x00000400;
    public static final int FLAG_REQUEST_MULTI_FINGER_GESTURES = 0x00001000;
    public static final int FLAG_REQUEST_2_FINGER_PASSTHROUGH = 0x00002000;
    public static final int FLAG_SEND_MOTION_EVENTS = 0x00004000;
    // Transcribed verbatim from /tmp/aosp_api/android16.txt: 2048 (0x800) and
    // 32768 (0x8000). Both were absent, so a service ORing them into
    // `flags` referenced a field this class did not declare.
    public static final int FLAG_SERVICE_HANDLES_DOUBLE_TAP = 0x00000800;
    public static final int FLAG_INPUT_METHOD_EDITOR = 0x00008000;

    public static final int CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT = 0x00000001;
    public static final int CAPABILITY_CAN_REQUEST_TOUCH_EXPLORATION = 0x00000002;
    public static final int CAPABILITY_CAN_REQUEST_ENHANCED_WEB_ACCESSIBILITY = 0x00000004;
    // ⚠ NAME, not value: AOSP's public constant is
    // CAPABILITY_CAN_REQUEST_FILTER_KEY_EVENTS (8 / 0x8 -- verbatim from
    // /tmp/aosp_api/android16.txt). This class previously declared only
    // CAPABILITY_CAN_FILTER_KEY_EVENTS, which is not an AOSP public name at
    // all, so any app naming the real one failed to link. The old spelling is
    // kept alongside it: it has been in this tree since the class landed, it
    // costs one static int, and removing it could break framework code that
    // adopted the wrong name.
    public static final int CAPABILITY_CAN_REQUEST_FILTER_KEY_EVENTS = 0x00000008;
    public static final int CAPABILITY_CAN_FILTER_KEY_EVENTS = 0x00000008;
    public static final int CAPABILITY_CAN_CONTROL_MAGNIFICATION = 0x00000010;
    public static final int CAPABILITY_CAN_PERFORM_GESTURES = 0x00000020;
    public static final int CAPABILITY_CAN_REQUEST_FINGERPRINT_GESTURES = 0x00000040;
    public static final int CAPABILITY_CAN_TAKE_SCREENSHOT = 0x00000080;

    /** Bitmask of the event types this service wants delivered. */
    public int eventTypes;
    /** Packages the service is scoped to, or null for all. */
    public String[] packageNames;
    /** Bitmask of FEEDBACK_* this service produces. */
    public int feedbackType;
    /** Milliseconds the platform waits before delivering an event. */
    public long notificationTimeout;
    /** Bitmask of the FLAG_* above. */
    public int flags;

    private String mId;
    private ResolveInfo mResolveInfo;
    private String mSettingsActivityName;
    private int mCapabilities;
    private String mDescription;
    private String mSummary;
    private String mIntro;
    private String mTileServiceName;
    private boolean mIsAccessibilityTool;
    private int mInteractiveUiTimeout;
    private int mNonInteractiveUiTimeout;
    private int mMotionEventSources;

    public AccessibilityServiceInfo() { }

    /** "<package>/<class>" of the declaring service component. */
    public String getId() { return mId; }

    public ResolveInfo getResolveInfo() { return mResolveInfo; }

    public String getSettingsActivityName() { return mSettingsActivityName; }

    public int getCapabilities() { return mCapabilities; }

    public boolean getCanRetrieveWindowContent() {
        return (mCapabilities & CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT) != 0;
    }

    /** Deprecated in AOSP in favor of loadDescription(PackageManager). */
    public String getDescription() { return mDescription; }

    /**
     * AOSP resolves the localized description against the service's own
     * resources; with no service installed there is nothing to resolve, and
     * AOSP itself returns null when the description resource is absent.
     */
    public String loadDescription(PackageManager packageManager) {
        return mDescription;
    }

    /**
     * API 29 timeouts. AOSP stores whatever the service sets and hands it back;
     * 0 means "no recommendation", which is the value a service that never set
     * one reads on a device too.
     */
    public int getInteractiveUiTimeoutMillis() { return mInteractiveUiTimeout; }

    public void setInteractiveUiTimeoutMillis(int timeout) {
        mInteractiveUiTimeout = timeout;
    }

    public int getNonInteractiveUiTimeoutMillis() { return mNonInteractiveUiTimeout; }

    public void setNonInteractiveUiTimeoutMillis(int timeout) {
        mNonInteractiveUiTimeout = timeout;
    }

    /**
     * API 34. Bitmask of android.view.InputDevice sources whose generic
     * MotionEvents the service wants. A plain round-trip in AOSP as well -- the
     * platform reads the field back out when it binds the service.
     */
    public int getMotionEventSources() { return mMotionEventSources; }

    public void setMotionEventSources(int sources) { mMotionEventSources = sources; }

    /** API 31. The tile a service offers for the quick-settings panel, or null.
     *  Mac.apk parses no accessibility-service metadata, so it is null -- the
     *  same value AOSP reports for a service that declares no tile. */
    public String getTileServiceName() { return mTileServiceName; }

    /** API 31. False unless the service declared itself an accessibility tool. */
    public boolean isAccessibilityTool() { return mIsAccessibilityTool; }

    /**
     * API 30. AOSP resolves a localized string from the service's own
     * resources and returns null when the resource id is 0 (unset). We parse no
     * service metadata, so the id is always unset and null is the AOSP-faithful
     * answer, not a shortcut.
     */
    public CharSequence loadSummary(PackageManager packageManager) { return mSummary; }

    /** API 31; same contract as loadSummary. */
    public CharSequence loadIntro(PackageManager packageManager) { return mIntro; }

    /** AOSP returns the literal "UNKNOWN" for an unrecognised capability, NOT
     *  null and NOT the number. */
    public static String capabilityToString(int capability) {
        switch (capability) {
            case CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT:
                return "CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT";
            case CAPABILITY_CAN_REQUEST_TOUCH_EXPLORATION:
                return "CAPABILITY_CAN_REQUEST_TOUCH_EXPLORATION";
            case CAPABILITY_CAN_REQUEST_FILTER_KEY_EVENTS:
                return "CAPABILITY_CAN_REQUEST_FILTER_KEY_EVENTS";
            case CAPABILITY_CAN_CONTROL_MAGNIFICATION:
                return "CAPABILITY_CAN_CONTROL_MAGNIFICATION";
            case CAPABILITY_CAN_PERFORM_GESTURES:
                return "CAPABILITY_CAN_PERFORM_GESTURES";
            case CAPABILITY_CAN_REQUEST_FINGERPRINT_GESTURES:
                return "CAPABILITY_CAN_REQUEST_FINGERPRINT_GESTURES";
            case CAPABILITY_CAN_TAKE_SCREENSHOT:
                return "CAPABILITY_CAN_TAKE_SCREENSHOT";
            default:
                return "UNKNOWN";
        }
    }

    public static String feedbackTypeToString(int feedbackType) {
        StringBuilder sb = new StringBuilder("[");
        while (feedbackType != 0) {
            final int feedbackTypeBit = (1 << Integer.numberOfTrailingZeros(feedbackType));
            feedbackType &= ~feedbackTypeBit;
            if (sb.length() > 1) sb.append(", ");
            switch (feedbackTypeBit) {
                case FEEDBACK_AUDIBLE: sb.append("FEEDBACK_AUDIBLE"); break;
                case FEEDBACK_HAPTIC:  sb.append("FEEDBACK_HAPTIC");  break;
                case FEEDBACK_GENERIC: sb.append("FEEDBACK_GENERIC"); break;
                case FEEDBACK_SPOKEN:  sb.append("FEEDBACK_SPOKEN");  break;
                case FEEDBACK_VISUAL:  sb.append("FEEDBACK_VISUAL");  break;
                case FEEDBACK_BRAILLE: sb.append("FEEDBACK_BRAILLE"); break;
                default: break;
            }
        }
        return sb.append("]").toString();
    }

    /** ⚠ AOSP returns NULL for an unrecognised flag; this used to return
     *  String.valueOf(flag), so a caller null-checking the result never saw the
     *  "unknown" case. The case list is AOSP's, verbatim.
     *  frameworks/base/core/java/android/accessibilityservice/AccessibilityServiceInfo.java */
    public static String flagToString(int flag) {
        switch (flag) {
            case DEFAULT: return "DEFAULT";
            case FLAG_INCLUDE_NOT_IMPORTANT_VIEWS: return "FLAG_INCLUDE_NOT_IMPORTANT_VIEWS";
            case FLAG_REQUEST_TOUCH_EXPLORATION_MODE: return "FLAG_REQUEST_TOUCH_EXPLORATION_MODE";
            case FLAG_SERVICE_HANDLES_DOUBLE_TAP: return "FLAG_SERVICE_HANDLES_DOUBLE_TAP";
            case FLAG_REQUEST_MULTI_FINGER_GESTURES: return "FLAG_REQUEST_MULTI_FINGER_GESTURES";
            case FLAG_REQUEST_2_FINGER_PASSTHROUGH: return "FLAG_REQUEST_2_FINGER_PASSTHROUGH";
            case FLAG_SEND_MOTION_EVENTS: return "FLAG_SEND_MOTION_EVENTS";
            case FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY:
                return "FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY";
            case FLAG_REPORT_VIEW_IDS: return "FLAG_REPORT_VIEW_IDS";
            case FLAG_REQUEST_FILTER_KEY_EVENTS: return "FLAG_REQUEST_FILTER_KEY_EVENTS";
            case FLAG_RETRIEVE_INTERACTIVE_WINDOWS: return "FLAG_RETRIEVE_INTERACTIVE_WINDOWS";
            case FLAG_ENABLE_ACCESSIBILITY_VOLUME: return "FLAG_ENABLE_ACCESSIBILITY_VOLUME";
            case FLAG_REQUEST_ACCESSIBILITY_BUTTON: return "FLAG_REQUEST_ACCESSIBILITY_BUTTON";
            case FLAG_REQUEST_FINGERPRINT_GESTURES: return "FLAG_REQUEST_FINGERPRINT_GESTURES";
            case FLAG_REQUEST_SHORTCUT_WARNING_DIALOG_SPOKEN_FEEDBACK:
                return "FLAG_REQUEST_SHORTCUT_WARNING_DIALOG_SPOKEN_FEEDBACK";
            case FLAG_INPUT_METHOD_EDITOR: return "FLAG_INPUT_METHOD_EDITOR";
            default: return null;
        }
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flagz) {
        dest.writeInt(eventTypes);
        dest.writeStringArray(packageNames);
        dest.writeInt(feedbackType);
        dest.writeLong(notificationTimeout);
        dest.writeInt(flags);
        dest.writeString(mId);
        dest.writeString(mSettingsActivityName);
        dest.writeInt(mCapabilities);
        dest.writeString(mDescription);
        dest.writeString(mSummary);
        dest.writeString(mIntro);
        dest.writeString(mTileServiceName);
        dest.writeInt(mIsAccessibilityTool ? 1 : 0);
        dest.writeInt(mInteractiveUiTimeout);
        dest.writeInt(mNonInteractiveUiTimeout);
        dest.writeInt(mMotionEventSources);
    }

    public static final Creator<AccessibilityServiceInfo> CREATOR =
            new Creator<AccessibilityServiceInfo>() {
        @Override public AccessibilityServiceInfo createFromParcel(Parcel in) {
            AccessibilityServiceInfo info = new AccessibilityServiceInfo();
            info.eventTypes = in.readInt();
            info.packageNames = in.createStringArray();
            info.feedbackType = in.readInt();
            info.notificationTimeout = in.readLong();
            info.flags = in.readInt();
            info.mId = in.readString();
            info.mSettingsActivityName = in.readString();
            info.mCapabilities = in.readInt();
            info.mDescription = in.readString();
            info.mSummary = in.readString();
            info.mIntro = in.readString();
            info.mTileServiceName = in.readString();
            info.mIsAccessibilityTool = in.readInt() != 0;
            info.mInteractiveUiTimeout = in.readInt();
            info.mNonInteractiveUiTimeout = in.readInt();
            info.mMotionEventSources = in.readInt();
            return info;
        }
        @Override public AccessibilityServiceInfo[] newArray(int size) {
            return new AccessibilityServiceInfo[size];
        }
    };
}
