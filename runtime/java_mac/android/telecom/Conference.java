// android.telecom.Conference -- a group of Connections merged into one call,
// owned by a ConnectionService.
//
// WHY THIS IS A REAL LOCAL STATE MACHINE, NOT A STUB -- the same reasoning as
// Connection (see that file's header). A Conference is an object the APP
// subclasses and drives: it calls addConnection()/setActive()/
// setDisconnected() and reads getConnections()/getState() back on its own code
// paths. That bookkeeping is ordinary object state, not a device capability,
// and AOSP keeps it in exactly these fields. So the mutators really do move the
// state here and the accessors really do report it: an app that adds two
// Connections and asserts getConnections().size() == 2 behaves the same on
// Mac.apk as on Android. addConnection() also sets the child Connection's
// back-pointer, so getConference() on the child answers correctly -- AOSP's
// own bidirectional bookkeeping.
//
// WHAT IS HONESTLY ABSENT: the half that talks to the Telecom SERVICE. A Mac
// has no Telecom service, so there are no listeners to notify (AOSP's listener
// set is likewise empty until Telecom binds the ConnectionService), the
// in-call UI is never updated, and none of the on* callbacks below is ever
// invoked -- they exist to be OVERRIDDEN, which is the whole reason the class
// must link. getCallAudioState()/getCurrentCallEndpoint() stay null for the
// same reason Connection's do: Telecom pushes those in, and AOSP also returns
// null before that push.
//
// Mirrors telecomm/java/android/telecom/Conference.java; public member list
// confirmed against frameworks/base/core/api/current.txt.
package android.telecom;

import android.net.Uri;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public abstract class Conference extends Conferenceable {

    /** AOSP: "used to indicate that the conference connect time is not known". */
    public static final long CONNECT_TIME_NOT_SPECIFIED = 0;

    private final PhoneAccountHandle mPhoneAccount;
    private final List<Connection> mChildConnections = new ArrayList<>();
    private final List<Connection> mUnmodifiableChildConnections =
            Collections.unmodifiableList(mChildConnections);
    private final List<Connection> mConferenceableConnections = new ArrayList<>();
    private final List<Connection> mUnmodifiableConferenceableConnections =
            Collections.unmodifiableList(mConferenceableConnections);

    private int mState = Connection.STATE_NEW;
    private DisconnectCause mDisconnectCause;
    private int mConnectionCapabilities;
    private int mConnectionProperties;
    private boolean mRingbackRequested;
    private StatusHints mStatusHints;
    private Bundle mExtras;
    private long mConnectTimeMillis = CONNECT_TIME_NOT_SPECIFIED;
    private long mConnectionStartElapsedRealTime = CONNECT_TIME_NOT_SPECIFIED;

    public Conference(PhoneAccountHandle phoneAccount) {
        mPhoneAccount = phoneAccount;
    }

    public final PhoneAccountHandle getPhoneAccountHandle() {
        return mPhoneAccount;
    }

    public final List<Connection> getConnections() {
        return mUnmodifiableChildConnections;
    }

    public final int getState() {
        return mState;
    }

    public final int getConnectionCapabilities() {
        return mConnectionCapabilities;
    }

    public final int getConnectionProperties() {
        return mConnectionProperties;
    }

    /**
     * @deprecated on AOSP. Telecom pushes the audio state in; with no Telecom
     * service this stays null, which is also what AOSP returns before the push.
     */
    @Deprecated
    public final CallAudioState getCallAudioState() {
        return null;
    }

    /** @see #getCallAudioState() -- no Telecom service means no endpoint. */
    public final CallEndpoint getCurrentCallEndpoint() {
        return null;
    }

    /**
     * A Mac has no cellular video-call path, so nothing ever installs a video
     * provider on a Conference. AOSP also returns null when the conference has
     * none, so callers already handle it.
     */
    public Connection.VideoProvider getVideoProvider() {
        return null;
    }

    /** AOSP default: audio-only. No video-call path exists here to change it. */
    public int getVideoState() {
        return VideoProfile.STATE_AUDIO_ONLY;
    }

    public final DisconnectCause getDisconnectCause() {
        return mDisconnectCause;
    }

    public final StatusHints getStatusHints() {
        return mStatusHints;
    }

    public final Bundle getExtras() {
        return mExtras;
    }

    public final boolean isRingbackRequested() {
        return mRingbackRequested;
    }

    public final List<Connection> getConferenceableConnections() {
        return mUnmodifiableConferenceableConnections;
    }

    public final long getConnectionTime() {
        return mConnectTimeMillis;
    }

    public final long getConnectionStartElapsedRealtimeMillis() {
        return mConnectionStartElapsedRealTime;
    }

    // ---- Mutators. Real local state; the Telecom notify half is absent. ----

    /**
     * AOSP refuses a duplicate child and installs the back-pointer on the
     * accepted one, so connection.getConference() answers. Copied exactly --
     * an app that adds the same Connection twice must see one child, not two.
     *
     * @return true if the connection was newly added.
     */
    public final boolean addConnection(Connection connection) {
        if (connection != null && !mChildConnections.contains(connection)) {
            if (connection.setConference(this)) {
                mChildConnections.add(connection);
                onConnectionAdded(connection);
                return true;
            }
        }
        return false;
    }

    public final void removeConnection(Connection connection) {
        if (connection != null && mChildConnections.remove(connection)) {
            connection.resetConference();
        }
    }

    public final void setConferenceableConnections(List<Connection> conferenceableConnections) {
        mConferenceableConnections.clear();
        if (conferenceableConnections != null) {
            mConferenceableConnections.addAll(conferenceableConnections);
        }
    }

    public final void setConnectionCapabilities(int connectionCapabilities) {
        mConnectionCapabilities = connectionCapabilities;
    }

    public final void setConnectionProperties(int connectionProperties) {
        mConnectionProperties = connectionProperties;
    }

    public final void setActive() {
        setState(Connection.STATE_ACTIVE);
    }

    public final void setDialing() {
        setState(Connection.STATE_DIALING);
    }

    public final void setRinging() {
        setState(Connection.STATE_RINGING);
    }

    public final void setOnHold() {
        setState(Connection.STATE_HOLDING);
    }

    public final void setDisconnected(DisconnectCause disconnectCause) {
        mDisconnectCause = disconnectCause;
        setState(Connection.STATE_DISCONNECTED);
        // AOSP disconnects every child so the app's own per-connection
        // teardown runs. Without this a merged call's children would sit in
        // STATE_ACTIVE forever after the conference ended.
        for (Connection connection : new ArrayList<>(mChildConnections)) {
            connection.setDisconnected(disconnectCause);
        }
    }

    /**
     * AOSP refuses every transition out of STATE_DISCONNECTED (see
     * Connection.setState for the same guard and the same reason).
     */
    private void setState(int newState) {
        if (mState == Connection.STATE_DISCONNECTED && mState != newState) {
            return;
        }
        mState = newState;
    }

    public final void setRingbackRequested(boolean ringback) {
        mRingbackRequested = ringback;
    }

    public final void setStatusHints(StatusHints statusHints) {
        mStatusHints = statusHints;
    }

    public final void setExtras(Bundle extras) {
        mExtras = extras;
    }

    /**
     * AOSP MERGES into the existing extras rather than replacing them, and
     * copies the incoming Bundle so a later caller mutation cannot be seen.
     * Same contract as Connection.putExtras.
     */
    public final void putExtras(Bundle extras) {
        if (extras == null) {
            return;
        }
        Bundle copy = new Bundle(extras);
        if (mExtras == null) {
            mExtras = new Bundle();
        }
        mExtras.putAll(copy);
    }

    public final void removeExtras(List<String> keys) {
        if (mExtras != null && keys != null) {
            for (String key : keys) {
                mExtras.remove(key);
            }
        }
    }

    public final void removeExtras(String... keys) {
        removeExtras(Arrays.asList(keys));
    }

    public final void setConnectionTime(long connectionTimeMillis) {
        mConnectTimeMillis = connectionTimeMillis;
    }

    /** @deprecated on AOSP in favour of setConnectionStartElapsedRealtimeMillis. */
    @Deprecated
    public final void setConnectionStartElapsedRealTime(long connectionStartElapsedRealTime) {
        setConnectionStartElapsedRealtimeMillis(connectionStartElapsedRealTime);
    }

    public final void setConnectionStartElapsedRealtimeMillis(long connectionStartElapsedRealTime) {
        mConnectionStartElapsedRealTime = connectionStartElapsedRealTime;
    }

    /**
     * No cellular video-call path exists, so there is no provider to install.
     * AOSP's own implementation is a notify-Telecom call, which is exactly the
     * half that is absent here.
     */
    public final void setVideoProvider(Connection videoProvider,
            Connection.VideoProvider videoProviderInstance) {
    }

    /** @see #setVideoProvider -- no video-call path to change the state of. */
    public final void setVideoState(Connection c, int videoState) {
    }

    /** Telecom relays conference events to the in-call UI; there is none. */
    public void sendConferenceEvent(String event, Bundle extras) {
    }

    /**
     * AOSP moves the conference to STATE_DISCONNECTED (if it is not already)
     * and drops every child, then notifies Telecom to remove it. With no
     * listeners the observable part is the local teardown, which we keep.
     */
    public final void destroy() {
        for (Connection connection : new ArrayList<>(mChildConnections)) {
            removeConnection(connection);
        }
        if (mState != Connection.STATE_DISCONNECTED) {
            setState(Connection.STATE_DISCONNECTED);
        }
    }

    /**
     * AOSP returns a Conference already in STATE_DISCONNECTED carrying the
     * given cause, used by a ConnectionService to reject a create request.
     * Real behaviour, no device dependency.
     */
    public static Conference createFailedConference(DisconnectCause disconnectCause,
            PhoneAccountHandle phoneAccount) {
        return new FailureSignalingConference(disconnectCause, phoneAccount);
    }

    private static final class FailureSignalingConference extends Conference {
        private FailureSignalingConference(DisconnectCause disconnectCause,
                PhoneAccountHandle phoneAccount) {
            super(phoneAccount);
            setDisconnected(disconnectCause);
        }
    }

    // Inbound callbacks from Telecom. None is invoked here (there is no Telecom
    // service to invoke them); they exist to be overridden, which is why the
    // class had to link in the first place.

    public void onConnectionAdded(Connection connection) {}

    public void onDisconnect() {}

    public void onSeparate(Connection connection) {}

    public void onAddConferenceParticipants(List<Uri> participants) {}

    public void onMerge(Connection connection) {}

    public void onMerge() {}

    public void onSwap() {}

    public void onHold() {}

    public void onUnhold() {}

    public void onAnswer(int videoState) {}

    public void onReject() {}

    public void onPlayDtmfTone(char c) {}

    public void onStopDtmfTone() {}

    /** @deprecated on AOSP alongside getCallAudioState(). */
    @Deprecated
    public void onCallAudioStateChanged(CallAudioState state) {}

    public void onCallEndpointChanged(CallEndpoint callEndpoint) {}

    public void onAvailableCallEndpointsChanged(List<CallEndpoint> availableEndpoints) {}

    public void onMuteStateChanged(boolean isMuted) {}

    public void onExtrasChanged(Bundle extras) {}
}
