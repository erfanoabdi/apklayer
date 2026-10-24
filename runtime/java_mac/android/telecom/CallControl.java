// android.telecom.CallControl -- the handle Telecom gives an app (through the
// OutcomeReceiver of TelecomManager.addCall) to drive a transactional call.
//
// ===================================================================
// NEVER SUCCEEDS INERTLY. Every method here reports FAILURE through the
// caller's OutcomeReceiver rather than doing nothing.
// ===================================================================
//
// The reasoning, which is measured rather than theoretical: an app that asks
// Telecom to change call state and gets NO callback simply waits. Signal's
// outgoing-call path blocks on a CallControlScope handed back by the platform,
// so a call-control API that accepts a request and then goes silent deadlocks
// the call and leaves the app's own audio manager disabled. An explicit error
// is the difference between "this device cannot do system-integrated calling"
// (which every such app already handles, because most devices restrict it) and
// a hang with no diagnosis.
//
// The error carries CODE_CALL_IS_NOT_BEING_TRACKED, which is precisely
// accurate: with no Telecom service, no call is being tracked. It is one of
// AOSP's own CallException codes, not an invented one.
//
// In practice nothing can even reach these methods: a CallControl only ever
// arrives via addCall's OutcomeReceiver, and our addCall reports failure
// instead of producing one. There is deliberately no public constructor, so an
// app cannot fabricate one either. The failure paths below exist so that this
// class cannot become a silent hang if that ever changes.
//
// Mirrors telecomm/java/android/telecom/CallControl.java (API 34); public
// surface confirmed against frameworks/base/core/api/current.txt.
package android.telecom;

import android.os.Bundle;
import android.os.OutcomeReceiver;
import android.os.ParcelUuid;

import java.util.Objects;
import java.util.concurrent.Executor;

public final class CallControl {

    private final ParcelUuid mCallId;

    // No public constructor on AOSP either: Telecom is the only producer.
    CallControl(ParcelUuid callId) {
        mCallId = callId;
    }

    public ParcelUuid getCallId() {
        return mCallId;
    }

    public void setActive(Executor executor, OutcomeReceiver<Void, CallException> callback) {
        failNoTelecom(executor, callback);
    }

    public void answer(int videoState, Executor executor,
            OutcomeReceiver<Void, CallException> callback) {
        failNoTelecom(executor, callback);
    }

    public void setInactive(Executor executor, OutcomeReceiver<Void, CallException> callback) {
        failNoTelecom(executor, callback);
    }

    public void disconnect(DisconnectCause disconnectCause, Executor executor,
            OutcomeReceiver<Void, CallException> callback) {
        Objects.requireNonNull(disconnectCause);
        failNoTelecom(executor, callback);
    }

    public void startCallStreaming(Executor executor,
            OutcomeReceiver<Void, CallException> callback) {
        failNoTelecom(executor, callback);
    }

    public void requestCallEndpointChange(CallEndpoint callEndpoint, Executor executor,
            OutcomeReceiver<Void, CallException> callback) {
        Objects.requireNonNull(callEndpoint);
        failNoTelecom(executor, callback);
    }

    public void requestMuteState(boolean isMuted, Executor executor,
            OutcomeReceiver<Void, CallException> callback) {
        failNoTelecom(executor, callback);
    }

    public void requestVideoState(int videoState, Executor executor,
            OutcomeReceiver<Void, CallException> callback) {
        failNoTelecom(executor, callback);
    }

    /**
     * sendEvent is fire-and-forget on AOSP -- it has no OutcomeReceiver and no
     * documented failure signal, so there is no callback to fail. Dropping the
     * event is the honest outcome: there is no Telecom service to deliver it
     * to, and no caller is waiting on a reply.
     */
    public void sendEvent(String event, Bundle extras) {
        Objects.requireNonNull(event);
        Objects.requireNonNull(extras);
    }

    /**
     * Report the failure on the caller's executor, exactly as Telecom would
     * deliver a real error. Never onResult -- see the header.
     */
    private static void failNoTelecom(Executor executor,
            OutcomeReceiver<Void, CallException> callback) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);
        executor.execute(() -> callback.onError(new CallException(
                "This device has no Telecom service, so no call is being tracked",
                CallException.CODE_CALL_IS_NOT_BEING_TRACKED)));
    }
}
