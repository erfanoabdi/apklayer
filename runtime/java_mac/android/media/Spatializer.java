package android.media;

import java.util.concurrent.Executor;

/**
 * Spatial-audio (head-tracked 3D audio) query surface, API 32+.
 *
 * Mac.apk plays through CoreAudio with no spatializer effect in the chain, so
 * the honest answer is the one AOSP gives on a device whose audio HAL has no
 * spatializer: unavailable, disabled, SPATIALIZER_IMMERSIVE_LEVEL_NONE, and
 * canBeSpatialized() false for every format. Media apps query this to decide
 * whether to REQUEST a spatialized track, so a false answer keeps them on the
 * normal path -- claiming support would make them ask for processing we cannot
 * perform.
 *
 * Source: frameworks/base/media/java/android/media/Spatializer.java
 */
public class Spatializer {

    public static final int SPATIALIZER_IMMERSIVE_LEVEL_OTHER = -1;
    public static final int SPATIALIZER_IMMERSIVE_LEVEL_NONE = 0;
    public static final int SPATIALIZER_IMMERSIVE_LEVEL_MULTICHANNEL = 1;
    public static final int SPATIALIZER_IMMERSIVE_LEVEL_MCHAN_BED_PLUS_OBJECTS = 2;

    public static final int HEAD_TRACKING_MODE_UNSUPPORTED = -2;
    public static final int HEAD_TRACKING_MODE_DISABLED = -1;
    public static final int HEAD_TRACKING_MODE_OTHER = 0;
    public static final int HEAD_TRACKING_MODE_RELATIVE_WORLD = 1;
    public static final int HEAD_TRACKING_MODE_RELATIVE_DEVICE = 2;

    Spatializer() { }

    public boolean isAvailable() { return false; }

    public boolean isEnabled() { return false; }

    public int getImmersiveAudioLevel() { return SPATIALIZER_IMMERSIVE_LEVEL_NONE; }

    public boolean canBeSpatialized(AudioAttributes attributes, AudioFormat format) {
        return false;
    }

    public boolean isHeadTrackerAvailable() { return false; }

    /**
     * The widest channel masks this spatializer can render with individual
     * channel positions. API 36. AOSP:
     * `return mAm.getService().getSpatializedChannelMasks();`
     * (frameworks/base/media/java/android/media/Spatializer.java, main, L546),
     * which itself falls back to Collections.emptyList() when the call fails.
     *
     * Empty, which is the same answer isAvailable()==false already implies: a
     * spatializer that does not exist renders no channel mask with discrete
     * positions. Naming one here would contradict canBeSpatialized(), and an
     * app checking capability by mask rather than by isAvailable() would then
     * request processing that never happens.
     */
    public java.util.List<Integer> getSpatializedChannelMasks() {
        return java.util.Collections.emptyList();
    }

    /** API 33. The head tracker never becomes available here (there is no
     *  spatializer and no head-tracking sensor), so a registered listener is
     *  retained and never fires -- the same as on a phone with no compatible
     *  headset attached. AOSP requires both arguments non-null. */
    public void addOnHeadTrackerAvailableListener(Executor executor,
            OnHeadTrackerAvailableListener listener) {
        java.util.Objects.requireNonNull(executor, "executor must not be null");
        java.util.Objects.requireNonNull(listener, "listener must not be null");
    }

    public void removeOnHeadTrackerAvailableListener(OnHeadTrackerAvailableListener listener) {
        java.util.Objects.requireNonNull(listener, "listener must not be null");
    }

    public void addOnSpatializerStateChangedListener(Executor executor,
            OnSpatializerStateChangedListener listener) { }

    public void removeOnSpatializerStateChangedListener(
            OnSpatializerStateChangedListener listener) { }

    /** AOSP declares this as an interface. */
    public interface OnSpatializerStateChangedListener {
        void onSpatializerEnabledChanged(Spatializer spat, boolean enabled);
        void onSpatializerAvailableChanged(Spatializer spat, boolean available);
    }

    public interface OnHeadTrackerAvailableListener {
        void onHeadTrackerAvailableChanged(Spatializer spat, boolean available);
    }
}
