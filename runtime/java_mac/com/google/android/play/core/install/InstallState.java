package com.google.android.play.core.install;

import com.google.android.play.core.install.model.InstallStatus;

// com.google.android.play.core.install.InstallState -- the snapshot delivered to
// an InstallStateUpdatedListener during a flexible in-app update. We never start
// a download, so no instance is ever produced; the type exists so listeners
// compile. Methods mirror the published Play Core InstallState.
public final class InstallState {
    private final int installStatus;
    private final int installErrorCode;
    private final long bytesDownloaded;
    private final long totalBytesToDownload;
    private final String packageName;

    InstallState(int installStatus, int installErrorCode, long bytesDownloaded,
                 long totalBytesToDownload, String packageName) {
        this.installStatus = installStatus;
        this.installErrorCode = installErrorCode;
        this.bytesDownloaded = bytesDownloaded;
        this.totalBytesToDownload = totalBytesToDownload;
        this.packageName = packageName;
    }

    public int installStatus() { return installStatus; }
    public int installErrorCode() { return installErrorCode; }
    public long bytesDownloaded() { return bytesDownloaded; }
    public long totalBytesToDownload() { return totalBytesToDownload; }
    public String packageName() { return packageName; }
}
