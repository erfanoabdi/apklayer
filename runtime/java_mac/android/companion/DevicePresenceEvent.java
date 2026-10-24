// android.companion.DevicePresenceEvent -- the event a CompanionDeviceService
// receives when an observed companion device appears or disappears.
//
// A REAL, complete value type (three fields, three accessors, Parcelable) with
// no device dependency, so an app that constructs one -- and AOSP gives it a
// public constructor, so apps can -- gets correct behaviour. What is absent is
// anything to FIRE it: nothing here observes device presence, because there is
// no companion transport (see CompanionDeviceManager).
//
// Mirrors
// frameworks/base/core/java/android/companion/DevicePresenceEvent.java;
// public surface and constants confirmed against
// frameworks/base/core/api/current.txt.
package android.companion;

import android.os.Parcel;
import android.os.ParcelUuid;
import android.os.Parcelable;

public final class DevicePresenceEvent implements Parcelable {

    public static final int EVENT_BLE_APPEARED = 0;
    public static final int EVENT_BLE_DISAPPEARED = 1;
    public static final int EVENT_BT_CONNECTED = 2;
    public static final int EVENT_BT_DISCONNECTED = 3;
    public static final int EVENT_SELF_MANAGED_APPEARED = 4;
    public static final int EVENT_SELF_MANAGED_DISAPPEARED = 5;

    /** AOSP's sentinel for an event reported by UUID rather than association. */
    public static final int NO_ASSOCIATION = -1;

    private final int mAssociationId;
    private final int mEvent;
    private final ParcelUuid mUuid;

    public DevicePresenceEvent(int associationId, int event, ParcelUuid uuid) {
        mAssociationId = associationId;
        mEvent = event;
        mUuid = uuid;
    }

    public int getAssociationId() { return mAssociationId; }

    public int getEvent() { return mEvent; }

    public ParcelUuid getUuid() { return mUuid; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mAssociationId);
        dest.writeInt(mEvent);
        dest.writeString(mUuid == null ? null : mUuid.toString());
    }

    @Override public String toString() {
        return "DevicePresenceEvent{associationId=" + mAssociationId
                + ", event=" + mEvent + ", uuid=" + mUuid + "}";
    }

    public static final Creator<DevicePresenceEvent> CREATOR = new Creator<DevicePresenceEvent>() {
        @Override public DevicePresenceEvent createFromParcel(Parcel source) {
            int associationId = source.readInt();
            int event = source.readInt();
            String uuid = source.readString();
            return new DevicePresenceEvent(associationId, event,
                    uuid == null ? null : ParcelUuid.fromString(uuid));
        }

        @Override public DevicePresenceEvent[] newArray(int size) {
            return new DevicePresenceEvent[size];
        }
    };
}
