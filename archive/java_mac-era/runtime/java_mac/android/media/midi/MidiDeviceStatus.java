package android.media.midi;
/** The open/closed state of one MIDI device's ports. Mac.apk exposes no MIDI
 *  devices, so an instance is never produced and every port reads as closed.
 *  Source: frameworks/base/media/java/android/media/midi/MidiDeviceStatus.java */
public final class MidiDeviceStatus implements android.os.Parcelable {
    MidiDeviceStatus() {}
    public MidiDeviceInfo getDeviceInfo() { return null; }
    public boolean isInputPortOpen(int portNumber) { return false; }
    public int getOutputPortOpenCount(int portNumber) { return 0; }

    // This class stores NO per-instance state (every getter above is a fixed
    // constant, because no device is ever described), so an honest round trip
    // writes nothing -- the same shape RoutingSessionInfo used before it gained
    // real fields.
    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) { }

    public static final android.os.Parcelable.Creator<MidiDeviceStatus> CREATOR =
            new android.os.Parcelable.Creator<MidiDeviceStatus>() {
        @Override public MidiDeviceStatus createFromParcel(android.os.Parcel in) {
            return new MidiDeviceStatus();
        }
        @Override public MidiDeviceStatus[] newArray(int size) {
            return new MidiDeviceStatus[size];
        }
    };
}
