// android.bluetooth.BluetoothClass -- wraps the Bluetooth Class-of-Device
// (CoD) 32-bit field (a remote device's declared major/minor device type
// plus service bits) and provides bit-mask accessors + the documented
// constant groups. AOSP ref (constants stable since Android 1.0, verified
// against android-10.0.0_r1 core/java/android/bluetooth/BluetoothClass.java
// -- the bit layout is the Bluetooth SIG's baseband CoD assignment, not an
// Android-version-specific detail):
//   https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-10.0.0_r1/core/java/android/bluetooth/BluetoothClass.java
//
// Only the public (non-@hide) surface is mirrored: getDeviceClass(),
// getMajorDeviceClass(), hasService(int), the Service/Device/Device.Major
// constant groups, equals/hashCode/toString, and Parcelable (a
// BluetoothDevice carries a BluetoothClass through Binder/Bundle in real
// Android; our BluetoothDevice.getBluetoothClass() currently always
// returns null pending bluetooth-usb-nfc-01, so nothing constructs one via
// CREATOR today, but a real app compiled against the public SDK expects
// the class to implement Parcelable). doesClassMatch/PROFILE_*/
// getClassOfDevice(Bytes) are @hide/@TestApi in AOSP -- excluded from the
// public SDK stub jar apps compile against, so no real app bytecode can
// reference them; skipped here as out of scope for app compatibility.
package android.bluetooth;

public final class BluetoothClass implements android.os.Parcelable {
    /** AOSP: "Legacy error value. Applications should use null instead." */
    public static final int ERROR = 0xFF000000;

    private final int mClass;

    public BluetoothClass(int classInt) {
        mClass = classInt;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof BluetoothClass) {
            return mClass == ((BluetoothClass) o).mClass;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return mClass;
    }

    @Override
    public String toString() {
        return Integer.toHexString(mClass);
    }

    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(android.os.Parcel out, int flags) {
        out.writeInt(mClass);
    }

    public static final android.os.Parcelable.Creator<BluetoothClass> CREATOR =
            new android.os.Parcelable.Creator<BluetoothClass>() {
        @Override public BluetoothClass createFromParcel(android.os.Parcel in) {
            return new BluetoothClass(in.readInt());
        }
        @Override public BluetoothClass[] newArray(int size) {
            return new BluetoothClass[size];
        }
    };

    /** Defines all service class constants. Each BluetoothClass encodes
     *  zero or more service classes. */
    public static final class Service {
        private static final int BITMASK = 0xFFE000;

        public static final int LIMITED_DISCOVERABILITY = 0x002000;
        public static final int POSITIONING              = 0x010000;
        public static final int NETWORKING                = 0x020000;
        public static final int RENDER                    = 0x040000;
        public static final int CAPTURE                   = 0x080000;
        public static final int OBJECT_TRANSFER            = 0x100000;
        public static final int AUDIO                      = 0x200000;
        public static final int TELEPHONY                  = 0x400000;
        public static final int INFORMATION                = 0x800000;
    }

    /** Returns true if the specified service class (a Service constant, or
     *  ORed combination) is supported by this BluetoothClass. */
    public boolean hasService(int service) {
        return ((mClass & Service.BITMASK & service) != 0);
    }

    /** Defines all device class constants. Each BluetoothClass encodes
     *  exactly one device class, with major and minor components. */
    public static class Device {
        private static final int BITMASK = 0x1FFC;

        /** Defines all major device class constants. */
        public static class Major {
            private static final int BITMASK = 0x1F00;

            public static final int MISC           = 0x0000;
            public static final int COMPUTER       = 0x0100;
            public static final int PHONE           = 0x0200;
            public static final int NETWORKING      = 0x0300;
            public static final int AUDIO_VIDEO     = 0x0400;
            public static final int PERIPHERAL      = 0x0500;
            public static final int IMAGING         = 0x0600;
            public static final int WEARABLE        = 0x0700;
            public static final int TOY             = 0x0800;
            public static final int HEALTH          = 0x0900;
            public static final int UNCATEGORIZED   = 0x1F00;
        }

        // Devices in the COMPUTER major class
        public static final int COMPUTER_UNCATEGORIZED     = 0x0100;
        public static final int COMPUTER_DESKTOP           = 0x0104;
        public static final int COMPUTER_SERVER             = 0x0108;
        public static final int COMPUTER_LAPTOP             = 0x010C;
        public static final int COMPUTER_HANDHELD_PC_PDA    = 0x0110;
        public static final int COMPUTER_PALM_SIZE_PC_PDA   = 0x0114;
        public static final int COMPUTER_WEARABLE           = 0x0118;

        // Devices in the PHONE major class
        public static final int PHONE_UNCATEGORIZED  = 0x0200;
        public static final int PHONE_CELLULAR        = 0x0204;
        public static final int PHONE_CORDLESS        = 0x0208;
        public static final int PHONE_SMART            = 0x020C;
        public static final int PHONE_MODEM_OR_GATEWAY = 0x0210;
        public static final int PHONE_ISDN              = 0x0214;

        // Minor classes for the AUDIO_VIDEO major class
        public static final int AUDIO_VIDEO_UNCATEGORIZED                  = 0x0400;
        public static final int AUDIO_VIDEO_WEARABLE_HEADSET               = 0x0404;
        public static final int AUDIO_VIDEO_HANDSFREE                       = 0x0408;
        public static final int AUDIO_VIDEO_MICROPHONE                     = 0x0410;
        public static final int AUDIO_VIDEO_LOUDSPEAKER                    = 0x0414;
        public static final int AUDIO_VIDEO_HEADPHONES                     = 0x0418;
        public static final int AUDIO_VIDEO_PORTABLE_AUDIO                 = 0x041C;
        public static final int AUDIO_VIDEO_CAR_AUDIO                      = 0x0420;
        public static final int AUDIO_VIDEO_SET_TOP_BOX                    = 0x0424;
        public static final int AUDIO_VIDEO_HIFI_AUDIO                     = 0x0428;
        public static final int AUDIO_VIDEO_VCR                            = 0x042C;
        public static final int AUDIO_VIDEO_VIDEO_CAMERA                   = 0x0430;
        public static final int AUDIO_VIDEO_CAMCORDER                      = 0x0434;
        public static final int AUDIO_VIDEO_VIDEO_MONITOR                  = 0x0438;
        public static final int AUDIO_VIDEO_VIDEO_DISPLAY_AND_LOUDSPEAKER  = 0x043C;
        public static final int AUDIO_VIDEO_VIDEO_CONFERENCING             = 0x0440;
        public static final int AUDIO_VIDEO_VIDEO_GAMING_TOY               = 0x0448;

        // Devices in the WEARABLE major class
        public static final int WEARABLE_UNCATEGORIZED = 0x0700;
        public static final int WEARABLE_WRIST_WATCH    = 0x0704;
        public static final int WEARABLE_PAGER           = 0x0708;
        public static final int WEARABLE_JACKET           = 0x070C;
        public static final int WEARABLE_HELMET            = 0x0710;
        public static final int WEARABLE_GLASSES           = 0x0714;

        // Devices in the TOY major class
        public static final int TOY_UNCATEGORIZED       = 0x0800;
        public static final int TOY_ROBOT                = 0x0804;
        public static final int TOY_VEHICLE               = 0x0808;
        public static final int TOY_DOLL_ACTION_FIGURE    = 0x080C;
        public static final int TOY_CONTROLLER             = 0x0810;
        public static final int TOY_GAME                   = 0x0814;

        // Devices in the HEALTH major class
        public static final int HEALTH_UNCATEGORIZED    = 0x0900;
        public static final int HEALTH_BLOOD_PRESSURE    = 0x0904;
        public static final int HEALTH_THERMOMETER        = 0x0908;
        public static final int HEALTH_WEIGHING            = 0x090C;
        public static final int HEALTH_GLUCOSE              = 0x0910;
        public static final int HEALTH_PULSE_OXIMETER       = 0x0914;
        public static final int HEALTH_PULSE_RATE            = 0x0918;
        public static final int HEALTH_DATA_DISPLAY           = 0x091C;
    }

    /** Return the major device class component of this BluetoothClass. */
    public int getMajorDeviceClass() {
        return (mClass & Device.Major.BITMASK);
    }

    /** Return the (major and minor) device class component of this
     *  BluetoothClass. */
    public int getDeviceClass() {
        return (mClass & Device.BITMASK);
    }
}
