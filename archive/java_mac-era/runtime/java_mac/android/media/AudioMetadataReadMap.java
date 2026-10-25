package android.media;

/**
 * A read-only typed key/value map of audio-format metadata, handed to
 * {@link AudioTrack.OnCodecFormatChangedListener} when an offloaded codec's
 * output format changes. API 29.
 *
 * Source: frameworks/base/media/java/android/media/AudioMetadataReadMap.java
 */
public interface AudioMetadataReadMap {
    /** @return true if the map holds a value for the key. */
    <T> boolean containsKey(AudioMetadata.Key<T> key);

    /** A mutable copy of this map. */
    AudioMetadataMap dup();

    /** @return the value, or null if absent. */
    <T> T get(AudioMetadata.Key<T> key);

    int size();
}
