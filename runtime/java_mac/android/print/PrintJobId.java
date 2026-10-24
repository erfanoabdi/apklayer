// android.print.PrintJobId
//
// An opaque, system-assigned identifier for a print job. Mirrors
// frameworks/base/core/java/android/print/PrintJobId.java (android16-release);
// AOSP's real id is a randomly generated UUID string, which is exactly what we
// generate here, so nothing about the identity is a placeholder.
package android.print;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.UUID;

public final class PrintJobId implements Parcelable {

    private final String mValue;

    /** @hide */
    public PrintJobId() {
        this(UUID.randomUUID().toString());
    }

    /** @hide */
    public PrintJobId(String value) {
        if (value == null) {
            throw new IllegalArgumentException("value cannot be null");
        }
        mValue = value;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + mValue.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        PrintJobId other = (PrintJobId) obj;
        return mValue.equals(other.mValue);
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeString(mValue);
    }

    @Override
    public int describeContents() { return 0; }

    /** @hide */
    public String flattenToString() { return mValue; }

    /** @hide */
    public static PrintJobId unflattenFromString(String string) {
        return new PrintJobId(string);
    }

    @Override
    public String toString() { return mValue; }

    public static final Parcelable.Creator<PrintJobId> CREATOR =
            new Parcelable.Creator<PrintJobId>() {
        @Override
        public PrintJobId createFromParcel(Parcel parcel) {
            String value = parcel.readString();
            if (value == null) {
                throw new IllegalArgumentException("value cannot be null");
            }
            return new PrintJobId(value);
        }

        @Override
        public PrintJobId[] newArray(int size) {
            return new PrintJobId[size];
        }
    };
}
