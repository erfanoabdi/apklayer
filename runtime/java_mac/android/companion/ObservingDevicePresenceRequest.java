// android.companion.ObservingDevicePresenceRequest -- names either an
// association id or a Bluetooth service UUID whose presence an app wants to
// observe.
//
// A REAL Builder-built value type with no device dependency. What is absent is
// the OBSERVER: CompanionDeviceManager.startObservingDevicePresence cannot
// register one, because there is no companion transport and no association to
// observe (see that class).
//
// Mirrors
// frameworks/base/core/java/android/companion/ObservingDevicePresenceRequest.java;
// public surface confirmed against frameworks/base/core/api/current.txt.
package android.companion;

import android.os.Parcel;
import android.os.ParcelUuid;
import android.os.Parcelable;

public final class ObservingDevicePresenceRequest implements Parcelable {

    private final int mAssociationId;
    private final ParcelUuid mUuid;

    private ObservingDevicePresenceRequest(int associationId, ParcelUuid uuid) {
        mAssociationId = associationId;
        mUuid = uuid;
    }

    public int getAssociationId() { return mAssociationId; }

    public ParcelUuid getUuid() { return mUuid; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mAssociationId);
        dest.writeString(mUuid == null ? null : mUuid.toString());
    }

    public static final Creator<ObservingDevicePresenceRequest> CREATOR =
            new Creator<ObservingDevicePresenceRequest>() {
        @Override public ObservingDevicePresenceRequest createFromParcel(Parcel source) {
            int associationId = source.readInt();
            String uuid = source.readString();
            Builder b = new Builder();
            if (associationId != DevicePresenceEvent.NO_ASSOCIATION) {
                b.setAssociationId(associationId);
            }
            if (uuid != null) {
                b.setUuid(ParcelUuid.fromString(uuid));
            }
            return b.build();
        }

        @Override public ObservingDevicePresenceRequest[] newArray(int size) {
            return new ObservingDevicePresenceRequest[size];
        }
    };

    public static final class Builder {
        private int mAssociationId = DevicePresenceEvent.NO_ASSOCIATION;
        private ParcelUuid mUuid;

        public Builder() {}

        public Builder setAssociationId(int associationId) {
            mAssociationId = associationId;
            return this;
        }

        public Builder setUuid(ParcelUuid uuid) {
            java.util.Objects.requireNonNull(uuid);
            mUuid = uuid;
            return this;
        }

        /**
         * AOSP requires EXACTLY one of the two: a request naming both, or
         * neither, identifies nothing observable.
         */
        public ObservingDevicePresenceRequest build() {
            if (mUuid != null && mAssociationId != DevicePresenceEvent.NO_ASSOCIATION) {
                throw new IllegalStateException("Cannot observe device presence based on "
                        + "both the UUID and the association ID.");
            }
            if (mUuid == null && mAssociationId == DevicePresenceEvent.NO_ASSOCIATION) {
                throw new IllegalStateException("Either the UUID or the association ID "
                        + "must be set.");
            }
            return new ObservingDevicePresenceRequest(mAssociationId, mUuid);
        }
    }
}
