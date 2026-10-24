// android.preference.PreferenceDataStore -- lets an app back its preference
// hierarchy with something OTHER than SharedPreferences (a network profile, a
// database, an encrypted blob). Mirrors AOSP
// frameworks/base/core/java/android/preference/PreferenceDataStore.java: an
// interface whose twelve methods are all `default`, the six putters throwing
// UnsupportedOperationException and the six getters returning the caller's
// default. That asymmetry is deliberate upstream -- a read-only store is a
// legitimate implementation, a silently-dropping write is not.
//
// This is wired in for real: Preference.persist*/getPersisted* consult
// getPreferenceDataStore() first and only fall through to SharedPreferences when
// no store is set, which is exactly AOSP's dispatch.
package android.preference;

import java.util.Set;

@SuppressWarnings("deprecation")
public interface PreferenceDataStore {

    default void putString(String key, String value) {
        throw new UnsupportedOperationException("Not implemented on this data store");
    }

    default void putStringSet(String key, Set<String> values) {
        throw new UnsupportedOperationException("Not implemented on this data store");
    }

    default void putInt(String key, int value) {
        throw new UnsupportedOperationException("Not implemented on this data store");
    }

    default void putLong(String key, long value) {
        throw new UnsupportedOperationException("Not implemented on this data store");
    }

    default void putFloat(String key, float value) {
        throw new UnsupportedOperationException("Not implemented on this data store");
    }

    default void putBoolean(String key, boolean value) {
        throw new UnsupportedOperationException("Not implemented on this data store");
    }

    default String getString(String key, String defValue) {
        return defValue;
    }

    default Set<String> getStringSet(String key, Set<String> defValues) {
        return defValues;
    }

    default int getInt(String key, int defValue) {
        return defValue;
    }

    default long getLong(String key, long defValue) {
        return defValue;
    }

    default float getFloat(String key, float defValue) {
        return defValue;
    }

    default boolean getBoolean(String key, boolean defValue) {
        return defValue;
    }
}
