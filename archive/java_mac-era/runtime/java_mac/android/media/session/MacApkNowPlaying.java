// android.media.session.MacApkNowPlaying -- the app's active MediaSession,
// mirrored into macOS Now Playing (Control Center, the menu-bar widget, the
// lock screen) and the media keys / AirPods / Touch Bar transport routed back
// into the session's Callback. Not AOSP API.
//
// WHY (owner, 2026-10-19: "rythum doesnt sync with now playing in mac os").
// On a device, an active MediaSession is what SystemUI's media controls, the
// lock screen, Bluetooth AVRCP and the hardware media keys all talk to:
// MediaSessionService routes every one of them to the session's Callback, and
// reads the session's MediaMetadata + PlaybackState to draw the controls. The
// Mac's equivalent of that system surface is MPNowPlayingInfoCenter (what is
// shown) + MPRemoteCommandCenter (the commands, including the keyboard's media
// keys), so this is rung 1 of the decision ladder: route the session there.
// Mac.apk runs one app per process, so "the session that has media-button
// focus" (MediaSession.getMediaButtonSession, the most recently activated one)
// IS the process's Now Playing entry, as MediaSessionService picks it on a
// device. Every app that publishes a platform MediaSession gets it, including
// androidx MediaSessionCompat and Media3 (both drive a platform session).
//
// Gate MACAPK_NOW_PLAYING (default on; =0 publishes nothing and registers no
// remote commands).
package android.media.session;

public final class MacApkNowPlaying {
    private MacApkNowPlaying() {}

    static final boolean ENABLED = !"0".equals(System.getenv("MACAPK_NOW_PLAYING"));

    // Remote commands, matching nowplaying_bridge.mm.
    static final int CMD_PLAY = 1, CMD_PAUSE = 2, CMD_TOGGLE = 3, CMD_NEXT = 4,
                     CMD_PREVIOUS = 5, CMD_SEEK = 6, CMD_STOP = 7;

    // macOS MPNowPlayingPlaybackState: 0 unknown, 1 playing, 2 paused, 3 stopped.
    private static native void nativeUpdate(String title, String artist, String album,
                                            double durationSec, double elapsedSec,
                                            double rate, int playbackState, long actions,
                                            byte[] artworkPng, boolean artworkChanged);
    private static native void nativeClear();

    private static volatile boolean sLinked = true;
    private static volatile MediaSession sPublished;
    private static android.graphics.Bitmap sLastArt;

    /** A session changed its metadata, state or activity. Publishes only the
     *  media-button session, the one a device's system UI would show. */
    static void sessionChanged(MediaSession s) {
        if (!ENABLED || !sLinked || s == null) return;
        MediaSession pick = pick();
        if (pick == null) {
            if (sPublished != null) clear();
            return;
        }
        // Republish when the session that changed IS the one shown, or when the
        // choice itself moved (another session started playing).
        if (pick == s || pick != sPublished) publish(pick);
    }

    /** A session was released or deactivated. */
    static void sessionGone(MediaSession s) {
        if (!ENABLED || !sLinked) return;
        if (sPublished != s) return;
        MediaSession pick = pick();
        if (pick != null && pick != s) publish(pick); else clear();
    }

    /** Which active session the system media controls show. AOSP does not
     *  simply take the last setActive(true): MediaSessionStack.findMediaButtonSession
     *  (android14 services/core/java/com/android/server/media/MediaSessionStack.java
     *  :244-275) says "If the app has multiple media sessions, the media session
     *  whose playback state is not null and matches the audio playback state
     *  becomes the media button session. Otherwise the top priority session
     *  becomes the media button session." So: the shown session
     *  while it still plays, else any playing active session (the media-button
     *  session first), else the media-button session, else any active session
     *  that has metadata. An app with two sessions (a library/browser session
     *  next to the player's, or a stale one never released) would otherwise
     *  publish the empty one and drop every later update from the real one. */
    private static MediaSession pick() {
        MediaSession cur = sPublished;
        if (cur != null && cur.isActive() && isPlaying(cur)) return cur;
        MediaSession top = MediaSession.getMediaButtonSession();
        if (top != null && isPlaying(top)) return top;
        java.util.List<MediaSession> live = MediaSession.getLiveSessions();
        for (MediaSession m : live) if (m.isActive() && isPlaying(m)) return m;
        if (top != null) return top;
        for (MediaSession m : live) if (m.isActive() && m.getMetadata() != null) return m;
        return null;
    }

    private static boolean isPlaying(MediaSession s) {
        PlaybackState ps = s.getPlaybackState();
        if (ps == null) return false;
        switch (ps.getState()) {
            case PlaybackState.STATE_PLAYING:
            case PlaybackState.STATE_BUFFERING:
            case PlaybackState.STATE_FAST_FORWARDING:
            case PlaybackState.STATE_REWINDING:
                return true;
            default:
                return false;
        }
    }

    private static synchronized void publish(MediaSession s) {
        android.media.MediaMetadata md = s.getMetadata();
        PlaybackState ps = s.getPlaybackState();
        String title = null, artist = null, album = null;
        double duration = 0;
        android.graphics.Bitmap art = null;
        if (md != null) {
            title = text(md, android.media.MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
            if (title == null) title = text(md, android.media.MediaMetadata.METADATA_KEY_TITLE);
            artist = text(md, android.media.MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
            if (artist == null) artist = text(md, android.media.MediaMetadata.METADATA_KEY_ARTIST);
            if (artist == null) artist = text(md, android.media.MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
            album = text(md, android.media.MediaMetadata.METADATA_KEY_ALBUM);
            long d = md.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION);
            duration = d > 0 ? d / 1000.0 : 0;
            art = md.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (art == null) art = md.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART);
            if (art == null) art = md.getBitmap(android.media.MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        }
        int state = 0;
        double elapsed = 0, rate = 0;
        long actions = 0;
        if (ps != null) {
            actions = ps.getActions();
            float speed = ps.getPlaybackSpeed();
            long pos = ps.getPosition();
            switch (ps.getState()) {
                case PlaybackState.STATE_PLAYING:
                case PlaybackState.STATE_FAST_FORWARDING:
                case PlaybackState.STATE_REWINDING:
                case PlaybackState.STATE_BUFFERING:
                case PlaybackState.STATE_CONNECTING:
                case PlaybackState.STATE_SKIPPING_TO_NEXT:
                case PlaybackState.STATE_SKIPPING_TO_PREVIOUS:
                case PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM:
                    state = 1;
                    break;
                case PlaybackState.STATE_PAUSED:
                    state = 2;
                    break;
                case PlaybackState.STATE_STOPPED:
                case PlaybackState.STATE_ERROR:
                case PlaybackState.STATE_NONE:
                    state = 3;
                    break;
                default:
                    state = 0;
            }
            // PlaybackState.getPosition is the position AT getLastPositionUpdateTime
            // (SystemClock.elapsedRealtime base); Now Playing extrapolates from the
            // elapsed time it is handed and the rate, so hand it "now".
            if (pos >= 0) {
                long upd = ps.getLastPositionUpdateTime();
                long extra = 0;
                if (state == 1 && upd > 0) {
                    extra = (long) ((android.os.SystemClock.elapsedRealtime() - upd) * speed);
                    if (extra < 0) extra = 0;
                }
                elapsed = (pos + extra) / 1000.0;
                if (duration > 0 && elapsed > duration) elapsed = duration;
            }
            // STATE_BUFFERING reports speed 0 on a device while it stalls; Now
            // Playing should hold the position still rather than run ahead.
            rate = (state == 1) ? speed : 0;
        }
        byte[] png = null;
        boolean artChanged = art != sLastArt;
        if (artChanged) {
            sLastArt = art;
            if (art != null) png = toPng(art);
        }
        try {
            nativeUpdate(title, artist, album, duration, elapsed, rate, state, actions, png, artChanged);
            sPublished = s;
        } catch (UnsatisfiedLinkError e) {
            // A runtime binary older than this jar: no bridge, nothing to do.
            sLinked = false;
        } catch (Throwable ignored) {
        }
    }

    private static synchronized void clear() {
        sPublished = null;
        sLastArt = null;
        try {
            nativeClear();
        } catch (UnsatisfiedLinkError e) {
            sLinked = false;
        } catch (Throwable ignored) {
        }
    }

    private static String text(android.media.MediaMetadata md, String key) {
        try {
            CharSequence t = md.getText(key);
            if (t == null) return null;
            String s = t.toString();
            return s.isEmpty() ? null : s;
        } catch (Throwable t) {
            return null;
        }
    }

    private static byte[] toPng(android.graphics.Bitmap b) {
        try {
            if (b.isRecycled()) return null;
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            if (!b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)) return null;
            return out.toByteArray();
        } catch (Throwable t) {
            return null;
        }
    }

    /** Called by nowplaying_bridge.mm (any thread) when macOS sends a transport
     *  command: Control Center, the menu-bar widget, a media key, a headset.
     *  Delivered on the session's callback Handler, as MediaSessionService's
     *  binder calls are on a device (MediaSession.CallbackMessageHandler). */
    static void onRemoteCommand(int cmd, double arg) {
        final MediaSession s = sPublished != null ? sPublished : MediaSession.getMediaButtonSession();
        if (s == null) return;
        final MediaSession.Callback cb = s.getCallback();
        if (cb == null) return;
        Runnable r = () -> {
            try {
                switch (cmd) {
                    case CMD_PLAY: cb.onPlay(); break;
                    case CMD_PAUSE: cb.onPause(); break;
                    case CMD_TOGGLE: {
                        PlaybackState ps = s.getPlaybackState();
                        boolean playing = ps != null && ps.getState() == PlaybackState.STATE_PLAYING;
                        if (playing) cb.onPause(); else cb.onPlay();
                        break;
                    }
                    case CMD_NEXT: cb.onSkipToNext(); break;
                    case CMD_PREVIOUS: cb.onSkipToPrevious(); break;
                    case CMD_SEEK: cb.onSeekTo((long) Math.max(0, arg * 1000.0)); break;
                    case CMD_STOP: cb.onStop(); break;
                    default: break;
                }
            } catch (Throwable t) {
                try {
                    com.macapk.loader.NativeLog.w("Mac.apk.NowPlaying", "remote command " + cmd + " threw: " + t);
                } catch (Throwable ignored) {}
            }
        };
        android.os.Handler h = s.getCallbackHandler();
        if (h == null) h = new android.os.Handler(android.os.Looper.getMainLooper());
        h.post(r);
    }
}
