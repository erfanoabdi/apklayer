// android.print.PrintDocumentInfo
//
// Metadata about the document a PrintDocumentAdapter produces: name, content
// type, page count and (once written) the data size in bytes. Built via its
// Builder and handed back through LayoutResultCallback.onLayoutFinished.
// Mirrors frameworks/base/core/java/android/print/PrintDocumentInfo.java
// (android16-release).
package android.print;

import android.os.Parcel;
import android.os.Parcelable;

public final class PrintDocumentInfo implements Parcelable {

    public static final int CONTENT_TYPE_UNKNOWN = -1;
    public static final int CONTENT_TYPE_DOCUMENT = 0;
    public static final int CONTENT_TYPE_PHOTO = 1;

    public static final int PAGE_COUNT_UNKNOWN = -1;

    private String mName;
    private int mPageCount;
    // AOSP leaves this at the int default, which is CONTENT_TYPE_DOCUMENT (0),
    // NOT CONTENT_TYPE_UNKNOWN. The previous revision defaulted the Builder to
    // CONTENT_TYPE_UNKNOWN, so an app that built an info without calling
    // setContentType read back -1 where a device reads 0.
    private int mContentType;
    private long mDataSize;

    private PrintDocumentInfo() {
        /* do nothing */
    }

    private PrintDocumentInfo(PrintDocumentInfo prototype) {
        mName = prototype.mName;
        mPageCount = prototype.mPageCount;
        mContentType = prototype.mContentType;
        mDataSize = prototype.mDataSize;
    }

    private PrintDocumentInfo(Parcel parcel) {
        mName = parcel.readString();
        if (mName == null || mName.isEmpty()) {
            throw new IllegalArgumentException("name cannot be empty");
        }
        mPageCount = parcel.readInt();
        if (mPageCount != PAGE_COUNT_UNKNOWN && mPageCount <= 0) {
            throw new IllegalArgumentException("invalid page count: " + mPageCount);
        }
        mContentType = parcel.readInt();
        mDataSize = parcel.readLong();
        if (mDataSize < 0) {
            throw new IllegalArgumentException("dataSize must be non-negative");
        }
    }

    public String getName() { return mName; }

    public int getPageCount() { return mPageCount; }

    public int getContentType() { return mContentType; }

    /**
     * The size in bytes of the written document. Zero until the document has
     * actually been written -- PrintManager sets it from the produced file, so
     * an app that reads it back off its own job's info sees the real size.
     */
    public long getDataSize() { return mDataSize; }

    /** @hide */
    public void setDataSize(long dataSize) { mDataSize = dataSize; }

    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeString(mName);
        parcel.writeInt(mPageCount);
        parcel.writeInt(mContentType);
        parcel.writeLong(mDataSize);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((mName != null) ? mName.hashCode() : 0);
        result = prime * result + mContentType;
        result = prime * result + mPageCount;
        result = prime * result + (int) mDataSize;
        result = prime * result + (int) (mDataSize >> 32);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        PrintDocumentInfo other = (PrintDocumentInfo) obj;
        if (mName == null ? other.mName != null : !mName.equals(other.mName)) return false;
        if (mContentType != other.mContentType) return false;
        if (mPageCount != other.mPageCount) return false;
        if (mDataSize != other.mDataSize) return false;
        return true;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("PrintDocumentInfo{");
        builder.append("name=").append(mName);
        builder.append(", pageCount=").append(mPageCount);
        builder.append(", contentType=").append(contentTypeToString(mContentType));
        builder.append(", dataSize=").append(mDataSize);
        builder.append("}");
        return builder.toString();
    }

    private String contentTypeToString(int contentType) {
        switch (contentType) {
            case CONTENT_TYPE_DOCUMENT: return "CONTENT_TYPE_DOCUMENT";
            case CONTENT_TYPE_PHOTO: return "CONTENT_TYPE_PHOTO";
            default: return "CONTENT_TYPE_UNKNOWN";
        }
    }

    public static final class Builder {
        private final PrintDocumentInfo mPrototype;

        public Builder(String name) {
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("name cannot be empty");
            }
            mPrototype = new PrintDocumentInfo();
            mPrototype.mName = name;
        }

        public Builder setPageCount(int pageCount) {
            if (pageCount < 0 && pageCount != PAGE_COUNT_UNKNOWN) {
                throw new IllegalArgumentException("pageCount"
                        + " must be greater than or equal to zero or"
                        + " DocumentInfo#PAGE_COUNT_UNKNOWN");
            }
            mPrototype.mPageCount = pageCount;
            return this;
        }

        public Builder setContentType(int type) {
            mPrototype.mContentType = type;
            return this;
        }

        public PrintDocumentInfo build() {
            // AOSP: zero pages means "unknown", because in that case the
            // spooler has to ask for all pages and count them in the PDF.
            if (mPrototype.mPageCount == 0) {
                mPrototype.mPageCount = PAGE_COUNT_UNKNOWN;
            }
            return new PrintDocumentInfo(mPrototype);
        }
    }

    public static final Parcelable.Creator<PrintDocumentInfo> CREATOR =
            new Parcelable.Creator<PrintDocumentInfo>() {
        @Override
        public PrintDocumentInfo createFromParcel(Parcel parcel) {
            return new PrintDocumentInfo(parcel);
        }

        @Override
        public PrintDocumentInfo[] newArray(int size) {
            return new PrintDocumentInfo[size];
        }
    };
}
