// android.print.PrinterId
//
// Identifies a printer: the ComponentName of the print service that discovered
// it plus a service-local id. Referenced by PrintJobInfo.getPrinterId(),
// PrinterInfo, PrinterCapabilitiesInfo and the whole android.printservice
// discovery API, so the type must exist for any of those to link.
// Mirrors frameworks/base/core/java/android/print/PrinterId.java
// (android16-release).
package android.print;

import android.content.ComponentName;
import android.os.Parcel;
import android.os.Parcelable;

public final class PrinterId implements Parcelable {

    private final ComponentName mServiceName;
    private final String mLocalId;

    /** @hide */
    public PrinterId(ComponentName serviceName, String localId) {
        if (serviceName == null) {
            throw new IllegalArgumentException("serviceName cannot be null");
        }
        if (localId == null) {
            throw new IllegalArgumentException("localId cannot be null");
        }
        mServiceName = serviceName;
        mLocalId = localId;
    }

    private PrinterId(Parcel parcel) {
        mServiceName = parcel.readParcelable(null, ComponentName.class);
        mLocalId = parcel.readString();
        if (mServiceName == null || mLocalId == null) {
            throw new IllegalArgumentException("malformed PrinterId in parcel");
        }
    }

    /** @hide */
    public ComponentName getServiceName() { return mServiceName; }

    public String getLocalId() { return mLocalId; }

    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeParcelable(mServiceName, flags);
        parcel.writeString(mLocalId);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null) return false;
        if (getClass() != object.getClass()) return false;
        PrinterId other = (PrinterId) object;
        if (!mServiceName.equals(other.mServiceName)) return false;
        if (!mLocalId.equals(other.mLocalId)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int hashCode = 1;
        hashCode = prime * hashCode + mServiceName.hashCode();
        hashCode = prime * hashCode + mLocalId.hashCode();
        return hashCode;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("PrinterId{");
        builder.append("serviceName=").append(mServiceName.flattenToString());
        builder.append(", localId=").append(mLocalId);
        builder.append('}');
        return builder.toString();
    }

    public static final Parcelable.Creator<PrinterId> CREATOR =
            new Parcelable.Creator<PrinterId>() {
        @Override
        public PrinterId createFromParcel(Parcel parcel) {
            return new PrinterId(parcel);
        }

        @Override
        public PrinterId[] newArray(int size) {
            return new PrinterId[size];
        }
    };
}
