// com.google.firebase.messaging.SendException -- the exception type passed to
// FirebaseMessagingService.onSendError(msgId, exception) when an upstream
// (device-to-server) send fails. On Mac.apk no upstream send ever happens (no
// FCM channel), so this is constructed only when handleIntent decodes a
// "send_error" intent. Shipped so consumer code that catches/inspects it
// compiles and so the error-code constants resolve.
//
// Faithful to firebase-android-sdk
// firebase-messaging/.../SendException.java: extends Exception; ERROR_*
// constants 0..4; getErrorCode() parses the wire error string. The ctor is
// package-private in the SDK; we keep it package-private too (only
// FirebaseMessagingService in this package constructs it).
package com.google.firebase.messaging;

public final class SendException extends Exception {

    public static final int ERROR_UNKNOWN            = 0;
    public static final int ERROR_INVALID_PARAMETERS = 1;
    public static final int ERROR_SIZE              = 2;
    public static final int ERROR_TTL_EXCEEDED      = 3;
    public static final int ERROR_TOO_MANY_MESSAGES = 4;

    private final int errorCode;

    SendException(String error) {
        super(error);
        this.errorCode = parseErrorCode(error);
    }

    public int getErrorCode() {
        return errorCode;
    }

    // Map the wire error string the GMS component sends to a code constant.
    private static int parseErrorCode(String error) {
        if (error == null) return ERROR_UNKNOWN;
        switch (error) {
            case "InvalidParameters":  return ERROR_INVALID_PARAMETERS;
            case "MessageTooBig":      return ERROR_SIZE;
            case "TtlExceeded":        return ERROR_TTL_EXCEEDED;
            case "TooManyMessages":    return ERROR_TOO_MANY_MESSAGES;
            default:                   return ERROR_UNKNOWN;
        }
    }
}
