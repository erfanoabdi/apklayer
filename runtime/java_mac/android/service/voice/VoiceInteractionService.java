package android.service.voice;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

import java.util.Collections;
import java.util.Set;

/**
 * Base for a voice assistant's always-resident service.
 *
 * PACKAGE NOTE -- applies to android.service.voice. A VoiceInteractionService
 * is the DEVICE'S assistant: the system binds it at boot, keeps it alive, and
 * routes the assist gesture and hotword to it. Mac.apk runs one app and is not
 * an Android device's assistant host, so nothing binds it, showSession() has no
 * system to ask, and isActiveService() is false.
 *
 * That last one is the important honest answer: an assistant app's own settings
 * screen calls isActiveService() to decide whether to show "you are the default
 * assistant" or "tap here to set me as default". False is correct here and is
 * the same answer every non-default assistant gets on a real device, so the app
 * shows its setup path rather than pretending to be enabled.
 *
 * Constants transcribed verbatim from /tmp/aosp_api/android16.txt:
 *   SERVICE_INTERFACE = "android.service.voice.VoiceInteractionService"
 *   SERVICE_META_DATA = "android.voice_interaction"
 *
 * Source: frameworks/base/core/java/android/service/voice/VoiceInteractionService.java
 */
public class VoiceInteractionService extends android.app.Service {

    public static final String SERVICE_INTERFACE =
            "android.service.voice.VoiceInteractionService";
    public static final String SERVICE_META_DATA = "android.voice_interaction";

    private int mDisabledShowContext;

    public VoiceInteractionService() { }

    /** AOSP returns the interaction binder. android.app.Service.onBind already
     *  returns null in this tree. */
    @Override public IBinder onBind(Intent intent) { return null; }

    /** Empty in AOSP too; the assistant overrides them. */
    public void onReady() { }

    public void onShutdown() { }

    public void onLaunchVoiceAssistFromKeyguard() { }

    /** API 33. */
    public void onPrepareToShowSession(Bundle args, int flags) { }

    public void onShowSessionFailed(Bundle args) { }

    /**
     * API 29. AOSP's default returns an EMPTY set -- "I support none of the
     * actions you asked about" -- rather than echoing the input back. An
     * assistant overrides it to claim the ones it handles.
     */
    public Set<String> onGetSupportedVoiceActions(Set<String> voiceActions) {
        return Collections.emptySet();
    }

    /** AOSP asks the system to start a session. There is no system to ask, and
     *  AOSP itself calls onShowSessionFailed when the request cannot be
     *  served -- so the assistant's own failure path runs rather than a
     *  session that never appears. */
    public void showSession(Bundle args, int flags) {
        onShowSessionFailed(args == null ? new Bundle() : args);
    }

    /** ⚠ ASSIGNS state so the getter round-trips; AOSP pushes the value to the
     *  system and reads it back, and a setter that dropped it would make
     *  getDisabledShowContext() contradict setDisabledShowContext(). */
    public void setDisabledShowContext(int flags) { mDisabledShowContext = flags; }

    public int getDisabledShowContext() { return mDisabledShowContext; }

    /** AOSP pushes hint text to the assist UI. No assist UI exists. */
    public final void setUiHints(Bundle hints) { }

    /**
     * False: Mac.apk has no "default assistant" setting for a guest to be
     * selected in. See the class note -- this is the answer that makes an
     * assistant app show its setup flow, which is the correct behaviour.
     */
    public static boolean isActiveService(Context context, ComponentName service) {
        return false;
    }
}
