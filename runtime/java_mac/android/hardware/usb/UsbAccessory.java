// android.hardware.usb.UsbAccessory
//
// Describes a USB accessory (USB accessory mode: the Android device is the USB
// device and the accessory is the host). Mac.apk exposes no host USB to the
// guest, so UsbManager.getAccessoryList() has no accessory to report -- but the
// TYPE must exist and be Parcelable, because an app compiled against the SDK
// emits call sites whose FULL descriptors name it:
//   getAccessoryList()[Landroid/hardware/usb/UsbAccessory;
//   openAccessory(Landroid/hardware/usb/UsbAccessory;)Landroid/os/ParcelFileDescriptor;
// and reads the ACTION_USB_ACCESSORY_ATTACHED extra through
// Intent.getParcelableExtra(EXTRA_ACCESSORY), which requires a Parcelable type.
// JVM invokevirtual resolution matches the whole descriptor INCLUDING the return
// type, so a stand-in Object/Object[] shape is a NoSuchMethodError at the call
// site rather than the graceful null AOSP documents. Mirrors
// frameworks/base/core/java/android/hardware/usb/UsbAccessory.java.
//
// Deliberate deviation: AOSP keeps the serial behind an IUsbSerialReader binder
// (so reading it is permission-gated) and parcels that binder. Mac.apk has no
// binder IPC -- everything is one process -- so the serial is held and parceled
// as the String that the app-visible getSerial() contract returns. Every
// observable behavior (the getters, equals/hashCode/toString, a Parcel round
// trip) matches AOSP.
package android.hardware.usb;

import android.os.Parcel;
import android.os.Parcelable;

public class UsbAccessory implements Parcelable {
    private final String mManufacturer;
    private final String mModel;
    private final String mDescription;
    private final String mVersion;
    private final String mUri;
    private final String mSerialNumber;

    /** AOSP's public (deprecated) constructor: the six descriptor strings. */
    public UsbAccessory(String manufacturer, String model, String description,
            String version, String uri, String serialNumber) {
        mManufacturer = manufacturer;
        mModel = model;
        mDescription = description;
        mVersion = version;
        mUri = uri;
        mSerialNumber = serialNumber;
    }

    public String getManufacturer() { return mManufacturer; }
    public String getModel() { return mModel; }
    public String getDescription() { return mDescription; }
    public String getVersion() { return mVersion; }
    public String getUri() { return mUri; }
    public String getSerial() { return mSerialNumber; }

    /** AOSP's private null-tolerant string compare used by equals(). */
    private static boolean compare(String s1, String s2) {
        if (s1 == null) return (s2 == null);
        return s1.equals(s2);
    }

    @Override public boolean equals(Object obj) {
        if (obj instanceof UsbAccessory) {
            UsbAccessory accessory = (UsbAccessory) obj;
            return (compare(mManufacturer, accessory.getManufacturer()) &&
                    compare(mModel, accessory.getModel()) &&
                    compare(mDescription, accessory.getDescription()) &&
                    compare(mVersion, accessory.getVersion()) &&
                    compare(mUri, accessory.getUri()) &&
                    compare(getSerial(), accessory.getSerial()));
        }
        return false;
    }

    @Override public int hashCode() {
        // AOSP xors the five descriptor hashes. Manufacturer/model are @NonNull
        // there because the platform always populates them; a guest can hand us
        // nulls through the public constructor, so null-guard instead of NPEing.
        return (mManufacturer == null ? 0 : mManufacturer.hashCode()) ^
               (mModel == null ? 0 : mModel.hashCode()) ^
               (mDescription == null ? 0 : mDescription.hashCode()) ^
               (mVersion == null ? 0 : mVersion.hashCode()) ^
               (mUri == null ? 0 : mUri.hashCode());
    }

    @Override public String toString() {
        return "UsbAccessory[mManufacturer=" + mManufacturer +
                          ", mModel=" + mModel +
                          ", mDescription=" + mDescription +
                          ", mVersion=" + mVersion +
                          ", mUri=" + mUri +
                          ", mSerialNumber=" + mSerialNumber + "]";
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeString(mManufacturer);
        parcel.writeString(mModel);
        parcel.writeString(mDescription);
        parcel.writeString(mVersion);
        parcel.writeString(mUri);
        // AOSP writes mSerialNumberReader.asBinder() here; see the header note.
        parcel.writeString(mSerialNumber);
    }

    public static final Parcelable.Creator<UsbAccessory> CREATOR =
        new Parcelable.Creator<UsbAccessory>() {
            @Override public UsbAccessory createFromParcel(Parcel in) {
                String manufacturer = in.readString();
                String model = in.readString();
                String description = in.readString();
                String version = in.readString();
                String uri = in.readString();
                String serialNumber = in.readString();
                return new UsbAccessory(manufacturer, model, description, version,
                                        uri, serialNumber);
            }
            @Override public UsbAccessory[] newArray(int size) {
                return new UsbAccessory[size];
            }
        };
}
