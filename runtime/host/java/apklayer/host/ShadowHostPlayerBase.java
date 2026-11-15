package apklayer.host;

import android.media.IAudioService;
import android.os.Build;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/**
 * The part of AudioService that PlayerBase talks to (trackPlayer, playerEvent, releasePlayer) instead of Robolectric's null proxy: this process
 * has a player (AudioTrack, MediaPlayer, SoundPool) started or none, and an AudioRecord started or none ({@link #activity}); the hub learns
 * the change (HubCallAudio.activity decides which MODE_IN_COMMUNICATION requester owns the call mode). The other calls answer their defaults.
 */
@Implements(className = "android.media.PlayerBase", isInAndroidSdk = false, minSdk = Build.VERSION_CODES.N)
public class ShadowHostPlayerBase {
    private static final int PLAYER_STATE_STARTED = 2;  // AudioPlaybackConfiguration.PLAYER_STATE_STARTED
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);
    private static final Set<Object> STARTED = new HashSet<>();  // players (their piid) and recorders (the AudioRecord) that run now

    private static final IAudioService SERVICE = (IAudioService) Proxy.newProxyInstance(IAudioService.class.getClassLoader(), new Class<?>[] {IAudioService.class},
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "trackPlayer": return NEXT_ID.getAndIncrement();
                    case "playerEvent": activity("player" + args[0], (Integer) args[1] == PLAYER_STATE_STARTED); return null;
                    case "releasePlayer": activity("player" + args[0], false); return null;
                    default: return method.getReturnType() == boolean.class ? (Object) false : method.getReturnType() == int.class ? (Object) 0 : null;
                }
            });

    @Implementation public static IAudioService getService() { return SERVICE; }

    /** {@code key} (a player or a recorder) runs or stopped; the hub hears when the process goes from none running to some, and back. */
    static void activity(Object key, boolean on) {
        synchronized (STARTED) {  // the report inside the lock: two threads' reports reach the hub in order
            boolean before = !STARTED.isEmpty();
            if (on) STARTED.add(key); else STARTED.remove(key);
            boolean after = !STARTED.isEmpty();
            if (before == after || !HubClient.connected()) return;
            try { HubClient.current().audioActivity(after); }
            catch (RuntimeException hubGone) { /* the instance is ending */ }
        }
    }
}
