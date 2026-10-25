// android.app.admin.NetworkEvent -- "An abstract class that represents a network
// event", the base of the DNS/connect events a device owner retrieves with
// DevicePolicyManager.retrieveNetworkLogs. API 26.
//
// AOSP ref: frameworks/base/core/java/android/app/admin/NetworkEvent.java
// (android16-release). ABSTRACT in AOSP with two @SystemApi-adjacent concrete
// subclasses (DnsEvent, ConnectEvent) that are also public; only the base is
// referenced by the public DevicePolicyManager surface, which is what this file
// provides. Network logging is never enabled here (see DevicePolicyManager), so
// no instance is ever produced by the runtime.
package android.app.admin;

import android.os.Parcel;
import android.os.Parcelable;

public abstract class NetworkEvent implements Parcelable {

    String mPackageName;
    long mTimestamp;
    long mId;

    NetworkEvent() { }

    NetworkEvent(String packageName, long timestamp) {
        mPackageName = packageName;
        mTimestamp = timestamp;
    }

    /** AOSP getPackageName(): "the package name of the UID that performed the
     *  query." */
    public String getPackageName() { return mPackageName; }

    /** AOSP getTimestamp(): "the timestamp of the event, in milliseconds since
     *  the epoch." */
    public long getTimestamp() { return mTimestamp; }

    /** AOSP getId(): "the id of the event, unique within a batch of logs." */
    public long getId() { return mId; }

    @Override public int describeContents() { return 0; }

    /** AOSP's CREATOR reads a discriminator int and delegates to the DnsEvent /
     *  ConnectEvent creators. Network logging is never enabled here, so no
     *  NetworkEvent is ever written; the parcel side exists to satisfy the
     *  Parcelable contract, and reading one back is not something this process
     *  can be handed. */
    public static final Parcelable.Creator<NetworkEvent> CREATOR =
            new Parcelable.Creator<NetworkEvent>() {
        @Override public NetworkEvent createFromParcel(Parcel in) { return null; }
        @Override public NetworkEvent[] newArray(int size) { return new NetworkEvent[size]; }
    };
}
