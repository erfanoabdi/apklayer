// android.telecom.RemoteConference -- a Conference owned by ANOTHER app's
// ConnectionService, handed to a connection manager through Telecom.
//
// Same standing as RemoteConnection (read that file's header for the full
// reasoning): only Telecom creates one, only a registered connection manager
// receives one, and the two factories that would produce it --
// ConnectionService.createRemoteOutgoingConference() and its incoming twin --
// return null on a device with no Telecom service, which is what AOSP does
// too (both are @Nullable in frameworks/base/core/api/current.txt). AOSP gives
// the class no public constructor either.
//
// The type is here because ConnectionService.onRemoteConferenceAdded() names
// it: an app that overrides that method cannot LINK without it.
//
// Mirrors telecomm/java/android/telecom/RemoteConference.java; public member
// list confirmed against frameworks/base/core/api/current.txt.
package android.telecom;

import android.os.Bundle;
import android.os.Handler;

import java.util.Collections;
import java.util.List;

public final class RemoteConference {

    /**
     * Callbacks Telecom fires as the remote conference changes. Never invoked
     * here; present to be overridden, which is why the type must link.
     */
    public abstract static class Callback {
        public Callback() {}

        public void onStateChanged(RemoteConference conference, int oldState, int newState) {}

        public void onDisconnected(RemoteConference conference,
                DisconnectCause disconnectCause) {}

        public void onConnectionAdded(RemoteConference conference,
                RemoteConnection connection) {}

        public void onConnectionRemoved(RemoteConference conference,
                RemoteConnection connection) {}

        public void onConnectionCapabilitiesChanged(RemoteConference conference,
                int connectionCapabilities) {}

        public void onConnectionPropertiesChanged(RemoteConference conference,
                int connectionProperties) {}

        public void onConferenceableConnectionsChanged(RemoteConference conference,
                List<RemoteConnection> conferenceableConnections) {}

        public void onDestroyed(RemoteConference conference) {}

        public void onExtrasChanged(RemoteConference conference, Bundle extras) {}
    }

    /** No public constructor on AOSP either: only Telecom creates these. */
    RemoteConference() {}

    public List<RemoteConnection> getConnections() {
        return Collections.emptyList();
    }

    public int getState() {
        return Connection.STATE_NEW;
    }

    public int getConnectionCapabilities() {
        return 0;
    }

    public int getConnectionProperties() {
        return 0;
    }

    public Bundle getExtras() {
        return null;
    }

    public DisconnectCause getDisconnectCause() {
        return null;
    }

    public List<RemoteConnection> getConferenceableConnections() {
        return Collections.emptyList();
    }

    public void registerCallback(Callback callback) {}

    public void registerCallback(Callback callback, Handler handler) {}

    public void unregisterCallback(Callback callback) {}

    // Commands to the remote end. There is no remote end, and AOSP likewise
    // no-ops each of these when its own IConnectionService binder is null.

    public void disconnect() {}

    public void separate(RemoteConnection connection) {}

    public void merge() {}

    public void swap() {}

    public void hold() {}

    public void unhold() {}

    public void playDtmfTone(char digit) {}

    public void stopDtmfTone() {}

    public void setCallAudioState(CallAudioState state) {}
}
