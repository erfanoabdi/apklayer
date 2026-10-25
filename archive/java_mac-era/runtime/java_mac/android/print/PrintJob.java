// android.print.PrintJob
//
// The handle PrintManager.print() returns. It is a thin view over the job's
// PrintJobInfo, re-read from PrintManager on every query unless the job has
// reached an immutable terminal state -- so an app that polls isCompleted() /
// isFailed() after calling print() observes the real outcome of the job rather
// than a snapshot taken before the adapter ran.
// Mirrors frameworks/base/core/java/android/print/PrintJob.java
// (android16-release).
package android.print;

import java.util.Objects;

public final class PrintJob {

    private final PrintManager mPrintManager;
    private PrintJobInfo mCachedInfo;

    PrintJob(PrintJobInfo info, PrintManager printManager) {
        mCachedInfo = info;
        mPrintManager = printManager;
    }

    public PrintJobId getId() {
        return mCachedInfo.getId();
    }

    public PrintJobInfo getInfo() {
        if (isInImmutableState()) {
            return mCachedInfo;
        }
        PrintJobInfo info = mPrintManager.getPrintJobInfo(mCachedInfo.getId());
        if (info != null) {
            mCachedInfo = info;
        }
        return mCachedInfo;
    }

    public void cancel() {
        final int state = getInfo().getState();
        if (state == PrintJobInfo.STATE_QUEUED
                || state == PrintJobInfo.STATE_STARTED
                || state == PrintJobInfo.STATE_BLOCKED
                || state == PrintJobInfo.STATE_FAILED) {
            mPrintManager.cancelPrintJob(mCachedInfo.getId());
        }
    }

    public void restart() {
        if (isFailed()) {
            mPrintManager.restartPrintJob(mCachedInfo.getId());
        }
    }

    public boolean isQueued() {
        return getInfo().getState() == PrintJobInfo.STATE_QUEUED;
    }

    public boolean isStarted() {
        return getInfo().getState() == PrintJobInfo.STATE_STARTED;
    }

    public boolean isBlocked() {
        return getInfo().getState() == PrintJobInfo.STATE_BLOCKED;
    }

    public boolean isCompleted() {
        return getInfo().getState() == PrintJobInfo.STATE_COMPLETED;
    }

    public boolean isFailed() {
        return getInfo().getState() == PrintJobInfo.STATE_FAILED;
    }

    public boolean isCancelled() {
        return getInfo().getState() == PrintJobInfo.STATE_CANCELED;
    }

    private boolean isInImmutableState() {
        final int state = mCachedInfo.getState();
        return state == PrintJobInfo.STATE_COMPLETED
                || state == PrintJobInfo.STATE_CANCELED;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        PrintJob other = (PrintJob) obj;
        return Objects.equals(mCachedInfo.getId(), other.mCachedInfo.getId());
    }

    @Override
    public int hashCode() {
        PrintJobId printJobId = mCachedInfo.getId();
        if (printJobId == null) {
            return 0;
        }
        return printJobId.hashCode();
    }
}
