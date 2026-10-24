package com.google.android.play.core.splitinstall;

// com.google.android.play.core.splitinstall.SplitInstallException -- the
// exception a failed split-install Task carries. Apps catch it and inspect
// getErrorCode() to choose a degrade path. On a de-Googled device startInstall()
// fails its Task with one of these (API_NOT_AVAILABLE), so the type and its
// getErrorCode() accessor must be exact. Extends RuntimeException to match the
// published Play Core type (it is not a checked exception).
public class SplitInstallException extends RuntimeException {
    private final int errorCode;

    public SplitInstallException(int errorCode) {
        super("Split Install Error: " + errorCode);
        this.errorCode = errorCode;
    }

    public int getErrorCode() { return errorCode; }
}
