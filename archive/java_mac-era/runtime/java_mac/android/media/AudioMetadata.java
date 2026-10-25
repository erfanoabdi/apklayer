package android.media;

import java.util.HashMap;
import java.util.Objects;

/**
 * Typed key/value metadata describing an audio format (API 29): the factory for
 * {@link AudioMetadataMap} plus the well-known {@link Format} keys.
 *
 * This is a complete, working implementation -- the map is ordinary in-process
 * data structure work with nothing platform-specific about it, so there is
 * nothing to stub. What Mac.apk does not have is a source that PRODUCES one: the
 * maps AOSP delivers come from an offloaded hardware codec reporting a format
 * change, and this host decodes in software (see AudioManager's offload
 * answers). So an app can build, populate and read these maps exactly as on
 * Android; it just never receives one from the platform.
 *
 * Source: frameworks/base/media/java/android/media/AudioMetadata.java
 * (android16-release).
 */
public final class AudioMetadata {

    private AudioMetadata() { }

    /** A typed metadata key. */
    public interface Key<T> {
        String getName();
        Class<T> getValueClass();
    }

    /** AOSP: "Creates a AudioMetadataMap for use." */
    public static AudioMetadataMap createMap() { return new BaseMap(); }

    /** The keys the platform itself populates for a codec format. */
    public static class Format {
        public static final Key<String> KEY_MIME = createKey("mime", String.class);
        public static final Key<Integer> KEY_SAMPLE_RATE = createKey("sample-rate", Integer.class);
        public static final Key<Integer> KEY_CHANNEL_MASK =
                createKey("channel-mask", Integer.class);
        public static final Key<Integer> KEY_PRESENTATION_ID =
                createKey("presentation-id", Integer.class);
        public static final Key<Integer> KEY_PROGRAM_ID = createKey("program-id", Integer.class);
        public static final Key<Integer> KEY_PRESENTATION_CONTENT_CLASSIFIER =
                createKey("presentation-content-classifier", Integer.class);
        public static final Key<String> KEY_PRESENTATION_LANGUAGE =
                createKey("presentation-language", String.class);
        public static final Key<Integer> KEY_BIT_RATE = createKey("bitrate", Integer.class);
        public static final Key<Integer> KEY_AUDIO_ENCODING =
                createKey("audio-encoding", Integer.class);
        public static final Key<Integer> KEY_BIT_WIDTH = createKey("bit-width", Integer.class);
        public static final Key<Boolean> KEY_ATMOS_PRESENT =
                createKey("atmos-present", Boolean.class);

        // AOSP's key names are the raw strings above (AudioMetadata.java
        // L200-260); they are the wire names the HAL uses, so they are copied
        // verbatim rather than invented.
        Format() { }
    }

    static <T> Key<T> createKey(String name, Class<T> type) {
        return new Key<T>() {
            @Override public String getName() { return name; }
            @Override public Class<T> getValueClass() { return type; }
            @Override public boolean equals(Object obj) {
                if (this == obj) return true;
                if (!(obj instanceof Key)) return false;
                Key<?> other = (Key<?>) obj;
                return name.equals(other.getName()) && type.equals(other.getValueClass());
            }
            @Override public int hashCode() { return Objects.hash(name, type); }
            @Override public String toString() { return name + ":" + type.getSimpleName(); }
        };
    }

    /** AOSP's BaseMap: a plain HashMap keyed on (name, valueClass), with the
     *  value type checked on the way in. */
    static final class BaseMap implements AudioMetadataMap {
        private final HashMap<Key<?>, Object> mMap = new HashMap<>();

        @Override public <T> boolean containsKey(Key<T> key) {
            Objects.requireNonNull(key);
            return mMap.containsKey(key);
        }

        @Override public AudioMetadataMap dup() {
            BaseMap copy = new BaseMap();
            copy.mMap.putAll(mMap);
            return copy;
        }

        @SuppressWarnings("unchecked")
        @Override public <T> T get(Key<T> key) {
            Objects.requireNonNull(key);
            Object value = mMap.get(key);
            return (value == null) ? null : (T) value;
        }

        @SuppressWarnings("unchecked")
        @Override public <T> T remove(Key<T> key) {
            Objects.requireNonNull(key);
            return (T) mMap.remove(key);
        }

        @SuppressWarnings("unchecked")
        @Override public <T> T set(Key<T> key, T value) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(value);
            // AOSP validates the value against the key's declared class so a
            // wrongly typed value cannot be read back as the wrong type later.
            if (!key.getValueClass().isInstance(value)) {
                throw new IllegalArgumentException(
                        "value is not of type " + key.getValueClass());
            }
            return (T) mMap.put(key, value);
        }

        @Override public int size() { return mMap.size(); }
    }
}
