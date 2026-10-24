package com.google.firebase.remoteconfig;

// Holds a single Remote Config value of indeterminate type. Real
// Firebase tracks the source (default vs remote vs static) per key;
// our stub treats every value as source=DEFAULT since no remote
// values can ever arrive.
public final class FirebaseRemoteConfigValue {
    public static final int VALUE_SOURCE_STATIC  = 0;
    public static final int VALUE_SOURCE_DEFAULT = 1;
    public static final int VALUE_SOURCE_REMOTE  = 2;

    private final Object value;
    FirebaseRemoteConfigValue(Object value) { this.value = value; }

    public boolean asBoolean() {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String)  return Boolean.parseBoolean((String) value);
        return false;
    }
    public byte[] asByteArray() {
        if (value instanceof byte[]) return (byte[]) value;
        return new byte[0];
    }
    public double asDouble() {
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String) {
            try { return Double.parseDouble((String) value); } catch (Exception e) { return 0.0; }
        }
        return 0.0;
    }
    public long asLong() {
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String) {
            try { return Long.parseLong((String) value); } catch (Exception e) { return 0L; }
        }
        return 0L;
    }
    public String asString() { return value != null ? value.toString() : ""; }
    public int getSource() {
        return value == null ? VALUE_SOURCE_STATIC : VALUE_SOURCE_DEFAULT;
    }
}
