// android.telecom.RemoteConnection -- a Connection owned by ANOTHER app's
// ConnectionService, handed to a connection manager through Telecom.
//
// WHY THIS TYPE EXISTS HERE BUT IS NEVER HANDED OUT. A RemoteConnection is
// created only by Telecom, on behalf of a registered CONNECTION MANAGER
// (PhoneAccount.CAPABILITY_CONNECTION_MANAGER), and reached only via
// ConnectionService.createRemoteOutgoingConnection() / the incoming twin.
// There is no Telecom service on a Mac, so those factories return null -- the
// answer AOSP itself gives when no remote connection service is available, and
// the reason both are annotated @Nullable in
// frameworks/base/core/api/current.txt. The class has NO public constructor on
// AOSP either, so an app cannot fabricate one.
//
// It is here because ConnectionService.conferenceRemoteConnections(),
// onRemoteExistingConnectionAdded() and RemoteConference name it in their
// signatures: an app whose ConnectionService subclass overrides those methods
// cannot LINK without the type. That is the whole job of this file.
//
// The accessors below therefore describe an object that cannot exist on this
// device; they return the same neutral values AOSP returns for a connection
// whose remote state has not been pushed yet, so no caller could be misled by
// a fabricated address, caller name or capability bit.
//
// Mirrors telecomm/java/android/telecom/RemoteConnection.java; public member
// list confirmed against frameworks/base/core/api/current.txt.
package android.telecom;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.Surface;

import java.util.Collections;
import java.util.List;

public final class RemoteConnection {

    /**
     * Callbacks Telecom fires as the remote connection changes. Never invoked
     * here (no Telecom service, and no RemoteConnection is ever handed out);
     * present to be overridden, which is why the type must link.
     */
    public abstract static class Callback {
        public Callback() {}

        public void onStateChanged(RemoteConnection connection, int state) {}

        public void onDisconnected(RemoteConnection connection,
                DisconnectCause disconnectCause) {}

        public void onRingbackRequested(RemoteConnection connection, boolean ringback) {}

        public void onConnectionCapabilitiesChanged(RemoteConnection connection,
                int connectionCapabilities) {}

        public void onConnectionPropertiesChanged(RemoteConnection connection,
                int connectionProperties) {}

        public void onPostDialWait(RemoteConnection connection, String remainingDigits) {}

        public void onPostDialChar(RemoteConnection connection, char nextChar) {}

        public void onVoipAudioChanged(RemoteConnection connection, boolean isVoip) {}

        public void onStatusHintsChanged(RemoteConnection connection, StatusHints statusHints) {}

        public void onAddressChanged(RemoteConnection connection, Uri address,
                int presentation) {}

        public void onCallerDisplayNameChanged(RemoteConnection connection,
                String callerDisplayName, int presentation) {}

        public void onVideoStateChanged(RemoteConnection connection, int videoState) {}

        public void onDestroyed(RemoteConnection connection) {}

        public void onConferenceableConnectionsChanged(RemoteConnection connection,
                List<RemoteConnection> conferenceableConnections) {}

        public void onVideoProviderChanged(RemoteConnection connection,
                VideoProvider videoProvider) {}

        public void onConferenceChanged(RemoteConnection connection,
                RemoteConference conference) {}

        public void onExtrasChanged(RemoteConnection connection, Bundle extras) {}

        public void onConnectionEvent(RemoteConnection connection, String event,
                Bundle extras) {}
    }

    /**
     * The remote end's video provider. Same reasoning as the outer class: no
     * RemoteConnection is ever handed out, so no provider is either. AOSP has
     * no public constructor for it.
     */
    public static class VideoProvider {

        public abstract static class Callback {
            public Callback() {}

            public void onSessionModifyRequestReceived(VideoProvider videoProvider,
                    VideoProfile videoProfile) {}

            public void onSessionModifyResponseReceived(VideoProvider videoProvider,
                    int status, VideoProfile requestedProfile, VideoProfile responseProfile) {}

            public void onCallSessionEvent(VideoProvider videoProvider, int event) {}

            public void onPeerDimensionsChanged(VideoProvider videoProvider,
                    int width, int height) {}

            public void onCallDataUsageChanged(VideoProvider videoProvider, long dataUsage) {}

            public void onCameraCapabilitiesChanged(VideoProvider videoProvider,
                    VideoProfile.CameraCapabilities cameraCapabilities) {}

            public void onVideoQualityChanged(VideoProvider videoProvider, int videoQuality) {}
        }

        VideoProvider() {}

        public void registerCallback(Callback l) {}

        public void unregisterCallback(Callback l) {}

        public void setCamera(String cameraId) {}

        public void setPreviewSurface(Surface surface) {}

        public void setDisplaySurface(Surface surface) {}

        public void setDeviceOrientation(int rotation) {}

        public void setZoom(float value) {}

        public void sendSessionModifyRequest(VideoProfile fromProfile, VideoProfile toProfile) {}

        public void sendSessionModifyResponse(VideoProfile responseProfile) {}

        public void requestCameraCapabilities() {}

        public void requestCallDataUsage() {}

        public void setPauseImage(Uri uri) {}
    }

    /** No public constructor on AOSP either: only Telecom creates these. */
    RemoteConnection() {}

    public void registerCallback(Callback callback) {}

    public void registerCallback(Callback callback, Handler handler) {}

    public void unregisterCallback(Callback callback) {}

    public Uri getAddress() { return null; }

    public int getAddressPresentation() { return TelecomManager.PRESENTATION_UNKNOWN; }

    public CharSequence getCallerDisplayName() { return null; }

    public int getCallerDisplayNamePresentation() { return TelecomManager.PRESENTATION_UNKNOWN; }

    public int getState() { return Connection.STATE_NEW; }

    public DisconnectCause getDisconnectCause() { return null; }

    public int getConnectionCapabilities() { return 0; }

    public int getConnectionProperties() { return 0; }

    public boolean isRingbackRequested() { return false; }

    public boolean isVoipAudioMode() { return false; }

    public StatusHints getStatusHints() { return null; }

    public int getVideoState() { return VideoProfile.STATE_AUDIO_ONLY; }

    public VideoProvider getVideoProvider() { return null; }

    public Bundle getExtras() { return null; }

    public RemoteConference getConference() { return null; }

    public List<RemoteConnection> getConferenceableConnections() {
        return Collections.emptyList();
    }

    // Commands to the remote end. There is no remote end, and AOSP likewise
    // no-ops each of these when its own IConnectionService binder is null.

    public void abort() {}

    public void answer() {}

    public void reject() {}

    public void hold() {}

    public void unhold() {}

    public void disconnect() {}

    public void playDtmfTone(char digit) {}

    public void stopDtmfTone() {}

    public void postDialContinue(boolean proceed) {}

    public void pullExternalCall() {}

    public void setCallAudioState(CallAudioState state) {}

    public void addConferenceParticipants(List<Uri> participants) {}
}
