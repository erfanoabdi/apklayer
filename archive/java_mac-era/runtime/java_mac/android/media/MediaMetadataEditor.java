package android.media;

/**
 * Base class for editing media metadata (the parent of
 * RemoteControlClient.MetadataEditor in AOSP). Stores key/value pairs and
 * round-trips them; there is no external metadata sink on this platform.
 *
 * Source: frameworks/base/media/java/android/media/MediaMetadataEditor.java
 */
public abstract class MediaMetadataEditor {

    public static final int BITMAP_KEY_ARTWORK = 100;
    public static final int RATING_KEY_BY_OTHERS = 101;
    public static final int RATING_KEY_BY_USER = 0x10000001;

    protected boolean mMetadataChanged = false;
    protected boolean mApplied = false;
    protected final java.util.HashMap<Integer, Object> mEditorMetadata = new java.util.HashMap<>();

    protected MediaMetadataEditor() { }

    public MediaMetadataEditor putObject(int key, Object value) {
        mEditorMetadata.put(key, value);
        mMetadataChanged = true;
        return this;
    }

    public MediaMetadataEditor putString(int key, String value) { return putObject(key, value); }
    public MediaMetadataEditor putLong(int key, long value) { return putObject(key, Long.valueOf(value)); }

    public Object getObject(int key, Object defaultValue) {
        return mEditorMetadata.containsKey(key) ? mEditorMetadata.get(key) : defaultValue;
    }
    public long getLong(int key, long defaultValue) {
        Object o = mEditorMetadata.get(key);
        return o instanceof Long ? (Long) o : defaultValue;
    }
    public String getString(int key, String defaultValue) {
        Object o = mEditorMetadata.get(key);
        return o instanceof String ? (String) o : defaultValue;
    }

    /** AOSP MediaMetadataEditor.putBitmap(int, Bitmap): only BITMAP_KEY_ARTWORK
     *  is a legal bitmap key, and anything else is IllegalArgumentException
     *  (MediaMetadataEditor.java, putBitmap). */
    public MediaMetadataEditor putBitmap(int key, android.graphics.Bitmap bitmap)
            throws IllegalArgumentException {
        if (key != BITMAP_KEY_ARTWORK) {
            throw new IllegalArgumentException("Invalid type 'Bitmap' for key " + key);
        }
        return putObject(key, bitmap);
    }

    /** AOSP MediaMetadataEditor.getBitmap(int, Bitmap): same key validation,
     *  and the supplied bitmap is returned when the key is absent. */
    public android.graphics.Bitmap getBitmap(int key, android.graphics.Bitmap defaultValue)
            throws IllegalArgumentException {
        if (key != BITMAP_KEY_ARTWORK) {
            throw new IllegalArgumentException("Invalid type 'Bitmap' for key " + key);
        }
        Object o = mEditorMetadata.get(key);
        return (o instanceof android.graphics.Bitmap) ? (android.graphics.Bitmap) o : defaultValue;
    }

    public void addEditableKey(int key) { }
    public void removeEditableKeys() { }
    public int[] getEditableKeys() { return new int[0]; }

    public void clear() { mEditorMetadata.clear(); }

    public abstract void apply();
}
