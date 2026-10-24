// android.media.SoundPool -- short-clip player with polyphony + loop.
//
// Backed by the macOS CoreAudio engine (runtime/src/audio_backend.mm) via
// com.macapk.loader.NativeAudio: load() reads the encoded clip bytes,
// hands them to the native decoder (Ogg Vorbis via stb_vorbis, everything
// else via AVAudioFile), and the decoded PCM is cached + replayed on a pool
// of source-node voices feeding the shared mixer. AndEngine (flappybird,
// bouncy) drives this via load(AssetFileDescriptor).
//
// Observable behavior mirrored from AOSP frameworks/base
// media/java/android/media/SoundPool.java: load() returns a soundID (>0),
// play() returns a streamID (>0), loop semantics (0=once, -1=forever,
// n=repeat n times), OnLoadCompleteListener fires after a load resolves.
package android.media;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;

public class SoundPool {
    private OnLoadCompleteListener mListener;

    public SoundPool(int maxStreams, int streamType, int srcQuality) {}

    public int load(android.content.Context context, int resId, int priority) {
        com.macapk.loader.AudioTrace.hit("SoundPool.load(res)", "resId=" + resId);
        try {
            InputStream in = context.getResources().openRawResource(resId);
            return loadStream(in, -1);
        } catch (Throwable t) { return 0; }
    }
    public int load(String path, int priority) {
        com.macapk.loader.AudioTrace.hit("SoundPool.load(path)", path);
        try {
            return loadStream(new FileInputStream(path), -1);
        } catch (Throwable t) { return 0; }
    }
    public int load(java.io.FileDescriptor fd, long offset, long length, int priority) {
        com.macapk.loader.AudioTrace.hit("SoundPool.load(fd)", "off=" + offset + " len=" + length);
        try {
            FileInputStream in = new FileInputStream(fd);
            if (offset > 0) in.skip(offset);
            return loadStream(in, length);
        } catch (Throwable t) { return 0; }
    }
    public int load(android.content.res.AssetFileDescriptor afd, int priority) {
        com.macapk.loader.AudioTrace.hit("SoundPool.load(afd)");
        try {
            InputStream in = afd.createInputStream();
            return loadStream(in, afd.getLength());
        } catch (Throwable t) { return 0; }
    }

    private int loadStream(InputStream in, long maxLen) {
        if (in == null) return 0;
        try {
            byte[] data = readAll(in, maxLen);
            if (data.length == 0) return 0;
            int soundID = com.macapk.loader.NativeAudio.nativeSpLoad(data, data.length);
            // AOSP allocates the sample id up front and posts SAMPLE_LOADED
            // later with a status (0 = success) once the async decode
            // resolves -- on BOTH outcomes, since the id was already handed
            // out and something has to tell the caller its load finished.
            // Our native decode is synchronous (no separate "id assigned"
            // step to report early), but the same one-notification-per-
            // attempted-load contract still has to hold: engines commonly
            // batch-load N clips and wait for N onLoadComplete calls before
            // proceeding (AndEngine does exactly this), so a load that fails
            // to decode and never fires the listener hangs that counter
            // forever with no error -- the failure shape this project
            // tracks as worst-case. status is 0 for success, 1 for failure
            // (AOSP does not standardize a specific nonzero failure value).
            fireLoadComplete(soundID, soundID > 0 ? 0 : 1);
            return soundID;
        } catch (Throwable t) {
            fireLoadComplete(0, 1);
            return 0;
        } finally {
            try { in.close(); } catch (Throwable ignore) {}
        }
    }

    private static byte[] readAll(InputStream in, long maxLen) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(
            maxLen > 0 && maxLen < (1 << 26) ? (int) maxLen : 8192);
        byte[] buf = new byte[8192];
        long remaining = maxLen > 0 ? maxLen : Long.MAX_VALUE;
        int n;
        while (remaining > 0
               && (n = in.read(buf, 0, (int) Math.min(buf.length, remaining))) > 0) {
            out.write(buf, 0, n);
            remaining -= n;
        }
        return out.toByteArray();
    }

    private void fireLoadComplete(final int soundID, final int status) {
        final OnLoadCompleteListener l = mListener;
        if (l == null) return;
        // AOSP fires this on a handler thread, not inline -- mirror that so a
        // listener that calls back into SoundPool doesn't re-enter load().
        new Thread(new Runnable() {
            @Override public void run() {
                try { l.onLoadComplete(SoundPool.this, soundID, status); }
                catch (Throwable ignore) {}
            }
        }, "soundpool-loadcomplete").start();
    }

    public int play(int soundID, float leftVolume, float rightVolume, int priority, int loop, float rate) {
        com.macapk.loader.AudioTrace.hit("SoundPool.play", "id=" + soundID + " loop=" + loop + " rate=" + rate);
        if (soundID <= 0) return 0;
        return com.macapk.loader.NativeAudio.nativeSpPlay(soundID, leftVolume, rightVolume, loop, rate);
    }
    public void pause(int streamID)  { com.macapk.loader.NativeAudio.nativeSpPause(streamID); }
    public void resume(int streamID) { com.macapk.loader.NativeAudio.nativeSpResume(streamID); }
    public void stop(int streamID)   { com.macapk.loader.NativeAudio.nativeSpStop(streamID); }
    // Bulk-pause / bulk-resume (cocos2d-x Cocos2dxSound.pauseAllEffects on
    // Activity.onPause/onResume). AOSP: autoPause stops every currently-playing
    // stream; autoResume restarts the auto-paused ones.
    public void autoPause()  { com.macapk.loader.NativeAudio.nativeSpAutoPause(); }
    public void autoResume() { com.macapk.loader.NativeAudio.nativeSpAutoResume(); }
    public void setVolume(int streamID, float l, float r) { com.macapk.loader.NativeAudio.nativeSpSetVolume(streamID, l, r); }
    public void setLoop(int streamID, int loop) { com.macapk.loader.NativeAudio.nativeSpSetLoop(streamID, loop); }
    // Live playback-rate (pitch+speed) modulation of a playing stream. AOSP:
    // "A value of 1.0 means playback at the original frequency ... 2.0 twice
    // as fast ... 0.5 half speed. If the stream does not exist, it will have
    // no effect." This is the one SoundPool call that only CONTINUOUSLY
    // MODULATED sounds use (a game engine's RPM loop); a silent no-op here
    // leaves a looped sample droning at constant pitch while every one-shot
    // sounds fine (HCR 1.8.1's engine hum, 2026-08-18).
    public void setRate(int streamID, float rate) {
        com.macapk.loader.AudioTrace.hit("SoundPool.setRate", "stream=" + streamID + " rate=" + rate);
        com.macapk.loader.NativeAudio.nativeSpSetRate(streamID, rate);
    }
    public void setPriority(int streamID, int priority) {}
    public boolean unload(int soundID) { return com.macapk.loader.NativeAudio.nativeSpUnload(soundID); }
    public void release() {}

    /** AOSP declares finalize() on this class so a dropped instance still
     *  releases whatever it holds. release() is idempotent here. */
    @Override protected void finalize() throws Throwable {
        try { release(); } finally { super.finalize(); }
    }

    public void setOnLoadCompleteListener(OnLoadCompleteListener l) { mListener = l; }
    public interface OnLoadCompleteListener { void onLoadComplete(SoundPool sp, int sampleId, int status); }
    public static class Builder {
        private int mMaxStreams = 1;
        private int mSessionId = AudioManager.AUDIO_SESSION_ID_GENERATE;

        public Builder() { }

        /** AOSP rejects a non-positive stream count
         *  (SoundPool.java, Builder.setMaxStreams). */
        public Builder setMaxStreams(int maxStreams) {
            if (maxStreams <= 0) {
                throw new IllegalArgumentException("Strictly positive value required for maxStreams");
            }
            mMaxStreams = maxStreams;
            return this;
        }

        public Builder setAudioAttributes(android.media.AudioAttributes a) { return this; }

        /** API 34. The audio session the pool's streams join, so an app can
         *  attach an audiofx effect to them. AOSP rejects a negative id and
         *  treats AUDIO_SESSION_ID_GENERATE as "allocate one". Recorded and
         *  validated; this tree's audiofx layer has no effect engine to attach
         *  to (see AudioEffect), so the id changes nothing audible -- but
         *  rejecting the call outright would break an app that only wanted to
         *  set it. */
        public Builder setAudioSessionId(int sessionId) {
            if (sessionId < 0) {
                throw new IllegalArgumentException("Invalid audio session ID " + sessionId);
            }
            mSessionId = sessionId;
            return this;
        }

        /** API 34. AOSP uses the Context for playback attribution only; there
         *  is one package in this process, so it is validated non-null (as AOSP
         *  requires) and changes nothing observable. */
        public Builder setContext(android.content.Context context) {
            java.util.Objects.requireNonNull(context, "context cannot be null");
            return this;
        }

        public SoundPool build() { return new SoundPool(mMaxStreams, 0, 0); }
    }
}
