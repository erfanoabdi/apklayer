// org.json.JSON -- package-private coercion helpers shared by JSONObject and
// JSONArray. Ported verbatim (behavior-for-behavior) from AOSP libcore:
//   https://android.googlesource.com/platform/libcore/+/master/json/src/main/java/org/json/JSON.java
// This class did not previously exist in Mac.apk's org.json shim, which is
// why JSONObject/JSONArray's getInt/getLong/getDouble/getBoolean/opt* methods
// hand-rolled their own (stricter, non-AOSP) coercion instead of sharing one
// helper -- e.g. Integer.parseInt("3.5") throws where AOSP's
// (int) Double.parseDouble("3.5") succeeds, and the opt* family did no String
// coercion at all (a JSON value stored as the STRING "42" made every optInt
// silently return the fallback instead of 42). See JSONObject.java /
// JSONArray.java for how these are wired in.
package org.json;

final class JSON {
    private JSON() {}

    /** AOSP: reject non-finite numbers before they enter a JSONObject/JSONArray. */
    static double checkDouble(double d) throws JSONException {
        if (Double.isInfinite(d) || Double.isNaN(d)) {
            throw new JSONException("Forbidden numeric value: " + d);
        }
        return d;
    }

    static Boolean toBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value instanceof String) {
            String stringValue = (String) value;
            if ("true".equalsIgnoreCase(stringValue)) {
                return true;
            } else if ("false".equalsIgnoreCase(stringValue)) {
                return false;
            }
        }
        return null;
    }

    static Double toDouble(Object value) {
        if (value instanceof Double) {
            return (Double) value;
        } else if (value instanceof Number) {
            return ((Number) value).doubleValue();
        } else if (value instanceof String) {
            try {
                return Double.valueOf((String) value);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    static Integer toInteger(Object value) {
        if (value instanceof Integer) {
            return (Integer) value;
        } else if (value instanceof Number) {
            return ((Number) value).intValue();
        } else if (value instanceof String) {
            try {
                return (int) Double.parseDouble((String) value);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    static Long toLong(Object value) {
        if (value instanceof Long) {
            return (Long) value;
        } else if (value instanceof Number) {
            return ((Number) value).longValue();
        } else if (value instanceof String) {
            try {
                return (long) Double.parseDouble((String) value);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    static String toString(Object value) {
        if (value instanceof String) {
            return (String) value;
        } else if (value != null) {
            return String.valueOf(value);
        }
        return null;
    }

    static JSONException typeMismatch(Object indexOrName, Object actual,
            String requiredType) throws JSONException {
        if (actual == null) {
            throw new JSONException("Value at " + indexOrName + " is null.");
        } else {
            throw new JSONException("Value " + actual + " at " + indexOrName
                    + " of type " + actual.getClass().getName()
                    + " cannot be converted to " + requiredType);
        }
    }

    static JSONException typeMismatch(Object actual, String requiredType)
            throws JSONException {
        if (actual == null) {
            throw new JSONException("Value is null.");
        } else {
            throw new JSONException("Value " + actual
                    + " of type " + actual.getClass().getName()
                    + " cannot be converted to " + requiredType);
        }
    }
}
