package com.google.android.play.core.splitinstall;

// com.google.android.play.core.splitinstall.SplitInstallErrorCode -- the error
// codes a SplitInstallException carries (and that SplitInstallSessionState may
// report). On a de-Googled device a split-install request cannot reach the Play
// Store, so startInstall() fails its Task with ERROR_API_NOT_AVAILABLE wrapped
// in a SplitInstallException. Values match the published Play Core @IntDef.
public final class SplitInstallErrorCode {
    private SplitInstallErrorCode() {}

    public static final int NO_ERROR = 0;
    public static final int ACTIVE_SESSIONS_LIMIT_EXCEEDED = -1;
    public static final int MODULE_UNAVAILABLE = -2;
    public static final int INVALID_REQUEST = -3;
    public static final int SESSION_NOT_FOUND = -4;
    public static final int API_NOT_AVAILABLE = -5;
    public static final int NETWORK_ERROR = -6;
    public static final int ACCESS_DENIED = -7;
    public static final int INCOMPATIBLE_WITH_EXISTING_SESSION = -8;
    public static final int SERVICE_DIED = -9;
    public static final int COPY_ERROR = -10;
    public static final int INSUFFICIENT_STORAGE = -11;
    public static final int SPLITCOMPAT_VERIFICATION_ERROR = -12;
    public static final int SPLITCOMPAT_EMULATION_ERROR = -13;
    public static final int SPLITCOMPAT_COPY_ERROR = -14;
    public static final int PLAY_STORE_NOT_FOUND = -15;
    public static final int APP_NOT_OWNED = -16;
    public static final int INTERNAL_ERROR = -100;
}
