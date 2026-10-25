package androidx.window.sidecar;
/** The device's fold posture. Mac.apk is a flat display -> POSTURE_OPENED. */
public final class SidecarDeviceState {
    public static final int POSTURE_UNKNOWN = 0;
    public static final int POSTURE_CLOSED = 1;
    public static final int POSTURE_HALF_OPENED = 2;
    public static final int POSTURE_OPENED = 3;
    public static final int POSTURE_FLIPPED = 4;
    /** Public field, as in the AOSP sidecar surface. */
    public int posture = POSTURE_OPENED;
    public SidecarDeviceState() {}
}
