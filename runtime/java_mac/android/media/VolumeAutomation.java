package android.media;

/**
 * Implemented by the classes whose volume a {@link VolumeShaper} can drive --
 * {@link AudioTrack} and {@link MediaPlayer}.
 *
 * Source: frameworks/base/media/java/android/media/VolumeAutomation.java
 */
public interface VolumeAutomation {
    /**
     * Returns a {@link VolumeShaper} object that can be used to control the
     * volume of this player with the given configuration.
     */
    VolumeShaper createVolumeShaper(VolumeShaper.Configuration configuration);
}
