// android.telecom.CallEventCallback -- the non-actionable updates Telecom
// pushes to an app that added a call transactionally (endpoint changed, mute
// state changed, ...).
//
// Pure interface; the app implements it. Never invoked here for the same
// reason as CallControlCallback: there is no Telecom service to push updates.
//
// onVideoStateChanged is `default` on AOSP (it arrived behind a feature flag
// after the interface shipped), so it is default here too -- an app compiled
// against the older shape must still satisfy the interface.
//
// Mirrors telecomm/java/android/telecom/CallEventCallback.java (API 34);
// member list confirmed against frameworks/base/core/api/current.txt.
package android.telecom;

import android.os.Bundle;

import java.util.List;

public interface CallEventCallback {

    void onCallEndpointChanged(CallEndpoint newCallEndpoint);

    void onAvailableCallEndpointsChanged(List<CallEndpoint> availableEndpoints);

    void onMuteStateChanged(boolean isMuted);

    void onCallStreamingFailed(int reason);

    void onEvent(String event, Bundle extras);

    default void onVideoStateChanged(int videoState) {}
}
