// android.os.PowerMonitor (API 35) -- the identity of one on-device energy
// meter (an ODPM rail or a modeled consumer), as enumerated by
// SystemHealthManager.getSupportedPowerMonitors().
//
// AOSP ref: frameworks/base/core/java/android/os/PowerMonitor.java
// (android16-release). Ported verbatim; `index` is public upstream too (it is
// the ordering key PowerMonitorReadings binary-searches on).
package android.os;

public final class PowerMonitor implements Parcelable {
    public static final int POWER_MONITOR_TYPE_CONSUMER = 0;
    public static final int POWER_MONITOR_TYPE_MEASUREMENT = 1;

    public final int index;
    private final int mType;
    private final String mName;

    public PowerMonitor(int index, int type, String name) {
        this.index = index;
        this.mType = type;
        this.mName = name;
    }

    public int getType() { return mType; }

    public String getName() { return mName; }

    private PowerMonitor(Parcel in) {
        index = in.readInt();
        mType = in.readInt();
        mName = in.readString();
    }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(index);
        dest.writeInt(mType);
        dest.writeString(mName);
    }

    @Override public int describeContents() { return 0; }

    public static final Creator<PowerMonitor> CREATOR = new Creator<PowerMonitor>() {
        @Override public PowerMonitor createFromParcel(Parcel in) { return new PowerMonitor(in); }
        @Override public PowerMonitor[] newArray(int size) { return new PowerMonitor[size]; }
    };
}
