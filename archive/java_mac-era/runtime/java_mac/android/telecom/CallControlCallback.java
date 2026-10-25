// android.telecom.CallControlCallback -- the handshake interface an app passes
// to TelecomManager.addCall so Telecom can ASK it to change call state (the
// user tapped answer, another call needs the audio focus, and so on).
//
// Pure interface, so there is nothing to degrade: the app implements it. It is
// never INVOKED here, because invoking it is Telecom's job and there is no
// Telecom service -- which is also why addCall reports failure rather than
// accepting the call and going quiet.
//
// Every method hands the app a Consumer<Boolean> it must complete to accept or
// reject the request. That contract is worth naming: an app that never calls
// the consumer would hang on a real device too.
//
// Mirrors telecomm/java/android/telecom/CallControlCallback.java (API 34);
// member list confirmed against frameworks/base/core/api/current.txt.
package android.telecom;

import java.util.function.Consumer;

public interface CallControlCallback {

    void onSetActive(Consumer<Boolean> wasCompleted);

    void onSetInactive(Consumer<Boolean> wasCompleted);

    void onAnswer(int videoState, Consumer<Boolean> wasCompleted);

    void onDisconnect(DisconnectCause disconnectCause, Consumer<Boolean> wasCompleted);

    void onCallStreamingStarted(Consumer<Boolean> wasCompleted);
}
