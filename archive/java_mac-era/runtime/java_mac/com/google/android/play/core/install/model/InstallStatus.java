package com.google.android.play.core.install.model;

// com.google.android.play.core.install.model.InstallStatus -- the lifecycle
// states a flexible in-app-update download passes through. We never start a
// download, so AppUpdateInfo.installStatus() reports UNKNOWN. Values match the
// published Play Core @IntDef.
public final class InstallStatus {
    private InstallStatus() {}

    public static final int UNKNOWN = 0;
    public static final int PENDING = 1;
    public static final int DOWNLOADING = 2;
    public static final int INSTALLING = 3;
    public static final int INSTALLED = 4;
    public static final int FAILED = 5;
    public static final int CANCELED = 6;
    public static final int DOWNLOADED = 11;
    public static final int REQUIRES_UI_INTENT = 10;
}
