// android.print.PageRange
//
// An inclusive range of zero-based page indices, handed to
// PrintDocumentAdapter.onWrite to say which pages to render. Pure value type.
// Mirrors frameworks/base/core/java/android/print/PageRange.java
// (android16-release).
package android.print;

import android.os.Parcel;
import android.os.Parcelable;

public final class PageRange implements Parcelable {

    /** Constant for specifying all pages. */
    public static final PageRange ALL_PAGES = new PageRange(0, Integer.MAX_VALUE);

    /** @hide */
    public static final PageRange[] ALL_PAGES_ARRAY = new PageRange[] { PageRange.ALL_PAGES };

    private final int mStart;
    private final int mEnd;

    public PageRange(int start, int end) {
        if (start < 0) {
            throw new IllegalArgumentException("start cannot be less than zero.");
        }
        if (end < 0) {
            throw new IllegalArgumentException("end cannot be less than zero.");
        }
        if (start > end) {
            throw new IllegalArgumentException("start must be lesser than end.");
        }
        mStart = start;
        mEnd = end;
    }

    private PageRange(Parcel parcel) {
        this(parcel.readInt(), parcel.readInt());
    }

    public int getStart() { return mStart; }

    public int getEnd() { return mEnd; }

    /** @hide Whether the given page index falls inside this range. */
    public boolean contains(int pageIndex) {
        return (pageIndex >= mStart) && (pageIndex <= mEnd);
    }

    /** @hide The number of pages in this range (inclusive of both ends). */
    public int getSize() {
        return mEnd - mStart + 1;
    }

    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(mStart);
        parcel.writeInt(mEnd);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + mEnd;
        result = prime * result + mStart;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        PageRange other = (PageRange) obj;
        if (mEnd != other.mEnd) return false;
        if (mStart != other.mStart) return false;
        return true;
    }

    // AOSP's exact format. The previous revision returned "start-end", which
    // an app that logs or parses the range would see differently from a device.
    @Override
    public String toString() {
        if (mStart == 0 && mEnd == Integer.MAX_VALUE) {
            return "PageRange[<all pages>]";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("PageRange[")
                .append(mStart)
                .append(" - ")
                .append(mEnd)
                .append("]");
        return builder.toString();
    }

    public static final Parcelable.Creator<PageRange> CREATOR =
            new Parcelable.Creator<PageRange>() {
        @Override
        public PageRange createFromParcel(Parcel parcel) {
            return new PageRange(parcel);
        }

        @Override
        public PageRange[] newArray(int size) {
            return new PageRange[size];
        }
    };
}
