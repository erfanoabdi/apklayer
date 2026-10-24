package android.media;

/**
 * The mutable form of {@link AudioMetadataReadMap}. API 29.
 *
 * Source: frameworks/base/media/java/android/media/AudioMetadataMap.java
 */
public interface AudioMetadataMap extends AudioMetadataReadMap {
    /** @return the removed value, or null if the key was absent. */
    <T> T remove(AudioMetadata.Key<T> key);

    /** @return the PREVIOUS value for the key, or null if there was none. */
    <T> T set(AudioMetadata.Key<T> key, T value);
}
