// android.hardware.usb.UsbInterface
//
// A USB interface descriptor value type. Like UsbEndpoint, Mac.apk never
// hands one out (no devices), but the type + getters must resolve for
// device-inspection code to compile. Endpoint enumeration faithfully walks
// the (possibly empty) endpoint array a caller supplies.
//
// MUST implement Parcelable: it is AOSP's UsbConfiguration.getInterface()
// element type, and UsbConfiguration.writeToParcel parcels the whole
// interface array. Same descriptor-fidelity reasoning as UsbAccessory /
// UsbDevice / UsbEndpoint in this package.
// Mirrors frameworks/base/core/java/android/hardware/usb/UsbInterface.java.
package android.hardware.usb;

// AOSP-conformance (framework_lint vs android-30 stub jar): AOSP does not
// mark this final. Final-when-AOSP-is-not is the dangerous direction -- an
// app compiled against real Android that subclasses this fails to resolve
// against our jar. See docs/aosp-conformance-audit_2026-08-23.md.
public class UsbInterface implements android.os.Parcelable {
    private final int mId;
    private final int mAlternateSetting;
    private final String mName;
    private final int mClass;
    private final int mSubclass;
    private final int mProtocol;
    private UsbEndpoint[] mEndpoints;

    public UsbInterface(int id, int alternateSetting, String name,
            int klass, int subClass, int protocol) {
        mId = id;
        mAlternateSetting = alternateSetting;
        mName = name;
        mClass = klass;
        mSubclass = subClass;
        mProtocol = protocol;
        mEndpoints = new UsbEndpoint[0];
    }

    public int getId() { return mId; }
    public int getAlternateSetting() { return mAlternateSetting; }
    public String getName() { return mName; }
    public int getInterfaceClass() { return mClass; }
    public int getInterfaceSubclass() { return mSubclass; }
    public int getInterfaceProtocol() { return mProtocol; }

    public int getEndpointCount() { return mEndpoints.length; }
    public UsbEndpoint getEndpoint(int index) { return mEndpoints[index]; }

    /** Internal setter used when a caller builds an interface from a descriptor. */
    public void setEndpoints(UsbEndpoint[] endpoints) {
        mEndpoints = endpoints != null ? endpoints : new UsbEndpoint[0];
    }

    @Override public String toString() {
        return "UsbInterface[mId=" + mId + ",mAlternateSetting=" + mAlternateSetting
                + ",mName=" + mName + ",mClass=" + mClass + ",mSubclass=" + mSubclass
                + ",mProtocol=" + mProtocol + ",mEndpoints=" + mEndpoints.length + "]";
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel parcel, int flags) {
        parcel.writeInt(mId);
        parcel.writeInt(mAlternateSetting);
        parcel.writeString(mName);
        parcel.writeInt(mClass);
        parcel.writeInt(mSubclass);
        parcel.writeInt(mProtocol);
        parcel.writeParcelableArray(mEndpoints, 0);
    }

    public static final Creator<UsbInterface> CREATOR = new Creator<UsbInterface>() {
        @Override public UsbInterface createFromParcel(android.os.Parcel in) {
            int id = in.readInt();
            int alternateSetting = in.readInt();
            String name = in.readString();
            int klass = in.readInt();
            int subClass = in.readInt();
            int protocol = in.readInt();
            UsbInterface iface = new UsbInterface(id, alternateSetting, name, klass, subClass, protocol);
            UsbEndpoint[] endpoints = in.readParcelableArray(UsbEndpoint.class.getClassLoader());
            iface.setEndpoints(endpoints);
            return iface;
        }
        @Override public UsbInterface[] newArray(int size) { return new UsbInterface[size]; }
    };
}
