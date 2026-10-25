// org.json.* lives in Android's bootclasspath but is not in OpenJDK, so we
// reimplement it here (see JSONObject.java / JSONArray.java / JSONTokener.java
// for the real parser + AOSP-mirroring get/put/opt surface -- this is NOT a
// compile-only stub; it backs real JSON parsing used by APKs that call
// org.json directly, same as on a real device).
package org.json;

public class JSONException extends Exception {
    public JSONException(String s) { super(s); }
    public JSONException(String s, Throwable cause) { super(s, cause); }
    public JSONException(Throwable cause) { super(cause); }
}
