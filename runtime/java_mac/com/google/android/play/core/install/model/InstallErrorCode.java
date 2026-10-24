package com.google.android.play.core.install.model;

// com.google.android.play.core.install.model.InstallErrorCode -- the error codes
// an InstallState.installErrorCode() can carry during a flexible in-app update.
// We never start a download, so NO_ERROR is the only value an InstallState would
// ever carry; the constants exist so apps that branch on them compile. Values
// match the published Play Core @IntDef.
public final class InstallErrorCode {
    private InstallErrorCode() {}

    public static final int NO_ERROR = 0;
    public static final int NO_ERROR_PARTIALLY_ALLOWED = 1;
    public static final int ERROR_UNKNOWN = -2;
    public static final int ERROR_API_NOT_AVAILABLE = -3;
    public static final int ERROR_INVALID_REQUEST = -4;
    public static final int ERROR_INSTALL_UNAVAILABLE = -5;
    public static final int ERROR_INSTALL_NOT_ALLOWED = -6;
    public static final int ERROR_DOWNLOAD_NOT_PRESENT = -7;
    public static final int ERROR_INTERNAL_ERROR = -100;
    public static final int ERROR_PLAY_STORE_NOT_FOUND = -9;
    public static final int ERROR_APP_NOT_OWNED = -10;
}
