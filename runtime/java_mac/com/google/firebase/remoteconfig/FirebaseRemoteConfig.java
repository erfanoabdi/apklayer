package com.google.firebase.remoteconfig;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;

import java.util.HashMap;
import java.util.Map;

// FirebaseRemoteConfig no-op shadow. The defaults map is the ONE
// piece of state we honor: apps frequently call setDefaultsAsync(...)
// and then read those defaults back via getString / getBoolean /
// getLong / getDouble. If we returned empty for every getter, apps
// would see their own configured fallbacks vanish at runtime --
// causing UI defaulting to "off" toggles, zero-quantity item lists,
// etc. Keeping the defaults in memory matches the user-visible
// outcome of a real fetchAndActivate that never receives any new
// server values.
public final class FirebaseRemoteConfig {
    private static final FirebaseRemoteConfig INSTANCE = new FirebaseRemoteConfig();

    public static final int LAST_FETCH_STATUS_SUCCESS         = -1;
    public static final int LAST_FETCH_STATUS_NO_FETCH_YET    = 0;
    public static final int LAST_FETCH_STATUS_FAILURE         = 1;
    public static final int LAST_FETCH_STATUS_THROTTLED       = 2;
    public static final long DEFAULT_VALUE_FOR_LONG    = 0L;
    public static final double DEFAULT_VALUE_FOR_DOUBLE = 0.0;
    public static final String DEFAULT_VALUE_FOR_STRING = "";
    public static final boolean DEFAULT_VALUE_FOR_BOOLEAN = false;

    private final Map<String, Object> defaults = new HashMap<>();

    private FirebaseRemoteConfig() {}

    public static FirebaseRemoteConfig getInstance()                  { return INSTANCE; }
    public static FirebaseRemoteConfig getInstance(FirebaseApp app)   { return INSTANCE; }

    // Real API returns Task<FirebaseRemoteConfigInfo>. The Firebase C++ SDK
    // awaits this during init before fetching; a missing method throws
    // NoSuchMethodError uncaught on the Unity main thread and stalls Crossy's
    // GameSceneBoot. Return an already-completed Task carrying getInfo() so the
    // C++ await resolves immediately.
    public Task<FirebaseRemoteConfigInfo> ensureInitialized() {
        return Tasks.forResult(getInfo());
    }
    public Task<Boolean> fetchAndActivate() { return Tasks.forResult(Boolean.FALSE); }
    public Task<Void>    fetch()            { return Tasks.forResult(null); }
    public Task<Void>    fetch(long minimumFetchIntervalInSeconds) {
        return Tasks.forResult(null);
    }
    public Task<Boolean> activate()         { return Tasks.forResult(Boolean.FALSE); }
    public Task<Void>    reset()            { return Tasks.forResult(null); }

    // Pre-v17 legacy synchronous methods. They were the canonical API
    // before fetchAndActivate became the recommended pattern, and many
    // shipped APKs (HCR 1.43 among them) still call them. Mirror the
    // sync no-op semantics: activateFetched returns true if there was
    // a newly-fetched config to activate (we always say "no, we have
    // no new config" -> false), and the *Defaults pre-async forms
    // just feed straight into the in-memory map.
    public boolean activateFetched()       { return false; }
    public long    getFetchTimeMillis()    { return 0L; }
    public int     getLastFetchStatus()    { return LAST_FETCH_STATUS_NO_FETCH_YET; }

    public Task<Void> setDefaultsAsync(Map<String, Object> defaults) {
        if (defaults != null) this.defaults.putAll(defaults);
        return Tasks.forResult(null);
    }
    /** Resource-XML form. Apps use this to point Firebase at an
     *  android:resource="@xml/remote_config_defaults" entry. Real
     *  Firebase parses the XML; we don't, but we still honor the
     *  caller's intent by recording that defaults were set (the map
     *  stays empty). Apps fall back to their hardcoded defaults
     *  rather than failing. */
    public Task<Void> setDefaultsAsync(int resourceId) {
        return Tasks.forResult(null);
    }
    /** Legacy synchronous setDefaults (deprecated in real SDK,
     *  still present and used by older apps). */
    public void setDefaults(Map<String, Object> defaults) {
        if (defaults != null) this.defaults.putAll(defaults);
    }
    public void setDefaults(int resourceId) {}

    public String getString(String key) {
        Object v = defaults.get(key);
        return v != null ? v.toString() : DEFAULT_VALUE_FOR_STRING;
    }
    public boolean getBoolean(String key) {
        Object v = defaults.get(key);
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof String)  return Boolean.parseBoolean((String) v);
        return DEFAULT_VALUE_FOR_BOOLEAN;
    }
    public long getLong(String key) {
        Object v = defaults.get(key);
        if (v instanceof Number) return ((Number) v).longValue();
        if (v instanceof String) {
            try { return Long.parseLong((String) v); } catch (Exception e) { return 0L; }
        }
        return DEFAULT_VALUE_FOR_LONG;
    }
    public double getDouble(String key) {
        Object v = defaults.get(key);
        if (v instanceof Number) return ((Number) v).doubleValue();
        if (v instanceof String) {
            try { return Double.parseDouble((String) v); } catch (Exception e) { return 0.0; }
        }
        return DEFAULT_VALUE_FOR_DOUBLE;
    }
    public byte[] getByteArray(String key) {
        Object v = defaults.get(key);
        if (v instanceof byte[]) return (byte[]) v;
        return new byte[0];
    }
    public FirebaseRemoteConfigValue getValue(String key) {
        return new FirebaseRemoteConfigValue(defaults.get(key));
    }
    public Map<String, FirebaseRemoteConfigValue> getAll() {
        Map<String, FirebaseRemoteConfigValue> out = new HashMap<>();
        for (Map.Entry<String, Object> e : defaults.entrySet()) {
            out.put(e.getKey(), new FirebaseRemoteConfigValue(e.getValue()));
        }
        return out;
    }
    public java.util.Set<String> getKeysByPrefix(String prefix) {
        java.util.Set<String> out = new java.util.HashSet<>();
        for (String k : defaults.keySet()) {
            if (prefix == null || k.startsWith(prefix)) out.add(k);
        }
        return out;
    }

    public Task<Void> setConfigSettingsAsync(FirebaseRemoteConfigSettings settings) {
        return Tasks.forResult(null);
    }
    /** Legacy synchronous form (pre v17.0.0). Many older APKs still
     *  call this. Real SDK kept the symbol for binary compat after
     *  the async form became canonical -- we mirror that here. */
    public void setConfigSettings(FirebaseRemoteConfigSettings settings) {}
    public FirebaseRemoteConfigInfo getInfo() {
        return new FirebaseRemoteConfigInfo() {
            @Override public long getFetchTimeMillis() { return 0L; }
            @Override public int  getLastFetchStatus()  { return LAST_FETCH_STATUS_NO_FETCH_YET; }
            @Override public FirebaseRemoteConfigSettings getConfigSettings() {
                return new FirebaseRemoteConfigSettings.Builder().build();
            }
        };
    }

    /** Listener registration. Real Firebase fires this when a config
     *  update arrives from the server; ours never fires. We accept
     *  the registration so apps don't NPE on the returned handle. */
    public ConfigUpdateListenerRegistration addOnConfigUpdateListener(
            ConfigUpdateListener listener) {
        return new ConfigUpdateListenerRegistration() {
            @Override public void remove() {}
        };
    }
}
