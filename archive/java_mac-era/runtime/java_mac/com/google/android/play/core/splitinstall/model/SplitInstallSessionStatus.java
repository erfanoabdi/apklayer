package com.google.android.play.core.splitinstall.model;

// com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus --
// the lifecycle states a dynamic-feature-module install session passes through.
// On a de-Googled device we never start a real split install (there is no Play
// Store to fetch a module from), so a SplitInstallSessionState never reaches a
// downloading/installed state; the constants exist so apps that branch on
// session.status() compile. Values match the published Play Core @IntDef.
public final class SplitInstallSessionStatus {
    private SplitInstallSessionStatus() {}

    public static final int UNKNOWN = 0;
    public static final int PENDING = 1;
    public static final int REQUIRES_USER_CONFIRMATION = 8;
    public static final int DOWNLOADING = 2;
    public static final int DOWNLOADED = 3;
    public static final int INSTALLING = 4;
    public static final int INSTALLED = 5;
    public static final int FAILED = 6;
    public static final int CANCELING = 9;
    public static final int CANCELED = 7;
}
