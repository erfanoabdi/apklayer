// android.media.VolumeProvider -- handles volume changes for a session whose
// audio is routed remotely (cast targets). AOSP frameworks/base
// media/java/android/media/VolumeProvider.java.
//
// Mac.apk has no remote playback routing, so a VolumeProvider is an inert holder:
// the framework never calls onAdjustVolume/onSetVolumeTo (no remote session
// drives it). The type must resolve because MediaSession.setPlaybackToRemote's
// published descriptor names it; a guest that subclasses VolumeProvider for a
// cast session links and runs, simply without remote routing.
package android.media;

public abstract class VolumeProvider {
    public static final int VOLUME_CONTROL_FIXED = 0;
    public static final int VOLUME_CONTROL_RELATIVE = 1;
    public static final int VOLUME_CONTROL_ABSOLUTE = 2;

    private final int mControlType;
    private final int mMaxVolume;
    private int mCurrentVolume;
    private Callback mCallback;
    private final String mVolumeControlId;

    public VolumeProvider(int volumeControl, int maxVolume, int currentVolume) {
        this(volumeControl, maxVolume, currentVolume, null);
    }

    public final int getVolumeControl() { return mControlType; }
    public final int getMaxVolume() { return mMaxVolume; }
    public final int getCurrentVolume() { return mCurrentVolume; }
    /** API 30. @Nullable in AOSP: the id the app passed to the 4-arg
     *  constructor, null when it used the 3-arg one. */
    public final String getVolumeControlId() { return mVolumeControlId; }

    public final void setCurrentVolume(int currentVolume) {
        mCurrentVolume = currentVolume;
        if (mCallback != null) mCallback.onVolumeChanged(this);
    }

    public void onSetVolumeTo(int volume) {}
    public void onAdjustVolume(int direction) {}

    public void setCallback(Callback callback) { mCallback = callback; }

    public abstract static class Callback {
        public abstract void onVolumeChanged(VolumeProvider volumeProvider);
    }

    /** API 30. The id lets a session distinguish several remote volume
     *  controls; it is stored so getVolumeControlId() reads back what was
     *  passed instead of always answering null. */
    public VolumeProvider(int volumeControl, int maxVolume, int currentVolume,
            String volumeControlId) {
        mControlType = volumeControl;
        mMaxVolume = maxVolume;
        mCurrentVolume = currentVolume;
        mVolumeControlId = volumeControlId;
    }
}

