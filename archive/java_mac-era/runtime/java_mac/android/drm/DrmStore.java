// android.drm.DrmStore -- the constant namespaces the legacy DRM framework
// speaks in: what you want to do with the content (Action), what the content
// IS (DrmObjectType), what state playback is in (Playback), what the rights
// look like (RightsStatus), and the column names constraints come back under
// (ConstraintsColumns).
//
// Pure constants and nested holder types, mirrored verbatim from
// frameworks/base/drm/java/android/drm/DrmStore.java (android16-release).
// Every value here is compared against by app code and by DrmManagerClient's
// own argument validation, so the literals must match AOSP exactly.
//
// The nested classes carry EXPLICIT public no-arg constructors because AOSP
// declares them explicitly (current.txt: `ctor @Deprecated public
// DrmStore.Action();` and siblings) -- these are published API even though
// instantiating a constant holder is pointless.
package android.drm;

@Deprecated
public class DrmStore {

    public DrmStore() {}

    /** Column names for the ContentValues returned by
     *  DrmManagerClient.getConstraints. */
    public interface ConstraintsColumns {
        public static final String MAX_REPEAT_COUNT = "max_repeat_count";
        public static final String REMAINING_REPEAT_COUNT = "remaining_repeat_count";
        public static final String LICENSE_START_TIME = "license_start_time";
        public static final String LICENSE_EXPIRY_TIME = "license_expiry_time";
        public static final String LICENSE_AVAILABLE_TIME = "license_available_time";
        public static final String EXTENDED_METADATA = "extended_metadata";
    }

    public static class DrmObjectType {
        public static final int UNKNOWN = 0x00;
        public static final int CONTENT = 0x01;
        public static final int RIGHTS_OBJECT = 0x02;
        public static final int TRIGGER_OBJECT = 0x03;
        public DrmObjectType() {}
    }

    public static class Playback {
        public static final int START = 0x00;
        public static final int STOP = 0x01;
        public static final int PAUSE = 0x02;
        public static final int RESUME = 0x03;

        /* package */ static boolean isValid(int playbackStatus) {
            switch (playbackStatus) {
                case START:
                case STOP:
                case PAUSE:
                case RESUME:
                    return true;
            }
            return false;
        }

        public Playback() {}
    }

    public static class Action {
        public static final int DEFAULT = 0x00;
        public static final int PLAY = 0x01;
        public static final int RINGTONE = 0x02;
        public static final int TRANSFER = 0x03;
        public static final int OUTPUT = 0x04;
        public static final int PREVIEW = 0x05;
        public static final int EXECUTE = 0x06;
        public static final int DISPLAY = 0x07;

        /** Package-private in AOSP and used by DrmManagerClient's argument
         *  checks; an invalid action must raise IllegalArgumentException there
         *  BEFORE any rights question is asked. */
        /* package */ static boolean isValid(int action) {
            switch (action) {
                case DEFAULT:
                case PLAY:
                case RINGTONE:
                case TRANSFER:
                case OUTPUT:
                case PREVIEW:
                case EXECUTE:
                case DISPLAY:
                    return true;
            }
            return false;
        }

        public Action() {}
    }

    public static class RightsStatus {
        public static final int RIGHTS_VALID = 0x00;
        public static final int RIGHTS_INVALID = 0x01;
        public static final int RIGHTS_EXPIRED = 0x02;
        public static final int RIGHTS_NOT_ACQUIRED = 0x03;
        public RightsStatus() {}
    }
}
