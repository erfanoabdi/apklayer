// android.media.SyncParams -- A/V sync tuning passed to MediaPlayer.setSyncParams.
// AOSP frameworks/base media/java/android/media/SyncParams.java. Mac.apk has no
// A/V sync controller, so this is a typed holder of the values apps set.
package android.media;

public final class SyncParams {
    public static final int SYNC_SOURCE_DEFAULT = 0;
    public static final int SYNC_SOURCE_SYSTEM_CLOCK = 1;
    public static final int SYNC_SOURCE_AUDIO = 2;
    public static final int SYNC_SOURCE_VSYNC = 3;
    public static final int AUDIO_ADJUST_MODE_DEFAULT = 0;
    public static final int AUDIO_ADJUST_MODE_STRETCH = 1;
    public static final int AUDIO_ADJUST_MODE_RESAMPLE = 2;

    private int mSyncSource = SYNC_SOURCE_DEFAULT;
    private int mAudioAdjustMode = AUDIO_ADJUST_MODE_DEFAULT;
    private float mTolerance = 0f;
    private float mFrameRate = 0f;

    public SyncParams() {}
    public SyncParams allowDefaults() { return this; }
    public SyncParams setSyncSource(int syncSource) { mSyncSource = syncSource; return this; }
    public int getSyncSource() { return mSyncSource; }
    public SyncParams setAudioAdjustMode(int audioAdjustMode) { mAudioAdjustMode = audioAdjustMode; return this; }
    public int getAudioAdjustMode() { return mAudioAdjustMode; }
    public SyncParams setTolerance(float tolerance) { mTolerance = tolerance; return this; }
    public float getTolerance() { return mTolerance; }
    public SyncParams setFrameRate(float frameRate) { mFrameRate = frameRate; return this; }
    public float getFrameRate() { return mFrameRate; }
}
