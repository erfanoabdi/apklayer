package com.google.firebase.database;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

// com.google.firebase.database.ServerValue -- sentinel values an app writes to
// request a server-computed value (e.g. the server timestamp). Real Firebase
// resolves these on the backend; on our offline shim writes are never
// round-tripped, so these sentinels are simply accepted by setValue and never
// resolved. The constants are kept so apps that reference them compile and
// write without error.
//
// TIMESTAMP and increment() match the published Firebase RTDB Android reference.
public class ServerValue {

    private ServerValue() {}

    // Map<String,String> {".sv":"timestamp"} -- the exact sentinel the SDK ships.
    public static final Map<String, String> TIMESTAMP;
    static {
        Map<String, String> m = new HashMap<String, String>();
        m.put(".sv", "timestamp");
        TIMESTAMP = Collections.unmodifiableMap(m);
    }

    // Atomic increment sentinel: {".sv":{"increment":delta}}.
    public static Object increment(double delta) {
        Map<String, Object> inner = new HashMap<String, Object>();
        inner.put("increment", delta);
        Map<String, Object> sentinel = new HashMap<String, Object>();
        sentinel.put(".sv", inner);
        return sentinel;
    }

    public static Object increment(long delta) {
        return increment((double) delta);
    }
}
