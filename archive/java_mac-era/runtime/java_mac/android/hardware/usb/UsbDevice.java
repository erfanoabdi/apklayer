// android.hardware.usb.UsbDevice
//
// A connected USB device descriptor. Mac.apk never exposes host USB to the
// guest (UsbManager.getDeviceList is always empty), so instances are not
// handed out in practice -- but apps reference UsbDevice in fields/casts and
// inspect descriptors, so the full getter surface + interface/config
// enumeration must resolve. Faithful to
// frameworks/base/core/java/android/hardware/usb/UsbDevice.java.
//
// MUST implement Parcelable: apps read the ACTION_USB_DEVICE_ATTACHED /
// USB_DEVICE_DETACHED extra via Intent.getParcelableExtra(EXTRA_DEVICE), and
// UsbManager.requestPermission's result intent carries the same extra --
// both require a Parcelable type, and the JVM's invokevirtual resolution at
// an app's call site matches the WHOLE descriptor including generic/erasure
// shape, so a non-Parcelable stand-in fails the same way the sibling
// UsbAccessory header (this package) already documents it fixed for
// UsbAccessory: a NoSuchMethodError-class failure at the call site instead
// of the graceful null/instance AOSP promises.
package android.hardware.usb;

public class UsbDevice implements android.os.Parcelable {
    private final String mName;
    private final int mVendorId;
    private final int mProductId;
    private final int mClass;
    private final int mSubclass;
    private final int mProtocol;
    private final String mManufacturerName;
    private final String mProductName;
    private final String mVersion;
    private final String mSerialNumber;
    private UsbInterface[] mInterfaces;
    private UsbConfiguration[] mConfigurations;

    public UsbDevice() {
        this("", 0, 0, 0, 0, 0, null, null, null, null);
    }

    public UsbDevice(String name, int vendorId, int productId, int klass,
            int subClass, int protocol, String manufacturerName, String productName,
            String version, String serialNumber) {
        mName = name;
        mVendorId = vendorId;
        mProductId = productId;
        mClass = klass;
        mSubclass = subClass;
        mProtocol = protocol;
        mManufacturerName = manufacturerName;
        mProductName = productName;
        mVersion = version;
        mSerialNumber = serialNumber;
        mInterfaces = new UsbInterface[0];
        mConfigurations = new UsbConfiguration[0];
    }

    public String getDeviceName() { return mName; }
    public String getManufacturerName() { return mManufacturerName; }
    public String getProductName() { return mProductName; }
    public String getVersion() { return mVersion; }
    public String getSerialNumber() { return mSerialNumber; }
    public int getVendorId() { return mVendorId; }
    public int getProductId() { return mProductId; }
    public int getDeviceClass() { return mClass; }
    public int getDeviceSubclass() { return mSubclass; }
    public int getDeviceProtocol() { return mProtocol; }

    /** AOSP getDeviceId(String) helper -- stable hash of the device path name. */
    public int getDeviceId() { return getDeviceId(mName); }
    public static int getDeviceId(String name) { return name == null ? 0 : name.hashCode(); }
    public static String getDeviceName(int id) { return Integer.toString(id); }

    // debug/framework_lint.py (--max 1000, "return type differs on a matching
    // signature") flagged getConfiguration(I) returning a bare Object here:
    // AOSP's real descriptor is
    // getConfiguration(I)Landroid/hardware/usb/UsbConfiguration;, and JVM
    // invokevirtual resolution matches the WHOLE descriptor including the
    // return type, so any call site compiled against the real SDK was a
    // NoSuchMethodError against this stub. getConfigurationCount() being
    // always 0 (no host USB is ever exposed) means the AOSP enumeration idiom
    // (`for i in 0..getConfigurationCount()`) never actually reaches
    // getConfiguration(), but the descriptor itself still has to be right for
    // any caller that doesn't follow that idiom.
    public int getConfigurationCount() { return mConfigurations.length; }
    public UsbConfiguration getConfiguration(int index) { return mConfigurations[index]; }

    public void setConfigurations(UsbConfiguration[] configurations) {
        mConfigurations = configurations != null ? configurations : new UsbConfiguration[0];
    }

    public int getInterfaceCount() { return mInterfaces.length; }
    public UsbInterface getInterface(int index) { return mInterfaces[index]; }

    public void setInterfaces(UsbInterface[] interfaces) {
        mInterfaces = interfaces != null ? interfaces : new UsbInterface[0];
    }

    @Override public String toString() {
        return "UsbDevice[mName=" + mName + ",mVendorId=" + mVendorId
                + ",mProductId=" + mProductId + "]";
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeString(mName);
        dest.writeInt(mVendorId);
        dest.writeInt(mProductId);
        dest.writeInt(mClass);
        dest.writeInt(mSubclass);
        dest.writeInt(mProtocol);
        dest.writeString(mManufacturerName);
        dest.writeString(mProductName);
        dest.writeString(mVersion);
        dest.writeString(mSerialNumber);
    }

    public static final Creator<UsbDevice> CREATOR = new Creator<UsbDevice>() {
        @Override public UsbDevice createFromParcel(android.os.Parcel in) {
            return new UsbDevice(in.readString(), in.readInt(), in.readInt(), in.readInt(),
                    in.readInt(), in.readInt(), in.readString(), in.readString(),
                    in.readString(), in.readString());
        }
        @Override public UsbDevice[] newArray(int size) { return new UsbDevice[size]; }
    };
}
