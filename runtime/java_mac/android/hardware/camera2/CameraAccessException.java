// android.hardware.camera2.CameraAccessException -- thrown when a
// camera operation fails. Mirrors AOSP's checked-exception + reason
// codes. Mac.apk has no camera, so operations that would touch a real
// device throw this with CAMERA_DISCONNECTED.
package android.hardware.camera2;

import android.util.AndroidException;

public class CameraAccessException extends AndroidException {
    public static final int CAMERA_IN_USE = 4;
    public static final int MAX_CAMERAS_IN_USE = 5;
    public static final int CAMERA_DISABLED = 1;
    public static final int CAMERA_DISCONNECTED = 2;
    public static final int CAMERA_ERROR = 3;

    private final int mReason;

    public CameraAccessException(int problem) { this(problem, getDefaultMessage(problem)); }
    public CameraAccessException(int problem, String message) { super(message); mReason = problem; }
    public CameraAccessException(int problem, String message, Throwable cause) {
        super(message, cause); mReason = problem;
    }
    public CameraAccessException(int problem, Throwable cause) {
        super(getDefaultMessage(problem), cause); mReason = problem;
    }

    public final int getReason() { return mReason; }

    private static String getDefaultMessage(int problem) {
        switch (problem) {
            case CAMERA_IN_USE: return "The camera device is in use already";
            case MAX_CAMERAS_IN_USE: return "The system-wide limit for number of open cameras has been reached";
            case CAMERA_DISCONNECTED: return "The camera device is removable and has been disconnected";
            case CAMERA_DISABLED: return "The camera is disabled due to a device policy, and cannot be opened";
            case CAMERA_ERROR: return "The camera device is currently in the error state";
            default: return null;
        }
    }
}
