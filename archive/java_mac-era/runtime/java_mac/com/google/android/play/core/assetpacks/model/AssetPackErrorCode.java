package com.google.android.play.core.assetpacks.model;

// Play Core asset-pack error codes. Values match the Play Core library
// (NO_ERROR=0 is the only one we report -- every pack on Mac.apk is an
// install-time pack already present in the APK assets, so it is always
// COMPLETED with no error).
public final class AssetPackErrorCode {
    private AssetPackErrorCode() {}
    public static final int NO_ERROR = 0;
    public static final int APP_UNAVAILABLE = -1;
    public static final int PACK_UNAVAILABLE = -2;
    public static final int INVALID_REQUEST = -3;
    public static final int DOWNLOAD_NOT_FOUND = -4;
    public static final int API_NOT_AVAILABLE = -5;
    public static final int NETWORK_ERROR = -6;
    public static final int ACCESS_DENIED = -7;
    public static final int INSUFFICIENT_STORAGE = -10;
    public static final int SPLITCOMPAT_COPY_ERROR = -11;
    public static final int CONFIRMATION_NOT_REQUIRED = -12;
    public static final int UNRECOGNIZED_INSTALLATION = -13;
    public static final int INTERNAL_ERROR = -100;
}
