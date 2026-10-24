// android.printservice.PrintJob
//
// A queued print job as seen by a PrintService: the service inspects it and
// drives it through start / block / complete / fail / cancel.
//
// The state machine here is REAL, and it is the part that matters: every guard
// AOSP applies (you may only complete() a STARTED job; you may not fail() one
// already in a terminal state; block() only from STARTED or BLOCKED; and so on)
// is reproduced, and a successful transition really does update the job's
// PrintJobInfo, so a service that reads isStarted()/getInfo() back sees what it
// set. The difference from a device is that AOSP performs the transition by IPC
// to the spooler and adopts the spooler's answer, while here the PrintJobInfo
// this object holds IS the only copy -- there is no spooler (see PrintService),
// and consequently nothing ever constructs one of these in the first place.
//
// Mirrors frameworks/base/core/java/android/printservice/PrintJob.java
// (android16-release).
package android.printservice;

import android.content.Context;
import android.print.PrintJobId;
import android.print.PrintJobInfo;

public final class PrintJob {

    private final Context mContext;
    private final PrintDocument mDocument;
    private PrintJobInfo mCachedInfo;

    PrintJob(Context context, PrintJobInfo jobInfo) {
        mContext = context;
        mCachedInfo = jobInfo;
        mDocument = new PrintDocument(jobInfo.getId(), jobInfo.getDocumentInfo());
    }

    public PrintJobId getId() {
        PrintService.throwIfNotCalledOnMainThread();
        return mCachedInfo.getId();
    }

    public PrintJobInfo getInfo() {
        PrintService.throwIfNotCalledOnMainThread();
        return mCachedInfo;
    }

    public PrintDocument getDocument() {
        PrintService.throwIfNotCalledOnMainThread();
        return mDocument;
    }

    public boolean isQueued() {
        PrintService.throwIfNotCalledOnMainThread();
        return getInfo().getState() == PrintJobInfo.STATE_QUEUED;
    }

    public boolean isStarted() {
        PrintService.throwIfNotCalledOnMainThread();
        return getInfo().getState() == PrintJobInfo.STATE_STARTED;
    }

    public boolean isBlocked() {
        PrintService.throwIfNotCalledOnMainThread();
        return getInfo().getState() == PrintJobInfo.STATE_BLOCKED;
    }

    public boolean isCompleted() {
        PrintService.throwIfNotCalledOnMainThread();
        return getInfo().getState() == PrintJobInfo.STATE_COMPLETED;
    }

    public boolean isFailed() {
        PrintService.throwIfNotCalledOnMainThread();
        return getInfo().getState() == PrintJobInfo.STATE_FAILED;
    }

    public boolean isCancelled() {
        PrintService.throwIfNotCalledOnMainThread();
        return getInfo().getState() == PrintJobInfo.STATE_CANCELED;
    }

    public boolean start() {
        PrintService.throwIfNotCalledOnMainThread();
        final int state = getInfo().getState();
        if (state == PrintJobInfo.STATE_QUEUED
                || state == PrintJobInfo.STATE_BLOCKED) {
            return setState(PrintJobInfo.STATE_STARTED, null);
        }
        return false;
    }

    public boolean block(String reason) {
        PrintService.throwIfNotCalledOnMainThread();
        final int state = getInfo().getState();
        if (state == PrintJobInfo.STATE_STARTED || state == PrintJobInfo.STATE_BLOCKED) {
            return setState(PrintJobInfo.STATE_BLOCKED, reason);
        }
        return false;
    }

    public boolean complete() {
        PrintService.throwIfNotCalledOnMainThread();
        if (isStarted()) {
            return setState(PrintJobInfo.STATE_COMPLETED, null);
        }
        return false;
    }

    public boolean fail(String error) {
        PrintService.throwIfNotCalledOnMainThread();
        if (!isInImmutableState()) {
            return setState(PrintJobInfo.STATE_FAILED, error);
        }
        return false;
    }

    public boolean cancel() {
        PrintService.throwIfNotCalledOnMainThread();
        if (!isInImmutableState()) {
            return setState(PrintJobInfo.STATE_CANCELED, null);
        }
        return false;
    }

    public void setProgress(float progress) {
        PrintService.throwIfNotCalledOnMainThread();
        mCachedInfo.setProgress(progress);
    }

    public void setStatus(CharSequence status) {
        PrintService.throwIfNotCalledOnMainThread();
        mCachedInfo.setStatus(status);
    }

    public void setStatus(int statusResId) {
        PrintService.throwIfNotCalledOnMainThread();
        mCachedInfo.setStatus(statusResId,
                mContext != null ? mContext.getPackageName() : "");
    }

    public boolean setTag(String tag) {
        PrintService.throwIfNotCalledOnMainThread();
        if (isInImmutableState()) {
            return false;
        }
        mCachedInfo.setTag(tag);
        return true;
    }

    public String getTag() {
        PrintService.throwIfNotCalledOnMainThread();
        return getInfo().getTag();
    }

    public String getAdvancedStringOption(String key) {
        PrintService.throwIfNotCalledOnMainThread();
        return getInfo().getAdvancedStringOption(key);
    }

    public boolean hasAdvancedOption(String key) {
        PrintService.throwIfNotCalledOnMainThread();
        return getInfo().hasAdvancedOption(key);
    }

    public int getAdvancedIntOption(String key) {
        PrintService.throwIfNotCalledOnMainThread();
        return getInfo().getAdvancedIntOption(key);
    }

    private boolean isInImmutableState() {
        final int state = mCachedInfo.getState();
        return state == PrintJobInfo.STATE_COMPLETED
                || state == PrintJobInfo.STATE_CANCELED
                || state == PrintJobInfo.STATE_FAILED;
    }

    private boolean setState(int state, String error) {
        mCachedInfo.setState(state);
        if (error != null) {
            mCachedInfo.setStatus(error);
        }
        return true;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        PrintJob other = (PrintJob) obj;
        return java.util.Objects.equals(mCachedInfo.getId(), other.mCachedInfo.getId());
    }

    @Override
    public int hashCode() {
        PrintJobId id = mCachedInfo.getId();
        return id == null ? 0 : id.hashCode();
    }
}
