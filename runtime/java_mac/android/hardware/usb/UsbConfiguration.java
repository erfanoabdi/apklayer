// android.hardware.usb.UsbConfiguration
//
// A USB device's configuration descriptor (one or more UsbInterfaces). Mac.apk
// never exposes host USB to the guest (UsbDevice.getConfigurationCount() is
// always 0), so instances are not handed out in practice -- but the TYPE must
// exist with the exact AOSP return type: UsbDevice.getConfiguration(int) was
// found (debug/framework_lint.py, --max 1000, "return type differs on a
// matching signature") returning a bare Object here instead of
// UsbConfiguration. JVM invokevirtual resolution matches the WHOLE descriptor
// including the return type, so any call site compiled against the real SDK
// (getConfiguration(I)Landroid/hardware/usb/UsbConfiguration;) is a
// NoSuchMethodError against our old Object-returning stub, the same descriptor-
// lie class the sibling UsbAccessory/UsbDevice Parcelable fixes in this
// package already document. Faithful to
// frameworks/base/core/java/android/hardware/usb/UsbConfiguration.java
// (android13-release, fetched verbatim): field list, constructor, every
// getter, and the Parcelable shape below are copied from that source.
package android.hardware.usb;

public class UsbConfiguration implements android.os.Parcelable {
    // AOSP private bmAttributes bit constants (UsbConfiguration.java): D6 self
    // powered, D5 remote wakeup (D7 is the USB spec's reserved-set-to-1 bit and
    // carries no boolean meaning of its own).
    private static final int ATTR_SELF_POWERED = 1 << 6;
    private static final int ATTR_REMOTE_WAKEUP = 1 << 5;

    private final int mId;
    private final String mName;
    private final int mAttributes;
    private final int mMaxPower;
    private UsbInterface[] mInterfaces;

    public UsbConfiguration(int id, String name, int attributes, int maxPower) {
        mId = id;
        mName = name;
        mAttributes = attributes;
        mMaxPower = maxPower;
        mInterfaces = new UsbInterface[0];
    }

    public int getId() { return mId; }
    public String getName() { return mName; }
    public boolean isSelfPowered() { return (mAttributes & ATTR_SELF_POWERED) != 0; }
    public boolean isRemoteWakeup() { return (mAttributes & ATTR_REMOTE_WAKEUP) != 0; }
    /** AOSP stores half the actual mA value (max is 510mA in a single byte) and
     *  doubles it back on read. */
    public int getMaxPower() { return mMaxPower * 2; }

    public int getInterfaceCount() { return mInterfaces.length; }
    public UsbInterface getInterface(int index) { return mInterfaces[index]; }

    /** Package-private, mirroring AOSP: only UsbDevice (when it eventually
     *  parses a real config) or our own Parcelable CREATOR populate this. */
    void setInterfaces(UsbInterface[] interfaces) {
        mInterfaces = interfaces != null ? interfaces : new UsbInterface[0];
    }

    @Override public String toString() {
        return "UsbConfiguration[mId=" + mId + ",mName=" + mName
                + ",mAttributes=" + mAttributes + ",mMaxPower=" + mMaxPower + "]";
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel parcel, int flags) {
        parcel.writeInt(mId);
        parcel.writeString(mName);
        parcel.writeInt(mAttributes);
        parcel.writeInt(mMaxPower);
        parcel.writeParcelableArray(mInterfaces, 0);
    }

    public static final Creator<UsbConfiguration> CREATOR = new Creator<UsbConfiguration>() {
        @Override public UsbConfiguration createFromParcel(android.os.Parcel in) {
            int id = in.readInt();
            String name = in.readString();
            int attributes = in.readInt();
            int maxPower = in.readInt();
            UsbConfiguration configuration = new UsbConfiguration(id, name, attributes, maxPower);
            UsbInterface[] interfaces = in.readParcelableArray(UsbInterface.class.getClassLoader());
            configuration.setInterfaces(interfaces);
            return configuration;
        }
        @Override public UsbConfiguration[] newArray(int size) { return new UsbConfiguration[size]; }
    };
}
