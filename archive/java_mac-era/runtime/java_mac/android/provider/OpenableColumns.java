package android.provider;

/**
 * The two columns any content: URI may expose so a client can name and size it
 * without opening it.
 *
 * Pure constants -- there is nothing platform-specific to get wrong, and this
 * is the contract every file-picker / share flow reads
 * (ContentResolver.query(uri, null, ...) then getColumnIndex(DISPLAY_NAME)).
 *
 * Source: frameworks/base/core/java/android/provider/OpenableColumns.java
 */
public interface OpenableColumns {
    public static final String DISPLAY_NAME = "_display_name";
    public static final String SIZE = "_size";
}
