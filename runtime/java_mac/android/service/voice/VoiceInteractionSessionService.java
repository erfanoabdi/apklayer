package android.service.voice;

import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

/**
 * The service the system binds to obtain a fresh VoiceInteractionSession each
 * time the assistant is invoked.
 *
 * See the package note on VoiceInteractionService: nothing invokes the
 * assistant here, so onNewSession is never called.
 *
 * Source: frameworks/base/core/java/android/service/voice/VoiceInteractionSessionService.java
 */
public abstract class VoiceInteractionSessionService extends android.app.Service {

    public VoiceInteractionSessionService() { }

    public abstract VoiceInteractionSession onNewSession(Bundle args);

    /** AOSP returns the session-service binder. android.app.Service.onBind
     *  already returns null in this tree. */
    @Override public IBinder onBind(Intent intent) { return null; }
}
