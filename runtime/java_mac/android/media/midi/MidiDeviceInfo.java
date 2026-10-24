package android.media.midi;
/** Describes a MIDI device. Mac.apk exposes no MIDI devices, so one is never
 *  produced; the type resolves. API 23. */
public final class MidiDeviceInfo implements android.os.Parcelable {
    public static final int TYPE_USB = 1, TYPE_VIRTUAL = 2, TYPE_BLUETOOTH = 3;

    // Universal MIDI Packet protocol constants (API 33). Pure identity values.
    public static final int PROTOCOL_UNKNOWN = -1;
    public static final int PROTOCOL_UMP_USE_MIDI_CI = 0;
    public static final int PROTOCOL_UMP_MIDI_1_0_UP_TO_64_BITS = 1;
    public static final int PROTOCOL_UMP_MIDI_1_0_UP_TO_64_BITS_AND_JRTS = 2;
    public static final int PROTOCOL_UMP_MIDI_1_0_UP_TO_128_BITS = 3;
    public static final int PROTOCOL_UMP_MIDI_1_0_UP_TO_128_BITS_AND_JRTS = 4;
    public static final int PROTOCOL_UMP_MIDI_2_0 = 17;
    public static final int PROTOCOL_UMP_MIDI_2_0_AND_JRTS = 18;
    private final android.os.Bundle mProperties;
    MidiDeviceInfo() { mProperties = new android.os.Bundle(); }
    public int getType() { return TYPE_VIRTUAL; }
    public int getId() { return -1; }
    public int getInputPortCount() { return 0; }
    public int getOutputPortCount() { return 0; }
    public android.os.Bundle getProperties() { return mProperties; }
    public boolean isPrivate() { return false; }

    public static final class PortInfo {
        public static final int TYPE_INPUT = 1;
        public static final int TYPE_OUTPUT = 2;
        private final int type, portNumber;
        private final String name;
        PortInfo(int type, int portNumber, String name) { this.type = type; this.portNumber = portNumber; this.name = name; }
        public int getType() { return type; }
        public int getPortNumber() { return portNumber; }
        public String getName() { return name != null ? name : ""; }
    }

    public PortInfo[] getPorts() { return new PortInfo[0]; }

    /** API 33. AOSP: the UMP protocol a Universal-MIDI-Packet device speaks;
     *  PROTOCOL_UNKNOWN for a legacy byte-stream device. Nothing here is a UMP
     *  device (there are no devices at all), so UNKNOWN is correct. */
    public int getDefaultProtocol() { return PROTOCOL_UNKNOWN; }

    // AOSP's MidiDeviceInfo parcels a type, id, port counts, port names, a
    // properties Bundle and a private flag. This class stores only the
    // properties Bundle -- every other getter is a fixed constant, because no
    // device is ever described -- so there is no per-instance state a write/read
    // pair could lose beyond that Bundle.
    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeBundle(mProperties);
    }

    public static final android.os.Parcelable.Creator<MidiDeviceInfo> CREATOR =
            new android.os.Parcelable.Creator<MidiDeviceInfo>() {
        @Override public MidiDeviceInfo createFromParcel(android.os.Parcel in) {
            MidiDeviceInfo info = new MidiDeviceInfo();
            android.os.Bundle b = in.readBundle();
            if (b != null) info.mProperties.putAll(b);
            return info;
        }
        @Override public MidiDeviceInfo[] newArray(int size) { return new MidiDeviceInfo[size]; }
    };
}


