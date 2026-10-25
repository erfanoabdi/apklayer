package android.media.metrics;

/** A network-type transition during playback.
 *  Source: frameworks/base/media/java/android/media/metrics/NetworkEvent.java */
public final class NetworkEvent extends Event implements android.os.Parcelable {

    public static final int NETWORK_TYPE_UNKNOWN = 0;
    public static final int NETWORK_TYPE_OTHER = 1;
    public static final int NETWORK_TYPE_WIFI = 2;
    public static final int NETWORK_TYPE_ETHERNET = 3;
    public static final int NETWORK_TYPE_2G = 4;
    public static final int NETWORK_TYPE_3G = 5;
    public static final int NETWORK_TYPE_4G = 6;
    public static final int NETWORK_TYPE_5G_NSA = 7;
    public static final int NETWORK_TYPE_5G_SA = 8;
    public static final int NETWORK_TYPE_OFFLINE = 9;

    private final int mNetworkType;

    private NetworkEvent(Builder b) {
        mNetworkType = b.mNetworkType;
        mTimeSinceCreatedMillis = b.mTimeSinceCreatedMillis;
    }

    public int getNetworkType() { return mNetworkType; }

    // AOSP's metrics events are Parcelable because they cross a binder to the
    // system metrics collector. Nothing here crosses a process boundary (see
    // MediaMetricsManager), so the wire shape only has to round-trip with our
    // own CREATOR -- our field order, not AOSP's, per the
    // absent-fields-not-padded rule.
    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeInt(mNetworkType);
        dest.writeLong(mTimeSinceCreatedMillis);
    }

    @Override public String toString() {
        return "NetworkEvent{ networkType=" + mNetworkType + " }";
    }

    public static final android.os.Parcelable.Creator<NetworkEvent> CREATOR =
            new android.os.Parcelable.Creator<NetworkEvent>() {
        @Override public NetworkEvent createFromParcel(android.os.Parcel in) {
            return new Builder()
                    .setNetworkType(in.readInt())
                    .setTimeSinceCreatedMillis(in.readLong())
                    .build();
        }
        @Override public NetworkEvent[] newArray(int size) { return new NetworkEvent[size]; }
    };

    public static final class Builder {
        private int mNetworkType = NETWORK_TYPE_UNKNOWN;
        private long mTimeSinceCreatedMillis = -1;

        public Builder() { }

        public Builder setNetworkType(int v) { mNetworkType = v; return this; }
        public Builder setTimeSinceCreatedMillis(long v) { mTimeSinceCreatedMillis = v; return this; }
        public Builder setMetricsBundle(android.os.Bundle b) { return this; }

        public NetworkEvent build() { return new NetworkEvent(this); }
    }
}
