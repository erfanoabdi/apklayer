// android.hardware.usb.UsbEndpoint
//
// A USB endpoint descriptor value type. Mac.apk never enumerates a device
// (getDeviceList is always empty) so instances are not handed out, but the
// type + getters must exist for device-inspection code to compile. The
// accessor math (direction/number/type from the address+attributes bytes)
// mirrors AOSP exactly so callers that DO construct one from a descriptor
// blob get faithful results.
//
// MUST implement Parcelable: it is AOSP's UsbInterface.getEndpoint() element
// type, and UsbInterface itself is parceled (as the array UsbConfiguration
// holds) whenever a caller round-trips a UsbConfiguration/UsbDevice tree
// through an Intent extra. Same descriptor-fidelity reasoning as UsbAccessory
// and UsbDevice in this package.
// Mirrors frameworks/base/core/java/android/hardware/usb/UsbEndpoint.java.
package android.hardware.usb;

// AOSP-conformance (framework_lint vs android-30 stub jar): AOSP does not
// mark this final. Final-when-AOSP-is-not is the dangerous direction -- an
// app compiled against real Android that subclasses this fails to resolve
// against our jar. See docs/aosp-conformance-audit_2026-08-23.md.
public class UsbEndpoint implements android.os.Parcelable {
    private final int mAddress;
    private final int mAttributes;
    private final int mMaxPacketSize;
    private final int mInterval;

    public UsbEndpoint(int address, int attributes, int maxPacketSize, int interval) {
        mAddress = address;
        mAttributes = attributes;
        mMaxPacketSize = maxPacketSize;
        mInterval = interval;
    }

    public int getAddress() { return mAddress; }
    public int getEndpointNumber() { return mAddress & UsbConstants.USB_ENDPOINT_NUMBER_MASK; }
    public int getDirection() { return mAddress & UsbConstants.USB_ENDPOINT_DIR_MASK; }
    public int getAttributes() { return mAttributes; }
    public int getType() { return mAttributes & UsbConstants.USB_ENDPOINT_XFERTYPE_MASK; }
    public int getMaxPacketSize() { return mMaxPacketSize; }
    public int getInterval() { return mInterval; }

    @Override public String toString() {
        return "UsbEndpoint[mAddress=" + mAddress + ",mAttributes=" + mAttributes
                + ",mMaxPacketSize=" + mMaxPacketSize + ",mInterval=" + mInterval + "]";
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel parcel, int flags) {
        parcel.writeInt(mAddress);
        parcel.writeInt(mAttributes);
        parcel.writeInt(mMaxPacketSize);
        parcel.writeInt(mInterval);
    }

    public static final Creator<UsbEndpoint> CREATOR = new Creator<UsbEndpoint>() {
        @Override public UsbEndpoint createFromParcel(android.os.Parcel in) {
            return new UsbEndpoint(in.readInt(), in.readInt(), in.readInt(), in.readInt());
        }
        @Override public UsbEndpoint[] newArray(int size) { return new UsbEndpoint[size]; }
    };
}
