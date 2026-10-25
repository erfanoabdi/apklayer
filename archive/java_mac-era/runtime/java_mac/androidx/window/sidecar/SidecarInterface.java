package androidx.window.sidecar;
import android.os.IBinder;
/** The sidecar backend. Mac.apk is flat: one normal device state, empty window
 *  layout, listener adds/removes are no-ops. */
public interface SidecarInterface {
    interface SidecarCallback {
        void onDeviceStateChanged(SidecarDeviceState newDeviceState);
        void onWindowLayoutChanged(IBinder windowToken, SidecarWindowLayoutInfo newLayout);
    }
    void setSidecarCallback(SidecarCallback callback);
    SidecarWindowLayoutInfo getWindowLayoutInfo(IBinder windowToken);
    void onWindowLayoutChangeListenerAdded(IBinder windowToken);
    void onWindowLayoutChangeListenerRemoved(IBinder windowToken);
    SidecarDeviceState getDeviceState();
    void onDeviceStateListenersChanged(boolean isEmpty);
}
