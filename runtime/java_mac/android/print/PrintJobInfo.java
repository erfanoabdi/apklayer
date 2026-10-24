// android.print.PrintJobInfo
//
// The full mutable description of a print job: id, label, target printer,
// state, copies, page ranges, PrintAttributes, the PrintDocumentInfo the
// adapter reported, progress/status and a Bundle of print-service-specific
// "advanced options". PrintManager owns one per job and mutates it as the job
// runs, so PrintJob.getInfo() reflects what actually happened.
// Mirrors frameworks/base/core/java/android/print/PrintJobInfo.java
// (android16-release).
package android.print;

import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Arrays;

public final class PrintJobInfo implements Parcelable {

    /** @hide */
    public static final int STATE_ANY = -1;
    /** @hide */
    public static final int STATE_ANY_VISIBLE_TO_CLIENTS = -2;
    /** @hide */
    public static final int STATE_ANY_ACTIVE = -3;
    /** @hide */
    public static final int STATE_ANY_SCHEDULED = -4;

    public static final int STATE_CREATED = 1;
    public static final int STATE_QUEUED = 2;
    public static final int STATE_STARTED = 3;
    public static final int STATE_BLOCKED = 4;
    public static final int STATE_COMPLETED = 5;
    public static final int STATE_FAILED = 6;
    public static final int STATE_CANCELED = 7;

    private PrintJobId mId;
    private String mLabel;
    private PrinterId mPrinterId;
    private String mPrinterName;
    private int mState;
    private int mAppId;
    private String mTag;
    private long mCreationTime;
    private int mCopies;
    private PageRange[] mPageRanges;
    private PrintAttributes mAttributes;
    private PrintDocumentInfo mDocumentInfo;
    private float mProgress;
    private CharSequence mStatus;
    private int mStatusRes;
    private CharSequence mStatusResAppPackageName;
    private Bundle mAdvancedOptions;
    private boolean mCanceling;

    /** @hide */
    public PrintJobInfo() {
        mProgress = -1;
    }

    /** @hide */
    public PrintJobInfo(PrintJobInfo other) {
        mId = other.mId;
        mLabel = other.mLabel;
        mPrinterId = other.mPrinterId;
        mPrinterName = other.mPrinterName;
        mState = other.mState;
        mAppId = other.mAppId;
        mTag = other.mTag;
        mCreationTime = other.mCreationTime;
        mCopies = other.mCopies;
        mPageRanges = other.mPageRanges;
        mAttributes = other.mAttributes;
        mDocumentInfo = other.mDocumentInfo;
        mProgress = other.mProgress;
        mStatus = other.mStatus;
        mStatusRes = other.mStatusRes;
        mStatusResAppPackageName = other.mStatusResAppPackageName;
        mCanceling = other.mCanceling;
        mAdvancedOptions = other.mAdvancedOptions;
    }

    private PrintJobInfo(Parcel parcel) {
        mId = parcel.readParcelable(null, PrintJobId.class);
        mLabel = parcel.readString();
        mPrinterId = parcel.readParcelable(null, PrinterId.class);
        mPrinterName = parcel.readString();
        mState = parcel.readInt();
        mAppId = parcel.readInt();
        mTag = parcel.readString();
        mCreationTime = parcel.readLong();
        mCopies = parcel.readInt();
        Parcelable[] parcelables = parcel.readParcelableArray(null, PageRange.class);
        if (parcelables != null) {
            mPageRanges = new PageRange[parcelables.length];
            for (int i = 0; i < parcelables.length; i++) {
                mPageRanges[i] = (PageRange) parcelables[i];
            }
        }
        mAttributes = parcel.readParcelable(null, PrintAttributes.class);
        mDocumentInfo = parcel.readParcelable(null, PrintDocumentInfo.class);
        mProgress = parcel.readFloat();
        mStatus = parcel.readCharSequence();
        mStatusRes = parcel.readInt();
        mStatusResAppPackageName = parcel.readCharSequence();
        mCanceling = (parcel.readInt() == 1);
        mAdvancedOptions = parcel.readBundle();
        if (mAdvancedOptions != null && mAdvancedOptions.containsKey(null)) {
            throw new IllegalArgumentException("advanced options cannot have a null key");
        }
    }

    public PrintJobId getId() { return mId; }

    /** @hide */
    public void setId(PrintJobId id) { mId = id; }

    public String getLabel() { return mLabel; }

    /** @hide */
    public void setLabel(String label) { mLabel = label; }

    public PrinterId getPrinterId() { return mPrinterId; }

    /** @hide */
    public void setPrinterId(PrinterId printerId) { mPrinterId = printerId; }

    /** @hide */
    public String getPrinterName() { return mPrinterName; }

    /** @hide */
    public void setPrinterName(String printerName) { mPrinterName = printerName; }

    public int getState() { return mState; }

    /** @hide */
    public void setState(int state) { mState = state; }

    /** @hide */
    public void setProgress(float progress) {
        if (progress < 0 || progress > 1) {
            throw new IllegalArgumentException("progress not in [0, 1]: " + progress);
        }
        mProgress = progress;
    }

    /** @hide */
    public void setStatus(CharSequence status) {
        mStatusRes = 0;
        mStatusResAppPackageName = null;
        mStatus = status;
    }

    /** @hide */
    public void setStatus(int status, CharSequence appPackageName) {
        mStatus = null;
        mStatusRes = status;
        mStatusResAppPackageName = appPackageName;
    }

    /** @hide */
    public int getAppId() { return mAppId; }

    /** @hide */
    public void setAppId(int appId) { mAppId = appId; }

    /** @hide */
    public String getTag() { return mTag; }

    /** @hide */
    public void setTag(String tag) { mTag = tag; }

    public long getCreationTime() { return mCreationTime; }

    /** @hide */
    public void setCreationTime(long creationTime) {
        if (creationTime < 0) {
            throw new IllegalArgumentException("creationTime must be non-negative.");
        }
        mCreationTime = creationTime;
    }

    public int getCopies() { return mCopies; }

    /** @hide */
    public void setCopies(int copyCount) {
        if (copyCount < 1) {
            throw new IllegalArgumentException("Copies must be more than one.");
        }
        mCopies = copyCount;
    }

    public PageRange[] getPages() { return mPageRanges; }

    /** @hide */
    public void setPages(PageRange[] pageRanges) { mPageRanges = pageRanges; }

    public PrintAttributes getAttributes() { return mAttributes; }

    /** @hide */
    public void setAttributes(PrintAttributes attributes) { mAttributes = attributes; }

    /** @hide */
    public PrintDocumentInfo getDocumentInfo() { return mDocumentInfo; }

    /** @hide */
    public void setDocumentInfo(PrintDocumentInfo info) { mDocumentInfo = info; }

    /** @hide */
    public boolean isCancelling() { return mCanceling; }

    /** @hide */
    public void setCancelling(boolean cancelling) { mCanceling = cancelling; }

    /** @hide */
    public boolean shouldStayAwake() {
        return mCanceling || mState == STATE_STARTED || mState == STATE_QUEUED;
    }

    public boolean hasAdvancedOption(String key) {
        return mAdvancedOptions != null && mAdvancedOptions.containsKey(key);
    }

    public String getAdvancedStringOption(String key) {
        if (mAdvancedOptions != null) {
            return mAdvancedOptions.getString(key);
        }
        return null;
    }

    public int getAdvancedIntOption(String key) {
        if (mAdvancedOptions != null) {
            return mAdvancedOptions.getInt(key);
        }
        return 0;
    }

    /** @hide */
    public Bundle getAdvancedOptions() { return mAdvancedOptions; }

    /** @hide */
    public void setAdvancedOptions(Bundle options) { mAdvancedOptions = options; }

    /** @hide */
    public float getProgress() { return mProgress; }

    /** @hide */
    public CharSequence getStatus(PackageManager pm) {
        if (mStatusRes == 0) {
            return mStatus;
        }
        try {
            return pm.getResourcesForApplication(mStatusResAppPackageName.toString())
                    .getString(mStatusRes);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        } catch (Resources.NotFoundException e) {
            return null;
        }
    }

    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeParcelable(mId, flags);
        parcel.writeString(mLabel);
        parcel.writeParcelable(mPrinterId, flags);
        parcel.writeString(mPrinterName);
        parcel.writeInt(mState);
        parcel.writeInt(mAppId);
        parcel.writeString(mTag);
        parcel.writeLong(mCreationTime);
        parcel.writeInt(mCopies);
        parcel.writeParcelableArray(mPageRanges, flags);
        parcel.writeParcelable(mAttributes, flags);
        parcel.writeParcelable(mDocumentInfo, 0);
        parcel.writeFloat(mProgress);
        parcel.writeCharSequence(mStatus);
        parcel.writeInt(mStatusRes);
        parcel.writeCharSequence(mStatusResAppPackageName);
        parcel.writeInt(mCanceling ? 1 : 0);
        parcel.writeBundle(mAdvancedOptions);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("PrintJobInfo{");
        builder.append("label: ").append(mLabel);
        builder.append(", id: ").append(mId);
        builder.append(", state: ").append(stateToString(mState));
        builder.append(", printer: " + mPrinterId);
        builder.append(", tag: ").append(mTag);
        builder.append(", creationTime: " + mCreationTime);
        builder.append(", copies: ").append(mCopies);
        builder.append(", attributes: " + (mAttributes != null
                ? mAttributes.toString() : null));
        builder.append(", documentInfo: " + (mDocumentInfo != null
                ? mDocumentInfo.toString() : null));
        builder.append(", cancelling: " + mCanceling);
        builder.append(", pages: " + (mPageRanges != null
                ? Arrays.toString(mPageRanges) : null));
        builder.append(", hasAdvancedOptions: " + (mAdvancedOptions != null));
        builder.append(", progress: " + mProgress);
        builder.append(", status: " + (mStatus != null
                ? mStatus.toString() : null));
        builder.append(", statusRes: " + mStatusRes);
        builder.append(", statusResAppPackageName: " + (mStatusResAppPackageName != null
                ? mStatusResAppPackageName.toString() : null));
        builder.append("}");
        return builder.toString();
    }

    /** @hide */
    public static String stateToString(int state) {
        switch (state) {
            case STATE_CREATED: return "STATE_CREATED";
            case STATE_QUEUED: return "STATE_QUEUED";
            case STATE_STARTED: return "STATE_STARTED";
            case STATE_BLOCKED: return "STATE_BLOCKED";
            case STATE_FAILED: return "STATE_FAILED";
            case STATE_COMPLETED: return "STATE_COMPLETED";
            case STATE_CANCELED: return "STATE_CANCELED";
            default: return "STATE_UNKNOWN";
        }
    }

    /**
     * AOSP's builder mutates ONE prototype and build() returns that same
     * instance -- and its setters return void, not Builder. Both reproduced;
     * an app written against the SDK links on the void descriptors.
     */
    public static final class Builder {
        private final PrintJobInfo mPrototype;

        public Builder(PrintJobInfo prototype) {
            mPrototype = (prototype != null)
                    ? new PrintJobInfo(prototype)
                    : new PrintJobInfo();
        }

        public void setCopies(int copies) {
            mPrototype.mCopies = copies;
        }

        public void setAttributes(PrintAttributes attributes) {
            mPrototype.mAttributes = attributes;
        }

        public void setPages(PageRange[] pages) {
            mPrototype.mPageRanges = pages;
        }

        /** @hide */
        public void setProgress(float progress) {
            if (progress < 0 || progress > 1) {
                throw new IllegalArgumentException("progress not in [0, 1]: " + progress);
            }
            mPrototype.mProgress = progress;
        }

        /** @hide */
        public void setStatus(CharSequence status) {
            mPrototype.mStatus = status;
        }

        public void putAdvancedOption(String key, String value) {
            if (key == null) {
                throw new NullPointerException("key cannot be null");
            }
            if (mPrototype.mAdvancedOptions == null) {
                mPrototype.mAdvancedOptions = new Bundle();
            }
            mPrototype.mAdvancedOptions.putString(key, value);
        }

        public void putAdvancedOption(String key, int value) {
            if (key == null) {
                throw new NullPointerException("key cannot be null");
            }
            if (mPrototype.mAdvancedOptions == null) {
                mPrototype.mAdvancedOptions = new Bundle();
            }
            mPrototype.mAdvancedOptions.putInt(key, value);
        }

        public PrintJobInfo build() {
            return mPrototype;
        }
    }

    public static final Parcelable.Creator<PrintJobInfo> CREATOR =
            new Parcelable.Creator<PrintJobInfo>() {
        @Override
        public PrintJobInfo createFromParcel(Parcel parcel) {
            return new PrintJobInfo(parcel);
        }

        @Override
        public PrintJobInfo[] newArray(int size) {
            return new PrintJobInfo[size];
        }
    };
}
