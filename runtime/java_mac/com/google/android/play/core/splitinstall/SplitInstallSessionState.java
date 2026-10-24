package com.google.android.play.core.splitinstall;

import android.app.PendingIntent;
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus;

import java.util.Collections;
import java.util.List;

// com.google.android.play.core.splitinstall.SplitInstallSessionState -- the
// snapshot delivered to a SplitInstallStateUpdatedListener during a dynamic
// feature install. We never start a real install (no Play Store backend), so no
// instance is ever produced by our SplitInstallManager; the type exists so
// listeners compile. Methods mirror the published Play Core SplitInstallSessionState.
public final class SplitInstallSessionState {
    private final int sessionId;
    private final int status;
    private final int errorCode;
    private final long bytesDownloaded;
    private final long totalBytesToDownload;
    private final List<String> moduleNames;
    private final List<String> languages;

    SplitInstallSessionState(int sessionId, int status, int errorCode,
                             long bytesDownloaded, long totalBytesToDownload,
                             List<String> moduleNames, List<String> languages) {
        this.sessionId = sessionId;
        this.status = status;
        this.errorCode = errorCode;
        this.bytesDownloaded = bytesDownloaded;
        this.totalBytesToDownload = totalBytesToDownload;
        this.moduleNames = (moduleNames != null) ? moduleNames : Collections.<String>emptyList();
        this.languages = (languages != null) ? languages : Collections.<String>emptyList();
    }

    public int sessionId() { return sessionId; }

    @SplitInstallSessionStatusAlias
    public int status() { return status; }

    @SplitInstallErrorCodeAlias
    public int errorCode() { return errorCode; }

    public long bytesDownloaded() { return bytesDownloaded; }
    public long totalBytesToDownload() { return totalBytesToDownload; }

    public List<String> moduleNames() { return moduleNames; }
    public List<String> languages() { return languages; }

    // Returned by Play Core when a session needs user confirmation. We never
    // reach that state, so this is always null.
    public PendingIntent resolutionIntent() { return null; }

    @Override
    public String toString() {
        return "SplitInstallSessionState{sessionId=" + sessionId + ", status=" + status
                + ", errorCode=" + errorCode + ", bytesDownloaded=" + bytesDownloaded
                + ", totalBytesToDownload=" + totalBytesToDownload + ", moduleNames="
                + moduleNames + ", languages=" + languages + "}";
    }

    // The published API annotates status()/errorCode() with @IntDef references;
    // we mirror the shape with these harmless private marker annotations so the
    // method signatures read like the SDK without depending on androidx @IntDef
    // (which is not on our compile classpath).
    private @interface SplitInstallSessionStatusAlias {}
    private @interface SplitInstallErrorCodeAlias {}
}
