package com.google.firebase.functions;

// com.google.firebase.functions.HttpsCallableResult -- the result of a callable
// function invocation. Real Functions wraps the JSON the function returned. On
// Mac.apk no call ever succeeds (no backend), so an instance is only ever
// constructed defensively with null data -- we never fabricate a function
// payload.
//
// Signatures mirror the published Firebase Functions Android reference.
public class HttpsCallableResult {

    private final Object data;

    HttpsCallableResult(Object data) {
        this.data = data;
    }

    public Object getData() { return data; }
}
