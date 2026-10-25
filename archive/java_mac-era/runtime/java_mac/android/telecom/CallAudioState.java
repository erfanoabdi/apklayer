// android.telecom.CallAudioState -- which audio route a call is using, which
// routes are available, and whether the mic is muted.
//
// A REAL value object -- the class itself is pure data and apps construct it in
// their own tests and callbacks. What is honestly EMPTY is where instances come
// FROM: Connection.getCallAudioState() returns null until Telecom pushes a
// state in, and Mac.apk has no Telecom service to push one. That is the same
// answer AOSP gives before a call is added to Telecom, so callers already
// handle it.
//
// getSupportedBluetoothDevices() returns an empty collection and
// getActiveBluetoothDevice() returns null: a Mac has no Bluetooth SCO
// headset routing under Telecom. Reporting a device we cannot route to would
// make an app show a Bluetooth button that does nothing.
//
// Mirrors telecomm/java/android/telecom/CallAudioState.java; constants and
// public surface confirmed against frameworks/base/core/api/current.txt.
package android.telecom;

import android.bluetooth.BluetoothDevice;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

public final class CallAudioState implements Parcelable {

    public static final int ROUTE_EARPIECE = 0x00000001;
    public static final int ROUTE_BLUETOOTH = 0x00000002;
    public static final int ROUTE_WIRED_HEADSET = 0x00000004;
    public static final int ROUTE_SPEAKER = 0x00000008;
    public static final int ROUTE_STREAMING = 0x00000010;

    /** Bit mask of ROUTE_EARPIECE and ROUTE_WIRED_HEADSET. */
    public static final int ROUTE_WIRED_OR_EARPIECE = ROUTE_EARPIECE | ROUTE_WIRED_HEADSET;

    private final boolean isMuted;
    private final int route;
    private final int supportedRouteMask;
    private final BluetoothDevice activeBluetoothDevice;
    private final Collection<BluetoothDevice> supportedBluetoothDevices;

    public CallAudioState(boolean muted, int route, int supportedRouteMask) {
        this(muted, route, supportedRouteMask, null, Collections.emptyList());
    }

    /** @hide on AOSP; kept package-visible for the copy constructor. */
    CallAudioState(boolean isMuted, int route, int supportedRouteMask,
            BluetoothDevice activeBluetoothDevice,
            Collection<BluetoothDevice> supportedBluetoothDevices) {
        this.isMuted = isMuted;
        this.route = route;
        this.supportedRouteMask = supportedRouteMask;
        this.activeBluetoothDevice = activeBluetoothDevice;
        this.supportedBluetoothDevices = supportedBluetoothDevices == null
                ? Collections.emptyList() : supportedBluetoothDevices;
    }

    public CallAudioState(CallAudioState state) {
        isMuted = state.isMuted();
        route = state.getRoute();
        supportedRouteMask = state.getSupportedRouteMask();
        activeBluetoothDevice = state.activeBluetoothDevice;
        supportedBluetoothDevices = state.getSupportedBluetoothDevices();
    }

    public boolean isMuted() {
        return isMuted;
    }

    public int getRoute() {
        return route;
    }

    public int getSupportedRouteMask() {
        return supportedRouteMask;
    }

    public Collection<BluetoothDevice> getSupportedBluetoothDevices() {
        return supportedBluetoothDevices;
    }

    public BluetoothDevice getActiveBluetoothDevice() {
        return activeBluetoothDevice;
    }

    public static String audioRouteToString(int route) {
        if (route == 0 || (route & ~(ROUTE_EARPIECE | ROUTE_BLUETOOTH | ROUTE_WIRED_HEADSET
                | ROUTE_SPEAKER | ROUTE_STREAMING)) != 0x0) {
            return "UNKNOWN";
        }

        StringBuffer buffer = new StringBuffer();
        if ((route & ROUTE_EARPIECE) == ROUTE_EARPIECE) {
            listAppend(buffer, "EARPIECE");
        }
        if ((route & ROUTE_BLUETOOTH) == ROUTE_BLUETOOTH) {
            listAppend(buffer, "BLUETOOTH");
        }
        if ((route & ROUTE_WIRED_HEADSET) == ROUTE_WIRED_HEADSET) {
            listAppend(buffer, "WIRED_HEADSET");
        }
        if ((route & ROUTE_SPEAKER) == ROUTE_SPEAKER) {
            listAppend(buffer, "SPEAKER");
        }
        if ((route & ROUTE_STREAMING) == ROUTE_STREAMING) {
            listAppend(buffer, "STREAMING");
        }

        return buffer.toString();
    }

    private static void listAppend(StringBuffer buffer, String str) {
        if (buffer.length() > 0) {
            buffer.append(", ");
        }
        buffer.append(str);
    }

    @Override public boolean equals(Object obj) {
        if (obj == null) return false;
        if (!(obj instanceof CallAudioState)) return false;
        CallAudioState state = (CallAudioState) obj;
        return Objects.equals(activeBluetoothDevice, state.activeBluetoothDevice)
                && isMuted() == state.isMuted()
                && getRoute() == state.getRoute()
                && getSupportedRouteMask() == state.getSupportedRouteMask()
                && Objects.equals(supportedBluetoothDevices, state.getSupportedBluetoothDevices());
    }

    @Override public int hashCode() {
        return Objects.hash(isMuted, route, supportedRouteMask,
                activeBluetoothDevice, supportedBluetoothDevices);
    }

    @Override public String toString() {
        return String.format("[AudioState isMuted: %b, route: %s, supportedRouteMask: %s, "
                        + "activeBluetoothDevice: [%s], supportedBluetoothDevices: [%s]]",
                isMuted, audioRouteToString(route), audioRouteToString(supportedRouteMask),
                activeBluetoothDevice, supportedBluetoothDevices);
    }

    @Override public int describeContents() {
        return 0;
    }

    @Override public void writeToParcel(Parcel destination, int flags) {
        destination.writeByte((byte) (isMuted ? 1 : 0));
        destination.writeInt(route);
        destination.writeInt(supportedRouteMask);
    }

    public static final Creator<CallAudioState> CREATOR = new Creator<CallAudioState>() {
        @Override public CallAudioState createFromParcel(Parcel source) {
            boolean isMuted = source.readByte() == 0 ? false : true;
            return new CallAudioState(isMuted, source.readInt(), source.readInt());
        }

        @Override public CallAudioState[] newArray(int size) {
            return new CallAudioState[size];
        }
    };
}
